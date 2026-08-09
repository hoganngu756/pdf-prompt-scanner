package com.promptscanner.backend.dto;

import com.promptscanner.backend.config.HeuristicRuleProperties;

/** Outbound shape for a configured rule. */
public record HeuristicRuleResponse(String phrase, boolean isRegex) {

    public static HeuristicRuleResponse from(HeuristicRuleProperties.Rule rule) {
        return new HeuristicRuleResponse(rule.getPhrase(), rule.isRegex());
    }
}
