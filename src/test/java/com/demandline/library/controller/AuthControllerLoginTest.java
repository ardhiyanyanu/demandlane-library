package com.demandline.library.controller;

import com.demandline.library.security.JwtUtil;
import com.demandline.library.service.UserService;
import com.demandline.library.service.model.Role;
import com.demandline.library.service.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthController login endpoint
 * Tests authentication logic, JWT generation, and error scenarios
 */
@DisplayName("AuthController Login Tests")
class AuthControllerLoginTest {

    @Mock
    private UserService userService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthController authController;

    private String testEmail;
    private String testPassword;
    private String encodedPassword;
    private User testUser;
    private Role testRole;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Test data
        testEmail = "admin@library.local";
        testPassword = "admin123";
        encodedPassword = "$2a$10$slYQmyNdGzin7olVN3p5Be7DlH.PKZbv5H8KnzzVgXXbVxzy.U5dO";

        // Create test role
        testRole = new Role(
            1,
            "ADMIN",
            "[\"ADMIN:CREATE\",\"ADMIN:READ\",\"ADMIN:UPDATE\",\"ADMIN:DELETE\",\"BOOK:CREATE\",\"BOOK:READ\",\"BOOK:UPDATE\",\"BOOK:DELETE\",\"BORROW:READ\",\"BORROW:UPDATE\",\"BORROW:DELETE\",\"MEMBER:READ\",\"MEMBER:UPDATE\"]",
            LocalDateTime.now(),
            LocalDateTime.now()
        );

