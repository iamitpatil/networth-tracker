import { useState, useEffect, useMemo, useRef } from 'react'
import { toast } from 'sonner'
import client from '../api/client'
import { useFamilyView } from '../context/FamilyViewContext'
import { Plus, Trash2, TrendingUp, TrendingDown, Search, X, Loader2, Building2, Landmark, Banknote, PiggyBank, ShieldCheck, Gem, FileText, Download, Upload, Eye, Users, ChevronRight, ChevronDown as ChevronDownIcon, Newspaper, IndianRupee, Calendar } from 'lucide-react'
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, AreaChart, Area, XAxis, YAxis, CartesianGrid } from 'recharts'
import NewsPanel from '../components/NewsPanel'
import UpstoxSync from '../components/UpstoxSync'
import { useFeature } from '../context/FeatureFlagContext'
import { createChart, CandlestickSeries, AreaSeries } from 'lightweight-charts'

const COLORS = ['#3b82f6', '#22c55e', '#f59e0b', '#a855f7', '#ef4444', '#06b6d4', '#14b8a6', '#f97316']

const ASSET_TYPES = [
  { value: 'EQUITY', label: 'Stocks', icon: TrendingUp, color: 'blue' },
  { value: 'MUTUAL_FUND', label: 'Mutual Funds', icon: TrendingUp, color: 'green' },
  { value: 'BOND', label: 'Bonds', icon: Landmark, color: 'cyan' },
  { value: 'GOLD', label: 'Gold / SGB', icon: Gem, color: 'amber' },
  { value: 'FD', label: 'Fixed Deposit', icon: Banknote, color: 'purple' },
  { value: 'PPF', label: 'PPF', icon: ShieldCheck, color: 'teal' },
  { value: 'EPF', label: 'EPF', icon: PiggyBank, color: 'orange' },
  { value: 'NPS', label: 'NPS', icon: Landmark, color: 'pink' },
]

const TYPE_TRANSACTIONS = {
  EQUITY: ['BUY', 'SELL', 'SIP', 'LUMPSUM'],
  MUTUAL_FUND: ['SIP', 'LUMPSUM', 'SELL'],
  BOND: ['BUY', 'SELL'],
  GOLD: ['BUY', 'SELL'],
  FD: ['OPEN', 'RENEW', 'WITHDRAW'],
  PPF: ['DEPOSIT', 'WITHDRAWAL'],
  EPF: ['CONTRIBUTION', 'WITHDRAWAL'],
  NPS: ['CONTRIBUTION', 'WITHDRAWAL'],
}

const ASSET_LABELS = {
  EQUITY: 'Stock', MUTUAL_FUND: 'MF', BOND: 'Bond', GOLD: 'Gold', FD: 'FD', PPF: 'PPF', EPF: 'EPF', NPS: 'NPS',
}

const ASSET_COLORS = {
  EQUITY: 'bg-blue-500/20 text-blue-400',
  MUTUAL_FUND: 'bg-green-500/20 text-green-400',
  BOND: 'bg-cyan-500/20 text-cyan-400',
  GOLD: 'bg-amber-500/20 text-amber-400',
  FD: 'bg-purple-500/20 text-purple-400',
  PPF: 'bg-teal-500/20 text-teal-400',
  EPF: 'bg-orange-500/20 text-orange-400',
  NPS: 'bg-pink-500/20 text-pink-400',
}

// Asset types that REQUIRE a demat account (must match backend)
const ASSET_TYPES_REQUIRING_DEMAT = ['EQUITY', 'ETF', 'MUTUAL_FUND']

const POPULAR_BONDS = [
  { name: 'RBI Floating Rate Savings Bond 2020 (Taxable)', coupon: 8.05 },
  { name: 'Sovereign Gold Bond (SGB)', coupon: 2.5 },
  { name: 'NHAI Tax-Free Bond', coupon: 8.2 },
  { name: 'IRFC Tax-Free Bond', coupon: 8.1 },
  { name: 'REC Tax-Free Bond', coupon: 8.01 },
  { name: 'PFC Tax-Free Bond', coupon: 8.0 },
  { name: 'HUDCO Tax-Free Bond', coupon: 8.1 },
  { name: 'NABARD Tax-Free Bond', coupon: 7.64 },
  { name: 'Indian Railway Finance Corp NCD', coupon: 7.5 },
  { name: 'Muthoot Finance NCD', coupon: 8.0 },
  { name: 'Shriram Transport NCD', coupon: 8.5 },
  { name: 'Mahindra Finance NCD', coupon: 7.75 },
  { name: 'NHPC Tax-Free Bond', coupon: 8.2 },
]

