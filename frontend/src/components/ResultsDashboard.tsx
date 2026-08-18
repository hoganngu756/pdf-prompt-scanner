import { useState, useEffect } from 'react';
import {
  AlertTriangle, ShieldCheck, HelpCircle, ChevronLeft, ChevronRight, Loader2, RotateCw,
} from 'lucide-react';
import { ScanResponse } from '../types';
import { buildChecks, overallVerdict, Check } from '../verdict';

// A single check can carry hundreds of findings — the backend caps visual findings
// at 200 and structure findings at 400 more. Past the first handful nobody is
// reading them, and the full list buries the verdict and the page preview.
const EVIDENCE_PREVIEW_COUNT = 10;

// Past this the scan is almost certainly waiting on a cold backend rather than a
// large document, and saying so is more use than repeating the phase list.
const SLOW_SCAN_SECONDS = 20;

interface ResultsDashboardProps {
  results: ScanResponse | null;
  loading: boolean;
  fileName?: string;
  onRetry?: () => void;
  onCancel?: () => void;
}

export default function ResultsDashboard({ results, loading, fileName, onRetry, onCancel }: ResultsDashboardProps) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [expandedChecks, setExpandedChecks] = useState<Set<string>>(new Set());
  const [elapsed, setElapsed] = useState(0);

  // The spinner is frozen for anyone on prefers-reduced-motion, so a scan needs a
  // progress signal that is text.
  useEffect(() => {
    if (!loading) {
      setElapsed(0);
      return;
    }
    const startedAt = Date.now();
    const id = setInterval(() => setElapsed(Math.floor((Date.now() - startedAt) / 1000)), 1000);
    return () => clearInterval(id);
  }, [loading]);

  useEffect(() => {
    setCurrentIndex(0);
    setExpandedChecks(new Set());
  }, [results]);

  const toggleEvidence = (name: string) => {
    setExpandedChecks((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  };

  if (loading) {
    return (
      <section className="scan-progress" aria-busy="true">
        <div className="scan-progress-status">
          <Loader2 size={18} className="animate-spin" />
          <span>Analysing document…</span>
          <span className="scan-progress-elapsed tabular">{elapsed}s</span>
        </div>
        <p className="scan-progress-note">
          {elapsed < SLOW_SCAN_SECONDS
            ? `Extracting text, inspecting document structure, rendering page previews and
               running the enabled checks.`
            : `Still working. The first scan can take up to a minute while the backend
               wakes up — the document is not being re-read.`}
        </p>
        <div className="skeleton skeleton-card" />
        <div className="skeleton skeleton-preview" />
        {onCancel && (
          <button type="button" className="btn-secondary scan-progress-cancel" onClick={onCancel}>
            Cancel scan
          </button>
        )}
      </section>
    );
  }

  if (!results) {
    return (
      <section className="empty-state">
        <strong>No document scanned yet</strong>
        <span>Upload a PDF, or run one of the sample documents below.</span>
      </section>
    );
  }

  if (results.error) {
    return (
      <section className="verdict is-warn is-failure">
        <div className="verdict-icon">
          <AlertTriangle size={22} />
        </div>
        <div>
          <h3 className="verdict-headline">Scan failed</h3>
          <p className="verdict-summary">{results.error}</p>
          {fileName && (
            <div className="verdict-meta">
              <span>{fileName}</span>
            </div>
          )}
          {onRetry && (
            <button type="button" className="btn-secondary verdict-retry" onClick={onRetry}>
              <RotateCw size={14} />
              Try again
            </button>
          )}
        </div>
      </section>
    );
  }

  const checks: Check[] = buildChecks(results);

  const verdict = overallVerdict(checks);
  const previews = results.previewImagesBase64 ?? [];
  const pageNumbers = results.previewPageNumbers;
  const sourcePage = pageNumbers?.[currentIndex];

  return (
    <>
      <section className={`verdict is-${verdict.state}`}>
        <div className="verdict-icon">
          {verdict.state === 'safe' ? <ShieldCheck size={22} />
            : verdict.state === 'warn' ? <HelpCircle size={22} />
            : <AlertTriangle size={22} />}
        </div>
        <div>
          <h3 className="verdict-headline">{verdict.headline}</h3>
          <p className="verdict-summary">{verdict.summary}</p>
          {fileName && (
            <div className="verdict-meta">
              <span>{fileName}</span>
              {previews.length > 0 && (
                <span>{previews.length} page{previews.length === 1 ? '' : 's'} rendered</span>
              )}
            </div>
          )}
        </div>
      </section>

      <section>
        <div className="section-head">
          <h3 className="eyebrow">Checks</h3>
          <span className="eyebrow tabular">{checks.length} run</span>
        </div>
        <div className="check-list">
          {checks.map((check) => {
            const evidence = check.evidence ?? [];
            const isExpanded = expandedChecks.has(check.name);
            const visible = isExpanded ? evidence : evidence.slice(0, EVIDENCE_PREVIEW_COUNT);

            return (
              <div key={check.name} className={`check is-${check.state}`}>
                <span className="check-name">{check.name}</span>
                <span className={`status is-${check.state}`}>{check.label}</span>
                {check.note && <p className="check-note">{check.note}</p>}
                {visible.length > 0 && (
                  <ul className="evidence-list">
                    {visible.map((finding, idx) => (
                      <li key={idx} className="evidence">
                        <span className="evidence-description">{finding.description}</span>
                        {finding.location && (
                          <span className="evidence-location">{finding.location}</span>
                        )}
                        {finding.quote && <q className="evidence-quote">{finding.quote}</q>}
                      </li>
                    ))}
                  </ul>
                )}
                {evidence.length > EVIDENCE_PREVIEW_COUNT && (
                  <button
                    type="button"
                    className="btn-secondary evidence-more"
                    aria-expanded={isExpanded}
                    aria-label={isExpanded
                      ? `Show only the first ${EVIDENCE_PREVIEW_COUNT} of ${evidence.length} for ${check.name}`
                      : `Show all ${evidence.length} for ${check.name}`}
                    onClick={() => toggleEvidence(check.name)}
                  >
                    {isExpanded ? 'Show fewer' : `Show all ${evidence.length}`}
                  </button>
                )}
              </div>
            );
          })}
        </div>
      </section>

      {previews.length > 0 && (
        <section className="preview">
          <div className="preview-header">
            <h3 className="eyebrow">Page preview</h3>
            <span className="preview-count tabular">
              {sourcePage
                ? `page ${sourcePage}${previews.length > 1 ? ` — ${currentIndex + 1}/${previews.length}` : ''}`
                : `${currentIndex + 1}/${previews.length}`}
            </span>
          </div>
          <div className="preview-frame">
            {previews.length > 1 && (
              <button
                className="carousel-nav-btn prev"
                aria-label="Previous page"
                onClick={() => setCurrentIndex((p) => (p - 1 + previews.length) % previews.length)}
              >
                <ChevronLeft size={16} />
              </button>
            )}
            <img src={previews[currentIndex]} alt={`Preview of page ${sourcePage ?? currentIndex + 1}`} />
            {previews.length > 1 && (
              <button
                className="carousel-nav-btn next"
                aria-label="Next page"
                onClick={() => setCurrentIndex((p) => (p + 1) % previews.length)}
              >
                <ChevronRight size={16} />
              </button>
            )}
          </div>
          {previews.length > 1 && (
            <div className="carousel-indicators">
              {previews.map((_, idx) => (
                <button
                  key={idx}
                  className={`carousel-indicator-dot ${currentIndex === idx ? 'active' : ''}`}
                  aria-label={`Go to page ${pageNumbers?.[idx] ?? idx + 1}`}
                  onClick={() => setCurrentIndex(idx)}
                />
              ))}
            </div>
          )}
        </section>
      )}
    </>
  );
}
