import { FileUp, FileText, Settings, ShieldAlert, Cpu, ScanSearch, Loader2 } from 'lucide-react';

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
      <h2 className="card-title">
        <FileUp size={24} color="var(--accent-color)" />
        Upload Document
      </h2>
      
      <div className="file-drop-zone" onClick={() => document.getElementById('file-upload').click()}>
        <div className="file-drop-icon">
          {file ? <FileText size={48} /> : <FileUp size={48} />}
        </div>
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
        <h4 style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px', color: 'var(--text-secondary)' }}>
          <Settings size={18} />
          Scan Configurations
        </h4>
        <label className="toggle-label">
          <input 
            type="checkbox" 
            checked={useHeuristics} 
            onChange={(e) => setUseHeuristics(e.target.checked)} 
          />
          <ShieldAlert size={18} color={useHeuristics ? "var(--accent-color)" : "var(--text-secondary)"} />
          Heuristics Scan
        </label>
        <label className="toggle-label">
          <input 
            type="checkbox" 
            checked={useLLM} 
            onChange={(e) => setUseLLM(e.target.checked)} 
          />
          <Cpu size={18} color={useLLM ? "var(--accent-color)" : "var(--text-secondary)"} />
          LLM AI Analysis
        </label>
      </div>

      <button 
        className="btn-primary"
        onClick={handleScan} 
        disabled={!file || loading}
      >
        {loading ? (
          <><Loader2 className="animate-spin" size={20} /> Scanning Document...</>
        ) : (
          <><ScanSearch size={20} /> Analyze Document</>
        )}
      </button>
    </section>
  );
}
