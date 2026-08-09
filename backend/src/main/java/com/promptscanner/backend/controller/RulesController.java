package com.promptscanner.backend.controller;

import com.promptscanner.backend.config.HeuristicRuleProperties;
import com.promptscanner.backend.dto.HeuristicRuleResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only view of the detection rule set.
 *
 * Rules are configuration (heuristic-rules.yml), not runtime state, so there is
 * nothing to create, update or delete over HTTP -- and consequently no admin
 * credential to protect, leak, or fail open.
 */
@RestController
@RequestMapping("/api")
public class RulesController {

    private final HeuristicRuleProperties ruleProperties;

    public RulesController(HeuristicRuleProperties ruleProperties) {
        this.ruleProperties = ruleProperties;
    }

    @GetMapping("/rules")
    public ResponseEntity<List<HeuristicRuleResponse>> getRules() {
        return ResponseEntity.ok(ruleProperties.getRules().stream()
                .map(HeuristicRuleResponse::from)
                .toList());
    }
}
