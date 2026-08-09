package com.promptscanner.backend.dto;

import com.promptscanner.backend.entity.HeuristicRule;

/**
 * Outbound shape for a rule. Field names match the previous entity-serialised
 * payload exactly, so this is not a breaking change for existing clients.
 */
public record HeuristicRuleResponse(Long id, String phrase, boolean isRegex, boolean active) {

    public static HeuristicRuleResponse from(HeuristicRule rule) {
        return new HeuristicRuleResponse(rule.getId(), rule.getPhrase(), rule.isRegex(), rule.isActive());
    }
}
