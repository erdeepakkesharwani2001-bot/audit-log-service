package com.schwab.audit.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuditEventRedactionTest {

    @Test
    void onCreateSetsCreatedAt() {
        AuditEventRedaction redaction = AuditEventRedaction.builder()
                .auditEventId(1L)
                .redactedFields("[\"ssn\"]")
                .redactionReason("PII")
                .redactedBy("admin")
                .redactedAt(LocalDateTime.now())
                .build();

        assertNull(redaction.getCreatedAt());
        redaction.onCreate();
        assertNotNull(redaction.getCreatedAt());
    }
}
