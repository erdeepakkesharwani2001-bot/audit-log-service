package com.schwab.audit.service;

import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.entity.RetentionPolicy;
import com.schwab.audit.repository.AuditEventRepository;
import com.schwab.audit.repository.RetentionPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing event retention policies.
 * 
 * Implements automatic archival of events based on retention policies.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RetentionPolicyService {

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final AuditEventRepository auditEventRepository;

    @Value("${app.audit.retention.days:365}")
    private int defaultRetentionDays;

    /**
     * Creates a new retention policy.
     * 
     * @param policyName the policy name
     * @param retentionDays days to retain before archiving
     * @param description description of policy
     * @return the created policy
     */
    @Transactional(readOnly = false)
    public RetentionPolicy createRetentionPolicy(String policyName, Integer retentionDays, String description) {
        log.info("Creating retention policy: {} - {} days", policyName, retentionDays);

        if (retentionPolicyRepository.existsByPolicyName(policyName)) {
            throw new IllegalArgumentException("Policy already exists: " + policyName);
        }

        RetentionPolicy policy = RetentionPolicy.builder()
                .policyName(policyName)
                .retentionDays(retentionDays)
                .description(description)
                .active(true)
                .build();

        return retentionPolicyRepository.save(policy);
    }

    /**
     * Retrieves all active retention policies.
     * 
     * @return list of active policies
     */
    public List<RetentionPolicy> getActivePolicies() {
        return retentionPolicyRepository.findAll().stream()
                .filter(RetentionPolicy::getActive)
                .toList();
    }

    /**
     * Applies retention policy to archive expired events.
     * Runs scheduled every day at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional(readOnly = false)
    public void applyRetentionPolicy() {
        log.info("Applying retention policies");

        List<RetentionPolicy> policies = getActivePolicies();

        for (RetentionPolicy policy : policies) {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(policy.getRetentionDays());
            archiveEventsOlderThan(cutoffDate);
        }

        log.info("Retention policies applied successfully");
    }

    /**
     * Archives events older than the specified date.
     * 
     * @param cutoffDate events created before this date will be archived
     */
    @Transactional(readOnly = false)
    public long archiveEventsOlderThan(LocalDateTime cutoffDate) {
        log.debug("Archiving events older than: {}", cutoffDate);

        List<AuditEvent> eventsToArchive = auditEventRepository.findByArchivedFalse(
                org.springframework.data.domain.PageRequest.of(0, Integer.MAX_VALUE))
                .stream()
                .filter(e -> e.getCreatedAt().isBefore(cutoffDate) && !e.getArchived())
                .toList();

        long count = 0;
        for (AuditEvent event : eventsToArchive) {
            event.markAsArchived();
            auditEventRepository.save(event);
            count++;
        }

        log.info("Archived {} events older than {}", count, cutoffDate);
        return count;
    }

    /**
     * Gets the default retention policy.
     * 
     * @return retention days
     */
    public int getDefaultRetentionDays() {
        return defaultRetentionDays;
    }
}
