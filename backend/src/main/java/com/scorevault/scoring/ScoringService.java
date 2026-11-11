package com.scorevault.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scorevault.common.ApiException;
import com.scorevault.common.AuditService;

@Service
public class ScoringService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public ScoringService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<JudgeTaskView> listTasksForJudge(int judgeUserId) {
        // 1. Fetch assignments for this judge
        String sql = "SELECT ja.assignment_id, ja.round_id, r.round_name, e.event_id, e.title AS event_title, "
                + "e.template_id, ja.team_id, t.team_name, u_lead.full_name AS team_lead, "
                + "s.file_blob_url, s.file_name, s.submission_notes, ja.status "
                + "FROM judge_assignments ja "
                + "JOIN rounds r ON r.round_id = ja.round_id "
                + "JOIN events e ON e.event_id = r.event_id "
                + "JOIN teams t ON t.team_id = ja.team_id "
                + "JOIN users u_lead ON u_lead.user_id = t.lead_user_id "
                + "LEFT JOIN submissions s ON s.round_id = ja.round_id AND s.team_id = ja.team_id "
                + "WHERE ja.judge_user_id = ? "
                + "ORDER BY ja.assignment_id DESC";

        return jdbc.query(sql, (rs, i) -> {
            int assignmentId = rs.getInt("assignment_id");
            int templateId = rs.getInt("template_id");
            String status = rs.getString("status");

            // Fetch criteria and existing scores for this assignment
            List<JudgeTaskCriterionView> criteria = loadCriteriaForAssignment(assignmentId, templateId);
            boolean isLocked = criteria.stream()
                    .anyMatch(c -> c.existingWeightedScore() != null && "SUBMITTED".equalsIgnoreCase(status));

            return new JudgeTaskView(
                    assignmentId,
                    rs.getInt("round_id"),
                    rs.getString("round_name"),
                    rs.getInt("event_id"),
                    rs.getString("event_title"),
                    rs.getInt("team_id"),
                    rs.getString("team_name"),
                    rs.getString("team_lead"),
                    rs.getString("file_blob_url"),
                    rs.getString("file_name"),
                    rs.getString("submission_notes"),
                    status,
                    isLocked,
                    criteria);
        }, judgeUserId);
    }

    private List<JudgeTaskCriterionView> loadCriteriaForAssignment(int assignmentId, int templateId) {
        // Map existing scores
        Map<Integer, BigDecimal[]> existingScores = new LinkedHashMap<>();
        jdbc.query("SELECT criterion_id, raw_score, weighted_score FROM scores WHERE assignment_id = ?",
                rs -> {
                    existingScores.put(rs.getInt("criterion_id"),
                            new BigDecimal[] {rs.getBigDecimal("raw_score"), rs.getBigDecimal("weighted_score")});
                }, assignmentId);

        return jdbc.query(
                "SELECT criterion_id, name, description, max_score, weightage_percent "
                        + "FROM criteria WHERE template_id = ? ORDER BY criterion_id",
                (rs, i) -> {
                    int cId = rs.getInt("criterion_id");
                    BigDecimal[] sc = existingScores.get(cId);
                    BigDecimal raw = sc != null ? sc[0] : null;
                    BigDecimal weighted = sc != null ? sc[1] : null;

                    return new JudgeTaskCriterionView(
                            cId,
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getBigDecimal("max_score"),
                            rs.getBigDecimal("weightage_percent"),
                            raw,
                            weighted);
                }, templateId);
    }

    @Transactional
    public ScoredSummaryView submitAndLockScores(SubmitScoreRequest req, int judgeUserId, String ip) {
        // 1. Verify caller owns this assignment
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ja.assignment_id, ja.team_id, t.team_name, ja.status, e.template_id "
                        + "FROM judge_assignments ja "
                        + "JOIN rounds r ON r.round_id = ja.round_id "
                        + "JOIN events e ON e.event_id = r.event_id "
                        + "JOIN teams t ON t.team_id = ja.team_id "
                        + "WHERE ja.assignment_id = ? AND ja.judge_user_id = ?",
                req.assignmentId(), judgeUserId);

        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assignment not found or not assigned to you.");
        }

        Map<String, Object> assignment = rows.get(0);
        int teamId = (Integer) assignment.get("team_id");
        String teamName = (String) assignment.get("team_name");
        int templateId = (Integer) assignment.get("template_id");

        // 2. Check if already locked
        Integer lockedCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM scores WHERE assignment_id = ? AND is_locked = 1",
                Integer.class, req.assignmentId());
        if (lockedCount != null && lockedCount > 0) {
            throw new ApiException(HttpStatus.LOCKED, "Scores for this assignment are already finalized and locked.");
        }

        // 3. Set database session context for audit trigger attribution
        jdbc.update("EXEC sp_set_session_context @key=N'user_id', @value=?", judgeUserId);
        jdbc.update("EXEC sp_set_session_context @key=N'client_ip', @value=?", ip);

        // 4. Load rubric criteria to validate raw scores and calculate weighted marks
        Map<Integer, BigDecimal[]> templateCriteria = new LinkedHashMap<>();
        jdbc.query("SELECT criterion_id, max_score, weightage_percent FROM criteria WHERE template_id = ?",
                rs -> {
                    templateCriteria.put(rs.getInt("criterion_id"),
                            new BigDecimal[] {rs.getBigDecimal("max_score"), rs.getBigDecimal("weightage_percent")});
                }, templateId);

        BigDecimal totalWeighted = BigDecimal.ZERO;

        // Clean out any previous draft scores if re-submitting before lock
        jdbc.update("DELETE FROM scores WHERE assignment_id = ? AND is_locked = 0", req.assignmentId());

        for (CriterionScoreInput input : req.criteriaScores()) {
            BigDecimal[] meta = templateCriteria.get(input.criterionId());
            if (meta == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Criterion ID " + input.criterionId() + " does not belong to this rubric.");
            }
            BigDecimal maxScore = meta[0];
            BigDecimal weightage = meta[1];

            if (input.rawScore().compareTo(maxScore) > 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Raw score " + input.rawScore() + " exceeds maximum score " + maxScore + " for criterion.");
            }

            // Weighted score formula (scale 0-100): (raw_score / max_score) * weightage_percent
            BigDecimal weighted = input.rawScore()
                    .divide(maxScore, 4, RoundingMode.HALF_UP)
                    .multiply(weightage)
                    .setScale(3, RoundingMode.HALF_UP);

            totalWeighted = totalWeighted.add(weighted);

            jdbc.update(
                    "INSERT INTO scores (assignment_id, criterion_id, raw_score, weighted_score, is_locked) VALUES (?, ?, ?, ?, 1)",
                    new Object[] {req.assignmentId(), input.criterionId(), input.rawScore(), weighted},
                    new int[] {Types.INTEGER, Types.INTEGER, Types.DECIMAL, Types.DECIMAL});
        }

        // 5. Update assignment status to SUBMITTED
        jdbc.update("UPDATE judge_assignments SET status = 'SUBMITTED' WHERE assignment_id = ?", req.assignmentId());

        // 6. Record in audit_log
        audit.log(judgeUserId, "SCORE_SUBMITTED_AND_LOCKED", "judge_assignments", String.valueOf(req.assignmentId()),
                "Evaluated team " + teamName + " (Total Weighted Score: " + totalWeighted.setScale(2, RoundingMode.HALF_UP) + ")", ip);

        return new ScoredSummaryView(
                req.assignmentId(),
                teamId,
                teamName,
                totalWeighted.setScale(2, RoundingMode.HALF_UP),
                true,
                "SUBMITTED",
                Instant.now().toString());
    }
}
