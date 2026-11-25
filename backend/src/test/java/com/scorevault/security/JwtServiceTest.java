package com.scorevault.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    @Test
    void signedTokenContainsUserAndRoleClaims() {
        JwtService service = new JwtService("scorevault-unit-test-secret-with-at-least-32-bytes", 30);

        var claims = service.parse(service.createToken(42, "JUDGE", "Test Judge"));

        assertEquals("42", claims.getSubject());
        assertEquals("JUDGE", claims.get("role", String.class));
        assertEquals("Test Judge", claims.get("name", String.class));
    }

    @Test
    void modifiedTokenIsRejected() {
        JwtService service = new JwtService("scorevault-unit-test-secret-with-at-least-32-bytes", 30);
        String token = service.createToken(42, "JUDGE", "Test Judge");

        assertThrows(JwtException.class, () -> service.parse(token + "x"));
    }
}
