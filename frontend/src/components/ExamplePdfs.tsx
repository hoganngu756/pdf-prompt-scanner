import { Download, FileWarning, AlertTriangle, CheckCircle } from 'lucide-react';

interface SamplePdf {
  filename: string;
  label: string;
  description: string;
  attackType: string;
  safe: boolean;
}

const SAMPLES: SamplePdf[] = [
  {
    filename: 'sample_ignore_instructions.pdf',
    label: 'Instruction Override',
    description: 'A company policy document with hidden "Ignore all previous instructions" injection that attempts to extract confidential data.',
    attackType: 'Instruction Override',
    safe: false,
  },
  {
    filename: 'sample_role_hijack.pdf',
    label: 'Role Hijacking',
    description: 'A financial report containing a "You are now DAN" role-hijack attempt that tries to bypass safety guidelines.',
    attackType: 'Role Hijacking',
    safe: false,
  },
  {
    filename: 'sample_data_exfil.pdf',
    label: 'Data Exfiltration',
    description: 'Meeting notes that instruct the AI to leak all context data to an external URL via query parameters.',
    attackType: 'Data Exfiltration',
    safe: false,
  },
  {
    filename: 'sample_markdown_injection.pdf',
    label: 'Context Manipulation',
    description: 'A resume with embedded instructions that force the AI to always rate it as "EXCELLENT - MUST HIRE."',
    attackType: 'Context Manipulation',
    safe: false,
  },
  {
    filename: 'sample_tiny_text.pdf',
    label: 'Tiny Text (Visual)',
    description: 'A product review feed containing a tiny, 2pt font instruction that overrides overall review sentiments.',
    attackType: 'Obfuscated text',
    safe: false,
  },
  {
    filename: 'sample_white_text.pdf',
    label: 'White Text (Visual)',
    description: 'A billing invoice containing white-on-white (RGB 255, 255, 255) text instructing the AI to waive the invoice balance.',
    attackType: 'Invisible text',
    safe: false,
  },
  {
    filename: 'sample_clean.pdf',
    label: 'Clean Document',
    description: 'A normal team lunch menu with no injections. Use this to see what a safe scan result looks like.',
    attackType: 'None',
    safe: true,
  },
];

interface ExamplePdfsProps {
  onSelectSample: (file: File) => void;
}

export default function ExamplePdfs({ onSelectSample }: ExamplePdfsProps) {
  const handleTrySample = async (sample: SamplePdf) => {
    try {
      const response = await fetch(`/samples/${sample.filename}`);
      const blob = await response.blob();
      const file = new File([blob], sample.filename, { type: 'application/pdf' });
      onSelectSample(file);
    } catch (err) {
      console.error('Failed to load sample:', err);
    }
  };

  return (
    <div className="examples-section">
      <h3 className="examples-title">
        <FileWarning size={18} />
        Try with example PDFs
      </h3>
      <p className="examples-subtitle">
        Download or load these sample documents to see the scanner in action. Each contains a different prompt injection technique.
      </p>

      <div className="sample-list">
        {SAMPLES.map((sample) => (
          <div key={sample.filename} className={`sample-item ${sample.safe ? 'safe' : 'danger'}`}>
            <div className="sample-info">
              <div className="sample-label">
                {sample.safe 
                  ? <CheckCircle size={14} color="#16a34a" /> 
                  : <AlertTriangle size={14} color="#dc2626" />
                }
                <strong>{sample.label}</strong>
                {!sample.safe && (
                  <span className="sample-tag">{sample.attackType}</span>
                )}
              </div>
              <p className="sample-desc">{sample.description}</p>
            </div>
            <div className="sample-actions">
              <button 
                className="sample-btn try"
                onClick={() => handleTrySample(sample)}
                title="Load this sample into the scanner"
              >
                Load & Scan
              </button>
              <a 
                href={`/samples/${sample.filename}`} 
                download 
                className="sample-btn download"
                title="Download this PDF"
              >
                <Download size={14} />
              </a>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
