package com.schwab.audit.repository;

import com.schwab.audit.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Repository for AuditEvent entities.
 * Provides database access for audit log queries and chain verification.
 * 
 * All queries are read-only (no update/delete operations after creation).
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Finds an event by its position in the chain.
     * 
     * @param chainPosition the sequential position
     * @return Optional containing the event if found
     */
    Optional<AuditEvent> findByChainPosition(Long chainPosition);

    /**
     * Finds all events for a specific resource (paginated).
     * 
     * @param resourceType the resource type
     * @param resourceId the resource ID
     * @param pageable pagination info
     * @return page of audit events
     */
    Page<AuditEvent> findByResourceTypeAndResourceId(String resourceType, String resourceId, Pageable pageable);

    /**
     * Finds all events created by a specific actor (paginated).
     * 
     * @param actorId the actor ID
     * @param pageable pagination info
     * @return page of audit events
     */
    Page<AuditEvent> findByActorId(String actorId, Pageable pageable);

    /**
     * Finds all events of a specific type (paginated).
     * 
     * @param eventType the event type
     * @param pageable pagination info
     * @return page of audit events
     */
    Page<AuditEvent> findByEventType(String eventType, Pageable pageable);

    /**
     * Finds all events within a timestamp range (paginated).
     * 
     * @param startTime the start timestamp
     * @param endTime the end timestamp
     * @param pageable pagination info
     * @return page of audit events
     */
    @Query("SELECT ae FROM AuditEvent ae WHERE ae.timestamp >= :startTime AND ae.timestamp <= :endTime ORDER BY ae.chainPosition ASC")
    Page<AuditEvent> findByTimestampRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * Finds all non-archived events (paginated).
     * 
     * @param pageable pagination info
     * @return page of unarchived events
     */
    Page<AuditEvent> findByArchivedFalse(Pageable pageable);

    /**
     * Finds all archived events (paginated).
     * 
     * @param pageable pagination info
     * @return page of archived events
     */
    Page<AuditEvent> findByArchivedTrue(Pageable pageable);

    /**
     * Finds the event with the highest chain position (most recent).
     * Used for building the next event's chain.
     * 
     * @return Optional containing the last event if any exist
     */
    @Query("SELECT ae FROM AuditEvent ae ORDER BY ae.chainPosition DESC LIMIT 1")
    Optional<AuditEvent> findLastEvent();

    /**
     * Finds the event by its content hash (unique).
     * 
     * @param contentHash the SHA-256 content hash
     * @return Optional containing the event if found
     */
    Optional<AuditEvent> findByContentHash(String contentHash);

    /**
     * Counts total number of audit events.
     * 
     * @return total count
     */
    long count();

    /**
     * Counts non-archived events.
     * 
     * @return count of unarchived events
     */
    long countByArchivedFalse();

    /**
     * Counts archived events.
     * 
     * @return count of archived events
     */
    long countByArchivedTrue();

    /**
     * Finds all events for a resource and event type (paginated).
     * 
     * @param resourceType the resource type
     * @param resourceId the resource ID
     * @param eventType the event type
     * @param pageable pagination info
     * @return page of matching events
     */
    @Query("SELECT ae FROM AuditEvent ae WHERE ae.resourceType = :resourceType AND ae.resourceId = :resourceId AND ae.eventType = :eventType ORDER BY ae.chainPosition DESC")
    Page<AuditEvent> findByResourceAndEventType(
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            @Param("eventType") String eventType,
            Pageable pageable);

    /**
     * Finds all events by actor within a timestamp range (paginated).
     * 
     * @param actorId the actor ID
     * @param startTime the start timestamp
     * @param endTime the end timestamp
     * @param pageable pagination info
     * @return page of matching events
     */
    @Query("SELECT ae FROM AuditEvent ae WHERE ae.actorId = :actorId AND ae.timestamp >= :startTime AND ae.timestamp <= :endTime ORDER BY ae.chainPosition DESC")
    Page<AuditEvent> findByActorAndTimestampRange(
            @Param("actorId") String actorId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    /**
     * Checks if a content hash already exists (for duplicate detection).
     * 
     * @param contentHash the SHA-256 content hash
     * @return true if hash exists
     */
    boolean existsByContentHash(String contentHash);

    /**
     * Finds events by all four criteria combined (most specific query).
     * 
     * @param eventType the event type
     * @param actorId the actor ID
     * @param resourceType the resource type
     * @param resourceId the resource ID
     * @param pageable pagination info
     * @return page of matching events
     */
    @Query("SELECT ae FROM AuditEvent ae WHERE ae.eventType = :eventType AND ae.actorId = :actorId AND ae.resourceType = :resourceType AND ae.resourceId = :resourceId ORDER BY ae.chainPosition DESC")
    Page<AuditEvent> findByEventTypeAndActorIdAndResourceTypeAndResourceId(
            @Param("eventType") String eventType,
            @Param("actorId") String actorId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId,
            Pageable pageable);

    Page<AuditEvent> findByResourceTypeAndResourceIdAndEventType(
            String resourceType,
            String resourceId,
            String eventType,
            Pageable pageable
    );
}
