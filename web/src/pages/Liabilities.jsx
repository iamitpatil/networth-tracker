import { useState, useEffect, useMemo, useRef } from 'react'
import client from '../api/client'
import { toast } from 'sonner'
import { Plus, Trash2, CheckCircle, XCircle, X, Home, Car, GraduationCap, CreditCard, Wallet, ChevronDown, ChevronUp, Calendar, Percent, Clock, IndianRupee, TrendingDown, BarChart3, Upload, FileText, PieChart, Loader2, Receipt, Banknote, ArrowUpRight, ArrowDownRight, Eye } from 'lucide-react'
import { PieChart as RechartsPie, Pie, Cell, ResponsiveContainer, Tooltip, BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts'
import { useReferenceData } from '../hooks/useReferenceData'

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

const SPEND_CATEGORIES = {
  FOOD: { label: 'Food & Dining', color: '#F97316', icon: '🍔' },
  SHOPPING: { label: 'Shopping', color: '#8B5CF6', icon: '🛍️' },
  TRAVEL: { label: 'Travel', color: '#3B82F6', icon: '✈️' },
  FUEL: { label: 'Fuel', color: '#EF4444', icon: '⛽' },
  ENTERTAINMENT: { label: 'Entertainment', color: '#EC4899', icon: '🎬' },
  UTILITIES: { label: 'Utilities', color: '#14B8A6', icon: '💡' },
  GROCERIES: { label: 'Groceries', color: '#22C55E', icon: '🛒' },
  HEALTH: { label: 'Health', color: '#EF4444', icon: '🏥' },
  EDUCATION: { label: 'Education', color: '#6366F1', icon: '📚' },
  EMI: { label: 'EMI / Loan', color: '#F59E0B', icon: '🏦' },
  SUBSCRIPTION: { label: 'Subscriptions', color: '#A855F7', icon: '📱' },
  INSURANCE: { label: 'Insurance', color: '#0EA5E9', icon: '🛡️' },
  TRANSFER: { label: 'Transfers', color: '#64748B', icon: '↔️' },
  OTHER: { label: 'Other', color: '#94A3B8', icon: '📋' },
}

export default function Liabilities() {
  const { options: lenders } = useReferenceData('LOAN_LENDER')
  const [totalLiabilities, setTotalLiabilities] = useState(0)
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [localLiabilities, setLocalLiabilities] = useState([])
  const [selectedLiability, setSelectedLiability] = useState(null)
  const [emiSchedule, setEmiSchedule] = useState([])
  const [activeFilter, setActiveFilter] = useState('all')
  const [ccBillParsing, setCcBillParsing] = useState(false)
  const [ccBillResult, setCcBillResult] = useState(null)
  const [showCcAnalysis, setShowCcAnalysis] = useState(false)
  const [ccPasswordNeeded, setCcPasswordNeeded] = useState(false)
  const [ccPassword, setCcPassword] = useState('')
  const [ccPendingFile, setCcPendingFile] = useState(null)
  const ccFileRef = useRef(null)
  const [spendReports, setSpendReports] = useState([])
  const [spendTrend, setSpendTrend] = useState([])
  const [selectedMonth, setSelectedMonth] = useState(null)
  const [monthAnalysis, setMonthAnalysis] = useState(null)
  const [loadingMonth, setLoadingMonth] = useState(false)
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
    // Load spend history
    loadSpendHistory()
  }, [])

  const loadSpendHistory = () => {
    client.get('/liabilities/cc-spend/reports').then(r => setSpendReports(r.data || [])).catch(() => {})
    client.get('/liabilities/cc-spend/trend').then(r => setSpendTrend(r.data || [])).catch(() => {})
  }

  const loadMonthDetail = async (month) => {
    setSelectedMonth(month)
    setLoadingMonth(true)
    try {
      const { data } = await client.get(`/liabilities/cc-spend/month/${month}`)
      setMonthAnalysis(data)
    } catch { setMonthAnalysis(null) }
    finally { setLoadingMonth(false) }
  }

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

  const uploadCcBill = async (file, password) => {
    setCcBillParsing(true)
    setCcBillResult(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      if (password) formData.append('password', password)
      const { data } = await client.post('/liabilities/parse-cc-bill', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      if (data.error === 'PASSWORD_REQUIRED') {
        setCcPendingFile(file)
        setCcPasswordNeeded(true)
        setCcPassword('')
        toast.warning('Password protected PDF', { description: 'Please enter the PDF password to continue' })
        return
      }
      setCcBillResult(data)
      setShowCcAnalysis(true)
      setCcPasswordNeeded(false)
      setCcPendingFile(null)
      setCcPassword('')
      // Auto-save to spend history
      try {
        await client.post('/liabilities/cc-spend/save', data)
        loadSpendHistory()
      } catch { /* silently fail — analysis still shown */ }
      toast.success('Bill parsed & saved', { description: `${data.cardIssuer || 'Credit card'} — ${(data.transactions || []).length} transactions found` })
    } catch (err) {
      const errData = err.response?.data
      if (errData?.error === 'PASSWORD_REQUIRED') {
        setCcPendingFile(file)
        setCcPasswordNeeded(true)
        setCcPassword('')
        toast.warning('Password protected PDF', { description: 'Please enter the PDF password to continue' })
      } else {
        toast.error('Failed to parse bill', { description: errData?.message || err.message })
      }
    } finally {
      setCcBillParsing(false)
    }
  }

  const handleCcBillUpload = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    if (!file.name.match(/\.(pdf|csv|txt)$/i)) {
      toast.error('Unsupported file', { description: 'Please upload a PDF, CSV, or text file' })
      if (ccFileRef.current) ccFileRef.current.value = ''
      return
    }
    await uploadCcBill(file, null)
    if (ccFileRef.current) ccFileRef.current.value = ''
  }

  const handleCcPasswordSubmit = async (e) => {
    e.preventDefault()
    if (!ccPendingFile || !ccPassword.trim()) return
    await uploadCcBill(ccPendingFile, ccPassword)
  }

  const cancelCcPassword = () => {
    setCcPasswordNeeded(false)
    setCcPendingFile(null)
    setCcPassword('')
  }

  const ccSpendData = useMemo(() => {
    if (!ccBillResult?.spendSummary) return []
    return Object.entries(ccBillResult.spendSummary)
      .map(([cat, amount]) => ({
        name: SPEND_CATEGORIES[cat]?.label || cat,
        value: Math.abs(Number(amount)),
        color: SPEND_CATEGORIES[cat]?.color || '#94A3B8',
        icon: SPEND_CATEGORIES[cat]?.icon || '📋',
        category: cat,
      }))
      .filter(d => d.value > 0)
      .sort((a, b) => b.value - a.value)
  }, [ccBillResult])

  const handlePayBill = async (report) => {
    const mode = await new Promise(resolve => {
      const m = prompt(`Mark bill as paid?\n\n${report.cardIssuer || 'Credit Card'} ****${report.cardLastFour || ''}\nAmount: ₹${Number(report.totalAmountDue || 0).toLocaleString('en-IN')}\n\nEnter payment mode (UPI / NEFT / AUTO_DEBIT / ONLINE):`, 'UPI')
      resolve(m)
    })
    if (mode === null) return
    try {
      await client.post(`/liabilities/cc-spend/${report.id}/pay`, {
        paidAmount: report.totalAmountDue,
        paidDate: new Date().toISOString().slice(0, 10),
        paymentMode: mode.toUpperCase() || 'ONLINE',
      })
      toast.success('Bill marked as paid', { description: `${report.cardIssuer} ****${report.cardLastFour} — ${fmt(report.totalAmountDue)} via ${mode.toUpperCase()}` })
      loadSpendHistory()
    } catch (err) {
      toast.error('Failed to mark as paid', { description: err.response?.data?.message || err.message })
    }
  }

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
          <p className="text-[var(--text-muted)] text-sm mt-1">Manage loans, EMIs, and credit obligations</p>
        </div>
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

      {/* Credit Card Bill Analysis */}
      <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
        <div className="p-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-purple-500/10 flex items-center justify-center">
              <Receipt className="w-5 h-5 text-purple-400" />
            </div>
            <div>
              <p className="font-semibold text-[var(--text)]">Credit Card Bill Analysis</p>
              <p className="text-xs text-[var(--text-muted)]">Upload a PDF/CSV statement to analyze spending patterns</p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {ccBillResult && (
              <button onClick={() => setShowCcAnalysis(!showCcAnalysis)}
                className="text-xs text-[var(--primary)] hover:underline">
                {showCcAnalysis ? 'Hide' : 'Show'} Analysis
              </button>
            )}
            <input ref={ccFileRef} type="file" accept=".pdf,.csv,.txt" onChange={handleCcBillUpload} className="hidden" />
            <button onClick={() => ccFileRef.current?.click()} disabled={ccBillParsing}
              className="px-4 py-2 bg-purple-500 hover:bg-purple-600 disabled:opacity-60 text-white rounded-lg text-sm font-medium transition inline-flex items-center gap-2">
              {ccBillParsing ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
              {ccBillParsing ? 'Analyzing...' : 'Upload Bill'}
            </button>
          </div>
        </div>

        {/* Password prompt for protected PDFs */}
        {ccPasswordNeeded && (
          <div className="border-t border-[var(--border)] p-4">
            <form onSubmit={handleCcPasswordSubmit} className="flex items-end gap-3">
              <div className="flex-1">
                <div className="flex items-center gap-2 mb-2">
                  <FileText className="w-4 h-4 text-amber-400" />
                  <p className="text-sm font-medium text-amber-400">Password protected PDF</p>
                </div>
                <p className="text-xs text-[var(--text-muted)] mb-2">This PDF is encrypted. Enter the password to unlock and parse it.</p>
                <input
                  type="password"
                  value={ccPassword}
                  onChange={(e) => setCcPassword(e.target.value)}
                  placeholder="Enter PDF password"
                  autoFocus
                  className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5 text-sm text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-purple-500/50 focus:border-purple-500"
                />
              </div>
              <button type="submit" disabled={ccBillParsing || !ccPassword.trim()}
                className="px-4 py-2.5 bg-purple-500 hover:bg-purple-600 disabled:opacity-60 text-white rounded-lg text-sm font-medium transition inline-flex items-center gap-2 shrink-0">
                {ccBillParsing ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                {ccBillParsing ? 'Parsing...' : 'Unlock & Parse'}
              </button>
              <button type="button" onClick={cancelCcPassword}
                className="px-3 py-2.5 bg-[var(--bg)] border border-[var(--border)] rounded-lg text-sm text-[var(--text-muted)] hover:text-[var(--text)] transition shrink-0">
                Cancel
              </button>
            </form>
          </div>
        )}

        {showCcAnalysis && ccBillResult && (
          <div className="border-t border-[var(--border)] p-5 space-y-5">
            {/* Bill summary */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
              <div className="bg-[var(--bg)] rounded-lg p-3">
                <p className="text-[10px] uppercase tracking-wider text-[var(--text-muted)]">Card</p>
                <p className="text-sm font-semibold text-[var(--text)]">{ccBillResult.cardIssuer || '—'}</p>
                {ccBillResult.cardLastFourDigits && <p className="text-xs text-[var(--text-muted)]">****{ccBillResult.cardLastFourDigits}</p>}
              </div>
              <div className="bg-[var(--bg)] rounded-lg p-3">
                <p className="text-[10px] uppercase tracking-wider text-[var(--text-muted)]">Total Due</p>
                <p className="text-sm font-semibold text-red-400">{fmt(ccBillResult.totalAmountDue)}</p>
              </div>
              <div className="bg-[var(--bg)] rounded-lg p-3">
                <p className="text-[10px] uppercase tracking-wider text-[var(--text-muted)]">Min Due</p>
                <p className="text-sm font-semibold text-amber-400">{fmt(ccBillResult.minimumAmountDue)}</p>
              </div>
              <div className="bg-[var(--bg)] rounded-lg p-3">
                <p className="text-[10px] uppercase tracking-wider text-[var(--text-muted)]">Due Date</p>
                <p className="text-sm font-semibold text-[var(--text)]">{ccBillResult.dueDate ? new Date(ccBillResult.dueDate).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }) : '—'}</p>
              </div>
            </div>

            {/* Spend breakdown chart + list */}
            {ccSpendData.length > 0 && (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                {/* Pie chart */}
                <div>
                  <p className="text-sm font-semibold text-[var(--text)] mb-3">Spend Breakdown</p>
                  <div className="h-56">
                    <ResponsiveContainer width="100%" height="100%">
                      <RechartsPie>
                        <Pie data={ccSpendData} dataKey="value" nameKey="name" cx="50%" cy="50%"
                          innerRadius={50} outerRadius={85} paddingAngle={2}>
                          {ccSpendData.map((d, i) => <Cell key={i} fill={d.color} />)}
                        </Pie>
                        <Tooltip formatter={(v) => fmt(v)} contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} />
                      </RechartsPie>
                    </ResponsiveContainer>
                  </div>
                </div>
                {/* Category list */}
                <div>
                  <p className="text-sm font-semibold text-[var(--text)] mb-3">By Category</p>
                  <div className="space-y-2 max-h-56 overflow-y-auto">
                    {ccSpendData.map((d, i) => {
                      const pct = ccBillResult.totalAmountDue > 0 ? (d.value / ccBillResult.totalAmountDue * 100) : 0
                      return (
                        <div key={i} className="flex items-center gap-3">
                          <span className="text-lg">{d.icon}</span>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center justify-between">
                              <p className="text-sm text-[var(--text)]">{d.name}</p>
                              <p className="text-sm font-semibold text-[var(--text)]">{fmt(d.value)}</p>
                            </div>
                            <div className="h-1 bg-[var(--border)] rounded-full mt-1 overflow-hidden">
                              <div className="h-full rounded-full" style={{ width: `${Math.min(100, pct)}%`, backgroundColor: d.color }} />
                            </div>
                          </div>
                          <span className="text-xs text-[var(--text-muted)] w-10 text-right">{pct.toFixed(0)}%</span>
                        </div>
                      )
                    })}
                  </div>
                </div>
              </div>
            )}

            {/* Transaction list */}
            {ccBillResult.transactions?.length > 0 && (
              <div>
                <p className="text-sm font-semibold text-[var(--text)] mb-3">Transactions ({ccBillResult.transactions.length})</p>
                <div className="max-h-72 overflow-y-auto rounded-lg border border-[var(--border)]">
                  <table className="w-full text-sm">
                    <thead className="bg-[var(--input-bg)] sticky top-0">
                      <tr>
                        <th className="px-3 py-2 text-left text-xs font-medium text-[var(--text-muted)]">Date</th>
                        <th className="px-3 py-2 text-left text-xs font-medium text-[var(--text-muted)]">Description</th>
                        <th className="px-3 py-2 text-left text-xs font-medium text-[var(--text-muted)]">Category</th>
                        <th className="px-3 py-2 text-right text-xs font-medium text-[var(--text-muted)]">Amount</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--border)]">
                      {ccBillResult.transactions.map((tx, i) => {
                        const cat = SPEND_CATEGORIES[tx.category] || SPEND_CATEGORIES.OTHER
                        const isCredit = tx.amount < 0
                        return (
                          <tr key={i} className="hover:bg-[var(--hover-bg)]">
                            <td className="px-3 py-2 text-[var(--text-muted)] whitespace-nowrap">{tx.date ? new Date(tx.date).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }) : '—'}</td>
                            <td className="px-3 py-2 text-[var(--text)] truncate max-w-[200px]">{tx.description}</td>
                            <td className="px-3 py-2">
                              <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded-full" style={{ backgroundColor: cat.color + '15', color: cat.color }}>
                                {cat.icon} {cat.label}
                              </span>
                            </td>
                            <td className={`px-3 py-2 text-right font-medium ${isCredit ? 'text-green-400' : 'text-[var(--text)]'}`}>
                              {isCredit ? '+' : ''}{fmt(Math.abs(tx.amount))}
                            </td>
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Spend History — Cred-like monthly tracker */}
      {spendReports.length > 0 && (
        <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
          <div className="p-4 flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-lg bg-indigo-500/10 flex items-center justify-center">
                <BarChart3 className="w-5 h-5 text-indigo-400" />
              </div>
              <div>
                <p className="font-semibold text-[var(--text)]">Monthly Spend History</p>
                <p className="text-xs text-[var(--text-muted)]">{spendReports.length} statement{spendReports.length !== 1 ? 's' : ''} tracked</p>
              </div>
            </div>
          </div>

          {/* Monthly trend bar chart */}
          {spendTrend.length > 1 && (
            <div className="px-4 pb-2">
              <div className="h-40">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={[...spendTrend].reverse()}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis dataKey="month" tick={{ fill: 'var(--text-muted)', fontSize: 10 }} tickFormatter={m => { const [y,mo] = (m||'').split('-'); return ['','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][+mo] || m }} />
                    <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 10 }} tickFormatter={v => v >= 100000 ? `${(v/100000).toFixed(1)}L` : v >= 1000 ? `${(v/1000).toFixed(0)}K` : v} width={45} />
                    <Tooltip formatter={(v) => fmt(v)} contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }} labelFormatter={m => { const [y,mo] = (m||'').split('-'); return `${['','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][+mo]} ${y}` }} />
                    <Bar dataKey="total" fill="#6366F1" radius={[4,4,0,0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* Bill cards */}
          <div className="border-t border-[var(--border)] divide-y divide-[var(--border)]">
            {spendReports.map(report => {
              const isPaid = report.paid === true
              const isOverdue = !isPaid && report.dueDate && new Date(report.dueDate) < new Date()
              const isExpanded = selectedMonth === report.statementMonth
              return (
                <div key={report.id} className={`${isPaid ? 'bg-green-500/[0.02]' : isOverdue ? 'bg-red-500/[0.03]' : ''}`}>
                  <div className="p-4 flex items-center gap-4 cursor-pointer hover:bg-[var(--hover-bg)] transition"
                    onClick={() => isExpanded ? setSelectedMonth(null) : loadMonthDetail(report.statementMonth)}>
                    {/* Month circle */}
                    <div className={`w-12 h-12 rounded-xl flex flex-col items-center justify-center shrink-0 ${isPaid ? 'bg-green-500/10' : isOverdue ? 'bg-red-500/10' : 'bg-indigo-500/10'}`}>
                      <span className={`text-xs font-bold ${isPaid ? 'text-green-400' : isOverdue ? 'text-red-400' : 'text-indigo-400'}`}>
                        {['','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][+(report.statementMonth||'').split('-')[1]] || '?'}
                      </span>
                      <span className="text-[10px] text-[var(--text-muted)]">{(report.statementMonth||'').split('-')[0]}</span>
                    </div>

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <p className="text-sm font-semibold text-[var(--text)]">{report.cardIssuer || 'Credit Card'}</p>
                        {report.cardLastFour && <span className="text-xs text-[var(--text-muted)]">****{report.cardLastFour}</span>}
                        {isPaid && <span className="text-[10px] bg-green-500/15 text-green-400 px-1.5 py-0.5 rounded-full font-medium">PAID</span>}
                        {isOverdue && <span className="text-[10px] bg-red-500/15 text-red-400 px-1.5 py-0.5 rounded-full font-medium">OVERDUE</span>}
                      </div>
                      <div className="flex items-center gap-4 mt-0.5">
                        <span className="text-xs text-[var(--text-muted)]">Due: {report.dueDate ? new Date(report.dueDate).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' }) : '—'}</span>
                        {report.transactions && <span className="text-xs text-[var(--text-muted)]">{Array.isArray(report.transactions) ? report.transactions.length : 0} txns</span>}
                      </div>
                    </div>

                    <div className="text-right shrink-0">
                      <p className={`text-sm font-bold ${isPaid ? 'text-green-400 line-through opacity-60' : 'text-[var(--text)]'}`}>{fmt(report.totalAmountDue)}</p>
                      {isPaid && report.paidAmount && <p className="text-xs text-green-400">Paid {fmt(report.paidAmount)}</p>}
                    </div>

                    <div className="shrink-0">
                      {!isPaid ? (
                        <button onClick={(e) => { e.stopPropagation(); handlePayBill(report) }}
                          className="px-3 py-1.5 bg-green-500 hover:bg-green-600 text-white text-xs font-medium rounded-lg transition inline-flex items-center gap-1">
                          <Banknote className="w-3.5 h-3.5" /> Pay
                        </button>
                      ) : (
                        <CheckCircle className="w-5 h-5 text-green-400" />
                      )}
                    </div>
                  </div>

                  {/* Expanded month detail */}
                  {isExpanded && monthAnalysis && !loadingMonth && (
                    <div className="px-4 pb-4 space-y-3">
                      {/* Category spend bars */}
                      {monthAnalysis.categories && Object.keys(monthAnalysis.categories).length > 0 && (
                        <div className="grid grid-cols-2 gap-2">
                          {Object.entries(monthAnalysis.categories)
                            .sort(([,a],[,b]) => Number(b) - Number(a))
                            .map(([cat, amt]) => {
                              const info = SPEND_CATEGORIES[cat] || SPEND_CATEGORIES.OTHER
                              const pct = monthAnalysis.totalSpend > 0 ? (Number(amt) / Number(monthAnalysis.totalSpend) * 100) : 0
                              return (
                                <div key={cat} className="flex items-center gap-2 bg-[var(--bg)] rounded-lg p-2">
                                  <span className="text-sm">{info.icon}</span>
                                  <div className="flex-1 min-w-0">
                                    <div className="flex justify-between text-xs">
                                      <span className="text-[var(--text-muted)]">{info.label}</span>
                                      <span className="font-medium text-[var(--text)]">{fmt(amt)}</span>
                                    </div>
                                    <div className="h-1 bg-[var(--border)] rounded-full mt-1">
                                      <div className="h-full rounded-full" style={{ width: `${Math.min(100, pct)}%`, backgroundColor: info.color }} />
                                    </div>
                                  </div>
                                </div>
                              )
                            })}
                        </div>
                      )}
                      {/* MoM change + top merchants */}
                      <div className="flex items-center gap-4 text-xs">
                        {monthAnalysis.monthOverMonthChange != null && (
                          <span className={`inline-flex items-center gap-1 px-2 py-1 rounded-full ${Number(monthAnalysis.monthOverMonthChange) > 0 ? 'bg-red-500/10 text-red-400' : 'bg-green-500/10 text-green-400'}`}>
                            {Number(monthAnalysis.monthOverMonthChange) > 0 ? <ArrowUpRight className="w-3 h-3" /> : <ArrowDownRight className="w-3 h-3" />}
                            {Math.abs(Number(monthAnalysis.monthOverMonthChange))}% vs last month
                          </span>
                        )}
                        {monthAnalysis.topMerchants?.length > 0 && (
                          <span className="text-[var(--text-muted)]">Top: {monthAnalysis.topMerchants.slice(0,3).map(m => m.merchant).join(', ')}</span>
                        )}
                      </div>
                    </div>
                  )}
                  {isExpanded && loadingMonth && (
                    <div className="flex justify-center py-4"><Loader2 className="w-5 h-5 animate-spin text-[var(--text-muted)]" /></div>
                  )}
                </div>
              )
            })}
          </div>
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
    </div>
  )
}
