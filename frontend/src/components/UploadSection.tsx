import { useState } from 'react';
import { FileUp, FileText, ScanSearch, Loader2 } from 'lucide-react';

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
  handleScan,
}: UploadSectionProps) {
  const [dragActive, setDragActive] = useState(false);

  const handleDrag = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') setDragActive(true);
    else if (e.type === 'dragleave') setDragActive(false);
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

  const openPicker = () => document.getElementById('file-upload')?.click();

  return (
    <section className="upload-section">
      <h3 className="rail-heading">Document</h3>

      <div
        className={`file-drop-zone ${dragActive ? 'active' : ''} ${file ? 'has-file' : ''}`}
        role="button"
        tabIndex={0}
        aria-label={file ? `Selected ${file.name}. Activate to choose a different PDF.` : 'Choose a PDF to scan'}
        onClick={openPicker}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker(); }
        }}
        onDragEnter={handleDrag}
        onDragOver={handleDrag}
        onDragLeave={handleDrag}
        onDrop={handleDrop}
      >
        <div className="file-drop-icon">
          {file ? <FileText size={22} /> : <FileUp size={22} />}
        </div>
        <p className="file-drop-title">{file ? file.name : 'Drop a PDF here'}</p>
        <p>{file ? `${(file.size / 1024 / 1024).toFixed(2)} MB` : 'or click to browse · max 10 MB'}</p>
        <input
          id="file-upload"
          type="file"
          accept="application/pdf"
          onChange={handleFileChange}
          style={{ display: 'none' }}
        />
      </div>

      <div className="settings-group">
        <h4 className="settings-group-title">Checks</h4>

        {/* Always runs server-side, so it is shown as a fixed row rather than a
            toggle — otherwise its result looks like it came from nowhere. */}
        <div className="settings-option">
          <div className="toggle-label is-always-on">
            Visual obfuscation
            <span className="option-note">always</span>
          </div>
          <p className="option-desc">
            Invisible rendering mode, transparent fill, white-on-white text and fonts under 3pt.
          </p>
        </div>

        <div className="settings-option">
          <div className="toggle-label is-always-on">
            Document structure
            <span className="option-note">always</span>
          </div>
          <p className="option-desc">
            Metadata, annotations, bookmarks and form fields, plus embedded JavaScript and
            auto-run actions.
          </p>
        </div>

        <div className="settings-option">
          <label className="toggle-label">
            <input type="checkbox" checked={useHeuristics} onChange={(e) => setUseHeuristics(e.target.checked)} />
            Heuristic rules
          </label>
          <p className="option-desc">
            Literal and regex patterns from your rule set, tolerant of spacing, punctuation
            and lookalike characters.
          </p>
        </div>

        <div className="settings-option">
          <label className="toggle-label">
            <input type="checkbox" checked={useLLM} onChange={(e) => setUseLLM(e.target.checked)} />
            AI context analysis
          </label>
          <p className="option-desc">
            Gemini reads the extracted text and judges intent, catching novel injections no
            static rule covers.
          </p>
        </div>
      </div>

      <button className="btn-primary" onClick={handleScan} disabled={!file || loading}>
        {loading
          ? <><Loader2 className="animate-spin" size={15} /> Scanning…</>
          : <><ScanSearch size={15} /> Run scan</>}
      </button>

      <p className="upload-hint">First scan may take ~60s while the backend wakes up.</p>
    </section>
  );
}
