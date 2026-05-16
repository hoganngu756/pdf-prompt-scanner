package com.promptscanner.backend.controller;

import com.promptscanner.backend.dto.ScanResponse;
import com.promptscanner.backend.entity.ScanRecord;
import com.promptscanner.backend.repository.ScanRecordRepository;
import com.promptscanner.backend.service.PdfScannerService;
import com.promptscanner.backend.service.HeuristicScannerService;
import com.promptscanner.backend.service.LlmScannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173") // Vite default port
public class ScanController {

    private final PdfScannerService pdfScannerService;
    private final HeuristicScannerService heuristicScannerService;
    private final LlmScannerService llmScannerService;
    private final ScanRecordRepository scanRecordRepository;

    public ScanController(PdfScannerService pdfScannerService, 
                          HeuristicScannerService heuristicScannerService,
                          LlmScannerService llmScannerService,
                          ScanRecordRepository scanRecordRepository) {
        this.pdfScannerService = pdfScannerService;
        this.heuristicScannerService = heuristicScannerService;
        this.llmScannerService = llmScannerService;
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

        try {
            String fileName = file.getOriginalFilename();
            System.out.println("Received file: " + fileName + " | Size: " + file.getSize());

            // Extract text and render preview using PDFBox
            PdfScannerService.PdfData pdfData = pdfScannerService.processPdf(file);
            String extractedText = pdfData.extractedText;
            response.setPreviewImagesBase64(pdfData.previewImagesBase64);
            
            System.out.println("--- Extracted Text Preview ---");
            System.out.println(extractedText.substring(0, Math.min(extractedText.length(), 200)) + "...");
            System.out.println("------------------------------");

            boolean isOverallSafe = true;
            String hFlagsStr = "";
            String lExplanation = "";

            // PHASE 3: Heuristic Scan
            if (useHeuristics) {
                ScanResponse.HeuristicResult hResult = heuristicScannerService.scan(extractedText);
                response.setHeuristicResult(hResult);
                if (!hResult.isSafe()) {
                    isOverallSafe = false;
                    hFlagsStr = String.join(", ", hResult.getFlags());
                }
            }

            // PHASE 3: LLM Scan
            if (useLLM) {
                ScanResponse.LlmResult lResult = llmScannerService.scan(extractedText);
                response.setLlmResult(lResult);
                if (!lResult.isSafe()) isOverallSafe = false;
                lExplanation = lResult.getAnalysis();
            }

            // Save to database
            ScanRecord record = new ScanRecord();
            record.setFileName(fileName);
            record.setScanDate(LocalDateTime.now());
            record.setSafe(isOverallSafe);
            record.setHeuristicFlags(hFlagsStr);
            record.setLlmExplanation(lExplanation);
            scanRecordRepository.save(record);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.setError("Failed to process the PDF file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
