package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.ScanResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class HeuristicScannerService {

    private static final List<String> SUSPICIOUS_PHRASES = List.of(
            "ignore all previous instructions",
            "system message",
            "you are now",
            "system prompt",
            "do not follow the rules",
            "forget everything",
            "bypass",
            "new instructions",
            "act as a",
            "jailbreak"
    );

    private static final List<Pattern> SUSPICIOUS_PATTERNS = buildPatterns(SUSPICIOUS_PHRASES);

    private static List<Pattern> buildPatterns(List<String> phrases) {
        List<Pattern> patterns = new ArrayList<>();
        for (String phrase : phrases) {
            patterns.add(buildObfuscationTolerantPattern(phrase));
        }
        return patterns;
    }

    /**
     * Builds a regex pattern that tolerates whitespace and punctuation injection between characters.
     * Example: "bypass" matches "b y p a s s", "b.y.p.a.s.s", "b_y_p_a_s_s"
     */
    private static Pattern buildObfuscationTolerantPattern(String phrase) {
        StringBuilder regex = new StringBuilder();
        for (char c : phrase.toCharArray()) {
            if (Character.isWhitespace(c)) {
                regex.append("[\\W_]+"); // At least some non-word character for space
            } else {
                // Escape regex specials just in case, though our phrases are alphabetic
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

        for (int i = 0; i < SUSPICIOUS_PATTERNS.size(); i++) {
            Pattern pattern = SUSPICIOUS_PATTERNS.get(i);
            if (pattern.matcher(normalizedText).find()) {
                flags.add("Detected suspicious phrase matching: '" + SUSPICIOUS_PHRASES.get(i) + "' (obfuscation ignored)");
            }
        }

        boolean isSafe = flags.isEmpty();
        return new ScanResponse.HeuristicResult(isSafe, flags);
    }
}
