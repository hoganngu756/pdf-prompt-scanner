import { ShieldCheck } from 'lucide-react';

interface HeaderProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
}

const TABS = [
  { id: 'scan', label: 'Scanner' },
  { id: 'history', label: 'History' },
  { id: 'rules', label: 'Rules' },
];

export default function Header({ activeTab, setActiveTab }: HeaderProps) {
  return (
    <header>
      <div className="header-left">
        <ShieldCheck size={18} className="logo-mark" />
        <h1 className="main-logo">PDF Prompt Scanner</h1>
      </div>
      {/* Icons dropped from the nav: three short words need no glyphs, and
          removing them lets the active-tab rule carry the state on its own. */}
      <nav className="header-tabs">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            className={`tab-btn ${activeTab === tab.id ? 'active' : ''}`}
            aria-current={activeTab === tab.id ? 'page' : undefined}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </nav>
    </header>
  );
}
