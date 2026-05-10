package com.promptscanner.backend.controller;

import com.promptscanner.backend.dto.ScanResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173") // Vite default port
public class ScanController {

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
            // PHASE 1: Stub implementation.
            // In Phase 2, we will use PDFBox to extract text here.
            String fileName = file.getOriginalFilename();
            System.out.println("Received file: " + fileName + " | Size: " + file.getSize());

            // Mocked Heuristic Scan
            if (useHeuristics) {
                // In Phase 3, we'll replace this with real regex logic against the extracted text.
                ScanResponse.HeuristicResult hResult = new ScanResponse.HeuristicResult(
                        true, 
                        List.of()
                );
                response.setHeuristicResult(hResult);
            }

            // Mocked LLM Scan
            if (useLLM) {
                // In Phase 3, we'll call an LLM API here.
                ScanResponse.LlmResult lResult = new ScanResponse.LlmResult(
                        true, 
                        "No prompt injection detected. (Mock LLM response)"
                );
                response.setLlmResult(lResult);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.setError("Failed to process the PDF file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
