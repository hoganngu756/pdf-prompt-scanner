import { ShieldCheck, History, ScanSearch } from 'lucide-react';

export default function Header({ activeTab, setActiveTab }) {
  return (
    <header>
      <div className="header-left">
        <ShieldCheck size={36} color="var(--accent-color)" />
        <h1>PDF Prompt Scanner</h1>
      </div>
      <div className="header-tabs">
        <button 
          className={`tab-btn ${activeTab === 'scan' ? 'active' : ''}`}
          onClick={() => setActiveTab('scan')}
        >
          <ScanSearch size={18} />
          Scanner
        </button>
        <button 
          className={`tab-btn ${activeTab === 'history' ? 'active' : ''}`}
          onClick={() => setActiveTab('history')}
        >
          <History size={18} />
          History
        </button>
      </div>
    </header>
  );
}
