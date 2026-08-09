import { useState, useEffect } from 'react'
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
  ChevronLeft,
  ChevronRight,
} from 'lucide-react'
import FloatingChat from './FloatingChat'
import AppLogo from './AppLogo'
import Tooltip from './ui/Tooltip'

/** Remembers the rail choice across sessions — re-collapsing on every load would be a nuisance. */
const RAIL_KEY = 'sidebarMinimized'

export default function Layout() {
  const { user, logout } = useAuth()
  const { fallbackKey, toggleTheme } = useTheme()
  const { view, setView, pendingCount, refreshKey } = useFamilyView()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [collapsed, setCollapsed] = useState({})
  const [minimized, setMinimized] = useState(() => localStorage.getItem(RAIL_KEY) === 'true')
  const navigate = useNavigate()

  useEffect(() => {
    localStorage.setItem(RAIL_KEY, String(minimized))
  }, [minimized])

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

  // The rail only applies from lg: up. Below that the sidebar is an off-canvas drawer, where a
  // 64px strip of icons over a dimmed page would be worse than the full menu it replaces.
  const rail = minimized && !sidebarOpen

  const linkClass = ({ isActive }) =>
    `flex items-center rounded-lg transition-all duration-150 ${
      rail ? 'lg:justify-center lg:px-0 lg:py-2 gap-3 px-3 py-2.5' : 'gap-3 px-3 py-2.5'
    } ${
      isActive
        ? 'bg-blue-500/10 text-blue-400 font-medium shadow-sm border-l-2 border-blue-500 ml-0'
        : 'text-theme-muted hover:bg-[var(--hover-bg)] hover:text-theme border-l-2 border-transparent ml-0'
    }`

  /**
   * Wraps a rail control in a tooltip so the icon is still identifiable when its label is hidden.
   * Expanded, the label is already on screen, so a tooltip would just be noise.
   */
  const withLabel = (label, children) =>
    rail
      ? <Tooltip content={label} side="right" className="w-full [&>*]:w-full">{children}</Tooltip>
      : children

  return (
    <div className="flex min-h-screen bg-theme">
      {sidebarOpen && (
        <div className="fixed inset-0 bg-black/50 z-40 lg:hidden" onClick={() => setSidebarOpen(false)} />
      )}

      <aside
        className={`fixed inset-y-0 left-0 z-50 sidebar-bg sidebar-border border-r sidebar-shadow transform transition-all duration-200 flex flex-col w-64 overflow-visible ${
          rail ? 'lg:w-16' : 'lg:w-64'
        } ${sidebarOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}`}
      >
        <div className={`flex items-center h-16 sidebar-border border-b bg-gradient-to-r from-blue-500/5 to-transparent ${
          rail ? 'lg:justify-center lg:px-0 justify-between px-5' : 'justify-between px-5'
        }`}>
          {/* The rail keeps the blue app mark rather than dropping to a bare strip of nav icons:
              it holds the brand and anchors the top of the rail. The wordmark is what goes, because
              that is what needs the width. */}
          <span className={rail ? 'lg:hidden' : ''}>
            <AppLogo variant="compact" />
          </span>
          {rail && (
            <span className="hidden lg:flex items-center gap-0.5">
              <AppLogo variant="icon" />
              {/* Expanding sits next to the mark rather than on the partition: at 64px wide the
                  rail's edge is a small target to go hunting for, and the top of the panel is
                  where the eye already is. */}
              <button
                onClick={() => setMinimized(false)}
                aria-label="Expand sidebar"
                aria-expanded="false"
                title="Expand sidebar"
                className="flex items-center justify-center w-4 h-8 rounded text-theme-muted
                           hover:text-blue-400 hover:bg-[var(--hover-bg)] transition-colors duration-150"
              >
                <ChevronRight className="w-3.5 h-3.5" />
              </button>
            </span>
          )}

          <button className="lg:hidden text-theme-muted hover:text-theme transition" onClick={() => setSidebarOpen(false)}>
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Collapsing sits on the partition itself, straddling the border between the panel and the
            page, which is where this affordance is conventionally looked for and where it reads as
            acting on the panel rather than as another nav item. Only shown while expanded — the rail
            has its own control beside the app mark. Hidden below lg:, where the sidebar is an
            off-canvas drawer with nothing to collapse into. */}
        {!rail && (
          <button
            onClick={() => setMinimized(true)}
            aria-label="Minimize sidebar"
            aria-expanded="true"
            title="Minimize sidebar"
            className="hidden lg:flex absolute top-1/2 -right-3 -translate-y-1/2 z-10 w-6 h-6 items-center justify-center
                       rounded-full sidebar-border border bg-[var(--bg-card)] text-theme-muted shadow-md
                       hover:text-blue-400 hover:border-blue-500/50 transition-colors duration-150"
          >
            <ChevronLeft className="w-3.5 h-3.5" />
          </button>
        )}

        <nav className={`space-y-5 overflow-y-auto overflow-x-hidden flex-1 min-h-0 p-4 ${rail ? 'lg:px-2' : ''}`}>
          {sections.map((section) => (
            <div key={section.label}>
              {/* Collapsed, a section heading has no room and its collapse toggle has no purpose
                  — the items are already reduced to icons. A divider keeps the grouping legible. */}
              {rail ? (
                <div className="hidden lg:block h-px mx-2 mb-2 bg-[var(--border)]" aria-hidden="true" />
              ) : null}
              <button
                onClick={() => toggleSection(section.label)}
                className={`items-center justify-between w-full text-xs font-semibold text-theme-secondary uppercase tracking-wider mb-1 px-3 py-1.5 hover:text-theme transition rounded-lg hover:bg-[var(--hover-bg)] group ${
                  rail ? 'flex lg:hidden' : 'flex'
                }`}
              >
                <span className="flex items-center gap-2">
                  <span className="w-1 h-1 rounded-full bg-blue-500/0 group-hover:bg-blue-500 transition-all duration-200" />
                  {section.label}
                </span>
                <ChevronDown className={`w-3.5 h-3.5 transition-transform ${collapsed[section.label] ? '-rotate-90' : ''}`} />
              </button>
              {/* A section collapsed by the user stays hidden when expanded, but in the rail every
                  item must remain reachable — there is no heading left to un-collapse it with. */}
              {(!collapsed[section.label] || rail) && (
                <div className={`space-y-1 ${collapsed[section.label] ? 'hidden lg:block' : ''}`}>
                  {section.items.map((item) => (
                    <div key={item.to}>
                      {withLabel(item.label, (
                        <NavLink
                          to={item.to}
                          onClick={() => setSidebarOpen(false)}
                          className={linkClass}
                        >
                          <span className="w-7 h-7 rounded-lg flex items-center justify-center shrink-0 bg-[var(--bg-card)]/50">{item.icon}</span>
                          <span className={rail ? 'lg:hidden' : ''}>{item.label}</span>
                        </NavLink>
                      ))}
                    </div>
                    ))}
                  </div>
                )}
              </div>
            ))}
        </nav>

        <div className={`sidebar-border border-t sidebar-bg bg-gradient-to-t from-blue-500/[0.02] to-transparent flex-shrink-0 p-4 ${rail ? 'lg:px-2' : ''}`}>
          {/* Self/Family is a two-button segmented control expanded; in the rail it becomes one
              button that toggles, because two 64px-wide buttons do not fit side by side. */}
          <div className={`items-center gap-1 mb-2 px-1 ${rail ? 'flex lg:hidden' : 'flex'}`}>
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
          {rail && (
            <div className="hidden lg:block mb-2">
              {withLabel(view === 'family' ? 'Family view — switch to self' : 'Self view — switch to family', (
                <button
                  onClick={() => setView(view === 'family' ? 'self' : 'family')}
                  aria-label={view === 'family' ? 'Switch to self view' : 'Switch to family view'}
                  className={`relative flex items-center justify-center w-full py-2 rounded-lg transition ${
                    view === 'family' ? 'bg-blue-500/20 text-blue-400' : 'text-[var(--text-muted)] hover:bg-[var(--hover-bg)]'
                  }`}
                >
                  <Users className="w-5 h-5" />
                  {pendingCount > 0 && (
                    <span className="absolute top-0.5 right-1 bg-red-500 text-white text-[10px] min-w-[16px] h-4 rounded-full flex items-center justify-center px-1">
                      {pendingCount}
                    </span>
                  )}
                </button>
              ))}
            </div>
          )}

          {withLabel(fallbackKey === 'dark' ? 'Light Mode' : 'Dark Mode', (
            <button
              onClick={toggleTheme}
              className={`flex items-center w-full text-theme-muted hover:bg-[var(--hover-bg)] hover:text-theme rounded-lg transition-all duration-150 group ${
                rail ? 'lg:justify-center lg:px-0 lg:py-2 gap-3 px-3 py-2' : 'gap-3 px-3 py-2'
              }`}
            >
              <span className="group-hover:scale-110 transition-transform duration-150">{fallbackKey === 'dark' ? <Sun className="w-5 h-5" style={{color:'var(--amber)'}} /> : <Moon className="w-5 h-5" style={{color:'var(--primary)'}} />}</span>
              <span className={rail ? 'lg:hidden' : ''}>{fallbackKey === 'dark' ? 'Light Mode' : 'Dark Mode'}</span>
            </button>
          ))}

          {withLabel(user?.name ? `${user.name} — open profile` : 'Profile', (
            <div
              onClick={() => navigate('/profile')}
              className={`flex items-center mb-2 cursor-pointer hover:bg-[var(--hover-bg)] rounded-lg transition-all duration-150 group ${
                rail ? 'lg:justify-center lg:px-0 gap-3 px-3 py-2' : 'gap-3 px-3 py-2'
              }`}
            >
              <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center text-white font-medium text-sm shrink-0 group-hover:scale-105 transition-transform duration-150">
                {user?.name?.charAt(0).toUpperCase()}
              </div>
              <div className={`flex-1 min-w-0 ${rail ? 'lg:hidden' : ''}`}>
                <p className="text-sm font-medium truncate text-theme">{user?.name}</p>
                <p className="text-xs text-theme-secondary truncate">{user?.email}</p>
              </div>
            </div>
          ))}

          {withLabel('Logout', (
            <button
              onClick={handleLogout}
              className={`flex items-center w-full text-theme-muted hover:text-red-400 hover:bg-[var(--hover-bg)] rounded-lg transition-all duration-150 group ${
                rail ? 'lg:justify-center lg:px-0 lg:py-2 gap-3 px-3 py-2' : 'gap-3 px-3 py-2'
              }`}
            >
              <LogOut className="w-5 h-5 group-hover:scale-110 transition-transform duration-150" style={{color:'var(--red)'}} />
              <span className={rail ? 'lg:hidden' : ''}>Logout</span>
            </button>
          ))}
        </div>
      </aside>

      <main className={`flex-1 min-w-0 h-screen overflow-y-auto transition-all duration-200 ${rail ? 'lg:ml-16' : 'lg:ml-64'}`}>
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
