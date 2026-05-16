import { useState } from 'react'
import { Outlet, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { useFamilyView } from '../context/FamilyViewContext'
import {
  TrendingUp,
  LayoutDashboard,
  Briefcase,
  ArrowLeftRight,
  BarChart3,
  Landmark,
  Target,
  Upload,
  LogOut,
  Menu,
  X,
  Wallet,
  Sun,
  Moon,
  Bot,
  ChevronDown,
  Users,
} from 'lucide-react'
import FloatingChat from './FloatingChat'

export default function Layout() {
  const { user, logout } = useAuth()
  const { fallbackKey, toggleTheme } = useTheme()
  const { view, setView, pendingCount, refreshKey } = useFamilyView()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [collapsed, setCollapsed] = useState({})
  const navigate = useNavigate()

  const toggleSection = (label) => {
    setCollapsed((prev) => ({ ...prev, [label]: !prev[label] }))
  }

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const sections = [
    { label: 'Overview', items: [
      { to: '/dashboard', icon: <LayoutDashboard className="w-5 h-5" style={{color:'var(--primary)'}} />, label: 'Dashboard' },
      { to: '/net-worth', icon: <Wallet className="w-5 h-5" style={{color:'var(--green)'}} />, label: 'Net Worth' },
      { to: '/family', icon: <Users className="w-5 h-5" style={{color:'var(--amber)'}} />, label: 'Family' },
    ]},
    { label: 'Portfolio', items: [
      { to: '/holdings', icon: <Briefcase className="w-5 h-5" style={{color:'var(--primary)'}} />, label: 'Holdings' },
      { to: '/transactions', icon: <ArrowLeftRight className="w-5 h-5" style={{color:'var(--amber)'}} />, label: 'Transactions' },
      { to: '/bank-accounts', icon: <Landmark className="w-5 h-5" style={{color:'var(--green)'}} />, label: 'Bank Accounts' },
    ]},
    { label: 'Income', items: [
      { to: '/salary', icon: <Wallet className="w-5 h-5" style={{color:'var(--green)'}} />, label: 'Salary' },
    ]},
    { label: 'Analysis', items: [
      { to: '/analytics', icon: <BarChart3 className="w-5 h-5" style={{color:'var(--primary)'}} />, label: 'Analytics' },
      { to: '/tax', icon: <Landmark className="w-5 h-5" style={{color:'var(--red)'}} />, label: 'Tax' },
      { to: '/ai-chat', icon: <Bot className="w-5 h-5" style={{color:'var(--green)'}} />, label: 'AI Chat' },
    ]},
    { label: 'Planning', items: [
      { to: '/goals', icon: <Target className="w-5 h-5" style={{color:'var(--green)'}} />, label: 'Goals' },
      { to: '/liabilities', icon: <TrendingUp className="w-5 h-5" style={{color:'var(--amber)'}} />, label: 'Liabilities' },
      { to: '/import', icon: <Upload className="w-5 h-5" style={{color:'var(--primary)'}} />, label: 'Import' },
    ]},
  ]

  const linkClass = ({ isActive }) =>
    `flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all duration-150 ${
      isActive
        ? 'bg-blue-500/10 text-blue-400 font-medium shadow-sm border-l-2 border-blue-500 ml-0'
        : 'text-theme-muted hover:bg-[var(--hover-bg)] hover:text-theme border-l-2 border-transparent ml-0'
    }`

  return (
    <div className="flex min-h-screen bg-theme">
      {sidebarOpen && (
        <div className="fixed inset-0 bg-black/50 z-40 lg:hidden" onClick={() => setSidebarOpen(false)} />
      )}

      <aside className={`fixed inset-y-0 left-0 z-50 w-64 sidebar-bg sidebar-border border-r sidebar-shadow transform transition-transform duration-200 ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}`}>
        <div className="flex items-center justify-between h-16 px-6 sidebar-border border-b bg-gradient-to-r from-blue-500/5 to-transparent">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center">
              <TrendingUp className="w-4 h-4 text-white" />
            </div>
            <span className="font-bold text-lg text-theme">NW Tracker</span>
          </div>
          <button className="lg:hidden text-theme-muted hover:text-theme transition" onClick={() => setSidebarOpen(false)}>
            <X className="w-5 h-5" />
          </button>
        </div>

        <nav className="p-4 space-y-5 overflow-y-auto h-[calc(100vh-16rem)]">
          {sections.map((section) => (
            <div key={section.label}>
              <button
                onClick={() => toggleSection(section.label)}
                className="flex items-center justify-between w-full text-xs font-semibold text-theme-secondary uppercase tracking-wider mb-1 px-3 py-1.5 hover:text-theme transition rounded-lg hover:bg-[var(--hover-bg)] group"
              >
                <span className="flex items-center gap-2">
                  <span className="w-1 h-1 rounded-full bg-blue-500/0 group-hover:bg-blue-500 transition-all duration-200" />
                  {section.label}
                </span>
                <ChevronDown className={`w-3.5 h-3.5 transition-transform ${collapsed[section.label] ? '-rotate-90' : ''}`} />
              </button>
              {!collapsed[section.label] && (
                <div className="space-y-1">
                  {section.items.map((item) => (
                    <NavLink
                      key={item.to}
                      to={item.to}
                      onClick={() => setSidebarOpen(false)}
                      className={linkClass}
                    >
                      <span className="w-7 h-7 rounded-lg flex items-center justify-center shrink-0 bg-[var(--bg-card)]/50">{item.icon}</span>
                      {item.label}
                    </NavLink>
                    ))}
                  </div>
                )}
              </div>
            ))}
        </nav>

        <div className="absolute bottom-0 left-0 right-0 p-4 sidebar-border border-t sidebar-bg bg-gradient-to-t from-blue-500/[0.02] to-transparent">
          <div className="flex items-center gap-1 mb-2 px-1">
            <button
              onClick={() => setView('self')}
              className={`flex-1 text-xs px-3 py-1.5 rounded-lg font-medium transition ${
                view === 'self' ? 'bg-blue-500/20 text-blue-400' : 'text-[var(--text-muted)] hover:bg-[var(--hover-bg)]'
              }`}
            >
              Self
            </button>
            <button
              onClick={() => setView('family')}
              className={`flex-1 text-xs px-3 py-1.5 rounded-lg font-medium transition flex items-center justify-center gap-1 ${
                view === 'family' ? 'bg-blue-500/20 text-blue-400' : 'text-[var(--text-muted)] hover:bg-[var(--hover-bg)]'
              }`}
            >
              Family
              {pendingCount > 0 && (
                <span className="bg-red-500 text-white text-[10px] min-w-[16px] h-4 rounded-full flex items-center justify-center px-1">
                  {pendingCount}
                </span>
              )}
            </button>
          </div>
          <button
            onClick={toggleTheme}
            className="flex items-center gap-3 w-full px-3 py-2 text-theme-muted hover:bg-[var(--hover-bg)] hover:text-theme rounded-lg transition-all duration-150 group"
          >
            <span className="group-hover:scale-110 transition-transform duration-150">{fallbackKey === 'dark' ? <Sun className="w-5 h-5" style={{color:'var(--amber)'}} /> : <Moon className="w-5 h-5" style={{color:'var(--primary)'}} />}</span>
            {fallbackKey === 'dark' ? 'Light Mode' : 'Dark Mode'}
          </button>
          <div
            onClick={() => navigate('/profile')}
            className="flex items-center gap-3 mb-2 px-3 cursor-pointer hover:bg-[var(--hover-bg)] rounded-lg py-2 transition-all duration-150 group"
          >
            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center text-white font-medium text-sm shrink-0 group-hover:scale-105 transition-transform duration-150">
              {user?.name?.charAt(0).toUpperCase()}
            </div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-medium truncate text-theme">{user?.name}</p>
              <p className="text-xs text-theme-secondary truncate">{user?.email}</p>
            </div>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center gap-3 w-full px-3 py-2 text-theme-muted hover:text-red-400 hover:bg-[var(--hover-bg)] rounded-lg transition-all duration-150 group"
          >
            <LogOut className="w-5 h-5 group-hover:scale-110 transition-transform duration-150" style={{color:'var(--red)'}} />
            Logout
          </button>
        </div>
      </aside>

      <main className="flex-1 min-w-0 lg:ml-64 h-screen overflow-y-auto">
        <header className="h-16 bg-[var(--bg-card)]/50 sidebar-border border-b flex items-center px-4 lg:hidden">
          <button onClick={() => setSidebarOpen(true)} className="p-2">
            <Menu className="w-5 h-5" />
          </button>
          <span className="ml-3 font-semibold text-theme">NW Tracker</span>
        </header>
        <div className="p-6">
          <Outlet key={refreshKey} />
        </div>
      </main>
      <FloatingChat />
    </div>
  )
}
