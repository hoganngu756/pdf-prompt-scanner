package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.ScanResponse;
import com.promptscanner.backend.entity.HeuristicRule;
import com.promptscanner.backend.repository.HeuristicRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class HeuristicScannerService {

    private static final Logger log = LoggerFactory.getLogger(HeuristicScannerService.class);

    private final HeuristicRuleRepository heuristicRuleRepository;

    /**
     * Compiling every rule on every scan is pure repeated work, since rule text
     * changes rarely. Keyed by phrase + mode so an edited rule compiles afresh.
     */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public HeuristicScannerService(HeuristicRuleRepository heuristicRuleRepository) {
        this.heuristicRuleRepository = heuristicRuleRepository;
    }

    private Pattern patternFor(HeuristicRule rule) {
        String key = (rule.isRegex() ? "re:" : "lit:") + rule.getPhrase();
        Pattern cached = patternCache.get(key);
        if (cached != null) {
            return cached;
        }
        Pattern compiled = rule.isRegex()
                ? Pattern.compile(rule.getPhrase(), Pattern.CASE_INSENSITIVE)
                : buildObfuscationTolerantPattern(rule.getPhrase());
        // Bound the cache so a churn of distinct rules can't grow it without limit
        if (patternCache.size() > 1000) {
            patternCache.clear();
        }
        patternCache.put(key, compiled);
        return compiled;
    }

    /**
     * Builds a regex pattern that tolerates whitespace and punctuation injection between characters.
     * Example: "bypass" matches "b y p a s s", "b.y.p.a.s.s", "b_y_p_a_s_s"
     *
     * The pattern is anchored at the start of a word, so a phrase can no longer match from
     * the middle of a longer word: "act as a" no longer fires on "react as a whole".
     *
     * Deliberately not anchored at the end. Trailing inflections usually carry the same
     * intent ("system prompt" should still catch "system prompts"), and for a security
     * scanner a missed injection costs more than an extra flag to review.
     */
    private Pattern buildObfuscationTolerantPattern(String phrase) {
        StringBuilder regex = new StringBuilder();

        // \b is only meaningful next to a word character, so anchor conditionally.
        if (!phrase.isEmpty() && isWordChar(phrase.charAt(0))) {
            regex.append("\\b");
        }

        char[] chars = phrase.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isWhitespace(c)) {
                regex.append("[\\W_]+"); // At least some non-word character for space
            } else {
                // Escape regex specials just in case
                regex.append(Pattern.quote(String.valueOf(c)));
                // Limit optional non-word characters between letters to avoid ReDoS
                if (i < chars.length - 1 && !Character.isWhitespace(chars[i + 1])) {
                    regex.append("[\\W_]{0,3}");
                }
            }
        }

        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public ScanResponse.HeuristicResult scan(String text) {
        List<String> flags = new ArrayList<>();
        List<HeuristicRule> activeRules = heuristicRuleRepository.findByIsActiveTrue();

        if (activeRules.isEmpty()) {
            // No rules means the engine checked nothing. Reporting "safe" here would
            // render a green badge for a scanner that is effectively switched off.
            log.warn("Heuristic scan requested but no active rules are configured; reporting as not safe.");
            flags.add("Heuristic engine has no active rules configured — this document was not checked. "
                    + "Add rules in the Rules tab or restart with an empty database to restore the defaults.");
            return new ScanResponse.HeuristicResult(false, flags, 0);
        }

        if (text == null || text.trim().isEmpty()) {
            return new ScanResponse.HeuristicResult(true, flags, activeRules.size());
        }

        // Normalize text to remove invisible unicode characters like zero-width spaces
        String normalizedText = text.replaceAll("[\\p{Cf}]", "");

        for (HeuristicRule rule : activeRules) {
            try {
                Pattern pattern = patternFor(rule);

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
        return new ScanResponse.HeuristicResult(isSafe, flags, activeRules.size());
    }
}
