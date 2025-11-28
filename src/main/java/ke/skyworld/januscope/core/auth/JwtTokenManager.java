package ke.skyworld.januscope.core.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import ke.skyworld.januscope.domain.models.User;
import ke.skyworld.januscope.utils.Logger;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT Token Manager
 * Generates and verifies JWT tokens
 */
public class JwtTokenManager {
    private static final Logger logger = Logger.getLogger(JwtTokenManager.class);
    
    private final SecretKey secretKey;
    private static final long ACCESS_TOKEN_VALIDITY_MINUTES = 15; // 15 minutes
    private static final long REFRESH_TOKEN_VALIDITY_DAYS = 7; // 7 days
    private static final long PASSWORD_RESET_TOKEN_VALIDITY_HOURS = 1; // 1 hour
    
    public JwtTokenManager(String secret) {
        // Ensure secret is at least 256 bits (32 bytes) for HS256
        if (secret.length() < 32) {
            secret = String.format("%-32s", secret).replace(' ', '0');
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Generate access token (short-lived)
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(ACCESS_TOKEN_VALIDITY_MINUTES, ChronoUnit.MINUTES);
        
        return Jwts.builder()
            .setSubject(user.getUserId().toString())
            .claim("email", user.getEmail())
            .claim("username", user.getUsername())
            .claim("role", user.getRoleName())
            .claim("type", "access")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    /**
     * Generate refresh token (long-lived)
     */
    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(REFRESH_TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS);
        
        return Jwts.builder()
            .setSubject(user.getUserId().toString())
            .claim("type", "refresh")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    /**
     * Verify access token and return user ID
     */
    public Integer verifyAccessToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            String type = claims.get("type", String.class);
            if (!"access".equals(type)) {
                logger.warn("Invalid token type: {}", type);
                return null;
            }
            
            return Integer.parseInt(claims.getSubject());
            
        } catch (ExpiredJwtException e) {
            logger.debug("Access token expired");
            return null;
        } catch (JwtException e) {
            logger.warn("Invalid access token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Verify refresh token and return user ID
     */
    public Integer verifyRefreshToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            String type = claims.get("type", String.class);
            if (!"refresh".equals(type)) {
                logger.warn("Invalid token type: {}", type);
                return null;
            }
            
            return Integer.parseInt(claims.getSubject());
            
        } catch (ExpiredJwtException e) {
            logger.debug("Refresh token expired");
            return null;
        } catch (JwtException e) {
            logger.warn("Invalid refresh token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Generate password reset token (short-lived, 1 hour)
     */
    public String generatePasswordResetToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(PASSWORD_RESET_TOKEN_VALIDITY_HOURS, ChronoUnit.HOURS);
        
        return Jwts.builder()
            .setSubject(user.getUserId().toString())
            .claim("email", user.getEmail())
            .claim("type", "password_reset")
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }
    
    /**
     * Verify password reset token and return user ID
     */
    public Integer verifyPasswordResetToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            String type = claims.get("type", String.class);
            if (!"password_reset".equals(type)) {
                logger.warn("Invalid token type: {}", type);
                return null;
            }
            
            return Integer.parseInt(claims.getSubject());
            
        } catch (ExpiredJwtException e) {
            logger.debug("Password reset token expired");
            return null;
        } catch (JwtException e) {
            logger.warn("Invalid password reset token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract user ID from token without verification (for logging)
     */
    public Integer extractUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
            // Simple extraction - in production use proper JSON parsing
            return null;
            
        } catch (Exception e) {
            return null;
        }
    }
}
