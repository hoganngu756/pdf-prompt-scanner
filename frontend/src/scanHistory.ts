import { ScanResponse } from './types';
import { buildChecks, overallVerdict } from './verdict';

const STORAGE_KEY = 'pdf_promptscanner_history';
const MAX_ENTRIES = 50;

export interface HistoryEntry {
  id: string;
  fileName: string;
  scannedAt: string;
  state: 'danger' | 'safe' | 'warn';
  headline: string;
  /** One line per check that reported something, for the detail column. */
  findings: string[];
}

/**
 * Scan history, kept in the visitor's own browser.
 *
 * The server used to store every scan — filenames, flagged excerpts and AI
 * analyses of documents other people uploaded — behind an admin key. Keeping it
 * client-side means each person sees only their own scans, nothing is shared,
 * no credential is needed, and no record of anyone's document ever reaches the
 * server. It also survives redeploys, which the server-side table did not.
 */
export function loadHistory(): HistoryEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? (parsed as HistoryEntry[]) : [];
  } catch {
    // Corrupt or unreadable storage should never break the page
    return [];
  }
}

/** Records a completed scan, newest first, keeping the list bounded. */
export function recordScan(fileName: string, results: ScanResponse): HistoryEntry[] {
  const checks = buildChecks(results);
  const verdict = overallVerdict(checks);

  const entry: HistoryEntry = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    fileName,
    scannedAt: new Date().toISOString(),
    state: verdict.state,
    headline: verdict.headline,
    findings: checks
      .filter((c) => c.state !== 'safe')
      .map((c) => `${c.name}: ${c.label}`),
  };

  const next = [entry, ...loadHistory()].slice(0, MAX_ENTRIES);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } catch {
    // Quota exceeded or storage disabled — the scan itself still succeeded
  }
  return next;
}

export function clearHistory(): HistoryEntry[] {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // ignore
  }
  return [];
}
