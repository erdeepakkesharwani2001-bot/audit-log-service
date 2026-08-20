package com.schwab.audit.service;

import com.schwab.audit.dto.request.AuditEventFilterRequest;
import com.schwab.audit.dto.response.AuditEventResponse;
import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for advanced filtering and querying of audit events.
 *
 * Supports filtering by:
 * - event type
 * - actor
 * - resource type
 * - resource ID
 * - timestamp range
 * - archived status
 *
 * Supports pagination and sorting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuditEventQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository auditEventRepository;

    /**
     * Executes an advanced filtered query with multiple criteria.
     *
     * @param filter filter request containing optional criteria
     * @return page of matching audit events
     */
    public Page<AuditEventResponse> executeFilteredQuery(
            AuditEventFilterRequest filter) {

        log.debug(
                "Executing filtered query: eventType={}, actorId={}, resourceType={}, resourceId={}, archived={}",
                filter.getEventType(),
                filter.getActorId(),
                filter.getResourceType(),
                filter.getResourceId(),
                filter.getArchived()
        );

        /*
         * Build pagination and sorting.
         */
        String sortBy = filter.getSortBy() == null || filter.getSortBy().isBlank()
                ? "chainPosition"
                : filter.getSortBy();
        String sortDirection = filter.getSortDirection() == null
                ? "DESC"
                : filter.getSortDirection();
        int page = Math.max(filter.getPage(), 0);
        int size = Math.min(Math.max(filter.getSize(), 1), MAX_PAGE_SIZE);

        Sort.Direction direction =
                "ASC".equalsIgnoreCase(sortDirection)
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Sort sort = Sort.by(
                direction,
                sortBy
        );

        Pageable pageable = PageRequest.of(
                page,
                size,
                sort
        );

        /*
         * ---------------------------------------------------------
         * 1. All four criteria
         * ---------------------------------------------------------
         */
        if (filter.getEventType() != null
                && filter.getActorId() != null
                && filter.getResourceType() != null
                && filter.getResourceId() != null) {

            return auditEventRepository
                    .findByEventTypeAndActorIdAndResourceTypeAndResourceId(
                            filter.getEventType(),
                            filter.getActorId(),
                            filter.getResourceType(),
                            filter.getResourceId(),
                            pageable
                    )
                    .map(this::mapToResponse);
        }

        /*
         * ---------------------------------------------------------
         * 2. Resource + event type
         * ---------------------------------------------------------
         *
         * IMPORTANT:
         * Filtering is performed by the database rather than
         * filtering a single Page in Java.
         */
        if (filter.getResourceType() != null
                && filter.getResourceId() != null
                && filter.getEventType() != null) {

            return auditEventRepository
                    .findByResourceTypeAndResourceIdAndEventType(
                            filter.getResourceType(),
                            filter.getResourceId(),
                            filter.getEventType(),
                            pageable
                    )
                    .map(this::mapToResponse);
        }

        /*
         * ---------------------------------------------------------
         * 3. Resource
         * ---------------------------------------------------------
         */
        if (filter.getResourceType() != null
                && filter.getResourceId() != null) {

            return auditEventRepository
                    .findByResourceTypeAndResourceId(
                            filter.getResourceType(),
                            filter.getResourceId(),
                            pageable
                    )
                    .map(this::mapToResponse);
        }

        /*
         * ---------------------------------------------------------
         * 4. Actor
         * ---------------------------------------------------------
         */
        if (filter.getActorId() != null) {

            return auditEventRepository
                    .findByActorId(
                            filter.getActorId(),
                            pageable
                    )
                    .map(this::mapToResponse);
        }

        /*
         * ---------------------------------------------------------
         * 5. Event type
         * ---------------------------------------------------------
         */
        if (filter.getEventType() != null) {

            return auditEventRepository
                    .findByEventType(
                            filter.getEventType(),
                            pageable
                    )
                    .map(this::mapToResponse);
        }

        /*
         * ---------------------------------------------------------
         * 6. Timestamp range
         * ---------------------------------------------------------
         */
        if (filter.getStartTime() != null
                && filter.getEndTime() != null) {

            return auditEventRepository
                    .findByTimestampRange(
                            filter.getStartTime(),
                            filter.getEndTime(),
                            pageable
                    )
                    .map(this::mapToResponse);
        }

        /*
         * ---------------------------------------------------------
         * 7. Archived status
         * ---------------------------------------------------------
         *
         * The .map(this::mapToResponse) is required because the
         * repository returns Page<AuditEvent>, while this service
         * returns Page<AuditEventResponse>.
         */
        if (filter.getArchived() != null) {

            if (filter.getArchived()) {

                return auditEventRepository
                        .findByArchivedTrue(pageable)
                        .map(this::mapToResponse);

            } else {

                return auditEventRepository
                        .findByArchivedFalse(pageable)
                        .map(this::mapToResponse);
            }
        }

        /*
         * ---------------------------------------------------------
         * 8. No filters
         * ---------------------------------------------------------
         */
        return auditEventRepository
                .findAll(pageable)
                .map(this::mapToResponse);
    }

    /**
     * Converts AuditEvent entity to response DTO.
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


}
