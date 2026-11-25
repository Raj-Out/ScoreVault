package com.scorevault.admin;

import java.util.List;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scorevault.common.ApiException;
import com.scorevault.common.AuditService;

@Service
public class AdminUserService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public AdminUserService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, AuditService audit) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    public List<AdminUserView> listUsers() {
        return jdbc.query("SELECT u.user_id,u.full_name,u.email,u.affiliation,u.is_active,r.role_name "
                        + "FROM users u JOIN roles r ON r.role_id=u.role_id ORDER BY r.role_name,u.full_name",
                (rs, row) -> new AdminUserView(rs.getInt("user_id"), rs.getString("full_name"),
                        rs.getString("email"), rs.getString("affiliation"), rs.getString("role_name"),
                        rs.getBoolean("is_active")));
    }

    @Transactional
    public AdminUserView createUser(CreateUserRequest request, int adminId, String ip) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String role = request.role().trim().toUpperCase(Locale.ROOT);
        try {
            jdbc.update("EXEC sp_set_session_context @key=N'user_id', @value=?", adminId);
            jdbc.update("EXEC sp_set_session_context @key=N'client_ip', @value=?", ip);
            int userId = jdbc.queryForObject("INSERT INTO users (role_id,full_name,email,password_hash,affiliation,is_active) "
                            + "OUTPUT INSERTED.user_id VALUES ((SELECT role_id FROM roles WHERE role_name=?),?,?,?,?,1)",
                    Integer.class, role, request.fullName().trim(), email,
                    passwordEncoder.encode(request.initialPassword()), request.affiliation().trim());
            audit.log(adminId, "USER_CREATED", "users", String.valueOf(userId),
                    "Created " + role + " account " + email, ip);
            return new AdminUserView(userId, request.fullName().trim(), email,
                    request.affiliation().trim(), role, true);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "An account already exists for that email address.");
        }
    }
}
