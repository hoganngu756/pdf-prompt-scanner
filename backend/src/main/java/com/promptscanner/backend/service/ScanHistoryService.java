package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.ScanResponse;
import com.promptscanner.backend.entity.ScanRecord;
import com.promptscanner.backend.repository.ScanRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Owns the audit trail. Recording a scan is a separate concern from running one,
 * so the orchestrator no longer builds entities or talks to a repository.
 */
@Service
public class ScanHistoryService {

    /** Must match the @Column(length = ...) declarations on ScanRecord. */
    private static final int FLAGS_MAX = 2000;
    private static final int EXPLANATION_MAX = 4000;

    private final ScanRecordRepository scanRecordRepository;

    public ScanHistoryService(ScanRecordRepository scanRecordRepository) {
        this.scanRecordRepository = scanRecordRepository;
    }

    public void record(String fileName, boolean safe, ScanResponse response) {
        ScanRecord record = new ScanRecord();
        record.setFileName(fileName);
        record.setScanDate(LocalDateTime.now());
        record.setSafe(safe);
        record.setVisualFlags(join(response.getVisualObfuscationResult() == null
                ? null : response.getVisualObfuscationResult().getFindings(), FLAGS_MAX));
        record.setStructureFlags(join(response.getDocumentStructureResult() == null
                ? null : response.getDocumentStructureResult().getFindings(), FLAGS_MAX));
        record.setHeuristicFlags(join(response.getHeuristicResult() == null
                ? null : response.getHeuristicResult().getFlags(), FLAGS_MAX));
        String analysis = response.getLlmResult() == null ? "" : response.getLlmResult().getAnalysis();
        record.setLlmExplanation(analysis == null || analysis.length() <= EXPLANATION_MAX
                ? analysis : analysis.substring(0, EXPLANATION_MAX - 1) + "\u2026");
        scanRecordRepository.save(record);
    }

    /**
     * SQLite ignores VARCHAR lengths, so an over-long value stores fine today —
     * but on any other engine the insert would fail and the audit record would be
     * lost. Truncating here keeps the record, which matters more than the tail.
     */
    private String join(List<String> findings, int maxLength) {
        if (findings == null || findings.isEmpty()) {
            return "";
        }
        String joined = String.join(" | ", findings);
        return joined.length() <= maxLength ? joined : joined.substring(0, maxLength - 1) + "\u2026";
    }
}
