import { useState } from 'react'
import { Building2, ArrowLeft, Palette, Upload, Trash2, Check, Plus, X, FolderOpen, Database, RefreshCw, Loader2, Pencil } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import DematAccounts from './DematAccounts'
import Documents from './Documents'

const DEFAULT_COLORS = {
  bg: '#0f172a', 'bg-card': '#1e293b', 'bg-card-hover': '#1e293b',
  border: '#334155', text: '#f1f5f9', 'text-muted': '#94a3b8',
  'text-secondary': '#64748b', primary: '#3b82f6', 'primary-hover': '#2563eb',
  green: '#22c55e', red: '#ef4444', amber: '#f59e0b',
  'sidebar-bg': '#1e293b', 'sidebar-border': '#334155',
  'hover-bg': '#ffffff', 'input-bg': '#334155', 'input-border': '#475569',
}

const COLOR_LABELS = {
  bg: 'Page Background', 'bg-card': 'Card Background', 'bg-card-hover': 'Card Hover',
  border: 'Borders', text: 'Primary Text', 'text-muted': 'Muted Text',
  'text-secondary': 'Secondary Text', primary: 'Primary Accent', 'primary-hover': 'Primary Hover',
  green: 'Green (Gain)', red: 'Red (Loss)', amber: 'Amber (Warning)',
  'sidebar-bg': 'Sidebar Background', 'sidebar-border': 'Sidebar Border',
  'hover-bg': 'Hover Overlay', 'input-bg': 'Input Background', 'input-border': 'Input Border',
}

const COLOR_GROUPS = [
  { label: 'Background', keys: ['bg', 'bg-card', 'bg-card-hover', 'input-bg'] },
  { label: 'Text', keys: ['text', 'text-muted', 'text-secondary'] },
  { label: 'Accents', keys: ['primary', 'primary-hover', 'green', 'red', 'amber'] },
  { label: 'Borders', keys: ['border', 'input-border', 'sidebar-border'] },
  { label: 'Sidebar', keys: ['sidebar-bg', 'sidebar-border'] },
  { label: 'Effects', keys: ['hover-bg'] },
]

