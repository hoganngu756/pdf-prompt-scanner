import { useState, useEffect } from 'react'
import Header from './components/Header'
import UploadSection from './components/UploadSection'
import ResultsDashboard from './components/ResultsDashboard'
import HistoryTable from './components/HistoryTable'
import './index.css'

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
      const res = await fetch('http://localhost:8080/api/history')
      const data = await res.json()
      setHistory(data)
    } catch (err) {
      console.error('Failed to fetch history:', err)
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
    if (!file) return

    setLoading(true)
    setResults(null)

    const formData = new FormData()
    formData.append('file', file)
    formData.append('useLLM', useLLM)
    formData.append('useHeuristics', useHeuristics)

    try {
      const response = await fetch('http://localhost:8080/api/scan', {
        method: 'POST',
        body: formData,
      })
      const data = await response.json()
      setResults(data)
    } catch (error) {
      console.error('Error during scan:', error)
      setResults({ error: 'Failed to connect to the server.' })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app-container">
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