        // Create test user
        testUser = new User(
            1,
            "Administrator",
            testEmail,
            encodedPassword,
            testRole,
            LocalDateTime.now(),
            LocalDateTime.now(),
            true  // active
        );
    }

    @Test
    @DisplayName("Should return JWT token with correct format on successful login")
    void testLoginSuccessfulWithValidCredentials() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest(testEmail, testPassword);
        String expectedToken = generateTestJwt();

        when(userService.getUserByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(true);
        when(jwtUtil.generateToken(anyInt(), anyString(), anyString(), any())).thenReturn(expectedToken);

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(expectedToken, response.getBody().token());
        assertEquals(testEmail, response.getBody().email());
        assertEquals("ADMIN", response.getBody().role());
        assertTrue(response.getBody().expiresIn() > 0);

        // Verify mocks were called
        verify(userService).getUserByEmail(testEmail);
        verify(passwordEncoder).matches(testPassword, encodedPassword);
        verify(jwtUtil).generateToken(eq(1), eq(testEmail), eq("ADMIN"), any());
    }

    @Test
    @DisplayName("Should validate JWT token structure is correct")
    void testJwtTokenStructureIsValid() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest(testEmail, testPassword);
        
        // Generate actual JWT for validation
        String actualJwt = generateTestJwt();
        
        when(userService.getUserByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(true);
        when(jwtUtil.generateToken(eq(1), eq(testEmail), eq("ADMIN"), any())).thenReturn(actualJwt);

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertNotNull(response.getBody());
        String token = response.getBody().token();
        
        // Verify JWT structure
        assertTrue(token.contains("."), "JWT should have dot separators");
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT should have 3 parts (header.payload.signature)");
        
        // Verify JWT payload contains required claims
        Claims claims = extractClaimsFromToken(token);
        assertEquals(testEmail, claims.getSubject(), "Subject should be email");
        assertEquals(1, claims.get("userId"), "Should contain userId");
        assertEquals("ADMIN", claims.get("role"), "Should contain role");
        assertNotNull(claims.get("permissions"), "Should contain permissions");
        assertNotNull(claims.getIssuedAt(), "Should have issuedAt");
        assertNotNull(claims.getExpiration(), "Should have expiration");
    }

    @Test
    @DisplayName("Should include permissions in JWT token")
    void testJwtTokenIncludesPermissions() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest(testEmail, testPassword);
        String actualJwt = generateTestJwt();
        
        when(userService.getUserByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(true);
        when(jwtUtil.generateToken(eq(1), eq(testEmail), eq("ADMIN"), any())).thenReturn(actualJwt);

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertNotNull(response.getBody());
        String token = response.getBody().token();
        
        Claims claims = extractClaimsFromToken(token);
        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");
        
        assertNotNull(permissions, "Permissions should not be null");
        assertFalse(permissions.isEmpty(), "Permissions should not be empty");
        assertTrue(permissions.contains("ADMIN:CREATE"), "Should contain ADMIN:CREATE permission");
        assertTrue(permissions.contains("BOOK:READ"), "Should contain BOOK:READ permission");
    }

    @Test
    @DisplayName("Should return 401 when user not found")
    void testLoginFailsWhenUserNotFound() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest("unknown@library.local", testPassword);
        when(userService.getUserByEmail("unknown@library.local")).thenReturn(Optional.empty());

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("", response.getBody().token());
        assertEquals("", response.getBody().email());
        assertEquals("", response.getBody().role());
        assertEquals(0L, response.getBody().expiresIn());

        verify(userService).getUserByEmail("unknown@library.local");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtil, never()).generateToken(anyInt(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should return 401 when user account is inactive")
    void testLoginFailsWhenUserIsInactive() {
        // Arrange
        User inactiveUser = new User(
            1,
            "Inactive Admin",
            testEmail,
            encodedPassword,
            testRole,
            LocalDateTime.now(),
            LocalDateTime.now(),
            false  // inactive
        );
        
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest(testEmail, testPassword);
        when(userService.getUserByEmail(testEmail)).thenReturn(Optional.of(inactiveUser));

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("", response.getBody().token());

        verify(userService).getUserByEmail(testEmail);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(jwtUtil, never()).generateToken(anyInt(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should return 401 when password is incorrect")
    void testLoginFailsWithIncorrectPassword() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest(testEmail, "wrongPassword");
        when(userService.getUserByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongPassword", encodedPassword)).thenReturn(false);

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("", response.getBody().token());

        verify(userService).getUserByEmail(testEmail);
        verify(passwordEncoder).matches("wrongPassword", encodedPassword);
        verify(jwtUtil, never()).generateToken(anyInt(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Should return 500 on unexpected exception")
    void testLoginHandlesUnexpectedException() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest(testEmail, testPassword);
        when(userService.getUserByEmail(testEmail)).thenThrow(new RuntimeException("Database error"));

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("", response.getBody().token());
        assertEquals("", response.getBody().email());

        verify(userService).getUserByEmail(testEmail);
    }

    @Test
    @DisplayName("Should extract user ID from JWT token")
    void testJwtTokenContainsUserIdClaim() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest(testEmail, testPassword);
        String actualJwt = generateTestJwt();
        
        when(userService.getUserByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(true);
        when(jwtUtil.generateToken(eq(1), eq(testEmail), eq("ADMIN"), any())).thenReturn(actualJwt);

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertNotNull(response.getBody());
        String token = response.getBody().token();
        Claims claims = extractClaimsFromToken(token);
        
        assertEquals(1, claims.get("userId"), "JWT should contain correct userId");
    }

    @Test
    @DisplayName("Should have correct expiration time in response")
    void testLoginResponseHasCorrectExpiration() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest(testEmail, testPassword);
        String actualJwt = generateTestJwt();
        
        when(userService.getUserByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(true);
        when(jwtUtil.generateToken(eq(1), eq(testEmail), eq("ADMIN"), any())).thenReturn(actualJwt);

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertNotNull(response.getBody());
        long expiresIn = response.getBody().expiresIn();
        
        // Should be 24 hours (86400 seconds)
        assertEquals(86400, expiresIn, "Expiration should be 24 hours (86400 seconds)");
    }

    @Test
    @DisplayName("Should include role name in response")
    void testLoginResponseIncludesRoleName() {
        // Arrange
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest(testEmail, testPassword);
        String actualJwt = generateTestJwt();
        
        when(userService.getUserByEmail(testEmail)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(true);
        when(jwtUtil.generateToken(eq(1), eq(testEmail), eq("ADMIN"), any())).thenReturn(actualJwt);

        // Act
        ResponseEntity<AuthController.LoginResponse> response = authController.login(loginRequest);

        // Assert
        assertNotNull(response.getBody());
        assertEquals("ADMIN", response.getBody().role(), "Response should contain role name");
    }

    // Helper method to generate test JWT
    private String generateTestJwt() {
        String jwtSecret = "your-super-secret-jwt-key-change-this-in-production-use-at-least-256-bits";
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 86400000);
        
        List<String> permissions = Arrays.asList(
            "ADMIN:CREATE", "ADMIN:READ", "ADMIN:UPDATE", "ADMIN:DELETE",
            "BOOK:CREATE", "BOOK:READ", "BOOK:UPDATE", "BOOK:DELETE",
            "BORROW:READ", "BORROW:UPDATE", "BORROW:DELETE",
            "MEMBER:READ", "MEMBER:UPDATE"
        );
        
        return Jwts.builder()
                .subject(testEmail)
                .claim("userId", 1)
                .claim("role", "ADMIN")
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    // Helper method to extract claims from JWT
    private Claims extractClaimsFromToken(String token) {
        String jwtSecret = "your-super-secret-jwt-key-change-this-in-production-use-at-least-256-bits";
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

