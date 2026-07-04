import { AlertTriangle, CheckCircle, ShieldAlert, FileSearch, Sparkles, LayoutDashboard } from 'lucide-react';

export default function ResultsDashboard({ results, loading }) {
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
            <div className="preview-container">
              <div className="preview-header">
                <h3><FileSearch size={18} /> Document Preview</h3>
                <span style={{fontSize: '0.8rem', color: 'var(--text-secondary)'}}>
                  {results.previewImagesBase64.length} flagged page(s) shown
                </span>
              </div>
              <div style={{display: 'flex', flexDirection: 'column', gap: '16px'}}>
                {results.previewImagesBase64.map((img, idx) => (
                  <img key={idx} src={img} alt={`PDF Preview Page ${idx + 1}`} />
                ))}
              </div>
            </div>
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
                    {results.heuristicResult.flags.map((flag, idx) => (
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
