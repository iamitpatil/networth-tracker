import { useState, useEffect } from 'react'
import client from '../api/client'
import { Plus, X, Building2, Pencil, Trash2, Loader2, Mail, RefreshCw, CheckCircle, AlertCircle, Download, Search } from 'lucide-react'
import { useReferenceData } from '../hooks/useReferenceData'
import { ConfirmDialog } from '../components/ui/Modal'
import StyledSelect from '../components/ui/StyledSelect'

export default function BankAccounts() {
  const { options: bankNames } = useReferenceData('BANK')
  const { options: accountTypes } = useReferenceData('BANK_ACCOUNT_TYPE')
  const [accounts, setAccounts] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState({})
  const [saving, setSaving] = useState(false)
  const [tab, setTab] = useState('accounts')

  const [gmailStatus, setGmailStatus] = useState(null)
  const [gmailLoading, setGmailLoading] = useState(false)
  const [gmailTxns, setGmailTxns] = useState([])
  const [txnFilter, setTxnFilter] = useState('pending')
  const [confirmDialog, setConfirmDialog] = useState({ open: false, title: '', description: '', onConfirm: null })

  const load = async () => {
    try {
      const { data } = await client.get('/bank-accounts')
      setAccounts(data || [])
    } catch (e) { console.error(e) }
    finally { setLoading(false) }
  }

  useEffect(() => { load() }, [])

  useEffect(() => {
    if (tab === 'gmail') {
      client.get('/gmail/status').then(r => setGmailStatus(r.data)).catch(() => {})
      loadTransactions()
    }
  }, [tab])

  const loadTransactions = () => {
    client.get(`/gmail/transactions?status=${txnFilter}`)
      .then(r => setGmailTxns(r.data || [])).catch(() => {})
  }

  const resetForm = () => setForm({ accountName: '', bankName: '', accountNumber: '', accountType: 'SAVINGS', ifscCode: '', branch: '', balance: '' })

  const openCreate = () => { resetForm(); setEditing(null); setShowForm(true) }
  const openEdit = (a) => { setForm({ ...a, balance: a.balance }); setEditing(a.id); setShowForm(true) }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = { ...form, balance: parseFloat(form.balance) || 0 }
      if (editing) {
        const { data } = await client.put(`/bank-accounts/${editing}`, payload)
        setAccounts((p) => p.map((a) => a.id === editing ? data : a))
      } else {
        const { data } = await client.post('/bank-accounts', payload)
        setAccounts((p) => [data, ...p])
      }
      setShowForm(false)
    } catch (e) { console.error(e) }
    finally { setSaving(false) }
  }

  const handleDelete = (id) => {
    setConfirmDialog({
      open: true,
      title: 'Delete this bank account?',
      description: 'This action cannot be undone.',
      onConfirm: async () => {
        try {
          await client.delete(`/bank-accounts/${id}`)
          setAccounts((p) => p.filter((a) => a.id !== id))
        } catch (e) { console.error(e) }
      },
    })
  }

  const handleGmailAuth = () => {
    client.get('/gmail/auth-url').then(r => {
      if (r.data.url) window.open(r.data.url, '_blank', 'width=600,height=700')
    })
  }

  const handleSync = async () => {
    setGmailLoading(true)
    try {
      await client.post('/gmail/sync')
      loadTransactions()
      load()
    } catch (e) { console.error(e) }
    finally { setGmailLoading(false) }
  }

  const handleConfirm = async (id) => {
    await client.post(`/gmail/transactions/${id}/confirm`)
    loadTransactions(); load()
  }

  const handleIgnore = async (id) => {
    await client.post(`/gmail/transactions/${id}/ignore`)
    loadTransactions()
  }

  if (loading) return <div className="flex justify-center py-20 text-[var(--text-muted)]">Loading...</div>

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Bank Accounts</h1>
          <p className="text-[var(--text-muted)] text-sm mt-1">
            {tab === 'accounts'
              ? `${accounts.length} accounts · Total: Rs. ${accounts.reduce((s, a) => s + (parseFloat(a.balance) || 0), 0).toLocaleString('en-IN')}`
              : 'Monitor transactions & balances from Gmail'}
          </p>
        </div>
        {tab === 'accounts' && (
          <button onClick={() => { if (showForm) { setShowForm(false); resetForm() } else openCreate() }}
            className={`px-4 py-2 rounded-lg flex items-center gap-2 transition ${showForm ? 'bg-[var(--input-bg)] hover:bg-[var(--hover-bg)]' : 'bg-blue-500 hover:bg-blue-600'}`}>
            {showForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
            {showForm ? 'Cancel' : 'Add Account'}
          </button>
        )}
      </div>

      <div className="flex gap-1 bg-[var(--bg-card)] rounded-xl p-1.5 border border-[var(--border)]">
        <button onClick={() => setTab('accounts')}
          className={`flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium transition ${
            tab === 'accounts' ? 'bg-blue-500/20 text-blue-400' : 'text-[var(--text-muted)] hover:text-[var(--text)]'}`}>
          <Building2 className="w-4 h-4" /> Accounts
        </button>
        <button onClick={() => setTab('gmail')}
          className={`flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium transition ${
            tab === 'gmail' ? 'bg-blue-500/20 text-blue-400' : 'text-[var(--text-muted)] hover:text-[var(--text)]'}`}>
          <Mail className="w-4 h-4" /> Bank Alerts
        </button>
      </div>

      {tab === 'accounts' && (
        <>
          {showForm && (
            <form onSubmit={handleSubmit} className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] space-y-4">
              <h3 className="text-lg font-semibold">{editing ? 'Edit' : 'Add'} Bank Account</h3>
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm text-[var(--text-muted)] mb-1">Account Name</label>
                  <input type="text" value={form.accountName || ''} onChange={(e) => setForm({ ...form, accountName: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" required />
                </div>
                <div>
                  <label className="block text-sm text-[var(--text-muted)] mb-1">Bank Name</label>
                  <StyledSelect value={form.bankName || ''} onChange={(v) => setForm({ ...form, bankName: v })}
                    label="Bank Name *" placeholder="Select bank..." options={bankNames} required showLogo />
                </div>
                <div>
                  <label className="block text-sm text-[var(--text-muted)] mb-1">Account Number</label>
                  <input type="text" value={form.accountNumber || ''} onChange={(e) => setForm({ ...form, accountNumber: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" />
                </div>
                <div>
                  <StyledSelect value={form.accountType || 'SAVINGS'} onChange={(v) => setForm({ ...form, accountType: v })}
                    label="Account Type"
                    options={accountTypes.length > 0 ? accountTypes : [
                      { value: 'SAVINGS', label: 'Savings' }, { value: 'CURRENT', label: 'Current' },
                      { value: 'FD', label: 'Fixed Deposit' }, { value: 'NRE', label: 'NRE' }, { value: 'NRO', label: 'NRO' },
                    ]} />
                </div>
                <div>
                  <label className="block text-sm text-[var(--text-muted)] mb-1">IFSC Code</label>
                  <input type="text" value={form.ifscCode || ''} onChange={(e) => setForm({ ...form, ifscCode: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" />
                </div>
                <div>
                  <label className="block text-sm text-[var(--text-muted)] mb-1">Branch</label>
                  <input type="text" value={form.branch || ''} onChange={(e) => setForm({ ...form, branch: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" />
                </div>
                <div>
                  <label className="block text-sm text-[var(--text-muted)] mb-1">Balance (Rs.)</label>
                  <input type="number" step="any" value={form.balance} onChange={(e) => setForm({ ...form, balance: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" />
                </div>
              </div>
              <div className="flex justify-end gap-3 pt-2">
                <button type="button" onClick={() => { setShowForm(false); resetForm() }} className="px-4 py-2 bg-[var(--input-bg)] rounded-lg hover:bg-[var(--hover-bg)] transition">Cancel</button>
                <button type="submit" disabled={saving || !form.accountName || !form.bankName}
                  className="px-6 py-2 bg-blue-500 rounded-lg hover:bg-blue-600 transition disabled:opacity-50 flex items-center gap-2">
                  {saving && <Loader2 className="w-4 h-4 animate-spin" />}
                  {editing ? 'Update' : 'Add'}
                </button>
              </div>
            </form>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {accounts.length === 0 ? (
              <div className="col-span-full text-center py-16 text-[var(--text-secondary)]">
                <Building2 className="w-16 h-16 mx-auto mb-4 opacity-40" />
                <p className="text-lg">No bank accounts yet</p>
                <p className="text-sm mt-1">Add your accounts to track cash balances in your net worth</p>
              </div>
            ) : accounts.map((a) => (
              <div key={a.id} className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)] hover:border-blue-500/30 transition">
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-blue-500/10 flex items-center justify-center overflow-hidden">
                      {bankNames.find(b => b.value === a.bankName)?.metadata?.logo ? (
                        <img src={bankNames.find(b => b.value === a.bankName).metadata.logo} alt="" className="w-7 h-7 object-contain"
                          onError={(e) => { e.target.style.display='none'; e.target.parentElement.querySelector('svg').style.display='block' }} />
                      ) : null}
                      <Building2 className={`w-5 h-5 text-blue-400 ${bankNames.find(b => b.value === a.bankName)?.metadata?.logo ? 'hidden' : ''}`} />
                    </div>
                    <div>
                      <p className="font-semibold">{a.accountName}</p>
                      <p className="text-xs text-[var(--text-muted)]">{a.bankName} · {a.accountType}</p>
                    </div>
                  </div>
                  <div className="flex gap-1">
                    <button onClick={() => openEdit(a)} className="p-1.5 text-[var(--text-secondary)] hover:text-blue-400 transition"><Pencil className="w-4 h-4" /></button>
                    <button onClick={() => handleDelete(a.id)} aria-label="Delete bank account" className="p-1.5 text-[var(--text-secondary)] hover:text-red-400 transition"><Trash2 className="w-4 h-4" /></button>
                  </div>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-sm text-[var(--text-muted)]">Balance</span>
                  <span className="text-lg font-bold">Rs. {(parseFloat(a.balance) || 0).toLocaleString('en-IN')}</span>
                </div>
                <div className="flex flex-wrap gap-2 mt-3 text-xs text-[var(--text-secondary)]">
                  {a.accountNumber && <span>••••{a.accountNumber.slice(-4)}</span>}
                  {a.ifscCode && <span>{a.ifscCode}</span>}
                  {a.branch && <span>{a.branch}</span>}
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {tab === 'gmail' && (
        <div className="space-y-4">
          {gmailStatus === null ? (
            <div className="text-center py-12 text-[var(--text-muted)]"><Loader2 className="w-6 h-6 animate-spin mx-auto" /></div>
          ) : !gmailStatus.connected ? (
            <div className="bg-[var(--bg-card)] rounded-xl p-8 border border-[var(--border)] text-center space-y-4">
              <Mail className="w-16 h-16 mx-auto text-blue-400/50" />
              <h3 className="text-lg font-semibold">Connect Gmail</h3>
              <p className="text-sm text-[var(--text-muted)] max-w-md mx-auto">
                Automatically scan bank alert emails (HDFC, ICICI, SBI, Axis, Kotak, etc.) to track transactions and update balances.
              </p>
              {gmailStatus.configured === 'false' ? (
                <p className="text-xs text-amber-400">Gmail not configured — set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET env vars, or edit balances manually in the Accounts tab.</p>
              ) : (
                <button onClick={handleGmailAuth}
                  className="inline-flex items-center gap-2 px-6 py-3 bg-blue-500 rounded-lg hover:bg-blue-600 transition text-sm font-medium">
                  <Mail className="w-4 h-4" /> Connect Gmail
                </button>
              )}
              <p className="text-xs text-[var(--text-muted)]">
                You can always <button onClick={() => setTab('accounts')} className="text-blue-400 hover:underline">manually edit balances</button> instead.
              </p>
            </div>
          ) : (
            <>
              <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)] flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-green-500/20 flex items-center justify-center">
                    <Mail className="w-5 h-5 text-green-400" />
                  </div>
                  <div>
                    <p className="font-medium">{gmailStatus.email}</p>
                    <p className="text-xs text-[var(--text-muted)]">
                      {gmailStatus.lastSyncAt
                        ? `Last synced: ${new Date(gmailStatus.lastSyncAt).toLocaleString('en-IN')}`
                        : 'Not synced yet'}
                    </p>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button onClick={handleSync} disabled={gmailLoading}
                    className="flex items-center gap-2 px-4 py-2 bg-blue-500 rounded-lg hover:bg-blue-600 disabled:opacity-50 transition text-sm">
                    {gmailLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <RefreshCw className="w-4 h-4" />}
                    Sync Now
                  </button>
                </div>
              </div>

              <div className="flex items-center justify-between">
                <h3 className="font-semibold text-[var(--text)]">Email Transactions</h3>
                <div className="flex gap-1 bg-[var(--bg)] rounded-lg p-0.5 border border-[var(--border)]">
                  <button onClick={() => { setTxnFilter('pending'); loadTransactions() }}
                    className={`px-3 py-1.5 rounded text-xs transition ${txnFilter === 'pending' ? 'bg-blue-500/20 text-blue-400' : 'text-[var(--text-muted)]'}`}>Pending</button>
                  <button onClick={() => { setTxnFilter('all'); loadTransactions() }}
                    className={`px-3 py-1.5 rounded text-xs transition ${txnFilter === 'all' ? 'bg-blue-500/20 text-blue-400' : 'text-[var(--text-muted)]'}`}>All</button>
                </div>
              </div>

              {gmailTxns.length === 0 ? (
                <div className="text-center py-12 text-[var(--text-muted)]">
                  <Search className="w-12 h-12 mx-auto mb-3 opacity-40" />
                  <p>No {txnFilter === 'pending' ? 'pending ' : ''}transactions found</p>
                  <p className="text-xs mt-1">New bank emails will appear here</p>
                </div>
              ) : (
                <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
                  <table className="w-full">
                    <thead className="bg-[var(--bg)]/50 text-left">
                      <tr>
                        <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Amount</th>
                        <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Type</th>
                        <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Balance</th>
                        <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Bank</th>
                        <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Date</th>
                        <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Status</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--border)]">
                      {gmailTxns.map((tx) => (
                        <tr key={tx.id} className="hover:bg-[var(--hover-bg)]">
                          <td className="px-4 py-3 font-medium">
                            {tx.amount ? `Rs. ${(parseFloat(tx.amount) || 0).toLocaleString('en-IN')}` : '-'}
                          </td>
                          <td className="px-4 py-3">
                            {tx.transactionType ? (
                              <span className={`text-xs px-2 py-0.5 rounded font-medium ${
                                tx.transactionType === 'CREDIT' ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'
                              }`}>{tx.transactionType}</span>
                            ) : '-'}
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                            {tx.balance ? `Rs. ${(parseFloat(tx.balance) || 0).toLocaleString('en-IN')}` : '-'}
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                            {tx.bankAccountId ? accounts.find(a => a.id === tx.bankAccountId)?.accountName || 'Linked' : '-'}
                          </td>
                          <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                            {tx.transactionDate ? new Date(tx.transactionDate).toLocaleDateString('en-IN') : (tx.createdAt ? new Date(tx.createdAt).toLocaleDateString('en-IN') : '-')}
                          </td>
                          <td className="px-4 py-3">
                            {tx.status === 'PENDING' ? (
                              <div className="flex gap-1">
                                <button onClick={() => handleConfirm(tx.id)}
                                  className="p-1 text-green-400 hover:text-green-300 transition" title="Confirm & update balance">
                                  <CheckCircle className="w-4 h-4" />
                                </button>
                                <button onClick={() => handleIgnore(tx.id)}
                                  className="p-1 text-red-400 hover:text-red-300 transition" title="Ignore">
                                  <AlertCircle className="w-4 h-4" />
                                </button>
                              </div>
                            ) : (
                              <span className={`text-xs font-medium ${
                                tx.status === 'CONFIRMED' ? 'text-green-400' : 'text-[var(--text-muted)]'
                              }`}>{tx.status}</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}
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
