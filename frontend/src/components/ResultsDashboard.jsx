export default function ResultsDashboard({ results, loading }) {
  return (
    <section className="card results-section">
      <h2 className="card-title">Analysis Dashboard</h2>
      
      {!results && !loading && (
        <div style={{color: 'var(--text-secondary)', textAlign: 'center', marginTop: '40px'}}>
          Upload a document to see security insights here.
        </div>
      )}

      {results?.error && (
        <div className="result-card danger">
          <div className="result-content">{results.error}</div>
        </div>
      )}

      {results && !results.error && (
        <>
          {results.previewImagesBase64 && results.previewImagesBase64.length > 0 && (
            <div className="preview-container">
              <div className="preview-header">
                <h3>Document Preview</h3>
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
                <h3>Heuristic Engine</h3>
                <span className={`badge ${results.heuristicResult.safe ? 'safe' : 'danger'}`}>
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
                <h3>AI Context Analysis</h3>
                <span className={`badge ${results.llmResult.safe ? 'safe' : 'danger'}`}>
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
