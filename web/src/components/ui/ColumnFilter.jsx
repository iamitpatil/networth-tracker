import { useState, useRef, useEffect, useCallback } from 'react'
import { createPortal } from 'react-dom'
import { Filter, X } from 'lucide-react'

/**
 * A filter control that lives in a table column heading.
 *
 * The funnel button sits next to the column label and opens a small panel holding whatever
 * controls that column needs. An active filter tints the icon, so it is visible at a glance
 * which columns are narrowing the data without opening anything.
 *
 * The panel is rendered through a portal with fixed positioning rather than absolutely inside
 * the header cell. Data tables live in a horizontally scrolling container, and that container
 * clips an absolutely positioned child instead of letting it overlay the rows.
 *
 * Props:
 *  - label:    column name, used in the panel heading and the button's accessible name
 *  - active:   whether a filter is currently set on this column
 *  - onClear:  clears this column's filter; the Clear button is hidden when not supplied
 *  - children: the filter controls
 */
export default function ColumnFilter({ label, active = false, onClear, children }) {
  const [open, setOpen] = useState(false)
  const [pos, setPos] = useState({ top: 0, left: 0 })
  const buttonRef = useRef(null)
  const panelRef = useRef(null)

  const place = useCallback(() => {
    const rect = buttonRef.current?.getBoundingClientRect()
    if (!rect) return
    const PANEL_WIDTH = 224
    // Keep the panel on screen when the column sits near the right edge.
    const left = Math.min(rect.left, window.innerWidth - PANEL_WIDTH - 12)
    setPos({ top: rect.bottom + 6, left: Math.max(12, left) })
  }, [])

  useEffect(() => {
    if (!open) return
    place()
    const onDocMouseDown = (e) => {
      if (panelRef.current?.contains(e.target) || buttonRef.current?.contains(e.target)) return
      setOpen(false)
    }
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', onDocMouseDown)
    document.addEventListener('keydown', onKey)
    // Reposition rather than drift away from the column when the table or page scrolls.
    window.addEventListener('resize', place)
    window.addEventListener('scroll', place, true)
    return () => {
      document.removeEventListener('mousedown', onDocMouseDown)
      document.removeEventListener('keydown', onKey)
      window.removeEventListener('resize', place)
      window.removeEventListener('scroll', place, true)
    }
  }, [open, place])

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-label={`Filter ${label}`}
        aria-expanded={open}
        title={active ? `${label} filter active` : `Filter ${label}`}
        className={`inline-flex items-center justify-center w-5 h-5 rounded transition-colors align-middle ${
          active
            ? 'text-blue-400 bg-blue-500/15 ring-1 ring-inset ring-blue-500/40'
            : 'text-[var(--text-secondary)] hover:text-[var(--text)] hover:bg-[var(--hover-bg)]'
        }`}
      >
        <Filter className="w-3 h-3" />
      </button>

      {open && createPortal(
        <div
          ref={panelRef}
          style={{ top: pos.top, left: pos.left, width: 224 }}
          className="fixed z-50 bg-[var(--bg-card)] border border-[var(--border)] rounded-lg shadow-xl p-3"
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs font-medium text-[var(--text-muted)]">{label}</span>
            <button type="button" onClick={() => setOpen(false)} aria-label="Close filter"
              className="text-[var(--text-secondary)] hover:text-[var(--text)]">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
          <div className="space-y-2">{children}</div>
          {onClear && active && (
            <button type="button" onClick={() => { onClear(); setOpen(false) }}
              className="mt-3 w-full text-xs text-red-400 hover:text-red-300">
              Clear this filter
            </button>
          )}
        </div>,
        document.body,
      )}
    </>
  )
}
