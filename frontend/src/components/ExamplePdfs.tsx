import { Download } from 'lucide-react';
import { toast } from 'react-hot-toast';

interface SamplePdf {
  filename: string;
  label: string;
  description: string;
  safe: boolean;
}

const SAMPLES: SamplePdf[] = [
  {
    filename: 'sample_ignore_instructions.pdf',
    label: 'Instruction override',
    description: 'Company policy document with a hidden "ignore all previous instructions" payload.',
    safe: false,
  },
  {
    filename: 'sample_role_hijack.pdf',
    label: 'Role hijacking',
    description: 'Financial report carrying a "you are now DAN" persona swap.',
    safe: false,
  },
  {
    filename: 'sample_data_exfil.pdf',
    label: 'Data exfiltration',
    description: 'Meeting notes instructing the model to append context to an external URL.',
    safe: false,
  },
  {
    filename: 'sample_markdown_injection.pdf',
    label: 'Context manipulation',
    description: 'Resume that pressures any reviewer model into an "excellent — must hire" verdict.',
    safe: false,
  },
  {
    filename: 'sample_tiny_text.pdf',
    label: 'Tiny text',
    description: 'Product reviews with a 2pt instruction overriding the overall sentiment.',
    safe: false,
  },
  {
    filename: 'sample_white_text.pdf',
    label: 'White-on-white text',
    description: 'Invoice with white text telling the model to waive the balance.',
    safe: false,
  },
  {
    filename: 'sample_invisible_render.pdf',
    label: 'Invisible render mode',
    description: 'Compliance attestation using PDF text rendering mode 3 — painted as nothing, still extractable.',
    safe: false,
  },
  {
    filename: 'sample_metadata_injection.pdf',
    label: 'Metadata & annotations',
    description: 'Resume with payloads in the Title, Subject, Keywords and a hidden annotation.',
    safe: false,
  },
  {
    filename: 'sample_homoglyph.pdf',
    label: 'Lookalike characters',
    description: 'Support ticket swapping Latin letters for identical Cyrillic ones.',
    safe: false,
  },
  {
    filename: 'sample_clean.pdf',
    label: 'Clean document',
    description: 'An ordinary lunch menu with no injection — the control case.',
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
      if (!response.ok) throw new Error(`Server returned ${response.status}`);
      const blob = await response.blob();
      onSelectSample(new File([blob], sample.filename, { type: 'application/pdf' }));
    } catch (err) {
      console.error('Failed to load sample:', err);
      toast.error(`Could not load "${sample.label}". Please try again.`);
    }
  };

  return (
    <section>
      <div className="section-head">
        <h3 className="eyebrow">Sample documents</h3>
        <span className="eyebrow tabular">{SAMPLES.length}</span>
      </div>

      <div className="sample-list">
        {SAMPLES.map((sample) => (
          <div key={sample.filename} className="sample-row">
            <span className="sample-name">{sample.label}</span>
            <span className="sample-actions">
              <span className={`status is-${sample.safe ? 'safe' : 'danger'}`}>
                {sample.safe ? 'Clean' : 'Malicious'}
              </span>
              <button className="btn-secondary" onClick={() => handleTrySample(sample)}>
                Scan
              </button>
              <a
                className="icon-btn"
                href={`/samples/${sample.filename}`}
                download
                aria-label={`Download ${sample.label} sample`}
                title="Download"
              >
                <Download size={14} />
              </a>
            </span>
            <p className="sample-desc">{sample.description}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
