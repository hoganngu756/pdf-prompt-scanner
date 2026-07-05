import { History, CheckCircle, AlertTriangle, SearchX } from 'lucide-react';
import { ScanRecord } from '../types';

interface HistoryTableProps {
  history: ScanRecord[];
}

export default function HistoryTable({ history }: HistoryTableProps) {
  return (
    <div className="card">
      <h2 className="card-title">
        <History size={18} />
        Scan History
      </h2>
      {history.length === 0 ? (
        <div style={{ color: '#9ca3af', textAlign: 'center', padding: '48px 0', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '12px' }}>
          <SearchX size={40} color="#e5e7eb" />
          <p>No scans have been performed yet.</p>
        </div>
      ) : (
        <div className="history-table-container">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>File Name</th>
                <th>Status</th>
                <th>Details</th>
              </tr>
            </thead>
            <tbody>
              {history.map(record => (
                <tr key={record.id}>
                  <td style={{ whiteSpace: 'nowrap' }}>{new Date(record.scanDate).toLocaleString()}</td>
                  <td style={{ fontWeight: 500 }}>{record.fileName}</td>
                  <td>
                    <span className={`badge ${record.safe ? 'safe' : 'danger'}`}>
                      {record.safe ? <CheckCircle size={12} /> : <AlertTriangle size={12} />}
                      {record.safe ? 'Secure' : 'Flagged'}
                    </span>
                  </td>
                  <td style={{ fontSize: '0.85rem', color: '#6b7280', maxWidth: '320px' }}>
                    {(() => {
                      const details = [];
                      if (record.visualFlags) details.push(`[Visual: ${record.visualFlags}]`);
                      if (record.heuristicFlags) details.push(`[Heuristics: ${record.heuristicFlags}]`);
                      if (record.llmExplanation) details.push(`[AI: ${record.llmExplanation}]`);
                      return details.length > 0 ? details.join(' ') : 'No issues found';
                    })()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
