import { useState, useEffect } from 'react'
import Header from './components/Header'
import UploadSection from './components/UploadSection'
import ResultsDashboard from './components/ResultsDashboard'
import HistoryTable from './components/HistoryTable'
import { Toaster, toast } from 'react-hot-toast'
import './index.css'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

function App() {
  const [file, setFile] = useState(null)
  const [useLLM, setUseLLM] = useState(true)
  const [useHeuristics, setUseHeuristics] = useState(true)
  const [results, setResults] = useState(null)
  const [loading, setLoading] = useState(false)
  const [activeTab, setActiveTab] = useState('scan')
  const [history, setHistory] = useState([])

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

  const handleFileChange = (e) => {
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
    formData.append('useLLM', useLLM)
    formData.append('useHeuristics', useHeuristics)

    try {
      const response = await fetch(`${API_BASE_URL}/scan`, {
        method: 'POST',
        body: formData,
      })
      const data = await response.json()
      
      if (!response.ok) {
        throw new Error(data.error || 'Failed to scan document')
      }
      
      setResults(data)
      toast.success('Scan completed successfully')
    } catch (error) {
      console.error('Error during scan:', error)
      toast.error(error.message || 'Failed to connect to the server')
      setResults({ error: error.message || 'Failed to connect to the server.' })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app-container">
      <Toaster 
        position="top-right"
        toastOptions={{
          style: {
            background: 'var(--bg-secondary)',
            color: 'var(--text-primary)',
            border: '1px solid var(--border-color)'
          }
        }} 
      />
      <Header activeTab={activeTab} setActiveTab={setActiveTab} />

      {activeTab === 'scan' && (
        <main className="main-content">
          <UploadSection 
            file={file}
            handleFileChange={handleFileChange}
            useHeuristics={useHeuristics}
            setUseHeuristics={setUseHeuristics}
            useLLM={useLLM}
            setUseLLM={setUseLLM}
            loading={loading}
            handleScan={handleScan}
          />
          <ResultsDashboard results={results} loading={loading} />
        </main>
      )}

      {activeTab === 'history' && (
        <HistoryTable history={history} />
      )}
    </div>
  )
}

export default App
