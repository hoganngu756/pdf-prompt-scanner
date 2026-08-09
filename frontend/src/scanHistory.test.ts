import { describe, it, expect, beforeEach } from 'vitest';
import { loadHistory, recordScan, clearHistory } from './scanHistory';
import { ScanResponse } from './types';

const flagged: ScanResponse = {
  visualObfuscationResult: { safe: false, findings: ['Page 1: hidden text'] },
  documentStructureResult: { safe: true, findings: [] },
  heuristicResult: { safe: true, flags: [], activeRuleCount: 10 },
};

const clean: ScanResponse = {
  visualObfuscationResult: { safe: true, findings: [] },
  documentStructureResult: { safe: true, findings: [] },
  heuristicResult: { safe: true, flags: [], activeRuleCount: 10 },
};

describe('local scan history', () => {
  beforeEach(() => localStorage.clear());

  it('starts empty', () => {
    expect(loadHistory()).toEqual([]);
  });

  it('records a scan with its verdict, newest first', () => {
    recordScan('first.pdf', clean);
    const history = recordScan('second.pdf', flagged);

    expect(history).toHaveLength(2);
    expect(history[0].fileName).toBe('second.pdf');
    expect(history[0].state).toBe('danger');
    expect(history[0].headline).toBe('Injection detected');
    expect(history[1].state).toBe('safe');
  });

  it('summarises only the checks that reported something', () => {
    const [entry] = recordScan('doc.pdf', flagged);
    expect(entry.findings).toEqual(['Visual obfuscation: 1 finding']);
  });

  it('persists across reloads', () => {
    recordScan('kept.pdf', clean);
    expect(loadHistory()[0].fileName).toBe('kept.pdf');
  });

  it('caps the list so storage cannot grow without bound', () => {
    for (let i = 0; i < 60; i++) recordScan(`doc-${i}.pdf`, clean);
    const history = loadHistory();
    expect(history).toHaveLength(50);
    expect(history[0].fileName).toBe('doc-59.pdf');
  });

  it('clears on request', () => {
    recordScan('doc.pdf', clean);
    expect(clearHistory()).toEqual([]);
    expect(loadHistory()).toEqual([]);
  });

  it('survives corrupt storage rather than breaking the page', () => {
    localStorage.setItem('pdf_promptscanner_history', 'not json at all');
    expect(loadHistory()).toEqual([]);
  });
});
