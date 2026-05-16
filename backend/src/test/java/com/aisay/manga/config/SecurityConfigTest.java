package com.aisay.manga.config;

import com.aisay.manga.utils.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityConfigTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void protectedProfileEndpointShouldReturnUnauthorizedWithoutToken() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/user/profile",
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertTrue(response.getBody() != null && response.getBody().contains("未认证或登录已过期"));
    }

    @Test
    void jwtUtilBeanShouldValidateGeneratedToken() {
        String token = jwtUtil.generateToken(1L, "security-test");

        assertTrue(jwtUtil.validateToken(token));
        assertEquals(1L, jwtUtil.getUserIdFromToken(token));
        assertEquals("security-test", jwtUtil.getUsernameFromToken(token));
    }
}
