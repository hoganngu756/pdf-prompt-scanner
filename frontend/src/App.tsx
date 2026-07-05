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

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

function App() {
  const [file, setFile] = useState<File | null>(null)
  const [useLLM, setUseLLM] = useState(true)
  const [useHeuristics, setUseHeuristics] = useState(true)
  const [results, setResults] = useState<ScanResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('scan')
  const [history, setHistory] = useState<ScanRecord[]>([])

  const fetchHistory = async () => {
    try {
      const res = await fetch(`${API_BASE_URL}/history`)
      if (!res.ok) throw new Error('Network response was not ok')
      const data = await res.json()
      setHistory(data)
    } catch (err) {
      console.error('Failed to fetch history:', err)
      toast.error('Failed to fetch scan history')
    }
  }

  useEffect(() => {
    if (activeTab === 'history') {
      fetchHistory()
    }
  }, [activeTab])

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setFile(e.target.files[0])
    }
  }

  const handleScan = async () => {
    if (!file) {
      toast.error('Please select a file first')
      return
    }

    setLoading(true)
    setResults(null)

    const formData = new FormData()
    formData.append('file', file)
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

  const handleSelectSample = (sampleFile: File) => {
    setFile(sampleFile)
    setResults(null)
    toast.success(`Loaded "${sampleFile.name}" — click Analyze Document to scan it.`)
  }

  return (
    <div className="app-container">
      <Toaster 
        position="top-right"
        toastOptions={{
          style: {
            background: '#ffffff',
            color: '#111827',
            border: '1px solid #e5e7eb',
            fontSize: '0.875rem'
          }
        }} 
      />
      <Header activeTab={activeTab} setActiveTab={setActiveTab} />

      {activeTab === 'scan' && (
        <main className="main-content">
          <div>
            <UploadSection 
              file={file}
              setFile={setFile}
              handleFileChange={handleFileChange}
              useHeuristics={useHeuristics}
              setUseHeuristics={setUseHeuristics}
              useLLM={useLLM}
              setUseLLM={setUseLLM}
              loading={loading}
              handleScan={handleScan}
            />
            <div style={{ marginTop: '16px' }}>
              <div className="card">
                <ExamplePdfs onSelectSample={handleSelectSample} />
              </div>
            </div>
          </div>
          <div>
            <ResultsDashboard results={results} loading={loading} />
            {!results && !loading && (
              <div className="card" style={{ marginTop: '16px' }}>
                <WelcomeGuide />
              </div>
            )}
          </div>
        </main>
      )}

      {activeTab === 'history' && (
        <HistoryTable history={history} />
      )}

      {activeTab === 'rules' && (
        <RulesManager />
      )}
    </div>
  )
}

export default App
