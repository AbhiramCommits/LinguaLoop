package com.lingualoop.api.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.lingualoop.api.learner.Learner;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.secret() == null || properties.secret().length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET is not configured (must be at least 32 characters, HS256)");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Learner learner) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(learner.getId().toString())
                .claim("email", learner.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.ttl())))
                .signWith(key)
                .compact();
    }

    public Long parseLearnerId(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }
}
