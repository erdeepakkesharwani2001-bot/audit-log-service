package com.schwab.audit.service;

import com.schwab.audit.dto.request.CreateAuditEventRequest;
import com.schwab.audit.dto.response.AuditEventResponse;
import com.schwab.audit.repository.AuditEventRepository;
import com.schwab.audit.util.Constants;
import com.schwab.audit.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AuditEventService.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AuditEventService Integration Tests")
class AuditEventServiceTest {

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private HashUtils hashUtils;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create first audit event with genesis hash")
    void testCreateFirstAuditEvent() {
        // Given
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .payload("{\"ip\": \"192.168.1.1\"}")
                .build();

        // When
        AuditEventResponse response = auditEventService.createAuditEvent(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals("USER_LOGIN", response.getEventType());
        assertEquals("user1", response.getActorId());
        assertEquals(1L, response.getChainPosition());
        assertEquals(Constants.GENESIS_HASH, response.getPreviousHash());
        assertNotNull(response.getContentHash());
    }

    @Test
    @DisplayName("Should create second audit event with correct chain")
    void testCreateSecondAuditEvent() {
        // Given
        CreateAuditEventRequest request1 = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .payload("{\"ip\": \"192.168.1.1\"}")
                .build();

        AuditEventResponse event1 = auditEventService.createAuditEvent(request1);

        CreateAuditEventRequest request2 = CreateAuditEventRequest.builder()
                .eventType("RECORD_UPDATED")
                .actorId("user1")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .payload("{\"field\": \"email\"}")
                .build();

        // When
        AuditEventResponse event2 = auditEventService.createAuditEvent(request2);

        // Then
        assertNotNull(event2);
        assertEquals(2L, event2.getChainPosition());
        assertEquals(event1.getContentHash(), event2.getPreviousHash());
    }

    @Test
    @DisplayName("Should reject duplicate event")
    void testRejectDuplicateEvent() {
        // Given
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .payload("{\"ip\": \"192.168.1.1\"}")
                .timestamp(LocalDateTime.of(2024, 1, 1, 12, 0, 0))
                .build();

        auditEventService.createAuditEvent(request);

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            auditEventService.createAuditEvent(request)
        );
    }

    @Test
    @DisplayName("Should retrieve all events with pagination")
    void testGetAllAuditEvents() {
        // Given
        CreateAuditEventRequest request1 = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        CreateAuditEventRequest request2 = CreateAuditEventRequest.builder()
                .eventType("RECORD_UPDATED")
                .actorId("user2")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .build();

        auditEventService.createAuditEvent(request1);
        auditEventService.createAuditEvent(request2);

        // When
        Page<AuditEventResponse> events = auditEventService.getAllAuditEvents(PageRequest.of(0, 10));

        // Then
        assertEquals(2, events.getTotalElements());
        assertEquals(2, events.getContent().size());
    }

    @Test
    @DisplayName("Should retrieve event by ID")
    void testGetAuditEventById() {
        // Given
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        AuditEventResponse created = auditEventService.createAuditEvent(request);

        // When
        var detail = auditEventService.getAuditEventById(created.getId());

        // Then
        assertNotNull(detail);
        assertEquals(created.getId(), detail.getId());
        assertEquals("USER_LOGIN", detail.getEventType());
        assertTrue(detail.getIsGenesis());
    }

    @Test
    @DisplayName("Should query events by resource")
    void testGetEventsByResource() {
        // Given
        CreateAuditEventRequest request1 = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        CreateAuditEventRequest request2 = CreateAuditEventRequest.builder()
                .eventType("RECORD_UPDATED")
                .actorId("user1")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .build();

        auditEventService.createAuditEvent(request1);
        auditEventService.createAuditEvent(request2);

        // When
        Page<AuditEventResponse> events = auditEventService.getEventsByResource("USER_SESSION", "session1", PageRequest.of(0, 10));

        // Then
        assertEquals(1, events.getTotalElements());
        assertEquals("USER_LOGIN", events.getContent().get(0).getEventType());
    }

    @Test
    @DisplayName("Should query events by actor")
    void testGetEventsByActor() {
        // Given
        CreateAuditEventRequest request1 = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        CreateAuditEventRequest request2 = CreateAuditEventRequest.builder()
                .eventType("RECORD_UPDATED")
                .actorId("user2")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .build();

        auditEventService.createAuditEvent(request1);
        auditEventService.createAuditEvent(request2);

        // When
        Page<AuditEventResponse> events = auditEventService.getEventsByActor("user1", PageRequest.of(0, 10));

        // Then
        assertEquals(1, events.getTotalElements());
    }

    @Test
    @DisplayName("Should query events by type")
    void testGetEventsByType() {
        // Given
        CreateAuditEventRequest request1 = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        CreateAuditEventRequest request2 = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user2")
                .resourceType("USER_SESSION")
                .resourceId("session2")
                .build();

        auditEventService.createAuditEvent(request1);
        auditEventService.createAuditEvent(request2);

        // When
        Page<AuditEventResponse> events = auditEventService.getEventsByType("USER_LOGIN", PageRequest.of(0, 10));

        // Then
        assertEquals(2, events.getTotalElements());
    }

    @Test
    @DisplayName("Should verify chain integrity")
    void testVerifyChainIntegrity() {
        // Given
        CreateAuditEventRequest request1 = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        CreateAuditEventRequest request2 = CreateAuditEventRequest.builder()
                .eventType("RECORD_UPDATED")
                .actorId("user1")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .build();

        auditEventService.createAuditEvent(request1);
        auditEventService.createAuditEvent(request2);

        // When
        boolean isValid = auditEventService.verifyChainIntegrity();

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should verify event integrity")
    void testVerifyEventIntegrity() {
        // Given
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        AuditEventResponse created = auditEventService.createAuditEvent(request);

        // When
        boolean isValid = auditEventService.verifyEventIntegrity(created.getId());

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should archive event")
    void testArchiveEvent() {
        // Given
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        AuditEventResponse created = auditEventService.createAuditEvent(request);

        // When
        AuditEventResponse archived = auditEventService.archiveAuditEvent(created.getId());

        // Then
        assertNotNull(archived);
        assertTrue(archived.getArchived());
        assertNotNull(archived.getArchivedAt());
    }

    @Test
    @DisplayName("Should return total event count")
    void testGetTotalEventCount() {
        // Given
        CreateAuditEventRequest request1 = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .build();

        CreateAuditEventRequest request2 = CreateAuditEventRequest.builder()
                .eventType("RECORD_UPDATED")
                .actorId("user1")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .build();

        auditEventService.createAuditEvent(request1);
        auditEventService.createAuditEvent(request2);

        // When
        long count = auditEventService.getTotalEventCount();

        // Then
        assertEquals(2, count);
    }

    @Test
    @DisplayName("Should create event with custom timestamp")
    void testCreateEventWithCustomTimestamp() {
        // Given
        LocalDateTime customTime = LocalDateTime.of(2024, 1, 1, 12, 30, 0);
        CreateAuditEventRequest request = CreateAuditEventRequest.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .timestamp(customTime)
                .build();

        // When
        AuditEventResponse response = auditEventService.createAuditEvent(request);

        // Then
        assertEquals(customTime, response.getTimestamp());
    }
}
