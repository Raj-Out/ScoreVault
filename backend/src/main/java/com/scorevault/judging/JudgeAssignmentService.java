package com.scorevault.judging;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scorevault.common.ApiException;
import com.scorevault.common.AuditService;

@Service
public class JudgeAssignmentService {

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public JudgeAssignmentService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<JudgeUserView> listJudges() {
        return jdbc.query(
                "SELECT user_id, full_name, email, affiliation "
                        + "FROM users WHERE role_id = (SELECT role_id FROM roles WHERE role_name = 'JUDGE') AND is_active = 1 "
                        + "ORDER BY full_name",
                (rs, i) -> new JudgeUserView(
                        rs.getInt("user_id"),
                        rs.getString("full_name"),
                        rs.getString("email"),
                        rs.getString("affiliation")));
    }

    public List<TeamOptionView> listTeamsForRound(int roundId) {
        return jdbc.query(
                "SELECT t.team_id, t.event_id, t.team_name, u_lead.full_name AS lead_name, "
                        + "u_lead.affiliation AS lead_affiliation, "
                        + "STRING_AGG(u_mem.affiliation, ', ') WITHIN GROUP (ORDER BY u_mem.user_id) AS member_affiliations "
                        + "FROM teams t "
                        + "JOIN rounds r ON r.event_id = t.event_id "
                        + "JOIN users u_lead ON u_lead.user_id = t.lead_user_id "
                        + "LEFT JOIN team_members tm ON tm.team_id = t.team_id "
                        + "LEFT JOIN users u_mem ON u_mem.user_id = tm.user_id "
                        + "WHERE r.round_id = ? "
                        + "GROUP BY t.team_id, t.event_id, t.team_name, u_lead.full_name, u_lead.affiliation "
                        + "ORDER BY t.team_name",
                (rs, i) -> new TeamOptionView(
                        rs.getInt("team_id"),
                        rs.getInt("event_id"),
                        rs.getString("team_name"),
                        rs.getString("lead_name"),
                        rs.getString("lead_affiliation"),
                        rs.getString("member_affiliations")),
                roundId);
    }

    public List<JudgeAssignmentView> listAssignments(Integer roundId) {
        String sql = "SELECT ja.assignment_id, ja.round_id, r.round_name, e.title AS event_title, "
                + "ja.judge_user_id, j.full_name AS judge_name, j.affiliation AS judge_affiliation, "
                + "ja.team_id, t.team_name, ja.status, ja.assigned_at "
                + "FROM judge_assignments ja "
                + "JOIN rounds r ON r.round_id = ja.round_id "
                + "JOIN events e ON e.event_id = r.event_id "
                + "JOIN users j ON j.user_id = ja.judge_user_id "
                + "JOIN teams t ON t.team_id = ja.team_id ";
        if (roundId != null) {
            sql += "WHERE ja.round_id = ? ";
        }
        sql += "ORDER BY ja.assignment_id DESC";

        Object[] params = roundId != null ? new Object[] {roundId} : new Object[] {};
        return jdbc.query(sql, (rs, i) -> {
            Timestamp ts = rs.getTimestamp("assigned_at");
            return new JudgeAssignmentView(
                    rs.getInt("assignment_id"),
                    rs.getInt("round_id"),
                    rs.getString("round_name"),
                    rs.getString("event_title"),
                    rs.getInt("judge_user_id"),
                    rs.getString("judge_name"),
                    rs.getString("judge_affiliation"),
                    rs.getInt("team_id"),
                    rs.getString("team_name"),
                    rs.getString("status"),
                    ts != null ? ts.toInstant().toString() : null);
        }, params);
    }

    @Transactional
    public JudgeAssignmentView assignJudge(AssignJudgeRequest req, int adminUserId, String ip) {
        try {
            // Set session context for trigger audit attribution
            jdbc.update("EXEC sp_set_session_context @key=N'user_id', @value=?", adminUserId);
            jdbc.update("EXEC sp_set_session_context @key=N'client_ip', @value=?", ip);

            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO judge_assignments (round_id, judge_user_id, team_id, status) VALUES (?, ?, ?, 'ASSIGNED')",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, req.roundId());
                ps.setInt(2, req.judgeUserId());
                ps.setInt(3, req.teamId());
                return ps;
            }, keys);

            Number key = keys.getKey();
            if (key == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Assignment could not be recorded.");
            }
            int assignmentId = key.intValue();

            audit.log(adminUserId, "JUDGE_ASSIGNED", "judge_assignments", String.valueOf(assignmentId),
                    "Judge " + req.judgeUserId() + " assigned to Team " + req.teamId() + " in Round " + req.roundId(), ip);

            return listAssignments(null).stream()
                    .filter(a -> a.assignmentId() == assignmentId)
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Assignment record could not be loaded."));

        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "This judge is already assigned to this team in this round.");
        } catch (DataAccessException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Conflict of interest") || msg.contains("50001"))) {
                // Extract clean message if possible
                int startIdx = msg.indexOf("Conflict of interest:");
                String cleanMsg = startIdx != -1 ? msg.substring(startIdx) : "Conflict of interest: Judge and team member share institutional affiliation.";
                int endIdx = cleanMsg.indexOf("\n");
                if (endIdx != -1) {
                    cleanMsg = cleanMsg.substring(0, endIdx);
                }
                throw new ApiException(HttpStatus.CONFLICT, cleanMsg);
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "Database rejected assignment: " + (msg != null ? msg : "Unknown database error."));
        }
    }
}
