package com.schwab.audit.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Detailed response DTO for audit event verification (GET /api/v1/audit/events/{id}).
 * 
 * Extends AuditEventResponse with additional fields for cryptographic verification:
 * - Chain verification information
 * - Redaction metadata (if any)
 * - Complete audit trail
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditEventDetailResponse {

    private Long id;
    private String eventType;
    private String actorId;
    private String resourceType;
    private String resourceId;
    private String payload;  // JSON content
    private LocalDateTime timestamp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime archivedAt;
    private String contentHash;  // SHA-256 hash of content
    private String previousHash;  // SHA-256 hash of previous event
    private Long chainPosition;  // Sequential position in audit chain
    private Boolean archived;  // Archival status
    private String redactionMetadata;  // JSON tracking redacted fields (if any)
    private Boolean isGenesis;  // True if first event in chain
    private Boolean isRedacted;  // True if any fields have been redacted
}
