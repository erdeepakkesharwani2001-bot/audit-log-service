package com.schwab.audit.service;

import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import com.schwab.audit.util.Constants;
import com.schwab.audit.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Service for verifying the integrity of the audit event hash chain.
 * 
 * Implements SHA-256 hash chain verification to detect tampering:
 * - Each event's previousHash must match the previous event's contentHash
 * - The first event's previousHash must be GENESIS_HASH
 * - Chain positions must be sequential
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ChainVerificationService {

    private final AuditEventRepository auditEventRepository;
    private final HashUtils hashUtils;

    /**
     * Verifies the integrity of the complete hash chain.
     * 
     * Walks through all events in order and verifies:
     * 1. First event has previousHash == GENESIS_HASH
     * 2. Each event's previousHash matches the previous event's contentHash
     * 3. Chain positions are sequential (1, 2, 3, ...)
     * 4. No gaps in the chain
     * 
     * @return true if entire chain is valid, false if any event is tampered with
     */
    public boolean verifyCompleteChain() {
        log.info("Starting complete chain verification");

        long totalEvents = auditEventRepository.count();
        if (totalEvents == 0) {
            log.info("No events to verify - chain is valid (empty)");
            return true;
        }

        AuditEvent previousEvent = null;
        boolean isValid = true;

        for (long position = 1; position <= totalEvents; position++) {
            Optional<AuditEvent> currentEventOpt = auditEventRepository.findByChainPosition(position);

            if (currentEventOpt.isEmpty()) {
                log.error("Chain gap detected at position: {}", position);
                return false;
            }

            AuditEvent currentEvent = currentEventOpt.get();

            // Verify this event
            if (!verifyEventInternal(currentEvent, previousEvent)) {
                log.error("Chain verification failed at position: {}", position);
                return false;
            }

            previousEvent = currentEvent;
        }

        log.info("Complete chain verification successful - {} events verified", totalEvents);
        return true;
    }

    /**
     * Verifies a specific event's integrity in the chain context.
     * 
     * Checks:
     * 1. Event's contentHash is valid (64 hex chars)
     * 2. Event's previousHash is valid (64 hex chars or GENESIS_HASH)
     * 3. If not genesis, previousHash matches previous event
     * 4. Chain position is valid
     * 
     * @param event the event to verify
     * @return true if event is valid, false otherwise
     */
    public boolean verifyEvent(AuditEvent event) {
        if (event == null) {
            log.warn("Cannot verify null event");
            return false;
        }

        log.debug("Verifying event - id: {}, position: {}", event.getId(), event.getChainPosition());

        // Verify content hash format
        if (event.getContentHash() == null || event.getContentHash().length() != Constants.HASH_HEX_LENGTH) {
            log.warn("Invalid content hash format - id: {}", event.getId());
            return false;
        }

        // Verify previous hash format
        if (event.getPreviousHash() == null || 
            (event.getPreviousHash().length() != Constants.HASH_HEX_LENGTH && 
             !Constants.GENESIS_HASH.equals(event.getPreviousHash()))) {
            log.warn("Invalid previous hash format - id: {}", event.getId());
            return false;
        }

        // Get the previous event if this is not the genesis event
        if (!Constants.GENESIS_HASH.equals(event.getPreviousHash())) {
            Optional<AuditEvent> previousEventOpt = auditEventRepository.findByChainPosition(event.getChainPosition() - 1);
            
            if (previousEventOpt.isEmpty()) {
                log.warn("Previous event not found for position: {}", event.getChainPosition());
                return false;
            }

            AuditEvent previousEvent = previousEventOpt.get();
            return verifyEventInternal(event, previousEvent);
        } else {
            // Genesis event - just verify it's at position 1
            if (event.getChainPosition() != 1) {
                log.warn("Genesis event should be at position 1, found at: {}", event.getChainPosition());
                return false;
            }
            return true;
        }
    }

    /**
     * Reconstructs and verifies an event's content hash.
     * 
     * This is useful for verifying that the event content hasn't been modified
     * (though in a read-only database, this is unlikely).
     * 
     * @param event the event to verify
     * @param expectedContentHash the expected content hash
     * @return true if reconstructed hash matches expected hash
     */
    public boolean verifyEventContentHash(AuditEvent event, String expectedContentHash) {
        if (event == null) {
            return false;
        }

        String reconstructedContent = buildEventContent(event);
        String computedHash = hashUtils.computeSha256(reconstructedContent);

        boolean isValid = hashUtils.verifyHash(expectedContentHash, computedHash);
        
        if (!isValid) {
            log.warn("Content hash mismatch for event - id: {}, expected: {}, computed: {}", 
                    event.getId(), expectedContentHash, computedHash);
        }

        return isValid;
    }

    /**
     * Internal verification logic comparing current and previous events.
     * 
     * @param currentEvent the event to verify
     * @param previousEvent the previous event in chain (or null if genesis)
     * @return true if verification passes
     */
    private boolean verifyEventInternal(AuditEvent currentEvent, AuditEvent previousEvent) {
        // Verify chain position sequence
        if (previousEvent == null) {
            // This should be the genesis event (position 1)
            if (currentEvent.getChainPosition() != 1) {
                log.warn("First event should be at position 1, found at: {}", currentEvent.getChainPosition());
                return false;
            }
            // Genesis event should have previousHash == GENESIS_HASH
            if (!Constants.GENESIS_HASH.equals(currentEvent.getPreviousHash())) {
                log.warn("Genesis event has invalid previousHash: {}", currentEvent.getPreviousHash());
                return false;
            }
            return true;
        }

        // Verify chain continuity
        if (currentEvent.getChainPosition() != previousEvent.getChainPosition() + 1) {
            log.warn("Chain position gap detected - expected: {}, got: {}", 
                    previousEvent.getChainPosition() + 1, currentEvent.getChainPosition());
            return false;
        }

        // Verify previousHash points to previous event's contentHash
        if (!hashUtils.verifyHash(currentEvent.getPreviousHash(), previousEvent.getContentHash())) {
            log.warn("Previous hash mismatch - event id: {}, position: {}", 
                    currentEvent.getId(), currentEvent.getChainPosition());
            return false;
        }

        return true;
    }

    /**
     * Builds a string representation of the event content for verification.
     * This must match the format used in AuditEventService.buildEventContent().
     * 
     * @param event the event
     * @return concatenated event content
     */
    private String buildEventContent(AuditEvent event) {
        return String.format(
                "%s|%s|%s|%s|%s|%s",
                event.getEventType(),
                event.getActorId(),
                event.getResourceType(),
                event.getResourceId(),
                event.getPayload() != null ? event.getPayload() : "",
                event.getTimestamp() != null ? event.getTimestamp().toString() : ""
        );
    }

    /**
     * Counts the number of valid events in the chain from the beginning up to a position.
     * Useful for checking chain integrity up to a specific point.
     * 
     * @param upToPosition the position to check up to (inclusive)
     * @return number of valid consecutive events from position 1
     */
    public long countValidChainFromBeginning(long upToPosition) {
        log.debug("Counting valid chain from beginning up to position: {}", upToPosition);

        AuditEvent previousEvent = null;
        long validCount = 0;

        for (long position = 1; position <= upToPosition; position++) {
            Optional<AuditEvent> currentEventOpt = auditEventRepository.findByChainPosition(position);

            if (currentEventOpt.isEmpty()) {
                log.debug("Chain ends at position: {}", position - 1);
                return validCount;
            }

            AuditEvent currentEvent = currentEventOpt.get();

            if (!verifyEventInternal(currentEvent, previousEvent)) {
                log.debug("Invalid event at position: {}", position);
                return validCount;
            }

            validCount++;
            previousEvent = currentEvent;
        }

        return validCount;
    }
}
