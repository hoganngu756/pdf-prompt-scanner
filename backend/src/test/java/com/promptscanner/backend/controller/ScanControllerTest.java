package com.promptscanner.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.promptscanner.backend.entity.HeuristicRule;
import com.promptscanner.backend.repository.HeuristicRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HeuristicRuleRepository ruleRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDb() {
        ruleRepository.deleteAll();
    }

    @Test
    void getRules_IsPublicAndSucceeds() throws Exception {
        mockMvc.perform(get("/api/rules"))
                .andExpect(status().isOk());
    }

    @Test
    void createRule_WithoutApiKeyHeader_Returns401() throws Exception {
        HeuristicRule rule = new HeuristicRule("test-phrase", false, true);

        mockMvc.perform(post("/api/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rule)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", containsString("Unauthorized")));
    }

    @Test
    void createRule_WithValidApiKeyHeader_Succeeds() throws Exception {
        HeuristicRule rule = new HeuristicRule("test-phrase-valid", false, true);

        mockMvc.perform(post("/api/rules")
                        .header("X-Admin-Api-Key", "test-admin-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rule)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phrase").value("test-phrase-valid"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createRule_WithExplicitActiveFalse_RespectsValue() throws Exception {
        HeuristicRule rule = new HeuristicRule("test-phrase-inactive", false, false);

        mockMvc.perform(post("/api/rules")
                        .header("X-Admin-Api-Key", "test-admin-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rule)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phrase").value("test-phrase-inactive"))
                .andExpect(jsonPath("$.active").value(false)); // Should be false, not overridden to true
    }

    @Test
    void scanPdf_WithEmptyFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/scan").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("empty or missing")));
    }
}
