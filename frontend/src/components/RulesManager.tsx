import { useState, useEffect } from 'react';
import { toast } from 'react-hot-toast';
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

  useEffect(() => {
    api
      .listRules()
      .then(setRules)
      .catch((err) => {
        console.error(err);
        toast.error(err instanceof Error ? err.message : 'Failed to load rules');
      })
      .finally(() => setLoading(false));
  }, []);

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
      ) : rules.length === 0 ? (
        <div className="empty-state">
          <strong>No rules configured</strong>
          <span>The heuristic engine cannot flag phrases until at least one rule exists.</span>
        </div>
      ) : (
        <>
          <div className="section-head">
            <span className="eyebrow">Rules</span>
            <span className="eyebrow tabular">{rules.length}</span>
          </div>
          <div className="table-wrap">
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
          <p className="field-hint" style={{ marginTop: 'var(--s-4)' }}>
            Edit <code>backend/src/main/resources/heuristic-rules.yml</code> and restart the
            server to change this set.
          </p>
        </>
      )}
    </>
  );
}
