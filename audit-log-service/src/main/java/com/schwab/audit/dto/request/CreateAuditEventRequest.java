package com.schwab.audit.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request DTO for creating a new audit event (POST /api/v1/audit/events).
 * 
 * Validates:
 * - eventType: Required, max 100 chars
 * - actorId: Required, max 255 chars
 * - resourceType: Required, max 100 chars
 * - resourceId: Required, max 255 chars
 * - payload: Optional JSON content
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAuditEventRequest {

    @NotBlank(message = "Event type is required")
    private String eventType;

    @NotBlank(message = "Actor ID is required")
    private String actorId;

    @NotBlank(message = "Resource type is required")
    private String resourceType;

    @NotBlank(message = "Resource ID is required")
    private String resourceId;

    private String payload;  // Optional JSON content

    private LocalDateTime timestamp;  // Optional; defaults to now() if not provided
}
