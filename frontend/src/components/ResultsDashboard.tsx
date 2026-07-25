import { useState, useEffect } from 'react';
import { AlertTriangle, CheckCircle, ShieldAlert, FileSearch, Sparkles, LayoutDashboard, ChevronLeft, ChevronRight, EyeOff, Loader2 } from 'lucide-react';
import { ScanResponse } from '../types';

interface ResultsDashboardProps {
  results: ScanResponse | null;
  loading: boolean;
}

export default function ResultsDashboard({ results, loading }: ResultsDashboardProps) {
  const [currentIndex, setCurrentIndex] = useState(0);

  useEffect(() => {
    setCurrentIndex(0);
  }, [results]);

  return (
    <section className="card results-section">
      <h2 className="card-title">
        <LayoutDashboard size={18} />
        Analysis Results
      </h2>
      
      {!results && !loading && (
        <div className="empty-state">
          <FileSearch size={40} />
          <p>Upload a document to see results here.</p>
        </div>
      )}

      {loading && (
        <div className="scan-progress" aria-live="polite" aria-busy="true">
          <div className="scan-progress-status">
            <Loader2 size={16} className="animate-spin" />
            <span>Analyzing document…</span>
          </div>
          <p className="scan-progress-note">
            Extracting text, rendering page previews, and running the enabled checks.
            The first scan can take up to a minute while the backend wakes up.
          </p>
          <div className="skeleton skeleton-preview" />
          <div className="skeleton skeleton-card" />
          <div className="skeleton skeleton-card" />
        </div>
      )}

      {results?.error && (
        <div className="result-card danger">
          <div className="result-header">
            <h3><AlertTriangle size={16} /> Scan Error</h3>
          </div>
          <div className="result-content">{results.error}</div>
        </div>
      )}

      {results && !results.error && (
        <>
          {results.previewImagesBase64 && results.previewImagesBase64.length > 0 && (
            (() => {
              const previewImages = results.previewImagesBase64;
              // Only flagged pages get rendered, so the label must come from the
              // backend's real page numbers — not the carousel index.
              const pageNumbers = results.previewPageNumbers;
              const sourcePage = pageNumbers?.[currentIndex];
              return (
                <div className="preview-container">
                  <div className="preview-header">
                    <h3><FileSearch size={15} /> Document Preview</h3>
                    <span className="preview-count">
                      {sourcePage
                        ? `Page ${sourcePage}${previewImages.length > 1 ? ` — ${currentIndex + 1} of ${previewImages.length} shown` : ''}`
                        : `Page ${currentIndex + 1} of ${previewImages.length}`}
                    </span>
                  </div>
                  
                  <div className="carousel-body">
                    {previewImages.length > 1 && (
                      <button 
                        onClick={() => setCurrentIndex(prev => (prev - 1 + previewImages.length) % previewImages.length)}
                        className="carousel-nav-btn prev"
                        title="Previous Page"
                      >
                        <ChevronLeft size={18} />
                      </button>
                    )}

                    <div className="carousel-slide">
                      <img 
                        src={previewImages[currentIndex]} 
                        alt={`PDF preview of page ${sourcePage ?? currentIndex + 1}`}
                      />
                    </div>

                    {previewImages.length > 1 && (
                      <button 
                        onClick={() => setCurrentIndex(prev => (prev + 1) % previewImages.length)}
                        className="carousel-nav-btn next"
                        title="Next Page"
                      >
                        <ChevronRight size={18} />
                      </button>
                    )}
                  </div>

                  {previewImages.length > 1 && (
                    <div className="carousel-indicators">
                      {previewImages.map((_, idx) => (
                        <button
                          key={idx}
                          onClick={() => setCurrentIndex(idx)}
                          className={`carousel-indicator-dot ${currentIndex === idx ? 'active' : ''}`}
                          title={`Go to page ${pageNumbers?.[idx] ?? idx + 1}`}
                        />
                      ))}
                    </div>
                  )}
                </div>
              );
            })()
          )}

          {results.visualObfuscationResult && (
            <div className={`result-card ${results.visualObfuscationResult.safe ? 'safe' : 'danger'}`}>
              <div className="result-header">
                <h3>
                  <EyeOff size={16} />
                  Visual Obfuscation Audit
                </h3>
                <span className={`badge ${results.visualObfuscationResult.safe ? 'safe' : 'danger'}`}>
                  {results.visualObfuscationResult.safe ? <CheckCircle size={12} /> : <AlertTriangle size={12} />}
                  {results.visualObfuscationResult.safe ? 'Secure' : 'Flagged'}
                </span>
              </div>
              <div className="result-content">
                {!results.visualObfuscationResult.safe ? (
                  <ul>
                    {results.visualObfuscationResult.findings?.map((finding, idx) => (
                      <li key={idx}>{finding}</li>
                    ))}
                  </ul>
                ) : (
                  <p>No invisible, tiny, or obfuscated text detected.</p>
                )}
              </div>
            </div>
          )}

          {results.heuristicResult && (() => {
            // activeRuleCount === 0 means the engine ran with no rules, so it
            // checked nothing. That is a configuration warning, not a verdict.
            const notConfigured = results.heuristicResult.activeRuleCount === 0;
            const state = notConfigured ? 'warning' : results.heuristicResult.safe ? 'safe' : 'danger';
            const label = notConfigured ? 'Not configured' : results.heuristicResult.safe ? 'Secure' : 'Flagged';

            return (
              <div className={`result-card ${state}`}>
                <div className="result-header">
                  <h3>
                    <ShieldAlert size={16} />
                    Heuristic Engine
                  </h3>
                  <span className={`badge ${state}`}>
                    {state === 'safe' ? <CheckCircle size={12} /> : <AlertTriangle size={12} />}
                    {label}
                  </span>
                </div>
                <div className="result-content">
                  {!results.heuristicResult.safe ? (
                    <ul>
                      {results.heuristicResult.flags?.map((flag, idx) => (
                        <li key={idx}>{flag}</li>
                      ))}
                    </ul>
                  ) : (
                    <p>
                      No known malicious patterns detected
                      {results.heuristicResult.activeRuleCount
                        ? ` (checked against ${results.heuristicResult.activeRuleCount} active rule${results.heuristicResult.activeRuleCount === 1 ? '' : 's'}).`
                        : '.'}
                    </p>
                  )}
                </div>
              </div>
            );
          })()}

          {results.llmResult && (
            <div className={`result-card ${results.llmResult.safe ? 'safe' : 'danger'}`}>
              <div className="result-header">
                <h3>
                  <Sparkles size={16} />
                  AI Context Analysis
                </h3>
                <span className={`badge ${results.llmResult.safe ? 'safe' : 'danger'}`}>
                  {results.llmResult.safe ? <CheckCircle size={12} /> : <AlertTriangle size={12} />}
                  {results.llmResult.safe ? 'Secure' : 'Flagged'}
                </span>
              </div>
              <div className="result-content">
                <p>{results.llmResult.analysis}</p>
              </div>
            </div>
          )}
        </>
      )}
    </section>
  );
}
