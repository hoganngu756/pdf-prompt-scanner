package com.promptscanner.backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
        // No admin credential exists any more, so there must be no way to mutate
        // the rule set over HTTP at all -- not merely a guarded one.
        // /api/rules exists but only answers GET -> 405
        mockMvc.perform(post("/api/rules").contentType("application/json").content("{\"phrase\":\"x\"}"))
                .andExpect(status().isMethodNotAllowed());
        // /api/rules/{id} has no mapping at all any more -> 404
        mockMvc.perform(put("/api/rules/1").contentType("application/json").content("{\"phrase\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/rules/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyEndpointNoLongerExists() throws Exception {
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
