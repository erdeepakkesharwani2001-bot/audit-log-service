package com.schwab.audit.service;

import com.schwab.audit.dto.request.CreateAuditEventRequest;
import com.schwab.audit.dto.response.AuditEventDetailResponse;
import com.schwab.audit.dto.response.AuditEventResponse;
import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import com.schwab.audit.util.Constants;
import com.schwab.audit.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.NoSuchElementException;

/**
 * Service for audit event management.
 * 
 * Handles:
 * - Creating audit events with SHA-256 hash chain
 * - Querying events with filtering and pagination
 * - Converting between entities and DTOs
 * 
 * Ensures tamper-evidence through hash chain verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuditEventService {

    private final AuditEventRepository auditEventRepository;
    private final HashUtils hashUtils;
    private final ChainVerificationService chainVerificationService;

    /**
     * Creates a new audit event with hash chain.
     * 
     * Process:
     * 1. Find the last event in the chain
     * 2. Calculate content hash of new event
     * 3. Set previousHash from last event (or GENESIS_HASH for first)
     * 4. Assign next chainPosition
     * 5. Persist to database
     * 
     * @param request the audit event creation request
     * @return the created audit event as response DTO
     */
    @Transactional(readOnly = false)
    public AuditEventResponse createAuditEvent(CreateAuditEventRequest request) {
        log.info("Creating audit event - type: {}, actor: {}, resource: {}/{}", 
                 request.getEventType(), request.getActorId(), 
                 request.getResourceType(), request.getResourceId());

        // Get the last event to determine chain position and previous hash
        Optional<AuditEvent> lastEvent = auditEventRepository.findLastEvent();

        // Resolve the timestamp once so the value used in the hash is exactly the
        // value persisted on the event. Calling LocalDateTime.now() twice can
        // otherwise create an unverifiable event when the timestamp is omitted.
        LocalDateTime eventTimestamp = request.getTimestamp() != null
                ? request.getTimestamp()
                : LocalDateTime.now();

        // Build content for hashing
        String eventContent = buildEventContent(request, eventTimestamp);
        String contentHash = hashUtils.computeSha256(eventContent);

        // Check for duplicate event (same content)
        if (auditEventRepository.existsByContentHash(contentHash)) {
            log.warn("Attempted to create duplicate event with content hash: {}", contentHash);
            throw new IllegalArgumentException("Event with identical content already exists");
        }

        // Determine chain position and previous hash
        long chainPosition = lastEvent.isPresent() ? lastEvent.get().getChainPosition() + 1 : 1;
        String previousHash = lastEvent.isPresent() ? lastEvent.get().getContentHash() : Constants.GENESIS_HASH;

        // Create the new event
        AuditEvent event = AuditEvent.builder()
                .eventType(request.getEventType())
                .actorId(request.getActorId())
                .resourceType(request.getResourceType())
                .resourceId(request.getResourceId())
                .payload(request.getPayload())
                .timestamp(eventTimestamp)
                .contentHash(contentHash)
                .previousHash(previousHash)
                .chainPosition(chainPosition)
                .archived(false)
                .build();

        // Save to database
        AuditEvent savedEvent = auditEventRepository.save(event);
        log.info("Audit event created successfully - id: {}, position: {}", 
                 savedEvent.getId(), savedEvent.getChainPosition());

        return mapToResponse(savedEvent);
    }

    /**
     * Retrieves all audit events with pagination.
     * 
     * @param pageable pagination parameters
     * @return page of audit events
     */
    public Page<AuditEventResponse> getAllAuditEvents(Pageable pageable) {
        log.debug("Retrieving all audit events - page: {}, size: {}", pageable.getPageNumber(), pageable.getPageSize());
        
        Page<AuditEvent> events = auditEventRepository.findAll(pageable);
        return events.map(this::mapToResponse);
    }

    /**
     * Retrieves a specific audit event by ID.
     * 
     * @param id the event ID
     * @return detailed event response
     * @throws RuntimeException if event not found
     */
    public AuditEventDetailResponse getAuditEventById(Long id) {
        log.debug("Retrieving audit event - id: {}", id);
        
        AuditEvent event = auditEventRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Audit event not found - id: {}", id);
                    return new NoSuchElementException("Audit event not found");
                });

        return mapToDetailResponse(event);
    }

    /**
     * Queries events by resource.
     * 
     * @param resourceType the resource type
     * @param resourceId the resource ID
     * @param pageable pagination parameters
     * @return page of matching events
     */
    public Page<AuditEventResponse> getEventsByResource(String resourceType, String resourceId, Pageable pageable) {
        log.debug("Retrieving events by resource - type: {}, id: {}", resourceType, resourceId);
        
        Page<AuditEvent> events = auditEventRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable);
        return events.map(this::mapToResponse);
    }

    /**
     * Queries events by actor (user).
     * 
     * @param actorId the actor ID
     * @param pageable pagination parameters
     * @return page of matching events
     */
    public Page<AuditEventResponse> getEventsByActor(String actorId, Pageable pageable) {
        log.debug("Retrieving events by actor - id: {}", actorId);
        
        Page<AuditEvent> events = auditEventRepository.findByActorId(actorId, pageable);
        return events.map(this::mapToResponse);
    }

    /**
     * Queries events by type.
     * 
     * @param eventType the event type
     * @param pageable pagination parameters
     * @return page of matching events
     */
    public Page<AuditEventResponse> getEventsByType(String eventType, Pageable pageable) {
        log.debug("Retrieving events by type - type: {}", eventType);
        
        Page<AuditEvent> events = auditEventRepository.findByEventType(eventType, pageable);
        return events.map(this::mapToResponse);
    }

    /**
     * Queries events within a timestamp range.
     * 
     * @param startTime the start timestamp
     * @param endTime the end timestamp
     * @param pageable pagination parameters
     * @return page of matching events
     */
    public Page<AuditEventResponse> getEventsByTimestampRange(LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        log.debug("Retrieving events by timestamp range - start: {}, end: {}", startTime, endTime);
        
        if (startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }

        Page<AuditEvent> events = auditEventRepository.findByTimestampRange(startTime, endTime, pageable);
        return events.map(this::mapToResponse);
    }

    /**
     * Queries non-archived events.
     * 
     * @param pageable pagination parameters
     * @return page of unarchived events
     */
    public Page<AuditEventResponse> getUnarchivedEvents(Pageable pageable) {
        log.debug("Retrieving unarchived events");
        
        Page<AuditEvent> events = auditEventRepository.findByArchivedFalse(pageable);
        return events.map(this::mapToResponse);
    }

    /**
     * Queries archived events.
     * 
     * @param pageable pagination parameters
     * @return page of archived events
     */
    public Page<AuditEventResponse> getArchivedEvents(Pageable pageable) {
        log.debug("Retrieving archived events");
        
        Page<AuditEvent> events = auditEventRepository.findByArchivedTrue(pageable);
        return events.map(this::mapToResponse);
    }

    /**
     * Retrieves the total count of audit events.
     * 
     * @return total count
     */
    public long getTotalEventCount() {
        return auditEventRepository.count();
    }

    /**
     * Verifies the integrity of the entire hash chain.
     * 
     * @return true if chain is valid, false otherwise
     */
    public boolean verifyChainIntegrity() {
        log.info("Verifying complete chain integrity");
        return chainVerificationService.verifyCompleteChain();
    }

    /**
     * Verifies a specific event's hash and its position in the chain.
     * 
     * @param eventId the event ID
     * @return true if event and chain position are valid
     */
    public boolean verifyEventIntegrity(Long eventId) {
        log.debug("Verifying event integrity - id: {}", eventId);
        
        AuditEvent event = auditEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        return chainVerificationService.verifyEvent(event);
    }

    /**
     * Archives an audit event (immutable operation).
     * Only admins should be able to call this.
     * 
     * @param eventId the event ID
     * @return updated event response
     */
    @Transactional(readOnly = false)
    public AuditEventResponse archiveAuditEvent(Long eventId) {
        log.info("Archiving audit event - id: {}", eventId);

        AuditEvent event = auditEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found"));

        if (event.getArchived()) {
            log.warn("Event already archived - id: {}", eventId);
            throw new IllegalArgumentException("Event is already archived");
        }

        event.markAsArchived();
        AuditEvent saved = auditEventRepository.save(event);

        log.info("Event archived successfully - id: {}", eventId);
        return mapToResponse(saved);
    }

    /**
     * Searches events by multiple criteria.
     * 
     * @param resourceType the resource type (optional)
     * @param resourceId the resource ID (optional)
     * @param eventType the event type (optional)
     * @param pageable pagination parameters
     * @return page of matching events
     */
    public Page<AuditEventResponse> searchEvents(String resourceType, String resourceId, String eventType, Pageable pageable) {
        if (resourceType != null && resourceId != null && eventType != null) {
            log.debug("Searching events by resource and type");
            Page<AuditEvent> events = auditEventRepository.findByResourceAndEventType(resourceType, resourceId, eventType, pageable);
            return events.map(this::mapToResponse);
        }

        // Fall back to all events
        return getAllAuditEvents(pageable);
    }

    /**
     * Builds a string representation of the event content for hashing.
     * Order matters for consistent hashing.
     * 
     * @param request the event creation request
     * @return concatenated event content
     */
    private String buildEventContent(CreateAuditEventRequest request, LocalDateTime timestamp) {
        return String.format(
                "%s|%s|%s|%s|%s|%s",
                request.getEventType(),
                request.getActorId(),
                request.getResourceType(),
                request.getResourceId(),
                request.getPayload() != null ? request.getPayload() : "",
                timestamp.toString()
        );
    }

    /**
     * Maps AuditEvent entity to response DTO.
     * 
     * @param event the entity
     * @return response DTO
     */
    private AuditEventResponse mapToResponse(AuditEvent event) {
        return AuditEventResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .actorId(event.getActorId())
                .resourceType(event.getResourceType())
                .resourceId(event.getResourceId())
                .payload(event.getPayload())
                .timestamp(event.getTimestamp())
                .createdAt(event.getCreatedAt())
                .archivedAt(event.getArchivedAt())
                .contentHash(event.getContentHash())
                .previousHash(event.getPreviousHash())
                .chainPosition(event.getChainPosition())
                .archived(event.getArchived())
                .build();
    }

    /**
     * Maps AuditEvent entity to detailed response DTO.
     * 
     * @param event the entity
     * @return detailed response DTO
     */
    private AuditEventDetailResponse mapToDetailResponse(AuditEvent event) {
        return AuditEventDetailResponse.builder()
                .id(event.getId())
                .eventType(event.getEventType())
                .actorId(event.getActorId())
                .resourceType(event.getResourceType())
                .resourceId(event.getResourceId())
                .payload(event.getPayload())
                .timestamp(event.getTimestamp())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .archivedAt(event.getArchivedAt())
                .contentHash(event.getContentHash())
                .previousHash(event.getPreviousHash())
                .chainPosition(event.getChainPosition())
                .archived(event.getArchived())
                .redactionMetadata(event.getRedactionMetadata())
                .isGenesis(event.isGenesis())
                .isRedacted(event.isRedacted())
                .build();
    }
}
