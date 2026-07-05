import { ShieldAlert, Zap, FileText } from 'lucide-react';

export default function WelcomeGuide() {
  return (
    <div className="welcome-guide">
      <div className="welcome-header">
        <h2>What is PDF Prompt Injection?</h2>
        <p>
          Prompt injection is a security vulnerability where attackers embed hidden instructions 
          inside documents. When these PDFs are processed by AI systems (like ChatGPT, Gemini, or 
          Claude), the injected text can manipulate the AI into ignoring its safety rules, leaking 
          private data, or performing unintended actions.
        </p>
      </div>

      <div className="guide-steps">
        <h3>How to use this tool</h3>
        <div className="steps-grid">
          <div className="step-card">
            <div className="step-number">1</div>
            <div className="step-content">
              <h4><FileText size={16} /> Upload a PDF</h4>
              <p>Drag and drop or browse for any PDF document you want to audit.</p>
            </div>
          </div>
          <div className="step-card">
            <div className="step-number">2</div>
            <div className="step-content">
              <h4><Zap size={16} /> Choose scan mode</h4>
              <p><strong>Heuristics</strong> checks for known malicious phrases. <strong>AI Analysis</strong> uses Gemini to detect subtle, novel injections.</p>
            </div>
          </div>
          <div className="step-card">
            <div className="step-number">3</div>
            <div className="step-content">
              <h4><ShieldAlert size={16} /> Review results</h4>
              <p>The dashboard shows whether threats were found, with specific details on what was flagged and why.</p>
            </div>
          </div>
        </div>
      </div>

      <div className="attack-types">
        <h3>Common attack patterns this tool detects</h3>
        <div className="attack-grid">
          <div className="attack-card">
            <div className="attack-tag red">High Risk</div>
            <h4>Instruction Override</h4>
            <p>Text like <code>"Ignore all previous instructions"</code> that tries to reset the AI's behavior.</p>
          </div>
          <div className="attack-card">
            <div className="attack-tag red">High Risk</div>
            <h4>Role Hijacking</h4>
            <p>Phrases like <code>"You are now DAN"</code> that attempt to assign a new persona to bypass safeguards.</p>
          </div>
          <div className="attack-card">
            <div className="attack-tag orange">Medium Risk</div>
            <h4>Data Exfiltration</h4>
            <p>Instructions to include sensitive data in URLs, API calls, or responses to external parties.</p>
          </div>
          <div className="attack-card">
            <div className="attack-tag orange">Medium Risk</div>
            <h4>Context Manipulation</h4>
            <p>Subtle rewording that biases AI summaries, like forcing positive ratings on resumes.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
