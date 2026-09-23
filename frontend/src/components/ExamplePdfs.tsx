import { ArrowRight, Download } from 'lucide-react';
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
    description: 'Policy document hiding "ignore all previous instructions".',
    safe: false,
  },
  {
    filename: 'sample_role_hijack.pdf',
    label: 'Role hijacking',
    description: 'Financial report with a "you are now DAN" persona swap.',
    safe: false,
  },
  {
    filename: 'sample_data_exfil.pdf',
    label: 'Data exfiltration',
    description: 'Meeting notes telling the model to send context to a URL.',
    safe: false,
  },
  {
    filename: 'sample_markdown_injection.pdf',
    label: 'Context manipulation',
    description: 'Resume steering a reviewer model to "must hire".',
    safe: false,
  },
  {
    filename: 'sample_tiny_text.pdf',
    label: 'Tiny text',
    description: 'Product reviews with a 2pt instruction flipping sentiment.',
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
    description: 'Attestation using render mode 3: never painted, still extracted.',
    safe: false,
  },
  {
    filename: 'sample_metadata_injection.pdf',
    label: 'Metadata & annotations',
    description: 'Resume with payloads in Title, Keywords and a hidden annotation.',
    safe: false,
  },
  {
    filename: 'sample_homoglyph.pdf',
    label: 'Lookalike characters',
    description: 'Support ticket with Cyrillic letters posing as Latin.',
    safe: false,
  },
  {
    filename: 'sample_clean.pdf',
    label: 'Clean document',
    description: 'An ordinary lunch menu. The control case.',
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

      {/* One action per row: the whole row scans. Download sits beside it rather
          than inside it, because interactive elements cannot nest. Nine of ten
          samples are payloads, so a "malicious" tag on each carries no signal;
          only the control case is marked. */}
      <ul className="sample-list">
        {SAMPLES.map((sample) => (
          <li key={sample.filename} className="sample-row">
            <button
              type="button"
              className="sample-scan"
              onClick={() => handleTrySample(sample)}
              aria-label={`Scan sample: ${sample.label}${sample.safe ? ' (clean control)' : ''}`}
            >
              <span className="sample-name">
                {sample.label}
                {sample.safe && <span className="status is-safe">Clean</span>}
              </span>
              <span className="sample-desc">{sample.description}</span>
              <span className="sample-go" aria-hidden="true">
                Scan <ArrowRight size={13} />
              </span>
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
          </li>
        ))}
      </ul>
    </section>
  );
}
