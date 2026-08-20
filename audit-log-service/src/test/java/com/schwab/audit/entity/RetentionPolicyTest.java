package com.schwab.audit.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetentionPolicyTest {

    @Test
    void lifecycleCallbacksPopulateTimestamps() {
        RetentionPolicy policy = RetentionPolicy.builder()
                .policyName("standard")
                .retentionDays(365)
                .active(true)
                .build();

        policy.onCreate();
        LocalDateTime createdAt = policy.getCreatedAt();
        LocalDateTime updatedAt = policy.getUpdatedAt();
        policy.onUpdate();

        assertNotNull(createdAt);
        assertNotNull(policy.getUpdatedAt());
        assertTrue(!policy.getUpdatedAt().isBefore(updatedAt));
    }
}
