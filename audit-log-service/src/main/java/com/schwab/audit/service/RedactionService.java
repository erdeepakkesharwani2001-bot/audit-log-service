package com.schwab.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.entity.AuditEventRedaction;
import com.schwab.audit.repository.AuditEventRedactionRepository;
import com.schwab.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for redacting sensitive data from audit events.
 * 
 * Implements field-level redaction while preserving audit trail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RedactionService {

    private final AuditEventRepository auditEventRepository;
    private final AuditEventRedactionRepository redactionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Redacts sensitive fields from an audit event.
     * 
     * Preserves original hash in metadata for verification.
     * 
     * @param eventId the event ID
     * @param fieldsToRedact list of field names to redact
     * @param reason the redaction reason
     * @param redactedBy the user performing redaction
     * @return updated event
     */
    @Transactional(readOnly = false)
    public AuditEvent redactEvent(Long eventId, List<String> fieldsToRedact, String reason, String redactedBy) throws JsonProcessingException {
        log.info("Redacting event {} - fields: {}, reason: {}", eventId, fieldsToRedact, reason);

        AuditEvent event = auditEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.isRedacted()) {
            log.warn("Event already redacted - id: {}", eventId);
            throw new IllegalArgumentException("Event has already been redacted");
        }

        // Parse payload JSON
        String originalPayload = event.getPayload();
        Map<String, Object> payloadMap = null;
        if (originalPayload != null) {
            try {
                payloadMap = objectMapper.readValue(originalPayload, Map.class);
            } catch (Exception e) {
                log.error("Failed to parse payload JSON", e);
                throw new RuntimeException("Invalid payload JSON format");
            }
        }

        // Mask sensitive fields in payload
        if (payloadMap != null) {
            for (String field : fieldsToRedact) {
                if (payloadMap.containsKey(field)) {
                    payloadMap.put(field, "***REDACTED***");
                }
            }
        }

        // Update event with redacted payload
        if (payloadMap != null) {
            try {
                event.setPayload(objectMapper.writeValueAsString(payloadMap));
            } catch (Exception e) {
                log.error("Failed to serialize redacted payload", e);
                throw new RuntimeException("Failed to update payload");
            }
        }

        // Store redaction metadata
        String redactionMetadata = buildRedactionMetadata(fieldsToRedact, reason, redactedBy);
        event.setRedactionMetadata(redactionMetadata);

        // Create redaction record
        AuditEventRedaction redaction = AuditEventRedaction.builder()
                .auditEventId(eventId)
                .redactedFields(objectMapper.writeValueAsString(fieldsToRedact))
                .redactionReason(reason)
                .redactedBy(redactedBy)
                .redactedAt(LocalDateTime.now())
                .build();

        auditEventRepository.save(event);
        redactionRepository.save(redaction);

        log.info("Event redacted successfully - id: {}", eventId);
        return event;
    }

    /**
     * Retrieves redaction history for an event.
     * 
     * @param eventId the event ID
     * @return redaction record if exists
     */
    public Optional<AuditEventRedaction> getRedactionHistory(Long eventId) {
        return redactionRepository.findByAuditEventId(eventId);
    }

    /**
     * Checks if an event has been redacted.
     * 
     * @param eventId the event ID
     * @return true if event is redacted
     */
    public boolean isEventRedacted(Long eventId) {
        return redactionRepository.existsByAuditEventId(eventId);
    }

    /**
     * Builds JSON metadata about the redaction.
     */
    private String buildRedactionMetadata(List<String> fieldsToRedact, String reason, String redactedBy) {
        try {
            Map<String, Object> metadata = Map.of(
                    "fields", fieldsToRedact,
                    "reason", reason,
                    "redactedBy", redactedBy,
                    "redactedAt", LocalDateTime.now().toString()
            );
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.error("Failed to build redaction metadata", e);
            return "{}";
        }
    }
}
