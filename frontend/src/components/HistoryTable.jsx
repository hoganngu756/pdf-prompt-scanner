import { History, CheckCircle, AlertTriangle, SearchX } from 'lucide-react';

export default function HistoryTable({ history }) {
  return (
    <div className="card">
      <h2 className="card-title">
        <History size={24} color="var(--accent-color)" />
        Scan History
      </h2>
      {history.length === 0 ? (
        <div style={{color: 'var(--text-secondary)', textAlign: 'center', padding: '40px 0', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '16px'}}>
          <SearchX size={48} color="rgba(255,255,255,0.1)" />
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
                <th>Explanation</th>
              </tr>
            </thead>
            <tbody>
              {history.map(record => (
                <tr key={record.id}>
                  <td style={{whiteSpace: 'nowrap'}}>{new Date(record.scanDate).toLocaleString()}</td>
                  <td style={{fontWeight: '500'}}>{record.fileName}</td>
                  <td>
                    <span className={`badge ${record.safe ? 'safe' : 'danger'}`}>
                      {record.safe ? <CheckCircle size={14} /> : <AlertTriangle size={14} />}
                      {record.safe ? 'Secure' : 'Flagged'}
                    </span>
                  </td>
                  <td style={{fontSize: '0.95rem', color: 'var(--text-secondary)'}}>
                    {record.llmExplanation || record.heuristicFlags || 'No issues found'}
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
