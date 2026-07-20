package com.promptscanner.backend.controller;

import com.promptscanner.backend.dto.ScanResponse;
import com.promptscanner.backend.entity.ScanRecord;
import com.promptscanner.backend.repository.ScanRecordRepository;
import com.promptscanner.backend.service.ScanOrchestrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ScanController {

    private final ScanOrchestrationService scanOrchestrationService;
    private final ScanRecordRepository scanRecordRepository;

    public ScanController(ScanOrchestrationService scanOrchestrationService, 
                          ScanRecordRepository scanRecordRepository) {
        this.scanOrchestrationService = scanOrchestrationService;
        this.scanRecordRepository = scanRecordRepository;
    }

    @GetMapping("/history")
    public ResponseEntity<List<ScanRecord>> getHistory() {
        return ResponseEntity.ok(scanRecordRepository.findAllByOrderByScanDateDesc());
    }

    @PostMapping("/scan")
    public ResponseEntity<ScanResponse> scanPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "useLLM", defaultValue = "false") boolean useLLM,
            @RequestParam(value = "useHeuristics", defaultValue = "true") boolean useHeuristics
    ) {
        ScanResponse response = new ScanResponse();

        if (file.isEmpty()) {
            response.setError("File is empty or missing.");
            return ResponseEntity.badRequest().body(response);
        }

        // Security: Validate file type
        String contentType = file.getContentType();
        String originalName = file.getOriginalFilename();
        if (contentType == null || !contentType.equals("application/pdf") || 
            originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            response.setError("Invalid file type. Only PDF files are accepted.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            ScanResponse scanResponse = scanOrchestrationService.orchestrateScan(file, useLLM, useHeuristics);
            return ResponseEntity.ok(scanResponse);
        } catch (IllegalArgumentException e) {
            response.setError(e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process the PDF file", e);
        }
    }
}
