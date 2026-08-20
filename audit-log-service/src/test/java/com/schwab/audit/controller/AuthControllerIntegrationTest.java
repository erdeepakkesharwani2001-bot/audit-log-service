package com.schwab.audit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.audit.dto.request.LoginRequest;
import com.schwab.audit.entity.User;
import com.schwab.audit.entity.enums.UserRole;
import com.schwab.audit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuthController.
 * 
 * Tests JWT login flow and error handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private String testPassword = "TestPassword123!";

    @BeforeEach
    void setUp() {
        // Create a test user
        testUser = User.builder()
                .username("testuser")
                .password(passwordEncoder.encode(testPassword))
                .role(UserRole.AUDITOR)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void testLogin_Success() throws Exception {
        // Given
        LoginRequest loginRequest = LoginRequest.builder()
                .username("testuser")
                .password(testPassword)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.role").value("AUDITOR"));
    }

    @Test
    @DisplayName("Should return 401 for invalid password")
    void testLogin_InvalidPassword() throws Exception {
        // Given
        LoginRequest loginRequest = LoginRequest.builder()
                .username("testuser")
                .password("WrongPassword")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @DisplayName("Should return 401 for non-existent user")
    void testLogin_UserNotFound() throws Exception {
        // Given
        LoginRequest loginRequest = LoginRequest.builder()
                .username("nonexistent")
                .password(testPassword)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("Should return 400 for missing username")
    void testLogin_MissingUsername() throws Exception {
        // Given
        LoginRequest loginRequest = LoginRequest.builder()
                .password(testPassword)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Should return 400 for missing password")
    void testLogin_MissingPassword() throws Exception {
        // Given
        LoginRequest loginRequest = LoginRequest.builder()
                .username("testuser")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Should return 400 for empty username")
    void testLogin_EmptyUsername() throws Exception {
        // Given
        LoginRequest loginRequest = LoginRequest.builder()
                .username("")
                .password(testPassword)
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 for empty password")
    void testLogin_EmptyPassword() throws Exception {
        // Given
        LoginRequest loginRequest = LoginRequest.builder()
                .username("testuser")
                .password("")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return JWT token with correct claims")
    void testLogin_TokenHasCorrectClaims() throws Exception {
        // Given
        LoginRequest loginRequest = LoginRequest.builder()
                .username("testuser")
                .password(testPassword)
                .build();

        // When
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseBody).get("token").asText();

        // Then
        assert(!token.isEmpty());
        // Token should be in format: header.payload.signature (3 parts separated by dots)
        String[] parts = token.split("\\.");
        assert(parts.length == 3);
    }

    @Test
    @DisplayName("Should login with ADMIN role")
    void testLogin_AdminRole() throws Exception {
        // Given
        User adminUser = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("AdminPassword123!"))
                .role(UserRole.ADMIN)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(adminUser);

        LoginRequest loginRequest = LoginRequest.builder()
                .username("admin")
                .password("AdminPassword123!")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    @DisplayName("Should login with AUDIT_WRITER role")
    void testLogin_AuditWriterRole() throws Exception {
        // Given
        User writerUser = User.builder()
                .username("writer")
                .password(passwordEncoder.encode("WriterPassword123!"))
                .role(UserRole.AUDIT_WRITER)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(writerUser);

        LoginRequest loginRequest = LoginRequest.builder()
                .username("writer")
                .password("WriterPassword123!")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("AUDIT_WRITER"));
    }
}
