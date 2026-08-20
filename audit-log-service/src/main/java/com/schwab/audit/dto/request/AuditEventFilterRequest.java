package com.schwab.audit.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Advanced filter criteria for audit event queries.
 * Supports complex filtering with multiple criteria combined.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventFilterRequest {

    // Event metadata filters
    private String eventType;
    private List<String> eventTypes;  // Multiple event types (OR)
    private String actorId;
    private List<String> actorIds;  // Multiple actors (OR)
    
    // Resource filters
    private String resourceType;
    private String resourceId;
    private List<String> resourceIds;  // Multiple resource IDs (OR)
    
    // Timestamp filters
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    
    // Status filters
    private Boolean archived;
    private Boolean redacted;
    
    // Payload search
    private String payloadContains;  // Simple text search in payload
    
    // Pagination
    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 20;

    @Builder.Default
    private String sortBy = "chainPosition";  // Field to sort by

    @Builder.Default
    private String sortDirection = "DESC";  // ASC or DESC
}
