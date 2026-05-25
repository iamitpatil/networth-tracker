import { useState, useEffect } from 'react'
import { Building2, ArrowLeft, Palette, Upload, Trash2, Check, Plus, X, FolderOpen, Database, RefreshCw, Loader2, Pencil, ShieldCheck, Wallet } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { useTheme } from '../context/ThemeContext'
import { useNavigate } from 'react-router-dom'
import client from '../api/client'
import DematAccounts from './DematAccounts'
import Documents from './Documents'
import AccountsHub from '../components/AccountsHub'

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
  { id: 'accounts', label: 'My Accounts', icon: Wallet, color: 'var(--primary)' },
  { id: '2fa', label: 'Two-Factor Auth', icon: ShieldCheck, color: 'var(--green)' },
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
  const [activeTab, setActiveTab] = useState('accounts')
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
        {activeTab === 'accounts' && <AccountsHub />}
        {activeTab === '2fa' && <TwoFactorAuth />}
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

function TwoFactorAuth() {
  const { user } = useAuth()
  const [enabled, setEnabled] = useState(user?.twoFactorEnabled || false)
  const [step, setStep] = useState('idle') // idle | setup | verify | disable
  const [qrUrl, setQrUrl] = useState('')
  const [secret, setSecret] = useState('')
  const [code, setCode] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const startSetup = async () => {
    setError('')
    setLoading(true)
    try {
      const { data } = await client.post('/auth/2fa/setup')
      setQrUrl(data.qrCodeUrl)
      setSecret(data.secret)
      setStep('verify')
    } catch (e) {
      setError(e.response?.data?.message || 'Failed to start 2FA setup')
    } finally {
      setLoading(false)
    }
  }

  const verifySetup = async () => {
    setError('')
    if (code.length !== 6) { setError('Enter a 6-digit code'); return }
    setLoading(true)
    try {
      await client.post('/auth/2fa/verify-setup', { code })
      setEnabled(true)
      setStep('idle')
      setCode('')
      setQrUrl('')
      setSecret('')
      // Update stored user
      const stored = JSON.parse(localStorage.getItem('user') || '{}')
      stored.twoFactorEnabled = true
      localStorage.setItem('user', JSON.stringify(stored))
    } catch (e) {
      setError(e.response?.data?.message || 'Invalid code. Try again.')
    } finally {
      setLoading(false)
    }
  }

  const disable2FA = async () => {
    setError('')
    if (code.length !== 6) { setError('Enter your current 6-digit code to disable 2FA'); return }
    setLoading(true)
    try {
      await client.post('/auth/2fa/disable', { code })
      setEnabled(false)
      setStep('idle')
      setCode('')
      const stored = JSON.parse(localStorage.getItem('user') || '{}')
      stored.twoFactorEnabled = false
      localStorage.setItem('user', JSON.stringify(stored))
    } catch (e) {
      setError(e.response?.data?.message || 'Invalid code. Cannot disable 2FA.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] space-y-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className={`w-10 h-10 rounded-lg flex items-center justify-center ${enabled ? 'bg-green-500/20' : 'bg-[var(--hover-bg)]'}`}>
            <ShieldCheck className={`w-5 h-5 ${enabled ? 'text-green-400' : 'text-[var(--text-muted)]'}`} />
          </div>
          <div>
            <h3 className="font-semibold text-[var(--text)]">Two-Factor Authentication</h3>
            <p className="text-sm text-[var(--text-muted)]">
              {enabled ? 'Enabled — your account is protected with TOTP' : 'Add an extra layer of security to your account'}
            </p>
          </div>
        </div>
        <span className={`text-xs font-medium px-2.5 py-1 rounded-full ${enabled ? 'bg-green-500/20 text-green-400' : 'bg-[var(--hover-bg)] text-[var(--text-muted)]'}`}>
          {enabled ? 'Enabled' : 'Disabled'}
        </span>
      </div>

      {step === 'idle' && !enabled && (
        <button
          onClick={startSetup}
          disabled={loading}
          className="flex items-center gap-2 px-4 py-2.5 bg-blue-500 hover:bg-blue-600 text-white rounded-lg text-sm font-medium transition disabled:opacity-60"
        >
          {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <ShieldCheck className="w-4 h-4" />}
          Enable Two-Factor Authentication
        </button>
      )}

      {step === 'idle' && enabled && (
        <button
          onClick={() => { setStep('disable'); setCode(''); setError('') }}
          className="flex items-center gap-2 px-4 py-2.5 border border-red-500/30 text-red-400 hover:bg-red-500/10 rounded-lg text-sm font-medium transition"
        >
          Disable Two-Factor Authentication
        </button>
      )}

      {step === 'verify' && (
        <div className="space-y-4">
          <div className="p-4 bg-[var(--bg)] rounded-lg border border-[var(--border)] space-y-3">
            <p className="text-sm font-medium text-[var(--text)]">1. Scan this QR code with your authenticator app</p>
            <p className="text-xs text-[var(--text-muted)]">(Google Authenticator, Authy, 1Password, etc.)</p>
            <div className="flex justify-center py-3">
              <img
                src={`https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(qrUrl)}`}
                alt="2FA QR Code"
                className="w-48 h-48 rounded-lg border border-[var(--border)]"
              />
            </div>
            <div className="text-center">
              <p className="text-xs text-[var(--text-muted)] mb-1">Or enter this secret manually:</p>
              <code className="text-xs font-mono bg-[var(--bg-card)] px-3 py-1.5 rounded border border-[var(--border)] text-[var(--text)] select-all">{secret}</code>
            </div>
          </div>

          <div>
            <p className="text-sm font-medium text-[var(--text)] mb-2">2. Enter the 6-digit code from your app</p>
            <input
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              className="w-full bg-[var(--bg)] border border-[var(--border)] rounded-lg px-4 py-3 text-sm text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-blue-500/50 focus:border-blue-500 transition tracking-[0.3em] text-center font-mono"
              placeholder="000000"
            />
          </div>

          {error && <p className="text-sm text-red-400">{error}</p>}

          <div className="flex gap-2">
            <button
              onClick={verifySetup}
              disabled={loading || code.length !== 6}
              className="flex items-center gap-2 px-4 py-2.5 bg-green-500 hover:bg-green-600 text-white rounded-lg text-sm font-medium transition disabled:opacity-60"
            >
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
              Verify & Enable
            </button>
            <button
              onClick={() => { setStep('idle'); setCode(''); setError(''); setQrUrl(''); setSecret('') }}
              className="px-4 py-2.5 border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)] rounded-lg text-sm transition"
            >
              Cancel
            </button>
          </div>
        </div>
      )}

      {step === 'disable' && (
        <div className="space-y-4">
          <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-lg">
            <p className="text-sm text-red-400">Enter your current authenticator code to disable 2FA. This will remove the extra security layer from your account.</p>
          </div>
          <input
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
            className="w-full bg-[var(--bg)] border border-[var(--border)] rounded-lg px-4 py-3 text-sm text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-red-500/50 focus:border-red-500 transition tracking-[0.3em] text-center font-mono"
            placeholder="000000"
          />

          {error && <p className="text-sm text-red-400">{error}</p>}

          <div className="flex gap-2">
            <button
              onClick={disable2FA}
              disabled={loading || code.length !== 6}
              className="flex items-center gap-2 px-4 py-2.5 bg-red-500 hover:bg-red-600 text-white rounded-lg text-sm font-medium transition disabled:opacity-60"
            >
              {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Trash2 className="w-4 h-4" />}
              Disable 2FA
            </button>
            <button
              onClick={() => { setStep('idle'); setCode(''); setError('') }}
              className="px-4 py-2.5 border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)] rounded-lg text-sm transition"
            >
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function DataRefresh() {
  const [backfillStatus, setBackfillStatus] = useState(null)
  const [polling, setPolling] = useState(false)
  const [days, setDays] = useState(365)
  const [error, setError] = useState('')

  // Poll backfill status
  useEffect(() => {
    if (!polling) return
    const interval = setInterval(async () => {
      try {
        const { data } = await client.get('/market/backfill/status')
        setBackfillStatus(data)
        if (!data.running) setPolling(false)
      } catch {}
    }, 2000)
    return () => clearInterval(interval)
  }, [polling])

  // Check status on mount
  useEffect(() => {
    client.get('/market/backfill/status')
      .then(({ data }) => {
        setBackfillStatus(data)
        if (data.running) setPolling(true)
      })
      .catch(() => {})
  }, [])

  const handleStart = async () => {
    setError('')
    // Optimistically show running state immediately
    setBackfillStatus({
      running: true,
      currentStep: 'symbols',
      currentStepMessage: 'Starting...',
      steps: ['symbols', 'equities', 'mutual_funds', 'nps'],
      completedSteps: [],
      startedAt: new Date().toISOString(),
    })
    setPolling(true)
    try {
      const { data } = await client.post(`/market/backfill?days=${days}`)
      if (data.status === 'already_running') {
        setError('A backfill job is already running')
      }
    } catch (e) {
      setError(e.response?.data?.message || 'Failed to start backfill')
      setPolling(false)
    }
  }

  const handleCancel = async () => {
    try {
      await client.post('/market/backfill/cancel')
    } catch {}
  }

  const isRunning = backfillStatus?.running
  const steps = [
    { key: 'symbols', label: 'Symbol Lists', desc: 'NSE equities, AMFI mutual funds, NSE bonds' },
    { key: 'equities', label: 'Equity Prices', desc: 'Historical OHLCV data for stocks' },
    { key: 'mutual_funds', label: 'MF NAV History', desc: 'Daily NAV data for mutual funds' },
    { key: 'nps', label: 'NPS NAV', desc: 'NPS scheme NAVs from npsnav.in' },
  ]
  const completedSteps = backfillStatus?.completedSteps || []
  const currentStep = backfillStatus?.currentStep
  const stepResults = backfillStatus?.stepResults || {}
  const stepErrors = backfillStatus?.stepErrors || {}
  const currentStepProgress = backfillStatus?.currentStepProgress

  return (
    <div className="space-y-4">
      {/* Full Backfill */}
      <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] space-y-5">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="font-semibold text-[var(--text)]">Data Backfill</h3>
            <p className="text-sm text-[var(--text-muted)] mt-1">
              Refresh all symbols, backfill price history for equities & mutual funds, update NPS NAVs
            </p>
          </div>
          {!isRunning ? (
            <div className="flex items-center gap-2">
              <select value={days} onChange={(e) => setDays(Number(e.target.value))}
                className="bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2 text-sm text-[var(--text)]">
                <option value={30}>30 days</option>
                <option value={90}>90 days</option>
                <option value={180}>6 months</option>
                <option value={365}>1 year</option>
                <option value={730}>2 years</option>
                <option value={1095}>3 years</option>
              </select>
              <button onClick={handleStart} disabled={polling}
                className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-500 hover:bg-blue-600 disabled:bg-[var(--input-bg)] disabled:cursor-not-allowed transition text-sm font-medium">
                {polling ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                {polling ? 'Starting...' : 'Start Backfill'}
              </button>
            </div>
          ) : (
            <button onClick={handleCancel}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-red-500/20 text-red-400 hover:bg-red-500/30 transition text-sm font-medium">
              <X className="w-4 h-4" /> Cancel
            </button>
          )}
        </div>

        {/* Progress Steps */}
        {backfillStatus && (isRunning || backfillStatus.completedAt) && (
          <div className="space-y-2">
            {steps.map((step) => {
              const isCompleted = completedSteps.some(s => s.startsWith(step.key))
              const hasError = stepErrors[step.key]
              const isCurrent = currentStep === step.key && isRunning
              return (
                <div key={step.key} className={`flex items-center gap-3 p-3 rounded-lg border transition ${
                  isCurrent ? 'border-blue-500/30 bg-blue-500/5' :
                  hasError ? 'border-red-500/20 bg-red-500/5' :
                  isCompleted ? 'border-green-500/20 bg-green-500/5' :
                  'border-[var(--border)] bg-[var(--bg)]'}`}>
                  <div className="flex-shrink-0">
                    {isCurrent ? <Loader2 className="w-5 h-5 text-blue-400 animate-spin" /> :
                     hasError ? <div className="w-5 h-5 rounded-full bg-red-500/20 flex items-center justify-center"><X className="w-3 h-3 text-red-400" /></div> :
                     isCompleted ? <div className="w-5 h-5 rounded-full bg-green-500/20 flex items-center justify-center"><Check className="w-3 h-3 text-green-400" /></div> :
                     <div className="w-5 h-5 rounded-full bg-[var(--input-bg)]" />}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className={`text-sm font-medium ${isCurrent ? 'text-blue-400' : isCompleted ? 'text-green-400' : hasError ? 'text-red-400' : 'text-[var(--text-muted)]'}`}>
                      {step.label}
                    </p>
                    <p className="text-xs text-[var(--text-muted)] truncate">
                      {hasError || stepResults[step.key] || (isCurrent && currentStepProgress) || (isCurrent && backfillStatus?.currentStepMessage) || step.desc}
                    </p>
                  </div>
                </div>
              )
            })}
          </div>
        )}

        {backfillStatus?.completedAt && !isRunning && (
          <p className="text-xs text-[var(--text-muted)]">
            Completed at {new Date(backfillStatus.completedAt).toLocaleString('en-IN')}
            {backfillStatus.equityRecords != null && ` · ${backfillStatus.equityRecords} equity records`}
            {backfillStatus.mfRecords != null && ` · ${backfillStatus.mfRecords} MF records`}
          </p>
        )}

        {error && <p className="text-sm text-red-400">{error}</p>}
      </div>
    </div>
  )
}
