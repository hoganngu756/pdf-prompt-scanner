import { useState } from 'react'
import Header from './components/Header'
import UploadSection from './components/UploadSection'
import ResultsDashboard from './components/ResultsDashboard'
import HistoryTable from './components/HistoryTable'
import RulesManager from './components/RulesManager'
import WelcomeGuide from './components/WelcomeGuide'
import ExamplePdfs from './components/ExamplePdfs'
import { Toaster, toast } from 'react-hot-toast'
import { ScanResponse } from './types'
import './index.css'

import { api } from './api'
import { loadHistory, recordScan, clearHistory, HistoryEntry } from './scanHistory'

function App() {
  const [file, setFile] = useState<File | null>(null)
  const [useLLM, setUseLLM] = useState(true)
  const [useHeuristics, setUseHeuristics] = useState(true)
  const [results, setResults] = useState<ScanResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('scan')
  const [history, setHistory] = useState<HistoryEntry[]>(() => loadHistory())

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

    try {
      const scanResults = await api.scan(targetFile, useLLM, useHeuristics)
      setResults(scanResults)
      // Recorded in this browser only; the server keeps nothing.
      setHistory(recordScan(targetFile.name, scanResults))
      toast.success('Scan complete')
    } catch (error) {
      console.error('Error during scan:', error)
      const errorMsg = error instanceof Error ? error.message : 'Failed to connect to the server'
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
            background: 'var(--surface)',
            color: 'var(--ink)',
            border: '1px solid var(--rule)',
            fontSize: 'var(--t-small)',
            borderRadius: 'var(--r-md)'
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
          <div className="rail">
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
          <div className="work">
            <ResultsDashboard results={results} loading={loading} fileName={file?.name} />
            {/* Keep the guide visible until a *successful* scan replaces it, so an
                error doesn't strand the user with no examples to retry from. */}
            {!loading && (!results || !!results.error) && (
              <>
                <WelcomeGuide />
                <ExamplePdfs onSelectSample={handleSelectSample} />
              </>
            )}
          </div>
        </main>
        </>
      )}

      {activeTab === 'history' && (
        <HistoryTable history={history} onClear={() => setHistory(clearHistory())} />
      )}

      {activeTab === 'rules' && (
        <RulesManager />
      )}
    </div>
  )
}

export default App
