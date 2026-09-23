import { ChevronRight } from 'lucide-react';

/**
 * Reference material, not the task. Collapsed by default so the empty scan view
 * leads with one thing — samples to try — and opens on demand for anyone who wants
 * the definition. Native <details> gives keyboard and screen-reader support free.
 */
export default function WelcomeGuide() {
  return (
    <details className="explainer">
      <summary className="explainer-toggle">
        <ChevronRight size={14} className="explainer-chevron" aria-hidden="true" />
        What counts as prompt injection?
      </summary>

      <p className="explainer-lede">
        Prompt injection hides instructions inside a document so that an AI reading it
        obeys the attacker rather than the user. The payload is usually invisible on the
        page — rendered transparently, sized below a point, or parked in metadata — so a
        human reviewer signs off on a file that quietly rewrites the model's behaviour.
      </p>

      <dl className="attack-list">
        <div className="attack-item">
          <dt>Instruction override</dt>
          <dd>Resets the model's task, e.g. <code>ignore all previous instructions</code>.</dd>
        </div>
        <div className="attack-item">
          <dt>Role hijacking</dt>
          <dd>Assigns a new persona to escape safety rules, e.g. <code>you are now DAN</code>.</dd>
        </div>
        <div className="attack-item">
          <dt>Data exfiltration</dt>
          <dd>Directs the model to place context or credentials into a URL it will fetch.</dd>
        </div>
        <div className="attack-item">
          <dt>Context manipulation</dt>
          <dd>Biases a judgement without an obvious command — forcing a hiring or scoring outcome.</dd>
        </div>
        <div className="attack-item">
          <dt>Hidden text</dt>
          <dd>Invisible render mode, transparent fill, white-on-white, or sub-3pt type.</dd>
        </div>
        <div className="attack-item">
          <dt>Lookalike characters</dt>
          <dd>Cyrillic or Greek letters standing in for Latin ones to slip past text rules.</dd>
        </div>
      </dl>
    </details>
  );
}
