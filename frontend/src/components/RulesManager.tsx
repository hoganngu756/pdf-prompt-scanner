import { useState, useEffect } from 'react';
import { AlertTriangle, RotateCw } from 'lucide-react';
import { HeuristicRule } from '../types';
import { api } from '../api';

/**
 * Read-only view of the detection rule set.
 *
 * Rules are configuration (backend/src/main/resources/heuristic-rules.yml), not
 * runtime state. Editing them here would require an admin credential, which was
 * the source of most of this project's security issues; a reviewable diff is a
 * better change mechanism for a security tool anyway.
 */
export default function RulesManager() {
  const [rules, setRules] = useState<HeuristicRule[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // A failed fetch must not fall through to the empty state: "no rules configured"
  // is a claim about the scanner's configuration, and we have no grounds to make it
  // when we never heard back from the server.
  const loadRules = () => {
    setLoading(true);
    setError(null);
    api
      .listRules()
      .then(setRules)
      .catch((err) => {
        console.error(err);
        setError(err instanceof Error ? err.message : 'Failed to load rules');
      })
      .finally(() => setLoading(false));
  };

  useEffect(() => { loadRules(); }, []);

  return (
    <>
      <div className="page-header">
        <h2>Detection rules</h2>
        <p>
          Phrases and patterns the heuristic engine matches against text recovered from a
          document — including its metadata, annotations and embedded images. Literal
          phrases tolerate spacing, punctuation and lookalike characters, so
          <code> іgnоre</code> in Cyrillic still matches.
        </p>
      </div>

      {loading ? (
        <div className="empty-state">
          <span>Loading rules…</span>
        </div>
      ) : error ? (
        <div className="notice is-warn" role="alert">
          <AlertTriangle size={16} />
          <div>
            <strong>Could not load the rule set</strong>
            {error}
            <p>
              This is a display failure, not a scanner one — it does not mean the engine
              has no rules. Scanning is unaffected.
            </p>
            <button type="button" className="btn-secondary" onClick={loadRules}>
              <RotateCw size={14} />
              Try again
            </button>
          </div>
        </div>
      ) : rules.length === 0 ? (
        <div className="empty-state">
          <strong>No rules configured</strong>
          <span>The heuristic engine cannot flag phrases until at least one rule exists.</span>
        </div>
      ) : (
        <>
          <div className="section-head">
            <h3 className="eyebrow">Rules</h3>
            <span className="eyebrow tabular">{rules.length}</span>
          </div>
          <div className="table-wrap" tabIndex={0} role="region" aria-label="Detection rules">
            <table>
              <thead>
                <tr>
                  <th>Pattern</th>
                  <th>Type</th>
                </tr>
              </thead>
              <tbody>
                {rules.map((rule) => (
                  <tr key={rule.phrase}>
                    <td><code className="rule-phrase">{rule.phrase}</code></td>
                    <td>
                      <span className="chip is-mono">{rule.isRegex ? 'regex' : 'literal'}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="field-hint table-footnote">
            Edit <code>backend/src/main/resources/heuristic-rules.yml</code> and restart the
            server to change this set.
          </p>
        </>
      )}
    </>
  );
}
