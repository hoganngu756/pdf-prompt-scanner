import { KeyRound } from 'lucide-react';
import { ScanRecord } from '../types';

interface HistoryTableProps {
  history: ScanRecord[];
  error?: string | null;
}

export default function HistoryTable({ history, error }: HistoryTableProps) {
  return (
    <>
      <div className="page-header">
        <h2>Scan history</h2>
        <p>Every document scanned by this instance, with the findings recorded at the time.</p>
      </div>

      {error ? (
        <div className="notice is-warn">
          <KeyRound size={16} />
          <div>
            <strong>Admin key required</strong>
            {error}
          </div>
        </div>
      ) : history.length === 0 ? (
        <div className="empty-state">
          <strong>No scans recorded</strong>
          <span>Results appear here once a document has been scanned.</span>
        </div>
      ) : (
        <>
          <div className="section-head">
            <span className="eyebrow">Records</span>
            <span className="eyebrow tabular">{history.length}</span>
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
                {history.map((record) => {
                  const details = [
                    record.visualFlags && `visual: ${record.visualFlags}`,
                    record.structureFlags && `structure: ${record.structureFlags}`,
                    record.heuristicFlags && `heuristic: ${record.heuristicFlags}`,
                    record.llmExplanation && `ai: ${record.llmExplanation}`,
                  ].filter(Boolean) as string[];

                  return (
                    <tr key={record.id}>
                      <td className="cell-nowrap">
                        {new Date(record.scanDate).toLocaleString(undefined, {
                          year: 'numeric', month: 'short', day: '2-digit',
                          hour: '2-digit', minute: '2-digit',
                        })}
                      </td>
                      <td className="cell-filename">{record.fileName}</td>
                      <td>
                        <span className={`status ${record.safe ? 'is-safe' : 'is-danger'}`}>
                          {record.safe ? 'Clean' : 'Flagged'}
                        </span>
                      </td>
                      <td className="cell-details">
                        {details.length > 0 ? details.join('\n') : '—'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  );
}
