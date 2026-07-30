package com.zhishiyun.kb.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    public String createAccessToken(Long userId, String roleCode) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProperties.getAccessTokenMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .setIssuer(jwtProperties.getIssuer())
                .setSubject(String.valueOf(userId))
                .claim("role", roleCode)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
