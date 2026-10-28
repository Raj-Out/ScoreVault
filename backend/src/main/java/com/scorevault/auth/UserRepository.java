package com.scorevault.auth;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Parameterised query: the email is bound as a value, never concatenated into SQL. */
    public Optional<UserRecord> findByEmail(String email) {
        List<UserRecord> rows = jdbc.query(
                "SELECT u.user_id, u.full_name, u.password_hash, u.is_active, r.role_name "
                        + "FROM users u JOIN roles r ON r.role_id = u.role_id WHERE u.email = ?",
                (rs, i) -> new UserRecord(
                        rs.getInt("user_id"),
                        rs.getString("full_name"),
                        rs.getString("password_hash"),
                        rs.getBoolean("is_active"),
                        rs.getString("role_name")),
                email);
        return rows.stream().findFirst();
    }
}
