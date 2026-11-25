package com.scorevault.auth;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scorevault.common.ApiException;
import com.scorevault.common.AuditService;
import com.scorevault.security.JwtService;

@Service
public class GoogleAuthService {
    private static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final String clientId;
    private final NimbusJwtDecoder decoder;

    public GoogleAuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtService jwtService, AuditService audit,
                             @Value("${scorevault.google.client-id:}") String clientId) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.decoder = this.clientId.isBlank() ? null : NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).build();
        if (this.decoder != null) {
            OAuth2TokenValidator<Jwt> issuerValidator = token -> {
                String issuer = token.getIssuer() == null ? "" : token.getIssuer().toString();
                return issuer.equals("https://accounts.google.com") || issuer.equals("accounts.google.com")
                        ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                                new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Invalid issuer", null));
            };
            OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(this.clientId)
                    ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(
                            new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Invalid audience", null));
            this.decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefault(), issuerValidator, audienceValidator));
        }
    }

    @Transactional
    public LoginResponse login(GoogleLoginRequest request, String ip) {
        if (decoder == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not configured yet.");
        }
        final Jwt identity;
        try {
            identity = decoder.decode(request.credential());
        } catch (JwtException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google sign-in could not be verified. Please try again.");
        }
        if (!Boolean.TRUE.equals(identity.getClaim("email_verified"))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google must verify this email address before sign-in.");
        }
        String subject = identity.getSubject();
        String email = identity.getClaimAsString("email");
        String name = identity.getClaimAsString("name");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Google did not provide a verified account email.");
        }
        email = email.trim().toLowerCase(Locale.ROOT);

        UserRecord user = findByGoogleSubject(subject);
        if (user == null) {
            user = findByEmail(email);
            if (user != null) {
                String linkedSubject = googleSubjectFor(user.id());
                if (linkedSubject != null && !linkedSubject.equals(subject)) {
                    throw new ApiException(HttpStatus.CONFLICT, "This email is linked to another Google account. Contact your event administrator.");
                }
                if (linkedSubject == null) {
                    int linkedRows;
                    try {
                        linkedRows = jdbc.update("UPDATE users SET google_subject=? WHERE user_id=? AND google_subject IS NULL", subject, user.id());
                    } catch (DuplicateKeyException ex) {
                        throw new ApiException(HttpStatus.CONFLICT, "This Google account is already linked to another ScoreVault user.");
                    }
                    if (linkedRows == 0) {
                        user = findByGoogleSubject(subject);
                        if (user == null) {
                            throw new ApiException(HttpStatus.CONFLICT, "This ScoreVault account was linked to a different Google account. Contact your event administrator.");
                        }
                    } else {
                        user = findByGoogleSubject(subject);
                    }
                }
            } else {
                String affiliation = request.affiliation() == null ? "" : request.affiliation().trim();
                if (affiliation.isBlank()) {
                    throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PROFILE_REQUIRED: Enter your school or organization to create a participant account.");
                }
                try {
                    int userId = jdbc.queryForObject(
                            "INSERT INTO users (role_id,full_name,email,password_hash,affiliation,is_active,google_subject) "
                                    + "OUTPUT INSERTED.user_id VALUES ((SELECT role_id FROM roles WHERE role_name='PARTICIPANT'),?,?,?,?,1,?)",
                            Integer.class, safeName(name, email), email, passwordEncoder.encode(java.util.UUID.randomUUID().toString()), affiliation, subject);
                    user = new UserRecord(userId, safeName(name, email), "", true, "PARTICIPANT");
                } catch (DuplicateKeyException ex) {
                    user = findByGoogleSubject(subject);
                    if (user == null) { user = findByEmail(email); }
                    if (user == null) { throw ex; }
                }
            }
        }
        if (user == null || !user.active()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This ScoreVault account is inactive. Contact your event administrator.");
        }
        audit.log(user.id(), "GOOGLE_LOGIN_SUCCESS", "users", String.valueOf(user.id()), null, ip);
        return new LoginResponse(jwtService.createToken(user.id(), user.role(), user.fullName()),
                new LoginResponse.UserInfo(user.id(), user.fullName(), user.role()));
    }

    private UserRecord findByGoogleSubject(String subject) {
        List<UserRecord> rows = jdbc.query("SELECT u.user_id,u.full_name,u.password_hash,u.is_active,r.role_name FROM users u "
                        + "JOIN roles r ON r.role_id=u.role_id WHERE u.google_subject=?",
                (rs, i) -> new UserRecord(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getString(5)), subject);
        return rows.stream().findFirst().orElse(null);
    }

    private UserRecord findByEmail(String email) {
        List<UserRecord> rows = jdbc.query("SELECT u.user_id,u.full_name,u.password_hash,u.is_active,r.role_name FROM users u "
                        + "JOIN roles r ON r.role_id=u.role_id WHERE u.email=?",
                (rs, i) -> new UserRecord(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getString(5)), email);
        return rows.stream().findFirst().orElse(null);
    }

    private String googleSubjectFor(int userId) {
        return jdbc.queryForObject("SELECT google_subject FROM users WHERE user_id=?", String.class, userId);
    }

    private static String safeName(String name, String email) {
        String value = name == null ? "" : name.trim();
        if (value.isBlank()) { value = email.substring(0, email.indexOf('@')); }
        return value.length() > 150 ? value.substring(0, 150) : value;
    }
}
