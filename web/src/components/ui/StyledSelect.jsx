import { useState, useEffect, useRef } from 'react'
import { ChevronDown } from 'lucide-react'

/**
 * Custom styled dropdown that matches the app's dark theme.
 * Replaces native <select> with a consistent look across all pages.
 *
 * Props:
 *  - value: current selected value
 *  - onChange: (value) => void
 *  - options: array of strings or { value, label, logo? } objects
 *  - placeholder: placeholder text (default: 'Select...')
 *  - label: optional label above the dropdown
 *  - required: show red border when empty
 *  - showLogo: show logo per option (from option.logo or option.metadata?.logo)
 *  - className: additional className for the wrapper
 *  - disabled: disable the dropdown
 */
export default function StyledSelect({ value, onChange, options = [], placeholder = 'Select...', label, required, showLogo, className = '', disabled }) {
  const [open, setOpen] = useState(false)
  const ref = useRef(null)

  const getVal = (o) => typeof o === 'string' ? o : o.value
  const getLabel = (o) => typeof o === 'string' ? o : (o.label || o.value)
  const getLogo = (o) => o?.logo || o?.metadata?.logo || null

  const selected = options.find(o => getVal(o) === value)
  const selectedLabel = selected ? getLabel(selected) : null
  const selectedLogo = selected ? getLogo(selected) : null

  useEffect(() => {
    if (!open) return
    const handleClick = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false) }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [open])

  return (
    <div ref={ref} className={className}>
      {label && <label className="block text-xs text-[var(--text-muted)] mb-1">{label}</label>}
      <div className="relative">
        <button
          type="button"
          onClick={() => !disabled && setOpen(!open)}
          disabled={disabled}
          className={`w-full bg-[var(--input-bg)] border rounded-lg px-3 py-2 text-sm text-left flex items-center justify-between focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50 transition ${
            !value && required ? 'border-red-500/30' : 'border-[var(--border)]'
          } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
        >
          <span className="flex items-center gap-2 truncate min-w-0">
            {showLogo && selectedLogo && (
              <img src={selectedLogo} alt="" className="w-4 h-4 rounded object-contain bg-white p-px flex-shrink-0"
                onError={(e) => { e.target.style.display = 'none' }} />
            )}
            <span className={value ? 'text-[var(--text)]' : 'text-[var(--text-muted)]'}>
              {selectedLabel || placeholder}
            </span>
          </span>
          <ChevronDown className={`w-3.5 h-3.5 text-[var(--text-muted)] flex-shrink-0 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
        {open && (
          <div className="absolute left-0 right-0 top-full mt-1 bg-[var(--bg-card)] border border-[var(--border)] rounded-lg shadow-xl z-50 max-h-52 overflow-y-auto">
            {!required && (
              <button type="button"
                onClick={() => { onChange(''); setOpen(false) }}
                className={`w-full px-3 py-2 flex items-center gap-2 hover:bg-[var(--hover-bg)] transition text-left text-sm ${!value ? 'bg-blue-500/10 text-blue-400' : 'text-[var(--text-muted)]'}`}>
                {placeholder}
              </button>
            )}
            {options.map(o => {
              const val = getVal(o)
              const lbl = getLabel(o)
              const logo = getLogo(o)
              const isSelected = val === value
              return (
                <button type="button" key={val}
                  onClick={() => { onChange(val); setOpen(false) }}
                  className={`w-full px-3 py-2 flex items-center gap-2 hover:bg-[var(--hover-bg)] transition text-left text-sm ${isSelected ? 'bg-blue-500/10 text-blue-400' : ''}`}>
                  {showLogo && logo && (
                    <img src={logo} alt="" className="w-4 h-4 rounded object-contain bg-white p-px flex-shrink-0"
                      onError={(e) => { e.target.style.display = 'none' }} />
                  )}
                  <span className="truncate">{lbl}</span>
                </button>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
