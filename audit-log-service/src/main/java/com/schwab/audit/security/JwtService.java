package com.schwab.audit.security;

import com.schwab.audit.entity.User;
import com.schwab.audit.util.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Service for JWT token generation, validation, and claims extraction.
 *
 * Compatible with JJWT 0.12.x.
 */
@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiry-hours:24}")
    private long jwtExpiryHours;

    /**
     * Generates a JWT token for the authenticated user.
     *
     * @param user authenticated user
     * @return JWT token
     */
    public String generateToken(User user) {
        try {
            Instant now = Instant.now();
            Instant expiryTime =
                    now.plus(jwtExpiryHours, ChronoUnit.HOURS);

            SecretKey key = getSigningKey();

            return Jwts.builder()
                    .subject(user.getUsername())
                    .claim(
                            Constants.JWT_CLAIM_ROLE,
                            user.getRole().name()
                    )
                    .claim("userId", user.getId())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiryTime))
                    .signWith(key)
                    .compact();

        } catch (Exception e) {
            log.error("Error generating JWT token", e);
            throw new IllegalStateException(
                    "Failed to generate JWT token",
                    e
            );
        }
    }

    /**
     * Validates a JWT token.
     *
     * @param token JWT token
     * @return true if token is valid
     */
    public boolean validateToken(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            getClaims(token);
            return true;

        } catch (ExpiredJwtException e) {
            log.debug("JWT token has expired");
            return false;

        } catch (UnsupportedJwtException e) {
            log.debug("JWT token is unsupported");
            return false;

        } catch (MalformedJwtException e) {
            log.debug("JWT token is malformed");
            return false;

        } catch (JwtException e) {
            log.debug("JWT token validation failed");
            return false;

        } catch (IllegalArgumentException e) {
            log.debug("JWT token is invalid");
            return false;
        }
    }

    /**
     * Extracts username from JWT.
     *
     * @param token JWT token
     * @return username
     */
    public String extractUsername(String token) {

        return getClaims(token).getSubject();
    }

    /**
     * Extracts role from JWT.
     *
     * @param token JWT token
     * @return role
     */
    public String extractRole(String token) {

        return getClaims(token)
                .get(Constants.JWT_CLAIM_ROLE, String.class);
    }

    /**
     * Extracts user ID from JWT.
     *
     * @param token JWT token
     * @return user ID
     */
    public Long extractUserId(String token) {

        return getClaims(token)
                .get("userId", Long.class);
    }

    /**
     * Returns the number of seconds remaining until expiry.
     *
     * @param token JWT token
     * @return seconds remaining
     */
    public long extractExpirySeconds(String token) {

        Date expiry = getClaims(token).getExpiration();

        return (expiry.getTime() - System.currentTimeMillis()) / 1000;
    }

    /**
     * Parses and validates JWT claims.
     *
     * JJWT 0.12.x API uses:
     *
     * Jwts.parser()
     *     .verifyWith(key)
     *     .build()
     *     .parseSignedClaims(token)
     */
    private Claims getClaims(String token) {

        try {
            SecretKey key = getSigningKey();

            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

        } catch (ExpiredJwtException e) {
            throw e;

        } catch (JwtException e) {
            throw new IllegalArgumentException(
                    "Invalid JWT token",
                    e
            );
        }
    }

    /**
     * Creates the HMAC signing key.
     *
     * JJWT requires a sufficiently long secret for HS256.
     */
    private SecretKey getSigningKey() {

        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret is not configured"
            );
        }

        byte[] keyBytes =
                jwtSecret.getBytes(StandardCharsets.UTF_8);

        return Keys.hmacShaKeyFor(keyBytes);
    }
}