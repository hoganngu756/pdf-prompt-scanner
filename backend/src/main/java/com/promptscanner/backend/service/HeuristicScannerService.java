package com.promptscanner.backend.service;

import com.promptscanner.backend.config.HeuristicRuleProperties;
import com.promptscanner.backend.dto.ScanResponse;
import com.promptscanner.backend.util.TextNormalizer;
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

    private final HeuristicRuleProperties ruleProperties;

    /**
     * Compiling every rule on every scan is pure repeated work. The rule set is
     * now fixed at boot, so this cache is filled once and never invalidated.
     */
    private final Map<String, Pattern> patternCache = new ConcurrentHashMap<>();

    public HeuristicScannerService(HeuristicRuleProperties ruleProperties) {
        this.ruleProperties = ruleProperties;
    }

    private Pattern patternFor(HeuristicRuleProperties.Rule rule) {
        String key = (rule.isRegex() ? "re:" : "lit:") + rule.getPhrase();
        return patternCache.computeIfAbsent(key, k -> rule.isRegex()
                ? Pattern.compile(rule.getPhrase(), Pattern.CASE_INSENSITIVE)
                : buildObfuscationTolerantPattern(rule.getPhrase()));
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
        List<HeuristicRuleProperties.Rule> rules = ruleProperties.getRules();

        if (rules.isEmpty()) {
            // No rules means the engine checked nothing. Reporting "safe" here would
            // render a green badge for a scanner that is effectively switched off.
            log.warn("Heuristic scan requested but no rules are configured; reporting as not safe.");
            flags.add("Heuristic engine has no rules configured — this document was not checked. "
                    + "Add rules to heuristic-rules.yml and restart.");
            return new ScanResponse.HeuristicResult(false, flags, 0);
        }

        if (text == null || text.trim().isEmpty()) {
            return new ScanResponse.HeuristicResult(true, flags, rules.size());
        }

        // Fold homoglyphs, compatibility forms, accents and zero-width padding so a
        // rule written in plain ASCII still matches a disguised spelling.
        String normalizedText = TextNormalizer.normalize(text);

        for (HeuristicRuleProperties.Rule rule : rules) {
            try {
                Pattern pattern = patternFor(rule);

                if (pattern.matcher(normalizedText).find()) {
                    String matchType = rule.isRegex() ? "regex pattern" : "phrase";
                    // If the raw text didn't match, the phrase was actively disguised --
                    // worth saying so, since that is itself evidence of intent.
                    boolean neededUnmasking = !pattern.matcher(text).find();
                    flags.add("Detected suspicious " + matchType + " matching: '" + rule.getPhrase() + "'"
                            + (neededUnmasking ? " (disguised with lookalike or hidden characters)" : ""));
                }
            } catch (PatternSyntaxException e) {
                // A bad regex is now a config error caught at boot, but stay resilient
                log.warn("Skipped invalid regex rule '{}': {}", rule.getPhrase(), e.getMessage());
            }
        }

        boolean isSafe = flags.isEmpty();
        return new ScanResponse.HeuristicResult(isSafe, flags, rules.size());
    }

    /** Literal phrases, for deriving preview highlight words. */
    public List<String> literalPhrases() {
        return ruleProperties.getRules().stream()
                .filter(r -> !r.isRegex())
                .map(HeuristicRuleProperties.Rule::getPhrase)
                .toList();
    }
}
