package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.ScanResponse;
import com.promptscanner.backend.entity.HeuristicRule;
import com.promptscanner.backend.repository.HeuristicRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class HeuristicScannerService {

    private static final Logger log = LoggerFactory.getLogger(HeuristicScannerService.class);

    private final HeuristicRuleRepository heuristicRuleRepository;

    public HeuristicScannerService(HeuristicRuleRepository heuristicRuleRepository) {
        this.heuristicRuleRepository = heuristicRuleRepository;
    }

    /**
     * Builds a regex pattern that tolerates whitespace and punctuation injection between characters.
     * Example: "bypass" matches "b y p a s s", "b.y.p.a.s.s", "b_y_p_a_s_s"
     */
    private Pattern buildObfuscationTolerantPattern(String phrase) {
        StringBuilder regex = new StringBuilder();
        for (char c : phrase.toCharArray()) {
            if (Character.isWhitespace(c)) {
                regex.append("[\\W_]+"); // At least some non-word character for space
            } else {
                // Escape regex specials just in case
                regex.append(Pattern.quote(String.valueOf(c)));
                regex.append("[\\W_]{0,3}"); // Limit optional non-word characters between letters to avoid ReDoS
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    public ScanResponse.HeuristicResult scan(String text) {
        List<String> flags = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return new ScanResponse.HeuristicResult(true, flags);
        }

        // Normalize text to remove invisible unicode characters like zero-width spaces
        String normalizedText = text.replaceAll("[\\p{Cf}]", "");

        List<HeuristicRule> activeRules = heuristicRuleRepository.findByIsActiveTrue();

        for (HeuristicRule rule : activeRules) {
            try {
                Pattern pattern;
                if (rule.isRegex()) {
                    pattern = Pattern.compile(rule.getPhrase(), Pattern.CASE_INSENSITIVE);
                } else {
                    pattern = buildObfuscationTolerantPattern(rule.getPhrase());
                }

                if (pattern.matcher(normalizedText).find()) {
                    String matchType = rule.isRegex() ? "regex pattern" : "phrase";
                    flags.add("Detected suspicious " + matchType + " matching: '" + rule.getPhrase() + "'");
                }
            } catch (PatternSyntaxException e) {
                // Log and skip invalid regex rules to keep scanner resilient
                log.warn("Skipped invalid regex rule '{}': {}", rule.getPhrase(), e.getMessage());
            }
        }

        boolean isSafe = flags.isEmpty();
        return new ScanResponse.HeuristicResult(isSafe, flags);
    }
}
