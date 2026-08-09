package com.promptscanner.backend.controller;

import com.promptscanner.backend.dto.HeuristicRuleRequest;
import com.promptscanner.backend.dto.HeuristicRuleResponse;
import com.promptscanner.backend.entity.HeuristicRule;
import com.promptscanner.backend.repository.HeuristicRuleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@RestController
@RequestMapping("/api")
public class RulesController {

    private final HeuristicRuleRepository heuristicRuleRepository;

    public RulesController(HeuristicRuleRepository heuristicRuleRepository) {
        this.heuristicRuleRepository = heuristicRuleRepository;
    }

    @GetMapping("/rules")
    public ResponseEntity<List<HeuristicRuleResponse>> getRules() {
        return ResponseEntity.ok(heuristicRuleRepository.findAll().stream()
                .map(HeuristicRuleResponse::from)
                .toList());
    }

    @PostMapping("/rules")
    public ResponseEntity<HeuristicRuleResponse> createRule(@RequestBody HeuristicRuleRequest request) {
        if (!request.hasPhrase()) {
            return ResponseEntity.badRequest().build();
        }
        validateRegex(request);

        HeuristicRule rule = new HeuristicRule(
                request.phrase().trim(), request.regexOrDefault(), request.activeOrDefault());
        return ResponseEntity.ok(HeuristicRuleResponse.from(heuristicRuleRepository.save(rule)));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<HeuristicRuleResponse> updateRule(@PathVariable("id") Long id,
                                                            @RequestBody HeuristicRuleRequest request) {
        if (!request.hasPhrase()) {
            return ResponseEntity.badRequest().build();
        }
        validateRegex(request);

        return heuristicRuleRepository.findById(id)
                .map(rule -> {
                    rule.setPhrase(request.phrase().trim());
                    rule.setRegex(request.regexOrDefault());
                    rule.setActive(request.activeOrDefault());
                    return ResponseEntity.ok(HeuristicRuleResponse.from(heuristicRuleRepository.save(rule)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable("id") Long id) {
        if (heuristicRuleRepository.existsById(id)) {
            heuristicRuleRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * An unparseable regex is silently skipped at scan time, so a rule saved with one
     * would show as "Active" while never matching anything. Reject it at the door.
     */
    private void validateRegex(HeuristicRuleRequest request) {
        if (request.regexOrDefault()) {
            try {
                Pattern.compile(request.phrase());
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regular expression: " + e.getDescription());
            }
        }
    }
}
