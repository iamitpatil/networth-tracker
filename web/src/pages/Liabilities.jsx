import { useState, useEffect, useMemo, useRef } from 'react'
import client from '../api/client'
import { toast } from 'sonner'
import { Plus, Trash2, CheckCircle, XCircle, X, Home, Car, GraduationCap, CreditCard, Wallet, Calendar, Percent, Clock, IndianRupee, TrendingDown, BarChart3, Loader2 } from 'lucide-react'
import { useReferenceData } from '../hooks/useReferenceData'
import CreditCardSpend from '../components/CreditCardSpend'

const LIABILITY_CATEGORIES = [
  {
    id: 'loans',
    label: 'Loans',
    icon: Home,
    color: 'blue',
    types: [
      { value: 'home_loan', label: 'Home Loan', icon: Home, color: 'blue', description: 'Housing / Mortgage' },
      { value: 'car_loan', label: 'Car Loan', icon: Car, color: 'indigo', description: 'Vehicle financing' },
      { value: 'education_loan', label: 'Education Loan', icon: GraduationCap, color: 'purple', description: 'Student / education financing' },
      { value: 'personal_loan', label: 'Personal Loan', icon: Wallet, color: 'amber', description: 'Unsecured personal loan' },
    ],
  },
  {
    id: 'revolving',
    label: 'Credit & Revolving',
    icon: CreditCard,
    color: 'red',
    types: [
      { value: 'credit_card', label: 'Credit Card', icon: CreditCard, color: 'red', description: 'Credit card outstanding' },
    ],
  },
]

const ALL_TYPES = LIABILITY_CATEGORIES.flatMap(c => c.types)
const TYPE_MAP = Object.fromEntries(ALL_TYPES.map(t => [t.value, t]))

function getTypeInfo(typeValue) {
  return TYPE_MAP[typeValue] || { label: typeValue, icon: Wallet, color: 'gray', description: '' }
}

function getCategoryForType(typeValue) {
  return LIABILITY_CATEGORIES.find(c => c.types.some(t => t.value === typeValue)) || LIABILITY_CATEGORIES[0]
}

const colorMap = {
  blue: { bg: 'bg-blue-500/10', text: 'text-blue-400', border: 'border-blue-500/20', ring: 'ring-blue-500/30' },
  indigo: { bg: 'bg-indigo-500/10', text: 'text-indigo-400', border: 'border-indigo-500/20', ring: 'ring-indigo-500/30' },
  purple: { bg: 'bg-purple-500/10', text: 'text-purple-400', border: 'border-purple-500/20', ring: 'ring-purple-500/30' },
  amber: { bg: 'bg-amber-500/10', text: 'text-amber-400', border: 'border-amber-500/20', ring: 'ring-amber-500/30' },
  red: { bg: 'bg-red-500/10', text: 'text-red-400', border: 'border-red-500/20', ring: 'ring-red-500/30' },
  gray: { bg: 'bg-[var(--hover-bg)]', text: 'text-[var(--text-muted)]', border: 'border-[var(--border)]', ring: 'ring-[var(--border)]' },
}

const fmt = (v) => v != null ? `₹${Number(v).toLocaleString('en-IN')}` : '—'


