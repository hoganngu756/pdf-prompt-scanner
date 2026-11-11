package com.promptscanner.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.not;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Pins the wire shape of a finding. Our prose and text recovered from the
     * document travel in separate fields, so a crafted PDF cannot supply content
     * that reads as the scanner's own framing — and the client can set quoted
     * material apart typographically. Fusing them back into one string is a
     * regression this test exists to catch.
     */
    @Test
    void scanResponse_KeepsOurProseSeparateFromRecoveredText() throws Exception {
        byte[] pdf = new ClassPathResource("pdf/injected.pdf").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile(
                "file", "injected.pdf", "application/pdf", pdf);

        mockMvc.perform(multipart("/api/scan").file(file)
                        .param("useLLM", "false")
                        .param("useHeuristics", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heuristicResult.safe").value(false))
                .andExpect(jsonPath("$.heuristicResult.flags[0].description").exists())
                .andExpect(jsonPath("$.heuristicResult.flags[0].quote")
                        .value("ignore all previous instructions"))
                // The matched phrase must not also be embedded in our sentence.
                .andExpect(jsonPath("$.heuristicResult.flags[0].description",
                        not(containsString("ignore all previous instructions"))));
    }

    /**
     * A bad upload is the client's problem, so it must come back as a 400 with a
     * message the user can act on -- not a 500 with a stack trace in our logs.
     */
    @Test
    void scan_RejectsAFileThatIsNotAPdfWith400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.pdf", "application/pdf", "just some text, not a PDF".getBytes());

        mockMvc.perform(multipart("/api/scan").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("could not be read as a PDF")));
    }

    @Test
    void scan_RejectsATruncatedPdfWith400() throws Exception {
        byte[] pdf = new ClassPathResource("pdf/safe.pdf").getContentAsByteArray();
        byte[] truncated = java.util.Arrays.copyOf(pdf, 20);
        MockMultipartFile file = new MockMultipartFile("file", "cut.pdf", "application/pdf", truncated);

        mockMvc.perform(multipart("/api/scan").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("could not be read as a PDF")));
    }

    @Test
    void scan_RejectsAPasswordProtectedPdfWith400() throws Exception {
        byte[] encrypted;
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy policy =
                    new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                            "owner", "secret", new org.apache.pdfbox.pdmodel.encryption.AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(out);
            encrypted = out.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile("file", "locked.pdf", "application/pdf", encrypted);

        mockMvc.perform(multipart("/api/scan").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("password-protected")));
    }

    @Test
    void scanResponse_OmitsEmptyFindingFieldsRatherThanSendingNulls() throws Exception {
        byte[] pdf = new ClassPathResource("pdf/safe.pdf").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile(
                "file", "safe.pdf", "application/pdf", pdf);

        mockMvc.perform(multipart("/api/scan").file(file)
                        .param("useLLM", "false")
                        .param("useHeuristics", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heuristicResult.safe").value(true))
                .andExpect(jsonPath("$.visualObfuscationResult.safe").value(true));
    }

    @Test
    void getRules_IsPublicAndReturnsTheConfiguredSet() throws Exception {
        mockMvc.perform(get("/api/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)))
                .andExpect(jsonPath("$[0].phrase").exists())
                .andExpect(jsonPath("$[0].isRegex").exists());
    }

    @Test
    void rulesAreReadOnly_WriteMethodsAreNotExposed() throws Exception {
        // Rules are configuration: there must be no way to mutate them over HTTP
        // at all -- not merely a guarded one.
        // /api/rules exists but only answers GET -> 405
        mockMvc.perform(post("/api/rules").contentType("application/json").content("{\"phrase\":\"x\"}"))
                .andExpect(status().isMethodNotAllowed());
        // /api/rules/{id} has no mapping at all -> 404
        mockMvc.perform(put("/api/rules/1").contentType("application/json").content("{\"phrase\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/rules/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noServerSideHistoryEndpoint() throws Exception {
        // Scan records are kept in the visitor's browser; the server stores nothing.
        mockMvc.perform(get("/api/history")).andExpect(status().isNotFound());
    }

    @Test
    void unknownPath_Returns404NotServerError() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mockMvc.perform(get("/definitely-not-a-route")).andExpect(status().isNotFound());
    }

    @Test
    void scanPdf_WithEmptyFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/scan").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("empty or missing")));
    }

    @Test
    void scanPdf_WithNonPdfContentType_ReturnsBadRequest() throws Exception {
        MockMultipartFile notPdf = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/scan").file(notPdf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Only PDF")));
    }
}
