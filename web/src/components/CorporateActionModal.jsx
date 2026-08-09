import { useState, useMemo } from 'react'
import { toast } from 'sonner'
import { Loader2, Gift, Split, GitBranch } from 'lucide-react'
import client from '../api/client'
import { Modal } from './ui'
import { dateInputValue } from '../utils/format'

const TYPES = [
  { value: 'BONUS', label: 'Bonus issue', icon: Gift,
    hint: 'Free shares on what you already hold' },
  { value: 'SPLIT', label: 'Split', icon: Split,
    hint: 'Shares sub-divided or consolidated' },
  { value: 'DEMERGER', label: 'Demerger', icon: GitBranch,
    hint: 'A business spun off as a new listing' },
]

/**
 * What each action does to cost basis, in the user's own terms.
 *
 * These are shown live in the form rather than buried in help, because the cost treatment is
 * exactly what people get wrong — most notably assuming demerged shares are free. They are not:
 * the original cost is divided between the two companies.
 */
const EXPLAIN = {
  BONUS: 'Bonus shares carry nil cost (s.55(2)(aa)). Your total cost is unchanged, so your '
    + 'average price falls — the drop is arithmetic, not a loss. Their holding period starts at '
    + 'allotment, so selling them soon after is short-term.',
  SPLIT: 'A split creates no cost and destroys none. Your share count and per-share cost move in '
    + 'opposite directions by the same ratio, and your holding period is not reset.',
  DEMERGER: 'The original cost is split between the two companies (s.49(2C)) using the ratio the '
    + 'company publishes — the new shares are not free. They inherit the period you held the '
    + 'original shares (s.2(42A)), so they can be long-term immediately.',
}

const input = 'w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2 '
  + 'text-sm text-[var(--text)] placeholder-[var(--text-secondary)] focus:outline-none '
  + 'focus:ring-1 focus:ring-blue-500/50'
const label = 'block text-xs text-[var(--text-secondary)] mb-1'

/**
 * Applies a bonus issue, split or demerger to one holding.
 *
 * Kept out of the Add Holding form on purpose: these are not trades. A bonus has no price, a
 * split has no quantity of its own, and a demerger writes to two holdings at once — none of which
 * a buy/sell form can express without misleading fields.
 */
