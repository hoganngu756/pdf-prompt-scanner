import { useState, useEffect } from 'react';
import { Plus, Trash2, Edit2, Save, X, ToggleLeft, ToggleRight, HelpCircle, Key } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { HeuristicRule } from '../types';

import { API_BASE_URL } from '../config';
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

  const getHeaders = (extraHeaders: Record<string, string> = {}) => {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...extraHeaders
    };
    if (adminKey.trim()) {
      headers['X-Admin-Api-Key'] = adminKey.trim();
    }
    return headers;
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

  const handleResponseError = async (response: Response, fallbackMessage: string) => {
    if (response.status === 401) {
      toast.error('Unauthorized: Please enter a valid Admin API Key in the settings field below.');
      return;
    }
    try {
      const data = await response.json();
      toast.error(data.error || fallbackMessage);
    } catch {
      toast.error(fallbackMessage);
    }
  };

  const fetchRules = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/rules`);
      if (!res.ok) throw new Error('Failed to fetch rules');
      const data = await res.json();
      setRules(data);
    } catch (err) {
      console.error(err);
      toast.error('Failed to load rules');
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
      const response = await fetch(`${API_BASE_URL}/rules`, {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify({
          phrase: newPhrase.trim(),
          isRegex: newIsRegex,
          active: true
        })
      });

      if (!response.ok) {
        await handleResponseError(response, 'Failed to create rule');
        return;
      }
      
      const newRule = await response.json();
      setRules(prev => [...prev, newRule]);
      setNewPhrase('');
      setNewIsRegex(false);
      toast.success('Rule added successfully');
    } catch (err) {
      console.error(err);
      toast.error('Failed to add rule');
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleActive = async (rule: HeuristicRule) => {
    try {
      const response = await fetch(`${API_BASE_URL}/rules/${rule.id}`, {
        method: 'PUT',
        headers: getHeaders(),
        body: JSON.stringify({
          phrase: rule.phrase,
          isRegex: rule.isRegex,
          active: !rule.active
        })
      });

      if (!response.ok) {
        await handleResponseError(response, 'Failed to update rule status');
        return;
      }
      const updatedRule = await response.json();
      setRules(prev => prev.map(r => r.id === rule.id ? updatedRule : r));
      toast.success(`Rule ${updatedRule.active ? 'enabled' : 'disabled'}`);
    } catch (err) {
      console.error(err);
      toast.error('Failed to update rule status');
    }
  };

  const handleDeleteRule = async (id: number) => {
    if (!confirm('Are you sure you want to delete this rule?')) return;

    try {
      const response = await fetch(`${API_BASE_URL}/rules/${id}`, {
        method: 'DELETE',
        headers: getHeaders()
      });

      if (!response.ok) {
        await handleResponseError(response, 'Failed to delete rule');
        return;
      }
      setRules(prev => prev.filter(r => r.id !== id));
      toast.success('Rule deleted successfully');
    } catch (err) {
      console.error(err);
      toast.error('Failed to delete rule');
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
      const response = await fetch(`${API_BASE_URL}/rules/${ruleId}`, {
        method: 'PUT',
        headers: getHeaders(),
        body: JSON.stringify({
          phrase: editPhrase.trim(),
          isRegex: editIsRegex,
          active: currentActive
        })
      });

      if (!response.ok) {
        await handleResponseError(response, 'Failed to save rule updates');
        return;
      }
      const updatedRule = await response.json();
      setRules(prev => prev.map(r => r.id === ruleId ? updatedRule : r));
      cancelEdit();
      toast.success('Rule updated successfully');
    } catch (err) {
      console.error(err);
      toast.error('Failed to save rule updates');
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
