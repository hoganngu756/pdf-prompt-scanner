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
                ? null : response.getVisualObfuscationResult().getFindings()));
        record.setStructureFlags(join(response.getDocumentStructureResult() == null
                ? null : response.getDocumentStructureResult().getFindings()));
        record.setHeuristicFlags(join(response.getHeuristicResult() == null
                ? null : response.getHeuristicResult().getFlags()));
        record.setLlmExplanation(response.getLlmResult() == null
                ? "" : response.getLlmResult().getAnalysis());
        scanRecordRepository.save(record);
    }

    private String join(List<String> findings) {
        return findings == null || findings.isEmpty() ? "" : String.join(" | ", findings);
    }
}
