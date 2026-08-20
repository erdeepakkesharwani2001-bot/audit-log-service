package com.schwab.audit.controller;

import com.schwab.audit.service.ComplianceReportingService;
import com.schwab.audit.service.ExportService;
import com.schwab.audit.service.RedactionService;
import com.schwab.audit.service.RetentionPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceControllerTest {

    @Mock
    private ComplianceReportingService complianceReportingService;

    @Mock
    private RetentionPolicyService retentionPolicyService;

    @Mock
    private RedactionService redactionService;

    @Mock
    private ExportService exportService;

    @InjectMocks
    private ComplianceController controller;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @BeforeEach
    void setUp() {
        startDate = LocalDateTime.of(2026, 1, 1, 0, 0);
        endDate = LocalDateTime.of(2026, 1, 31, 23, 59);
    }

    // ============================================================
    // generateComplianceReport()
    // ============================================================

    @Test
    void generateComplianceReport_shouldReturnReport() {

        Map<String, Object> report = Map.of(
                "totalEvents", 100L,
                "compliant", true
        );

        when(complianceReportingService.generateComplianceReport(
                startDate,
                endDate
        )).thenReturn(report);

        ResponseEntity<Map<String, Object>> response =
                controller.generateComplianceReport(startDate, endDate);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(report, response.getBody());

        verify(complianceReportingService)
                .generateComplianceReport(startDate, endDate);
    }

    // ============================================================
    // generateUserAuditTrail()
    // ============================================================

    @Test
    void generateUserAuditTrail_shouldReturnTrail() {

        String actorId = "newadmin";
        int days = 30;

        Map<String, Object> trail = Map.of(
                "actorId", actorId,
                "events", 10
        );

        when(complianceReportingService.generateUserAuditTrail(
                actorId,
                days
        )).thenReturn(trail);

        ResponseEntity<Map<String, Object>> response =
                controller.generateUserAuditTrail(actorId, days);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(trail, response.getBody());

        verify(complianceReportingService)
                .generateUserAuditTrail(actorId, days);
    }

    @Test
    void generateUserAuditTrail_shouldWorkWithDifferentDays() {

        String actorId = "admin";
        int days = 365;

        Map<String, Object> trail = Map.of(
                "actorId", actorId,
                "days", days
        );

        when(complianceReportingService.generateUserAuditTrail(
                actorId,
                days
        )).thenReturn(trail);

        ResponseEntity<Map<String, Object>> response =
                controller.generateUserAuditTrail(actorId, days);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(trail, response.getBody());

        verify(complianceReportingService)
                .generateUserAuditTrail(actorId, days);
    }

    // ============================================================
    // generateResourceAuditTrail()
    // ============================================================

    @Test
    void generateResourceAuditTrail_shouldReturnTrail() {

        String resourceType = "APPLICATION";
        String resourceId = "APP-001";

        Map<String, Object> trail = Map.of(
                "resourceType", resourceType,
                "resourceId", resourceId,
                "events", 5
        );

        when(complianceReportingService.generateResourceAuditTrail(
                resourceType,
                resourceId
        )).thenReturn(trail);

        ResponseEntity<Map<String, Object>> response =
                controller.generateResourceAuditTrail(
                        resourceType,
                        resourceId
                );

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(trail, response.getBody());

        verify(complianceReportingService)
                .generateResourceAuditTrail(resourceType, resourceId);
    }

    // ============================================================
    // performComplianceCheck()
    // ============================================================

    @Test
    void performComplianceCheck_shouldReturnResult() {

        Map<String, Object> result = Map.of(
                "status", "PASS",
                "issues", 0
        );

        when(complianceReportingService.performComplianceCheck())
                .thenReturn(result);

        ResponseEntity<Map<String, Object>> response =
                controller.performComplianceCheck();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(result, response.getBody());

        verify(complianceReportingService)
                .performComplianceCheck();
    }

    // ============================================================
    // applyRetentionPolicy()
    // ============================================================

    @Test
    void applyRetentionPolicy_shouldArchiveEvents() {

        int retentionDays = 365;

        when(retentionPolicyService.archiveEventsOlderThan(any(LocalDateTime.class)))
                .thenReturn(25L);

        ResponseEntity<Map<String, String>> response =
                controller.applyRetentionPolicy(retentionDays);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        assertNotNull(response.getBody());

        assertEquals(
                "Retention policy applied",
                response.getBody().get("message")
        );

        assertEquals(
                "25",
                response.getBody().get("archivedCount")
        );

        verify(retentionPolicyService)
                .archiveEventsOlderThan(any(LocalDateTime.class));
    }

    @Test
    void applyRetentionPolicy_shouldHandleZeroArchivedEvents() {

        when(retentionPolicyService.archiveEventsOlderThan(any(LocalDateTime.class)))
                .thenReturn(0L);

        ResponseEntity<Map<String, String>> response =
                controller.applyRetentionPolicy(30);

        assertEquals(200, response.getStatusCode().value());

        assertEquals(
                "0",
                response.getBody().get("archivedCount")
        );

        verify(retentionPolicyService)
                .archiveEventsOlderThan(any(LocalDateTime.class));
    }

    // ============================================================
    // redactEvent()
    // ============================================================

    @Test
    void redactEvent_shouldRedactFields() throws Exception {

        Long eventId = 100L;

        List<String> fields = List.of(
                "password",
                "ssn",
                "creditCard"
        );

        String reason = "Sensitive information";
        String redactedBy = "admin";

        Map<String, Object> request = Map.of(
                "fields", fields,
                "reason", reason,
                "redactedBy", redactedBy
        );

        ResponseEntity<Map<String, String>> response =
                controller.redactEvent(eventId, request);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());

        assertEquals(
                "Event redacted successfully",
                response.getBody().get("message")
        );

        verify(redactionService).redactEvent(
                eventId,
                fields,
                reason,
                redactedBy
        );
    }

    @Test
    void redactEvent_shouldHandleSingleField() throws Exception {

        Long eventId = 1L;

        List<String> fields = List.of("password");

        Map<String, Object> request = Map.of(
                "fields", fields,
                "reason", "PII removal",
                "redactedBy", "admin"
        );

        ResponseEntity<Map<String, String>> response =
                controller.redactEvent(eventId, request);

        assertEquals(200, response.getStatusCode().value());

        verify(redactionService).redactEvent(
                eq(eventId),
                eq(fields),
                eq("PII removal"),
                eq("admin")
        );
    }

    // ============================================================
    // exportAsJson()
    // ============================================================

    @Test
    void exportAsJson_shouldReturnJsonFile() {

        String json = """
                [
                  {
                    "id": 1,
                    "eventType": "USER_LOGIN"
                  }
                ]
                """;

        when(exportService.exportAsJson())
                .thenReturn(json);

        ResponseEntity<String> response =
                controller.exportAsJson();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(json, response.getBody());

        assertEquals(
                "attachment; filename=audit-events.json",
                response.getHeaders().getFirst("Content-Disposition")
        );

        verify(exportService).exportAsJson();
    }

    // ============================================================
    // exportAsCSV()
    // ============================================================

    @Test
    void exportAsCSV_shouldReturnCsvFile() {

        String csv = """
                id,eventType,actorId
                1,USER_LOGIN,admin
                """;

        when(exportService.exportAsCSV())
                .thenReturn(csv);

        ResponseEntity<String> response =
                controller.exportAsCSV();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(csv, response.getBody());

        assertEquals(
                "attachment; filename=audit-events.csv",
                response.getHeaders().getFirst("Content-Disposition")
        );

        assertEquals(
                "text/csv",
                response.getHeaders().getFirst("Content-Type")
        );

        verify(exportService).exportAsCSV();
    }

    // ============================================================
    // generateAuditReport()
    // ============================================================

    @Test
    void generateAuditReport_shouldReturnReport() {

        String report = """
                AUDIT REPORT
                Total Events: 100
                Status: COMPLIANT
                """;

        when(exportService.generateAuditReport())
                .thenReturn(report);

        ResponseEntity<String> response =
                controller.generateAuditReport();

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(report, response.getBody());

        verify(exportService).generateAuditReport();
    }
}