package com.schwab.audit.security;

import com.schwab.audit.entity.User;
import com.schwab.audit.entity.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtService.
 * 
 * Tests JWT token generation, validation, and claims extraction.
 */
@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("JwtService Tests")
class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .password("hashedPassword")
                .role(UserRole.AUDITOR)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should generate valid JWT token")
    void testGenerateToken() {
        // When
        String token = jwtService.generateToken(testUser);

        // Then
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains("."));  // JWT format: header.payload.signature
    }

    @Test
    @DisplayName("Should validate a valid token")
    void testValidateToken_ValidToken() {
        // Given
        String token = jwtService.generateToken(testUser);

        // When
        boolean isValid = jwtService.validateToken(token);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should reject null token")
    void testValidateToken_NullToken() {
        // When
        boolean isValid = jwtService.validateToken(null);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject empty token")
    void testValidateToken_EmptyToken() {
        // When
        boolean isValid = jwtService.validateToken("");

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject malformed token")
    void testValidateToken_MalformedToken() {
        // When
        boolean isValid = jwtService.validateToken("invalid.token.format");

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should extract username from token")
    void testExtractUsername() {
        // Given
        String token = jwtService.generateToken(testUser);

        // When
        String username = jwtService.extractUsername(token);

        // Then
        assertEquals("testuser", username);
    }

    @Test
    @DisplayName("Should extract role from token")
    void testExtractRole() {
        // Given
        String token = jwtService.generateToken(testUser);

        // When
        String role = jwtService.extractRole(token);

        // Then
        assertEquals("AUDITOR", role);
    }

    @Test
    @DisplayName("Should extract expiry seconds from token")
    void testExtractExpirySeconds() {
        // Given
        String token = jwtService.generateToken(testUser);

        // When
        long expirySeconds = jwtService.extractExpirySeconds(token);

        // Then
        assertTrue(expirySeconds > 0);
        assertTrue(expirySeconds <= 24 * 3600);  // Should be <= 24 hours
    }

    @Test
    @DisplayName("Should throw exception for invalid token in extractUsername")
    void testExtractUsername_InvalidToken() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            jwtService.extractUsername("invalid.token")
        );
    }

    @Test
    @DisplayName("Should throw exception for invalid token in extractRole")
    void testExtractRole_InvalidToken() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            jwtService.extractRole("invalid.token")
        );
    }

    @Test
    @DisplayName("Should generate tokens with different usernames")
    void testGenerateToken_DifferentUsers() {
        // Given
        User user1 = User.builder()
                .id(1L)
                .username("user1")
                .password("pass1")
                .role(UserRole.AUDITOR)
                .createdAt(LocalDateTime.now())
                .build();

        User user2 = User.builder()
                .id(2L)
                .username("user2")
                .password("pass2")
                .role(UserRole.AUDIT_WRITER)
                .createdAt(LocalDateTime.now())
                .build();

        // When
        String token1 = jwtService.generateToken(user1);
        String token2 = jwtService.generateToken(user2);

        // Then
        assertNotEquals(token1, token2);
        assertEquals("user1", jwtService.extractUsername(token1));
        assertEquals("user2", jwtService.extractUsername(token2));
        assertEquals("AUDITOR", jwtService.extractRole(token1));
        assertEquals("AUDIT_WRITER", jwtService.extractRole(token2));
    }

    @Test
    @DisplayName("Should generate tokens with different roles")
    void testGenerateToken_DifferentRoles() {
        // Given
        User adminUser = User.builder()
                .id(1L)
                .username("admin")
                .password("pass")
                .role(UserRole.ADMIN)
                .createdAt(LocalDateTime.now())
                .build();

        User writerUser = User.builder()
                .id(2L)
                .username("writer")
                .password("pass")
                .role(UserRole.AUDIT_WRITER)
                .createdAt(LocalDateTime.now())
                .build();

        // When
        String adminToken = jwtService.generateToken(adminUser);
        String writerToken = jwtService.generateToken(writerUser);

        // Then
        assertTrue(jwtService.validateToken(adminToken));
        assertTrue(jwtService.validateToken(writerToken));
        assertEquals("ADMIN", jwtService.extractRole(adminToken));
        assertEquals("AUDIT_WRITER", jwtService.extractRole(writerToken));
    }
}
