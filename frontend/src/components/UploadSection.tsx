import { useState } from 'react';
import { FileUp, FileText, Settings, ShieldAlert, Cpu, ScanSearch, Loader2, EyeOff } from 'lucide-react';

interface UploadSectionProps {
  file: File | null;
  /** Validates and accepts a candidate file; returns false if it was rejected. */
  onFileSelected: (file: File) => boolean;
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
  onFileSelected,
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
      // Delegates validation so a rejected drop reports why instead of doing nothing
      onFileSelected(e.dataTransfer.files[0]);
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
        role="button"
        tabIndex={0}
        aria-label={file ? `Selected file ${file.name}. Activate to choose a different PDF.` : 'Choose a PDF file to scan'}
        onClick={() => document.getElementById('file-upload')?.click()}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            document.getElementById('file-upload')?.click();
          }
        }}
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
        <h4 className="settings-group-title">
          <Settings size={14} />
          Scan Options
        </h4>
        
        {/* Always runs server-side, so it's shown as a fixed row rather than a
            toggle — otherwise its result card looks like it came from nowhere. */}
        <div className="settings-option">
          <div className="toggle-label is-always-on">
            <EyeOff size={16} className="option-icon is-on" />
            Visual Obfuscation Audit
            <span className="badge compact neutral">Always on</span>
          </div>
          <div className="option-tooltip">
            <strong>Visual Obfuscation Audit</strong>
            <p>Always runs. Detects white-on-white text and fonts under 3pt — payloads that are invisible to a human reader but not to an AI.</p>
          </div>
        </div>

        <div className="settings-option">
          <label className="toggle-label">
            <input
              type="checkbox"
              checked={useHeuristics}
              onChange={(e) => setUseHeuristics(e.target.checked)}
            />
            <ShieldAlert size={16} className={`option-icon ${useHeuristics ? 'is-on' : ''}`} />
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
            <Cpu size={16} className={`option-icon ${useLLM ? 'is-on' : ''}`} />
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

      <p className="upload-hint">
        First scan may take ~60s if the backend is waking up.
      </p>
    </section>
  );
}
