import { useState, useEffect } from 'react';
import { AlertTriangle, CheckCircle, ShieldAlert, FileSearch, Sparkles, LayoutDashboard, ChevronLeft, ChevronRight, EyeOff } from 'lucide-react';
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
        <div style={{ color: '#9ca3af', textAlign: 'center', padding: '48px 0', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px' }}>
          <FileSearch size={40} color="#e5e7eb" />
          <p>Upload a document to see results here.</p>
        </div>
      )}

      {results?.error && (
        <div className="result-card danger">
          <div className="result-header">
            <h3><AlertTriangle size={16} color="#dc2626" /> Scan Error</h3>
          </div>
          <div className="result-content">{results.error}</div>
        </div>
      )}

      {results && !results.error && (
        <>
          {results.previewImagesBase64 && results.previewImagesBase64.length > 0 && (
            (() => {
              const previewImages = results.previewImagesBase64;
              return (
                <div className="preview-container">
                  <div className="preview-header">
                    <h3><FileSearch size={15} /> Document Preview</h3>
                    <span style={{ fontSize: '0.8rem', color: '#9ca3af', fontWeight: 500 }}>
                      Page {currentIndex + 1} of {previewImages.length}
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
                        alt={`PDF Preview Page ${currentIndex + 1}`} 
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
                          title={`Go to page ${idx + 1}`}
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

          {results.heuristicResult && (
            <div className={`result-card ${results.heuristicResult.safe ? 'safe' : 'danger'}`}>
              <div className="result-header">
                <h3>
                  <ShieldAlert size={16} />
                  Heuristic Engine
                </h3>
                <span className={`badge ${results.heuristicResult.safe ? 'safe' : 'danger'}`}>
                  {results.heuristicResult.safe ? <CheckCircle size={12} /> : <AlertTriangle size={12} />}
                  {results.heuristicResult.safe ? 'Secure' : 'Flagged'}
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
                  <p>No known malicious patterns detected.</p>
                )}
              </div>
            </div>
          )}

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
