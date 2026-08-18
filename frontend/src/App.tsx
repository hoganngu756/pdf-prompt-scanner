import { useState, useEffect, useRef } from 'react'
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
import { buildChecks, overallVerdict } from './verdict'
import { loadHistory, recordScan, clearHistory, restoreHistory, HistoryEntry } from './scanHistory'

function App() {
  const [file, setFile] = useState<File | null>(null)
  const [useLLM, setUseLLM] = useState(true)
  const [useHeuristics, setUseHeuristics] = useState(true)
  const [results, setResults] = useState<ScanResponse | null>(null)
  const [cancelled, setCancelled] = useState(false)
  const abortRef = useRef<AbortController | null>(null)
  const workRef = useRef<HTMLDivElement>(null)
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
    const controller = new AbortController()
    abortRef.current = controller
    setLoading(true)
    setResults(null)
    setCancelled(false)

    try {
      const scanResults = await api.scan(targetFile, useLLM, useHeuristics, controller.signal)
      setResults(scanResults)
      // Recorded in this browser only; the server keeps nothing.
      setHistory(recordScan(targetFile.name, scanResults))
    } catch (error) {
      // The user asked for this one; it is not a failure to report as one.
      if ((error as Error)?.name === 'AbortError') {
        setCancelled(true)
        return
      }
      console.error('Error during scan:', error)
      const errorMsg = error instanceof Error ? error.message : 'Failed to connect to the server'
      setResults({ error: errorMsg })
    } finally {
      abortRef.current = null
      setLoading(false)
    }
  }

  const cancelScan = () => abortRef.current?.abort()

  // Clearing is unrecoverable — this is the only copy of these scans. An undo is
  // less interruptive than a confirm dialog and covers the same mistake.
  const handleClearHistory = () => {
    const cleared = history
    setHistory(clearHistory())
    toast((t) => (
      <span className="toast-undo">
        Scan history cleared
        <button
          className="btn-secondary"
          onClick={() => { setHistory(restoreHistory(cleared)); toast.dismiss(t.id) }}
        >
          Undo
        </button>
      </span>
    ), { duration: 8000 })
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

  // Starting a scan from a sample row leaves the user scrolled past the results
  // area, watching the guide it just unmounted. Bring the progress state to them.
  useEffect(() => {
    if (!loading || !workRef.current) return
    // Already looking at it (the usual desktop case) — don't yank the page.
    const { top } = workRef.current.getBoundingClientRect()
    if (top >= 0 && top <= window.innerHeight) return
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    workRef.current.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' })
  }, [loading])

  // The dashboard can't carry aria-live itself: React mounts the region and its
  // content in the same commit, which assistive tech doesn't reliably detect.
  // This region is always present, and it announces the headline only — routing
  // the whole report through it would read every finding aloud.
  const announcement = (() => {
    if (loading) return 'Scanning document.'
    if (cancelled) return 'Scan cancelled.'
    if (!results) return ''
    if (results.error) return `Scan failed. ${results.error}`
    const verdict = overallVerdict(buildChecks(results))
    return `${verdict.headline}. ${verdict.summary}`
  })()

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
          },
          // The library's default icons ship their own saturated green/red, which
          // is chroma we don't control. Only errors get a toast now, and its mark
          // uses verdict red from the palette.
          error: {
            iconTheme: { primary: 'var(--danger)', secondary: 'var(--n-0)' }
          }
        }}
      />
      <Header activeTab={activeTab} setActiveTab={setActiveTab} />

      <main>
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
        <div className="main-content">
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
          <div className="work" ref={workRef}>
            <p className="sr-only" role="status" aria-live="polite">{announcement}</p>
            <ResultsDashboard
              results={results}
              loading={loading}
              fileName={file?.name}
              onRetry={file ? () => runScan(file) : undefined}
              onCancel={cancelScan}
            />
            {/* Keep the guide visible until a *successful* scan replaces it, so an
                error doesn't strand the user with no examples to retry from. */}
            {!loading && (!results || !!results.error) && (
              <>
                <WelcomeGuide />
                <ExamplePdfs onSelectSample={handleSelectSample} />
              </>
            )}
          </div>
        </div>
        </>
      )}

      {activeTab === 'history' && (
        <HistoryTable history={history} onClear={handleClearHistory} />
      )}

      {activeTab === 'rules' && (
        <RulesManager />
      )}
      </main>
    </div>
  )
}

export default App
