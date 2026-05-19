package com.aisay.manga.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;

    private final long expiration;

    /**
     * 作用：根据配置初始化 JWT 签名密钥和过期时间。
     * 调用方：Spring 容器启动时自动构造 JwtUtil。
     */
    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    /**
     * 作用：为登录成功的用户生成 JWT。
     * 调用方：UserServiceImpl#login。
     */
    public String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 作用：校验 JWT 是否可解析且签名有效。
     * 调用方：JwtAuthenticationFilter#doFilterInternal、ChatWebSocketController#resolveUserId。
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * 作用：从 JWT claims 中读取用户 ID。
     * 调用方：JwtAuthenticationFilter#doFilterInternal、ChatWebSocketController#resolveUserId。
     */
    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    /**
     * 作用：从 JWT claims 中读取用户名，缺失时使用 subject。
     * 调用方：当前预留给需要用户名的鉴权/日志场景。
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseClaims(token);
        String username = claims.get("username", String.class);
        return username != null ? username : claims.getSubject();
    }

    /**
     * 作用：解析并校验 JWT，返回 claims 负载。
     * 调用方：validateToken、getUserIdFromToken、getUsernameFromToken。
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
