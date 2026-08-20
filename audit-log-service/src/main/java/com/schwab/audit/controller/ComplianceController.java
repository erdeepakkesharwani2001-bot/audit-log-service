package com.schwab.audit.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.schwab.audit.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST Controller for compliance reporting and scenario B endpoints.
 * 
 * Requires ADMIN role for all operations.
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Compliance & Advanced Features", description = "Compliance reporting and scenario B/C endpoints")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class ComplianceController {

    private final ComplianceReportingService complianceReportingService;
    private final RetentionPolicyService retentionPolicyService;
    private final RedactionService redactionService;
    private final ExportService exportService;

    /**
     * Generates a compliance report for a date range.
     */
    @GetMapping("/reports/compliance")
    @Operation(summary = "Generate compliance report")
    public ResponseEntity<Map<String, Object>> generateComplianceReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        log.info("Generating compliance report: {} to {}", startDate, endDate);
        Map<String, Object> report = complianceReportingService.generateComplianceReport(startDate, endDate);
        return ResponseEntity.ok(report);
    }

    /**
     * Generates user audit trail.
     */
    @GetMapping("/reports/user-audit-trail")
    @Operation(summary = "Generate user audit trail")
    public ResponseEntity<Map<String, Object>> generateUserAuditTrail(
            @RequestParam String actorId,
            @RequestParam(defaultValue = "30") int days) {
        
        log.info("Generating audit trail for user: {}", actorId);
        Map<String, Object> trail = complianceReportingService.generateUserAuditTrail(actorId, days);
        return ResponseEntity.ok(trail);
    }

    /**
     * Generates resource audit trail.
     */
    @GetMapping("/reports/resource-audit-trail")
    @Operation(summary = "Generate resource audit trail")
    public ResponseEntity<Map<String, Object>> generateResourceAuditTrail(
            @RequestParam String resourceType,
            @RequestParam String resourceId) {
        
        log.info("Generating audit trail for resource: {}/{}", resourceType, resourceId);
        Map<String, Object> trail = complianceReportingService.generateResourceAuditTrail(resourceType, resourceId);
        return ResponseEntity.ok(trail);
    }

    /**
     * Performs compliance check.
     */
    @PostMapping("/check")
    @Operation(summary = "Perform compliance check")
    public ResponseEntity<Map<String, Object>> performComplianceCheck() {
        log.info("Performing compliance check");
        Map<String, Object> result = complianceReportingService.performComplianceCheck();
        return ResponseEntity.ok(result);
    }

    /**
     * Applies retention policy.
     */
    @PostMapping("/retention/apply")
    @Operation(summary = "Apply retention policy")
    public ResponseEntity<Map<String, String>> applyRetentionPolicy(
            @RequestParam(defaultValue = "365") int retentionDays) {
        
        log.info("Applying retention policy - {} days", retentionDays);
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        long archivedCount = retentionPolicyService.archiveEventsOlderThan(cutoffDate);
        
        return ResponseEntity.ok(Map.of("message", "Retention policy applied", "archivedCount", String.valueOf(archivedCount)));
    }

    /**
     * Redacts event fields.
     */
    @PostMapping("/redact/{eventId}")
    @Operation(summary = "Redact event fields")
    public ResponseEntity<Map<String, String>> redactEvent(
            @PathVariable Long eventId,
            @RequestBody Map<String, Object> request) throws JsonProcessingException {
        
        log.info("Redacting event: {}", eventId);
        
        @SuppressWarnings("unchecked")
        java.util.List<String> fields = (java.util.List<String>) request.get("fields");
        String reason = (String) request.get("reason");
        String redactedBy = (String) request.get("redactedBy");
        
        redactionService.redactEvent(eventId, fields, reason, redactedBy);
        
        return ResponseEntity.ok(Map.of("message", "Event redacted successfully"));
    }

    /**
     * Exports events as JSON.
     */
    @GetMapping("/export/json")
    @Operation(summary = "Export events as JSON")
    public ResponseEntity<String> exportAsJson() {
        log.info("Exporting events as JSON");
        String json = exportService.exportAsJson();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=audit-events.json")
                .body(json);
    }

    /**
     * Exports events as CSV.
     */
    @GetMapping("/export/csv")
    @Operation(summary = "Export events as CSV")
    public ResponseEntity<String> exportAsCSV() {
        log.info("Exporting events as CSV");
        String csv = exportService.exportAsCSV();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=audit-events.csv")
                .header("Content-Type", "text/csv")
                .body(csv);
    }

    /**
     * Generates audit report.
     */
    @GetMapping("/reports/audit")
    @Operation(summary = "Generate audit report")
    public ResponseEntity<String> generateAuditReport() {
        log.info("Generating audit report");
        String report = exportService.generateAuditReport();
        return ResponseEntity.ok(report);
    }
}
