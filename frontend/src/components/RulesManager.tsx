import { useState, useEffect } from 'react';
import { Settings, Plus, Trash2, Edit2, Save, X, ToggleLeft, ToggleRight, HelpCircle, Key } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { HeuristicRule } from '../types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

export default function RulesManager() {
  const [rules, setRules] = useState<HeuristicRule[]>([]);
  const [loading, setLoading] = useState(true);
  
  const [newPhrase, setNewPhrase] = useState('');
  const [newIsRegex, setNewIsRegex] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editPhrase, setEditPhrase] = useState('');
  const [editIsRegex, setEditIsRegex] = useState(false);

  // Admin access key storage
  const [adminKey, setAdminKey] = useState(() => localStorage.getItem('pdf_promptscanner_admin_key') || '');

  const handleAdminKeyChange = (val: string) => {
    setAdminKey(val);
    localStorage.setItem('pdf_promptscanner_admin_key', val);
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
    <div className="main-content" style={{ gridTemplateColumns: '1fr' }}>
      <div className="card">
        <h2 className="card-title">
          <Settings size={18} />
          Heuristics Rules
        </h2>

        {/* Admin Secret Configuration Section */}
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
          backgroundColor: 'var(--color-gray-50)',
          border: '1px solid var(--color-gray-200)',
          padding: '16px',
          borderRadius: 'var(--radius-md)',
          marginBottom: '20px'
        }}>
          <label htmlFor="admin-key" style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '0.8rem', fontWeight: 600, color: 'var(--color-gray-600)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>
            <Key size={14} />
            Admin Authorization Key
          </label>
          <input 
            id="admin-key"
            type="password" 
            placeholder="Enter Admin API Key to modify rules"
            value={adminKey}
            onChange={e => handleAdminKeyChange(e.target.value)}
            style={{
              padding: '9px 12px',
              border: '1px solid var(--color-gray-300)',
              borderRadius: 'var(--radius-sm)',
              fontSize: '0.875rem',
              width: '100%',
              maxWidth: '350px',
              fontFamily: 'monospace'
            }}
          />
          <span style={{ fontSize: '0.75rem', color: 'var(--color-gray-400)' }}>
            Required for adding, editing, deleting, or toggling heuristic rules on the server.
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
                <HelpCircle size={14} color="#9ca3af" />
              </span>
            </div>
            <button type="submit" className="btn-primary add-rule-btn" disabled={submitting}>
              <Plus size={16} />
              Add Rule
            </button>
          </div>
        </form>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#9ca3af' }}>
            Loading rules…
          </div>
        ) : rules.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '40px 0', color: '#9ca3af' }}>
            No heuristic rules defined yet.
          </div>
        ) : (
          <div className="history-table-container" style={{ marginTop: '16px' }}>
            <table>
              <thead>
                <tr>
                  <th>Pattern</th>
                  <th>Type</th>
                  <th>Status</th>
                  <th style={{ textAlign: 'right' }}>Actions</th>
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
                          <code style={{ fontSize: '0.85rem' }}>{rule.phrase}</code>
                        )}
                      </td>
                      <td>
                        {isEditing ? (
                          <label className="toggle-label" style={{ fontSize: '0.8rem' }}>
                            <input 
                              type="checkbox"
                              checked={editIsRegex}
                              onChange={e => setEditIsRegex(e.target.checked)}
                            />
                            Regex
                          </label>
                        ) : (
                          <span className={`badge ${rule.isRegex ? 'danger' : 'safe'}`} style={{ fontSize: '0.7rem', padding: '2px 8px' }}>
                            {rule.isRegex ? 'Regex' : 'Literal'}
                          </span>
                        )}
                      </td>
                      <td>
                        <button 
                          onClick={() => handleToggleActive(rule)}
                          className="status-toggle-btn"
                          title={rule.active ? 'Disable rule' : 'Enable rule'}
                          style={{
                            background: 'none',
                            border: 'none',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            color: rule.active ? '#16a34a' : '#9ca3af',
                            transition: 'color 0.15s'
                          }}
                        >
                          {rule.active ? <ToggleRight size={24} /> : <ToggleLeft size={24} />}
                          <span style={{ marginLeft: '6px', fontSize: '0.8rem' }}>
                            {rule.active ? 'Active' : 'Off'}
                          </span>
                        </button>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '4px' }}>
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
        )}
      </div>
    </div>
  );
}
