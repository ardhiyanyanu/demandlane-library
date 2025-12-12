package com.demandline.library.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * JWT Utility Component
 * Handles JWT token creation, validation, and extraction of claims
 */
@Component
@Slf4j
public class JwtUtil {
    Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    @Value("${jwt.secret:your-secret-key-change-this-in-production-at-least-256-bits}")
    private String jwtSecret;
    
    @Value("${jwt.expiration:86400000}") // 24 hours default
    private long jwtExpiration;
    
    /**
     * Generate JWT token
     * @param userId user ID
     * @param email user email
     * @param role user role
     * @param permissions list of permissions
     * @return JWT token string
     */
    public String generateToken(Integer userId, String email, String role, List<String> permissions) {
        logger.debug("Generating JWT token for user: {}", email);
        
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);
        
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }
    
    /**
     * Validate JWT token
     * @param token JWT token
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            logger.error("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Extract claims from token
     * @param token JWT token
     * @return Claims object
     */
    public Claims getClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
    
    /**
     * Extract email from token
     * @param token JWT token
     * @return email
     */
    public String getEmailFromToken(String token) {
        return getClaims(token).getSubject();
    }
    
    /**
     * Extract user ID from token
     * @param token JWT token
     * @return user ID
     */
    public Integer getUserIdFromToken(String token) {
        return getClaims(token).get("userId", Integer.class);
    }
    
    /**
     * Extract role from token
     * @param token JWT token
     * @return role
     */
    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }
    
    /**
     * Extract permissions from token
     * @param token JWT token
     * @return list of permissions
     */
    @SuppressWarnings("unchecked")
    public List<String> getPermissionsFromToken(String token) {
        return getClaims(token).get("permissions", List.class);
    }
}

