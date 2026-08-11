package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.ScanResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Runs the enabled checks over one document and assembles the response.
 *
 * Nothing is persisted: scan results are returned to the caller and forgotten.
 * The browser keeps its own local history, so no record of anyone's document
 * ever touches the server.
 */
@Service
public class ScanOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(ScanOrchestrationService.class);

    private final PdfScannerService pdfScannerService;
    private final HeuristicScannerService heuristicScannerService;
    private final LlmScannerService llmScannerService;

    public ScanOrchestrationService(PdfScannerService pdfScannerService,
                                    HeuristicScannerService heuristicScannerService,
                                    LlmScannerService llmScannerService) {
        this.pdfScannerService = pdfScannerService;
        this.heuristicScannerService = heuristicScannerService;
        this.llmScannerService = llmScannerService;
    }

    public ScanResponse orchestrateScan(MultipartFile file, boolean useLLM, boolean useHeuristics)
            throws IOException {
        ScanResponse response = new ScanResponse();
        String fileName = file.getOriginalFilename();
        log.info("Orchestrating scan for file: {} | Size: {} bytes", fileName, file.getSize());

        PdfScannerService.PdfData pdfData = pdfScannerService.processPdf(
                file, PdfScannerService.highlightWordsFrom(heuristicScannerService.literalPhrases()));

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
            if (lResult.isAvailable() && !lResult.isSafe()) {
                isOverallSafe = false;
            }
        }

        log.info("Scan complete for {} | overall safe: {}", fileName, isOverallSafe);
        return response;
    }
}
