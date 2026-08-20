package com.schwab.audit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for audit events (returned from list/detail endpoints).
 * 
 * Includes:
 * - Event metadata: id, eventType, actorId, resourceType, resourceId
 * - Timestamps: timestamp, createdAt, archivedAt
 * - Chain verification: contentHash, previousHash, chainPosition
 * - Status: archived
 * - Payload: event data (can be null or redacted)
 * 
 * Excludes:
 * - Internal audit fields: createdBy, updatedBy, updatedAt
 * - Raw redactionMetadata (use dedicated API for redaction details)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEventResponse {

    private Long id;
    private String eventType;
    private String actorId;
    private String resourceType;
    private String resourceId;
    private String payload;  // JSON content (may be redacted)
    private LocalDateTime timestamp;
    private LocalDateTime createdAt;
    private LocalDateTime archivedAt;
    private String contentHash;  // SHA-256 hash (64 hex chars)
    private String previousHash;  // SHA-256 hash of previous event or "GENESIS_HASH"
    private Long chainPosition;  // Position in the chain
    private Boolean archived;  // Whether event has been archived
}
