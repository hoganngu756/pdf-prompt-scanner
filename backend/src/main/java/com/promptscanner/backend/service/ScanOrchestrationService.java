package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.ScanResponse;
import com.promptscanner.backend.entity.ScanRecord;
import com.promptscanner.backend.repository.ScanRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScanOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ScanOrchestrationService.class);

    private final PdfScannerService pdfScannerService;
    private final HeuristicScannerService heuristicScannerService;
    private final LlmScannerService llmScannerService;
    private final ScanRecordRepository scanRecordRepository;

    public ScanOrchestrationService(PdfScannerService pdfScannerService,
                                    HeuristicScannerService heuristicScannerService,
                                    LlmScannerService llmScannerService,
                                    ScanRecordRepository scanRecordRepository) {
        this.pdfScannerService = pdfScannerService;
        this.heuristicScannerService = heuristicScannerService;
        this.llmScannerService = llmScannerService;
        this.scanRecordRepository = scanRecordRepository;
    }

    public ScanResponse orchestrateScan(MultipartFile file, boolean useLLM, boolean useHeuristics) throws IOException {
        ScanResponse response = new ScanResponse();
        String fileName = file.getOriginalFilename();
        log.info("Orchestrating scan for file: {} | Size: {} bytes", fileName, file.getSize());

        // Extract text and render preview using PDFBox
        PdfScannerService.PdfData pdfData = pdfScannerService.processPdf(file);
        String extractedText = pdfData.extractedText();
        response.setPreviewImagesBase64(pdfData.previewImagesBase64());
        
        if (log.isDebugEnabled() && extractedText != null) {
            log.debug("Extracted Text Preview: {}...", extractedText.substring(0, Math.min(extractedText.length(), 200)));
        }

        boolean isOverallSafe = true;
        String hFlagsStr = "";
        String lExplanation = "";
        String vFlagsStr = "";

        // Visual Obfuscation Scan
        List<String> voFindings = pdfData.visualObfuscationFindings();
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
            if (!lResult.isSafe()) {
                isOverallSafe = false;
            }
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

        return response;
    }
}
