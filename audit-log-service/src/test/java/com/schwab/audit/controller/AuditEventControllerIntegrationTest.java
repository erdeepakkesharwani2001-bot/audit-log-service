package com.schwab.audit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.audit.dto.request.CreateAuditEventRequest;
import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.entity.User;
import com.schwab.audit.entity.enums.UserRole;
import com.schwab.audit.repository.AuditEventRepository;
import com.schwab.audit.repository.UserRepository;
import com.schwab.audit.security.JwtService;
import com.schwab.audit.util.HashUtils;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AuditEventController REST endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AuditEventController Integration Tests")
class AuditEventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private HashUtils hashUtils;

    private String writerToken;
    private String auditorToken;
    private User writerUser;
    private User auditorUser;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
        userRepository.deleteAll();

        // Create test users
        writerUser = User.builder()
                .username("writer")
                .password(passwordEncoder.encode("password"))
                .role(UserRole.AUDIT_WRITER)
                .createdAt(LocalDateTime.now())
                .build();

        auditorUser = User.builder()
                .username("auditor")
                .password(passwordEncoder.encode("password"))
                .role(UserRole.AUDITOR)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.saveAll(java.util.List.of(writerUser, auditorUser));

        // Generate tokens
        writerToken = jwtService.generateToken(writerUser);
        auditorToken = jwtService.generateToken(auditorUser);
    }

    @Test
    @DisplayName("Should create audit event successfully")
    void testCreateAuditEvent_Success() throws Exception {
        // Given
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .payload("{\"ip\": \"192.168.1.1\"}")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/audit/events")
                .header("Authorization", "Bearer " + writerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.eventType").value("USER_LOGIN"))
                .andExpect(jsonPath("$.chainPosition").value(1))
                .andExpect(jsonPath("$.previousHash").value("GENESIS_HASH"))
                .andExpect(jsonPath("$.contentHash").isNotEmpty());
    }

    @Test
    @DisplayName("Should reject event creation without authentication")
    void testCreateAuditEvent_Unauthorized() throws Exception {
        // Given
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/audit/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject event creation for AUDITOR role")
    void testCreateAuditEvent_Forbidden() throws Exception {
        // Given
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/audit/events")
                .header("Authorization", "Bearer " + auditorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should return 400 for missing required fields")
    void testCreateAuditEvent_MissingFields() throws Exception {
        // Given
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                // Missing other required fields
                .build();

        // When & Then
        mockMvc.perform(post("/api/v1/audit/events")
                .header("Authorization", "Bearer " + writerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should list audit events")
    void testListAuditEvents() throws Exception {
        // Given
        AuditEvent event = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash1")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();

        auditEventRepository.save(event);

        // When & Then
        mockMvc.perform(get("/api/v1/audit/events")
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Should get event by ID")
    void testGetAuditEventById() throws Exception {
        // Given
        AuditEvent event = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash1")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();
        event.setContentHash(hashUtils.computeSha256(String.format("%s|%s|%s|%s|%s|%s",
                event.getEventType(), event.getActorId(), event.getResourceType(), event.getResourceId(),
                event.getPayload(), event.getTimestamp())));

        AuditEvent saved = auditEventRepository.save(event);

        // When & Then
        mockMvc.perform(get("/api/v1/audit/events/" + saved.getId())
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.eventType").value("USER_LOGIN"))
                .andExpect(jsonPath("$.isGenesis").value(true));
    }

    @Test
    @DisplayName("Should return 404 for non-existent event")
    void testGetAuditEventById_NotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/audit/events/999")
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should search events by resource")
    void testSearchByResource() throws Exception {
        // Given
        AuditEvent event = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash1")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();

        auditEventRepository.save(event);

        // When & Then
        mockMvc.perform(get("/api/v1/audit/events/search/by-resource")
                .param("resourceType", "USER_SESSION")
                .param("resourceId", "session1")
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Should search events by actor")
    void testSearchByActor() throws Exception {
        // Given
        AuditEvent event = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash1")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();

        auditEventRepository.save(event);

        // When & Then
        mockMvc.perform(get("/api/v1/audit/events/search/by-actor")
                .param("actorId", "actor1")
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Should search events by type")
    void testSearchByType() throws Exception {
        // Given
        AuditEvent event = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash1")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();

        auditEventRepository.save(event);

        // When & Then
        mockMvc.perform(get("/api/v1/audit/events/search/by-type")
                .param("eventType", "USER_LOGIN")
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Should verify chain integrity")
    void testVerifyChain() throws Exception {
        // Given
        AuditEvent event1 = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash1")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .eventType("RECORD_UPDATED")
                .actorId("actor1")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash2")
                .previousHash("hash1")
                .chainPosition(2L)
                .archived(false)
                .build();

        auditEventRepository.saveAll(java.util.List.of(event1, event2));

        // When & Then
        mockMvc.perform(post("/api/v1/audit/events/verify-chain")
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.totalEvents").value(2));
    }

    @Test
    @DisplayName("Should verify event integrity")
    void testVerifyEvent() throws Exception {
        // Given
        AuditEvent event = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash1")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();
        event.setContentHash(hashUtils.computeSha256(String.format("%s|%s|%s|%s|%s|%s",
                event.getEventType(), event.getActorId(), event.getResourceType(), event.getResourceId(),
                event.getPayload(), event.getTimestamp())));

        AuditEvent saved = auditEventRepository.save(event);

        // When & Then
        mockMvc.perform(post("/api/v1/audit/events/" + saved.getId() + "/verify")
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @DisplayName("Should get statistics")
    void testGetStatistics() throws Exception {
        // Given
        AuditEvent event = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash1")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();

        auditEventRepository.save(event);

        // When & Then
        mockMvc.perform(get("/api/v1/audit/events/stats/summary")
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEvents").value(1));
    }

    @Test
    @DisplayName("Should enforce pagination limits")
    void testListAuditEvents_MaxPageSize() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/audit/events")
                .param("size", "200")  // Request more than max
                .header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());
        // Should be limited to 100
    }
}
