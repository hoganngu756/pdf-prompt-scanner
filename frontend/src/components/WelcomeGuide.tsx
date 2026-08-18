/**
 * Compressed from a full explainer to a short definition plus a reference list.
 * The previous version ran to roughly 1,600px of brochure copy above the tool's
 * own output, which buried the thing people came to use.
 */
export default function WelcomeGuide() {
  return (
    <section className="explainer">
      <div className="section-head">
        <h3 className="eyebrow">What this looks for</h3>
      </div>

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
    </section>
  );
}
