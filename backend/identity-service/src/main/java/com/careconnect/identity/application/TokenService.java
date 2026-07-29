package com.careconnect.identity.application;

import com.careconnect.identity.domain.Role;
import com.careconnect.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and parses tokens. Two different kinds by design:
 *  - access token: signed JWT, self-contained claims, 15 min, never stored
 *  - refresh token: 256-bit random opaque string; only its SHA-256 hash is
 *    persisted. Opaque because the server looks it up anyway (revocation),
 *    so JWT structure would add nothing but size.
 */
@Service
public class TokenService {

    private final JwtProperties properties;
    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", user.getRoles().stream().map(Role::getName).sorted().toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(properties.accessTtlMinutes()))))
                .signWith(key)
                .compact();
    }

    /** @return claims if the token is valid and unexpired */
    public Claims parse(String jwt) throws JwtException {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(jwt)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    public List<String> roles(Claims claims) {
        return claims.get("roles", List.class);
    }

    public String newOpaqueRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public Duration refreshTtl() {
        return Duration.ofDays(properties.refreshTtlDays());
    }
}
