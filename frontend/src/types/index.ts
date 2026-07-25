export interface HeuristicRule {
  id: number;
  phrase: string;
  isRegex: boolean;
  active: boolean;
}

export interface ScanRecord {
  id: number;
  fileName: string;
  scanDate: string;
  safe: boolean;
  heuristicFlags?: string;
  llmExplanation?: string;
  visualFlags?: string; // for future feature 1
}

export interface HeuristicResult {
  safe: boolean;
  flags: string[];
  /** Rules the document was actually checked against. Zero means the engine is not configured. */
  activeRuleCount?: number;
}

export interface LlmResult {
  safe: boolean;
  analysis: string;
}

export interface VisualObfuscationResult {
  safe: boolean;
  findings: string[];
}

export interface ScanResponse {
  heuristicResult?: HeuristicResult;
  llmResult?: LlmResult;
  visualObfuscationResult?: VisualObfuscationResult;
  error?: string;
  previewImagesBase64?: string[];
  /** 1-based source page numbers aligned with previewImagesBase64 by index.
   *  Only flagged pages are rendered, so these are not contiguous. */
  previewPageNumbers?: number[];
}
