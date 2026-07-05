package com.promptscanner.backend.controller;

import com.promptscanner.backend.entity.HeuristicRule;
import com.promptscanner.backend.repository.HeuristicRuleRepository;
import com.promptscanner.backend.dto.ScanResponse;
import com.promptscanner.backend.entity.ScanRecord;
import com.promptscanner.backend.repository.ScanRecordRepository;
import com.promptscanner.backend.service.PdfScannerService;
import com.promptscanner.backend.service.HeuristicScannerService;
import com.promptscanner.backend.service.LlmScannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allow frontend deployment domain
public class ScanController {

    private static final Logger log = LoggerFactory.getLogger(ScanController.class);

    private final PdfScannerService pdfScannerService;
    private final HeuristicScannerService heuristicScannerService;
    private final LlmScannerService llmScannerService;
    private final ScanRecordRepository scanRecordRepository;
    private final HeuristicRuleRepository heuristicRuleRepository;

    public ScanController(PdfScannerService pdfScannerService, 
                          HeuristicScannerService heuristicScannerService,
                          LlmScannerService llmScannerService,
                          ScanRecordRepository scanRecordRepository,
                          HeuristicRuleRepository heuristicRuleRepository) {
        this.pdfScannerService = pdfScannerService;
        this.heuristicScannerService = heuristicScannerService;
        this.llmScannerService = llmScannerService;
        this.scanRecordRepository = scanRecordRepository;
        this.heuristicRuleRepository = heuristicRuleRepository;
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
            String fileName = file.getOriginalFilename();
            log.info("Received file: {} | Size: {}", fileName, file.getSize());

            // Extract text and render preview using PDFBox
            PdfScannerService.PdfData pdfData = pdfScannerService.processPdf(file);
            String extractedText = pdfData.extractedText;
            response.setPreviewImagesBase64(pdfData.previewImagesBase64);
            
            if (log.isDebugEnabled()) {
                log.debug("Extracted Text Preview: {}...", extractedText.substring(0, Math.min(extractedText.length(), 200)));
            }

            boolean isOverallSafe = true;
            String hFlagsStr = "";
            String lExplanation = "";
            String vFlagsStr = "";

            // Visual Obfuscation Scan
            List<String> voFindings = pdfData.visualObfuscationFindings;
            boolean isVoSafe = voFindings.isEmpty();
            ScanResponse.VisualObfuscationResult voResult = new ScanResponse.VisualObfuscationResult(isVoSafe, voFindings);
            response.setVisualObfuscationResult(voResult);
            if (!isVoSafe) {
                isOverallSafe = false;
                vFlagsStr = String.join(" | ", voFindings);
            }

            // Heuristic Scan
            if (useHeuristics) {
                ScanResponse.HeuristicResult hResult = heuristicScannerService.scan(extractedText);
                response.setHeuristicResult(hResult);
                if (!hResult.isSafe()) {
                    isOverallSafe = false;
                    hFlagsStr = String.join(", ", hResult.getFlags());
                }
            }

            // LLM Scan
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
            record.setVisualFlags(vFlagsStr);
            scanRecordRepository.save(record);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to process the PDF file", e);
        }
    }

    // --- RULES ENDPOINTS ---

    @GetMapping("/rules")
    public ResponseEntity<List<HeuristicRule>> getRules() {
        return ResponseEntity.ok(heuristicRuleRepository.findAll());
    }

    @PostMapping("/rules")
    public ResponseEntity<HeuristicRule> createRule(@RequestBody HeuristicRule rule) {
        if (rule.getPhrase() == null || rule.getPhrase().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        // Force active by default if not set
        if (!rule.isActive()) {
            rule.setActive(true);
        }
        return ResponseEntity.ok(heuristicRuleRepository.save(rule));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<HeuristicRule> updateRule(@PathVariable("id") Long id, @RequestBody HeuristicRule updatedRule) {
        return heuristicRuleRepository.findById(id)
                .map(rule -> {
                    rule.setPhrase(updatedRule.getPhrase());
                    rule.setRegex(updatedRule.isRegex());
                    rule.setActive(updatedRule.isActive());
                    return ResponseEntity.ok(heuristicRuleRepository.save(rule));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable("id") Long id) {
        if (heuristicRuleRepository.existsById(id)) {
            heuristicRuleRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }
}