function hexToRgba(hex, alpha) {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r},${g},${b},${alpha})`
}

const TABS = [
  { id: 'demat', label: 'Demat Accounts', icon: Building2, color: 'var(--primary)' },
  { id: 'documents', label: 'Documents', icon: FolderOpen, color: 'var(--amber)' },
  { id: 'themes', label: 'Themes', icon: Palette, color: 'var(--green)' },
  { id: 'data', label: 'Data', icon: Database, color: 'var(--blue)' },
]

function ThemeCard({ theme, active, onSelect, onDelete, onEdit }) {
  let colors
  try { colors = JSON.parse(theme.colorsJson) } catch { return null }
  return (
    <div
      onClick={() => onSelect(theme.id)}
      className={`relative p-4 rounded-xl border-2 transition-all cursor-pointer ${
        active
          ? 'border-blue-500 bg-blue-500/10'
          : 'border-[var(--border)] bg-[var(--bg-card)] hover:border-[var(--text-muted)]'
      }`}
    >
      {active && (
        <div className="absolute top-2 right-2 w-6 h-6 rounded-full bg-blue-500 flex items-center justify-center">
          <Check className="w-3.5 h-3.5 text-white" />
        </div>
      )}
      <div className="flex gap-2 mb-3">
        <div className="w-6 h-6 rounded" style={{ backgroundColor: colors.primary }} />
        <div className="w-6 h-6 rounded" style={{ backgroundColor: colors.green }} />
        <div className="w-6 h-6 rounded" style={{ backgroundColor: colors.red }} />
        <div className="w-6 h-6 rounded" style={{ backgroundColor: colors.amber }} />
      </div>
      <div className="flex gap-2 mb-3">
        <div className="w-6 h-6 rounded" style={{ backgroundColor: colors['sidebar-bg'] }} />
        <div className="w-6 h-6 rounded" style={{ backgroundColor: colors['bg-card'] }} />
        <div className="w-6 h-6 rounded" style={{ backgroundColor: colors['input-bg'] }} />
      </div>
      <div className="flex gap-1.5 mb-3">
        <div className="h-2 flex-1 rounded-full" style={{ backgroundColor: colors.text }} />
        <div className="h-2 flex-1 rounded-full" style={{ backgroundColor: colors['text-muted'] }} />
        <div className="h-2 flex-1 rounded-full" style={{ backgroundColor: colors['text-secondary'] }} />
      </div>
      <p className="text-sm font-medium text-[var(--text)]">{theme.name}</p>
      {theme.isSystem && (
        <span className="text-[10px] text-[var(--text-muted)]">System</span>
      )}
      {!theme.isSystem && (
        <div className="flex gap-2 mt-2">
          <button
            onClick={(e) => { e.stopPropagation(); onEdit(theme) }}
            className="text-xs text-blue-400 hover:text-blue-300 flex items-center gap-1"
          >
            <Pencil className="w-3 h-3" /> Edit
          </button>
          <button
            onClick={(e) => { e.stopPropagation(); onDelete(theme.id) }}
            className="text-xs text-red-400 hover:text-red-300 flex items-center gap-1"
          >
            <Trash2 className="w-3 h-3" /> Delete
          </button>
        </div>
      )}
    </div>
  )
}

export default function Profile() {
  const { user } = useAuth()
  const { themes, currentThemeId, setThemeById, setFallback, fallbackKey } = useTheme()
  const navigate = useNavigate()
  const [activeTab, setActiveTab] = useState('demat')
  const [showUpload, setShowUpload] = useState(false)
  const [editingTheme, setEditingTheme] = useState(null)
  const [themeName, setThemeName] = useState('')
  const [customColors, setCustomColors] = useState(DEFAULT_COLORS)
  const [uploadError, setUploadError] = useState('')

  const handleSelect = async (id) => {
    await setThemeById(id)
  }

  const handleDelete = async (id) => {
    try {
      await client.delete(`/themes/${id}`)
      window.location.reload()
    } catch {}
  }

  const handleEdit = (theme) => {
    let colors
    try { colors = JSON.parse(theme.colorsJson) } catch { colors = { ...DEFAULT_COLORS } }
    setEditingTheme(theme)
    setThemeName(theme.name)
    setCustomColors(colors)
    setShowUpload(true)
  }

  const handleUpload = async () => {
    setUploadError('')
    if (!themeName.trim()) { setUploadError('Enter a theme name'); return }
    const colors = { ...customColors }
    colors['hover-bg'] = hexToRgba(customColors['hover-bg'], 0.05)
    const payload = { name: themeName.trim(), colorsJson: JSON.stringify(colors) }
    try {
      if (editingTheme) {
        await client.put(`/themes/${editingTheme.id}`, payload)
      } else {
        await client.post('/themes', payload)
      }
      setShowUpload(false)
      setEditingTheme(null)
      setThemeName('')
      setCustomColors(DEFAULT_COLORS)
      window.location.reload()
    } catch {
      setUploadError(editingTheme ? 'Update failed' : 'Upload failed')
    }
  }

  const cancelUpload = () => {
    setShowUpload(false)
    setEditingTheme(null)
    setThemeName('')
    setCustomColors(DEFAULT_COLORS)
    setUploadError('')
  }

  const updateColor = (key, val) => {
    setCustomColors(prev => ({ ...prev, [key]: val }))
  }

  return (
    <div className="space-y-6">
      <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
        <button
          onClick={() => navigate('/dashboard')}
          className="flex items-center gap-1 text-sm text-[var(--text-muted)] hover:text-[var(--text)] transition mb-4"
        >
          <ArrowLeft className="w-4 h-4" /> Back to Dashboard
        </button>
        <div className="flex items-center gap-4">
          <div className="w-16 h-16 rounded-full bg-blue-500/20 flex items-center justify-center text-blue-400 font-bold text-2xl">
            {user?.name?.charAt(0).toUpperCase()}
          </div>
          <div>
            <h1 className="text-2xl font-bold text-[var(--text)]">{user?.name}</h1>
            <p className="text-[var(--text-muted)]">{user?.email}</p>
          </div>
        </div>
      </div>

      <div className="flex gap-1 bg-[var(--bg-card)] rounded-xl p-1.5 border border-[var(--border)]">
        {TABS.map((tab) => {
          const Icon = tab.icon
          return (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium transition ${
                activeTab === tab.id
                  ? 'bg-blue-500/20 text-blue-400'
                  : 'text-[var(--text-muted)] hover:text-[var(--text)] hover:bg-[var(--hover-bg)]'
              }`}
            >
              <Icon className="w-4 h-4" style={{color: tab.color}} />
              {tab.label}
            </button>
          )
        })}
      </div>

      <div>
        {activeTab === 'demat' && <DematAccounts />}
        {activeTab === 'documents' && <Documents />}
        {activeTab === 'themes' && (
          <div className="space-y-4">
            {showUpload ? (
              <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="font-semibold text-[var(--text)]">{editingTheme ? 'Edit' : 'Create'} Custom Theme</h3>
                  <button onClick={cancelUpload} className="text-[var(--text-muted)] hover:text-[var(--text)]">
                    <X className="w-5 h-5" />
                  </button>
                </div>

                <div>
                  <label className="block text-sm text-[var(--text-muted)] mb-1">Theme Name</label>
                  <input
                    type="text" value={themeName}
                    onChange={(e) => setThemeName(e.target.value)}
                    className="w-full input-bg input-border border rounded-lg px-3 py-2 text-sm text-[var(--text)] focus:outline-none focus:border-blue-500"
                    placeholder="My Theme"
                  />
                </div>

                <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                  {COLOR_GROUPS.map((group) => (
                    <div key={group.label} className="space-y-2">
                      <p className="text-xs font-semibold text-[var(--text-muted)] uppercase tracking-wider">{group.label}</p>
                      {group.keys.map((key) => (
                        <div key={key} className="flex items-center gap-2">
                          <input
                            type="color"
                            value={customColors[key]}
                            onChange={(e) => updateColor(key, e.target.value)}
                            className="w-8 h-8 rounded cursor-pointer border border-[var(--border)] bg-transparent shrink-0"
                          />
                          <div className="flex-1 min-w-0">
                            <p className="text-xs text-[var(--text)] truncate">{COLOR_LABELS[key]}</p>
                            <p className="text-[10px] text-[var(--text-muted)] font-mono">{customColors[key]}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  ))}
                </div>

                <div className="flex items-center gap-4 p-4 rounded-lg border border-[var(--border)]" style={{ backgroundColor: customColors['bg-card'], color: customColors.text }}>
                  <div className="w-10 h-10 rounded-lg shrink-0" style={{ backgroundColor: customColors.primary }} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium">{themeName || 'Preview'}</p>
                    <p className="text-xs" style={{ color: customColors['text-muted'] }}>Sample text with colors</p>
                    <div className="flex gap-2 mt-1">
                      <span className="text-xs px-2 py-0.5 rounded" style={{ backgroundColor: customColors.green, color: '#fff' }}>+2.5%</span>
                      <span className="text-xs px-2 py-0.5 rounded" style={{ backgroundColor: customColors.red, color: '#fff' }}>-1.2%</span>
                    </div>
                  </div>
                </div>

                {uploadError && (
                  <p className="text-sm text-red-400">{uploadError}</p>
                )}

                <div className="flex gap-2">
                  <button
                    onClick={handleUpload}
                    className="flex items-center gap-2 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600 transition text-sm"
                  >
                    <Upload className="w-4 h-4" /> {editingTheme ? 'Update Theme' : 'Save Theme'}
                  </button>
                  <button
                    onClick={() => setCustomColors(DEFAULT_COLORS)}
                    className="px-3 py-2 border border-[var(--border)] rounded-lg text-sm text-[var(--text-muted)] hover:text-[var(--text)] transition"
                  >
                    Reset
                  </button>
                  {editingTheme && (
                    <button
                      onClick={cancelUpload}
                      className="px-3 py-2 border border-[var(--border)] rounded-lg text-sm text-[var(--text-muted)] hover:text-[var(--text)] transition"
                    >
                      Cancel
                    </button>
                  )}
                </div>
              </div>
            ) : (
              <div className="flex items-center justify-between">
                <h3 className="font-semibold text-[var(--text)]">Available Themes</h3>
                <div className="flex gap-2">
                  <button
                    onClick={() => { setFallback('dark'); setShowUpload(false) }}
                    className={`px-3 py-1.5 rounded-lg text-xs transition ${
                      !currentThemeId && fallbackKey === 'dark'
                        ? 'bg-blue-500/20 text-blue-400'
                        : 'bg-[var(--bg-card)] border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)]'
                    }`}
                  >
                    Dark
                  </button>
                  <button
                    onClick={() => { setFallback('light'); setShowUpload(false) }}
                    className={`px-3 py-1.5 rounded-lg text-xs transition ${
                      !currentThemeId && fallbackKey === 'light'
                        ? 'bg-blue-500/20 text-blue-400'
                        : 'bg-[var(--bg-card)] border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)]'
                    }`}
                  >
                    Light
                  </button>
                  <button
                    onClick={() => { cancelUpload(); setShowUpload(true) }}
                    className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs bg-blue-500 text-white hover:bg-blue-600 transition"
                  >
                    <Plus className="w-3 h-3" /> Custom
                  </button>
                </div>
              </div>
            )}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-3">
              {themes.length === 0 ? (
                <p className="col-span-full text-center text-sm text-[var(--text-muted)] py-8">No themes available.</p>
              ) : (
                themes.map((t) => (
                  <ThemeCard
                    key={t.id}
                    theme={t}
                    active={currentThemeId === t.id}
                    onSelect={handleSelect}
                    onDelete={handleDelete}
                    onEdit={handleEdit}
                  />
                ))
              )}
            </div>
          </div>
        )}
        {activeTab === 'data' && <DataRefresh />}
      </div>
    </div>
  )
}

function DataRefresh() {
  const [syncing, setSyncing] = useState(false)
  const [done, setDone] = useState(false)
  const [error, setError] = useState('')

  const handleRefresh = async () => {
    setSyncing(true)
    setError('')
    setDone(false)
    try {
      await client.post('/symbols/refresh')
      setDone(true)
    } catch (e) {
      setError('Failed to refresh symbols')
    } finally {
      setSyncing(false)
    }
  }

  return (
    <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="font-semibold text-[var(--text)]">Market Data</h3>
          <p className="text-sm text-[var(--text-muted)] mt-1">Refresh NSE equities and mutual fund symbol list</p>
        </div>
        <button
          onClick={handleRefresh}
          disabled={syncing}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-500 hover:bg-blue-600 disabled:bg-slate-600 disabled:cursor-not-allowed transition text-sm font-medium"
        >
          {syncing ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
          {syncing ? 'Refreshing...' : 'Refresh Symbols'}
        </button>
      </div>
      {done && <p className="text-sm text-green-400">Symbols updated successfully</p>}
      {error && <p className="text-sm text-red-400">{error}</p>}
    </div>
  )
}
