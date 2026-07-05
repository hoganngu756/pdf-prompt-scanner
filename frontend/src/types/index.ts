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
}
