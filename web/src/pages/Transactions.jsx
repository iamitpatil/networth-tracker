import { useState, useEffect, useMemo } from 'react'
import client from '../api/client'
import { useFamilyView } from '../context/FamilyViewContext'
import { TrendingUp, TrendingDown, Repeat, DollarSign, Filter, Calendar, Search, Users } from 'lucide-react'

export default function Transactions() {
  const { view: familyView } = useFamilyView()
  const isFamilyView = familyView === 'family'
  const [transactions, setTransactions] = useState([])
  const [holdings, setHoldings] = useState([])
  const [loading, setLoading] = useState(true)
  const [activeFilter, setActiveFilter] = useState('ALL')
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [assetFilter, setAssetFilter] = useState('ALL')
  const [symbolSearch, setSymbolSearch] = useState('')

  useEffect(() => {
    Promise.all([client.get('/portfolio/transactions'), client.get('/portfolio/holdings')])
      .then(([tx, h]) => {
        setTransactions(tx.data || [])
        setHoldings(h.data || [])
      })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const holdingsMap = useMemo(() => {
    const map = {}
    holdings.forEach((h) => { map[h.id] = h })
    return map
  }, [holdings])

  const summary = useMemo(() => {
    const buys = transactions.filter((t) => t.transactionType === 'BUY').length
    const sells = transactions.filter((t) => t.transactionType === 'SELL').length
    const sips = transactions.filter((t) => t.transactionType === 'SIP').length
    const totalVolume = transactions.reduce((sum, t) => sum + (t.amount || 0), 0)
    return { buys, sells, sips, totalVolume }
  }, [transactions])

  const assetTypes = useMemo(() => {
    const types = new Set()
    holdings.forEach((h) => { if (h.assetType) types.add(h.assetType) })
    return ['ALL', ...Array.from(types).sort()]
  }, [holdings])

  const filtered = useMemo(() => {
    let items = transactions

    if (activeFilter !== 'ALL') items = items.filter((t) => t.transactionType === activeFilter)
    if (assetFilter !== 'ALL') {
      const holdingIds = new Set(holdings.filter((h) => h.assetType === assetFilter).map((h) => h.id))
      items = items.filter((t) => holdingIds.has(t.holdingId))
    }
    if (symbolSearch) {
      const search = symbolSearch.toLowerCase()
      const holdingIds = new Set(holdings.filter((h) => h.symbol?.toLowerCase().includes(search) || h.name?.toLowerCase().includes(search)).map((h) => h.id))
      items = items.filter((t) => holdingIds.has(t.holdingId))
    }
    if (dateFrom) items = items.filter((t) => new Date(t.transactionDate) >= new Date(dateFrom))
    if (dateTo) items = items.filter((t) => new Date(t.transactionDate) <= new Date(dateTo + 'T23:59:59'))

    return [...items].sort((a, b) => new Date(b.transactionDate) - new Date(a.transactionDate))
  }, [transactions, activeFilter, assetFilter, symbolSearch, dateFrom, dateTo, holdings])

  const fmt = (v) => Number(v).toLocaleString('en-IN')

  const typeColors = {
    BUY: 'text-green-500 bg-green-500/10',
    SELL: 'text-red-400 bg-red-400/10',
    SIP: 'text-green-400 bg-green-400/10',
    LUMPSUM: 'text-amber-400 bg-amber-400/10',
  }

  if (loading) return <div className="flex justify-center py-20 text-slate-400">Loading...</div>

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Transactions</h1>
        <p className="text-slate-400 text-sm mt-1">{transactions.length} total · {filtered.length} shown</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <div className="flex items-center justify-between">
            <div><p className="text-slate-400 text-sm">Total Buys</p><p className="text-2xl font-bold text-blue-400 mt-1">{summary.buys}</p></div>
            <TrendingUp className="w-8 h-8 text-blue-400/30" />
          </div>
        </div>
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <div className="flex items-center justify-between">
            <div><p className="text-slate-400 text-sm">Total Sells</p><p className="text-2xl font-bold text-red-400 mt-1">{summary.sells}</p></div>
            <TrendingDown className="w-8 h-8 text-red-400/30" />
          </div>
        </div>
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <div className="flex items-center justify-between">
            <div><p className="text-slate-400 text-sm">Total SIPs</p><p className="text-2xl font-bold text-green-400 mt-1">{summary.sips}</p></div>
            <Repeat className="w-8 h-8 text-green-400/30" />
          </div>
        </div>
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <div className="flex items-center justify-between">
            <div><p className="text-slate-400 text-sm">Total Volume</p><p className="text-xl font-bold text-amber-400 mt-1">Rs. {fmt(summary.totalVolume)}</p></div>
            <DollarSign className="w-8 h-8 text-amber-400/30" />
          </div>
        </div>
      </div>

      <div className="bg-slate-800 rounded-xl p-4 border border-slate-700">
        <div className="flex items-center gap-3">
          <Filter className="w-4 h-4 text-slate-400 shrink-0" />

          <div className="flex items-center gap-1">
            <Calendar className="w-3.5 h-3.5 text-slate-500" />
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)}
              className="bg-slate-700 border border-slate-600 rounded px-2 py-1 text-xs text-slate-200 w-28" />
            <span className="text-slate-500 text-xs">-</span>
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)}
              className="bg-slate-700 border border-slate-600 rounded px-2 py-1 text-xs text-slate-200 w-28" />
          </div>

          <div className="flex items-center gap-1">
            <Search className="w-3.5 h-3.5 text-slate-500" />
            <input type="text" placeholder="Symbol..." value={symbolSearch} onChange={(e) => setSymbolSearch(e.target.value)}
              className="bg-slate-700 border border-slate-600 rounded px-2 py-1 text-xs text-slate-200 w-24 placeholder-slate-500" />
          </div>

          <select value={assetFilter} onChange={(e) => setAssetFilter(e.target.value)}
            className="bg-slate-700 border border-slate-600 rounded px-2 py-1 text-xs text-slate-200">
            {assetTypes.map((a) => <option key={a} value={a}>{a === 'ALL' ? 'All' : a.replace('_', ' ')}</option>)}
          </select>

          <div className="flex items-center gap-1">
            {['ALL', 'BUY', 'SELL', 'SIP', 'LUMPSUM'].map((f) => (
              <button key={f} onClick={() => setActiveFilter(f)}
                className={`px-2 py-1 rounded text-xs font-medium transition-colors ${
                  activeFilter === f ? 'bg-blue-500/20 text-blue-400 border border-blue-500/40' : 'bg-slate-700 text-slate-400 hover:bg-slate-600 border border-slate-600'
                }`}>{f}</button>
            ))}
          </div>

          {(dateFrom || dateTo || symbolSearch || assetFilter !== 'ALL' || activeFilter !== 'ALL') && (
            <button onClick={() => { setDateFrom(''); setDateTo(''); setSymbolSearch(''); setAssetFilter('ALL'); setActiveFilter('ALL') }}
              className="text-xs text-red-400 hover:text-red-300 shrink-0">Clear</button>
          )}
        </div>
      </div>

      <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden">
        <table className="w-full">
          <thead className="bg-slate-700/50 text-left">
            <tr>
              <th className="px-4 py-3 text-sm font-medium text-slate-400">Date</th>
              <th className="px-4 py-3 text-sm font-medium text-slate-400">Holding</th>
              <th className="px-4 py-3 text-sm font-medium text-slate-400">Type</th>
              <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Qty</th>
              <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Price</th>
              <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Amount</th>
              <th className="px-4 py-3 text-sm font-medium text-slate-400">Broker</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-700">
            {filtered.length === 0 ? (
              <tr><td colSpan="7" className="px-4 py-12 text-center text-slate-500">No transactions found</td></tr>
            ) : (
              filtered.map((tx) => (
                <tr key={tx.id} className="hover:bg-slate-700/30">
                  <td className="px-4 py-3 text-sm">{new Date(tx.transactionDate).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })}</td>
                  <td className="px-4 py-3 text-sm">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-medium">{holdingsMap[tx.holdingId]?.symbol || tx.holdingId?.substring(0, 8)}</span>
                      {isFamilyView && tx.ownerName && (
                        <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium bg-purple-500/15 text-purple-400 ring-1 ring-inset ring-purple-500/30">
                          <Users className="w-2.5 h-2.5" />
                          {tx.ownerName}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${typeColors[tx.transactionType] || 'text-slate-400 bg-slate-700'}`}>
                      {tx.transactionType}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right text-sm">{tx.quantity}</td>
                  <td className="px-4 py-3 text-right text-sm">Rs. {fmt(tx.price)}</td>
                  <td className="px-4 py-3 text-right text-sm font-medium">Rs. {fmt(tx.amount)}</td>
                  <td className="px-4 py-3 text-sm text-slate-500">{tx.broker || '-'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
