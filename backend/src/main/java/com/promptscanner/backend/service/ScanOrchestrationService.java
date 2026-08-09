package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.ScanResponse;
import com.promptscanner.backend.entity.HeuristicRule;
import com.promptscanner.backend.repository.HeuristicRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Runs the enabled checks over one document and assembles the response.
 *
 * This is the only place that knows about both rules and PDFs: it reads the
 * active rule set and hands the derived highlight words down to extraction, so
 * the lower-level services stay free of persistence concerns.
 */
@Service
public class ScanOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ScanOrchestrationService.class);

    private final PdfScannerService pdfScannerService;
    private final HeuristicScannerService heuristicScannerService;
    private final LlmScannerService llmScannerService;
    private final HeuristicRuleRepository heuristicRuleRepository;
    private final ScanHistoryService scanHistoryService;

    public ScanOrchestrationService(PdfScannerService pdfScannerService,
                                    HeuristicScannerService heuristicScannerService,
                                    LlmScannerService llmScannerService,
                                    HeuristicRuleRepository heuristicRuleRepository,
                                    ScanHistoryService scanHistoryService) {
        this.pdfScannerService = pdfScannerService;
        this.heuristicScannerService = heuristicScannerService;
        this.llmScannerService = llmScannerService;
        this.heuristicRuleRepository = heuristicRuleRepository;
        this.scanHistoryService = scanHistoryService;
    }

    public ScanResponse orchestrateScan(MultipartFile file, boolean useLLM, boolean useHeuristics)
            throws IOException {
        ScanResponse response = new ScanResponse();
        String fileName = file.getOriginalFilename();
        log.info("Orchestrating scan for file: {} | Size: {} bytes", fileName, file.getSize());

        List<String> literalPhrases = heuristicRuleRepository.findByIsActiveTrue().stream()
                .filter(rule -> !rule.isRegex())
                .map(HeuristicRule::getPhrase)
                .toList();

        PdfScannerService.PdfData pdfData =
                pdfScannerService.processPdf(file, PdfScannerService.highlightWordsFrom(literalPhrases));

        String extractedText = pdfData.extractedText();
        response.setPreviewImagesBase64(pdfData.previewImagesBase64());
        response.setPreviewPageNumbers(pdfData.previewPageNumbers());

        boolean isOverallSafe = true;

        // Visual obfuscation
        List<String> voFindings = pdfData.visualObfuscationFindings();
        response.setVisualObfuscationResult(
                new ScanResponse.VisualObfuscationResult(voFindings.isEmpty(), voFindings));
        if (!voFindings.isEmpty()) {
            isOverallSafe = false;
        }

        // Document structure: metadata, annotations, active content
        List<String> structureFindings = pdfData.structureFindings();
        response.setDocumentStructureResult(
                new ScanResponse.DocumentStructureResult(structureFindings.isEmpty(), structureFindings));
        if (!structureFindings.isEmpty()) {
            isOverallSafe = false;
        }

        if (useHeuristics) {
            ScanResponse.HeuristicResult hResult = heuristicScannerService.scan(extractedText);
            response.setHeuristicResult(hResult);
            if (!hResult.isSafe()) {
                isOverallSafe = false;
            }
        }

        if (useLLM) {
            ScanResponse.LlmResult lResult = llmScannerService.scan(extractedText);
            response.setLlmResult(lResult);
            if (!lResult.isSafe()) {
                isOverallSafe = false;
            }
        }

        scanHistoryService.record(fileName, isOverallSafe, response);
        return response;
    }
}
