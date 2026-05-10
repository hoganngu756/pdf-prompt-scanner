package com.promptscanner.backend.service;

import com.promptscanner.backend.dto.ScanResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class HeuristicScannerService {

    // Common phrases used in prompt injections or jailbreaks
    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignore all previous instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system message", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("do not follow the rules", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget everything", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bypass", Pattern.CASE_INSENSITIVE)
    );

    public ScanResponse.HeuristicResult scan(String text) {
        List<String> flags = new ArrayList<>();
        
        if (text == null || text.trim().isEmpty()) {
            return new ScanResponse.HeuristicResult(true, flags);
        }

        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(text).find()) {
                flags.add("Detected suspicious phrase matching pattern: " + pattern.pattern());
            }
        }

        boolean isSafe = flags.isEmpty();
        return new ScanResponse.HeuristicResult(isSafe, flags);
    }
}
