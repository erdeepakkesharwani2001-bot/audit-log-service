package com.schwab.audit.service;

import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceReportingServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private ComplianceReportingService complianceReportingService;

    // ============================================================
    // generateComplianceReport()
    // ============================================================

    @Test
    void generateComplianceReport_shouldGenerateReportWithEvents() {

        LocalDateTime startDate =
                LocalDateTime.of(2026, 1, 1, 0, 0);

        LocalDateTime endDate =
                LocalDateTime.of(2026, 1, 31, 23, 59);

        AuditEvent event1 = createEvent(
                "USER_LOGIN",
                "admin",
                true,
                LocalDateTime.of(2026, 1, 10, 10, 0)
        );

        AuditEvent event2 = createEvent(
                "USER_LOGIN",
                "admin",
                false,
                LocalDateTime.of(2026, 1, 11, 10, 0)
        );

        AuditEvent event3 = createEvent(
                "USER_LOGOUT",
                "user1",
                true,
                LocalDateTime.of(2026, 1, 12, 10, 0)
        );

        Page<AuditEvent> page =
                new PageImpl<>(List.of(event1, event2, event3));

        when(auditEventRepository.findByTimestampRange(
                eq(startDate),
                eq(endDate),
                any(Pageable.class)
        )).thenReturn(page);

        Map<String, Object> result =
                complianceReportingService.generateComplianceReport(
                        startDate,
                        endDate
                );

        assertNotNull(result);

        assertNotNull(result.get("reportGenerated"));

        assertEquals(startDate, result.get("periodStart"));
        assertEquals(endDate, result.get("periodEnd"));

        assertEquals(3, result.get("totalEvents"));

        @SuppressWarnings("unchecked")
        Map<String, Long> eventDistribution =
                (Map<String, Long>) result.get("eventTypeDistribution");

        assertEquals(2L, eventDistribution.get("USER_LOGIN"));
        assertEquals(1L, eventDistribution.get("USER_LOGOUT"));

        @SuppressWarnings("unchecked")
        Map<String, Long> actorActivity =
                (Map<String, Long>) result.get("actorActivity");

        assertEquals(2L, actorActivity.get("admin"));
        assertEquals(1L, actorActivity.get("user1"));

        assertEquals(2L, result.get("archivedEvents"));

        verify(auditEventRepository)
                .findByTimestampRange(
                        eq(startDate),
                        eq(endDate),
                        any(Pageable.class)
                );
    }

    // ============================================================
    // generateComplianceReport() - EMPTY
    // ============================================================

    @Test
    void generateComplianceReport_shouldHandleEmptyEvents() {

        LocalDateTime startDate =
                LocalDateTime.of(2026, 1, 1, 0, 0);

        LocalDateTime endDate =
                LocalDateTime.of(2026, 1, 31, 23, 59);

        Page<AuditEvent> page =
                new PageImpl<>(List.of());

        when(auditEventRepository.findByTimestampRange(
                eq(startDate),
                eq(endDate),
                any(Pageable.class)
        )).thenReturn(page);

        Map<String, Object> result =
                complianceReportingService.generateComplianceReport(
                        startDate,
                        endDate
                );

        assertNotNull(result);

        assertEquals(0, result.get("totalEvents"));
        assertEquals(0L, result.get("archivedEvents"));

        @SuppressWarnings("unchecked")
        Map<String, Long> eventDistribution =
                (Map<String, Long>) result.get("eventTypeDistribution");

        assertTrue(eventDistribution.isEmpty());

        @SuppressWarnings("unchecked")
        Map<String, Long> actorActivity =
                (Map<String, Long>) result.get("actorActivity");

        assertTrue(actorActivity.isEmpty());
    }

    // ============================================================
    // generateUserAuditTrail()
    // ============================================================

    @Test
    void generateUserAuditTrail_shouldReturnUserEvents() {

        String actorId = "admin";
        int days = 30;

        AuditEvent event1 = createEvent(
                "USER_LOGIN",
                actorId,
                false,
                LocalDateTime.now()
        );

        AuditEvent event2 = createEvent(
                "USER_LOGOUT",
                actorId,
                false,
                LocalDateTime.now()
        );

        Page<AuditEvent> page =
                new PageImpl<>(List.of(event1, event2));

        when(auditEventRepository.findByActorAndTimestampRange(
                eq(actorId),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(page);

        Map<String, Object> result =
                complianceReportingService.generateUserAuditTrail(
                        actorId,
                        days
                );

        assertNotNull(result);

        assertEquals(actorId, result.get("actorId"));
        assertEquals(days, result.get("periodDays"));
        assertEquals(2, result.get("totalActions"));

        @SuppressWarnings("unchecked")
        List<AuditEvent> events =
                (List<AuditEvent>) result.get("events");

        assertEquals(2, events.size());
        assertSame(event1, events.get(0));
        assertSame(event2, events.get(1));

        verify(auditEventRepository)
                .findByActorAndTimestampRange(
                        eq(actorId),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        any(Pageable.class)
                );
    }

    // ============================================================
    // generateUserAuditTrail() - EMPTY
    // ============================================================

    @Test
    void generateUserAuditTrail_shouldHandleNoEvents() {

        String actorId = "unknown";
        int days = 7;

        Page<AuditEvent> page =
                new PageImpl<>(List.of());

        when(auditEventRepository.findByActorAndTimestampRange(
                eq(actorId),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Pageable.class)
        )).thenReturn(page);

        Map<String, Object> result =
                complianceReportingService.generateUserAuditTrail(
                        actorId,
                        days
                );

        assertNotNull(result);

        assertEquals(actorId, result.get("actorId"));
        assertEquals(days, result.get("periodDays"));
        assertEquals(0, result.get("totalActions"));

        @SuppressWarnings("unchecked")
        List<AuditEvent> events =
                (List<AuditEvent>) result.get("events");

        assertTrue(events.isEmpty());
    }

    // ============================================================
    // generateResourceAuditTrail() - WITH EVENTS
    // ============================================================

    @Test
    void generateResourceAuditTrail_shouldReturnResourceEvents() {

        String resourceType = "APPLICATION";
        String resourceId = "APP-001";

        LocalDateTime timestamp =
                LocalDateTime.of(2026, 1, 15, 12, 0);

        AuditEvent event1 = createEvent(
                "CONFIG_UPDATE",
                "admin",
                false,
                timestamp
        );

        AuditEvent event2 = createEvent(
                "USER_LOGIN",
                "user1",
                false,
                timestamp.minusDays(1)
        );

        Page<AuditEvent> page =
                new PageImpl<>(List.of(event1, event2));

        when(auditEventRepository.findByResourceTypeAndResourceId(
                eq(resourceType),
                eq(resourceId),
                any(Pageable.class)
        )).thenReturn(page);

        Map<String, Object> result =
                complianceReportingService.generateResourceAuditTrail(
                        resourceType,
                        resourceId
                );

        assertNotNull(result);

        assertEquals(resourceType, result.get("resourceType"));
        assertEquals(resourceId, result.get("resourceId"));

        assertEquals(2, result.get("totalChanges"));

        assertEquals(
                timestamp,
                result.get("lastModified")
        );

        @SuppressWarnings("unchecked")
        List<AuditEvent> events =
                (List<AuditEvent>) result.get("events");

        assertEquals(2, events.size());
        assertSame(event1, events.get(0));
        assertSame(event2, events.get(1));

        verify(auditEventRepository)
                .findByResourceTypeAndResourceId(
                        eq(resourceType),
                        eq(resourceId),
                        any(Pageable.class)
                );
    }

    // ============================================================
    // generateResourceAuditTrail() - EMPTY
    // Covers:
    //
    // events.isEmpty() ? null : events.get(0).getTimestamp()
    // ============================================================

    @Test
    void generateResourceAuditTrail_shouldHandleNoEvents() {

        String resourceType = "APPLICATION";
        String resourceId = "DOES-NOT-EXIST";

        Page<AuditEvent> page =
                new PageImpl<>(List.of());

        when(auditEventRepository.findByResourceTypeAndResourceId(
                eq(resourceType),
                eq(resourceId),
                any(Pageable.class)
        )).thenReturn(page);

        Map<String, Object> result =
                complianceReportingService.generateResourceAuditTrail(
                        resourceType,
                        resourceId
                );

        assertNotNull(result);

        assertEquals(resourceType, result.get("resourceType"));
        assertEquals(resourceId, result.get("resourceId"));

        assertEquals(0, result.get("totalChanges"));

        assertNull(result.get("lastModified"));

        @SuppressWarnings("unchecked")
        List<AuditEvent> events =
                (List<AuditEvent>) result.get("events");

        assertTrue(events.isEmpty());
    }

    // ============================================================
    // performComplianceCheck()
    // TOTAL EVENTS > 0
    // Covers archivePercentage calculation
    // ============================================================

    @Test
    void performComplianceCheck_shouldReturnCompliantResult() {

        when(auditEventRepository.count())
                .thenReturn(100L);

        when(auditEventRepository.countByArchivedTrue())
                .thenReturn(25L);

        Map<String, Object> result =
                complianceReportingService.performComplianceCheck();

        assertNotNull(result);

        assertNotNull(result.get("checkDate"));

        assertEquals(100L, result.get("totalEvents"));

        assertEquals(
                "25.0%",
                result.get("archivedPercentage")
        );

        assertEquals(
                true,
                result.get("isCompliant")
        );

        assertEquals(
                "COMPLIANT",
                result.get("status")
        );

        verify(auditEventRepository).count();
        verify(auditEventRepository).countByArchivedTrue();
    }

    // ============================================================
    // performComplianceCheck()
    // TOTAL EVENTS = 0
    //
    // Covers:
    // totalEvents > 0 ? ... : 0
    // ============================================================

    @Test
    void performComplianceCheck_shouldHandleZeroEvents() {

        when(auditEventRepository.count())
                .thenReturn(0L);

        when(auditEventRepository.countByArchivedTrue())
                .thenReturn(0L);

        Map<String, Object> result =
                complianceReportingService.performComplianceCheck();

        assertNotNull(result);

        assertNotNull(result.get("checkDate"));

        assertEquals(
                0L,
                result.get("totalEvents")
        );

        assertEquals(
                "0.0%",
                result.get("archivedPercentage")
        );

        assertEquals(
                true,
                result.get("isCompliant")
        );

        assertEquals(
                "COMPLIANT",
                result.get("status")
        );

        verify(auditEventRepository).count();
        verify(auditEventRepository).countByArchivedTrue();
    }

    // ============================================================
    // Helper method
    // ============================================================

    private AuditEvent createEvent(
            String eventType,
            String actorId,
            boolean archived,
            LocalDateTime timestamp) {

        return AuditEvent.builder()
                .id(1L)
                .eventType(eventType)
                .actorId(actorId)
                .resourceType("APPLICATION")
                .resourceId("APP-001")
                .payload("{}")
                .timestamp(timestamp)
                .contentHash("a".repeat(64))
                .previousHash("GENESIS_HASH")
                .chainPosition(1L)
                .archived(archived)
                .build();
    }
}