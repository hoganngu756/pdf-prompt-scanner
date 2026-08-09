package com.promptscanner.backend.dto;

import com.promptscanner.backend.entity.ScanRecord;

import java.time.LocalDateTime;

/**
 * Outbound shape for a history record.
 *
 * {@code scanDate} is serialised here as an ISO-8601 string rather than left as
 * a {@link LocalDateTime}. Exposing the entity meant the endpoint depended on
 * Jackson having JavaTimeModule registered, and when a stray ObjectMapper bean
 * displaced Spring Boot's, the whole endpoint returned 500 in production. A
 * String cannot fail to serialise.
 */
public record ScanRecordResponse(
        Long id,
        String fileName,
        String scanDate,
        boolean safe,
        String heuristicFlags,
        String llmExplanation,
        String visualFlags,
        String structureFlags
) {

    public static ScanRecordResponse from(ScanRecord record) {
        LocalDateTime scanned = record.getScanDate();
        return new ScanRecordResponse(
                record.getId(),
                record.getFileName(),
                scanned == null ? null : scanned.toString(),
                record.isSafe(),
                record.getHeuristicFlags(),
                record.getLlmExplanation(),
                record.getVisualFlags(),
                record.getStructureFlags());
    }
}
