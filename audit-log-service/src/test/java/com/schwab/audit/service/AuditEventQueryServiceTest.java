package com.schwab.audit.service;

import com.schwab.audit.dto.request.AuditEventFilterRequest;
import com.schwab.audit.dto.response.AuditEventResponse;
import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AuditEventQueryService advanced filtering.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("AuditEventQueryService Tests")
class AuditEventQueryServiceTest {

    @Autowired
    private AuditEventQueryService queryService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();

        // Create test events
        AuditEvent event1 = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("user1")
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
                .actorId("user2")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .timestamp(LocalDateTime.now())
                .contentHash("hash2")
                .previousHash("hash1")
                .chainPosition(2L)
                .archived(false)
                .build();

        auditEventRepository.saveAll(java.util.List.of(event1, event2));
    }

    @Test
    @DisplayName("Should filter by event type")
    void testFilterByEventType() {
        // Given
        AuditEventFilterRequest filter = AuditEventFilterRequest.builder()
                .eventType("USER_LOGIN")
                .build();

        // When
        Page<AuditEventResponse> result = queryService.executeFilteredQuery(filter);

        // Then
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should filter by actor")
    void testFilterByActor() {
        // Given
        AuditEventFilterRequest filter = AuditEventFilterRequest.builder()
                .actorId("user1")
                .build();

        // When
        Page<?> result = queryService.executeFilteredQuery(filter);

        // Then
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Should return all when no filters")
    void testNoFilters() {
        // Given
        AuditEventFilterRequest filter = AuditEventFilterRequest.builder().build();

        // When
        Page<AuditEventResponse> result = queryService.executeFilteredQuery(filter);

        // Then
        assertEquals(2, result.getTotalElements());
        assertEquals(2L, result.getContent().get(0).getChainPosition());
    }

    @Test
    @DisplayName("Should apply safe defaults when pagination and sorting are omitted")
    void testDefaultsAppliedByBuilder() {
        AuditEventFilterRequest filter = AuditEventFilterRequest.builder().build();

        Page<AuditEventResponse> result = queryService.executeFilteredQuery(filter);

        assertEquals(0, result.getNumber());
        assertEquals(20, result.getSize());
        assertEquals(Sort.Direction.DESC, result.getSort().getOrderFor("chainPosition").getDirection());
    }
}
