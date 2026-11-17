package com.scorevault.participant;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParticipantTeamService {

    private final JdbcTemplate jdbc;

    public ParticipantTeamService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public TeamView createTeam(int userId, CreateTeamRequest req) {
        if (req == null || req.eventId() == null || req.teamName() == null || req.teamName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId and teamName are required.");
        }
        String name = req.teamName().trim();
        if (name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Team name is too long.");
        }
        int eventId = req.eventId();

        Integer events = jdbc.queryForObject(
                "SELECT COUNT(*) FROM events WHERE event_id = ?", Integer.class, eventId);
        if (events == null || events == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
        }
        if (isInTeamForEvent(userId, eventId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already in a team for this event.");
        }

        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO teams (event_id, team_name, lead_user_id, created_at) "
                            + "VALUES (?, ?, ?, SYSUTCDATETIME())",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, eventId);
            ps.setString(2, name);
            ps.setInt(3, userId);
            return ps;
        }, keys);
        int teamId = keys.getKey().intValue();

        addMemberRow(teamId, userId);
        return getTeam(teamId, userId);
    }

    public List<TeamView> listMyTeams(int userId) {
        List<Integer> ids = jdbc.queryForList(
                "SELECT t.team_id FROM teams t "
                        + "JOIN team_members tm ON tm.team_id = t.team_id "
                        + "WHERE tm.user_id = ? ORDER BY t.created_at DESC",
                Integer.class, userId);
        return ids.stream().map(id -> getTeam(id, userId)).toList();
    }

    @Transactional
    public TeamView addMember(int callerId, int teamId, String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required.");
        }
        List<int[]> team = jdbc.query(
                "SELECT event_id, lead_user_id FROM teams WHERE team_id = ?",
                (rs, i) -> new int[] { rs.getInt("event_id"), rs.getInt("lead_user_id") }, teamId);
        if (team.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found.");
        }
        if (team.get(0)[1] != callerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the team lead can add members.");
        }
        List<Integer> users = jdbc.queryForList(
                "SELECT user_id FROM users WHERE LOWER(email) = LOWER(?) AND is_active = 1",
                Integer.class, email.trim());
        if (users.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No active user with that email.");
        }
        int newUserId = users.get(0);
        if (isInTeamForEvent(newUserId, team.get(0)[0])) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That user is already in a team for this event.");
        }
        addMemberRow(teamId, newUserId);
        return getTeam(teamId, callerId);
    }

    private boolean isInTeamForEvent(int userId, int eventId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM team_members tm JOIN teams t ON t.team_id = tm.team_id "
                        + "WHERE tm.user_id = ? AND t.event_id = ?",
                Integer.class, userId, eventId);
        return n != null && n > 0;
    }

    private void addMemberRow(int teamId, int userId) {
        jdbc.update("INSERT INTO team_members (team_id, user_id, joined_at) VALUES (?, ?, SYSUTCDATETIME())",
                teamId, userId);
    }

    private TeamView getTeam(int teamId, int viewerId) {
        List<TeamView> rows = jdbc.query(
                "SELECT t.team_id, t.event_id, e.title, t.team_name, t.lead_user_id "
                        + "FROM teams t JOIN events e ON e.event_id = t.event_id WHERE t.team_id = ?",
                (rs, i) -> new TeamView(rs.getInt("team_id"), rs.getInt("event_id"), rs.getString("title"),
                        rs.getString("team_name"), rs.getInt("lead_user_id") == viewerId, List.of()),
                teamId);
        TeamView t = rows.get(0);
        List<String> members = jdbc.queryForList(
                "SELECT u.full_name FROM team_members tm JOIN users u ON u.user_id = tm.user_id "
                        + "WHERE tm.team_id = ? ORDER BY tm.joined_at",
                String.class, teamId);
        return new TeamView(t.teamId(), t.eventId(), t.eventTitle(), t.teamName(), t.lead(), members);
    }
}
