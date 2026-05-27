import { useState, useEffect, useRef } from 'react'
import { Building2, Plus, Trash2, Pencil, Star, Download, ChevronDown, X, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import client from '../api/client'
import { ConfirmDialog } from '../components/ui/Modal'
import { useFeature } from '../context/FeatureFlagContext'
import { PageSkeleton } from '../components/ui'

const BROKERS = [
  'Zerodha', 'Groww', 'Angel One', 'ICICI Direct', 'HDFC Securities',
  'Sharekhan', '5Paisa', 'Upstox', 'Motilal Oswal', 'Kotak Securities',
  'Axis Direct', 'IIFL', 'Edelweiss', 'SBI Securities', 'Paytm Money',
]

const ACCOUNT_TYPES = ['Equity', 'Commodity', 'Derivatives', 'Mutual Funds']

const getBrokerLogo = (name) => {
  if (!name) return null
  const fname = name.replace(/[& ]/g, '_').replace(/[^a-zA-Z0-9_-]/g, '')
  return `/logos/brokers/${fname}.png`
}

export default function DematAccounts() {
  const upstoxEnabled = useFeature('upstox-import')
  const zerodhaEnabled = useFeature('zerodha-import')
  const hasBrokers = upstoxEnabled || zerodhaEnabled
  const [accounts, setAccounts] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({
    brokerName: '',
    accountNumber: '',
    accountType: 'Equity',
    description: '',
    isDefault: false,
  })
  const [confirmDialog, setConfirmDialog] = useState({ open: false, title: '', description: '', onConfirm: null })
  const [importOpen, setImportOpen] = useState(false)
  const [brokerStatus, setBrokerStatus] = useState({})
  const [syncingBroker, setSyncingBroker] = useState(null)
  const [brokerDropdownOpen, setBrokerDropdownOpen] = useState(false)
  const importRef = useRef(null)
  const brokerDropdownRef = useRef(null)

  useEffect(() => { fetchAccounts() }, [])

  // Fetch broker connection statuses
  useEffect(() => {
    if (!hasBrokers) return
    const fetches = []
    if (upstoxEnabled) fetches.push(client.get('/brokers/upstox/status').then(r => ['upstox', r.data]).catch(() => ['upstox', { connected: false }]))
    if (zerodhaEnabled) fetches.push(client.get('/brokers/zerodha/status').then(r => ['zerodha', r.data]).catch(() => ['zerodha', { connected: false }]))
    Promise.all(fetches).then(results => {
      const map = {}
      results.forEach(([key, val]) => { map[key] = val })
      setBrokerStatus(map)
    })
  }, [hasBrokers, upstoxEnabled, zerodhaEnabled])

  // Handle broker OAuth callbacks from URL params
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const code = params.get('code')
    if (code && upstoxEnabled) {
      window.history.replaceState({}, '', window.location.pathname)
      setSyncingBroker('upstox')
      client.post('/brokers/upstox/callback', { code })
        .then(res => {
          if (res.data?.success) {
            toast.success('Upstox connected', { description: res.data.brokerUserName || '' })
            setBrokerStatus(prev => ({ ...prev, upstox: { connected: true, status: 'ACTIVE', brokerUserName: res.data.brokerUserName } }))
            fetchAccounts()
          } else {
            toast.error('Upstox connection failed', { description: res.data?.message })
          }
        })
        .catch(err => toast.error('Connection failed', { description: err.message }))
        .finally(() => setSyncingBroker(null))
      return
    }
    const requestToken = params.get('request_token')
    const kiteStatus = params.get('status')
    if (requestToken && kiteStatus === 'success' && zerodhaEnabled) {
      window.history.replaceState({}, '', window.location.pathname)
      setSyncingBroker('zerodha')
      client.post('/brokers/zerodha/callback', { request_token: requestToken })
        .then(res => {
          if (res.data?.success) {
            toast.success('Zerodha connected', { description: res.data.brokerUserName || '' })
            setBrokerStatus(prev => ({ ...prev, zerodha: { connected: true, status: 'ACTIVE', brokerUserName: res.data.brokerUserName } }))
            fetchAccounts()
          } else {
            toast.error('Zerodha connection failed', { description: res.data?.message })
          }
        })
        .catch(err => toast.error('Connection failed', { description: err.message }))
        .finally(() => setSyncingBroker(null))
    }
  }, [])

  // Close dropdowns on outside click
  useEffect(() => {
    if (!importOpen && !brokerDropdownOpen) return
    const handleClick = (e) => {
      if (importOpen && importRef.current && !importRef.current.contains(e.target)) setImportOpen(false)
      if (brokerDropdownOpen && brokerDropdownRef.current && !brokerDropdownRef.current.contains(e.target)) setBrokerDropdownOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [importOpen, brokerDropdownOpen])

  async function fetchAccounts() {
    try {
      const { data } = await client.get('/demat-accounts')
      setAccounts(data || [])
    } catch (err) {
      console.error('Failed to load demat accounts', err)
    } finally {
      setLoading(false)
    }
  }

  function resetForm() {
    setForm({ brokerName: '', accountNumber: '', accountType: 'Equity', description: '', isDefault: false })
  }

  function openAdd() {
    resetForm()
    setEditingId(null)
    setShowForm(true)
  }

  function openEdit(acc) {
    setForm({
      brokerName: acc.brokerName,
      accountNumber: '',
      accountType: acc.accountType,
      description: acc.description || '',
      isDefault: acc.isDefault,
    })
    setEditingId(acc.id)
    setShowForm(true)
  }

  async function handleSubmit(e) {
    e.preventDefault()
    const payload = {
      brokerName: form.brokerName,
      accountNumber: form.accountNumber || null,
      accountType: form.accountType,
      description: form.description || null,
      isDefault: form.isDefault,
    }
    try {
      if (editingId) {
        await client.put(`/demat-accounts/${editingId}`, payload)
      } else {
        await client.post('/demat-accounts', payload)
      }
      setShowForm(false)
      resetForm()
      setEditingId(null)
      await fetchAccounts()
    } catch (err) {
      console.error('Failed to save demat account', err)
    }
  }

  function handleDelete(id) {
    setConfirmDialog({
      open: true,
      title: 'Delete this demat account?',
      description: 'This action cannot be undone.',
      onConfirm: async () => {
        try {
          await client.delete(`/demat-accounts/${id}`)
          await fetchAccounts()
        } catch (err) {
          console.error('Failed to delete demat account', err)
        }
      },
    })
  }

  const handleBrokerConnect = async (broker) => {
    try {
      const { data } = await client.get(`/brokers/${broker}/auth-url`)
      window.location.href = data.url
    } catch (err) {
      toast.error('Failed to get auth URL', { description: err.message })
    }
  }

  const handleBrokerSync = async (broker) => {
    setSyncingBroker(broker)
    try {
      const { data } = await client.post(`/brokers/${broker}/sync`)
      if (data.success) {
        toast.success(`${broker === 'upstox' ? 'Upstox' : 'Zerodha'} synced`, { description: data.message })
        setBrokerStatus(prev => ({ ...prev, [broker]: { ...prev[broker], lastSyncedAt: new Date().toISOString() } }))
        fetchAccounts()
      } else {
        toast.error('Sync failed', { description: data.message })
        if (data.message?.includes('expired') || data.message?.includes('reconnect')) {
          setBrokerStatus(prev => ({ ...prev, [broker]: { ...prev[broker], status: 'TOKEN_EXPIRED' } }))
        }
      }
    } catch (err) {
      toast.error('Sync failed', { description: err.message })
    } finally {
      setSyncingBroker(null)
    }
  }

  const handleBrokerDisconnect = async (broker) => {
    const name = broker === 'upstox' ? 'Upstox' : 'Zerodha'
    setConfirmDialog({
      open: true,
      title: `Disconnect ${name}?`,
      description: 'Your imported holdings will remain.',
      onConfirm: async () => {
        try {
          await client.post(`/brokers/${broker}/disconnect`)
          setBrokerStatus(prev => ({ ...prev, [broker]: { connected: false } }))
          toast.success(`${name} disconnected`)
        } catch (err) {
          toast.error('Failed to disconnect', { description: err.message })
        }
      },
    })
  }

  const renderBrokerRow = (brokerId, brokerName) => {
    const s = brokerStatus[brokerId] || {}
    const isConnected = s.connected && s.status === 'ACTIVE'
    const isExpired = s.connected && s.status === 'TOKEN_EXPIRED'
    const isSyncing = syncingBroker === brokerId
    return (
      <div key={brokerId} className="px-4 py-3 hover:bg-[var(--hover-bg)] transition">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="relative">
              <img src={getBrokerLogo(brokerName)} alt={brokerName} className="w-7 h-7 rounded-md object-contain bg-white p-0.5"
                onError={(e) => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex' }} />
              <div className="w-7 h-7 rounded-md bg-[var(--hover-bg)] items-center justify-center text-[var(--text-muted)] hidden">
                <Building2 className="w-4 h-4" />
              </div>
              <div className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2 border-[var(--bg-card)] ${isConnected ? 'bg-green-400' : isExpired ? 'bg-amber-400' : 'bg-[var(--text-secondary)]'}`} />
            </div>
            <div>
              <p className="text-sm font-medium">{brokerName}</p>
              <p className="text-xs text-[var(--text-muted)]">
                {isConnected && s.brokerUserName ? s.brokerUserName : isExpired ? 'Session expired' : 'Not connected'}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-1.5">
            {isConnected && (
              <>
                <button onClick={() => { setImportOpen(false); handleBrokerSync(brokerId) }} disabled={isSyncing}
                  className="px-2.5 py-1 rounded-md text-xs bg-blue-500/20 text-blue-400 hover:bg-blue-500/30 transition disabled:opacity-50">
                  {isSyncing ? <Loader2 className="w-3 h-3 animate-spin" /> : 'Sync'}
                </button>
                <button onClick={() => { setImportOpen(false); handleBrokerDisconnect(brokerId) }}
                  className="p-1 rounded-md text-[var(--text-muted)] hover:text-red-400 hover:bg-red-500/10 transition">
                  <X className="w-3.5 h-3.5" />
                </button>
              </>
            )}
            {(isExpired || !s.connected) && (
              <button onClick={() => { setImportOpen(false); handleBrokerConnect(brokerId) }}
                className="px-2.5 py-1 rounded-md text-xs bg-blue-500 text-white hover:bg-blue-600 transition">
                {isExpired ? 'Reconnect' : 'Connect'}
              </button>
            )}
          </div>
        </div>
      </div>
    )
  }

  if (loading) return <PageSkeleton />

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Demat Accounts</h1>
          <p className="text-[var(--text-muted)] text-sm mt-1">Manage your brokerage accounts</p>
        </div>
        <div className="flex items-center gap-2">
          {hasBrokers && (
            <div className="relative" ref={importRef}>
              <button
                onClick={() => setImportOpen(!importOpen)}
                className="px-4 py-2 rounded-lg flex items-center gap-2 transition bg-[var(--bg-card)] border border-[var(--border)] hover:bg-[var(--hover-bg)]"
              >
                <Download className="w-4 h-4" /> Import
                <ChevronDown className={`w-3.5 h-3.5 transition-transform ${importOpen ? 'rotate-180' : ''}`} />
              </button>
              {importOpen && (
                <div className="absolute right-0 top-full mt-2 w-72 bg-[var(--bg-card)] border border-[var(--border)] rounded-xl shadow-xl z-50 overflow-hidden">
                  <div className="px-4 py-2.5 border-b border-[var(--border)]">
                    <p className="text-xs font-medium text-[var(--text-muted)] uppercase tracking-wide">Import from Broker</p>
                  </div>
                  {upstoxEnabled && renderBrokerRow('upstox', 'Upstox')}
                  {upstoxEnabled && zerodhaEnabled && <div className="border-b border-[var(--border)]" />}
                  {zerodhaEnabled && renderBrokerRow('zerodha', 'Zerodha')}
                </div>
              )}
            </div>
          )}
          <button
            onClick={() => { if (!showForm) openAdd(); else { setShowForm(false); resetForm(); } }}
            className={`px-4 py-2 rounded-lg flex items-center gap-2 transition ${showForm ? 'bg-[var(--input-bg)] hover:bg-[var(--hover-bg)]' : 'bg-blue-500 hover:bg-blue-600'}`}
          >
            <Plus className="w-4 h-4" /> {showForm ? 'Cancel' : 'Add Account'}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <p className="text-[var(--text-muted)] text-sm">Total Accounts</p>
          <p className="text-2xl font-bold mt-1">{accounts.length}</p>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <p className="text-[var(--text-muted)] text-sm">Active Brokers</p>
          <p className="text-2xl font-bold mt-1">{new Set(accounts.map(a => a.brokerName)).size}</p>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <p className="text-[var(--text-muted)] text-sm">Default Account</p>
          <p className="text-lg font-bold mt-1 text-blue-400">
            {accounts.find(a => a.isDefault)?.brokerName || 'Not set'}
          </p>
        </div>
      </div>

      {showForm && (
        <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
          <h3 className="text-lg font-semibold mb-4">{editingId ? 'Edit' : 'Add New'} Demat Account</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-[var(--text-muted)] mb-1">Broker</label>
                <div className="relative" ref={brokerDropdownRef}>
                  <button
                    type="button"
                    onClick={() => setBrokerDropdownOpen(!brokerDropdownOpen)}
                    className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5 text-left flex items-center justify-between"
                  >
                    {form.brokerName ? (
                      <span className="flex items-center gap-2">
                        {getBrokerLogo(form.brokerName) ? (
                          <img src={getBrokerLogo(form.brokerName)} alt="" className="w-5 h-5 rounded object-contain bg-white p-px flex-shrink-0"
                            onError={(e) => { e.target.style.display = 'none' }} />
                        ) : (
                          <Building2 className="w-4 h-4 text-[var(--text-muted)] flex-shrink-0" />
                        )}
                        <span>{form.brokerName}</span>
                      </span>
                    ) : (
                      <span className="text-[var(--text-muted)]">Select broker...</span>
                    )}
                    <ChevronDown className={`w-4 h-4 text-[var(--text-muted)] flex-shrink-0 transition-transform ${brokerDropdownOpen ? 'rotate-180' : ''}`} />
                  </button>
                  {brokerDropdownOpen && (
                    <div className="absolute left-0 right-0 top-full mt-1 bg-[var(--bg-card)] border border-[var(--border)] rounded-lg shadow-xl z-50 max-h-60 overflow-y-auto">
                      {BROKERS.map((b) => {
                        const logo = getBrokerLogo(b)
                        const isSelected = form.brokerName === b
                        return (
                          <button type="button" key={b}
                            onClick={() => { setForm({ ...form, brokerName: b }); setBrokerDropdownOpen(false) }}
                            className={`w-full px-3 py-2.5 flex items-center gap-2 hover:bg-[var(--hover-bg)] transition text-left text-sm ${isSelected ? 'bg-blue-500/10 text-blue-400' : ''}`}>
                            {logo ? (
                              <img src={logo} alt="" className="w-5 h-5 rounded object-contain bg-white p-px flex-shrink-0"
                                onError={(e) => { e.target.style.display = 'none' }} />
                            ) : (
                              <Building2 className="w-4 h-4 text-[var(--text-muted)] flex-shrink-0" />
                            )}
                            <span>{b}</span>
                          </button>
                        )
                      })}
                    </div>
                  )}
                </div>
              </div>
              <div>
                <label className="block text-sm text-[var(--text-muted)] mb-1">Account Number <span className="text-[var(--text-secondary)]">(optional)</span></label>
                <input
                  type="text"
                  value={form.accountNumber}
                  onChange={(e) => setForm({ ...form, accountNumber: e.target.value })}
                  className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5"
                  placeholder={editingId ? 'Leave blank to keep unchanged' : 'Full demat account number'}
                />
              </div>
              <div>
                <label className="block text-sm text-[var(--text-muted)] mb-1">Account Type</label>
                <select
                  value={form.accountType}
                  onChange={(e) => setForm({ ...form, accountType: e.target.value })}
                  className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5"
                >
                  {ACCOUNT_TYPES.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>
              <div className="flex items-end pb-2">
                <label className="flex items-center gap-3 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={form.isDefault}
                    onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
                    className="w-4 h-4 rounded bg-[var(--input-bg)] border-[var(--border)]"
                  />
                  <span className="text-sm text-[var(--text)]">Set as default account</span>
                </label>
              </div>
            </div>
            <div>
              <label className="block text-sm text-[var(--text-muted)] mb-1">Notes <span className="text-[var(--text-secondary)]">(optional)</span></label>
              <textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5"
                placeholder="Any notes about this account..."
                rows={2}
              />
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => { setShowForm(false); resetForm(); }}
                className="px-4 py-2 bg-[var(--input-bg)] rounded-lg hover:bg-[var(--hover-bg)] transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-6 py-2 bg-blue-500 hover:bg-blue-600 rounded-lg font-medium transition"
              >
                {editingId ? 'Update' : 'Add'} Account
              </button>
            </div>
          </form>
        </div>
      )}

      {accounts.length > 0 ? (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {accounts.map((acc) => {
            const logo = getBrokerLogo(acc.brokerName)
            return (
              <div key={acc.id} className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)] relative group">
                {acc.isDefault && (
                  <div className="absolute top-3 right-3 flex items-center gap-1 text-xs bg-blue-500/20 text-blue-400 px-2 py-1 rounded-full">
                    <Star className="w-3 h-3" /> Default
                  </div>
                )}
                <div className="flex items-start gap-4">
                  <div className="w-12 h-12 rounded-xl bg-white/10 flex items-center justify-center flex-shrink-0 overflow-hidden">
                    {logo ? (
                      <img src={logo} alt={acc.brokerName} className="w-10 h-10 rounded-lg object-contain bg-white p-1"
                        onError={(e) => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex' }} />
                    ) : null}
                    <div className={`w-12 h-12 rounded-xl bg-blue-500/20 items-center justify-center ${logo ? 'hidden' : 'flex'}`}>
                      <Building2 className="w-6 h-6 text-blue-400" />
                    </div>
                  </div>
                  <div className="flex-1 min-w-0">
                    <h3 className="text-lg font-semibold">{acc.brokerName}</h3>
                    <div className="flex flex-wrap gap-2 mt-2">
                      <span className="text-xs bg-[var(--input-bg)] text-[var(--text)] px-2 py-1 rounded">{acc.accountType}</span>
                      {acc.accountNumber && (
                        <span className="text-xs bg-[var(--input-bg)] text-[var(--text)] px-2 py-1 rounded font-mono">
                          {acc.accountNumber}
                        </span>
                      )}
                    </div>
                    {acc.description && (
                      <p className="text-sm text-[var(--text-muted)] mt-2">{acc.description}</p>
                    )}
                  </div>
                </div>
                <div className="flex gap-2 mt-4 pt-3 border-t border-[var(--border)]">
                  <button
                    onClick={() => openEdit(acc)}
                    className="flex items-center gap-1 text-sm text-[var(--text-muted)] hover:text-blue-400 transition"
                  >
                    <Pencil className="w-3.5 h-3.5" /> Edit
                  </button>
                  <button
                    onClick={() => handleDelete(acc.id)}
                    className="flex items-center gap-1 text-sm text-[var(--text-secondary)] hover:text-red-400 transition"
                  >
                    <Trash2 className="w-3.5 h-3.5" /> Delete
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        <div className="bg-[var(--bg-card)] rounded-xl p-12 border border-[var(--border)] text-center">
          <Building2 className="w-12 h-12 text-[var(--text-secondary)] mx-auto mb-4" />
          <p className="text-[var(--text-muted)] mb-2">No demat accounts added yet</p>
          <p className="text-[var(--text-secondary)] text-sm">Click "Add Account" to link your brokerage accounts</p>
        </div>
      )}

      <ConfirmDialog
        open={confirmDialog.open}
        onClose={() => setConfirmDialog(d => ({ ...d, open: false }))}
        onConfirm={confirmDialog.onConfirm}
        title={confirmDialog.title}
        description={confirmDialog.description}
      />
    </div>
  )
}
