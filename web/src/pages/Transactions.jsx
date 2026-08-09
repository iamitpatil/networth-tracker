import { useState, useEffect, useMemo, useRef } from 'react'
import { toast } from 'sonner'
import client from '../api/client'
import { useFamilyView } from '../context/FamilyViewContext'
import { TrendingUp, TrendingDown, Repeat, DollarSign, Users, Upload, Download, Loader2 } from 'lucide-react'
import { PageSkeleton, ColumnFilter, Modal } from '../components/ui'

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
  const [brokerFilter, setBrokerFilter] = useState('ALL')
  const [importOpen, setImportOpen] = useState(false)
  const [importing, setImporting] = useState(false)
  const [importResult, setImportResult] = useState(null)
  const fileRef = useRef(null)

  useEffect(() => {
    Promise.all([client.get('/portfolio/transactions'), client.get('/portfolio/holdings')])
      .then(([tx, h]) => {
        setTransactions(tx.data || [])
        setHoldings(h.data || [])
      })
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const reload = () =>
    Promise.all([client.get('/portfolio/transactions'), client.get('/portfolio/holdings')])
      .then(([tx, h]) => { setTransactions(tx.data || []); setHoldings(h.data || []) })

  const downloadSample = async () => {
    try {
      // Fetched through the API client so the auth header travels with it; a plain link
      // would hit the endpoint unauthenticated.
      const { data } = await client.get('/portfolio/transactions/import/sample', { responseType: 'blob' })
      const url = URL.createObjectURL(new Blob([data], { type: 'text/csv' }))
      const a = document.createElement('a')
      a.href = url
      a.download = 'transactions-sample.csv'
      a.click()
      URL.revokeObjectURL(url)
    } catch {
      toast.error('Could not download the sample')
    }
  }

  const uploadCsv = async (file) => {
    if (!file) return
    setImporting(true)
    setImportResult(null)
    try {
      const form = new FormData()
      form.append('file', file)
      const { data } = await client.post('/portfolio/transactions/import', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setImportResult(data)
      if (data.imported > 0) {
        toast.success(`Imported ${data.imported} transaction${data.imported === 1 ? '' : 's'}`,
          { description: data.failed ? `${data.failed} row(s) could not be imported` : undefined })
        await reload()
      } else if (data.failed) {
        toast.error('Nothing imported', { description: `${data.failed} row(s) failed` })
      }
    } catch (e) {
      // A file-level problem (missing columns, unreadable) comes back as a 400 with a message.
      const msg = e?.response?.data?.message || e?.response?.data?.error || 'Import failed'
      setImportResult({ fileError: msg })
      toast.error(msg)
    } finally {
      setImporting(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

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

  const brokers = useMemo(() => {
    const found = new Set()
    transactions.forEach((t) => { if (t.broker) found.add(t.broker) })
    return Array.from(found).sort()
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
    if (brokerFilter !== 'ALL') {
      items = items.filter((t) => (t.broker || '') === (brokerFilter === 'NONE' ? '' : brokerFilter))
    }
    if (dateFrom) items = items.filter((t) => new Date(t.transactionDate) >= new Date(dateFrom))
    if (dateTo) items = items.filter((t) => new Date(t.transactionDate) <= new Date(dateTo + 'T23:59:59'))

    return [...items].sort((a, b) => new Date(b.transactionDate) - new Date(a.transactionDate))
  }, [transactions, activeFilter, assetFilter, symbolSearch, brokerFilter, dateFrom, dateTo, holdings])

  const fmt = (v) => Number(v).toLocaleString('en-IN')

  const typeColors = {
    BUY: 'text-green-500 bg-green-500/10',
    SELL: 'text-red-400 bg-red-400/10',
    SIP: 'text-green-400 bg-green-400/10',
    LUMPSUM: 'text-amber-400 bg-amber-400/10',
    // Corporate actions share a purple family so they read as one group, visibly apart from
    // the green/red of money actually moving.
    BONUS: 'text-purple-400 bg-purple-400/10',
    SPLIT: 'text-purple-300 bg-purple-300/10',
    DEMERGER_IN: 'text-indigo-400 bg-indigo-400/10',
    DEMERGER_OUT: 'text-indigo-300 bg-indigo-300/10',
  }

  /** Types where no money moved, so a price of 0 is correct rather than missing data. */
  const NON_CASH = new Set(['BONUS', 'SPLIT', 'DEMERGER_IN', 'DEMERGER_OUT'])

  const anyFilterActive = dateFrom || dateTo || symbolSearch || assetFilter !== 'ALL'
    || activeFilter !== 'ALL' || brokerFilter !== 'ALL'

  const clearFilters = () => {
    setDateFrom(''); setDateTo(''); setSymbolSearch('')
    setAssetFilter('ALL'); setActiveFilter('ALL'); setBrokerFilter('ALL')
  }

  // Native inputs rather than the app's StyledSelect for these: StyledSelect positions its
  // menu absolutely, and inside the table's horizontally scrolling container that menu gets
  // clipped instead of overlaying the rows.
  const cellInput = 'w-full bg-[var(--input-bg)] border border-[var(--border)] rounded px-2 py-1 text-xs text-[var(--text)] placeholder-[var(--text-secondary)] focus:outline-none focus:ring-1 focus:ring-blue-500/50'

  if (loading) return <PageSkeleton />

  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-bold">Transactions</h1>
          <p className="text-[var(--text-muted)] text-sm mt-1">
            {transactions.length} total · {filtered.length} shown
            {anyFilterActive && (
              <button onClick={clearFilters} className="ml-3 text-xs text-red-400 hover:text-red-300">
                Clear filters
              </button>
            )}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={downloadSample}
            className="px-3 py-2 rounded-lg text-sm flex items-center gap-2 bg-[var(--input-bg)] border border-[var(--border)] hover:bg-[var(--hover-bg)] transition">
            <Download className="w-4 h-4" /> Sample CSV
          </button>
          <button onClick={() => { setImportResult(null); setImportOpen(true) }}
            className="px-4 py-2 rounded-lg text-sm flex items-center gap-2 bg-blue-500 hover:bg-blue-600 text-white transition">
            <Upload className="w-4 h-4" /> Import Transactions
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)] transition-all duration-200 hover:border-blue-500/30 hover:-translate-y-0.5">
          <div className="flex items-center justify-between">
            <div><p className="text-[var(--text-muted)] text-sm">Total Buys</p><p className="text-2xl font-bold text-blue-400 mt-1">{summary.buys}</p></div>
            <TrendingUp className="w-8 h-8 text-blue-400/30" />
          </div>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <div className="flex items-center justify-between">
            <div><p className="text-[var(--text-muted)] text-sm">Total Sells</p><p className="text-2xl font-bold text-red-400 mt-1">{summary.sells}</p></div>
            <TrendingDown className="w-8 h-8 text-red-400/30" />
          </div>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <div className="flex items-center justify-between">
            <div><p className="text-[var(--text-muted)] text-sm">Total SIPs</p><p className="text-2xl font-bold text-green-400 mt-1">{summary.sips}</p></div>
            <Repeat className="w-8 h-8 text-green-400/30" />
          </div>
        </div>
        <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
          <div className="flex items-center justify-between">
            <div><p className="text-[var(--text-muted)] text-sm">Total Volume</p><p className="text-xl font-bold text-amber-400 mt-1">Rs. {fmt(summary.totalVolume)}</p></div>
            <DollarSign className="w-8 h-8 text-amber-400/30" />
          </div>
        </div>
      </div>

      <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
        <div className="overflow-x-auto scrollbar-thin">
          <table className="w-full min-w-[680px]">
          <thead className="bg-[var(--input-bg)] text-left">
            {/* Each filterable column carries a funnel next to its label. The icon tints when
                that column is filtered, so which columns are narrowing the data is visible
                without opening anything. Qty, Price and Amount have no filter, so no icon. */}
            <tr>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">
                <span className="inline-flex items-center gap-1.5">
                  Date
                  <ColumnFilter label="Date" active={!!(dateFrom || dateTo)}
                    onClear={() => { setDateFrom(''); setDateTo('') }}>
                    <label className="block text-[10px] text-[var(--text-secondary)]">From</label>
                    <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)}
                      className={cellInput} />
                    <label className="block text-[10px] text-[var(--text-secondary)]">To</label>
                    <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)}
                      className={cellInput} />
                  </ColumnFilter>
                </span>
              </th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">
                <span className="inline-flex items-center gap-1.5">
                  Holding
                  <ColumnFilter label="Holding" active={!!symbolSearch || assetFilter !== 'ALL'}
                    onClear={() => { setSymbolSearch(''); setAssetFilter('ALL') }}>
                    <input type="text" placeholder="Search symbol or name" value={symbolSearch}
                      onChange={(e) => setSymbolSearch(e.target.value)} className={cellInput} />
                    <select aria-label="Asset type" value={assetFilter}
                      onChange={(e) => setAssetFilter(e.target.value)} className={cellInput}>
                      {assetTypes.map((a) => (
                        <option key={a} value={a}>{a === 'ALL' ? 'All asset types' : a.replace('_', ' ')}</option>
                      ))}
                    </select>
                  </ColumnFilter>
                </span>
              </th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">
                <span className="inline-flex items-center gap-1.5">
                  Type
                  <ColumnFilter label="Type" active={activeFilter !== 'ALL'}
                    onClear={() => setActiveFilter('ALL')}>
                    {/* Buttons here rather than a select: the panel has room, and one tap
                        picks a type instead of two to open and choose. */}
                    <div className="grid grid-cols-2 gap-1">
                      {['ALL', 'BUY', 'SELL', 'SIP', 'LUMPSUM', 'BONUS', 'SPLIT', 'DEMERGER_IN', 'DEMERGER_OUT'].map((f) => (
                        <button key={f} type="button" onClick={() => setActiveFilter(f)}
                          className={`px-2 py-1 rounded text-xs font-medium transition-colors ${
                            activeFilter === f
                              ? 'bg-blue-500/20 text-blue-400 border border-blue-500/40'
                              : 'bg-[var(--input-bg)] text-[var(--text-muted)] hover:bg-[var(--hover-bg)] border border-[var(--border)]'
                          }`}>{f.replace('DEMERGER_', 'DEM. ')}</button>
                      ))}
                    </div>
                  </ColumnFilter>
                </span>
              </th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Qty</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Price</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Amount</th>
              <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">
                <span className="inline-flex items-center gap-1.5">
                  Broker
                  <ColumnFilter label="Broker" active={brokerFilter !== 'ALL'}
                    onClear={() => setBrokerFilter('ALL')}>
                    <select aria-label="Broker" value={brokerFilter}
                      onChange={(e) => setBrokerFilter(e.target.value)} className={cellInput}>
                      <option value="ALL">All brokers</option>
                      {brokers.map((b) => <option key={b} value={b}>{b}</option>)}
                      <option value="NONE">(none)</option>
                    </select>
                  </ColumnFilter>
                </span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[var(--border)]">
            {filtered.length === 0 ? (
              <tr><td colSpan="7" className="px-4 py-12 text-center text-[var(--text-secondary)]">No transactions found</td></tr>
            ) : (
              filtered.map((tx) => (
                <tr key={tx.id} className="hover:bg-[var(--hover-bg)]">
                  <td className="px-4 py-3 text-sm">{new Date(tx.transactionDate).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })}</td>
                  <td className="px-4 py-3 text-sm">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-medium">{holdingsMap[tx.holdingId]?.symbol || tx.holdingId?.substring(0, 8)}</span>
                      {tx.acquisitionDate && (
                        <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-medium bg-indigo-500/15 text-indigo-300 ring-1 ring-inset ring-indigo-500/30"
                          title={'Holding period counted from ' + new Date(tx.acquisitionDate).toLocaleDateString('en-IN')
                            + ' — inherited from the original shares, so these may already be long-term'}>
                          held since {new Date(tx.acquisitionDate).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })}
                        </span>
                      )}
                      {isFamilyView && tx.ownerName && (
                        <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-medium bg-purple-500/15 text-purple-400 ring-1 ring-inset ring-purple-500/30">
                          <Users className="w-2.5 h-2.5" />
                          {tx.ownerName}
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-1 rounded text-xs font-medium ${typeColors[tx.transactionType] || 'text-[var(--text-muted)] bg-[var(--input-bg)]'}`}>
                      {tx.transactionType}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right text-sm">{tx.quantity}</td>
                  <td className="px-4 py-3 text-right text-sm">
                    {NON_CASH.has(tx.transactionType) && Number(tx.price) === 0
                      // "Rs. 0" reads as a data problem. Bonus shares genuinely cost nothing,
                      // and a split has no price at all, so say so.
                      ? <span className="text-[var(--text-secondary)] text-xs"
                          title={tx.transactionType === 'BONUS'
                            ? 'Bonus shares carry nil cost of acquisition'
                            : 'No price: this row only re-denominates existing shares'}>
                          {tx.transactionType === 'BONUS' ? 'nil cost' : '—'}
                        </span>
                      : <>Rs. {fmt(tx.price)}</>}
                  </td>
                  <td className="px-4 py-3 text-right text-sm font-medium">
                    {NON_CASH.has(tx.transactionType) && Number(tx.amount) === 0
                      ? <span className="text-[var(--text-secondary)] text-xs">no cash</span>
                      : <>Rs. {fmt(tx.amount)}</>}
                  </td>
                  <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">{tx.broker || '-'}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
        </div>
      </div>
      <Modal open={importOpen} onClose={() => setImportOpen(false)} title="Import transactions">
        <div className="space-y-4">
          <p className="text-sm text-[var(--text-secondary)]">
            Upload a CSV with one transaction per row. Valid rows are imported even if others
            fail, and anything rejected is listed below with its row number.
          </p>
          <div className="text-xs text-[var(--text-muted)] bg-[var(--input-bg)] border border-[var(--border)] rounded-lg p-3">
            <p className="font-medium text-[var(--text-secondary)] mb-1">Required columns</p>
            <code className="block break-words">symbol, assetType, transactionType, quantity, price, transactionDate</code>
            <p className="mt-2">Optional: <code>ratio</code>, <code>broker</code>, <code>notes</code>. A
            symbol with no holding yet has one created for it.</p>
            <p className="mt-2 text-[var(--text-secondary)]">
              <span className="font-medium">Corporate actions:</span> a <code>BONUS</code> row puts the
              free shares in <code>quantity</code> with <code>price</code> 0. A <code>SPLIT</code> row
              needs <code>ratio</code> as before:after, e.g. <code>1:2</code>. Both must come after the
              purchase they act on. Demergers need the company's cost apportionment, so they are applied
              from the holding's Corporate action form instead.
            </p>
            <button onClick={downloadSample} className="mt-2 text-blue-400 hover:text-blue-300">
              Download the sample
            </button>
          </div>

          <input ref={fileRef} type="file" accept=".csv,text/csv" onChange={(e) => uploadCsv(e.target.files?.[0])}
            disabled={importing}
            className="w-full text-sm text-[var(--text-secondary)] file:mr-3 file:py-2 file:px-3 file:rounded-lg file:border-0 file:text-sm file:bg-blue-500 file:text-white hover:file:bg-blue-600 file:cursor-pointer disabled:opacity-50" />

          {importing && (
            <p className="text-sm text-[var(--text-muted)] flex items-center gap-2">
              <Loader2 className="w-4 h-4 animate-spin" /> Importing...
            </p>
          )}

          {importResult?.fileError && (
            <p className="text-sm text-red-400">{importResult.fileError}</p>
          )}

          {importResult && !importResult.fileError && (
            <div className="space-y-2">
              <div className="flex items-center gap-4 text-sm">
                <span className="text-green-400">{importResult.imported} imported</span>
                {importResult.failed > 0 && <span className="text-red-400">{importResult.failed} failed</span>}
                <span className="text-[var(--text-muted)]">of {importResult.totalRows} rows</span>
              </div>
              {importResult.createdHoldings?.length > 0 && (
                <p className="text-xs text-[var(--text-muted)]">
                  New holdings created: {importResult.createdHoldings.join(', ')}
                </p>
              )}
              {importResult.errors?.length > 0 && (
                <div className="max-h-40 overflow-y-auto border border-[var(--border)] rounded-lg divide-y divide-[var(--border)]">
                  {importResult.errors.map((err, i) => (
                    <div key={i} className="px-3 py-2 text-xs">
                      <span className="text-red-400 font-medium">Row {err.row}</span>
                      {err.symbol && <span className="text-[var(--text-muted)]"> · {err.symbol}</span>}
                      <span className="block text-[var(--text-secondary)]">{err.reason}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </Modal>
    </div>
  )
}
