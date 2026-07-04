export default function UploadSection({ 
  file, 
  handleFileChange, 
  useHeuristics, 
  setUseHeuristics, 
  useLLM, 
  setUseLLM, 
  loading, 
  handleScan 
}) {
  return (
    <section className="card upload-section">
      <h2 className="card-title">Upload Document</h2>
      
      <div className="file-drop-zone" onClick={() => document.getElementById('file-upload').click()}>
        <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1" strokeLinecap="round" strokeLinejoin="round" style={{color: 'var(--text-secondary)'}}>
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
          <polyline points="14 2 14 8 20 8"/>
          <line x1="12" y1="18" x2="12" y2="12"/>
          <line x1="9" y1="15" x2="15" y2="15"/>
        </svg>
        <h3>{file ? file.name : 'Drag & drop PDF files here'}</h3>
        <p>{file ? `${(file.size / 1024 / 1024).toFixed(2)} MB` : 'or click to browse'}</p>
        <input 
          id="file-upload"
          type="file" 
          accept="application/pdf" 
          onChange={handleFileChange} 
          style={{ display: 'none' }} 
        />
      </div>

      <div className="settings-group">
        <label className="toggle-label">
          <input 
            type="checkbox" 
            checked={useHeuristics} 
            onChange={(e) => setUseHeuristics(e.target.checked)} 
          />
          Heuristics Scan
        </label>
        <label className="toggle-label">
          <input 
            type="checkbox" 
            checked={useLLM} 
            onChange={(e) => setUseLLM(e.target.checked)} 
          />
          LLM Analysis
        </label>
      </div>

      <button 
        className="btn-primary"
        onClick={handleScan} 
        disabled={!file || loading}
      >
        {loading ? (
          <><span className="loader"></span> Scanning Document...</>
        ) : 'Analyze Document'}
      </button>
    </section>
  );
}
