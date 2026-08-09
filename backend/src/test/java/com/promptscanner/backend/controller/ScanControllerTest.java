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
    void getHistory_WithoutApiKeyHeader_Returns401() throws Exception {
        // History exposes filenames and AI analyses of other users' documents
        mockMvc.perform(get("/api/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getHistory_WithValidApiKeyHeader_SucceedsAndSerializesDates() throws Exception {
        // Also guards the ObjectMapper regression: a bean override once stripped
        // JavaTimeModule, making LocalDateTime serialization fail with a 500.
        mockMvc.perform(get("/api/history")
                        .header("X-Admin-Api-Key", "test-admin-secret-key"))
                .andExpect(status().isOk());
    }

    @Test
    void createRule_IgnoresClientSuppliedId() throws Exception {
        // The request DTO has no id field, so a client cannot steer which row is
        // written. Previously the JPA entity was bound directly from the body.
        mockMvc.perform(post("/api/rules")
                        .header("X-Admin-Api-Key", "test-admin-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": 4242, \"phrase\": \"mass-assignment-probe\", \"isRegex\": false, \"active\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(4242)))
                .andExpect(jsonPath("$.phrase").value("mass-assignment-probe"));
    }

    @Test
    void createRule_WithInvalidRegex_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/rules")
                        .header("X-Admin-Api-Key", "test-admin-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phrase\": \"[a-z\", \"isRegex\": true, \"active\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRules_ResponseShapeIsStable() throws Exception {
        mockMvc.perform(post("/api/rules")
                        .header("X-Admin-Api-Key", "test-admin-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phrase\": \"shape-check\", \"isRegex\": false, \"active\": true}"))
                .andExpect(status().isOk());

        // Field names the frontend depends on must survive the DTO boundary
        mockMvc.perform(get("/api/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].phrase").exists())
                .andExpect(jsonPath("$[0].isRegex").exists())
                .andExpect(jsonPath("$[0].active").exists());
    }

    @Test
    void unknownPath_Returns404NotServerError() throws Exception {
        // A catch-all handler once turned every unknown path into a 500 with a
        // full stack trace, letting any crawler inflate the logs without limit.
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
        mockMvc.perform(get("/definitely-not-a-route")).andExpect(status().isNotFound());
    }

    @Test
    void createRule_WithOverlongPhrase_ReturnsBadRequest() throws Exception {
        String tooLong = "a".repeat(501);
        mockMvc.perform(post("/api/rules")
                        .header("X-Admin-Api-Key", "test-admin-secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phrase\": \"" + tooLong + "\", \"isRegex\": false, \"active\": true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void scanPdf_WithEmptyFile_ReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/scan").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("empty or missing")));
    }
}
