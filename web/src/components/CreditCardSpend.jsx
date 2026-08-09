import { useState, useEffect, useMemo, useRef } from 'react'
import client from '../api/client'
import { toast } from 'sonner'
import { CheckCircle, Upload, FileText, Loader2, Receipt, Banknote, ArrowUpRight, ArrowDownRight, BarChart3 } from 'lucide-react'
import { PieChart as RechartsPie, Pie, Cell, Sector, ResponsiveContainer, Tooltip, BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts'
import { dateInputValue } from '../utils/format'

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

const fmt = (v) => v != null ? `₹${Number(v).toLocaleString('en-IN')}` : '—'

export default function CreditCardSpend() {
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

  const loadSpendHistory = () => {
    client.get('/liabilities/cc-spend/reports').then(r => setSpendReports(r.data || [])).catch(() => {})
    client.get('/liabilities/cc-spend/trend').then(r => setSpendTrend(r.data || [])).catch(() => {})
  }

  useEffect(() => {
    loadSpendHistory()
  }, [])

  const loadMonthDetail = async (month) => {
    setSelectedMonth(month)
    setLoadingMonth(true)
    try {
      const { data } = await client.get(`/liabilities/cc-spend/month/${month}`)
      setMonthAnalysis(data)
    } catch { setMonthAnalysis(null) }
    finally { setLoadingMonth(false) }
  }

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
        paidDate: dateInputValue(),
        paymentMode: mode.toUpperCase() || 'ONLINE',
      })
      toast.success('Bill marked as paid', { description: `${report.cardIssuer} ****${report.cardLastFour} — ${fmt(report.totalAmountDue)} via ${mode.toUpperCase()}` })
      loadSpendHistory()
    } catch (err) {
      toast.error('Failed to mark as paid', { description: err.response?.data?.message || err.message })
    }
  }

  return (
    <>
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
                          innerRadius={50} outerRadius={85} paddingAngle={1} strokeWidth={0}
                          activeShape={(props) => <Sector {...props} outerRadius={props.outerRadius + 6} />}>
                          {ccSpendData.map((d, i) => <Cell key={i} fill={d.color} cursor="pointer" />)}
                        </Pie>
                        <Tooltip formatter={(v) => fmt(v)} contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 8, color: 'var(--text)' }}
                          itemStyle={{ color: 'var(--text)' }} />
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
                    <XAxis dataKey="month" tick={{ fill: 'var(--text-muted)', fontSize: 10 }} tickFormatter={m => { const [,mo] = (m||'').split('-'); return ['','Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][+mo] || m }} />
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
    </>
  )
}
