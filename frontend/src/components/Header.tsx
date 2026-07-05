import { ShieldCheck, History, ScanSearch, Settings } from 'lucide-react';

interface HeaderProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
}

export default function Header({ activeTab, setActiveTab }: HeaderProps) {
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
        <button 
          className={`tab-btn ${activeTab === 'rules' ? 'active' : ''}`}
          onClick={() => setActiveTab('rules')}
        >
          <Settings size={18} />
          Rules
        </button>
      </div>
    </header>
  );
}
