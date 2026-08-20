package com.schwab.audit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AuditEventRedaction entity for tracking redacted events.
 * 
 * Records when fields were redacted, by whom, and for what reason.
 */
@Entity
@Table(name = "audit_event_redactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventRedaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long auditEventId;

    @Column(nullable = false, columnDefinition = "CLOB")
    private String redactedFields;  // JSON array of field names

    @Column(nullable = false, length = 255)
    private String redactionReason;

    @Column(nullable = false, length = 255)
    private String redactedBy;

    @Column(nullable = false)
    private LocalDateTime redactedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
