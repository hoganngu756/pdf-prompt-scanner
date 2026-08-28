import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup, fireEvent, act } from '@testing-library/react';
import ResultsDashboard from './ResultsDashboard';
import { Finding, ScanResponse } from '../types';

afterEach(cleanup);

const finding = (n: number): Finding => ({
  description: `Hidden text via invisible text rendering mode`,
  location: `Page ${n}`,
  quote: `payload ${n}`,
});

const withFindings = (count: number): ScanResponse => ({
  visualObfuscationResult: {
    safe: false,
    findings: Array.from({ length: count }, (_, i) => finding(i + 1)),
  },
  documentStructureResult: { safe: true, findings: [] },
  heuristicResult: { safe: true, flags: [], activeRuleCount: 10 },
});

describe('ResultsDashboard', () => {
  describe('evidence volume', () => {
    it('shows only the first ten findings, with a control to reveal the rest', () => {
      // The backend permits hundreds of findings; rendering them all buries the
      // verdict and lets a padded document make the report unusable.
      render(<ResultsDashboard results={withFindings(200)} loading={false} />);

      expect(screen.getAllByText(/^payload /)).toHaveLength(10);
      expect(screen.getByRole('button', { name: /Show all 200/ })).toBeDefined();
    });

    it('reveals every finding once expanded, and collapses again', () => {
      render(<ResultsDashboard results={withFindings(25)} loading={false} />);

      fireEvent.click(screen.getByRole('button', { name: /Show all 25/ }));
      expect(screen.getAllByText(/^payload /)).toHaveLength(25);

      fireEvent.click(screen.getByRole('button', { name: /Show only the first 10/ }));
      expect(screen.getAllByText(/^payload /)).toHaveLength(10);
    });

    it('offers no control when everything already fits', () => {
      render(<ResultsDashboard results={withFindings(4)} loading={false} />);

      expect(screen.getAllByText(/^payload /)).toHaveLength(4);
      expect(screen.queryByRole('button', { name: /Show all/ })).toBeNull();
    });

    it('collapses again when a new document is scanned', () => {
      // Expansion state must not leak from one document's report into the next.
      const { rerender } = render(
        <ResultsDashboard results={withFindings(25)} loading={false} />,
      );
      fireEvent.click(screen.getByRole('button', { name: /Show all 25/ }));
      expect(screen.getAllByText(/^payload /)).toHaveLength(25);

      rerender(<ResultsDashboard results={withFindings(25)} loading={false} />);
      expect(screen.getAllByText(/^payload /)).toHaveLength(10);
    });
  });

  describe('quoted evidence', () => {
    it('keeps recovered text in its own element, apart from our prose', () => {
      // Rule 3 of the design system, and the reason the Finding DTO is split:
      // a crafted document must not be able to supply text that reads as ours.
      const { container } = render(
        <ResultsDashboard results={withFindings(1)} loading={false} />,
      );

      const quote = container.querySelector('.evidence-quote');
      const description = container.querySelector('.evidence-description');
      expect(quote?.textContent).toBe('payload 1');
      expect(description?.textContent).toContain('invisible text rendering mode');
      expect(description?.textContent).not.toContain('payload 1');
      expect(container.querySelector('.evidence-location')?.textContent).toBe('Page 1');
    });
  });

  describe('failure state', () => {
    it('reports a failure without claiming anything about the document', () => {
      render(<ResultsDashboard results={{ error: 'Backend unreachable' }} loading={false} />);

      expect(screen.getByText('Scan failed')).toBeDefined();
      expect(screen.getByText('Backend unreachable')).toBeDefined();
    });

    it('offers a retry only when there is a file to retry', () => {
      const onRetry = vi.fn();
      render(
        <ResultsDashboard results={{ error: 'nope' }} loading={false} onRetry={onRetry} />,
      );
      fireEvent.click(screen.getByRole('button', { name: /Try again/ }));
      expect(onRetry).toHaveBeenCalledOnce();

      cleanup();
      render(<ResultsDashboard results={{ error: 'nope' }} loading={false} />);
      expect(screen.queryByRole('button', { name: /Try again/ })).toBeNull();
    });
  });

  describe('waiting', () => {
    it('reports elapsed time and offers a way out', () => {
      const onCancel = vi.fn();
      render(<ResultsDashboard results={null} loading onCancel={onCancel} />);

      // Elapsed seconds are the one honest progress signal, and unlike the spinner
      // they still move under prefers-reduced-motion.
      expect(screen.getByText('0s')).toBeDefined();
      fireEvent.click(screen.getByRole('button', { name: /Cancel scan/ }));
      expect(onCancel).toHaveBeenCalledOnce();
    });

    it('counts upward while the scan runs', () => {
      vi.useFakeTimers();
      try {
        render(<ResultsDashboard results={null} loading />);
        // The interval updates state, so the DOM only reflects it inside act().
        act(() => { vi.advanceTimersByTime(3000); });
        expect(screen.getByText('3s')).toBeDefined();
      } finally {
        vi.useRealTimers();
      }
    });
  });

  it('says nothing has been scanned yet before the first scan', () => {
    render(<ResultsDashboard results={null} loading={false} />);
    expect(screen.getByText('No document scanned yet')).toBeDefined();
  });
});
