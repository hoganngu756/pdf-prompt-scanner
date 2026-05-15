import { useState } from 'react'
import './index.css'

function App() {
  const [file, setFile] = useState(null)
  const [useLLM, setUseLLM] = useState(true)
  const [useHeuristics, setUseHeuristics] = useState(true)
  const [results, setResults] = useState(null)
  const [loading, setLoading] = useState(false)

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files.length > 0) {
      setFile(e.target.files[0])
    }
  }

  const handleScan = async () => {
    if (!file) return

    setLoading(true)
    setResults(null)

    const formData = new FormData()
    formData.append('file', file)
    formData.append('useLLM', useLLM)
    formData.append('useHeuristics', useHeuristics)

    try {
      const response = await fetch('http://localhost:8080/api/scan', {
        method: 'POST',
        body: formData,
      })
      const data = await response.json()
      setResults(data)
    } catch (error) {
      console.error('Error during scan:', error)
      setResults({ error: 'Failed to connect to the server.' })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app-container">
      <header>
        <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{color: 'var(--accent-color)'}}>
          <path d="M21.2 15c.7-1.2 1-2.5.7-3.9-.6-2-2.4-3.5-4.4-3.5h-1.2c-.7-3-3.2-5.2-6.2-5.6-3-.3-5.9 1.3-7.3 4-1.2 2.5-1 6.5.5 8.8m8.7-1.6V21"/>
          <path d="M16 16l-4-4-4 4"/>
        </svg>
        <h1>PDF Prompt Scanner</h1>
      </header>

      <main className="main-content">
        {/* Left Column: Upload */}
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

        {/* Right Column: Results */}
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
                    <p>{results.llmResult.reason}</p>
                  </div>
                </div>
              )}
            </>
          )}
        </section>
      </main>
    </div>
  )
}

export default App
