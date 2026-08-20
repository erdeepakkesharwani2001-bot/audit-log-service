package com.schwab.audit.controller;

import com.schwab.audit.dto.request.CreateAuditEventRequest;
import com.schwab.audit.dto.response.AuditEventDetailResponse;
import com.schwab.audit.dto.response.AuditEventResponse;
import com.schwab.audit.service.AuditEventService;
import com.schwab.audit.service.ChainVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for audit event operations.
 * 
 * Provides endpoints for:
 * - Creating audit events (AUDIT_WRITER, ADMIN)
 * - Reading/querying events (AUDITOR, ADMIN)
 * - Verifying chain integrity (AUDITOR, ADMIN)
 * 
 * All endpoints require JWT authentication.
 */
@RestController
@RequestMapping("/api/v1/audit/events")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Events", description = "Audit event management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AuditEventController {

    private final AuditEventService auditEventService;
    private final ChainVerificationService chainVerificationService;

    /**
     * Creates a new audit event with hash chain.
     * 
     * Required role: AUDIT_WRITER or ADMIN
     * 
     * @param request the event creation request
     * @return created event response with 201 status
     */
    @PostMapping
    @PreAuthorize("hasRole('AUDIT_WRITER') or hasRole('ADMIN')")
    @Operation(
        summary = "Create audit event",
        description = "Create a new audit event and add it to the hash chain"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Event created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuditEventResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request body"),
        @ApiResponse(responseCode = "401", description = "Unauthorized - invalid JWT token"),
        @ApiResponse(responseCode = "403", description = "Forbidden - insufficient permissions"),
        @ApiResponse(responseCode = "409", description = "Conflict - duplicate event content"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AuditEventResponse> createAuditEvent(
            @Valid @RequestBody CreateAuditEventRequest request) {

        log.info("POST /api/v1/audit/events - Creating event: {}", request.getEventType());

        AuditEventResponse response = auditEventService.createAuditEvent(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves all audit events with pagination.
     * 
     * Required role: AUDITOR or ADMIN
     * 
     * @param page page number (0-indexed, default 0)
     * @param size page size (default 20, max 100)
     * @return paginated list of events
     */
    @GetMapping
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(
        summary = "List audit events",
        description = "Retrieve all audit events with pagination"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Events retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Page.class))
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Page<AuditEventResponse>> listAuditEvents(
            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)")
            @RequestParam(defaultValue = "20") int size) {

        log.debug("GET /api/v1/audit/events - page: {}, size: {}", page, size);

        // Validate pagination parameters
        if (size > 100) {
            size = 100;
        }
        if (size < 1) {
            size = 1;
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "chainPosition"));
        Page<AuditEventResponse> events = auditEventService.getAllAuditEvents(pageable);

        return ResponseEntity.ok(events);
    }

    /**
     * Retrieves a specific audit event with full details.
     * 
     * Required role: AUDITOR or ADMIN
     * 
     * @param id the event ID
     * @return detailed event response
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(
        summary = "Get audit event details",
        description = "Retrieve a specific audit event with full details including chain verification info"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Event retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuditEventDetailResponse.class))
        ),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AuditEventDetailResponse> getAuditEvent(
            @Parameter(description = "Event ID")
            @PathVariable Long id) {

        log.debug("GET /api/v1/audit/events/{} - Retrieving event details", id);

        AuditEventDetailResponse event = auditEventService.getAuditEventById(id);

        return ResponseEntity.ok(event);
    }

    /**
     * Queries events by resource type and ID.
     * 
     * Required role: AUDITOR or ADMIN
     * 
     * @param resourceType the resource type
     * @param resourceId the resource ID
     * @param page page number
     * @param size page size
     * @return paginated events
     */
    @GetMapping("/search/by-resource")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(summary = "Search events by resource")
    public ResponseEntity<Page<AuditEventResponse>> searchByResource(
            @Parameter(description = "Resource type") @RequestParam String resourceType,
            @Parameter(description = "Resource ID") @RequestParam String resourceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Search events by resource - type: {}, id: {}", resourceType, resourceId);

        if (size > 100) size = 100;
        if (size < 1) size = 1;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "chainPosition"));
        Page<AuditEventResponse> events = auditEventService.getEventsByResource(resourceType, resourceId, pageable);

        return ResponseEntity.ok(events);
    }

    /**
     * Queries events by actor (user).
     * 
     * Required role: AUDITOR or ADMIN
     * 
     * @param actorId the actor ID
     * @param page page number
     * @param size page size
     * @return paginated events
     */
    @GetMapping("/search/by-actor")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(summary = "Search events by actor")
    public ResponseEntity<Page<AuditEventResponse>> searchByActor(
            @Parameter(description = "Actor ID") @RequestParam String actorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Search events by actor - id: {}", actorId);

        if (size > 100) size = 100;
        if (size < 1) size = 1;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "chainPosition"));
        Page<AuditEventResponse> events = auditEventService.getEventsByActor(actorId, pageable);

        return ResponseEntity.ok(events);
    }

    /**
     * Queries events by type.
     * 
     * Required role: AUDITOR or ADMIN
     * 
     * @param eventType the event type
     * @param page page number
     * @param size page size
     * @return paginated events
     */
    @GetMapping("/search/by-type")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(summary = "Search events by type")
    public ResponseEntity<Page<AuditEventResponse>> searchByType(
            @Parameter(description = "Event type") @RequestParam String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Search events by type - type: {}", eventType);

        if (size > 100) size = 100;
        if (size < 1) size = 1;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "chainPosition"));
        Page<AuditEventResponse> events = auditEventService.getEventsByType(eventType, pageable);

        return ResponseEntity.ok(events);
    }

    /**
     * Queries events by timestamp range.
     * 
     * Required role: AUDITOR or ADMIN
     * 
     * @param startTime start timestamp (ISO format)
     * @param endTime end timestamp (ISO format)
     * @param page page number
     * @param size page size
     * @return paginated events
     */
    @GetMapping("/search/by-time-range")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(summary = "Search events by timestamp range")
    public ResponseEntity<Page<AuditEventResponse>> searchByTimeRange(
            @Parameter(description = "Start timestamp (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "End timestamp (ISO format)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.debug("Search events by time range - start: {}, end: {}", startTime, endTime);

        if (size > 100) size = 100;
        if (size < 1) size = 1;

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "chainPosition"));
        Page<AuditEventResponse> events = auditEventService.getEventsByTimestampRange(startTime, endTime, pageable);

        return ResponseEntity.ok(events);
    }

    /**
     * Verifies the integrity of the complete hash chain.
     * 
     * Required role: AUDITOR or ADMIN
     * 
     * @return verification result with status and message
     */
    @PostMapping("/verify-chain")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(
        summary = "Verify chain integrity",
        description = "Verify the integrity of the complete hash chain"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification result returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> verifyChain() {
        log.info("POST /api/v1/audit/events/verify-chain - Verifying chain integrity");

        boolean isValid = chainVerificationService.verifyCompleteChain();
        long totalEvents = auditEventService.getTotalEventCount();

        Map<String, Object> response = new HashMap<>();
        response.put("valid", isValid);
        response.put("message", isValid ? "Chain integrity verified" : "Chain integrity check failed");
        response.put("totalEvents", totalEvents);

        return ResponseEntity.ok(response);
    }

    /**
     * Verifies a specific event's integrity in the chain.
     * 
     * Required role: AUDITOR or ADMIN
     * 
     * @param id the event ID
     * @return verification result
     */
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(
        summary = "Verify event integrity",
        description = "Verify a specific event's integrity in the chain"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Verification result returned"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<Map<String, Object>> verifyEvent(
            @Parameter(description = "Event ID")
            @PathVariable Long id) {

        log.info("POST /api/v1/audit/events/{}/verify - Verifying event integrity", id);

        boolean isValid = auditEventService.verifyEventIntegrity(id);

        Map<String, Object> response = new HashMap<>();
        response.put("valid", isValid);
        response.put("message", isValid ? "Event integrity verified" : "Event integrity check failed");
        response.put("eventId", id);

        return ResponseEntity.ok(response);
    }

    /**
     * Archives an audit event (immutable operation).
     * 
     * Required role: ADMIN only
     * 
     * @param id the event ID
     * @return archived event response
     */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Archive audit event",
        description = "Archive an audit event (immutable operation, ADMIN only)"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Event archived successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Event not found"),
        @ApiResponse(responseCode = "409", description = "Event already archived"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<AuditEventResponse> archiveEvent(
            @Parameter(description = "Event ID")
            @PathVariable Long id) {

        log.info("POST /api/v1/audit/events/{}/archive - Archiving event", id);

        AuditEventResponse response = auditEventService.archiveAuditEvent(id);

        return ResponseEntity.ok(response);
    }

    /**
     * Gets statistics about the audit log.
     * 
     * Required role: AUDITOR or ADMIN
     * 
     * @return statistics including total count
     */
    @GetMapping("/stats/summary")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    @Operation(summary = "Get audit log statistics")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.debug("GET /api/v1/audit/events/stats/summary - Getting statistics");

        long totalEvents = auditEventService.getTotalEventCount();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", totalEvents);

        return ResponseEntity.ok(stats);
    }
}
