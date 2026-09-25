import { Finding, ScanResponse } from './types';

export type CheckState = 'danger' | 'safe' | 'warn';

export interface Check {
  name: string;
  state: CheckState;
  label: string;
  /** Reported observations. Only `description` is ours; the rest is quoted. */
  evidence?: Finding[];
  /** Our own prose about the check — rendered as ordinary copy. */
  note?: string;
}

export interface Verdict {
  state: CheckState;
  headline: string;
  summary: string;
}

const plural = (n: number, one: string, many: string) => (n === 1 ? one : many);

/** Not a detection layer, so it is left out of the "N of M checks" counts. */
const COVERAGE = 'Scan coverage';

/** Flattens a scan response into the rows the report renders. */
export function buildChecks(results: ScanResponse): Check[] {
  const checks: Check[] = [];

  if (results.visualObfuscationResult) {
    const r = results.visualObfuscationResult;
    const n = r.findings?.length ?? 0;
    checks.push({
      name: 'Visual obfuscation',
      state: r.safe ? 'safe' : 'danger',
      label: r.safe ? 'Clean' : `${n} ${plural(n, 'finding', 'findings')}`,
      evidence: r.safe ? undefined : r.findings,
      note: r.safe ? 'No invisible, transparent, or microscopic text.' : undefined,
    });
  }

  if (results.documentStructureResult) {
    const r = results.documentStructureResult;
    const n = r.findings?.length ?? 0;
    checks.push({
      name: 'Document structure',
      state: r.safe ? 'safe' : 'danger',
      label: r.safe ? 'Clean' : `${n} ${plural(n, 'finding', 'findings')}`,
      evidence: r.safe ? undefined : r.findings,
      note: r.safe ? 'No hidden metadata, annotations, or active content.' : undefined,
    });
  }

  if (results.heuristicResult) {
    const r = results.heuristicResult;
    const notConfigured = r.activeRuleCount === 0;
    const n = r.flags?.length ?? 0;
    checks.push({
      name: 'Heuristic rules',
      state: notConfigured ? 'warn' : r.safe ? 'safe' : 'danger',
      label: notConfigured ? 'Not configured' : r.safe ? 'Clean' : `${n} ${plural(n, 'match', 'matches')}`,
      evidence: r.safe ? undefined : r.flags,
      note:
        r.safe && !notConfigured
          ? `No known patterns matched across ${r.activeRuleCount} active ${plural(r.activeRuleCount ?? 0, 'rule', 'rules')}.`
          : undefined,
    });
  }

  if (results.llmResult) {
    const r = results.llmResult;
    // A layer that could not run is inconclusive, never a verdict — the same
    // rule the heuristic engine follows when no rules are configured.
    const unavailable = r.available === false;
    checks.push({
      name: 'AI context analysis',
      state: unavailable ? 'warn' : r.safe ? 'safe' : 'danger',
      label: unavailable ? 'Did not run' : r.safe ? 'Clean' : 'Flagged',
      note: r.analysis,
    });
  }

  // Content past a size ceiling was never analysed. With no detection, that
  // makes the result inconclusive: the unread part is exactly where a padded
  // document would put its payload.
  const limitations = results.limitations ?? [];
  if (limitations.length > 0) {
    checks.push({
      name: COVERAGE,
      state: 'warn',
      label: 'Incomplete',
      note: limitations.join(' '),
    });
  }

  return checks;
}

/**
 * Collapses the checks into the single answer the user came for.
 *
 * A heuristic engine with no rules loaded is inconclusive rather than clean: it
 * examined nothing, so it cannot vouch for the file. Reporting that as "safe" is
 * the same failure mode the backend's fail-closed rules guard against.
 */
export function overallVerdict(checks: Check[]): Verdict {
  const layers = checks.filter((c) => c.name !== COVERAGE);
  const flagged = layers.filter((c) => c.state === 'danger').length;

  if (flagged > 0) {
    return {
      state: 'danger',
      headline: 'Injection detected',
      summary: `${flagged} of ${layers.length} ${plural(layers.length, 'check', 'checks')} flagged this document. Do not pass it to an AI system unmodified.`,
    };
  }

  if (checks.some((c) => c.name === COVERAGE)) {
    return {
      state: 'warn',
      headline: 'Inconclusive',
      summary: 'Part of this document was too large to analyse in full, so it has not been fully examined.',
    };
  }

  if (checks.some((c) => c.state === 'warn')) {
    return {
      state: 'warn',
      headline: 'Inconclusive',
      summary: 'A check could not run properly, so this document has not been fully examined.',
    };
  }

  return {
    state: 'safe',
    headline: 'No injection found',
    summary: `All ${layers.length} checks passed. No hidden instructions, disguised text, or suspicious document structure were detected.`,
  };
}
