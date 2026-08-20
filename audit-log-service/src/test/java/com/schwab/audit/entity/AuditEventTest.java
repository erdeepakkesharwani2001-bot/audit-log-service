package com.schwab.audit.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuditEvent entity.
 */
@DisplayName("AuditEvent Entity Tests")
class AuditEventTest {

    @Test
    @DisplayName("Should create AuditEvent with builder")
    void testCreateAuditEvent() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        // When
        AuditEvent event = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("user123")
                .resourceType("USER_SESSION")
                .resourceId("session456")
                .payload("{\"ip\": \"192.168.1.1\"}")
                .timestamp(now)
                .contentHash("abc123def456")
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(false)
                .build();

        // Then
        assertNotNull(event);
        assertEquals("USER_LOGIN", event.getEventType());
        assertEquals("user123", event.getActorId());
        assertEquals("USER_SESSION", event.getResourceType());
        assertEquals("session456", event.getResourceId());
        assertEquals("{\"ip\": \"192.168.1.1\"}", event.getPayload());
        assertEquals(now, event.getTimestamp());
        assertEquals("abc123def456", event.getContentHash());
        assertEquals("GENESIS_HASH", event.getPreviousHash());
        assertEquals(1L, event.getChainPosition());
        assertFalse(event.getArchived());
    }

    @Test
    @DisplayName("Should detect genesis event correctly")
    void testIsGenesis() {
        // Given
        AuditEvent genesisEvent = AuditEvent.builder()
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .build();

        AuditEvent nonGenesisEvent = AuditEvent.builder()
                .previousHash("abc123def456")
                .chainPosition(2L)
                .build();

        // When & Then
        assertTrue(genesisEvent.isGenesis());
        assertFalse(nonGenesisEvent.isGenesis());
    }

    @Test
    @DisplayName("Should detect redacted event correctly")
    void testIsRedacted() {
        // Given
        AuditEvent redactedEvent = AuditEvent.builder()
                .redactionMetadata("{\"fields\": [\"email\"]}")
                .chainPosition(1L)
                .build();

        AuditEvent notRedactedEvent = AuditEvent.builder()
                .redactionMetadata(null)
                .chainPosition(2L)
                .build();

        AuditEvent emptyRedactionEvent = AuditEvent.builder()
                .redactionMetadata("")
                .chainPosition(3L)
                .build();

        // When & Then
        assertTrue(redactedEvent.isRedacted());
        assertFalse(notRedactedEvent.isRedacted());
        assertFalse(emptyRedactionEvent.isRedacted());
    }

    @Test
    @DisplayName("Should mark event as archived")
    void testMarkAsArchived() {
        // Given
        AuditEvent event = AuditEvent.builder()
                .archived(false)
                .chainPosition(1L)
                .build();

        // When
        event.markAsArchived();

        // Then
        assertTrue(event.getArchived());
        assertNotNull(event.getArchivedAt());
    }

    @Test
    @DisplayName("Should not change archived status when already archived")
    void testMarkAsArchived_AlreadyArchived() {
        // Given
        LocalDateTime firstArchiveTime = LocalDateTime.now().minusHours(1);
        AuditEvent event = AuditEvent.builder()
                .archived(true)
                .archivedAt(firstArchiveTime)
                .chainPosition(1L)
                .build();

        // When
        event.markAsArchived();

        // Then
        assertTrue(event.getArchived());
        assertEquals(firstArchiveTime, event.getArchivedAt());  // Should not change
    }

    @Test
    @DisplayName("Should set archived flag to false by default in @PrePersist")
    void testPrePersist_DefaultArchivedFlag() {
        // Given
        AuditEvent event = AuditEvent.builder()
                .eventType("TEST")
                .actorId("actor1")
                .resourceType("RESOURCE")
                .resourceId("res1")
                .chainPosition(1L)
                // Don't set archived or timestamp
                .build();

        // When
        event.onCreate();

        // Then
        assertFalse(event.getArchived());
        assertNotNull(event.getTimestamp());
    }

    @Test
    @DisplayName("Should keep provided timestamp in @PrePersist")
    void testPrePersist_ProvidedTimestamp() {
        // Given
        LocalDateTime providedTime = LocalDateTime.now().minusHours(1);
        AuditEvent event = AuditEvent.builder()
                .eventType("TEST")
                .actorId("actor1")
                .resourceType("RESOURCE")
                .resourceId("res1")
                .timestamp(providedTime)
                .chainPosition(1L)
                .build();

        // When
        event.onCreate();

        // Then
        assertEquals(providedTime, event.getTimestamp());
    }

    @Test
    @DisplayName("Should handle all event types")
    void testVariousEventTypes() {
        // Given
        String[] eventTypes = {
            "USER_LOGIN", "USER_LOGOUT", "RECORD_UPDATED", "RECORD_DELETED",
            "PERMISSION_GRANTED", "PERMISSION_REVOKED", "FIELD_REDACTED", "RECORD_ARCHIVED"
        };

        // When & Then
        for (String eventType : eventTypes) {
            AuditEvent event = AuditEvent.builder()
                    .eventType(eventType)
                    .actorId("actor1")
                    .resourceType("RESOURCE")
                    .resourceId("res1")
                    .chainPosition(1L)
                    .build();

            assertEquals(eventType, event.getEventType());
        }
    }

    @Test
    @DisplayName("Should handle JSON payload")
    void testJsonPayload() {
        // Given
        String complexJson = "{\"user_id\": 123, \"action\": \"login\", \"ip\": \"192.168.1.1\", \"timestamp\": \"2024-01-01T12:00:00\"}";

        AuditEvent event = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER")
                .resourceId("user1")
                .payload(complexJson)
                .chainPosition(1L)
                .build();

        // When & Then
        assertEquals(complexJson, event.getPayload());
    }

    @Test
    @DisplayName("Should handle chain position increments")
    void testChainPositionSequence() {
        // Given
        AuditEvent event1 = AuditEvent.builder()
                .chainPosition(1L)
                .previousHash("GENESIS_HASH")
                .build();

        AuditEvent event2 = AuditEvent.builder()
                .chainPosition(2L)
                .previousHash("hash1")
                .build();

        AuditEvent event3 = AuditEvent.builder()
                .chainPosition(3L)
                .previousHash("hash2")
                .build();

        // When & Then
        assertEquals(1L, event1.getChainPosition());
        assertEquals(2L, event2.getChainPosition());
        assertEquals(3L, event3.getChainPosition());
        assertEquals(event2.getPreviousHash(), "hash1");  // Points to previous
    }
}
