import { useState, useEffect, useRef } from 'react'
import client from '../api/client'
import { toast } from 'sonner'
import {
  Landmark, Building2, CreditCard, Shield, PiggyBank, Briefcase,
  Plus, Trash2, Pencil, X, ChevronDown, ChevronUp, Eye, EyeOff,
  Upload, FileText, Download, Loader2, Paperclip
} from 'lucide-react'
import { ConfirmDialog } from './ui/Modal'
import { useFeature } from '../context/FeatureFlagContext'

const BROKER_DOMAINS = {
  'Upstox': 'upstox.com', 'Zerodha': 'zerodha.com',
}
const getBrokerLogo = (name) => {
  const domain = BROKER_DOMAINS[name]
  return domain ? `https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://${domain}&size=128` : null
}

const NPS_CRAS = [
  { value: 'PROTEAN', label: 'Protean (NSDL)', logo: '/logos/cra/protean.jpg' },
  { value: 'KFINTECH', label: 'KFintech (Karvy)', logo: '/logos/cra/kfintech.svg' },
  { value: 'CAMS', label: 'CAMS', logo: '/logos/cra/cams.svg' },
]

const TABS = [
  { id: 'bank', label: 'Bank', icon: Landmark, color: 'blue' },
  { id: 'demat', label: 'Demat', icon: Building2, color: 'indigo' },
  { id: 'cc', label: 'Credit Cards', icon: CreditCard, color: 'red' },
  { id: 'nps', label: 'NPS/PRAN', icon: Shield, color: 'green' },
  { id: 'ppf', label: 'PPF', icon: PiggyBank, color: 'amber' },
  { id: 'epf', label: 'EPF', icon: Briefcase, color: 'purple' },
]

const fmt = (v) => v != null ? `₹${Number(v).toLocaleString('en-IN')}` : '—'

import { useReferenceData } from '../hooks/useReferenceData'

