package com.yef.fota.auth;

import com.yef.fota.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final Key key;
    private final int expireHours;

    public JwtTokenProvider(@Value("${fota.jwt.secret}") String secret,
                            @Value("${fota.jwt.expire-hours:12}") int expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireHours = expireHours;
    }

    public String generateToken(Long userId, String username) {
        LocalDateTime now = LocalDateTime.now();
        Date issuedAt = Date.from(now.atZone(ZoneId.systemDefault()).toInstant());
        Date expiration = Date.from(now.plusHours(expireHours).atZone(ZoneId.systemDefault()).toInstant());
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .setIssuedAt(issuedAt)
                .setExpiration(expiration)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public AuthUser parseToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            Long userId = ((Number) claims.get("userId")).longValue();
            String username = claims.get("username", String.class);
            return new AuthUser(userId, username);
        } catch (Exception ex) {
            throw new BusinessException("登录状态已失效，请重新登录");
        }
    }
}
