import React, { useState } from 'react';

function App() {
  const [file, setFile] = useState(null);
  const [useLLM, setUseLLM] = useState(false);
  const [useHeuristics, setUseHeuristics] = useState(true);
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState(null);

  const handleFileChange = (e) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
      setResults(null);
    }
  };

  const handleScan = async () => {
    if (!file) return;
    setLoading(true);
    setResults(null);

    const formData = new FormData();
    formData.append('file', file);
    formData.append('useLLM', useLLM);
    formData.append('useHeuristics', useHeuristics);

    try {
      // We will proxy /api to the backend in vite.config.js
      const response = await fetch('/api/scan', {
        method: 'POST',
        body: formData,
      });
      const data = await response.json();
      setResults(data);
    } catch (error) {
      console.error("Error scanning file", error);
      setResults({ error: "Failed to connect to the scanning server." });
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="app-container">
      <h1>PDF Prompt Scanner</h1>
      
      <div 
        className="upload-box" 
        onClick={() => document.getElementById('file-upload').click()}
      >
        <input 
          id="file-upload" 
          type="file" 
          accept="application/pdf" 
          onChange={handleFileChange} 
        />
        {file ? (
          <p>Selected: <strong>{file.name}</strong></p>
        ) : (
          <p>Click or drag a PDF here to upload</p>
        )}
      </div>

      <div className="options-container">
        <label className="option-label">
          <input 
            type="checkbox" 
            checked={useHeuristics} 
            onChange={(e) => setUseHeuristics(e.target.checked)} 
          />
          Heuristic Scan (Fast)
        </label>
        <label className="option-label">
          <input 
            type="checkbox" 
            checked={useLLM} 
            onChange={(e) => setUseLLM(e.target.checked)} 
          />
          LLM Deep Scan (Advanced)
        </label>
      </div>

      <div style={{ textAlign: 'center' }}>
        <button 
          className="btn" 
          onClick={handleScan} 
          disabled={!file || loading || (!useHeuristics && !useLLM)}
        >
          {loading ? 'Scanning...' : 'Scan PDF'}
        </button>
      </div>

      {results && (
        <div className="results">
          <h2>Scan Results</h2>
          {results.error ? (
            <div className="result-item danger">
              <p>{results.error}</p>
            </div>
          ) : (
            <>
              {results.heuristicResult && (
                <div className={`result-item ${results.heuristicResult.safe ? 'safe' : 'danger'}`}>
                  <h3>Heuristic Scan: {results.heuristicResult.safe ? 'Safe' : 'Suspicious'}</h3>
                  {!results.heuristicResult.safe && (
                    <ul>
                      {results.heuristicResult.flags.map((flag, idx) => (
                        <li key={idx}>{flag}</li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
              {results.llmResult && (
                <div className={`result-item ${results.llmResult.safe ? 'safe' : 'danger'}`}>
                  <h3>LLM Scan: {results.llmResult.safe ? 'Safe' : 'Suspicious'}</h3>
                  <p>{results.llmResult.analysis}</p>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}

export default App;