export default function CorporateActionModal({ open, onClose, holding, onApplied }) {
  const [type, setType] = useState('BONUS')
  const [actionDate, setActionDate] = useState(() => dateInputValue())
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)
  const [result, setResult] = useState(null)

  // BONUS
  const [sharesReceived, setSharesReceived] = useState('1')
  const [sharesHeld, setSharesHeld] = useState('1')
  // SPLIT
  const [fromQuantity, setFromQuantity] = useState('1')
  const [toQuantity, setToQuantity] = useState('2')
  // DEMERGER
  const [resultingSymbol, setResultingSymbol] = useState('')
  const [resultingName, setResultingName] = useState('')
  const [resultingQuantity, setResultingQuantity] = useState('')
  const [costApportionmentPercent, setCostApportionmentPercent] = useState('')

  const held = Number(holding?.quantity) || 0
  const avg = Number(holding?.averageBuyPrice) || 0

  /**
   * The position after the action, computed here so the user sees the effect before committing.
   * Mirrors the server's arithmetic; the server remains the authority and its response replaces
   * this once applied.
   */
  const preview = useMemo(() => {
    if (!holding) return null
    const totalCost = held * avg
    if (type === 'BONUS') {
      const r = Number(sharesReceived), h = Number(sharesHeld)
      if (!(r > 0) || !(h > 0)) return null
      const bonus = Math.floor((held * r) / h)   // no registrar issues fractional shares
      if (bonus <= 0) return { warning: `A ${r}:${h} bonus on ${held} shares is less than one share.` }
      const qty = held + bonus
      return { qty, avg: qty ? totalCost / qty : 0, note: `${bonus} free shares added` }
    }
    if (type === 'SPLIT') {
      const f = Number(fromQuantity), t = Number(toQuantity)
      if (!(f > 0) || !(t > 0)) return null
      if (f === t) return { warning: 'A split from and to the same number changes nothing.' }
      const qty = held / (f / t)
      return { qty, avg: qty ? totalCost / qty : 0,
        note: t > f ? 'Total cost unchanged' : 'Consolidation — fewer shares, higher average' }
    }
    const pct = Number(costApportionmentPercent), newQty = Number(resultingQuantity)
    if (!(pct >= 0) || !(pct <= 100) || !(newQty > 0)) return null
    const movedCost = totalCost * (pct / 100)
    return {
      qty: held, avg: avg * (1 - pct / 100),
      resulting: { qty: newQty, costPerShare: movedCost / newQty, movedCost },
      note: `Rs. ${Math.round(movedCost).toLocaleString('en-IN')} of cost moves to the new company`,
    }
  }, [holding, held, avg, type, sharesReceived, sharesHeld, fromQuantity, toQuantity,
      resultingQuantity, costApportionmentPercent])

  const reset = () => {
    setResult(null); setNotes('')
    setSharesReceived('1'); setSharesHeld('1')
    setFromQuantity('1'); setToQuantity('2')
    setResultingSymbol(''); setResultingName(''); setResultingQuantity('')
    setCostApportionmentPercent('')
  }

  const close = () => { reset(); onClose?.() }

  const submit = async () => {
    setSaving(true)
    try {
      const body = { type, actionDate, notes: notes || undefined }
      if (type === 'BONUS') {
        body.sharesReceived = sharesReceived
        body.sharesHeld = sharesHeld
      } else if (type === 'SPLIT') {
        body.fromQuantity = fromQuantity
        body.toQuantity = toQuantity
      } else {
        body.resultingSymbol = resultingSymbol
        body.resultingName = resultingName || undefined
        body.resultingQuantity = resultingQuantity
        body.costApportionmentPercent = costApportionmentPercent
        body.resultingAssetType = holding.assetType
      }
      const { data } = await client.post(`/portfolio/holdings/${holding.id}/corporate-actions`, body)
      setResult(data)
      toast.success(`${type === 'DEMERGER' ? 'Demerger' : type === 'SPLIT' ? 'Split' : 'Bonus'} recorded`)
      await onApplied?.()
    } catch (e) {
      // The server validates the same things and explains why in plain language; surface that
      // rather than a generic failure.
      toast.error(e?.response?.data?.message || e?.response?.data?.error
        || 'Could not apply the action')
    } finally {
      setSaving(false)
    }
  }

  const canSubmit = () => {
    if (!actionDate || saving) return false
    if (preview?.warning) return false
    if (type === 'BONUS') return Number(sharesReceived) > 0 && Number(sharesHeld) > 0
    if (type === 'SPLIT') return Number(fromQuantity) > 0 && Number(toQuantity) > 0
    return !!resultingSymbol.trim() && Number(resultingQuantity) > 0
      && costApportionmentPercent !== '' && Number(costApportionmentPercent) >= 0
      && Number(costApportionmentPercent) <= 100
  }

  const num = (v, digits = 2) =>
    Number(v).toLocaleString('en-IN', { maximumFractionDigits: digits })

  if (!holding) return null

  return (
    <Modal open={open} onClose={close} size="lg"
      title={`Corporate action — ${holding.symbol}`}
      description={`You hold ${num(held, 4)} shares at an average of Rs. ${num(avg)}`}>
      {result ? (
        <div className="space-y-4">
          <p className="text-sm text-[var(--text)]">{result.summary}</p>
          <div className="grid grid-cols-2 gap-3 text-sm">
            <div className="bg-[var(--input-bg)] border border-[var(--border)] rounded-lg p-3">
              <p className="text-xs text-[var(--text-secondary)]">{holding.symbol} quantity</p>
              <p className="font-medium">{num(result.quantityBefore, 4)} → {num(result.quantityAfter, 4)}</p>
            </div>
            <div className="bg-[var(--input-bg)] border border-[var(--border)] rounded-lg p-3">
              <p className="text-xs text-[var(--text-secondary)]">Average price</p>
              <p className="font-medium">Rs. {num(result.averagePriceBefore)} → Rs. {num(result.averagePriceAfter)}</p>
            </div>
            {result.resultingSymbol && (
              <div className="col-span-2 bg-indigo-500/10 border border-indigo-500/30 rounded-lg p-3">
                <p className="text-xs text-indigo-300">New holding</p>
                <p className="font-medium">
                  {result.resultingSymbol} — {num(result.resultingQuantity, 4)} shares at
                  Rs. {num(result.resultingCostPerShare)}
                </p>
                <p className="text-xs text-[var(--text-secondary)] mt-1">
                  Counted as held since {new Date(result.inheritedAcquisitionDate).toLocaleDateString('en-IN')}
                </p>
              </div>
            )}
          </div>
          <button onClick={close}
            className="w-full px-4 py-2 bg-blue-500 hover:bg-blue-600 text-white rounded-lg text-sm transition">
            Done
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          <div className="grid grid-cols-3 gap-2">
            {TYPES.map((t) => {
              const Icon = t.icon
              return (
                <button key={t.value} type="button" onClick={() => { setType(t.value); setResult(null) }}
                  className={`px-3 py-2.5 rounded-lg text-sm border transition text-left ${
                    type === t.value
                      ? 'bg-blue-500/20 text-blue-400 border-blue-500/40'
                      : 'bg-[var(--input-bg)] text-[var(--text-muted)] border-[var(--border)] hover:bg-[var(--hover-bg)]'
                  }`}>
                  <span className="flex items-center gap-1.5 font-medium"><Icon className="w-4 h-4" />{t.label}</span>
                  <span className="block text-[11px] mt-0.5 opacity-80">{t.hint}</span>
                </button>
              )
            })}
          </div>

          <p className="text-xs text-[var(--text-secondary)] bg-[var(--input-bg)] border border-[var(--border)] rounded-lg p-3">
            {EXPLAIN[type]}
          </p>

          {type === 'BONUS' && (
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={label}>Shares received</label>
                <input type="number" min="0" step="any" value={sharesReceived}
                  onChange={(e) => setSharesReceived(e.target.value)} className={input} />
              </div>
              <div>
                <label className={label}>for every … held</label>
                <input type="number" min="0" step="any" value={sharesHeld}
                  onChange={(e) => setSharesHeld(e.target.value)} className={input} />
              </div>
              <p className="col-span-2 text-[11px] text-[var(--text-secondary)]">
                A 1:2 bonus is 1 received for every 2 held. Fractions are dropped — no registrar
                issues part shares.
              </p>
            </div>
          )}

          {type === 'SPLIT' && (
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={label}>Shares before</label>
                <input type="number" min="0" step="any" value={fromQuantity}
                  onChange={(e) => setFromQuantity(e.target.value)} className={input} />
              </div>
              <div>
                <label className={label}>become</label>
                <input type="number" min="0" step="any" value={toQuantity}
                  onChange={(e) => setToQuantity(e.target.value)} className={input} />
              </div>
              <p className="col-span-2 text-[11px] text-[var(--text-secondary)]">
                1 → 2 splits each share in two. 2 → 1 is a consolidation.
              </p>
            </div>
          )}

          {type === 'DEMERGER' && (
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className={label}>New company symbol</label>
                <input value={resultingSymbol} placeholder="e.g. JIOFIN"
                  onChange={(e) => setResultingSymbol(e.target.value.toUpperCase())} className={input} />
              </div>
              <div>
                <label className={label}>Name (optional)</label>
                <input value={resultingName} placeholder="Looked up if left blank"
                  onChange={(e) => setResultingName(e.target.value)} className={input} />
              </div>
              <div>
                <label className={label}>Shares received</label>
                <input type="number" min="0" step="any" value={resultingQuantity}
                  onChange={(e) => setResultingQuantity(e.target.value)} className={input} />
              </div>
              <div>
                <label className={label}>Cost apportioned to it (%)</label>
                <input type="number" min="0" max="100" step="any" value={costApportionmentPercent}
                  onChange={(e) => setCostApportionmentPercent(e.target.value)} className={input}
                  placeholder="e.g. 4.79" />
              </div>
              <p className="col-span-2 text-[11px] text-[var(--text-secondary)]">
                The percentage comes from the demerger scheme the company publishes — it is the new
                company's share of the pre-demerger net asset value. Guessing it here misstates the
                gain on both holdings later.
              </p>
            </div>
          )}

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={label}>Action date</label>
              <input type="date" value={actionDate} max={dateInputValue()}
                onChange={(e) => setActionDate(e.target.value)} className={input} />
            </div>
            <div>
              <label className={label}>Notes (optional)</label>
              <input value={notes} onChange={(e) => setNotes(e.target.value)} className={input} />
            </div>
          </div>

          {preview?.warning && (
            <p className="text-sm text-amber-400">{preview.warning}</p>
          )}

          {preview && !preview.warning && (
            <div className="bg-[var(--input-bg)] border border-[var(--border)] rounded-lg p-3 text-sm">
              <p className="text-xs text-[var(--text-secondary)] mb-1.5">After this action</p>
              <p>
                <span className="font-medium">{holding.symbol}</span>: {num(preview.qty, 4)} shares
                at Rs. {num(preview.avg)}
                <span className="text-[var(--text-secondary)]"> (from {num(held, 4)} at Rs. {num(avg)})</span>
              </p>
              {preview.resulting && (
                <p className="mt-1">
                  <span className="font-medium">{resultingSymbol || 'New company'}</span>:
                  {' '}{num(preview.resulting.qty, 4)} shares at
                  Rs. {num(preview.resulting.costPerShare)}
                </p>
              )}
              <p className="text-xs text-[var(--text-secondary)] mt-1.5">{preview.note}</p>
            </div>
          )}

          <div className="flex justify-end gap-2 pt-1">
            <button type="button" onClick={close}
              className="px-4 py-2 bg-[var(--input-bg)] rounded-lg text-sm hover:bg-[var(--hover-bg)] transition">
              Cancel
            </button>
            <button type="button" onClick={submit} disabled={!canSubmit()}
              className="px-4 py-2 bg-blue-500 hover:bg-blue-600 disabled:opacity-50 disabled:cursor-not-allowed text-white rounded-lg text-sm flex items-center gap-2 transition">
              {saving && <Loader2 className="w-4 h-4 animate-spin" />}
              Apply action
            </button>
          </div>
        </div>
      )}
    </Modal>
  )
}
