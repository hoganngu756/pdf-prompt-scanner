package com.promptscanner.backend.dto;

import java.util.List;

public class ScanResponse {
    private HeuristicResult heuristicResult;
    private LlmResult llmResult;
    private VisualObfuscationResult visualObfuscationResult;
    private DocumentStructureResult documentStructureResult;
    private String error;
    private List<String> previewImagesBase64;
    private List<Integer> previewPageNumbers;

    public ScanResponse() {}

    public List<String> getPreviewImagesBase64() { return previewImagesBase64; }
    public void setPreviewImagesBase64(List<String> previewImagesBase64) { this.previewImagesBase64 = previewImagesBase64; }

    /** 1-based source page numbers aligned with previewImagesBase64 by index. */
    public List<Integer> getPreviewPageNumbers() { return previewPageNumbers; }
    public void setPreviewPageNumbers(List<Integer> previewPageNumbers) { this.previewPageNumbers = previewPageNumbers; }

    public HeuristicResult getHeuristicResult() { return heuristicResult; }
    public void setHeuristicResult(HeuristicResult heuristicResult) { this.heuristicResult = heuristicResult; }

    public LlmResult getLlmResult() { return llmResult; }
    public void setLlmResult(LlmResult llmResult) { this.llmResult = llmResult; }

    public VisualObfuscationResult getVisualObfuscationResult() { return visualObfuscationResult; }
    public void setVisualObfuscationResult(VisualObfuscationResult visualObfuscationResult) { this.visualObfuscationResult = visualObfuscationResult; }

    public DocumentStructureResult getDocumentStructureResult() { return documentStructureResult; }
    public void setDocumentStructureResult(DocumentStructureResult documentStructureResult) { this.documentStructureResult = documentStructureResult; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public static class HeuristicResult {
        private boolean safe;
        private List<Finding> flags;
        private int activeRuleCount;

        public HeuristicResult() {}
        public HeuristicResult(boolean safe, List<Finding> flags) {
            this(safe, flags, 0);
        }
        public HeuristicResult(boolean safe, List<Finding> flags, int activeRuleCount) {
            this.safe = safe;
            this.flags = flags;
            this.activeRuleCount = activeRuleCount;
        }

        public boolean isSafe() { return safe; }
        public void setSafe(boolean safe) { this.safe = safe; }
        public List<Finding> getFlags() { return flags; }
        public void setFlags(List<Finding> flags) { this.flags = flags; }

        /** Number of active rules the document was actually checked against. Zero means the engine did nothing. */
        public int getActiveRuleCount() { return activeRuleCount; }
        public void setActiveRuleCount(int activeRuleCount) { this.activeRuleCount = activeRuleCount; }
    }

    public static class LlmResult {
        private boolean safe;
        private String analysis;
        /** False when the model could not be consulted at all. */
        private boolean available = true;

        public LlmResult() {}
        public LlmResult(boolean safe, String analysis) {
            this(safe, analysis, true);
        }
        public LlmResult(boolean safe, String analysis, boolean available) {
            this.safe = safe;
            this.analysis = analysis;
            this.available = available;
        }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }

        /** A check that could not run must not be reported as a detection. */
        public static LlmResult unavailable(String reason) {
            return new LlmResult(true, reason, false);
        }

        public boolean isSafe() { return safe; }
        public void setSafe(boolean safe) { this.safe = safe; }
        public String getAnalysis() { return analysis; }
        public void setAnalysis(String analysis) { this.analysis = analysis; }
    }

    /** Findings from document structure: metadata, annotations, and active content. */
    public static class DocumentStructureResult {
        private boolean safe;
        private List<Finding> findings;

        public DocumentStructureResult() {}
        public DocumentStructureResult(boolean safe, List<Finding> findings) {
            this.safe = safe;
            this.findings = findings;
        }

        public boolean isSafe() { return safe; }
        public void setSafe(boolean safe) { this.safe = safe; }
        public List<Finding> getFindings() { return findings; }
        public void setFindings(List<Finding> findings) { this.findings = findings; }
    }

    public static class VisualObfuscationResult {
        private boolean safe;
        private List<Finding> findings;

        public VisualObfuscationResult() {}
        public VisualObfuscationResult(boolean safe, List<Finding> findings) {
            this.safe = safe;
            this.findings = findings;
        }

        public boolean isSafe() { return safe; }
        public void setSafe(boolean safe) { this.safe = safe; }
        public List<Finding> getFindings() { return findings; }
        public void setFindings(List<Finding> findings) { this.findings = findings; }
    }
}
