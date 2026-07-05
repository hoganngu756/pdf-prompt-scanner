import { useState } from 'react';
import { FileUp, FileText, Settings, ShieldAlert, Cpu, ScanSearch, Loader2 } from 'lucide-react';

interface UploadSectionProps {
  file: File | null;
  setFile: (file: File | null) => void;
  handleFileChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
  useHeuristics: boolean;
  setUseHeuristics: (val: boolean) => void;
  useLLM: boolean;
  setUseLLM: (val: boolean) => void;
  loading: boolean;
  handleScan: () => void;
}

export default function UploadSection({ 
  file, 
  setFile,
  handleFileChange, 
  useHeuristics, 
  setUseHeuristics, 
  useLLM, 
  setUseLLM, 
  loading, 
  handleScan 
}: UploadSectionProps) {
  const [dragActive, setDragActive] = useState(false);

  const handleDrag = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true);
    } else if (e.type === "dragleave") {
      setDragActive(false);
    }
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const droppedFile = e.dataTransfer.files[0];
      if (droppedFile.type === "application/pdf" || droppedFile.name.toLowerCase().endsWith(".pdf")) {
        setFile(droppedFile);
      }
    }
  };

  return (
    <section className="card upload-section">
      <h2 className="card-title">
        <FileUp size={24} color="var(--accent-color)" />
        Upload Document
      </h2>
      
      <div 
        className={`file-drop-zone ${dragActive ? 'active' : ''}`}
        onClick={() => document.getElementById('file-upload')?.click()}
        onDragEnter={handleDrag}
        onDragOver={handleDrag}
        onDragLeave={handleDrag}
        onDrop={handleDrop}
      >
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

      <div style={{ marginTop: '12px', fontSize: '0.85rem', color: 'var(--text-secondary)', textAlign: 'center', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}>
        <span style={{ fontSize: '1rem' }}>ℹ️</span>
        <em>Note: Backend runs on a free tier. The first scan may take up to 60s to wake up.</em>
      </div>
    </section>
  );
}
