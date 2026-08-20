package com.schwab.audit.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * AuditEvent entity representing a tamper-evident audit log entry.
 * 
 * Core Features:
 * - Append-only: No update/delete operations after creation
 * - Tamper-evident: SHA-256 hash chain verification (content_hash + previous_hash)
 * - Sequential: chain_position ensures event ordering
 * - Archivable: Records can be archived with archive timestamp
 * - Redactable: Sensitive fields can be redacted while preserving original hash
 * 
 * Database Schema:
 * - Unique constraints on content_hash and chain_position
 * - 6 performance indexes for common queries
 * - CLOB storage for JSON payloads and redaction metadata
 */
@Entity
@Table(name = "audit_events", indexes = {
    @Index(name = "idx_audit_events_chain_position", columnList = "chain_position", unique = true),
    @Index(name = "idx_audit_events_actor_id", columnList = "actor_id"),
    @Index(name = "idx_audit_events_resource", columnList = "resource_type, resource_id"),
    @Index(name = "idx_audit_events_event_type", columnList = "event_type"),
    @Index(name = "idx_audit_events_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_events_archived", columnList = "archived")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String actorId;

    @Column(nullable = false, length = 100)
    private String resourceType;

    @Column(nullable = false, length = 255)
    private String resourceId;

    @Column(columnDefinition = "CLOB")
    private String payload;  // JSON content

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = true)
    private LocalDateTime archivedAt;

    @Column(nullable = false, length = 64, unique = true)
    private String contentHash;  // SHA-256 hash of the event content (64 hex chars)

    @Column(nullable = false, length = 64)
    private String previousHash;  // SHA-256 hash of previous event or "GENESIS_HASH"

    @Column(nullable = false, unique = true)
    private Long chainPosition;  // Sequential position in the audit chain

    @Column(nullable = false)
    private Boolean archived;  // Whether this event has been archived (immutable)

    @Column(columnDefinition = "CLOB")
    private String redactionMetadata;  // JSON tracking redacted fields and reasons

    @CreatedBy
    @Column(nullable = false, updatable = false, length = 255)
    @JsonIgnore
    private String createdBy;

    @LastModifiedBy
    @Column(nullable = false, length = 255)
    @JsonIgnore
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        if (this.archived == null) {
            this.archived = false;
        }
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }

    /**
     * Marks this event as archived with current timestamp.
     * This is an append-only operation: once archived, cannot be unarchived.
     */
    public void markAsArchived() {
        if (!this.archived) {
            this.archived = true;
            this.archivedAt = LocalDateTime.now();
        }
    }

    /**
     * Returns true if this is the first event in the chain (genesis block).
     */
    public boolean isGenesis() {
        return "GENESIS_HASH".equals(this.previousHash);
    }

    /**
     * Returns true if any fields have been redacted.
     */
    public boolean isRedacted() {
        return this.redactionMetadata != null && !this.redactionMetadata.isEmpty();
    }
}
