import { useState, useEffect } from 'react';
import {
  AlertTriangle, ShieldCheck, HelpCircle, ChevronLeft, ChevronRight, Loader2,
} from 'lucide-react';
import { ScanResponse } from '../types';
import { buildChecks, overallVerdict, Check } from '../verdict';

interface ResultsDashboardProps {
  results: ScanResponse | null;
  loading: boolean;
  fileName?: string;
}

export default function ResultsDashboard({ results, loading, fileName }: ResultsDashboardProps) {
  const [currentIndex, setCurrentIndex] = useState(0);

  useEffect(() => { setCurrentIndex(0); }, [results]);

  if (loading) {
    return (
      <section className="scan-progress" aria-live="polite" aria-busy="true">
        <div className="scan-progress-status">
          <Loader2 size={18} className="animate-spin" />
          <span>Analysing document…</span>
        </div>
        <p className="scan-progress-note">
          Extracting text, inspecting document structure, rendering page previews and
          running the enabled checks. The first scan can take up to a minute while the
          backend wakes up.
        </p>
        <div className="skeleton skeleton-card" />
        <div className="skeleton skeleton-preview" />
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
      <section className="notice is-danger" role="alert">
        <AlertTriangle size={16} />
        <div>
          <strong>Scan failed</strong>
          {results.error}
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
      <section className={`verdict is-${verdict.state}`} aria-live="polite">
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
          <span className="eyebrow">Checks</span>
          <span className="eyebrow tabular">{checks.length} run</span>
        </div>
        <div className="check-list">
          {checks.map((check) => (
            <div key={check.name} className={`check is-${check.state}`}>
              <span className="check-name">{check.name}</span>
              <span className={`status is-${check.state}`}>{check.label}</span>
              {check.note && <p className="check-note">{check.note}</p>}
              {check.evidence && check.evidence.length > 0 && (
                <ul className="evidence-list">
                  {check.evidence.map((item, idx) => (
                    <li key={idx} className="evidence">{item}</li>
                  ))}
                </ul>
              )}
            </div>
          ))}
        </div>
      </section>

      {previews.length > 0 && (
        <section className="preview">
          <div className="preview-header">
            <span className="eyebrow">Page preview</span>
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
