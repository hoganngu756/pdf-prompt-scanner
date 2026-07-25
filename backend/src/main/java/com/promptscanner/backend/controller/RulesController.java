package com.promptscanner.backend.controller;

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
    public ResponseEntity<List<HeuristicRule>> getRules() {
        return ResponseEntity.ok(heuristicRuleRepository.findAll());
    }

    /**
     * An unparseable regex is silently skipped at scan time, so a rule saved with one
     * would show as "Active" while never matching anything. Reject it at the door.
     */
    private void validateRegex(HeuristicRule rule) {
        if (Boolean.TRUE.equals(rule.getIsRegex()) && rule.getPhrase() != null) {
            try {
                Pattern.compile(rule.getPhrase());
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regular expression: " + e.getDescription());
            }
        }
    }

    @PostMapping("/rules")
    public ResponseEntity<HeuristicRule> createRule(@RequestBody HeuristicRule rule) {
        if (rule.getPhrase() == null || rule.getPhrase().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        validateRegex(rule);
        // Default active to true only if not provided
        if (rule.getIsActive() == null) {
            rule.setActive(true);
        }
        // Default isRegex to false if not provided
        if (rule.getIsRegex() == null) {
            rule.setRegex(false);
        }
        return ResponseEntity.ok(heuristicRuleRepository.save(rule));
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<HeuristicRule> updateRule(@PathVariable("id") Long id, @RequestBody HeuristicRule updatedRule) {
        validateRegex(updatedRule);
        return heuristicRuleRepository.findById(id)
                .map(rule -> {
                    rule.setPhrase(updatedRule.getPhrase());
                    rule.setRegex(updatedRule.isRegex());
                    rule.setActive(updatedRule.isActive());
                    return ResponseEntity.ok(heuristicRuleRepository.save(rule));
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
}
