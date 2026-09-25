package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.ScanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Collaborators are hand-written stubs rather than Mockito mocks, which cannot
 * instrument concrete classes on every JDK this project is built with.
 */
class ScanOrchestrationServiceTest {

    private PdfScannerService.PdfData extracted;
    private ScanResponse.LlmResult llmAnswer = new ScanResponse.LlmResult(true, "Nothing found.");

    private final PdfScannerService pdfScanner = new PdfScannerService(null, null) {
        @Override
        public PdfData processPdf(MultipartFile file, Set<String> highlightWords) {
            return extracted;
        }
    };

    private final HeuristicScannerService heuristics = new HeuristicScannerService(null) {
        @Override
        public List<String> literalPhrases() {
            return List.of();
        }

        @Override
        public ScanResponse.HeuristicResult scan(String text) {
            return new ScanResponse.HeuristicResult(true, List.of(), 10);
        }
    };

    private final LlmScannerService llm = new LlmScannerService(null, null) {
        @Override
        public ScanResponse.LlmResult scan(String text) {
            return llmAnswer;
        }
    };

    private final ScanOrchestrationService service = new ScanOrchestrationService(pdfScanner, heuristics, llm);

    private final MockMultipartFile file =
            new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{1});

    private void extracting(String text, List<String> limitations) {
        extracted = new PdfScannerService.PdfData(text, List.of(), List.of(), List.of(), List.of(), limitations);
    }

    @Test
    void passesExtractionLimitationsThrough() throws Exception {
        extracting("text", List.of("Page text was truncated."));

        ScanResponse response = service.orchestrateScan(file, false, true);

        assertEquals(List.of("Page text was truncated."), response.getLimitations());
    }

    @Test
    void reportsWhenTheAiLayerSawOnlyPartOfTheText() throws Exception {
        // Regression: text past the AI layer's input cap was dropped silently, so a
        // payload placed after it was never read by the model and nothing said so.
        extracting("a".repeat(LlmScannerService.MAX_INPUT_CHARS + 1), List.of());

        ScanResponse response = service.orchestrateScan(file, true, true);

        assertEquals(1, response.getLimitations().size());
        assertTrue(response.getLimitations().get(0).startsWith("AI analysis covered only"));
    }

    @Test
    void noLimitationsForAnOrdinaryDocument() throws Exception {
        extracting("a short document", List.of());

        assertTrue(service.orchestrateScan(file, true, true).getLimitations().isEmpty());
    }

    @Test
    void anAiLayerThatDidNotRunIsNotReportedAsPartial() throws Exception {
        // "Did not run" is already reported by the layer itself.
        extracting("a".repeat(LlmScannerService.MAX_INPUT_CHARS + 1), List.of());
        llmAnswer = ScanResponse.LlmResult.unavailable("down");

        assertTrue(service.orchestrateScan(file, true, true).getLimitations().isEmpty());
    }
}
