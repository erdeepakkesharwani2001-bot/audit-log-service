package com.schwab.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.audit.entity.AuditEvent;
import com.schwab.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for exporting audit events in various formats.
 * 
 * Supports: JSON, CSV, and detailed audit reports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ExportService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Exports all events as JSON array.
     * 
     * @return JSON string
     */
    public String exportAsJson() {
        log.debug("Exporting all events as JSON");

        List<AuditEvent> events = auditEventRepository.findAll(PageRequest.of(0, Integer.MAX_VALUE / 100))
                .toList();

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(events);
        } catch (Exception e) {
            log.error("Failed to export as JSON", e);
            throw new RuntimeException("Export failed", e);
        }
    }

    /**
     * Exports events as CSV format.
     * 
     * @return CSV string
     */
    public String exportAsCSV() {
        log.debug("Exporting all events as CSV");

        StringWriter sw = new StringWriter();
        
        // CSV header
        sw.append("ID,Event Type,Actor ID,Resource Type,Resource ID,Timestamp,Chain Position,Archived\n");

        // Fetch events and write rows
        List<AuditEvent> events = auditEventRepository.findAll(PageRequest.of(0, Integer.MAX_VALUE / 100))
                .toList();

        for (AuditEvent event : events) {
            sw.append(String.format(
                    "%d,%s,%s,%s,%s,%s,%d,%s\n",
                    event.getId(),
                    escapeCSV(event.getEventType()),
                    escapeCSV(event.getActorId()),
                    escapeCSV(event.getResourceType()),
                    escapeCSV(event.getResourceId()),
                    event.getTimestamp(),
                    event.getChainPosition(),
                    event.getArchived()
            ));
        }

        return sw.toString();
    }

    /**
     * Generates an audit report with statistics.
     * 
     * @return report string
     */
    public String generateAuditReport() {
        log.debug("Generating audit report");

        StringBuilder report = new StringBuilder();
        report.append("=== AUDIT LOG REPORT ===\n");
        report.append("Generated: ").append(LocalDateTime.now()).append("\n\n");

        long totalEvents = auditEventRepository.count();
        long archivedCount = auditEventRepository.countByArchivedTrue();
        long unarchivedCount = auditEventRepository.countByArchivedFalse();

        report.append(String.format("Total Events: %d\n", totalEvents));
        report.append(String.format("Archived Events: %d (%.1f%%)\n", archivedCount, 
                totalEvents > 0 ? (archivedCount * 100.0 / totalEvents) : 0));
        report.append(String.format("Active Events: %d (%.1f%%)\n", unarchivedCount,
                totalEvents > 0 ? (unarchivedCount * 100.0 / totalEvents) : 0));

        return report.toString();
    }

    /**
     * Escapes CSV field values.
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
