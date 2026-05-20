import { useState, useEffect } from 'react'
import { Link2, Unlink, Loader2, Search, X, Percent } from 'lucide-react'
import client from '../api/client'
import { toast } from 'sonner'

export default function GoalHoldingLinker({ goal, onClose, onUpdate }) {
  const [linkedHoldings, setLinkedHoldings] = useState([])
  const [allHoldings, setAllHoldings] = useState([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [linking, setLinking] = useState(null)

  useEffect(() => {
    Promise.all([
      client.get(`/goals/${goal.id}/holdings`),
      client.get('/portfolio/holdings'),
    ]).then(([linked, all]) => {
      setLinkedHoldings(linked.data || [])
      setAllHoldings(all.data || [])
    }).catch(console.error)
      .finally(() => setLoading(false))
  }, [goal.id])

  const linkedIds = new Set(linkedHoldings.map(l => l.holdingId?.toString()))

  const availableHoldings = allHoldings.filter(h => {
    if (linkedIds.has(h.id?.toString())) return false
    if (!search) return true
    const q = search.toLowerCase()
    return h.symbol?.toLowerCase().includes(q) || h.name?.toLowerCase().includes(q)
  })

  const handleLink = async (holding, pct = 100) => {
    setLinking(holding.id)
    try {
      await client.post(`/goals/${goal.id}/holdings`, {
        holdingId: holding.id,
        allocationPct: pct,
      })
      const { data } = await client.get(`/goals/${goal.id}/holdings`)
      setLinkedHoldings(data || [])
      onUpdate?.()
      toast.success(`${holding.symbol} linked to goal`)
    } catch (err) {
      const msg = err.response?.data?.message || err.message
      toast.error('Cannot link holding', { description: msg })
    } finally {
      setLinking(null)
    }
  }

  const handleUnlink = async (holdingId, symbol) => {
    try {
      await client.delete(`/goals/${goal.id}/holdings/${holdingId}`)
      setLinkedHoldings(prev => prev.filter(l => l.holdingId?.toString() !== holdingId?.toString()))
      onUpdate?.()
      toast.success(`${symbol} unlinked`)
    } catch (err) {
      toast.error('Failed to unlink', { description: err.message })
    }
  }

  const totalAllocated = linkedHoldings.reduce((s, l) => s + (l.allocatedValue || 0), 0)

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={onClose}>
      <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] w-full max-w-lg mx-4 max-h-[85vh] flex flex-col" onClick={e => e.stopPropagation()}>
        {/* Header */}
        <div className="flex items-center justify-between p-4 border-b border-[var(--border)] shrink-0">
          <div>
            <h3 className="text-lg font-semibold flex items-center gap-2">
              <Link2 className="w-5 h-5 text-blue-400" /> Link Holdings
            </h3>
            <p className="text-sm text-[var(--text-muted)]">{goal.name}</p>
          </div>
          <button onClick={onClose} className="text-[var(--text-secondary)] hover:text-white transition p-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="overflow-y-auto flex-1 p-4 space-y-4">
          {loading ? (
            <div className="flex justify-center py-8"><Loader2 className="w-6 h-6 animate-spin text-[var(--text-muted)]" /></div>
          ) : (
            <>
              {/* Linked Holdings */}
              {linkedHoldings.length > 0 && (
                <div>
                  <p className="text-xs font-medium text-[var(--text-muted)] uppercase tracking-wider mb-2">
                    Linked ({linkedHoldings.length}) — Rs. {totalAllocated.toLocaleString('en-IN')} allocated
                  </p>
                  <div className="space-y-2">
                    {linkedHoldings.map(l => (
                      <div key={l.id} className="flex items-center justify-between p-2.5 rounded-lg bg-blue-500/5 border border-blue-500/20">
                        <div className="min-w-0">
                          <p className="text-sm font-medium">{l.symbol}</p>
                          <p className="text-xs text-[var(--text-muted)]">
                            {l.allocationPct}% — Rs. {(l.allocatedValue || 0).toLocaleString('en-IN')}
                          </p>
                        </div>
                        <button
                          onClick={() => handleUnlink(l.holdingId, l.symbol)}
                          className="text-[var(--text-muted)] hover:text-red-400 transition p-1"
                          title="Unlink"
                        >
                          <Unlink className="w-4 h-4" />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Available Holdings */}
              <div>
                <p className="text-xs font-medium text-[var(--text-muted)] uppercase tracking-wider mb-2">
                  Available Holdings
                </p>
                <div className="flex items-center gap-2 bg-[var(--input-bg)] rounded-lg px-3 py-2 mb-2 border border-[var(--border)]">
                  <Search className="w-4 h-4 text-[var(--text-muted)]" />
                  <input
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                    placeholder="Search holdings..."
                    className="bg-transparent w-full text-sm focus:outline-none"
                  />
                  {search && <button onClick={() => setSearch('')}><X className="w-3.5 h-3.5 text-[var(--text-muted)]" /></button>}
                </div>
                {availableHoldings.length === 0 ? (
                  <p className="text-center py-4 text-sm text-[var(--text-secondary)]">
                    {allHoldings.length === linkedHoldings.length ? 'All holdings linked' : 'No matching holdings'}
                  </p>
                ) : (
                  <div className="space-y-1.5 max-h-60 overflow-y-auto">
                    {availableHoldings.map(h => (
                      <div key={h.id} className="flex items-center justify-between p-2.5 rounded-lg hover:bg-[var(--hover-bg)] transition">
                        <div className="min-w-0 flex-1">
                          <p className="text-sm font-medium">{h.symbol}</p>
                          <p className="text-xs text-[var(--text-muted)]">
                            {h.name} — Rs. {(h.currentValue || 0).toLocaleString('en-IN')}
                          </p>
                        </div>
                        <button
                          onClick={() => handleLink(h)}
                          disabled={linking === h.id}
                          className="flex items-center gap-1 px-2.5 py-1 rounded text-xs bg-blue-500/20 text-blue-400 hover:bg-blue-500/30 transition disabled:opacity-50"
                        >
                          {linking === h.id ? <Loader2 className="w-3 h-3 animate-spin" /> : <Link2 className="w-3 h-3" />}
                          Link
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
