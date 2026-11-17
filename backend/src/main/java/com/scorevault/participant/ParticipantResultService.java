package com.scorevault.participant;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ParticipantResultService {

    private final JdbcTemplate jdbc;

    public ParticipantResultService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Only rounds with status COMPLETED (finalized and locked by the admin) are ever returned. */
    public List<TeamResultView> myResults(int userId) {
        List<TeamResultView> base = jdbc.query(
                "SELECT t.team_id, t.team_name, e.event_id, e.title, "
                        + "r.round_id, r.round_number, r.round_name, "
                        + "lb.final_weighted_average_score, lb.leaderboard_rank, lb.judges_evaluated_count "
                        + "FROM team_members tm "
                        + "JOIN teams t ON t.team_id = tm.team_id "
                        + "JOIN events e ON e.event_id = t.event_id "
                        + "JOIN rounds r ON r.event_id = e.event_id AND r.status = 'COMPLETED' "
                        + "LEFT JOIN vw_round_leaderboard lb "
                        + "  ON lb.round_id = r.round_id AND lb.team_id = t.team_id "
                        + "WHERE tm.user_id = ? "
                        + "ORDER BY e.title, r.round_number",
                (rs, i) -> {
                    long rank = rs.getLong("leaderboard_rank");
                    Long rankOrNull = rs.wasNull() ? null : rank;
                    return new TeamResultView(
                            rs.getInt("team_id"), rs.getString("team_name"),
                            rs.getInt("event_id"), rs.getString("title"),
                            rs.getInt("round_id"), rs.getInt("round_number"), rs.getString("round_name"),
                            rs.getBigDecimal("final_weighted_average_score"), rankOrNull,
                            rs.getInt("judges_evaluated_count"), List.of());
                },
                userId);

        return base.stream()
                .map(b -> new TeamResultView(b.teamId(), b.teamName(), b.eventId(), b.eventTitle(),
                        b.roundId(), b.roundNumber(), b.roundName(), b.finalScore(), b.rank(),
                        b.judgesEvaluated(), criteriaFor(b.roundId(), b.teamId())))
                .toList();
    }

    // Averaged across judges, so no individual judge's scores are exposed.
    private List<ResultCriterionView> criteriaFor(int roundId, int teamId) {
        return jdbc.query(
                "SELECT c.name, c.max_score, c.weightage_percent, "
                        + "AVG(s.raw_score) AS avg_raw, AVG(s.weighted_score) AS avg_weighted "
                        + "FROM judge_assignments ja "
                        + "JOIN scores s ON s.assignment_id = ja.assignment_id "
                        + "JOIN criteria c ON c.criterion_id = s.criterion_id "
                        + "WHERE ja.round_id = ? AND ja.team_id = ? "
                        + "  AND ja.status = 'SUBMITTED' AND s.is_locked = 1 "
                        + "GROUP BY c.criterion_id, c.name, c.max_score, c.weightage_percent "
                        + "ORDER BY c.criterion_id",
                (rs, i) -> new ResultCriterionView(
                        rs.getString("name"), rs.getBigDecimal("max_score"),
                        rs.getBigDecimal("weightage_percent"),
                        rs.getBigDecimal("avg_raw").setScale(2, java.math.RoundingMode.HALF_UP),
                        rs.getBigDecimal("avg_weighted").setScale(2, java.math.RoundingMode.HALF_UP)),
                roundId, teamId);
    }
}