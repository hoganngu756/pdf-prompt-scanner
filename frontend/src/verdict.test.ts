import { describe, it, expect } from 'vitest';
import { buildChecks, overallVerdict } from './verdict';
import { ScanResponse } from './types';

const clean = (extra: Partial<ScanResponse> = {}): ScanResponse => ({
  visualObfuscationResult: { safe: true, findings: [] },
  documentStructureResult: { safe: true, findings: [] },
  heuristicResult: { safe: true, flags: [], activeRuleCount: 10 },
  llmResult: { safe: true, analysis: 'Nothing suspicious.' },
  ...extra,
});

describe('buildChecks', () => {
  it('includes only the checks present in the response', () => {
    const checks = buildChecks({ visualObfuscationResult: { safe: true, findings: [] } });
    expect(checks.map((c) => c.name)).toEqual(['Visual obfuscation']);
  });

  it('treats recovered document text as evidence, and our own prose as a note', () => {
    const checks = buildChecks(
      clean({ visualObfuscationResult: { safe: false, findings: ['Page 1: hidden text'] } }),
    );
    const visual = checks.find((c) => c.name === 'Visual obfuscation')!;
    expect(visual.evidence).toEqual(['Page 1: hidden text']);
    expect(visual.note).toBeUndefined();

    const ai = checks.find((c) => c.name === 'AI context analysis')!;
    expect(ai.note).toBe('Nothing suspicious.');
    expect(ai.evidence).toBeUndefined();
  });

  it('pluralises counts correctly', () => {
    const one = buildChecks(clean({ visualObfuscationResult: { safe: false, findings: ['a'] } }));
    expect(one.find((c) => c.name === 'Visual obfuscation')!.label).toBe('1 finding');

    const two = buildChecks(clean({ visualObfuscationResult: { safe: false, findings: ['a', 'b'] } }));
    expect(two.find((c) => c.name === 'Visual obfuscation')!.label).toBe('2 findings');
  });

  it('marks a rule engine with no active rules as a warning, not clean', () => {
    const checks = buildChecks(clean({ heuristicResult: { safe: true, flags: [], activeRuleCount: 0 } }));
    const heuristic = checks.find((c) => c.name === 'Heuristic rules')!;
    expect(heuristic.state).toBe('warn');
    expect(heuristic.label).toBe('Not configured');
  });
});

describe('overallVerdict', () => {
  it('reports clean when every check passed', () => {
    const verdict = overallVerdict(buildChecks(clean()));
    expect(verdict.state).toBe('safe');
    expect(verdict.headline).toBe('No injection found');
  });

  it('reports detection and counts the flagged checks', () => {
    const verdict = overallVerdict(
      buildChecks(
        clean({
          visualObfuscationResult: { safe: false, findings: ['x'] },
          heuristicResult: { safe: false, flags: ['y'], activeRuleCount: 10 },
        }),
      ),
    );
    expect(verdict.state).toBe('danger');
    expect(verdict.summary).toContain('2 of 4 checks');
  });

  it('never reports safe when a check could not run', () => {
    // The whole point: an engine that examined nothing cannot vouch for the file.
    const verdict = overallVerdict(
      buildChecks(clean({ heuristicResult: { safe: true, flags: [], activeRuleCount: 0 } })),
    );
    expect(verdict.state).toBe('warn');
    expect(verdict.headline).toBe('Inconclusive');
  });

  it('prefers detection over inconclusive when both are present', () => {
    const verdict = overallVerdict(
      buildChecks(
        clean({
          heuristicResult: { safe: true, flags: [], activeRuleCount: 0 },
          visualObfuscationResult: { safe: false, findings: ['x'] },
        }),
      ),
    );
    expect(verdict.state).toBe('danger');
  });
});

describe('unavailable AI layer', () => {
  const base: ScanResponse = {
    visualObfuscationResult: { safe: true, findings: [] },
    documentStructureResult: { safe: true, findings: [] },
    heuristicResult: { safe: true, flags: [], activeRuleCount: 10 },
  };

  it('never reports a flag when the model could not be consulted', () => {
    const checks = buildChecks({
      ...base,
      llmResult: { safe: true, analysis: 'could not be reached', available: false },
    });
    const ai = checks.find((c) => c.name === 'AI context analysis')!;
    expect(ai.state).toBe('warn');
    expect(ai.label).toBe('Did not run');
    // Rate-limited Gemini once turned every benign document into a detection
    expect(overallVerdict(checks).state).toBe('warn');
  });

  it('still reports a real flag when the model did run', () => {
    const checks = buildChecks({
      ...base,
      llmResult: { safe: false, analysis: 'injection found', available: true },
    });
    expect(overallVerdict(checks).state).toBe('danger');
  });
});
