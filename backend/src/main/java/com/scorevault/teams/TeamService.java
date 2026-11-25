package com.scorevault.teams;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
public class TeamService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public TeamService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<EventTeamOption> listOpenEvents() {
        return jdbc.query(
                "SELECT e.event_id, e.title, c.name AS category_name FROM events e "
                        + "JOIN event_categories c ON c.category_id = e.category_id "
                        + "WHERE e.status IN ('UPCOMING', 'ACTIVE') ORDER BY e.start_date, e.title",
                (rs, row) -> new EventTeamOption(rs.getInt("event_id"), rs.getString("title"), rs.getString("category_name")));
    }

    public List<TeamView> listMine(int userId) {
        List<Integer> ids = jdbc.queryForList(
                "SELECT DISTINCT t.team_id FROM teams t LEFT JOIN team_members tm ON tm.team_id = t.team_id "
                        + "WHERE t.lead_user_id = ? OR tm.user_id = ? ORDER BY t.team_id DESC",
                Integer.class, userId, userId);
        return ids.stream().map(this::getTeam).toList();
    }

    @Transactional
    public TeamView create(CreateTeamRequest request, int leadId, String ip) {
        Integer eventCount = jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE event_id=? AND status IN ('UPCOMING','ACTIVE')",
                Integer.class, request.eventId());
        if (eventCount == null || eventCount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This event is not accepting team registrations.");
        }
        List<Integer> memberIds = resolveParticipants(request.memberEmails(), leadId);
        if (isAlreadyOnEventTeam(request.eventId(), leadId)) {
            throw new ApiException(HttpStatus.CONFLICT, "You already belong to a team for this event.");
        }
        for (int memberId : memberIds) {
            if (isAlreadyOnEventTeam(request.eventId(), memberId)) {
                throw new ApiException(HttpStatus.CONFLICT, "A member already belongs to a team for this event.");
            }
        }
        try {
            jdbc.update("EXEC sp_set_session_context @key=N'user_id', @value=?", leadId);
            jdbc.update("EXEC sp_set_session_context @key=N'client_ip', @value=?", ip);
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO teams (event_id, team_name, lead_user_id) VALUES (?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setInt(1, request.eventId());
                statement.setString(2, request.teamName().trim());
                statement.setInt(3, leadId);
                return statement;
            }, keys);
            Number key = keys.getKey();
            if (key == null) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Team could not be created."); }
            int teamId = key.intValue();
            for (int memberId : memberIds) {
                jdbc.update("INSERT INTO team_members (team_id, user_id) VALUES (?, ?)", teamId, memberId);
            }
            audit.log(leadId, "TEAM_CREATED", "teams", String.valueOf(teamId),
                    "Team " + request.teamName().trim() + " created for event " + request.eventId(), ip);
            return getTeam(teamId);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "That team name is already used for this event.");
        }
    }

    private List<Integer> resolveParticipants(List<String> emails, int leadId) {
        if (emails == null || emails.isEmpty()) { return List.of(); }
        Set<String> unique = new HashSet<>();
        List<Integer> ids = new ArrayList<>();
        for (String raw : emails) {
            String email = raw.trim().toLowerCase(Locale.ROOT);
            if (!unique.add(email)) { throw new ApiException(HttpStatus.BAD_REQUEST, "Remove duplicate member emails."); }
            if (jdbc.queryForObject("SELECT COUNT(*) FROM users u JOIN roles r ON r.role_id=u.role_id "
                    + "WHERE LOWER(u.email)=? AND u.is_active=1 AND r.role_name='PARTICIPANT'",
                    Integer.class, email) != 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Member email is not an active participant: " + email);
            }
            Integer id = jdbc.queryForObject("SELECT user_id FROM users WHERE LOWER(email)=?", Integer.class, email);
            if (id == leadId) { throw new ApiException(HttpStatus.BAD_REQUEST, "You are already the team lead; do not add your own email as a member."); }
            ids.add(id);
        }
        return ids;
    }

    private boolean isAlreadyOnEventTeam(int eventId, int userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM teams t WHERE t.event_id=? AND (t.lead_user_id=? OR EXISTS "
                        + "(SELECT 1 FROM team_members tm WHERE tm.team_id=t.team_id AND tm.user_id=?))",
                Integer.class, eventId, userId, userId);
        return count != null && count > 0;
    }

    private TeamView getTeam(int teamId) {
        TeamBase team = jdbc.queryForObject(
                "SELECT t.team_id,t.event_id,e.title,t.team_name,u.full_name AS lead_name "
                        + "FROM teams t JOIN events e ON e.event_id=t.event_id "
                        + "JOIN users u ON u.user_id=t.lead_user_id WHERE t.team_id=?",
                (rs, row) -> new TeamBase(rs.getInt("team_id"), rs.getInt("event_id"), rs.getString("title"),
                        rs.getString("team_name"), rs.getString("lead_name")), teamId);
        List<TeamMemberView> members = jdbc.query(
                "SELECT u.user_id,u.full_name,u.email,u.affiliation FROM team_members tm "
                        + "JOIN users u ON u.user_id=tm.user_id WHERE tm.team_id=? ORDER BY u.full_name",
                (rs, row) -> new TeamMemberView(rs.getInt("user_id"), rs.getString("full_name"),
                        rs.getString("email"), rs.getString("affiliation")), teamId);
        return new TeamView(team.teamId(), team.eventId(), team.eventTitle(), team.teamName(), team.leadName(), members);
    }

    private record TeamBase(int teamId, int eventId, String eventTitle, String teamName, String leadName) {}
}
