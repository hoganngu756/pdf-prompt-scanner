import { ShieldCheck, History, ScanSearch, Settings } from 'lucide-react';

interface HeaderProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
}

export default function Header({ activeTab, setActiveTab }: HeaderProps) {
  return (
    <header>
      <div className="header-left">
        <ShieldCheck size={24} color="#3b82f6" />
        <h1 className="main-logo">PDF Prompt Scanner</h1>
      </div>
      <div className="header-tabs">
        <button 
          className={`tab-btn ${activeTab === 'scan' ? 'active' : ''}`}
          onClick={() => setActiveTab('scan')}
        >
          <ScanSearch size={16} />
          Scanner
        </button>
        <button 
          className={`tab-btn ${activeTab === 'history' ? 'active' : ''}`}
          onClick={() => setActiveTab('history')}
        >
          <History size={16} />
          History
        </button>
        <button 
          className={`tab-btn ${activeTab === 'rules' ? 'active' : ''}`}
          onClick={() => setActiveTab('rules')}
        >
          <Settings size={16} />
          Rules
        </button>
      </div>
    </header>
  );
}
