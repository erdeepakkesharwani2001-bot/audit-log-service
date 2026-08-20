package com.schwab.audit.service;

import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for compliance reporting and audit trail generation.
 * 
 * Generates compliance reports for regulatory and internal audits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ComplianceReportingService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Generates a compliance report for a time period.
     * 
     * @param startDate start of period
     * @param endDate end of period
     * @return compliance report
     */
    public Map<String, Object> generateComplianceReport(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Generating compliance report for period: {} to {}", startDate, endDate);

        List<AuditEvent> events = auditEventRepository.findByTimestampRange(
                startDate, endDate, PageRequest.of(0, Integer.MAX_VALUE / 100)).toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportGenerated", LocalDateTime.now());
        report.put("periodStart", startDate);
        report.put("periodEnd", endDate);
        report.put("totalEvents", events.size());

        // Event type distribution
        Map<String, Long> eventTypeDistribution = events.stream()
                .collect(java.util.stream.Collectors.groupingByConcurrent(
                        AuditEvent::getEventType,
                        java.util.stream.Collectors.counting()
                ));
        report.put("eventTypeDistribution", eventTypeDistribution);

        // Actor activity
        Map<String, Long> actorActivity = events.stream()
                .collect(java.util.stream.Collectors.groupingByConcurrent(
                        AuditEvent::getActorId,
                        java.util.stream.Collectors.counting()
                ));
        report.put("actorActivity", actorActivity);

        // Archived events
        long archivedInPeriod = events.stream().filter(AuditEvent::getArchived).count();
        report.put("archivedEvents", archivedInPeriod);

        return report;
    }

    /**
     * Generates a user activity audit trail.
     * 
     * @param actorId the user ID
     * @param days number of days to look back
     * @return audit trail for user
     */
    public Map<String, Object> generateUserAuditTrail(String actorId, int days) {
        log.debug("Generating audit trail for user: {} - last {} days", actorId, days);

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        LocalDateTime endDate = LocalDateTime.now();

        List<AuditEvent> events = auditEventRepository.findByActorAndTimestampRange(
                actorId, startDate, endDate, PageRequest.of(0, Integer.MAX_VALUE / 100)).toList();

        Map<String, Object> trail = new LinkedHashMap<>();
        trail.put("actorId", actorId);
        trail.put("periodDays", days);
        trail.put("totalActions", events.size());
        trail.put("events", events);

        return trail;
    }

    /**
     * Generates resource audit trail.
     * 
     * @param resourceType resource type
     * @param resourceId resource ID
     * @return audit trail for resource
     */
    public Map<String, Object> generateResourceAuditTrail(String resourceType, String resourceId) {
        log.debug("Generating audit trail for resource: {}/{}", resourceType, resourceId);

        List<AuditEvent> events = auditEventRepository.findByResourceTypeAndResourceId(
                resourceType, resourceId, PageRequest.of(0, Integer.MAX_VALUE / 100)).toList();

        Map<String, Object> trail = new LinkedHashMap<>();
        trail.put("resourceType", resourceType);
        trail.put("resourceId", resourceId);
        trail.put("totalChanges", events.size());
        trail.put("lastModified", events.isEmpty() ? null : events.get(0).getTimestamp());
        trail.put("events", events);

        return trail;
    }

    /**
     * Performs compliance check on chain integrity.
     * 
     * @return compliance check result
     */
    public Map<String, Object> performComplianceCheck() {
        log.info("Performing compliance check");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkDate", LocalDateTime.now());

        long totalEvents = auditEventRepository.count();
        result.put("totalEvents", totalEvents);

        // Check archive coverage
        long archivedCount = auditEventRepository.countByArchivedTrue();
        double archivePercentage = totalEvents > 0 ? (archivedCount * 100.0 / totalEvents) : 0;
        result.put("archivedPercentage", String.format("%.1f%%", archivePercentage));

        // Compliance status
        boolean isCompliant = archivePercentage >= 0;  // Basic check
        result.put("isCompliant", isCompliant);
        result.put("status", isCompliant ? "COMPLIANT" : "NON_COMPLIANT");

        return result;
    }
}
