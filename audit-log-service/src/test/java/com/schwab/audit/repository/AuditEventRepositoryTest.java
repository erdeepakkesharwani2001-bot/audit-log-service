package com.schwab.audit.repository;

import com.schwab.audit.entity.AuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AuditEventRepository using H2 database.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("AuditEventRepository Integration Tests")
class AuditEventRepositoryTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    private AuditEvent event1;
    private AuditEvent event2;
    private AuditEvent event3;

    @BeforeEach
    void setUp() {
        // Create test events
        event1 = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .payload("{\"ip\": \"192.168.1.1\"}")
                .timestamp(LocalDateTime.now().minusHours(2))
                .contentHash("hash1")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();

        event2 = AuditEvent.builder()
                .eventType("RECORD_UPDATED")
                .actorId("actor2")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .payload("{\"field\": \"email\"}")
                .timestamp(LocalDateTime.now().minusHours(1))
                .contentHash("hash2")
                .previousHash("hash1")
                .chainPosition(2L)
                .archived(false)
                .build();

        event3 = AuditEvent.builder()
                .eventType("RECORD_UPDATED")
                .actorId("actor1")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .payload("{\"field\": \"name\"}")
                .timestamp(LocalDateTime.now())
                .contentHash("hash3")
                .previousHash("hash2")
                .chainPosition(3L)
                .archived(true)
                .archivedAt(LocalDateTime.now())
                .build();

        auditEventRepository.saveAll(List.of(event1, event2, event3));
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find event by chain position")
    void testFindByChainPosition() {
        // When
        Optional<AuditEvent> found = auditEventRepository.findByChainPosition(2L);

        // Then
        assertTrue(found.isPresent());
        assertEquals("RECORD_UPDATED", found.get().getEventType());
        assertEquals("actor2", found.get().getActorId());
    }

    @Test
    @DisplayName("Should return empty for non-existent chain position")
    void testFindByChainPosition_NotFound() {
        // When
        Optional<AuditEvent> found = auditEventRepository.findByChainPosition(999L);

        // Then
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Should find events by resource")
    void testFindByResourceTypeAndResourceId() {
        // When
        Page<AuditEvent> found = auditEventRepository.findByResourceTypeAndResourceId(
                "ACCOUNT", "account1", PageRequest.of(0, 10));

        // Then
        assertEquals(2, found.getTotalElements());
        assertEquals(2, found.getContent().size());
    }

    @Test
    @DisplayName("Should find events by actor")
    void testFindByActorId() {
        // When
        Page<AuditEvent> found = auditEventRepository.findByActorId("actor1", PageRequest.of(0, 10));

        // Then
        assertEquals(2, found.getTotalElements());
    }

    @Test
    @DisplayName("Should find events by event type")
    void testFindByEventType() {
        // When
        Page<AuditEvent> found = auditEventRepository.findByEventType("RECORD_UPDATED", PageRequest.of(0, 10));

        // Then
        assertEquals(2, found.getTotalElements());
    }

    @Test
    @DisplayName("Should find events by timestamp range")
    void testFindByTimestampRange() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(3);
        LocalDateTime endTime = LocalDateTime.now();

        // When
        Page<AuditEvent> found = auditEventRepository.findByTimestampRange(
                startTime, endTime, PageRequest.of(0, 10));

        // Then
        assertEquals(3, found.getTotalElements());
    }

    @Test
    @DisplayName("Should find unarchived events")
    void testFindByArchivedFalse() {
        // When
        Page<AuditEvent> found = auditEventRepository.findByArchivedFalse(PageRequest.of(0, 10));

        // Then
        assertEquals(2, found.getTotalElements());
        assertTrue(found.getContent().stream().allMatch(e -> !e.getArchived()));
    }

    @Test
    @DisplayName("Should find archived events")
    void testFindByArchivedTrue() {
        // When
        Page<AuditEvent> found = auditEventRepository.findByArchivedTrue(PageRequest.of(0, 10));

        // Then
        assertEquals(1, found.getTotalElements());
        assertEquals("hash3", found.getContent().get(0).getContentHash());
    }

    @Test
    @DisplayName("Should find last event")
    void testFindLastEvent() {
        // When
        Optional<AuditEvent> lastEvent = auditEventRepository.findLastEvent();

        // Then
        assertTrue(lastEvent.isPresent());
        assertEquals(3L, lastEvent.get().getChainPosition());
        assertEquals("hash3", lastEvent.get().getContentHash());
    }

    @Test
    @DisplayName("Should find event by content hash")
    void testFindByContentHash() {
        // When
        Optional<AuditEvent> found = auditEventRepository.findByContentHash("hash2");

        // Then
        assertTrue(found.isPresent());
        assertEquals("RECORD_UPDATED", found.get().getEventType());
        assertEquals("actor2", found.get().getActorId());
    }

    @Test
    @DisplayName("Should count total events")
    void testCountAll() {
        // When
        long count = auditEventRepository.count();

        // Then
        assertEquals(3, count);
    }

    @Test
    @DisplayName("Should count unarchived events")
    void testCountByArchivedFalse() {
        // When
        long count = auditEventRepository.countByArchivedFalse();

        // Then
        assertEquals(2, count);
    }

    @Test
    @DisplayName("Should count archived events")
    void testCountByArchivedTrue() {
        // When
        long count = auditEventRepository.countByArchivedTrue();

        // Then
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Should check if content hash exists")
    void testExistsByContentHash() {
        // When
        boolean exists1 = auditEventRepository.existsByContentHash("hash1");
        boolean exists2 = auditEventRepository.existsByContentHash("nonexistent");

        // Then
        assertTrue(exists1);
        assertFalse(exists2);
    }

    @Test
    @DisplayName("Should find events by resource and event type")
    void testFindByResourceAndEventType() {
        // When
        Page<AuditEvent> found = auditEventRepository.findByResourceAndEventType(
                "ACCOUNT", "account1", "RECORD_UPDATED", PageRequest.of(0, 10));

        // Then
        assertEquals(2, found.getTotalElements());
        assertTrue(found.getContent().stream()
                .allMatch(e -> e.getResourceType().equals("ACCOUNT") &&
                               e.getEventType().equals("RECORD_UPDATED")));
    }

    @Test
    @DisplayName("Should find events by actor and timestamp range")
    void testFindByActorAndTimestampRange() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().minusHours(3);
        LocalDateTime endTime = LocalDateTime.now();

        // When
        Page<AuditEvent> found = auditEventRepository.findByActorAndTimestampRange(
                "actor1", startTime, endTime, PageRequest.of(0, 10));

        // Then
        assertEquals(2, found.getTotalElements());
        assertTrue(found.getContent().stream().allMatch(e -> e.getActorId().equals("actor1")));
    }

    @Test
    @DisplayName("Should support pagination")
    void testPagination() {
        // When
        Page<AuditEvent> page1 = auditEventRepository.findByArchivedFalse(PageRequest.of(0, 1));
        Page<AuditEvent> page2 = auditEventRepository.findByArchivedFalse(PageRequest.of(1, 1));

        // Then
        assertEquals(1, page1.getSize());
        assertEquals(1, page2.getSize());
        assertEquals(2, page1.getTotalElements());
        assertNotEquals(page1.getContent().get(0).getId(), page2.getContent().get(0).getId());
    }

    @Test
    @DisplayName("Should save new event")
    void testSaveNewEvent() {
        // Given
        AuditEvent newEvent = AuditEvent.builder()
                .eventType("NEW_EVENT")
                .actorId("actor3")
                .resourceType("RESOURCE")
                .resourceId("res1")
                .timestamp(LocalDateTime.now())
                .contentHash("newHash")
                .previousHash("hash3")
                .chainPosition(4L)
                .archived(false)
                .build();

        // When
        AuditEvent saved = auditEventRepository.save(newEvent);

        // Then
        assertNotNull(saved.getId());
        Optional<AuditEvent> retrieved = auditEventRepository.findByChainPosition(4L);
        assertTrue(retrieved.isPresent());
        assertEquals("NEW_EVENT", retrieved.get().getEventType());
    }
}
