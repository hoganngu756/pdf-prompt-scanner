package com.promptscanner.backend.dto;

import java.util.List;

public class ScanResponse {
    private HeuristicResult heuristicResult;
    private LlmResult llmResult;
    private String error;
    private List<String> previewImagesBase64;

    public ScanResponse() {}

    public List<String> getPreviewImagesBase64() { return previewImagesBase64; }
    public void setPreviewImagesBase64(List<String> previewImagesBase64) { this.previewImagesBase64 = previewImagesBase64; }

    public HeuristicResult getHeuristicResult() { return heuristicResult; }
    public void setHeuristicResult(HeuristicResult heuristicResult) { this.heuristicResult = heuristicResult; }

    public LlmResult getLlmResult() { return llmResult; }
    public void setLlmResult(LlmResult llmResult) { this.llmResult = llmResult; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public static class HeuristicResult {
        private boolean safe;
        private List<String> flags;

        public HeuristicResult() {}
        public HeuristicResult(boolean safe, List<String> flags) {
            this.safe = safe;
            this.flags = flags;
        }

        public boolean isSafe() { return safe; }
        public void setSafe(boolean safe) { this.safe = safe; }
        public List<String> getFlags() { return flags; }
        public void setFlags(List<String> flags) { this.flags = flags; }
    }

    public static class LlmResult {
        private boolean safe;
        private String analysis;

        public LlmResult() {}
        public LlmResult(boolean safe, String analysis) {
            this.safe = safe;
            this.analysis = analysis;
        }

        public boolean isSafe() { return safe; }
        public void setSafe(boolean safe) { this.safe = safe; }
        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }
    }
}
