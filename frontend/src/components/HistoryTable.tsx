import { Trash2 } from 'lucide-react';
import { HistoryEntry } from '../scanHistory';

interface HistoryTableProps {
  history: HistoryEntry[];
  onClear: () => void;
}

export default function HistoryTable({ history, onClear }: HistoryTableProps) {
  return (
    <>
      <div className="page-header">
        <h2>Scan history</h2>
        <p>
          Documents you have scanned in this browser. Nothing is sent anywhere or shared —
          the server keeps no record of any scan.
        </p>
      </div>

      {history.length === 0 ? (
        <div className="empty-state">
          <strong>No scans yet</strong>
          <span>Results appear here once you have scanned a document.</span>
        </div>
      ) : (
        <>
          <div className="section-head">
            <span className="eyebrow">This browser</span>
            <button className="btn-secondary" onClick={onClear}>
              <Trash2 size={13} /> Clear history
            </button>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Scanned</th>
                  <th>Document</th>
                  <th>Verdict</th>
                  <th>Findings</th>
                </tr>
              </thead>
              <tbody>
                {history.map((entry) => (
                  <tr key={entry.id}>
                    <td className="cell-nowrap">
                      {new Date(entry.scannedAt).toLocaleString(undefined, {
                        month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit',
                      })}
                    </td>
                    <td className="cell-filename">{entry.fileName}</td>
                    <td>
                      <span className={`status is-${entry.state}`}>{entry.headline}</span>
                    </td>
                    <td className="cell-details">
                      {entry.findings.length > 0 ? entry.findings.join('\n') : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  );
}
