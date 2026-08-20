package com.schwab.audit.service;

import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import com.schwab.audit.util.Constants;
import com.schwab.audit.util.HashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ChainVerificationService.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("ChainVerificationService Integration Tests")
class ChainVerificationServiceTest {

    @Autowired
    private ChainVerificationService chainVerificationService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    private AuditEvent event1;
    private AuditEvent event2;
    private AuditEvent event3;

    @Autowired
    private HashUtils hashUtils;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();

        // Create a valid chain
        LocalDateTime timestamp1 = LocalDateTime.now();
        LocalDateTime timestamp2 = timestamp1.plusSeconds(1);
        LocalDateTime timestamp3 = timestamp2.plusSeconds(1);

        event1 = AuditEvent.builder()
                .eventType("USER_LOGIN")
                .actorId("actor1")
                .resourceType("USER_SESSION")
                .resourceId("session1")
                .payload("{\"ip\": \"192.168.1.1\"}")
                .timestamp(timestamp1)
                .previousHash(Constants.GENESIS_HASH)
                .chainPosition(1L)
                .archived(false)
                .build();
        event1.setContentHash(hashFor(event1));

        event2 = AuditEvent.builder()
                .eventType("RECORD_UPDATED")
                .actorId("actor2")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .payload("{\"field\": \"email\"}")
                .timestamp(timestamp2)
                .previousHash(event1.getContentHash())
                .chainPosition(2L)
                .archived(false)
                .build();
        event2.setContentHash(hashFor(event2));

        event3 = AuditEvent.builder()
                .eventType("RECORD_UPDATED")
                .actorId("actor1")
                .resourceType("ACCOUNT")
                .resourceId("account1")
                .payload("{\"field\": \"name\"}")
                .timestamp(timestamp3)
                .previousHash(event2.getContentHash())
                .chainPosition(3L)
                .archived(false)
                .build();
        event3.setContentHash(hashFor(event3));

        auditEventRepository.saveAll(java.util.List.of(event1, event2, event3));
    }

    @Test
    @DisplayName("Should verify valid chain")
    void testVerifyCompleteChain_Valid() {
        // When
        boolean isValid = chainVerificationService.verifyCompleteChain();

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should return true for empty chain")
    void testVerifyCompleteChain_Empty() {
        // Given
        auditEventRepository.deleteAll();

        // When
        boolean isValid = chainVerificationService.verifyCompleteChain();

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should verify single event (genesis)")
    void testVerifyEvent_Genesis() {
        // When
        boolean isValid = chainVerificationService.verifyEvent(event1);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should verify middle event in chain")
    void testVerifyEvent_Middle() {
        // When
        boolean isValid = chainVerificationService.verifyEvent(event2);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should verify last event in chain")
    void testVerifyEvent_Last() {
        // When
        boolean isValid = chainVerificationService.verifyEvent(event3);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should reject null event")
    void testVerifyEvent_Null() {
        // When
        boolean isValid = chainVerificationService.verifyEvent(null);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject event with invalid content hash length")
    void testVerifyEvent_InvalidHashLength() {
        // Given
        AuditEvent invalidEvent = AuditEvent.builder()
                .contentHash("short")
                .previousHash(Constants.GENESIS_HASH)
                .chainPosition(1L)
                .build();

        // When
        boolean isValid = chainVerificationService.verifyEvent(invalidEvent);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should reject genesis event not at position 1")
    void testVerifyEvent_GenesisNotAtPosition1() {
        // Given
        AuditEvent invalidEvent = AuditEvent.builder()
                .contentHash("a".repeat(64))
                .previousHash(Constants.GENESIS_HASH)
                .chainPosition(2L)  // Wrong position
                .build();

        // When
        boolean isValid = chainVerificationService.verifyEvent(invalidEvent);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should count valid chain from beginning")
    void testCountValidChainFromBeginning() {
        // When
        long count = chainVerificationService.countValidChainFromBeginning(3);

        // Then
        assertEquals(3, count);
    }

    @Test
    @DisplayName("Should count partial valid chain")
    void testCountValidChainFromBeginning_Partial() {
        // When
        long count = chainVerificationService.countValidChainFromBeginning(2);

        // Then
        assertEquals(2, count);
    }

    @Test
    @DisplayName("Should detect missing event in chain")
    void testVerifyCompleteChain_MissingEvent() {
        // Given - Delete event2 from the chain
        auditEventRepository.delete(event2);

        // When
        boolean isValid = chainVerificationService.verifyCompleteChain();

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should verify event content hash")
    void testVerifyEventContentHash() {
        // When - Reconstruct the hash
        String expectedHash = event1.getContentHash();
        boolean isValid = chainVerificationService.verifyEventContentHash(event1, expectedHash);

        // Then
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Should reject mismatched content hash")
    void testVerifyEventContentHash_Mismatch() {
        // When
        String expectedHash = "wronghash";
        boolean isValid = chainVerificationService.verifyEventContentHash(event1, expectedHash);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should handle event with null previous hash")
    void testVerifyEvent_NullPreviousHash() {
        // Given
        AuditEvent invalidEvent = AuditEvent.builder()
                .contentHash("a".repeat(64))
                .previousHash(null)
                .chainPosition(1L)
                .build();

        // When
        boolean isValid = chainVerificationService.verifyEvent(invalidEvent);

        // Then
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Should verify event at specific position")
    void testVerifyEventAtPosition() {
        // When
        long validCount = chainVerificationService.countValidChainFromBeginning(2);

        // Then - Should have verified first 2 events
        assertEquals(2, validCount);
    }

    private String hashFor(AuditEvent event) {
        return hashUtils.computeSha256(String.format("%s|%s|%s|%s|%s|%s",
                event.getEventType(), event.getActorId(), event.getResourceType(), event.getResourceId(),
                event.getPayload() == null ? "" : event.getPayload(), event.getTimestamp()));
    }
}
