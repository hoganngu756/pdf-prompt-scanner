import { useState, useEffect } from 'react';
import { Plus, Trash2, Edit2, Save, X, ToggleLeft, ToggleRight, HelpCircle, Key } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { HeuristicRule } from '../types';

import { api, ApiError } from '../api';
import { getAdminKey, setAdminKey } from '../adminKey';

export default function RulesManager() {
  const [rules, setRules] = useState<HeuristicRule[]>([]);
  const [loading, setLoading] = useState(true);
  
  const [newPhrase, setNewPhrase] = useState('');
  const [newIsRegex, setNewIsRegex] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editPhrase, setEditPhrase] = useState('');
  const [editIsRegex, setEditIsRegex] = useState(false);

  // Admin access key storage (shared with the History tab via ./adminKey)
  const [adminKey, setAdminKeyState] = useState(() => getAdminKey());

  const handleAdminKeyChange = (val: string) => {
    setAdminKeyState(val);
    setAdminKey(val);
  };

  /** A rule with an unparseable regex is skipped silently at scan time, so catch it here. */
  const isInvalidRegex = (phrase: string, useRegex: boolean): boolean => {
    if (!useRegex) return false;
    try {
      new RegExp(phrase);
      return false;
    } catch (err) {
      toast.error(`Invalid regular expression: ${err instanceof Error ? err.message : 'check the syntax'}`);
      return true;
    }
  };

  /** One place to turn an ApiError into user-facing feedback. */
  const reportError = (err: unknown, fallback: string) => {
    console.error(err);
    if (err instanceof ApiError && err.isAuthProblem) {
      toast.error('Unauthorized: enter a valid Admin API Key in the field above.');
      return;
    }
    toast.error(err instanceof Error ? err.message : fallback);
  };

  const fetchRules = async () => {
    try {
      setRules(await api.listRules());
    } catch (err) {
      reportError(err, 'Failed to load rules');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRules();
  }, []);

  const handleAddRule = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPhrase.trim()) {
      toast.error('Rule phrase cannot be empty');
      return;
    }
    if (isInvalidRegex(newPhrase.trim(), newIsRegex)) return;

    setSubmitting(true);
    try {
      const newRule = await api.createRule({
        phrase: newPhrase.trim(), isRegex: newIsRegex, active: true,
      });
      setRules(prev => [...prev, newRule]);
      setNewPhrase('');
      setNewIsRegex(false);
      toast.success('Rule added');
    } catch (err) {
      reportError(err, 'Failed to add rule');
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleActive = async (rule: HeuristicRule) => {
    try {
      const updatedRule = await api.updateRule(rule.id, {
        phrase: rule.phrase, isRegex: rule.isRegex, active: !rule.active,
      });
      setRules(prev => prev.map(r => r.id === rule.id ? updatedRule : r));
      toast.success(`Rule ${updatedRule.active ? 'enabled' : 'disabled'}`);
    } catch (err) {
      reportError(err, 'Failed to update rule status');
    }
  };

  const handleDeleteRule = async (id: number) => {
    if (!confirm('Are you sure you want to delete this rule?')) return;

    try {
      await api.deleteRule(id);
      setRules(prev => prev.filter(r => r.id !== id));
      toast.success('Rule deleted');
    } catch (err) {
      reportError(err, 'Failed to delete rule');
    }
  };

  const startEdit = (rule: HeuristicRule) => {
    setEditingId(rule.id);
    setEditPhrase(rule.phrase);
    setEditIsRegex(rule.isRegex);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditPhrase('');
    setEditIsRegex(false);
  };

  const handleSaveEdit = async (ruleId: number, currentActive: boolean) => {
    if (!editPhrase.trim()) {
      toast.error('Rule phrase cannot be empty');
      return;
    }
    if (isInvalidRegex(editPhrase.trim(), editIsRegex)) return;

    try {
      const updatedRule = await api.updateRule(ruleId, {
        phrase: editPhrase.trim(), isRegex: editIsRegex, active: currentActive,
      });
      setRules(prev => prev.map(r => r.id === ruleId ? updatedRule : r));
      cancelEdit();
      toast.success('Rule updated');
    } catch (err) {
      reportError(err, 'Failed to save rule updates');
    }
  };

  return (
    <>
      <div className="page-header">
        <h2>Heuristic rules</h2>
        <p>
          Literal phrases and regular expressions the scanner matches against text
          recovered from a document, including its metadata and annotations.
        </p>
      </div>

      <div className="rules-layout">
        <div className="admin-key-panel">
          <label htmlFor="admin-key">
            <Key size={14} />
            Admin Authorization Key
          </label>
          <input
            id="admin-key"
            type="password"
            placeholder="Enter Admin API Key to modify rules"
            value={adminKey}
            onChange={e => handleAdminKeyChange(e.target.value)}
          />
          <span className="field-hint">
            Required for adding, editing, deleting, or toggling heuristic rules, and for
            viewing scan history.
          </span>
        </div>

        <form onSubmit={handleAddRule} className="rule-form">
          <div className="form-row">
            <div className="input-group">
              <label htmlFor="rule-phrase">Detection Phrase / Pattern</label>
              <input 
                id="rule-phrase"
                type="text" 
                placeholder="e.g. ignore all instructions"
                value={newPhrase}
                onChange={e => setNewPhrase(e.target.value)}
                disabled={submitting}
              />
            </div>
            <div className="checkbox-group">
              <label className="toggle-label">
                <input 
                  type="checkbox"
                  checked={newIsRegex}
                  onChange={e => setNewIsRegex(e.target.checked)}
                  disabled={submitting}
                />
                Regex
              </label>
              <span className="tooltip-info" title="Regex rules match raw patterns. Non-regex rules are auto-expanded to ignore spacing, dots, and common text obfuscation.">
                <HelpCircle size={14} />
              </span>
            </div>
            <button type="submit" className="btn-primary add-rule-btn" disabled={submitting}>
              <Plus size={16} />
              Add Rule
            </button>
          </div>
        </form>

        {loading ? (
          <div className="empty-state">
            <span>Loading rules…</span>
          </div>
        ) : rules.length === 0 ? (
          <div className="empty-state">
            <strong>No rules defined</strong>
            <span>The scanner cannot flag phrases until at least one rule is active.</span>
          </div>
        ) : (
          <>
          <div className="section-head">
            <span className="eyebrow">Rules</span>
            <span className="eyebrow tabular">{rules.filter(r => r.active).length} active / {rules.length}</span>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Pattern</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th className="cell-right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {rules.map(rule => {
                  const isEditing = editingId === rule.id;
                  return (
                    <tr key={rule.id}>
                      <td>
                        {isEditing ? (
                          <input 
                            type="text"
                            className="edit-phrase-input"
                            value={editPhrase}
                            onChange={e => setEditPhrase(e.target.value)}
                          />
                        ) : (
                          <code className="rule-phrase">{rule.phrase}</code>
                        )}
                      </td>
                      <td>
                        {isEditing ? (
                          <label className="toggle-label">
                            <input 
                              type="checkbox"
                              checked={editIsRegex}
                              onChange={e => setEditIsRegex(e.target.checked)}
                            />
                            Regex
                          </label>
                        ) : (
                          <span className="chip is-mono">
                            {rule.isRegex ? 'regex' : 'literal'}
                          </span>
                        )}
                      </td>
                      <td>
                        <button
                          onClick={() => handleToggleActive(rule)}
                          className={`status-toggle-btn ${rule.active ? 'is-active' : ''}`}
                          title={rule.active ? 'Disable rule' : 'Enable rule'}
                          aria-pressed={rule.active}
                        >
                          {rule.active ? <ToggleRight size={24} /> : <ToggleLeft size={24} />}
                          <span>{rule.active ? 'Active' : 'Off'}</span>
                        </button>
                      </td>
                      <td className="cell-right">
                        <div className="rule-actions">
                          {isEditing ? (
                            <>
                              <button onClick={() => handleSaveEdit(rule.id, rule.active)} className="action-icon-btn save" title="Save changes">
                                <Save size={16} />
                              </button>
                              <button onClick={cancelEdit} className="action-icon-btn cancel" title="Cancel editing">
                                <X size={16} />
                              </button>
                            </>
                          ) : (
                            <>
                              <button onClick={() => startEdit(rule)} className="action-icon-btn edit" title="Edit rule">
                                <Edit2 size={16} />
                              </button>
                              <button onClick={() => handleDeleteRule(rule.id)} className="action-icon-btn delete" title="Delete rule">
                                <Trash2 size={16} />
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          </>
        )}
      </div>
    </>
  );
}
