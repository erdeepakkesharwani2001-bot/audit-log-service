package com.schwab.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExportService unit tests")
class ExportServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExportService exportService;

    @Test
    @DisplayName("exports CSV with correctly escaped special characters")
    void exportAsCsvEscapesSpecialCharacters() {
        AuditEvent event = AuditEvent.builder()
                .id(42L)
                .eventType("ACCOUNT,UPDATED")
                .actorId("user\"one")
                .resourceType("ACCOUNT")
                .resourceId("account\n42")
                .timestamp(LocalDateTime.of(2026, 8, 18, 10, 15))
                .chainPosition(7L)
                .archived(false)
                .build();
        when(auditEventRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(event)));

        String csv = exportService.exportAsCSV();

        assertEquals("ID,Event Type,Actor ID,Resource Type,Resource ID,Timestamp,Chain Position,Archived\n"
                        + "42,\"ACCOUNT,UPDATED\",\"user\"\"one\",ACCOUNT,\"account\n42\",2026-08-18T10:15,7,false\n",
                csv);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditEventRepository).findAll(pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
    }

    @Test
    @DisplayName("wraps JSON serialization failures in an export exception")
    void exportAsJsonWrapsSerializationFailure() throws Exception {
        when(auditEventRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenThrow(new IllegalStateException("serializer unavailable"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> exportService.exportAsJson());

        assertEquals("Export failed", exception.getMessage());
        assertEquals("serializer unavailable", exception.getCause().getMessage());
    }

    @Test
    @DisplayName("includes totals and percentages in the audit report")
    void generateAuditReportIncludesSummaryStatistics() {
        when(auditEventRepository.count()).thenReturn(4L);
        when(auditEventRepository.countByArchivedTrue()).thenReturn(1L);
        when(auditEventRepository.countByArchivedFalse()).thenReturn(3L);

        String report = exportService.generateAuditReport();

        org.junit.jupiter.api.Assertions.assertAll(
                () -> org.junit.jupiter.api.Assertions.assertTrue(report.contains("Total Events: 4")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(report.contains("Archived Events: 1 (25.0%)")),
                () -> org.junit.jupiter.api.Assertions.assertTrue(report.contains("Active Events: 3 (75.0%)"))
        );
    }
}
