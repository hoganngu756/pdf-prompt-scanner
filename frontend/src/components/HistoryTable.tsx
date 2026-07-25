import { History, CheckCircle, AlertTriangle, SearchX, KeyRound } from 'lucide-react';
import { ScanRecord } from '../types';

interface HistoryTableProps {
  history: ScanRecord[];
  error?: string | null;
}

export default function HistoryTable({ history, error }: HistoryTableProps) {
  return (
    <div className="card">
      <h2 className="card-title">
        <History size={18} />
        Scan History
      </h2>
      {error ? (
        <div className="result-card warning">
          <div className="result-header">
            <h3><KeyRound size={16} /> Admin key required</h3>
          </div>
          <div className="result-content">
            <p>{error}</p>
          </div>
        </div>
      ) : history.length === 0 ? (
        <div className="empty-state">
          <SearchX size={40} />
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
                  <td className="cell-nowrap">{new Date(record.scanDate).toLocaleString()}</td>
                  <td className="cell-filename">{record.fileName}</td>
                  <td>
                    <span className={`badge ${record.safe ? 'safe' : 'danger'}`}>
                      {record.safe ? <CheckCircle size={12} /> : <AlertTriangle size={12} />}
                      {record.safe ? 'Secure' : 'Flagged'}
                    </span>
                  </td>
                  <td className="cell-details">
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
