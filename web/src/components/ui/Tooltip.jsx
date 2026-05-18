import { useState } from 'react'

/**
 * Simple tooltip - shows on hover/focus.
 * Use sparingly - prefer explicit labels when possible.
 */
export default function Tooltip({ children, content, side = 'top', className = '' }) {
  const [show, setShow] = useState(false)

  if (!content) return children

  const sideClasses = {
    top: 'bottom-full left-1/2 -translate-x-1/2 mb-2',
    bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
    left: 'right-full top-1/2 -translate-y-1/2 mr-2',
    right: 'left-full top-1/2 -translate-y-1/2 ml-2',
  }

  return (
    <span
      className={`relative inline-flex ${className}`}
      onMouseEnter={() => setShow(true)}
      onMouseLeave={() => setShow(false)}
      onFocus={() => setShow(true)}
      onBlur={() => setShow(false)}
    >
      {children}
      {show && (
        <span
          role="tooltip"
          className={`
            absolute z-50 pointer-events-none
            ${sideClasses[side]}
            px-2 py-1 rounded-md
            bg-slate-900 text-white text-xs
            shadow-lg border border-slate-700
            whitespace-nowrap
            animate-in fade-in duration-150
          `}
        >
          {content}
        </span>
      )}
    </span>
  )
}
