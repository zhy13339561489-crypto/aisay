package com.aisay.manga.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil(
            "aisay-manga-local-development-secret-key-please-change-in-production",
            86_400_000L
    );

    @Test
    void generateValidateAndParseToken() {
        String token = jwtUtil.generateToken(1001L, "alice");

        assertTrue(jwtUtil.validateToken(token));
        assertEquals(1001L, jwtUtil.getUserIdFromToken(token));
        assertEquals("alice", jwtUtil.getUsernameFromToken(token));
    }

    @Test
    void invalidTokenShouldReturnFalse() {
        assertFalse(jwtUtil.validateToken("not-a-valid-token"));
    }
}