export default function Liabilities() {
  const [activeTab, setActiveTab] = useState('loans')
  const { options: lenders } = useReferenceData('LOAN_LENDER')
  const [totalLiabilities, setTotalLiabilities] = useState(0)
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [localLiabilities, setLocalLiabilities] = useState([])
  const [selectedLiability, setSelectedLiability] = useState(null)
  const [emiSchedule, setEmiSchedule] = useState([])
  const [activeFilter, setActiveFilter] = useState('all')

  const [form, setForm] = useState({
    liabilityType: 'home_loan',
    lender: '',
    originalAmount: '',
    interestRate: '',
    tenureMonths: '',
    startDate: new Date().toISOString().slice(0, 10),
  })

  useEffect(() => {
    client.get('/net-worth/breakdown')
      .then((res) => setTotalLiabilities(res.data.totalLiabilities || 0))
      .catch(() => {})
    client.get('/liabilities')
      .then((res) => setLocalLiabilities(res.data || []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  // Group liabilities by category
  const grouped = useMemo(() => {
    const groups = {}
    for (const cat of LIABILITY_CATEGORIES) {
      const typeValues = cat.types.map(t => t.value)
      const items = localLiabilities.filter(l => typeValues.includes(l.liabilityType))
      if (items.length > 0) {
        groups[cat.id] = {
          ...cat,
          items,
          totalOutstanding: items.reduce((sum, l) => sum + (l.outstandingAmount || l.originalAmount || 0), 0),
          totalEmi: items.reduce((sum, l) => sum + (l.monthlyEmi || 0), 0),
          count: items.length,
        }
      }
    }
    return groups
  }, [localLiabilities])

  const filtered = useMemo(() => {
    if (activeFilter === 'all') return localLiabilities
    const cat = LIABILITY_CATEGORIES.find(c => c.id === activeFilter)
    if (!cat) return localLiabilities
    const typeValues = cat.types.map(t => t.value)
    return localLiabilities.filter(l => typeValues.includes(l.liabilityType))
  }, [localLiabilities, activeFilter])

  const totalMonthlyEmi = localLiabilities.reduce((s, l) => s + (l.monthlyEmi || 0), 0)
  const totalOutstanding = localLiabilities.reduce((s, l) => s + (l.outstandingAmount || l.originalAmount || 0), 0)

  const handleSubmit = async (e) => {
    e.preventDefault()
    try {
      const { data } = await client.post('/liabilities', {
        liabilityType: form.liabilityType,
        lender: form.lender,
        originalAmount: parseFloat(form.originalAmount),
        interestRate: parseFloat(form.interestRate),
        tenureMonths: parseInt(form.tenureMonths),
        startDate: form.startDate,
      })
      setLocalLiabilities(prev => [...prev, data])
      setShowForm(false)
      setForm({ liabilityType: 'home_loan', lender: '', originalAmount: '', interestRate: '', tenureMonths: '', startDate: new Date().toISOString().slice(0, 10) })
      toast.success('Liability added', { description: `${getTypeInfo(data.liabilityType).label} from ${data.lender}` })
      setSelectedLiability(data)
      fetchEmiSchedule(data.id)
    } catch (err) {
      toast.error('Failed to add liability', { description: err.response?.data?.message || err.message })
    }
  }

  const fetchEmiSchedule = async (id) => {
    try {
      const { data } = await client.get(`/liabilities/${id}/emi-schedule`)
      setEmiSchedule(data)
    } catch {
      setEmiSchedule([])
    }
  }

  const handleMarkPaid = async (emi) => {
    if (!selectedLiability) return
    try {
      await client.post(`/liabilities/${selectedLiability.id}/emi-pay`, { paymentDate: emi.dueDate, amount: emi.totalEmi })
      fetchEmiSchedule(selectedLiability.id)
      toast.success('EMI marked as paid')
    } catch (err) {
      toast.error('Failed to mark paid', { description: err.response?.data?.message || err.message })
    }
  }

  const handleDelete = async (id) => {
    if (!confirm('Delete this liability?')) return
    try {
      await client.delete(`/liabilities/${id}`)
      setLocalLiabilities(prev => prev.filter(l => l.id !== id))
      if (selectedLiability?.id === id) { setSelectedLiability(null); setEmiSchedule([]) }
      toast.success('Liability deleted')
    } catch (err) {
      toast.error('Failed to delete', { description: err.response?.data?.message || err.message })
    }
  }

  if (loading) return <div className="flex justify-center py-20 text-[var(--text-muted)]">Loading...</div>

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-[var(--text)]">Liabilities</h1>
          <p className="text-[var(--text-muted)] text-sm mt-1">Manage loans, EMIs, and credit card spends</p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-[var(--bg-card)] rounded-xl p-1.5 border border-[var(--border)]">
        {[
          { id: 'loans', label: 'Loans & EMIs', icon: Home },
          { id: 'credit-cards', label: 'Credit Cards', icon: CreditCard },
        ].map(tab => {
          const Icon = tab.icon
          return (
            <button key={tab.id} onClick={() => setActiveTab(tab.id)}
              className={`flex-1 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg text-sm font-medium transition ${
                activeTab === tab.id ? 'bg-blue-500/20 text-blue-400' : 'text-[var(--text-muted)] hover:text-[var(--text)] hover:bg-[var(--hover-bg)]'
              }`}>
              <Icon className="w-4 h-4" /> {tab.label}
            </button>
          )
        })}
      </div>

      {/* Credit Cards Tab */}
      {activeTab === 'credit-cards' && <CreditCardSpend />}

      {/* Loans Tab */}
      {activeTab === 'loans' && <>

      <div className="flex items-center justify-end">
        <button
          onClick={() => setShowForm(!showForm)}
          className={`px-4 py-2 rounded-lg flex items-center gap-2 text-sm font-medium transition ${showForm ? 'bg-[var(--bg-card)] border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)]' : 'bg-blue-500 hover:bg-blue-600 text-white'}`}
        >
          {showForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
          {showForm ? 'Cancel' : 'Add Liability'}
        </button>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-9 h-9 rounded-lg bg-red-500/10 flex items-center justify-center">
              <TrendingDown className="w-4.5 h-4.5 text-red-400" />
            </div>
            <span className="text-sm text-[var(--text-muted)]">Total Outstanding</span>
          </div>
          <p className="text-2xl font-bold text-red-400">{fmt(totalOutstanding)}</p>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-9 h-9 rounded-lg bg-amber-500/10 flex items-center justify-center">
              <Calendar className="w-4.5 h-4.5 text-amber-400" />
            </div>
            <span className="text-sm text-[var(--text-muted)]">Monthly EMI</span>
          </div>
          <p className="text-2xl font-bold text-amber-400">{fmt(totalMonthlyEmi)}</p>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-9 h-9 rounded-lg bg-blue-500/10 flex items-center justify-center">
              <BarChart3 className="w-4.5 h-4.5 text-blue-400" />
            </div>
            <span className="text-sm text-[var(--text-muted)]">Active Liabilities</span>
          </div>
          <p className="text-2xl font-bold text-[var(--text)]">{localLiabilities.length}</p>
        </div>
      </div>

      {/* Category breakdown pills */}
      {localLiabilities.length > 0 && (
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setActiveFilter('all')}
            className={`px-3.5 py-1.5 rounded-lg text-xs font-medium transition ${activeFilter === 'all' ? 'bg-blue-500/20 text-blue-400 ring-1 ring-blue-500/30' : 'bg-[var(--bg-card)] border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)]'}`}
          >
            All ({localLiabilities.length})
          </button>
          {LIABILITY_CATEGORIES.map(cat => {
            const g = grouped[cat.id]
            if (!g) return null
            const Icon = cat.icon
            const c = colorMap[cat.color]
            return (
              <button
                key={cat.id}
                onClick={() => setActiveFilter(cat.id)}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-medium transition flex items-center gap-1.5 ${activeFilter === cat.id ? `${c.bg} ${c.text} ring-1 ${c.ring}` : 'bg-[var(--bg-card)] border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)]'}`}
              >
                <Icon className="w-3.5 h-3.5" />
                {cat.label} ({g.count})
                <span className="text-[10px] opacity-70">{fmt(g.totalOutstanding)}</span>
              </button>
            )
          })}
        </div>
      )}

      {/* Category-grouped summary cards */}
      {localLiabilities.length > 0 && activeFilter === 'all' && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {Object.values(grouped).map(g => {
            const Icon = g.icon
            const c = colorMap[g.color]
            return (
              <div key={g.id} className={`bg-[var(--bg-card)] rounded-xl p-4 border border-[var(--border)] cursor-pointer hover:border-[var(--text-secondary)] transition`}
                onClick={() => setActiveFilter(g.id)}>
                <div className="flex items-center gap-3 mb-3">
                  <div className={`w-10 h-10 rounded-lg ${c.bg} flex items-center justify-center`}>
                    <Icon className={`w-5 h-5 ${c.text}`} />
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-[var(--text)]">{g.label}</p>
                    <p className="text-xs text-[var(--text-muted)]">{g.count} {g.count === 1 ? 'item' : 'items'}</p>
                  </div>
                </div>
                <div className="flex justify-between text-sm">
                  <div>
                    <p className="text-[var(--text-muted)] text-xs">Outstanding</p>
                    <p className="font-semibold text-red-400">{fmt(g.totalOutstanding)}</p>
                  </div>
                  <div className="text-right">
                    <p className="text-[var(--text-muted)] text-xs">Monthly EMI</p>
                    <p className="font-semibold text-amber-400">{fmt(g.totalEmi)}</p>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Add form */}
      {showForm && (
        <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
          <h3 className="text-lg font-semibold text-[var(--text)] mb-1">Add New Liability</h3>
          <p className="text-xs text-[var(--text-muted)] mb-5">Select the type and fill in the details</p>

          {/* Type selector cards */}
          <div className="mb-5">
            <label className="block text-sm font-medium text-[var(--text)] mb-2">Type</label>
            <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-2">
              {ALL_TYPES.map(t => {
                const Icon = t.icon
                const c = colorMap[t.color]
                const selected = form.liabilityType === t.value
                return (
                  <button key={t.value} type="button"
                    onClick={() => setForm({ ...form, liabilityType: t.value })}
                    className={`flex flex-col items-center gap-1.5 p-3 rounded-xl text-xs font-medium transition border ${selected ? `${c.bg} ${c.text} ${c.border} ring-1 ${c.ring}` : 'bg-[var(--bg)] border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)] hover:border-[var(--text-secondary)]'}`}
                  >
                    <Icon className="w-5 h-5" />
                    {t.label}
                  </button>
                )
              })}
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-[var(--text-muted)] mb-1">Lender</label>
                <select value={form.lender} onChange={(e) => setForm({ ...form, lender: e.target.value })}
                  className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5 text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50" required>
                  <option value="">Select lender...</option>
                  {lenders.map(l => <option key={l.value} value={l.value}>{l.label}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm text-[var(--text-muted)] mb-1">Original Amount</label>
                <div className="relative">
                  <IndianRupee className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]" />
                  <input type="number" step="any" value={form.originalAmount} onChange={(e) => setForm({ ...form, originalAmount: e.target.value })}
                    className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg pl-9 pr-3 py-2.5 text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50"
                    placeholder="500000" required />
                </div>
              </div>
              <div>
                <label className="block text-sm text-[var(--text-muted)] mb-1">Interest Rate</label>
                <div className="relative">
                  <Percent className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]" />
                  <input type="number" step="any" value={form.interestRate} onChange={(e) => setForm({ ...form, interestRate: e.target.value })}
                    className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg pl-9 pr-3 py-2.5 text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50"
                    placeholder="8.5" required />
                </div>
              </div>
              <div>
                <label className="block text-sm text-[var(--text-muted)] mb-1">Tenure (months)</label>
                <div className="relative">
                  <Clock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]" />
                  <input type="number" value={form.tenureMonths} onChange={(e) => setForm({ ...form, tenureMonths: e.target.value })}
                    className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg pl-9 pr-3 py-2.5 text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50"
                    placeholder="240" required />
                </div>
              </div>
              <div>
                <label className="block text-sm text-[var(--text-muted)] mb-1">Start Date</label>
                <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })}
                  className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5 text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50"
                  required />
              </div>
            </div>
            <div className="flex justify-end gap-3 pt-2">
              <button type="button" onClick={() => setShowForm(false)}
                className="px-4 py-2 bg-[var(--bg)] border border-[var(--border)] rounded-lg text-sm text-[var(--text-muted)] hover:text-[var(--text)] transition">
                Cancel
              </button>
              <button type="submit"
                className="px-6 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg text-sm font-medium transition">
                Add Liability
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Liability cards */}
      {filtered.length > 0 && (
        <div className="space-y-3">
          {activeFilter !== 'all' && (
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-semibold text-[var(--text)]">
                {LIABILITY_CATEGORIES.find(c => c.id === activeFilter)?.label || 'Liabilities'}
              </h3>
              <button onClick={() => setActiveFilter('all')} className="text-xs text-[var(--primary)] hover:underline">Show all</button>
            </div>
          )}
          {filtered.map(liability => {
            const info = getTypeInfo(liability.liabilityType)
            const Icon = info.icon
            const c = colorMap[info.color]
            const outstanding = liability.outstandingAmount || liability.originalAmount || 0
            const original = liability.originalAmount || 0
            const paidPct = original > 0 ? Math.min(100, ((original - outstanding) / original) * 100) : 0
            const isSelected = selectedLiability?.id === liability.id

            return (
              <div key={liability.id}
                className={`bg-[var(--bg-card)] rounded-xl border transition ${isSelected ? 'border-blue-500/50 ring-1 ring-blue-500/20' : 'border-[var(--border)] hover:border-[var(--text-secondary)]'}`}>
                <div className="p-4">
                  <div className="flex items-start gap-3">
                    <div className={`w-10 h-10 rounded-lg ${c.bg} flex items-center justify-center shrink-0`}>
                      <Icon className={`w-5 h-5 ${c.text}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="font-semibold text-[var(--text)]">{liability.lender}</p>
                          <span className={`text-xs ${c.text} ${c.bg} px-2 py-0.5 rounded-full`}>{info.label}</span>
                        </div>
                        <div className="flex items-center gap-1.5">
                          <button
                            onClick={() => { setSelectedLiability(isSelected ? null : liability); if (!isSelected) fetchEmiSchedule(liability.id); else setEmiSchedule([]); }}
                            className="text-xs text-blue-400 hover:text-blue-300 px-2 py-1 rounded hover:bg-blue-500/10 transition">
                            {isSelected ? 'Hide' : 'View'} EMIs
                          </button>
                          <button onClick={() => handleDelete(liability.id)}
                            className="text-[var(--text-secondary)] hover:text-red-400 p-1 rounded hover:bg-red-500/10 transition">
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </div>
                      </div>

                      {/* Stats row */}
                      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-3">
                        <div>
                          <p className="text-[10px] uppercase tracking-wider text-[var(--text-muted)]">Original</p>
                          <p className="text-sm font-semibold text-[var(--text)]">{fmt(liability.originalAmount)}</p>
                        </div>
                        <div>
                          <p className="text-[10px] uppercase tracking-wider text-[var(--text-muted)]">Outstanding</p>
                          <p className="text-sm font-semibold text-red-400">{fmt(outstanding)}</p>
                        </div>
                        <div>
                          <p className="text-[10px] uppercase tracking-wider text-[var(--text-muted)]">Monthly EMI</p>
                          <p className="text-sm font-semibold text-amber-400">{fmt(liability.monthlyEmi)}</p>
                        </div>
                        <div>
                          <p className="text-[10px] uppercase tracking-wider text-[var(--text-muted)]">Interest Rate</p>
                          <p className="text-sm font-semibold text-[var(--text)]">{liability.interestRate}%</p>
                        </div>
                      </div>

                      {/* Repayment progress bar */}
                      <div className="mt-3">
                        <div className="flex items-center justify-between text-[10px] text-[var(--text-muted)] mb-1">
                          <span>Repayment Progress</span>
                          <span>{paidPct.toFixed(1)}% paid</span>
                        </div>
                        <div className="h-1.5 bg-[var(--border)] rounded-full overflow-hidden">
                          <div className="h-full bg-green-500 rounded-full transition-all" style={{ width: `${paidPct}%` }} />
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                {/* EMI Schedule (expanded) */}
                {isSelected && emiSchedule.length > 0 && (
                  <div className="border-t border-[var(--border)]">
                    <div className="px-4 py-3 flex items-center justify-between bg-[var(--bg)]/50">
                      <p className="text-sm font-semibold text-[var(--text)]">EMI Schedule</p>
                      <button onClick={() => { setSelectedLiability(null); setEmiSchedule([]) }}
                        className="text-[var(--text-secondary)] hover:text-[var(--text)]">
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                    <div className="max-h-80 overflow-y-auto">
                      <table className="w-full text-sm">
                        <thead className="bg-[var(--input-bg)] sticky top-0">
                          <tr>
                            <th className="px-4 py-2 text-left text-xs font-medium text-[var(--text-muted)]">#</th>
                            <th className="px-4 py-2 text-left text-xs font-medium text-[var(--text-muted)]">Due Date</th>
                            <th className="px-4 py-2 text-right text-xs font-medium text-[var(--text-muted)]">Principal</th>
                            <th className="px-4 py-2 text-right text-xs font-medium text-[var(--text-muted)]">Interest</th>
                            <th className="px-4 py-2 text-right text-xs font-medium text-[var(--text-muted)]">Total</th>
                            <th className="px-4 py-2 text-center text-xs font-medium text-[var(--text-muted)]">Status</th>
                            <th className="px-4 py-2"></th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-[var(--border)]">
                          {emiSchedule.map(emi => (
                            <tr key={emi.id} className={`${emi.paid ? 'bg-green-500/5' : ''} hover:bg-[var(--hover-bg)]`}>
                              <td className="px-4 py-2 text-[var(--text-muted)]">{emi.emiNumber}</td>
                              <td className="px-4 py-2">{new Date(emi.dueDate).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })}</td>
                              <td className="px-4 py-2 text-right text-blue-400">{fmt(emi.principal)}</td>
                              <td className="px-4 py-2 text-right text-amber-400">{fmt(emi.interest)}</td>
                              <td className="px-4 py-2 text-right font-medium">{fmt(emi.totalEmi)}</td>
                              <td className="px-4 py-2 text-center">
                                {emi.paid ? (
                                  <span className="inline-flex items-center gap-1 text-green-400 text-xs"><CheckCircle className="w-3.5 h-3.5" /> Paid</span>
                                ) : (
                                  <span className="inline-flex items-center gap-1 text-[var(--text-muted)] text-xs"><XCircle className="w-3.5 h-3.5" /> Due</span>
                                )}
                              </td>
                              <td className="px-4 py-2 text-right">
                                {!emi.paid && (
                                  <button onClick={() => handleMarkPaid(emi)}
                                    className="px-2.5 py-1 bg-green-500/15 text-green-400 rounded text-xs hover:bg-green-500/25 transition">
                                    Pay
                                  </button>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {/* Empty state */}
      {localLiabilities.length === 0 && !showForm && (
        <div className="bg-[var(--bg-card)] rounded-xl p-16 border border-[var(--border)] text-center">
          <div className="w-16 h-16 rounded-2xl bg-green-500/10 flex items-center justify-center mx-auto mb-4">
            <CheckCircle className="w-8 h-8 text-green-400" />
          </div>
          <p className="text-lg font-semibold text-[var(--text)] mb-1">You're debt free!</p>
          <p className="text-sm text-[var(--text-muted)] mb-6">No liabilities tracked yet. Add loans or credit cards to monitor repayment.</p>
          <button onClick={() => setShowForm(true)}
            className="px-5 py-2.5 bg-blue-500 hover:bg-blue-600 text-white rounded-lg text-sm font-medium transition inline-flex items-center gap-2">
            <Plus className="w-4 h-4" /> Add Your First Liability
          </button>
        </div>
      )}

      </>}
    </div>
  )
}
