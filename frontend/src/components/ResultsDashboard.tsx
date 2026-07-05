import { useState, useEffect } from 'react';
import { AlertTriangle, CheckCircle, ShieldAlert, FileSearch, Sparkles, LayoutDashboard, ChevronLeft, ChevronRight } from 'lucide-react';
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
        <LayoutDashboard size={24} color="var(--accent-color)" />
        Analysis Dashboard
      </h2>
      
      {!results && !loading && (
        <div style={{color: 'var(--text-secondary)', textAlign: 'center', marginTop: '40px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px'}}>
          <FileSearch size={48} color="rgba(255,255,255,0.1)" />
          <p>Upload a document to see security insights here.</p>
        </div>
      )}

      {results?.error && (
        <div className="result-card danger">
          <div className="result-header">
            <h3><AlertTriangle size={20} color="var(--danger-color)" /> Scan Error</h3>
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
                    <h3><FileSearch size={18} /> Document Preview</h3>
                    <span style={{fontSize: '0.85rem', color: 'var(--text-secondary)', fontWeight: '500'}}>
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
                        <ChevronLeft size={22} />
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
                        <ChevronRight size={22} />
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

          {results.heuristicResult && (
            <div className={`result-card ${results.heuristicResult.safe ? 'safe' : 'danger'}`}>
              <div className="result-header">
                <h3><ShieldAlert size={20} /> Heuristic Engine</h3>
                <span className={`badge ${results.heuristicResult.safe ? 'safe' : 'danger'}`}>
                  {results.heuristicResult.safe ? <CheckCircle size={14} /> : <AlertTriangle size={14} />}
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
                <h3><Sparkles size={20} /> AI Context Analysis</h3>
                <span className={`badge ${results.llmResult.safe ? 'safe' : 'danger'}`}>
                  {results.llmResult.safe ? <CheckCircle size={14} /> : <AlertTriangle size={14} />}
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
