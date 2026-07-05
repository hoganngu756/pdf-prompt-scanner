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
        <FileUp size={18} />
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
          {file ? <FileText size={40} /> : <FileUp size={40} />}
        </div>
        <h3>{file ? file.name : 'Drag & drop a PDF here'}</h3>
        <p>{file ? `${(file.size / 1024 / 1024).toFixed(2)} MB` : 'or click to browse files'}</p>
        <input 
          id="file-upload"
          type="file" 
          accept="application/pdf" 
          onChange={handleFileChange} 
          style={{ display: 'none' }} 
        />
      </div>

      <div className="settings-group">
        <h4 style={{ display: 'flex', alignItems: 'center', gap: '6px', marginBottom: '4px', color: '#6b7280', fontSize: '0.8rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.03em' }}>
          <Settings size={14} />
          Scan Options
        </h4>
        
        <div className="settings-option">
          <label className="toggle-label">
            <input 
              type="checkbox" 
              checked={useHeuristics} 
              onChange={(e) => setUseHeuristics(e.target.checked)} 
            />
            <ShieldAlert size={16} color={useHeuristics ? "#3b82f6" : "#9ca3af"} />
            Heuristics Scan
          </label>
          <div className="option-tooltip">
            <strong>Heuristics Engine</strong>
            <p>Scans the text for known malicious words, instruction overrides, and character obfuscation using static patterns.</p>
          </div>
        </div>

        <div className="settings-option">
          <label className="toggle-label">
            <input 
              type="checkbox" 
              checked={useLLM} 
              onChange={(e) => setUseLLM(e.target.checked)} 
            />
            <Cpu size={16} color={useLLM ? "#3b82f6" : "#9ca3af"} />
            AI Analysis (Gemini)
          </label>
          <div className="option-tooltip">
            <strong>AI Context Analysis</strong>
            <p>Uses Gemini AI to inspect the document and detect complex prompt hijacking or jailbreak attempts by checking context and semantic intent.</p>
          </div>
        </div>
      </div>

      <button 
        className="btn-primary"
        onClick={handleScan} 
        disabled={!file || loading}
      >
        {loading ? (
          <><Loader2 className="animate-spin" size={18} /> Scanning…</>
        ) : (
          <><ScanSearch size={18} /> Analyze Document</>
        )}
      </button>

      <p style={{ fontSize: '0.78rem', color: '#9ca3af', textAlign: 'center' }}>
        First scan may take ~60s if the backend is waking up.
      </p>
    </section>
  );
}