export default function AccountsHub() {
  const [activeTab, setActiveTab] = useState('bank')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({})

  const [confirmDialog, setConfirmDialog] = useState({ open: false, title: '', description: '', onConfirm: null })
  const { options: bankRef } = useReferenceData('BANK')
  const { options: brokerRef } = useReferenceData('BROKER')
  const { options: cardIssuerRef } = useReferenceData('CARD_ISSUER')

  // Broker import state
  const upstoxEnabled = useFeature('upstox-import')
  const zerodhaEnabled = useFeature('zerodha-import')
  const hasBrokers = upstoxEnabled || zerodhaEnabled
  const [importOpen, setImportOpen] = useState(false)
  const [brokerStatus, setBrokerStatus] = useState({})
  const [syncingBroker, setSyncingBroker] = useState(null)
  const [refreshingNps, setRefreshingNps] = useState(false)
  const importRef = useRef(null)

  const getLogo = (category, name) => {
    const refs = category === 'BANK' ? bankRef : category === 'BROKER' ? brokerRef : category === 'CARD_ISSUER' ? cardIssuerRef : []
    return refs.find(r => r.value === name)?.metadata?.logo || null
  }

  useEffect(() => { loadAll() }, [])

  // Fetch broker statuses
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

  // Handle broker OAuth callbacks
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
            loadAll()
          } else toast.error('Upstox connection failed', { description: res.data?.message })
        })
        .catch(err => toast.error('Connection failed', { description: err.message }))
        .finally(() => setSyncingBroker(null))
      return
    }
    const requestToken = params.get('request_token')
    if (requestToken && params.get('status') === 'success' && zerodhaEnabled) {
      window.history.replaceState({}, '', window.location.pathname)
      setSyncingBroker('zerodha')
      client.post('/brokers/zerodha/callback', { request_token: requestToken })
        .then(res => {
          if (res.data?.success) {
            toast.success('Zerodha connected', { description: res.data.brokerUserName || '' })
            setBrokerStatus(prev => ({ ...prev, zerodha: { connected: true, status: 'ACTIVE', brokerUserName: res.data.brokerUserName } }))
            loadAll()
          } else toast.error('Zerodha connection failed', { description: res.data?.message })
        })
        .catch(err => toast.error('Connection failed', { description: err.message }))
        .finally(() => setSyncingBroker(null))
    }
  }, [])

  // Close import dropdown on outside click
  useEffect(() => {
    if (!importOpen) return
    const handleClick = (e) => { if (importRef.current && !importRef.current.contains(e.target)) setImportOpen(false) }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [importOpen])

  const loadAll = async () => {
    try {
      const { data: d } = await client.get('/accounts')
      setData(d)
    } catch {} finally { setLoading(false) }
  }

  const resetForm = () => { setForm({}); setShowForm(false); setEditingId(null) }

  const handleBrokerConnect = async (broker) => {
    try {
      const { data } = await client.get(`/brokers/${broker}/auth-url`)
      window.location.href = data.url
    } catch (err) { toast.error('Failed to get auth URL', { description: err.message }) }
  }

  const handleBrokerSync = async (broker) => {
    setSyncingBroker(broker)
    try {
      const { data } = await client.post(`/brokers/${broker}/sync`)
      if (data.success) {
        toast.success(`${broker === 'upstox' ? 'Upstox' : 'Zerodha'} synced`, { description: data.message })
        setBrokerStatus(prev => ({ ...prev, [broker]: { ...prev[broker], lastSyncedAt: new Date().toISOString() } }))
        loadAll()
      } else {
        toast.error('Sync failed', { description: data.message })
        if (data.message?.includes('expired') || data.message?.includes('reconnect'))
          setBrokerStatus(prev => ({ ...prev, [broker]: { ...prev[broker], status: 'TOKEN_EXPIRED' } }))
      }
    } catch (err) { toast.error('Sync failed', { description: err.message }) }
    finally { setSyncingBroker(null) }
  }

  const handleBrokerDisconnect = (broker) => {
    const name = broker === 'upstox' ? 'Upstox' : 'Zerodha'
    setConfirmDialog({
      open: true, title: `Disconnect ${name}?`, description: 'Your imported holdings will remain.',
      onConfirm: async () => {
        try {
          await client.post(`/brokers/${broker}/disconnect`)
          setBrokerStatus(prev => ({ ...prev, [broker]: { connected: false } }))
          toast.success(`${name} disconnected`)
        } catch (err) { toast.error('Failed to disconnect', { description: err.message }) }
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

  const handleCreate = async (endpoint, body) => {
    try {
      await client.post(`/accounts/${endpoint}`, body)
      toast.success('Account added')
      resetForm()
      loadAll()
    } catch (err) { toast.error('Failed to add', { description: err.response?.data?.message || err.message }) }
  }

  const handleUpdate = async (endpoint, id, body) => {
    try {
      await client.put(`/accounts/${endpoint}/${id}`, body)
      toast.success('Account updated')
      resetForm()
      loadAll()
    } catch (err) { toast.error('Failed to update', { description: err.response?.data?.message || err.message }) }
  }

  const handleDelete = (endpoint, id, label) => {
    setConfirmDialog({
      open: true,
      title: `Delete ${label}?`,
      description: 'This action cannot be undone.',
      onConfirm: async () => {
        try {
          await client.delete(`/accounts/${endpoint}/${id}`)
          toast.success(`${label} deleted`)
          loadAll()
        } catch (err) { toast.error('Failed to delete', { description: err.response?.data?.message || err.message }) }
      },
    })
  }

  // For bank/demat we use existing endpoints
  const handleBankCreate = async (body) => {
    try { await client.post('/bank-accounts', body); toast.success('Bank account added'); resetForm(); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }
  const handleBankUpdate = async (id, body) => {
    try { await client.put(`/bank-accounts/${id}`, body); toast.success('Updated'); resetForm(); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }
  const handleBankDelete = (id) => {
    setConfirmDialog({
      open: true,
      title: 'Delete bank account?',
      description: 'This action cannot be undone.',
      onConfirm: async () => {
        try { await client.delete(`/bank-accounts/${id}`); toast.success('Deleted'); loadAll() }
        catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
      },
    })
  }
  const handleDematCreate = async (body) => {
    try { await client.post('/demat-accounts', body); toast.success('Demat account added'); resetForm(); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }
  const handleDematUpdate = async (id, body) => {
    try { await client.put(`/demat-accounts/${id}`, body); toast.success('Updated'); resetForm(); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }
  const handleDematDelete = (id) => {
    setConfirmDialog({
      open: true,
      title: 'Delete demat account?',
      description: 'This action cannot be undone.',
      onConfirm: async () => {
        try { await client.delete(`/demat-accounts/${id}`); toast.success('Deleted'); loadAll() }
        catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
      },
    })
  }

  if (loading) return <div className="flex justify-center py-12 text-[var(--text-muted)]">Loading accounts...</div>

  const counts = data?.counts || {}

  return (
    <div className="space-y-4">
      {/* Summary bar */}
      <div className="flex items-center gap-4 flex-wrap">
        {TABS.map(tab => {
          const Icon = tab.icon
          const count = counts[tab.id === 'cc' ? 'creditCards' : tab.id === 'bank' ? 'bankAccounts' : tab.id === 'demat' ? 'dematAccounts' : `${tab.id}Accounts`] || 0
          const isActive = activeTab === tab.id
          return (
            <button key={tab.id} onClick={() => { setActiveTab(tab.id); resetForm() }}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-sm font-medium transition ${
                isActive ? `bg-${tab.color}-500/15 text-${tab.color}-400 ring-1 ring-${tab.color}-500/30`
                : 'bg-[var(--bg)] border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)]'}`}>
              <Icon className="w-4 h-4" />
              {tab.label}
              {count > 0 && <span className="text-xs opacity-70">({count})</span>}
            </button>
          )
        })}
        <span className="text-xs text-[var(--text-muted)] ml-auto">{counts.total || 0} total accounts</span>
      </div>

      {/* Content */}
      <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)]">
        <div className="p-4 flex items-center justify-between border-b border-[var(--border)]">
          <h3 className="font-semibold text-[var(--text)]">{TABS.find(t => t.id === activeTab)?.label} Accounts</h3>
          <div className="flex items-center gap-2">
            {activeTab === 'nps' && (
              <button onClick={async () => {
                  setRefreshingNps(true)
                  try {
                    const { data } = await client.post('/accounts/nps/refresh-nav')
                    if (data.updated > 0) {
                      toast.success(`NPS NAV updated`, { description: `${data.updated} account(s) refreshed` })
                      loadAll()
                    } else {
                      toast.info('No NPS accounts with scheme codes to refresh')
                    }
                  } catch (err) { toast.error('NAV refresh failed', { description: err.message }) }
                  finally { setRefreshingNps(false) }
                }}
                disabled={refreshingNps}
                className="px-3 py-1.5 rounded-lg text-xs font-medium transition inline-flex items-center gap-1.5 bg-green-500/20 text-green-400 hover:bg-green-500/30 disabled:opacity-50">
                {refreshingNps ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Download className="w-3.5 h-3.5" />}
                Refresh NAV
              </button>
            )}
            {activeTab === 'demat' && hasBrokers && (
              <div className="relative" ref={importRef}>
                <button onClick={() => setImportOpen(!importOpen)}
                  className="px-3 py-1.5 rounded-lg text-xs font-medium transition inline-flex items-center gap-1.5 bg-[var(--bg)] border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)]">
                  <Download className="w-3.5 h-3.5" /> Import
                  <ChevronDown className={`w-3 h-3 transition-transform ${importOpen ? 'rotate-180' : ''}`} />
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
            <button onClick={() => { setShowForm(!showForm); setEditingId(null); setForm({}) }}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition inline-flex items-center gap-1.5 ${
                showForm ? 'bg-[var(--bg)] border border-[var(--border)] text-[var(--text-muted)]' : 'bg-blue-500 hover:bg-blue-600 text-white'}`}>
              {showForm ? <><X className="w-3.5 h-3.5" /> Cancel</> : <><Plus className="w-3.5 h-3.5" /> Add</>}
            </button>
          </div>
        </div>

        {/* Forms */}
        {showForm && activeTab === 'cc' && <CreditCardForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleUpdate('credit-cards', editingId, f) : handleCreate('credit-cards', f)} onCancel={resetForm} />}
        {showForm && activeTab === 'nps' && <NpsForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleUpdate('nps', editingId, f) : handleCreate('nps', f)} onCancel={resetForm} />}
        {showForm && activeTab === 'ppf' && <PpfForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleUpdate('ppf', editingId, f) : handleCreate('ppf', f)} onCancel={resetForm} />}
        {showForm && activeTab === 'epf' && <EpfForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleUpdate('epf', editingId, f) : handleCreate('epf', f)} onCancel={resetForm} />}
        {showForm && activeTab === 'bank' && <BankForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleBankUpdate(editingId, f) : handleBankCreate(f)} onCancel={resetForm} />}
        {showForm && activeTab === 'demat' && <DematForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleDematUpdate(editingId, f) : handleDematCreate(f)} onCancel={resetForm} />}

        {/* Lists */}
        <div className="divide-y divide-[var(--border)]">
          {activeTab === 'bank' && (data?.bankAccounts || []).map(a => (
            <AccountRow key={a.id} icon={Landmark} color="blue" title={a.accountName} subtitle={`${a.bankName} • ${a.accountType || 'SAVINGS'}`}
              detail={fmt(a.balance)} extra={a.accountNumber ? `****${a.accountNumber.slice(-4)}` : ''}
              accountType="BANK" accountId={a.id} logoUrl={getLogo('BANK', a.bankName)}
              onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleBankDelete(a.id)} />
          ))}
          {activeTab === 'demat' && (data?.dematAccounts || []).map(a => (
            <AccountRow key={a.id} icon={Building2} color="indigo" title={a.brokerName} subtitle={a.accountType || 'Equity'}
              detail={a.isDefault ? 'Default' : ''} extra={a.accountNumber || ''}
              accountType="DEMAT" accountId={a.id} logoUrl={getLogo('BROKER', a.brokerName)}
              onEdit={() => { setForm({ brokerName: a.brokerName, accountNumber: a.accountNumber || '', accountType: a.accountType, description: a.description, isDefault: a.isDefault }); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleDematDelete(a.id)} />
          ))}
          {activeTab === 'cc' && (data?.creditCards || []).map(a => (
            <AccountRow key={a.id} icon={CreditCard} color="red" title={`${a.cardIssuer} ${a.cardName || ''}`}
              subtitle={`${a.cardNetwork || ''} • ****${a.cardLastFour || '????'}`}
              detail={a.creditLimit ? `Limit: ${fmt(a.creditLimit)}` : ''}
              extra={a.rewardType ? `${a.rewardType}` : ''}
              badge={a.isActive ? null : 'Inactive'}
              accountType="CREDIT_CARD" accountId={a.id} logoUrl={getLogo('BANK', a.cardIssuer) || getLogo('CARD_ISSUER', a.cardIssuer)}
              onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleDelete('credit-cards', a.id, `${a.cardIssuer} card`)} />
          ))}
          {activeTab === 'nps' && (data?.npsAccounts || []).map(a => {
            const craLogo = NPS_CRAS.find(c => c.value === a.cra)?.logo
            return (
              <AccountRow key={a.id} icon={Shield} color="green" title={`PRAN: ${a.pranNumber}`}
                subtitle={`${a.fundManager || 'Unknown'} • ${a.tier}${a.schemePreference ? ` • ${a.schemePreference}` : ''}${a.cra ? ` • ${a.cra}` : ''}`}
                detail={a.currentValue ? fmt(a.currentValue) : ''} extra={a.employerName || ''}
                accountType="NPS" accountId={a.id} logoUrl={craLogo}
                onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
                onDelete={() => handleDelete('nps', a.id, `NPS ${a.pranNumber}`)} />
            )
          })}
          {activeTab === 'ppf' && (data?.ppfAccounts || []).map(a => (
            <AccountRow key={a.id} icon={PiggyBank} color="amber" title={`PPF - ${a.bankOrPostOffice}`}
              subtitle={`A/C: ${a.accountNumber}${a.branch ? ` • ${a.branch}` : ''}`}
              detail={a.currentBalance ? fmt(a.currentBalance) : ''}
              extra={a.maturityDate ? `Matures: ${new Date(a.maturityDate).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })}` : ''}
              accountType="PPF" accountId={a.id}
              onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleDelete('ppf', a.id, 'PPF account')} />
          ))}
          {activeTab === 'epf' && (data?.epfAccounts || []).map(a => (
            <AccountRow key={a.id} icon={Briefcase} color="purple" title={a.employerName || 'EPF Account'}
              subtitle={`UAN: ${a.uanNumber || '—'}${a.pfNumber ? ` • PF: ${a.pfNumber}` : ''}`}
              detail={a.currentBalance ? fmt(a.currentBalance) : ''}
              extra={a.isActive ? 'Active' : 'Inactive'}
              accountType="EPF" accountId={a.id}
              onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleDelete('epf', a.id, 'EPF account')} />
          ))}
        </div>

        {/* Empty state */}
        {((activeTab === 'bank' && !(data?.bankAccounts?.length)) ||
          (activeTab === 'demat' && !(data?.dematAccounts?.length)) ||
          (activeTab === 'cc' && !(data?.creditCards?.length)) ||
          (activeTab === 'nps' && !(data?.npsAccounts?.length)) ||
          (activeTab === 'ppf' && !(data?.ppfAccounts?.length)) ||
          (activeTab === 'epf' && !(data?.epfAccounts?.length))) && !showForm && (
          <div className="p-8 text-center text-[var(--text-muted)]">
            <p className="text-sm">No {TABS.find(t => t.id === activeTab)?.label.toLowerCase()} accounts yet</p>
            <button onClick={() => setShowForm(true)} className="text-xs text-[var(--primary)] mt-2 hover:underline">Add one</button>
          </div>
        )}
      </div>

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

function AccountRow({ icon: Icon, color, title, subtitle, detail, extra, badge, onEdit, onDelete, accountType, accountId, logoUrl }) {
  const [showDocs, setShowDocs] = useState(false)
  const [docs, setDocs] = useState([])
  const [loadingDocs, setLoadingDocs] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [confirmDialog, setConfirmDialog] = useState({ open: false, title: '', description: '', onConfirm: null })
  const fileRef = useRef(null)

  const loadDocs = async () => {
    if (!accountType || !accountId) return
    setLoadingDocs(true)
    try {
      const { data } = await client.get(`/documents/account/${accountType}/${accountId}`)
      setDocs(data || [])
    } catch { setDocs([]) }
    finally { setLoadingDocs(false) }
  }

  const toggleDocs = () => {
    if (!showDocs) loadDocs()
    setShowDocs(!showDocs)
  }

  const handleUpload = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('category', accountType)
      formData.append('description', `${title} document`)
      formData.append('accountType', accountType)
      formData.append('accountId', accountId)
      await client.post('/documents/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
      toast.success('Document uploaded')
      loadDocs()
    } catch (err) {
      toast.error('Upload failed', { description: err.response?.data?.message || err.message })
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  const handleDeleteDoc = (docId) => {
    setConfirmDialog({
      open: true,
      title: 'Delete this document?',
      description: 'This action cannot be undone.',
      onConfirm: async () => {
        try {
          await client.delete(`/documents/${docId}`)
          setDocs(prev => prev.filter(d => d.id !== docId))
          toast.success('Document deleted')
        } catch (err) {
          toast.error('Delete failed', { description: err.response?.data?.message || err.message })
        }
      },
    })
  }

  return (
    <div>
      <div className="px-4 py-3 flex items-center gap-3 hover:bg-[var(--hover-bg)] transition">
        <div className={`w-9 h-9 rounded-lg bg-${color}-500/10 flex items-center justify-center shrink-0 overflow-hidden`}>
          {logoUrl ? (
            <img src={logoUrl} alt="" className="w-6 h-6 object-contain" onError={(e) => { e.target.style.display='none'; e.target.nextSibling.style.display='block' }} />
          ) : null}
          <Icon className={`w-4.5 h-4.5 text-${color}-400 ${logoUrl ? 'hidden' : ''}`} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <p className="text-sm font-medium text-[var(--text)] truncate">{title}</p>
            {badge && <span className="text-[10px] bg-[var(--hover-bg)] text-[var(--text-muted)] px-1.5 py-0.5 rounded">{badge}</span>}
          </div>
          <p className="text-xs text-[var(--text-muted)] truncate">{subtitle}</p>
        </div>
        {detail && <p className="text-sm font-semibold text-[var(--text)] shrink-0">{detail}</p>}
        {extra && <span className="text-xs text-[var(--text-muted)] shrink-0">{extra}</span>}
        <div className="flex items-center gap-1 shrink-0">
          {accountType && accountId && (
            <button onClick={toggleDocs} className={`p-1.5 rounded transition ${showDocs ? 'bg-blue-500/10 text-blue-400' : 'text-[var(--text-muted)] hover:text-blue-400 hover:bg-blue-500/10'}`}
              title="Documents">
              <Paperclip className="w-3.5 h-3.5" />
            </button>
          )}
          <button onClick={onEdit} className="p-1.5 rounded hover:bg-blue-500/10 text-[var(--text-muted)] hover:text-blue-400 transition"><Pencil className="w-3.5 h-3.5" /></button>
          <button onClick={onDelete} className="p-1.5 rounded hover:bg-red-500/10 text-[var(--text-muted)] hover:text-red-400 transition"><Trash2 className="w-3.5 h-3.5" /></button>
        </div>
      </div>

      {/* Documents section */}
      {showDocs && (
        <div className="px-4 pb-3 pl-16">
          <div className="bg-[var(--bg)] rounded-lg border border-[var(--border)] p-3 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-[var(--text-muted)]">Documents ({docs.length})</span>
              <div>
                <input ref={fileRef} type="file" onChange={handleUpload} className="hidden" />
                <button onClick={() => fileRef.current?.click()} disabled={uploading}
                  className="text-xs text-blue-400 hover:text-blue-300 inline-flex items-center gap-1 transition">
                  {uploading ? <Loader2 className="w-3 h-3 animate-spin" /> : <Upload className="w-3 h-3" />}
                  {uploading ? 'Uploading...' : 'Upload'}
                </button>
              </div>
            </div>
            {loadingDocs && <div className="flex justify-center py-2"><Loader2 className="w-4 h-4 animate-spin text-[var(--text-muted)]" /></div>}
            {!loadingDocs && docs.length === 0 && (
              <p className="text-xs text-[var(--text-secondary)] text-center py-2">No documents attached. Upload passbook, statement, or any supporting file.</p>
            )}
            {docs.map(doc => (
              <div key={doc.id} className="flex items-center gap-2 py-1.5 px-2 rounded hover:bg-[var(--hover-bg)] transition group">
                <FileText className="w-3.5 h-3.5 text-[var(--text-muted)] shrink-0" />
                <div className="flex-1 min-w-0">
                  <p className="text-xs text-[var(--text)] truncate">{doc.originalFilename}</p>
                  <p className="text-[10px] text-[var(--text-muted)]">
                    {doc.fileSize ? `${(doc.fileSize / 1024).toFixed(0)} KB` : ''} 
                    {doc.createdAt ? ` • ${new Date(doc.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })}` : ''}
                  </p>
                </div>
                <a href={`/api/v1/documents/${doc.id}/download`} target="_blank" rel="noreferrer"
                  className="p-1 rounded text-[var(--text-muted)] hover:text-blue-400 opacity-0 group-hover:opacity-100 transition">
                  <Download className="w-3 h-3" />
                </a>
                <button onClick={() => handleDeleteDoc(doc.id)}
                  className="p-1 rounded text-[var(--text-muted)] hover:text-red-400 opacity-0 group-hover:opacity-100 transition">
                  <Trash2 className="w-3 h-3" />
                </button>
              </div>
            ))}
          </div>
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

// ── Forms ──

function FormWrapper({ children, onSubmit, onCancel, editingId }) {
  return (
    <form onSubmit={(e) => { e.preventDefault(); onSubmit() }} className="p-4 border-b border-[var(--border)] bg-[var(--bg)]/50 space-y-3">
      {children}
      <div className="flex justify-end gap-2 pt-1">
        <button type="button" onClick={onCancel} className="px-3 py-1.5 text-xs text-[var(--text-muted)] border border-[var(--border)] rounded-lg hover:text-[var(--text)]">Cancel</button>
        <button type="submit" className="px-4 py-1.5 text-xs bg-blue-500 hover:bg-blue-600 text-white rounded-lg font-medium">{editingId ? 'Update' : 'Add'}</button>
      </div>
    </form>
  )
}

function Input({ label, value, onChange, type = 'text', placeholder, required }) {
  return (
    <div>
      <label className="block text-xs text-[var(--text-muted)] mb-1">{label}</label>
      <input type={type} value={value || ''} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} required={required}
        className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2 text-sm text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50" />
    </div>
  )
}

function Select({ label, value, onChange, options, required, showLogo }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const selected = options.find(o => (typeof o === 'string' ? o : o.value) === value)
  const selectedLabel = selected ? (typeof selected === 'string' ? selected : selected.label) : null
  const selectedLogo = selected?.metadata?.logo

  useEffect(() => {
    if (!open) return
    const handleClick = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  return (
    <div ref={ref}>
      <label className="block text-xs text-[var(--text-muted)] mb-1">{label}</label>
      <div className="relative">
        <button type="button" onClick={() => setOpen(!open)}
          className={`w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2 text-sm text-left flex items-center justify-between focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50 ${!value && required ? 'border-red-500/30' : ''}`}>
          <span className="flex items-center gap-2 truncate">
            {showLogo && selectedLogo && (
              <img src={selectedLogo} alt="" className="w-4 h-4 rounded object-contain bg-white p-px flex-shrink-0" onError={(e) => { e.target.style.display = 'none' }} />
            )}
            <span className={value ? 'text-[var(--text)]' : 'text-[var(--text-muted)]'}>{selectedLabel || 'Select...'}</span>
          </span>
          <ChevronDown className={`w-3.5 h-3.5 text-[var(--text-muted)] flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
        {open && (
          <div className="absolute left-0 right-0 top-full mt-1 bg-[var(--bg-card)] border border-[var(--border)] rounded-lg shadow-xl z-50 max-h-52 overflow-y-auto">
            {!required && (
              <button type="button" onClick={() => { onChange(''); setOpen(false) }}
                className={`w-full px-3 py-2 flex items-center gap-2 hover:bg-[var(--hover-bg)] transition text-left text-sm ${!value ? 'bg-blue-500/10 text-blue-400' : 'text-[var(--text-muted)]'}`}>
                Select...
              </button>
            )}
            {options.map(o => {
              const val = typeof o === 'string' ? o : o.value
              const lbl = typeof o === 'string' ? o : o.label
              const logo = o?.metadata?.logo
              const isSelected = val === value
              return (
                <button type="button" key={val} onClick={() => { onChange(val); setOpen(false) }}
                  className={`w-full px-3 py-2 flex items-center gap-2 hover:bg-[var(--hover-bg)] transition text-left text-sm ${isSelected ? 'bg-blue-500/10 text-blue-400' : ''}`}>
                  {showLogo && logo && (
                    <img src={logo} alt="" className="w-4 h-4 rounded object-contain bg-white p-px flex-shrink-0" onError={(e) => { e.target.style.display = 'none' }} />
                  )}
                  <span className="truncate">{lbl}</span>
                </button>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}

function CreditCardForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  const { options: issuers } = useReferenceData('CARD_ISSUER')
  const { options: networks } = useReferenceData('CARD_NETWORK')
  const { options: rewardTypes } = useReferenceData('CARD_REWARD_TYPE')
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Select label="Card Issuer *" value={form.cardIssuer} onChange={v => s('cardIssuer', v)} options={issuers} required showLogo />
        <Input label="Card Name" value={form.cardName} onChange={v => s('cardName', v)} placeholder="e.g. Regalia, Simply Click" />
        <Select label="Network" value={form.cardNetwork} onChange={v => s('cardNetwork', v)} options={networks} showLogo />
        <Input label="Last 4 Digits" value={form.cardLastFour} onChange={v => s('cardLastFour', v.slice(0,4))} placeholder="1234" />
        <Input label="Credit Limit" value={form.creditLimit} onChange={v => s('creditLimit', v)} type="number" placeholder="300000" />
        <Input label="Billing Cycle Day" value={form.billingCycleDay} onChange={v => s('billingCycleDay', v)} type="number" placeholder="1-28" />
        <Input label="Payment Due Day" value={form.paymentDueDay} onChange={v => s('paymentDueDay', v)} type="number" placeholder="15" />
        <Select label="Reward Type" value={form.rewardType} onChange={v => s('rewardType', v)} options={rewardTypes} />
        <Input label="Annual Fee" value={form.annualFee} onChange={v => s('annualFee', v)} type="number" placeholder="499" />
      </div>
    </FormWrapper>
  )
}

function NpsForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  const { options: fundManagers } = useReferenceData('NPS_FUND_MANAGER')
  const { options: tiers } = useReferenceData('NPS_TIER')
  const { options: schemes } = useReferenceData('NPS_SCHEME')
  const selectedCra = NPS_CRAS.find(c => c.value === form.cra)
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Input label="PRAN Number *" value={form.pranNumber} onChange={v => s('pranNumber', v.slice(0,12))} placeholder="12-digit PRAN" required />
        <div>
          <label className="block text-xs text-[var(--text-muted)] mb-1">CRA</label>
          <div className="flex gap-2">
            {NPS_CRAS.map(cra => (
              <button key={cra.value} type="button"
                onClick={() => s('cra', cra.value)}
                className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-xs font-medium transition border ${
                  form.cra === cra.value
                    ? 'bg-blue-500/15 text-blue-400 border-blue-500/40'
                    : 'bg-[var(--input-bg)] text-[var(--text-muted)] border-[var(--border)] hover:border-[var(--text-secondary)]'
                }`}>
                <img src={cra.logo} alt="" className="w-4 h-4 rounded object-contain bg-white p-px"
                  onError={(e) => { e.target.style.display = 'none' }} />
                {cra.label}
              </button>
            ))}
          </div>
        </div>
        <Select label="Fund Manager" value={form.fundManager} onChange={v => s('fundManager', v)} options={fundManagers} />
        <Select label="Tier *" value={form.tier} onChange={v => s('tier', v)} options={tiers} required />
        <Select label="Scheme Preference" value={form.schemePreference} onChange={v => s('schemePreference', v)} options={schemes} />
        <Input label="Opening Date" value={form.openingDate} onChange={v => s('openingDate', v)} type="date" />
        <Input label="Employer" value={form.employerName} onChange={v => s('employerName', v)} placeholder="Company name" />
      </div>
    </FormWrapper>
  )
}

function PpfForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  const { options: banks } = useReferenceData('BANK')
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Input label="Account Number *" value={form.accountNumber} onChange={v => s('accountNumber', v)} placeholder="PPF A/C number" required />
        <Select label="Bank / Post Office *" value={form.bankOrPostOffice} onChange={v => s('bankOrPostOffice', v)} options={banks} required />
        <Input label="Branch" value={form.branch} onChange={v => s('branch', v)} placeholder="Branch name" />
        <Input label="Opening Date" value={form.openingDate} onChange={v => s('openingDate', v)} type="date" />
        <Input label="Maturity Date" value={form.maturityDate} onChange={v => s('maturityDate', v)} type="date" />
        <Input label="Nominee" value={form.nominee} onChange={v => s('nominee', v)} placeholder="Nominee name" />
        <Input label="Current Balance" value={form.currentBalance} onChange={v => s('currentBalance', v)} type="number" placeholder="450000" />
        <Input label="This FY Deposit" value={form.currentFyDeposit} onChange={v => s('currentFyDeposit', v)} type="number" placeholder="Max 1.5L" />
        <Input label="Interest Rate %" value={form.interestRate} onChange={v => s('interestRate', v)} type="number" placeholder="7.1" />
      </div>
    </FormWrapper>
  )
}

function EpfForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Input label="UAN Number" value={form.uanNumber} onChange={v => s('uanNumber', v.slice(0,12))} placeholder="12-digit UAN" />
        <Input label="PF Number" value={form.pfNumber} onChange={v => s('pfNumber', v)} placeholder="Regional PF number" />
        <Input label="Employer *" value={form.employerName} onChange={v => s('employerName', v)} placeholder="Company name" required />
        <Input label="Date of Joining" value={form.dateOfJoining} onChange={v => s('dateOfJoining', v)} type="date" />
        <Input label="Employee Rate %" value={form.employeeContributionRate} onChange={v => s('employeeContributionRate', v)} type="number" placeholder="12" />
        <Input label="Employer Rate %" value={form.employerContributionRate} onChange={v => s('employerContributionRate', v)} type="number" placeholder="12" />
        <Input label="Current Balance" value={form.currentBalance} onChange={v => s('currentBalance', v)} type="number" placeholder="500000" />
        <Input label="Basic Salary" value={form.basicSalary} onChange={v => s('basicSalary', v)} type="number" placeholder="Monthly basic" />
      </div>
    </FormWrapper>
  )
}

function BankForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  const { options: banks } = useReferenceData('BANK')
  const { options: accountTypes } = useReferenceData('BANK_ACCOUNT_TYPE')
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Input label="Account Name *" value={form.accountName} onChange={v => s('accountName', v)} placeholder="e.g. HDFC Savings" required />
        <Select label="Bank Name *" value={form.bankName} onChange={v => s('bankName', v)} options={banks} required showLogo />
        <Input label="Account Number" value={form.accountNumber} onChange={v => s('accountNumber', v)} placeholder="A/C number" />
        <Select label="Type" value={form.accountType} onChange={v => s('accountType', v)} options={accountTypes} />
        <Input label="IFSC Code" value={form.ifscCode} onChange={v => s('ifscCode', v)} placeholder="HDFC0001234" />
        <Input label="Branch" value={form.branch} onChange={v => s('branch', v)} placeholder="Branch name" />
        <Input label="Balance" value={form.balance} onChange={v => s('balance', v)} type="number" placeholder="285000" />
      </div>
    </FormWrapper>
  )
}

function DematForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  const { options: brokers } = useReferenceData('BROKER')
  const { options: dematTypes } = useReferenceData('DEMAT_ACCOUNT_TYPE')
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Select label="Broker *" value={form.brokerName} onChange={v => s('brokerName', v)} options={brokers} required showLogo />
        <Input label="Account Number" value={form.accountNumber} onChange={v => s('accountNumber', v)} placeholder="Demat A/C number" />
        <Select label="Type" value={form.accountType} onChange={v => s('accountType', v)} options={dematTypes} />
        <Input label="Description" value={form.description} onChange={v => s('description', v)} placeholder="Notes" />
      </div>
    </FormWrapper>
  )
}
