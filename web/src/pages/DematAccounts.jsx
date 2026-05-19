import { useState, useEffect } from 'react'
import { Building2, Plus, Trash2, Pencil, Check, X, Star } from 'lucide-react'
import client from '../api/client'

const BROKERS = [
  'Zerodha', 'Groww', 'Angel One', 'ICICI Direct', 'HDFC Securities',
  'Sharekhan', '5Paisa', 'Upstox', 'Motilal Oswal', 'Kotak Securities',
  'Axis Direct', 'IIFL', 'Edelweiss', 'SBI Securities', 'Paytm Money',
]

const ACCOUNT_TYPES = ['Equity', 'Commodity', 'Derivatives', 'Mutual Funds']

export default function DematAccounts() {
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

  useEffect(() => { fetchAccounts() }, [])

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

  async function handleDelete(id) {
    if (!confirm('Delete this demat account?')) return
    try {
      await client.delete(`/demat-accounts/${id}`)
      await fetchAccounts()
    } catch (err) {
      console.error('Failed to delete demat account', err)
    }
  }

  if (loading) return <div className="flex justify-center py-20 text-slate-400">Loading...</div>

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Demat Accounts</h1>
          <p className="text-slate-400 text-sm mt-1">Manage your brokerage accounts</p>
        </div>
        <button
          onClick={() => { if (!showForm) openAdd(); else { setShowForm(false); resetForm(); } }}
          className={`px-4 py-2 rounded-lg flex items-center gap-2 transition ${showForm ? 'bg-slate-600 hover:bg-slate-500' : 'bg-blue-500 hover:bg-blue-600'}`}
        >
          <Plus className="w-4 h-4" /> {showForm ? 'Cancel' : 'Add Account'}
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <p className="text-slate-400 text-sm">Total Accounts</p>
          <p className="text-2xl font-bold mt-1">{accounts.length}</p>
        </div>
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <p className="text-slate-400 text-sm">Active Brokers</p>
          <p className="text-2xl font-bold mt-1">{new Set(accounts.map(a => a.brokerName)).size}</p>
        </div>
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <p className="text-slate-400 text-sm">Default Account</p>
          <p className="text-lg font-bold mt-1 text-blue-400">
            {accounts.find(a => a.isDefault)?.brokerName || 'Not set'}
          </p>
        </div>
      </div>

      {showForm && (
        <div className="bg-slate-800 rounded-xl p-6 border border-slate-700">
          <h3 className="text-lg font-semibold mb-4">{editingId ? 'Edit' : 'Add New'} Demat Account</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-slate-400 mb-1">Broker</label>
                <select
                  value={form.brokerName}
                  onChange={(e) => setForm({ ...form, brokerName: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                  required
                >
                  <option value="">Select broker...</option>
                  {BROKERS.map((b) => (
                    <option key={b} value={b}>{b}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Account Number <span className="text-slate-500">(optional)</span></label>
                <input
                  type="text"
                  value={form.accountNumber}
                  onChange={(e) => setForm({ ...form, accountNumber: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                  placeholder={editingId ? 'Leave blank to keep unchanged' : 'Full demat account number'}
                />
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Account Type</label>
                <select
                  value={form.accountType}
                  onChange={(e) => setForm({ ...form, accountType: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
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
                    className="w-4 h-4 rounded bg-slate-700 border-slate-600"
                  />
                  <span className="text-sm text-slate-300">Set as default account</span>
                </label>
              </div>
            </div>
            <div>
              <label className="block text-sm text-slate-400 mb-1">Notes <span className="text-slate-500">(optional)</span></label>
              <textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                placeholder="Any notes about this account..."
                rows={2}
              />
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => { setShowForm(false); resetForm(); }}
                className="px-4 py-2 bg-slate-700 rounded-lg hover:bg-slate-600 transition"
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
          {accounts.map((acc) => (
            <div key={acc.id} className="bg-slate-800 rounded-xl p-5 border border-slate-700 relative group">
              {acc.isDefault && (
                <div className="absolute top-3 right-3 flex items-center gap-1 text-xs bg-blue-500/20 text-blue-400 px-2 py-1 rounded-full">
                  <Star className="w-3 h-3" /> Default
                </div>
              )}
              <div className="flex items-start gap-4">
                <div className="w-12 h-12 rounded-xl bg-blue-500/20 flex items-center justify-center flex-shrink-0">
                  <Building2 className="w-6 h-6 text-blue-400" />
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="text-lg font-semibold">{acc.brokerName}</h3>
                  <div className="flex flex-wrap gap-2 mt-2">
                    <span className="text-xs bg-slate-700 text-slate-300 px-2 py-1 rounded">{acc.accountType}</span>
                    {acc.accountNumber && (
                      <span className="text-xs bg-slate-700 text-slate-300 px-2 py-1 rounded font-mono">
                        {acc.accountNumber}
                      </span>
                    )}
                  </div>
                  {acc.description && (
                    <p className="text-sm text-slate-400 mt-2">{acc.description}</p>
                  )}
                </div>
              </div>
              <div className="flex gap-2 mt-4 pt-3 border-t border-slate-700">
                <button
                  onClick={() => openEdit(acc)}
                  className="flex items-center gap-1 text-sm text-slate-400 hover:text-blue-400 transition"
                >
                  <Pencil className="w-3.5 h-3.5" /> Edit
                </button>
                <button
                  onClick={() => handleDelete(acc.id)}
                  className="flex items-center gap-1 text-sm text-slate-500 hover:text-red-400 transition"
                >
                  <Trash2 className="w-3.5 h-3.5" /> Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="bg-slate-800 rounded-xl p-12 border border-slate-700 text-center">
          <Building2 className="w-12 h-12 text-slate-600 mx-auto mb-4" />
          <p className="text-slate-400 mb-2">No demat accounts added yet</p>
          <p className="text-slate-500 text-sm">Click "Add Account" to link your brokerage accounts</p>
        </div>
      )}
    </div>
  )
}
