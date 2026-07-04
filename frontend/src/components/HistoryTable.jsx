export default function HistoryTable({ history }) {
  return (
    <div className="card">
      <h2 className="card-title">Scan History</h2>
      {history.length === 0 ? (
        <p style={{color: 'var(--text-secondary)'}}>No scans have been performed yet.</p>
      ) : (
        <div style={{overflowX: 'auto'}}>
          <table style={{width: '100%', borderCollapse: 'collapse'}}>
            <thead>
              <tr style={{borderBottom: '2px solid var(--border-color)', textAlign: 'left'}}>
                <th style={{padding: '12px'}}>Date</th>
                <th style={{padding: '12px'}}>File Name</th>
                <th style={{padding: '12px'}}>Status</th>
                <th style={{padding: '12px'}}>Explanation</th>
              </tr>
            </thead>
            <tbody>
              {history.map(record => (
                <tr key={record.id} style={{borderBottom: '1px solid var(--border-color)'}}>
                  <td style={{padding: '12px', whiteSpace: 'nowrap'}}>{new Date(record.scanDate).toLocaleString()}</td>
                  <td style={{padding: '12px', fontWeight: '500'}}>{record.fileName}</td>
                  <td style={{padding: '12px'}}>
                    <span className={`badge ${record.safe ? 'safe' : 'danger'}`}>
                      {record.safe ? 'Secure' : 'Flagged'}
                    </span>
                  </td>
                  <td style={{padding: '12px', fontSize: '0.9rem', color: 'var(--text-secondary)'}}>
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
