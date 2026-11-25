package com.scorevault.admin;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

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
public class AdminEventService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public AdminEventService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<EventOptionView> listEvents() {
        return jdbc.query(
                "SELECT e.event_id, e.title, c.name AS category_name, e.status "
                        + "FROM events e JOIN event_categories c ON c.category_id = e.category_id "
                        + "WHERE e.status <> 'ARCHIVED' ORDER BY e.start_date, e.title",
                (rs, row) -> new EventOptionView(rs.getInt("event_id"), rs.getString("title"),
                        rs.getString("category_name"), rs.getString("status")));
    }

    @Transactional
    public int createRound(CreateRoundRequest request, int adminId, String ip) {
        Integer eventCount = jdbc.queryForObject("SELECT COUNT(*) FROM events WHERE event_id=? AND status <> 'ARCHIVED'",
                Integer.class, request.eventId());
        if (eventCount == null || eventCount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an available event.");
        }
        try {
            jdbc.update("EXEC sp_set_session_context @key=N'user_id', @value=?", adminId);
            jdbc.update("EXEC sp_set_session_context @key=N'client_ip', @value=?", ip);
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO rounds (event_id, round_number, round_name, is_final, status) "
                                + "VALUES (?, ?, ?, ?, 'SCHEDULED')",
                        Statement.RETURN_GENERATED_KEYS);
                statement.setInt(1, request.eventId());
                statement.setInt(2, request.roundNumber());
                statement.setString(3, request.roundName().trim());
                statement.setBoolean(4, request.isFinal());
                return statement;
            }, keys);
            Number key = keys.getKey();
            if (key == null) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Round could not be created."); }
            int roundId = key.intValue();
            audit.log(adminId, "ROUND_CREATED", "rounds", String.valueOf(roundId),
                    "Round " + request.roundNumber() + " created for event " + request.eventId(), ip);
            return roundId;
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "That round number already exists for this event.");
        }
    }
}