export default function Holdings() {
  const { view: familyView } = useFamilyView()
  const isFamilyView = familyView === 'family'
  const upstoxEnabled = useFeature('upstox-import')
  const [holdings, setHoldings] = useState([])
  const [symbols, setSymbols] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [dematAccounts, setDematAccounts] = useState([])
  const [assetType, setAssetType] = useState('')
  const [form, setForm] = useState({
    symbol: '',
    dematAccountId: '',
    transactionDate: new Date().toISOString().slice(0, 16),
    quantity: '',
    price: '',
    transactionType: 'BUY',
    name: '',
    couponRate: '',
    maturityDate: '',
  })
  const [searchOpen, setSearchOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [filter, setFilter] = useState('all')
  const searchRef = useRef(null)
  const [invoiceHolding, setInvoiceHolding] = useState(null)
  const [invoices, setInvoices] = useState([])
  const [invoicesLoading, setInvoicesLoading] = useState(false)
  const [uploadingInvoice, setUploadingInvoice] = useState(false)
  const [previewDoc, setPreviewDoc] = useState(null)
  const [investmentHistory, setInvestmentHistory] = useState([])
  const [chartHolding, setChartHolding] = useState(null)
  const [priceHistory, setPriceHistory] = useState([])
  const [chartDays, setChartDays] = useState(90)
  const [chartLoading, setChartLoading] = useState(false)
  const [backfillStatus, setBackfillStatus] = useState(null)
  const [chartMode, setChartMode] = useState('recharts')
  const [newsHolding, setNewsHolding] = useState(null)
  const [dividendSummary, setDividendSummary] = useState(null)
  const [dividendHolding, setDividendHolding] = useState(null)
  const [dividendRecords, setDividendRecords] = useState([])
  const [dividendRecordsLoading, setDividendRecordsLoading] = useState(false)
  const [calculatingDividends, setCalculatingDividends] = useState(false)

  const chartStats = useMemo(() => {
    if (!priceHistory.length) return null
    const first = priceHistory[0]
    const last = priceHistory[priceHistory.length - 1]
    const prices = priceHistory.map(p => p.close).filter(p => p != null)
    const high = Math.max(...prices)
    const low = Math.min(...prices)
    const avg = prices.reduce((a, b) => a + b, 0) / prices.length
    const change = last.close - first.close
    const changePct = (change / first.close) * 100
    const isUp = change >= 0
    return { high, low, avg, change, changePct, isUp, lineColor: isUp ? '#22c55e' : '#ef4444', gradId: `chartGrad_${isUp ? 'up' : 'dn'}` }
  }, [priceHistory])

  const selectedSymbol = useMemo(
    () => symbols.find((s) => s.symbol === form.symbol),
    [form.symbol, symbols]
  )
  const isEquity = assetType === 'EQUITY'
  const isMF = assetType === 'MUTUAL_FUND'
  const isBond = assetType === 'BOND'
  const needsSymbol = isEquity || isMF || isBond

  const filteredSymbols = useMemo(() => {
    const categoryMap = { 'MUTUAL_FUND': 'MUTUAL_FUND', 'BOND': 'BOND' }
    const cat = categoryMap[assetType] || 'EQUITY'
    const list = symbols.filter((s) => s.category === cat)
    if (!search) return list.slice(0, 50) // Limit initial display for bonds (5900+)
    const q = search.toLowerCase()
    return list.filter(
      (s) => s.symbol.toLowerCase().includes(q) || s.name.toLowerCase().includes(q)
    ).slice(0, 50)
  }, [search, symbols, assetType])

  const filteredHoldings = useMemo(() => {
    if (filter === 'all') return holdings
    return holdings.filter((h) => h.assetType === filter)
  }, [holdings, filter])

  // Group holdings by symbol+name (for family view consolidation)
  // Returns array of groups; each group has { symbol, name, assetType, isGroup, holdings[], totals }
  const groupedHoldings = useMemo(() => {
    if (!isFamilyView) {
      // Not family view - just return holdings as single-item groups
      return filteredHoldings.map(h => ({
        key: h.id,
        symbol: h.symbol,
        name: h.name,
        assetType: h.assetType,
        isGroup: false,
        holdings: [h],
        owners: h.ownerName ? [h.ownerName] : [],
        totalQuantity: h.quantity || 0,
        totalValue: h.currentValue || 0,
        totalPnL: h.unrealizedPnl || 0,
        totalInvested: (h.quantity || 0) * (h.averageBuyPrice || 0),
        avgPrice: h.averageBuyPrice || 0,
        currentPrice: h.currentPrice,
        dayChangePct: h.dayChangePct,
        representative: h,
      }))
    }

    // Family view: group by symbol+assetType
    const groups = new Map()
    for (const h of filteredHoldings) {
      const key = `${h.symbol}__${h.assetType}`
      if (!groups.has(key)) {
        groups.set(key, {
          key,
          symbol: h.symbol,
          name: h.name,
          assetType: h.assetType,
          holdings: [],
          owners: new Set(),
        })
      }
      const g = groups.get(key)
      g.holdings.push(h)
      if (h.ownerName) g.owners.add(h.ownerName)
    }

    return Array.from(groups.values()).map(g => {
      const totalQuantity = g.holdings.reduce((s, h) => s + (Number(h.quantity) || 0), 0)
      const totalValue = g.holdings.reduce((s, h) => s + (Number(h.currentValue) || 0), 0)
      const totalPnL = g.holdings.reduce((s, h) => s + (Number(h.unrealizedPnl) || 0), 0)
      const totalInvested = g.holdings.reduce((s, h) => s + ((Number(h.quantity) || 0) * (Number(h.averageBuyPrice) || 0)), 0)
      const avgPrice = totalQuantity > 0 ? totalInvested / totalQuantity : 0
      // Use first holding's current price (should be same across members for same symbol)
      const currentPrice = g.holdings[0]?.currentPrice
      const dayChangePct = g.holdings[0]?.dayChangePct
      return {
        ...g,
        owners: Array.from(g.owners),
        isGroup: g.holdings.length > 1,
        totalQuantity,
        totalValue,
        totalPnL,
        totalInvested,
        avgPrice,
        currentPrice,
        dayChangePct,
        representative: g.holdings[0],
      }
    })
  }, [filteredHoldings, isFamilyView])

  const [expandedGroups, setExpandedGroups] = useState({})
  const toggleGroup = (key) => {
    setExpandedGroups(prev => ({ ...prev, [key]: !prev[key] }))
  }

  const dematMap = useMemo(() => {
    const map = {}
    dematAccounts.forEach((d) => { map[d.id] = d })
    return map
  }, [dematAccounts])

  useEffect(() => {
    Promise.all([
      client.get('/portfolio/holdings'),
      client.get('/demat-accounts'),
      client.get('/symbols'),
      client.get('/portfolio/investment-over-time?days=365'),
      client.get('/dividends/summary'),
    ]).then(([h, d, s, i, div]) => {
      setHoldings(h.data || [])
      setDematAccounts(d.data || [])
      setSymbols(s.data || [])
      setInvestmentHistory(i.data || [])
      setDividendSummary(div.data || null)
    }).catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    if (loading) return
    const params = filter === 'all' ? '?days=365' : `?days=365&assetType=${filter}`
    client.get(`/portfolio/investment-over-time${params}`)
      .then(res => setInvestmentHistory(res.data || []))
      .catch(console.error)
  }, [filter])

  useEffect(() => {
    if (isMF) setForm((f) => ({ ...f, transactionType: 'SIP' }))
    else if (isEquity || isBond) setForm((f) => ({ ...f, transactionType: 'BUY' }))
  }, [assetType, isEquity, isMF, isBond])

  useEffect(() => {
    if (!backfillStatus?.running) return
    const interval = setInterval(async () => {
      try {
        const { data } = await client.get('/market/backfill-mf-status')
        setBackfillStatus(data)
        if (!data.running && chartHolding) {
          const { data: history } = await client.get(`/portfolio/holdings/${chartHolding.id}/price-history?days=${chartDays}`)
          setPriceHistory(history || [])
        }
      } catch (e) {
        console.error('Failed to poll backfill status:', e)
      }
    }, 3000)
    return () => clearInterval(interval)
  }, [backfillStatus?.running])

  useEffect(() => {
    if (!chartHolding || chartMode !== 'chart' || chartLoading || priceHistory.length === 0) {
      const el = document.getElementById('lw_chart_container')
      if (el) el.innerHTML = ''
      return
    }

    const container = document.getElementById('lw_chart_container')
    if (!container) return
    container.innerHTML = ''

    const chart = createChart(container, {
      autoSize: true,
      layout: {
        background: { type: 'solid', color: 'transparent' },
        textColor: '#9ca3af',
      },
      grid: {
        vertLines: { color: '#334155' },
        horzLines: { color: '#334155' },
      },
      crosshair: { mode: 0 },
      rightPriceScale: { borderColor: '#475569' },
      timeScale: { borderColor: '#475569' },
    })

    const isCandlestick = chartHolding.assetType === 'EQUITY' || chartHolding.assetType === 'ETF'

    if (isCandlestick) {
      const series = chart.addSeries(CandlestickSeries, {
        upColor: '#22c55e',
        downColor: '#ef4444',
        borderUpColor: '#22c55e',
        borderDownColor: '#ef4444',
        wickUpColor: '#22c55e',
        wickDownColor: '#ef4444',
      })
      const data = priceHistory
        .filter(p => p.open != null && p.high != null && p.low != null && p.close != null)
        .map(p => ({ time: p.date, open: p.open, high: p.high, low: p.low, close: p.close }))
      if (data.length) series.setData(data)
    } else {
      const series = chart.addSeries(AreaSeries, {
        lineColor: '#3b82f6',
        topColor: 'rgba(59, 130, 246, 0.3)',
        bottomColor: 'rgba(59, 130, 246, 0)',
      })
      const data = priceHistory
        .filter(p => p.close != null)
        .map(p => ({ time: p.date, value: p.close }))
      if (data.length) series.setData(data)
    }

    chart.timeScale().fitContent()

    return () => {
      try { chart.remove() } catch (_) {}
      container.innerHTML = ''
    }
  }, [chartHolding, chartMode, chartLoading, priceHistory])

  const handleSelectSymbol = (symbol) => {
    setForm({ ...form, symbol })
    setSearchOpen(false)
    setSearch('')
  }

  const resetForm = () => {
    setForm({
      symbol: '', dematAccountId: '',
      transactionDate: new Date().toISOString().slice(0, 16),
      quantity: '', price: '', transactionType: 'BUY', name: '',
      couponRate: '', maturityDate: '',
    })
    setAssetType('')
  }

  const openForm = () => {
    resetForm()
    setShowForm(true)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!assetType) return
    if (needsSymbol && !selectedSymbol) return
    if (!needsSymbol && !form.name) return

    // Validate demat account required for tradeable assets
    if (ASSET_TYPES_REQUIRING_DEMAT.includes(assetType) && !form.dematAccountId) {
      toast.error('Demat account required', {
        description: `Please select a demat account for ${assetType} holdings.`,
      })
      return
    }

    setSubmitting(true)
    try {
      const isBondLike = assetType === 'BOND' || assetType === 'GOLD'
      const qty = needsSymbol || isBondLike ? (parseFloat(form.quantity) || 0) : 1
      const price = needsSymbol || isBondLike ? (parseFloat(form.price) || 0) : (parseFloat(form.quantity) || 0)
      const sym = needsSymbol ? selectedSymbol.symbol : assetType
      const name = needsSymbol ? selectedSymbol.name : form.name

      const matchSymbol = needsSymbol ? selectedSymbol.symbol : assetType
      const matchName = needsSymbol ? selectedSymbol.name : form.name
      const matchingHolding = holdings.find((h) =>
        h.symbol === matchSymbol && h.name === matchName &&
        (h.dematAccountId === form.dematAccountId || (!h.dematAccountId && !form.dematAccountId))
      )
      let holdingId = matchingHolding?.id

      if (!holdingId) {
        const payload = {
          assetType,
          symbol: sym,
          name,
          exchange: selectedSymbol?.exchange || '',
          sector: selectedSymbol?.sector || '',
          quantity: 0,
          averageBuyPrice: 0,
          dematAccountId: form.dematAccountId || null,
        }
        if (assetType === 'BOND') {
          const metadata = {}
          if (form.couponRate) metadata.interestRate = form.couponRate
          if (Object.keys(metadata).length > 0) payload.metadata = metadata
          if (form.maturityDate) payload.lockInUntil = form.maturityDate
        }
        const { data } = await client.post('/portfolio/holdings', payload)
        holdingId = data.id
      }

      const transactions = TYPE_TRANSACTIONS[assetType] || ['BUY']
      const txnType = form.transactionType || transactions[0]
      await client.post('/portfolio/transactions', {
        holdingId,
        transactionType: txnType,
        quantity: qty,
        price,
        transactionDate: new Date(form.transactionDate).toISOString(),
        broker: form.dematAccountId ? (dematMap[form.dematAccountId]?.brokerName || null) : null,
      })

      resetForm()
      setShowForm(false)
      const res = await client.get('/portfolio/holdings')
      setHoldings(res.data)
    } catch (err) {
      console.error('Failed to add:', err)
    } finally {
      setSubmitting(false)
    }
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this holding?')) {
      await client.delete(`/portfolio/holdings/${id}`)
      const res = await client.get('/portfolio/holdings')
      setHoldings(res.data)
    }
  }

  const openChart = async (holding, days) => {
    setChartHolding(holding)
    setChartDays(days)
    setChartLoading(true)
    setBackfillStatus(null)
    try {
      // For MFs, check backfill status FIRST so we know if one is in progress
      let isBackfillRunning = false
      if (holding.assetType === 'MUTUAL_FUND') {
        try {
          const statusRes = await client.get('/market/backfill-mf-status')
          if (statusRes.data?.running) {
            setBackfillStatus(statusRes.data)
            isBackfillRunning = true
          }
        } catch (e) {
          // Ignore status fetch errors
        }
      }

      const { data } = await client.get(`/portfolio/holdings/${holding.id}/price-history?days=${days}`)
      setPriceHistory(data || [])

      // If no history and not already showing running status, check once more
      // This handles the case where backfill just finished (completed=true)
      if ((!data || data.length === 0) && !isBackfillRunning && holding.assetType === 'MUTUAL_FUND') {
        try {
          const statusRes = await client.get('/market/backfill-mf-status')
          if (statusRes.data?.completed || statusRes.data?.running) {
            setBackfillStatus(statusRes.data)
          }
        } catch (e) {
          // Ignore
        }
      }
    } catch (e) {
      console.error('Failed to load price history:', e)
      setPriceHistory([])
    } finally {
      setChartLoading(false)
    }
  }

  const triggerEquityBackfill = async (holding) => {
    try {
      const { toast } = await import('sonner')
      toast.loading('Fetching price history...', { id: 'equity-backfill' })
      const backfillRes = await client.post(`/market/backfill-holding/${holding.id}?days=${chartDays > 180 ? 365 : chartDays + 30}`)
      const count = backfillRes.data?.recordsBackfilled || 0
      // Reload price history
      const { data } = await client.get(`/portfolio/holdings/${holding.id}/price-history?days=${chartDays}`)
      setPriceHistory(data || [])
      if (data && data.length > 0) {
        toast.success('Price history loaded', {
          id: 'equity-backfill',
          description: `${count} records fetched`,
        })
      } else {
        toast.warning('No price data available', {
          id: 'equity-backfill',
          description: backfillRes.data?.message || 'Upstox may not have data for this symbol',
        })
      }
    } catch (e) {
      const { toast } = await import('sonner')
      toast.error('Failed to fetch history', {
        id: 'equity-backfill',
        description: e?.message || 'Please try again',
      })
    }
  }

  const triggerMfBackfill = async (holding) => {
    try {
      const { toast } = await import('sonner')
      // Optimistic UI - show progress immediately
      setBackfillStatus({ running: true, progressDays: 0, totalDays: 1 })
      toast.info('Backfill started', {
        description: 'NAV history will be downloaded in the background.',
      })
      await client.post('/market/backfill-mf-history')
    } catch (e) {
      setBackfillStatus(null)
      const { toast } = await import('sonner')
      toast.error('Failed to start backfill', {
        description: e?.message || 'Please try again',
      })
    }
  }

  const closeChart = () => {
    setChartHolding(null)
    setPriceHistory([])
    setChartMode('recharts')
  }

  const openInvoices = async (holding) => {
    setInvoiceHolding(holding)
    setInvoicesLoading(true)
    try {
      const { data } = await client.get(`/documents/holding/${holding.id}`)
      setInvoices(data || [])
    } catch (e) {
      console.error('Failed to load invoices:', e)
      setInvoices([])
    } finally {
      setInvoicesLoading(false)
    }
  }

  const handleInvoiceUpload = async (e) => {
    const file = e.target.files?.[0]
    if (!file || !invoiceHolding) return
    setUploadingInvoice(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('category', 'INVOICE')
      formData.append('holdingId', invoiceHolding.id)
      await client.post('/documents/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      const { data } = await client.get(`/documents/holding/${invoiceHolding.id}`)
      setInvoices(data || [])
    } catch (e) {
      console.error('Upload failed:', e)
    } finally {
      setUploadingInvoice(false)
    }
  }

  const handleInvoiceDownload = async (doc) => {
    try {
      const response = await client.get(`/documents/${doc.id}/download`, { responseType: 'blob' })
      const url = URL.createObjectURL(response.data)
      const a = document.createElement('a')
      a.href = url
      a.download = doc.originalFilename
      document.body.appendChild(a)
      a.click()
      a.remove()
      URL.revokeObjectURL(url)
    } catch (e) {
      console.error('Download failed:', e)
    }
  }

  const handleInvoiceDelete = async (docId) => {
    if (!confirm('Delete this invoice?')) return
    try {
      await client.delete(`/documents/${docId}`)
      setInvoices((prev) => prev.filter((d) => d.id !== docId))
    } catch (e) {
      console.error('Delete failed:', e)
    }
  }

  const handleInvoicePreview = async (doc) => {
    try {
      const response = await client.get(`/documents/${doc.id}/download`, { responseType: 'blob' })
      const url = URL.createObjectURL(response.data)
      setPreviewDoc({ ...doc, blobUrl: url })
    } catch (e) {
      console.error('Preview failed:', e)
    }
  }

  const closePreview = () => {
    if (previewDoc?.blobUrl) URL.revokeObjectURL(previewDoc.blobUrl)
    setPreviewDoc(null)
  }

  const handleCalculateDividends = async () => {
    setCalculatingDividends(true)
    try {
      const { data } = await client.post('/dividends/calculate')
      toast.success(`Dividends calculated: ${data.newDividends} new, ${data.updatedDividends} updated`)
      const { data: summary } = await client.get('/dividends/summary')
      setDividendSummary(summary)
    } catch (e) {
      toast.error('Failed to calculate dividends')
    } finally {
      setCalculatingDividends(false)
    }
  }

  const handleShowDividendDetail = async (holding) => {
    setDividendHolding(holding)
    setDividendRecordsLoading(true)
    setDividendRecords([])
    try {
      const { data } = await client.get(`/dividends/holding/${holding.id}`)
      setDividendRecords(data || [])
    } catch (e) {
      toast.error('Failed to load dividend records')
    } finally {
      setDividendRecordsLoading(false)
    }
  }

  // Context-aware allocation: by asset type when "All", by individual holding when specific tab
  const allocation = useMemo(() => {
    if (filter === 'all') {
      // Group by asset type
      return [
        { name: 'Equity', key: 'EQUITY' },
        { name: 'Mutual Fund', key: 'MUTUAL_FUND' },
        { name: 'Bond', key: 'BOND' },
        { name: 'Gold', key: 'GOLD' },
        { name: 'FD', key: 'FD' },
        { name: 'PPF', key: 'PPF' },
        { name: 'EPF', key: 'EPF' },
        { name: 'NPS', key: 'NPS' },
      ].map(a => ({
        ...a,
        value: holdings.filter(h => h.assetType === a.key).reduce((s, h) => s + (h.currentValue || 0), 0),
      })).filter(a => a.value > 0)
    } else {
      // Group by individual holding within the selected type
      return filteredHoldings
        .map(h => ({
          name: h.symbol || h.name || '?',
          key: h.id,
          value: h.currentValue || 0,
        }))
        .filter(a => a.value > 0)
        .sort((a, b) => b.value - a.value)
    }
  }, [filter, holdings, filteredHoldings])

  const totalValue = filteredHoldings.reduce((s, h) => s + (h.currentValue || 0), 0)
  const totalPnL = filteredHoldings.reduce((s, h) => s + (h.unrealizedPnl || 0), 0)
  const totalInvested = filteredHoldings.reduce((s, h) => s + ((h.quantity || 0) * (h.averageBuyPrice || 0)), 0)

  if (loading) return <HoldingsSkeletonLoader />

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Holdings</h1>
          <p className="text-[var(--text-muted)] text-sm mt-1">{holdings.length} investments across all asset classes</p>
        </div>
        <button
          onClick={() => { if (showForm) { setShowForm(false); resetForm() } else openForm() }}
          className={`px-4 py-2 rounded-lg flex items-center gap-2 transition ${showForm ? 'bg-[var(--input-bg)] hover:bg-[var(--hover-bg)]' : 'bg-blue-500 hover:bg-blue-600'}`}
        >
          <Plus className="w-4 h-4" /> {showForm ? 'Cancel' : 'Add Holdings'}
        </button>
      </div>

      {upstoxEnabled && (
        <UpstoxSync onSyncComplete={() => client.get('/portfolio/holdings').then(r => setHoldings(r.data || []))} />
      )}

      {showForm && (
        <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
          <h3 className="text-lg font-semibold mb-5">Add Holding</h3>
          <form onSubmit={handleSubmit} className="space-y-5">

            {/* Asset Type Selector */}
            <div>
              <label className="block text-sm text-[var(--text-muted)] mb-2">Asset Type</label>
              <div className="flex flex-wrap gap-2">
                {ASSET_TYPES.map((t) => {
                  const Icon = t.icon
                  return (
                    <button
                      key={t.value}
                      type="button"
                      onClick={() => { setAssetType(t.value); setForm({ ...form, symbol: '', name: '' }) }}
                      className={`flex items-center gap-1.5 px-3 py-2 rounded-lg text-sm font-medium transition border ${
                        assetType === t.value
                          ? 'bg-blue-500/20 text-blue-400 border-blue-500/40'
                          : 'bg-[var(--input-bg)] text-[var(--text-muted)] border-[var(--border)] hover:border-[var(--text-secondary)]'
                      }`}
                    >
                      <Icon className="w-4 h-4" />
                      {t.label}
                    </button>
                  )
                })}
              </div>
            </div>

            {assetType && (
              <>
                {/* Symbol search for EQUITY/MF */}
                {needsSymbol && (
                  <div className="relative" ref={searchRef}>
                    <label className="block text-sm text-[var(--text-muted)] mb-1">
                      {isEquity ? 'Select Stock' : 'Select Mutual Fund'}
                    </label>
                    <div
                      className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5 cursor-pointer flex items-center justify-between"
                      onClick={() => setSearchOpen(!searchOpen)}
                    >
                      {selectedSymbol ? (
                        <div>
                          <div className="font-medium text-sm">{selectedSymbol.symbol}</div>
                          <div className="text-xs text-[var(--text-muted)]">{selectedSymbol.name}</div>
                        </div>
                      ) : (
                        <span className="text-[var(--text-secondary)]">Type to search...</span>
                      )}
                      {searchOpen ? <X className="w-4 h-4 text-[var(--text-muted)]" /> : <Search className="w-4 h-4 text-[var(--text-muted)]" />}
                    </div>
                    {searchOpen && (
                      <div className="absolute z-50 w-full mt-1 bg-[var(--bg-card)] border border-[var(--border)] rounded-lg shadow-xl max-h-64 overflow-hidden">
                        <div className="p-2 border-b border-[var(--border)]">
                          <div className="flex items-center gap-2 bg-[var(--bg)] rounded-lg px-3 py-1.5">
                            <Search className="w-4 h-4 text-[var(--text-muted)]" />
                            <input autoFocus value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search..." className="bg-transparent w-full text-sm focus:outline-none" onClick={(e) => e.stopPropagation()} />
                            {search && <X className="w-4 h-4 cursor-pointer" onClick={(e) => { e.stopPropagation(); setSearch('') }} />}
                          </div>
                        </div>
                        <div className="overflow-y-auto max-h-52">
                          {filteredSymbols.length === 0 ? (
                            <div className="px-3 py-8 text-center text-sm text-[var(--text-muted)]">No symbols found</div>
                          ) : (
                            filteredSymbols.map((s) => (
                              <div key={s.symbol} className="px-3 py-2 hover:bg-[var(--hover-bg)] cursor-pointer flex items-center gap-3 border-b border-[var(--border)]/30" onClick={() => handleSelectSymbol(s.symbol)}>
                                <span className={`text-sm font-mono font-medium min-w-[100px] ${s.category === 'MUTUAL_FUND' ? 'text-green-300' : s.category === 'BOND' ? 'text-cyan-300' : 'text-blue-300'}`}>
                                  {s.category === 'MUTUAL_FUND' ? s.symbol.substring(0, 16) : s.symbol.replace('.NS', '')}
                                </span>
                                <span className="text-xs text-[var(--text-muted)] flex-1 truncate">{s.name}</span>
                                {s.category === 'BOND' && s.sector?.includes('Coupon') ? (
                                  <span className="text-[10px] text-cyan-400 bg-cyan-500/10 px-1.5 py-0.5 rounded shrink-0">{s.sector.replace('Bond | ', '')}</span>
                                ) : (
                                  <span className="text-xs text-[var(--text-secondary)]">{s.sector || s.category}</span>
                                )}
                              </div>
                            ))
                          )}
                        </div>
                      </div>
                    )}
                  </div>
                )}

                {/* Name input for non-tradeable assets */}
                {!needsSymbol && (
                  <div>
                    <label className="block text-sm text-[var(--text-muted)] mb-1">Name</label>
                    {assetType === 'BOND' ? (
                      <>
                        <input
                          type="text"
                          value={form.name}
                          onChange={(e) => setForm({ ...form, name: e.target.value })}
                          className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5"
                          placeholder="e.g., RBI Floating Rate Bond 2025"
                          list="bond-suggestions"
                          required
                        />
                        <datalist id="bond-suggestions">
                          {POPULAR_BONDS.map(b => (
                            <option key={b.name} value={b.name} />
                          ))}
                        </datalist>
                        {!form.name && (
                          <div className="flex flex-wrap gap-1.5 mt-2">
                            {POPULAR_BONDS.slice(0, 6).map(b => (
                              <button key={b.name} type="button"
                                onClick={() => setForm({ ...form, name: b.name, couponRate: String(b.coupon) })}
                                className="text-xs px-2 py-1 rounded bg-[var(--input-bg)] text-[var(--text-muted)] hover:bg-cyan-500/20 hover:text-cyan-400 transition border border-[var(--border)]">
                                {b.name.length > 25 ? b.name.slice(0, 25) + '...' : b.name}
                              </button>
                            ))}
                          </div>
                        )}
                      </>
                    ) : (
                      <input
                        type="text"
                        value={form.name}
                        onChange={(e) => setForm({ ...form, name: e.target.value })}
                        className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5"
                        placeholder={assetType === 'FD' ? 'e.g., HDFC Bank FD 2025' : `e.g., ${assetType} Account`}
                        required
                      />
                    )}
                  </div>
                )}

                {/* Bond-specific fields: coupon rate & maturity */}
                {assetType === 'BOND' && (
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm text-[var(--text-muted)] mb-1">Coupon Rate (%)</label>
                      <input
                        type="number"
                        step="0.01"
                        value={form.couponRate}
                        onChange={(e) => setForm({ ...form, couponRate: e.target.value })}
                        className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5"
                        placeholder="e.g., 8.05"
                      />
                    </div>
                    <div>
                      <label className="block text-sm text-[var(--text-muted)] mb-1">Maturity Date</label>
                      <input
                        type="date"
                        value={form.maturityDate}
                        onChange={(e) => setForm({ ...form, maturityDate: e.target.value })}
                        className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5"
                      />
                    </div>
                  </div>
                )}

                {/* Transaction type */}
                {TYPE_TRANSACTIONS[assetType] && (
                  <div>
                    <label className="block text-sm text-[var(--text-muted)] mb-2">Transaction Type</label>
                    <div className="flex flex-wrap gap-2">
                      {TYPE_TRANSACTIONS[assetType].map((t) => (
                        <button
                          key={t}
                          type="button"
                          onClick={() => setForm({ ...form, transactionType: t })}
                          className={`px-3 py-1.5 rounded-lg text-sm font-medium transition ${
                            form.transactionType === t
                              ? t === 'SELL' || t === 'WITHDRAWAL'
                                ? 'bg-red-500/20 text-red-400 border border-red-500/40'
                                : t === 'BUY'
                                  ? 'bg-green-500/20 text-green-400 border border-green-500/40'
                                  : 'bg-blue-500/20 text-blue-400 border border-blue-500/40'
                              : 'bg-[var(--input-bg)] text-[var(--text-muted)] border border-[var(--border)] hover:bg-[var(--hover-bg)]'
                          }`}
                        >
                          {t}
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {/* Common fields: date, quantity, price */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div>
                    <label className="block text-sm text-[var(--text-muted)] mb-1">Date</label>
                    <input type="datetime-local" value={form.transactionDate} onChange={(e) => setForm({ ...form, transactionDate: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" required />
                  </div>
                  <div>
                    <label className="block text-sm text-[var(--text-muted)] mb-1">
                      {isEquity ? 'Shares' : isMF ? 'Units' : assetType === 'GOLD' ? 'Grams' : assetType === 'BOND' ? 'Face Value (₹)' : 'Amount (₹)'}
                    </label>
                    <input type="number" step="any" value={form.quantity} onChange={(e) => setForm({ ...form, quantity: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" required placeholder={isEquity ? '10' : isMF ? '150.234' : assetType === 'GOLD' ? '50' : assetType === 'BOND' ? '10000' : '100000'} />
                  </div>
                  <div>
                    <label className="block text-sm text-[var(--text-muted)] mb-1">
                      {isEquity ? 'Price/Share' : isMF ? 'NAV' : assetType === 'GOLD' ? 'Price/Gram' : assetType === 'BOND' ? 'Purchase Price (₹)' : 'Rate (if any)'}
                    </label>
                    <input type="number" step="any" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" required={needsSymbol || assetType === 'BOND'} placeholder={isEquity ? '1450.50' : isMF ? '45.678' : assetType === 'BOND' ? '10000' : '0'} />
                  </div>
                </div>

                {/* Demat account selector (required for tradeable assets) */}
                {assetType !== 'PPF' && assetType !== 'EPF' && assetType !== 'NPS' && (
                  <div>
                    <label className="block text-sm text-[var(--text-muted)] mb-1">
                      Demat Account
                      {ASSET_TYPES_REQUIRING_DEMAT.includes(assetType) && (
                        <span className="text-red-400 ml-1">*</span>
                      )}
                    </label>
                    {dematAccounts.length === 0 ? (
                      <div className="bg-amber-500/10 border border-amber-500/30 rounded-lg px-3 py-2.5 text-sm text-amber-400">
                        <p className="mb-1">⚠️ No demat accounts found.</p>
                        <a href="/demat-accounts" className="underline text-amber-300 hover:text-amber-200">
                          Add a demat account first →
                        </a>
                      </div>
                    ) : (
                      <select
                        value={form.dematAccountId}
                        onChange={(e) => setForm({ ...form, dematAccountId: e.target.value })}
                        className={`w-full bg-[var(--input-bg)] border rounded-lg px-3 py-2.5 ${
                          ASSET_TYPES_REQUIRING_DEMAT.includes(assetType) && !form.dematAccountId
                            ? 'border-red-500/50'
                            : 'border-[var(--border)]'
                        }`}
                        required={ASSET_TYPES_REQUIRING_DEMAT.includes(assetType)}
                      >
                        <option value="">
                          {ASSET_TYPES_REQUIRING_DEMAT.includes(assetType)
                            ? 'Select a demat account *'
                            : 'No account (general)'}
                        </option>
                        {dematAccounts.map((d) => (
                          <option key={d.id} value={d.id}>{d.brokerName}{d.accountNumber ? ` (${d.accountNumber})` : ''}{d.isDefault ? ' ⭐' : ''}</option>
                        ))}
                      </select>
                    )}
                    {ASSET_TYPES_REQUIRING_DEMAT.includes(assetType) && (
                      <p className="text-xs text-[var(--text-muted)] mt-1">
                        Required for tracking holdings across brokers
                      </p>
                    )}
                  </div>
                )}

                {/* Total value preview */}
                <div className="bg-[var(--bg)] rounded-lg p-4">
                  <div className="flex items-center justify-between">
                    <span className="text-[var(--text-muted)]">Total Value</span>
                    <span className="text-2xl font-bold">Rs. {((parseFloat(form.quantity) || 0) * (parseFloat(form.price) || 0) || (parseFloat(form.quantity) || 0)).toLocaleString('en-IN')}</span>
                  </div>
                </div>

                <div className="flex justify-end gap-3">
                  <button type="button" onClick={() => { setShowForm(false); resetForm() }} className="px-4 py-2 bg-[var(--input-bg)] rounded-lg hover:bg-[var(--hover-bg)] transition">Cancel</button>
                  <button
                    type="submit"
                    disabled={submitting || !assetType || (needsSymbol && !selectedSymbol) || (!needsSymbol && !form.name) || !form.quantity}
                    className="px-6 py-2 rounded-lg font-medium flex items-center gap-2 transition bg-blue-500 hover:bg-blue-600 disabled:bg-[var(--input-bg)] disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
                    {submitting ? 'Adding...' : `Add ${ASSET_LABELS[assetType] || 'Holding'}`}
                  </button>
                </div>
              </>
            )}
          </form>
        </div>
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <p className="text-[var(--text-muted)] text-sm">Invested</p>
          <p className="text-xl font-bold mt-1">Rs. {totalInvested.toLocaleString('en-IN')}</p>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <p className="text-[var(--text-muted)] text-sm">Current Value</p>
          <p className="text-xl font-bold mt-1">Rs. {totalValue.toLocaleString('en-IN')}</p>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <p className="text-[var(--text-muted)] text-sm">Total P&L</p>
          <p className={`text-xl font-bold mt-1 ${totalPnL >= 0 ? 'text-green-400' : 'text-red-400'}`}>
            {totalPnL >= 0 ? '+' : ''}Rs. {Math.abs(totalPnL).toLocaleString('en-IN')}
          </p>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <p className="text-[var(--text-muted)] text-sm">Returns</p>
          <p className={`text-xl font-bold mt-1 ${totalPnL >= 0 ? 'text-green-400' : 'text-red-400'}`}>
            {totalInvested > 0 ? ((totalPnL / totalInvested) * 100).toFixed(2) : 0}%
          </p>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <div className="flex items-center justify-between">
            <p className="text-[var(--text-muted)] text-sm">Dividends</p>
            {dividendSummary && (
              <button
                onClick={handleCalculateDividends}
                disabled={calculatingDividends}
                className="text-[10px] text-blue-400 hover:text-blue-300 transition disabled:opacity-50"
                title="Recalculate dividends"
              >
                {calculatingDividends ? '...' : 'Refresh'}
              </button>
            )}
          </div>
          <p className="text-xl font-bold mt-1 text-green-400">
            Rs. {dividendSummary ? Number(dividendSummary.totalDividends || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 }) : '-'}
          </p>
          {dividendSummary && dividendSummary.dividendYield > 0 && (
            <p className="text-xs text-[var(--text-muted)] mt-0.5">
              Yield: {Number(dividendSummary.dividendYield).toFixed(2)}%
            </p>
          )}
        </div>
      </div>

      {/* Charts: Allocation + Investment Growth */}
      {allocation.length > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
            <h3 className="text-lg font-semibold mb-4">{filter === 'all' ? 'Asset Allocation' : `${ASSET_LABELS[filter] || filter} Allocation`}</h3>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={allocation} dataKey="value" nameKey="name" cx="50%" cy="50%" outerRadius={90} innerRadius={50} paddingAngle={3}>
                    {allocation.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                  </Pie>
                  <Tooltip formatter={(v) => `Rs. ${Number(v).toLocaleString('en-IN')}`} contentStyle={{ backgroundColor: 'var(--bg-card)', border: '1px solid var(--border)' }} />
                </PieChart>
              </ResponsiveContainer>
            </div>
            <div className="flex flex-wrap gap-3 mt-2 justify-center">
              {allocation.map((item, i) => (
                <div key={item.name} className="flex items-center gap-2 text-sm">
                  <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS[i % COLORS.length] }} />
                  <span className="text-[var(--text-muted)]">{item.name}</span>
                  <span className="font-medium">{((item.value / totalValue) * 100).toFixed(1)}%</span>
                </div>
              ))}
            </div>
          </div>

          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
            <h3 className="text-lg font-semibold mb-4">Investment Growth</h3>
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <AreaChart data={investmentHistory}>
                  <defs>
                    <linearGradient id="investedGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#22c55e" stopOpacity={0.15} />
                      <stop offset="95%" stopColor="#22c55e" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="valueGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.15} />
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="date" stroke="var(--text-muted)" tick={{ fontSize: 12 }}
                    tickFormatter={(val) => { const d = new Date(val); return d.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' }) }} />
                  <YAxis stroke="var(--text-muted)" tick={{ fontSize: 12 }}
                    tickFormatter={(val) => {
                      if (val >= 100000) return `Rs ${(val / 100000).toFixed(1)}L`;
                      if (val >= 1000) return `Rs ${(val / 1000).toFixed(0)}K`;
                      return `Rs ${val}`;
                    }} />
                  <Tooltip contentStyle={{ backgroundColor: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '8px', color: 'var(--text)' }}
                    formatter={(val, name) => [`Rs. ${Number(val).toLocaleString('en-IN')}`, name === 'invested' ? 'Invested' : 'Value']}
                    labelFormatter={(val) => new Date(val).toLocaleDateString('en-IN', { month: 'long', day: 'numeric', year: 'numeric' })} />
                  <Area type="monotone" dataKey="invested" stroke="#22c55e" strokeWidth={2} fill="url(#investedGradient)" name="invested" />
                  <Area type="monotone" dataKey="value" stroke="#3b82f6" strokeWidth={2} fill="url(#valueGradient)" name="value" />
                </AreaChart>
              </ResponsiveContainer>
            </div>
            <div className="flex items-center justify-center gap-4 mt-2 text-sm">
              <div className="flex items-center gap-2">
                <div className="w-3 h-0.5 rounded bg-green-400" />
                <span className="text-[var(--text-muted)]">Invested</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-3 h-0.5 rounded bg-blue-400" />
                <span className="text-[var(--text-muted)]">Value</span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Table */}
      <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
        <div className="px-4 py-3 border-b border-[var(--border)] flex flex-wrap gap-2">
          {['all', ...ASSET_TYPES.map(t => t.value)].map((f) => (
            <button key={f} onClick={() => setFilter(f)} className={`px-3 py-1.5 rounded-lg text-sm transition ${filter === f ? 'bg-blue-500/20 text-blue-400' : 'text-[var(--text-muted)] hover:bg-[var(--hover-bg)]'}`}>
              {f === 'all' ? 'All' : ASSET_LABELS[f] || f}
            </button>
          ))}
        </div>

        <div className="overflow-x-auto -mx-4 sm:mx-0">
        <table className="w-full min-w-[800px]">
          <thead className="bg-[var(--bg)]/50 text-left sticky top-0 z-10">
            <tr>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Name</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Demat</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Type</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Qty</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Avg Price</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Value</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Day Chg %</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">P&L</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Dividends</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {groupedHoldings.length === 0 ? (
              <tr><td colSpan="10" className="px-4 py-12 text-center text-[var(--text-secondary)]">No holdings yet. Add your first investment above.</td></tr>
            ) : (
              groupedHoldings.flatMap((group) => {
                const isExpanded = expandedGroups[group.key]
                const isClickable = group.assetType === 'EQUITY' || group.assetType === 'ETF' || group.assetType === 'MUTUAL_FUND'
                const groupRow = (
                  <tr key={group.key} className={`hover:bg-[var(--hover-bg)] ${group.isGroup ? 'bg-[var(--input-bg)]/20' : ''}`}>
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2 flex-wrap">
                        {group.isGroup && (
                          <button
                            onClick={() => toggleGroup(group.key)}
                            className="text-[var(--text-muted)] hover:text-blue-400 transition p-0.5 -ml-1"
                            aria-label={isExpanded ? 'Collapse breakdown' : 'Expand breakdown'}
                          >
                            {isExpanded ? <ChevronDownIcon className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                          </button>
                        )}
                        {isClickable
                          ? <button onClick={() => openChart(group.representative, chartDays)} className="font-medium text-left hover:text-blue-400 transition">{group.symbol}</button>
                          : <span className="font-medium">{group.symbol}</span>}
                        {/* Show owner badges - all in family view, none in self view */}
                        {isFamilyView && group.owners.length > 0 && (
                          <>
                            {group.isGroup ? (
                              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium bg-purple-500/15 text-purple-400 ring-1 ring-inset ring-purple-500/30">
                                <Users className="w-2.5 h-2.5" />
                                {group.owners.length} members
                              </span>
                            ) : (
                              <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium bg-purple-500/15 text-purple-400 ring-1 ring-inset ring-purple-500/30">
                                <Users className="w-2.5 h-2.5" />
                                {group.owners[0]}
                              </span>
                            )}
                          </>
                        )}
                      </div>
                      {group.name && <div className="text-xs text-[var(--text-secondary)]">{group.name}</div>}
                    </td>
                    <td className="px-4 py-3">
                      {group.isGroup ? (
                        <span className="text-xs text-[var(--text-secondary)]">{group.holdings.length} accounts</span>
                      ) : group.representative.dematAccountBroker ? (
                        <div className="flex items-center gap-1 text-xs text-[var(--text-muted)]">
                          <Building2 className="w-3 h-3" />
                           {group.representative.dematAccountBroker}{group.representative.dematAccountNumber ? ` (${group.representative.dematAccountNumber})` : ''}
                        </div>
                      ) : (
                        <span className="text-xs text-[var(--text-secondary)]">General</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`text-xs px-2 py-1 rounded ${ASSET_COLORS[group.assetType] || 'bg-[var(--input-bg)] text-[var(--text)]'}`}>
                        {ASSET_LABELS[group.assetType] || group.assetType.replace('_', ' ')}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">{group.totalQuantity}</td>
                    <td className="px-4 py-3 text-right">Rs. {Number(group.avgPrice).toLocaleString('en-IN', { maximumFractionDigits: 2 })}</td>
                    <td className="px-4 py-3 text-right font-medium">Rs. {Number(group.totalValue).toLocaleString('en-IN', { maximumFractionDigits: 0 })}</td>
                    <td className={`px-4 py-3 text-right font-medium ${group.dayChangePct != null ? (group.dayChangePct >= 0 ? 'text-green-400' : 'text-red-400') : 'text-[var(--text-muted)]'}`}>
                      {group.dayChangePct != null ? `${group.dayChangePct >= 0 ? '+' : ''}${Number(group.dayChangePct).toFixed(2)}%` : '-'}
                    </td>
                    <td className={`px-4 py-3 text-right font-medium ${group.totalPnL >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                      <div className="flex items-center justify-end gap-1">
                        {group.totalPnL >= 0 ? <TrendingUp className="w-4 h-4" /> : <TrendingDown className="w-4 h-4" />}
                        Rs. {Math.abs(group.totalPnL).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                      </div>
                    </td>
                    <td className="px-4 py-3 text-right">
                      {!group.isGroup && group.representative && (
                        (() => {
                          const symbol = group.symbol
                          const divAmt = dividendSummary?.dividendsByStock?.[symbol]
                          if (divAmt && Number(divAmt) > 0) {
                            return (
                              <button
                                onClick={() => handleShowDividendDetail(group.representative)}
                                className="inline-flex items-center gap-1 text-xs font-medium text-green-400 hover:text-green-300 transition"
                                title="View dividend details"
                              >
                                <IndianRupee className="w-3 h-3" />
                                {Number(divAmt).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                              </button>
                            )
                          }
                          return <span className="text-xs text-[var(--text-secondary)]">-</span>
                        })()
                      )}
                      {group.isGroup && <span className="text-xs text-[var(--text-secondary)]">-</span>}
                    </td>
                    <td className="px-4 py-3 text-right">
                      {!group.isGroup && (
                        <div className="flex items-center justify-end gap-1">
                          {(group.assetType === 'EQUITY' || group.assetType === 'ETF' || group.assetType === 'MUTUAL_FUND') && (
                            <button onClick={() => setNewsHolding(group.representative)} className="text-[var(--text-secondary)] hover:text-blue-400 transition p-1" title="News">
                              <Newspaper className="w-4 h-4" />
                            </button>
                          )}
                          {group.assetType === 'GOLD' && (
                            <button onClick={() => openInvoices(group.representative)} className="text-[var(--text-secondary)] hover:text-amber-400 transition p-1" title="Invoices">
                              <FileText className="w-4 h-4" />
                            </button>
                          )}
                          <button onClick={() => handleDelete(group.representative.id)} aria-label="Delete holding" className="text-[var(--text-secondary)] hover:text-red-400 transition p-1"><Trash2 className="w-4 h-4" /></button>
                        </div>
                      )}
                    </td>
                  </tr>
                )

                if (!group.isGroup || !isExpanded) return [groupRow]

                // Expanded sub-rows - one per member's holding
                const subRows = group.holdings.map((h) => {
                  const pnl = h.unrealizedPnl || 0
                  return (
                    <tr key={h.id} className="bg-[var(--bg)]/30 hover:bg-[var(--hover-bg)]/50 border-l-2 border-purple-500/40">
                      <td className="px-4 py-2 pl-12">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium bg-purple-500/15 text-purple-400 ring-1 ring-inset ring-purple-500/30">
                            <Users className="w-2.5 h-2.5" />
                            {h.ownerName || 'Unknown'}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-2">
                        {h.dematAccountBroker ? (
                          <div className="flex items-center gap-1 text-xs text-[var(--text-muted)]">
                            <Building2 className="w-3 h-3" />
                             {h.dematAccountBroker}{h.dematAccountNumber ? ` (${h.dematAccountNumber})` : ''}
                          </div>
                        ) : (
                          <span className="text-xs text-[var(--text-secondary)]">General</span>
                        )}
                      </td>
                      <td className="px-4 py-2"></td>
                      <td className="px-4 py-2 text-right text-sm">{h.quantity}</td>
                      <td className="px-4 py-2 text-right text-sm">Rs. {h.averageBuyPrice.toLocaleString('en-IN', { maximumFractionDigits: 2 })}</td>
                      <td className="px-4 py-2 text-right text-sm">Rs. {(h.currentValue || 0).toLocaleString('en-IN', { maximumFractionDigits: 0 })}</td>
                      <td className="px-4 py-2"></td>
                      <td className={`px-4 py-2 text-right text-sm ${pnl >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                        Rs. {Math.abs(pnl).toLocaleString('en-IN', { maximumFractionDigits: 0 })}
                      </td>
                      <td className="px-4 py-2"></td>
                      <td className="px-4 py-2"></td>
                    </tr>
                  )
                })

                return [groupRow, ...subRows]
              })
            )}
          </tbody>
        </table>
        </div>
      </div>

      {invoiceHolding && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => { setInvoiceHolding(null); setInvoices([]) }}>
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] w-full max-w-lg mx-4 max-h-[80vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-5">
              <div>
                <h3 className="text-lg font-semibold">Invoices</h3>
                <p className="text-sm text-[var(--text-muted)]">{invoiceHolding.symbol} — {invoiceHolding.name}</p>
              </div>
              <button onClick={() => { setInvoiceHolding(null); setInvoices([]) }} className="text-[var(--text-secondary)] hover:text-white transition"><X className="w-5 h-5" /></button>
            </div>

            <label className="flex items-center justify-center gap-2 px-4 py-3 rounded-lg border-2 border-dashed border-[var(--border)] cursor-pointer hover:border-amber-400/50 transition mb-4">
              <Upload className="w-4 h-4 text-[var(--text-muted)]" />
              <span className="text-sm text-[var(--text-muted)]">{uploadingInvoice ? 'Uploading...' : 'Upload Invoice (PDF/Image)'}</span>
              <input type="file" accept=".pdf,.png,.jpg,.jpeg,.webp" className="hidden" onChange={handleInvoiceUpload} disabled={uploadingInvoice} />
            </label>

            {invoicesLoading ? (
              <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-[var(--text-muted)]" /></div>
            ) : invoices.length === 0 ? (
              <p className="text-center py-8 text-sm text-[var(--text-secondary)]">No invoices uploaded yet</p>
            ) : (
              <div className="space-y-2">
                {invoices.map((doc) => (
                  <div key={doc.id} className="flex items-center justify-between p-3 rounded-lg bg-[var(--bg)]/50 hover:bg-[var(--hover-bg)] transition">
                    <div className="flex items-center gap-3 min-w-0">
                      <FileText className="w-5 h-5 text-amber-400 shrink-0" />
                      <div className="min-w-0">
                        <p className="text-sm font-medium truncate">{doc.originalFilename}</p>
                        <p className="text-xs text-[var(--text-muted)]">
                          {new Date(doc.createdAt).toLocaleDateString('en-IN')}
                          {doc.fileSize ? ` · ${(doc.fileSize / 1024).toFixed(1)} KB` : ''}
                        </p>
                      </div>
                    </div>
                    <div className="flex items-center gap-1 shrink-0">
                      <button onClick={() => handleInvoicePreview(doc)} className="p-1.5 text-[var(--text-secondary)] hover:text-amber-400 transition" title="Preview">
                        <Eye className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleInvoiceDownload(doc)} className="p-1.5 text-[var(--text-secondary)] hover:text-blue-400 transition" title="Download">
                        <Download className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleInvoiceDelete(doc.id)} aria-label="Delete invoice" className="p-1.5 text-[var(--text-secondary)] hover:text-red-400 transition" title="Delete">
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {previewDoc && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/80" onClick={closePreview}>
          <div className="relative w-full max-w-4xl mx-4 max-h-[90vh]" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-3">
              <p className="text-sm text-white/70 truncate">{previewDoc.originalFilename}</p>
              <button onClick={closePreview} className="text-white/70 hover:text-white transition"><X className="w-6 h-6" /></button>
            </div>
            <div className="bg-black rounded-xl overflow-hidden flex items-center justify-center max-h-[85vh]">
              {previewDoc.contentType?.startsWith('image/') ? (
                <img src={previewDoc.blobUrl} alt={previewDoc.originalFilename} className="max-w-full max-h-[85vh] object-contain" />
              ) : previewDoc.contentType === 'application/pdf' ? (
                <iframe src={previewDoc.blobUrl} className="w-full h-[85vh]" title={previewDoc.originalFilename} />
              ) : (
                <div className="py-16 text-center text-white/50">
                  <FileText className="w-16 h-16 mx-auto mb-4 opacity-50" />
                  <p className="text-lg">Preview not available</p>
                  <button onClick={() => handleInvoiceDownload(previewDoc)} className="mt-4 px-4 py-2 bg-blue-500 rounded-lg text-sm hover:bg-blue-600 transition">Download to view</button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {newsHolding && (
        <NewsPanel holding={newsHolding} onClose={() => setNewsHolding(null)} />
      )}

      {chartHolding && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={closeChart}>
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] w-full max-w-3xl mx-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-4">
              <div>
                <h3 className="text-lg font-semibold">{chartHolding.symbol}</h3>
                <p className="text-sm text-[var(--text-muted)]">{chartHolding.name}</p>
              </div>
              <button onClick={closeChart} className="text-[var(--text-secondary)] hover:text-white transition"><X className="w-5 h-5" /></button>
            </div>

            <div className="flex items-center gap-2 mb-4 flex-wrap">
              {[{ label: '1M', days: 30 }, { label: '3M', days: 90 }, { label: '6M', days: 180 }, { label: '1Y', days: 365 }].map(p => (
                <button key={p.days} onClick={() => openChart(chartHolding, p.days)}
                  className={`px-3 py-1.5 rounded-lg text-sm transition ${chartDays === p.days ? 'bg-blue-500/20 text-blue-400' : 'bg-[var(--input-bg)] text-[var(--text-muted)] hover:bg-[var(--hover-bg)]'}`}>
                  {p.label}
                </button>
              ))}
              <div className="flex gap-1 ml-auto">
                <button onClick={() => setChartMode('recharts')}
                  className={`px-3 py-1.5 rounded-lg text-sm transition ${chartMode === 'recharts' ? 'bg-blue-500/20 text-blue-400' : 'bg-[var(--input-bg)] text-[var(--text-muted)] hover:bg-[var(--hover-bg)]'}`}>
                  Price
                </button>
                <button onClick={() => setChartMode('chart')}
                  className={`px-3 py-1.5 rounded-lg text-sm transition ${chartMode === 'chart' ? 'bg-blue-500/20 text-blue-400' : 'bg-[var(--input-bg)] text-[var(--text-muted)] hover:bg-[var(--hover-bg)]'}`}>
                  Chart
                </button>
              </div>
            </div>

            {chartLoading ? (
              <div className="flex justify-center py-16"><Loader2 className="w-6 h-6 animate-spin text-[var(--text-muted)]" /></div>
            ) : priceHistory.length === 0 ? (
              <div className="text-center py-12">
                {backfillStatus?.running ? (
                  <>
                    <Loader2 className="w-6 h-6 animate-spin text-blue-400 mx-auto mb-3" />
                    <p className="text-sm text-[var(--text-secondary)]">Backfilling NAV history...</p>
                    <p className="text-xs text-[var(--text-muted)] mt-1">
                      {backfillStatus.progressDays} / {backfillStatus.totalDays} days processed
                    </p>
                    <p className="text-xs text-[var(--text-muted)] mt-2">
                      You can close this dialog - backfill continues in background.
                    </p>
                  </>
                ) : backfillStatus?.completed && (backfillStatus?.recordsBackfilled || 0) === 0 ? (
                  <>
                    <p className="text-sm text-amber-400 mb-2">No historical NAV data available for this fund.</p>
                    <p className="text-xs text-[var(--text-muted)]">
                      AMFI may not have published NAVs for this scheme in the requested period.
                    </p>
                  </>
                ) : chartHolding?.assetType === 'MUTUAL_FUND' ? (
                  <>
                    <p className="text-sm text-[var(--text-secondary)] mb-3">No NAV history available yet.</p>
                    <button onClick={() => triggerMfBackfill(chartHolding)}
                      className="px-4 py-2 rounded-lg bg-blue-500/20 text-blue-400 text-sm hover:bg-blue-500/30 transition">
                      Fetch NAV History
                    </button>
                    <p className="text-xs text-[var(--text-muted)] mt-3">
                      This downloads NAVs from AMFI (may take a few minutes).
                    </p>
                  </>
                ) : chartHolding?.assetType === 'EQUITY' || chartHolding?.assetType === 'ETF' ? (
                  <>
                    <p className="text-sm text-[var(--text-secondary)] mb-3">No price history available yet.</p>
                    <button onClick={() => triggerEquityBackfill(chartHolding)}
                      className="px-4 py-2 rounded-lg bg-blue-500/20 text-blue-400 text-sm hover:bg-blue-500/30 transition">
                      Fetch Price History
                    </button>
                    <p className="text-xs text-[var(--text-muted)] mt-3">
                      Downloads OHLC data via Upstox API.
                    </p>
                  </>
                ) : (
                  <p className="text-sm text-[var(--text-secondary)]">Price history not available for this asset type.</p>
                )}
              </div>
            ) : chartStats ? <div>
                <div className="grid grid-cols-4 gap-3 mb-4 text-sm">
                  {[
                    { label: 'High', val: chartStats.high, color: 'text-green-400' },
                    { label: 'Low', val: chartStats.low, color: 'text-red-400' },
                    { label: 'Avg', val: chartStats.avg, color: 'text-blue-400' },
                    { label: 'Change', val: chartStats.change, pct: chartStats.changePct, color: chartStats.isUp ? 'text-green-400' : 'text-red-400' },
                  ].map(s => (
                    <div key={s.label} className="bg-[var(--input-bg)] rounded-lg p-2.5 text-center">
                      <div className="text-[10px] text-[var(--text-muted)] uppercase tracking-wider">{s.label}</div>
                      <div className={`font-semibold ${s.color}`}>
                        Rs. {Number(s.val).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                        {s.pct != null && <span className="text-[11px] ml-1 opacity-70">({s.pct >= 0 ? '+' : ''}{s.pct.toFixed(2)}%)</span>}
                      </div>
                    </div>
                  ))}
                </div>
                <div className="relative h-64">
                  <div className="h-64" style={{ visibility: chartMode === 'recharts' ? 'visible' : 'hidden' }}>
                <ResponsiveContainer width="100%" height={256}>
                  <AreaChart data={priceHistory}>
                    <defs>
                      <linearGradient id={chartStats.gradId} x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor={chartStats.lineColor} stopOpacity={0.2} />
                        <stop offset="95%" stopColor={chartStats.lineColor} stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" strokeOpacity={0.4} />
                    <XAxis dataKey="date" stroke="var(--text-muted)" tick={{ fontSize: 11 }}
                      tickFormatter={(val) => { const d = new Date(val); return d.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' }) }} />
                    <YAxis stroke="var(--text-muted)" tick={{ fontSize: 11 }} domain={['dataMin - 1', 'dataMax + 1']}
                      tickFormatter={(val) => {
                        if (val >= 100000) return `Rs ${(val / 100000).toFixed(1)}L`;
                        if (val >= 1000) return `Rs ${(val / 1000).toFixed(0)}K`;
                        return `Rs ${val}`;
                      }} />
                    <Tooltip contentStyle={{ backgroundColor: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '8px', fontSize: '12px' }}
                      formatter={(val, name) => [<span className="font-mono">Rs. {Number(val).toLocaleString('en-IN', { maximumFractionDigits: 2 })}</span>, 'NAV']}
                      labelFormatter={(val) => {
                        const p = priceHistory.find(d => d.date === val)
                        if (!p) return new Date(val).toLocaleDateString('en-IN', { month: 'long', day: 'numeric', year: 'numeric' })
                        const d = new Date(val)
                        let tooltip = d.toLocaleDateString('en-IN', { weekday: 'short', month: 'long', day: 'numeric', year: 'numeric' })
                        if (p.open != null) tooltip += `\nO: ${Number(p.open).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
                        if (p.high != null) tooltip += `  H: ${Number(p.high).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
                        if (p.low != null) tooltip += `  L: ${Number(p.low).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
                        if (p.close != null) tooltip += `  C: ${Number(p.close).toLocaleString('en-IN', { maximumFractionDigits: 2 })}`
                        if (p.volume != null) tooltip += `\nVol: ${Number(p.volume).toLocaleString('en-IN')}`
                        return tooltip
                      }} />
                    <Area type="monotone" dataKey="close" stroke={chartStats.lineColor} strokeWidth={2} fill={`url(#${chartStats.gradId})`} name="close"
                      dot={<circle r={0} />} activeDot={<circle r={4} fill={chartStats.lineColor} stroke="var(--bg-card)" strokeWidth={2} />} />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
                  <div id="lw_chart_container" className="absolute inset-0" style={{ visibility: chartMode === 'chart' ? 'visible' : 'hidden' }} />
                </div>
              </div> : null}
          </div>
        </div>
      )}

      {/* Dividend Detail Modal */}
      {dividendHolding && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => { setDividendHolding(null); setDividendRecords([]) }}>
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] w-full max-w-lg mx-4 max-h-[80vh] overflow-y-auto" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between mb-5">
              <div>
                <h3 className="text-lg font-semibold">{dividendHolding.symbol} - Dividends</h3>
                <p className="text-sm text-[var(--text-muted)]">
                  {dividendRecords.length} record{dividendRecords.length !== 1 ? 's' : ''}
                </p>
              </div>
              <button onClick={() => { setDividendHolding(null); setDividendRecords([]) }} className="text-[var(--text-secondary)] hover:text-[var(--text)] transition p-1">
                <X className="w-5 h-5" />
              </button>
            </div>

            {dividendRecordsLoading ? (
              <div className="flex items-center justify-center py-12">
                <Loader2 className="w-6 h-6 animate-spin text-blue-400" />
              </div>
            ) : dividendRecords.length === 0 ? (
              <div className="text-center py-12">
                <IndianRupee className="w-12 h-12 mx-auto text-[var(--text-muted)] mb-3" />
                <p className="text-[var(--text-secondary)]">No dividend records yet.</p>
                <button
                  onClick={() => { setDividendHolding(null); setDividendRecords([]); handleCalculateDividends() }}
                  className="mt-3 text-sm text-blue-400 hover:text-blue-300 transition"
                >
                  Calculate dividends now
                </button>
              </div>
            ) : (
              <div className="space-y-2">
                {dividendRecords.map((rec, i) => (
                  <div key={rec.id || i} className="flex items-center justify-between p-3 rounded-lg bg-[var(--input-bg)]/50">
                    <div className="flex items-center gap-3">
                      <Calendar className="w-4 h-4 text-[var(--text-muted)]" />
                      <div>
                        <p className="text-sm font-medium">
                          {rec.recordDate
                            ? new Date(rec.recordDate).toLocaleDateString('en-IN', { month: 'short', day: 'numeric', year: 'numeric' })
                            : rec.exDate
                              ? new Date(rec.exDate).toLocaleDateString('en-IN', { month: 'short', day: 'numeric', year: 'numeric' })
                              : 'Unknown'}
                        </p>
                        <p className="text-xs text-[var(--text-muted)]">
                          {rec.dividendType || 'Dividend'} {rec.reinvested ? '(Reinvested)' : ''}
                        </p>
                      </div>
                    </div>
                    <p className="text-sm font-medium text-green-400">
                      +Rs. {Number(rec.dividendAmount).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                    </p>
                  </div>
                ))}
                <div className="flex items-center justify-between p-3 rounded-lg bg-green-500/10 border border-green-500/20 mt-3">
                  <p className="text-sm font-semibold">Total</p>
                  <p className="text-sm font-bold text-green-400">
                    Rs. {dividendRecords.reduce((s, r) => s + Number(r.dividendAmount || 0), 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function ShimmerBar({ className = '' }) {
  return (
    <div className={`relative overflow-hidden bg-[var(--border)] rounded ${className}`}>
      <div className="absolute inset-0 -translate-x-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-[var(--hover-bg)] to-transparent" />
    </div>
  )
}

function HoldingsSkeletonLoader() {
  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      {/* Header skeleton */}
      <div className="flex items-center justify-between">
        <div>
          <ShimmerBar className="h-7 w-36 mb-2" />
          <ShimmerBar className="h-4 w-56" />
        </div>
        <ShimmerBar className="h-10 w-32 rounded-lg" />
      </div>

      {/* Summary cards skeleton */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        {[0, 1, 2, 3, 4].map(i => (
          <div key={i} className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
            <ShimmerBar className="h-3 w-20 mb-3" />
            <ShimmerBar className="h-6 w-28" />
          </div>
        ))}
      </div>

      {/* Charts skeleton */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
          <ShimmerBar className="h-5 w-28 mb-4" />
          <div className="flex justify-center py-6">
            <div className="w-44 h-44 rounded-full border-[12px] border-[var(--border)] relative overflow-hidden">
              <div className="absolute inset-0 -translate-x-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-[var(--hover-bg)] to-transparent" />
            </div>
          </div>
          <div className="flex justify-center gap-4 mt-2">
            {[0, 1, 2].map(i => <ShimmerBar key={i} className="h-3 w-16" />)}
          </div>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
          <ShimmerBar className="h-5 w-36 mb-4" />
          <div className="h-56 flex items-end gap-2 px-4">
            {[40, 55, 45, 65, 50, 70, 60, 75, 68, 80, 72, 85].map((h, i) => (
              <div key={i} className="flex-1 relative overflow-hidden rounded-t" style={{ height: `${h}%` }}>
                <div className="absolute inset-0 bg-[var(--border)]" />
                <div className="absolute inset-0 -translate-x-full animate-[shimmer_1.5s_infinite] bg-gradient-to-r from-transparent via-[var(--hover-bg)] to-transparent" style={{ animationDelay: `${i * 100}ms` }} />
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Table skeleton */}
      <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
        {/* Tab bar skeleton */}
        <div className="px-4 py-3 border-b border-[var(--border)] flex gap-2">
          {[0, 1, 2, 3, 4, 5].map(i => (
            <ShimmerBar key={i} className="h-8 w-16 rounded-lg" />
          ))}
        </div>
        {/* Table header */}
        <div className="px-4 py-3 bg-[var(--bg)]/50 flex gap-4">
          <ShimmerBar className="h-3 w-32" />
          <ShimmerBar className="h-3 w-16" />
          <ShimmerBar className="h-3 w-12" />
          <ShimmerBar className="h-3 w-16 ml-auto" />
          <ShimmerBar className="h-3 w-20" />
          <ShimmerBar className="h-3 w-16" />
        </div>
        {/* Table rows */}
        {[0, 1, 2, 3, 4, 5, 6].map(i => (
          <div key={i} className="px-4 py-4 border-t border-[var(--border)] flex items-center gap-4" style={{ animationDelay: `${i * 80}ms` }}>
            <div className="flex items-center gap-3 flex-1">
              <ShimmerBar className="w-8 h-8 rounded-lg shrink-0" />
              <div>
                <ShimmerBar className="h-4 w-24 mb-1.5" />
                <ShimmerBar className="h-3 w-36" />
              </div>
            </div>
            <ShimmerBar className="h-5 w-12 rounded" />
            <ShimmerBar className="h-4 w-12" />
            <ShimmerBar className="h-4 w-16" />
            <ShimmerBar className="h-4 w-20" />
            <ShimmerBar className="h-4 w-16" />
            <ShimmerBar className="h-4 w-8" />
          </div>
        ))}
      </div>
    </div>
  )
}
