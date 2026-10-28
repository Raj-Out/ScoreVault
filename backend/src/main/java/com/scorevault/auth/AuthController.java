package com.scorevault.auth;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.scorevault.common.ApiException;
import com.scorevault.common.AuditService;
import com.scorevault.security.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    // A valid bcrypt hash of an unused password. Checked when the email is unknown so that
    // "unknown email" and "wrong password" take the same time (prevents user enumeration).
    private static final String DUMMY_HASH = "$2a$12$jR28eFK6NO3MmN5gqlpBH.oOl0ChIk53r4xQL2ttEiPr2K0o9z8NO";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final AuditService audit;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwtService, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.audit = audit;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        String email = request.email().trim();

        Optional<UserRecord> found = users.findByEmail(email);
        boolean passwordOk;
        if (found.isPresent()) {
            passwordOk = encoder.matches(request.password(), found.get().passwordHash());
        } else {
            encoder.matches(request.password(), DUMMY_HASH);
            passwordOk = false;
        }

        if (!passwordOk || !found.get().active()) {
            Integer userId = found.isPresent() ? found.get().id() : null;
            audit.log(userId, "LOGIN_FAILED", "users", "login", abbreviate(email), ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        UserRecord user = found.get();
        audit.log(user.id(), "LOGIN_SUCCESS", "users", String.valueOf(user.id()), null, ip);
        String token = jwtService.createToken(user.id(), user.role(), user.fullName());
        return new LoginResponse(token, new LoginResponse.UserInfo(user.id(), user.fullName(), user.role()));
    }

    private static String abbreviate(String text) {
        return text.length() > 100 ? text.substring(0, 100) : text;
    }
}
