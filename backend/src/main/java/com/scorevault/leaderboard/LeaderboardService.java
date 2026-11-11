package com.scorevault.leaderboard;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardService {

    private final JdbcTemplate jdbc;

    public LeaderboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RoundOptionView> listRounds() {
        return jdbc.query(
                "SELECT r.round_id, r.event_id, e.title AS event_title, r.round_number, r.round_name, "
                        + "r.is_final, r.status "
                        + "FROM rounds r "
                        + "JOIN events e ON e.event_id = r.event_id "
                        + "ORDER BY r.event_id, r.round_number",
                (rs, i) -> new RoundOptionView(
                        rs.getInt("round_id"),
                        rs.getInt("event_id"),
                        rs.getString("event_title"),
                        rs.getInt("round_number"),
                        rs.getString("round_name"),
                        rs.getBoolean("is_final"),
                        rs.getString("status")));
    }

    public List<LeaderboardEntryView> getLeaderboard(int roundId) {
        return jdbc.query(
                "SELECT round_id, event_id, event_title, round_number, round_name, "
                        + "team_id, team_name, team_lead, institution, judges_evaluated_count, "
                        + "final_weighted_average_score, leaderboard_rank, "
                        + "priority_criterion_score, first_submitted_at "
                        + "FROM vw_round_leaderboard "
                        + "WHERE round_id = ? "
                        + "ORDER BY leaderboard_rank, team_name",
                (rs, i) -> {
                    Timestamp ts = rs.getTimestamp("first_submitted_at");
                    return new LeaderboardEntryView(
                            rs.getInt("round_id"),
                            rs.getInt("event_id"),
                            rs.getString("event_title"),
                            rs.getInt("round_number"),
                            rs.getString("round_name"),
                            rs.getInt("team_id"),
                            rs.getString("team_name"),
                            rs.getString("team_lead"),
                            rs.getString("institution"),
                            rs.getInt("judges_evaluated_count"),
                            rs.getBigDecimal("final_weighted_average_score"),
                            rs.getLong("leaderboard_rank"),
                            rs.getBigDecimal("priority_criterion_score"),
                            ts != null ? ts.toInstant().toString() : null);
                },
                roundId);
    }
}
