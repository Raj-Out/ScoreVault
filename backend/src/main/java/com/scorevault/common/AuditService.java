package com.scorevault.common;

import java.sql.Types;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Writes security-relevant events to the audit_log table (activity log). */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void log(Integer userId, String action, String entityName, String entityId, String detail, String ip) {
        jdbc.update(
                "INSERT INTO audit_log (entity_name, entity_id, action_type, new_value, performed_by_user_id, ip_address) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                new Object[] {entityName, entityId, action, detail, userId, ip},
                new int[] {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.NVARCHAR, Types.INTEGER, Types.VARCHAR});
    }
}
