/**
 * One reported observation. `description` is the scanner's own prose; `location`
 * and `quote` are recovered from the uploaded document and are therefore
 * attacker-controlled — they must never be rendered as if they were our words.
 */
export interface Finding {
  description: string;
  location?: string;
  quote?: string;
}

export interface HeuristicRule {
  phrase: string;
  isRegex: boolean;
}

export interface HeuristicResult {
  safe: boolean;
  flags: Finding[];
  /** Rules the document was actually checked against. Zero means the engine is not configured. */
  activeRuleCount?: number;
}

export interface LlmResult {
  safe: boolean;
  analysis: string;
  /** False when the model could not be consulted; the layer did not run. */
  available?: boolean;
}

export interface VisualObfuscationResult {
  safe: boolean;
  findings: Finding[];
}

export interface DocumentStructureResult {
  safe: boolean;
  findings: Finding[];
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
