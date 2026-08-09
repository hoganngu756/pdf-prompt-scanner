export interface HeuristicRule {
  phrase: string;
  isRegex: boolean;
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

export interface DocumentStructureResult {
  safe: boolean;
  findings: string[];
}

export interface ScanResponse {
  heuristicResult?: HeuristicResult;
  llmResult?: LlmResult;
  visualObfuscationResult?: VisualObfuscationResult;
  documentStructureResult?: DocumentStructureResult;
  error?: string;
  previewImagesBase64?: string[];
  /** 1-based source page numbers aligned with previewImagesBase64 by index.
   *  Only flagged pages are rendered, so these are not contiguous. */
  previewPageNumbers?: number[];
}
