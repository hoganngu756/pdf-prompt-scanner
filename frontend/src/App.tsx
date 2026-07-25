import { useState, useEffect } from 'react'
import Header from './components/Header'
import UploadSection from './components/UploadSection'
import ResultsDashboard from './components/ResultsDashboard'
import HistoryTable from './components/HistoryTable'
import RulesManager from './components/RulesManager'
import WelcomeGuide from './components/WelcomeGuide'
import ExamplePdfs from './components/ExamplePdfs'
import { Toaster, toast } from 'react-hot-toast'
import { ScanResponse, ScanRecord } from './types'
import './index.css'

import { API_BASE_URL } from './config'
import { withAdminKey } from './adminKey'

function App() {
  const [file, setFile] = useState<File | null>(null)
  const [useLLM, setUseLLM] = useState(true)
  const [useHeuristics, setUseHeuristics] = useState(true)
  const [results, setResults] = useState<ScanResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('scan')
  const [history, setHistory] = useState<ScanRecord[]>([])

  const [historyError, setHistoryError] = useState<string | null>(null)

  const fetchHistory = async () => {
    setHistoryError(null)
    try {
      // History exposes other users' filenames and analyses, so it is credentialed
      const res = await fetch(`${API_BASE_URL}/history`, { headers: withAdminKey() })
      if (res.status === 401 || res.status === 503) {
        const msg = 'Scan history requires an Admin API Key. Enter it in the Rules tab.'
        setHistoryError(msg)
        setHistory([])
        return
      }
      if (!res.ok) throw new Error('Network response was not ok')
      const data = await res.json()
      setHistory(data)
    } catch (err) {
      console.error('Failed to fetch history:', err)
      setHistoryError('Failed to fetch scan history.')
      toast.error('Failed to fetch scan history')
    }
  }

  useEffect(() => {
    if (activeTab === 'history') {
      fetchHistory()
    }
  }, [activeTab])

  // Mirrors spring.servlet.multipart.max-file-size on the backend. Checking here
  // saves the user a full upload that the server would only reject at the end.
  const MAX_FILE_BYTES = 10 * 1024 * 1024

  const acceptFile = (candidate: File): boolean => {
    const isPdf =
      candidate.type === 'application/pdf' || candidate.name.toLowerCase().endsWith('.pdf')
    if (!isPdf) {
      toast.error(`"${candidate.name}" is not a PDF. Only PDF files can be scanned.`)
      return false
    }
    if (candidate.size > MAX_FILE_BYTES) {
      toast.error(
        `"${candidate.name}" is ${(candidate.size / 1024 / 1024).toFixed(1)} MB. The maximum size is 10 MB.`
      )
      return false
    }
    setFile(candidate)
    setResults(null)
    return true
  }

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      acceptFile(e.target.files[0])
    }
    // Allow re-selecting the same file after a rejection
    e.target.value = ''
  }

  // Takes the file explicitly so callers can scan immediately after selecting one,
  // without waiting for the `file` state update to land.
  const runScan = async (targetFile: File) => {
    setLoading(true)
    setResults(null)

    const formData = new FormData()
    formData.append('file', targetFile)
    formData.append('useLLM', String(useLLM))
    formData.append('useHeuristics', String(useHeuristics))

    try {
      const response = await fetch(`${API_BASE_URL}/scan`, {
        method: 'POST',
        body: formData,
      })
      
      let data: any;
      const contentType = response.headers.get("content-type");
      if (contentType && contentType.includes("application/json")) {
        data = await response.json();
      } else {
        const text = await response.text();
        data = { error: text || `Server returned error status ${response.status}` };
      }
      
      if (!response.ok) {
        throw new Error(data.error || 'Failed to scan document')
      }
      
      setResults(data)
      toast.success('Scan completed successfully')
    } catch (error) {
      console.error('Error during scan:', error)
      const errorMsg = error instanceof Error ? error.message : 'Failed to connect to the server';
      toast.error(errorMsg)
      setResults({ error: errorMsg })
    } finally {
      setLoading(false)
    }
  }

  const handleScan = () => {
    if (!file) {
      toast.error('Please select a file first')
      return
    }
    runScan(file)
  }

  const handleSelectSample = (sampleFile: File) => {
    setFile(sampleFile)
    runScan(sampleFile)
  }

  return (
    <div className="app-container">
      <Toaster
        position="top-right"
        toastOptions={{
          style: {
            background: 'var(--surface-card)',
            color: 'var(--text-primary)',
            border: '1px solid var(--border-default)',
            fontSize: 'var(--text-base)'
          }
        }}
      />
      <Header activeTab={activeTab} setActiveTab={setActiveTab} />

      {activeTab === 'scan' && (
        <>
        <div className="page-header">
          <h2>Audit a PDF before your AI reads it</h2>
          <p>
            Detects hidden instructions, invisible or microscopic text, and jailbreak
            attempts embedded in documents — the kinds of payloads that hijack an LLM
            without ever being visible to a human reader.
          </p>
        </div>
        <main className="main-content">
          <div>
            <UploadSection
              file={file}
              onFileSelected={acceptFile}
              handleFileChange={handleFileChange}
              useHeuristics={useHeuristics}
              setUseHeuristics={setUseHeuristics}
              useLLM={useLLM}
              setUseLLM={setUseLLM}
              loading={loading}
              handleScan={handleScan}
            />
          </div>
          <div className="results-aside">
            <ResultsDashboard results={results} loading={loading} />
            {/* Keep the guide visible until a *successful* scan replaces it, so an
                error doesn't strand the user with no examples to retry from. */}
            {!loading && (!results || !!results.error) && (
              <>
                <div className="card">
                  <WelcomeGuide />
                </div>
                <div className="card">
                  <ExamplePdfs onSelectSample={handleSelectSample} />
                </div>
              </>
            )}
          </div>
        </main>
        </>
      )}

      {activeTab === 'history' && (
        <HistoryTable history={history} error={historyError} />
      )}

      {activeTab === 'rules' && (
        <RulesManager />
      )}
    </div>
  )
}

export default App
