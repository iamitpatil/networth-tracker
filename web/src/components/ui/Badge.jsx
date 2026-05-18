/**
 * Badge - for categories, statuses, etc.
 */
const variants = {
  default: 'bg-[var(--input-bg)] text-[var(--text-secondary)]',
  blue: 'bg-blue-500/15 text-blue-400 ring-1 ring-inset ring-blue-500/30',
  green: 'bg-green-500/15 text-green-400 ring-1 ring-inset ring-green-500/30',
  red: 'bg-red-500/15 text-red-400 ring-1 ring-inset ring-red-500/30',
  amber: 'bg-amber-500/15 text-amber-400 ring-1 ring-inset ring-amber-500/30',
  purple: 'bg-purple-500/15 text-purple-400 ring-1 ring-inset ring-purple-500/30',
  cyan: 'bg-cyan-500/15 text-cyan-400 ring-1 ring-inset ring-cyan-500/30',
  orange: 'bg-orange-500/15 text-orange-400 ring-1 ring-inset ring-orange-500/30',
  pink: 'bg-pink-500/15 text-pink-400 ring-1 ring-inset ring-pink-500/30',
  gray: 'bg-slate-500/15 text-slate-400 ring-1 ring-inset ring-slate-500/30',
}

const sizes = {
  sm: 'text-[10px] px-1.5 py-0.5',
  md: 'text-xs px-2 py-1',
  lg: 'text-sm px-2.5 py-1',
}

export default function Badge({
  children,
  variant = 'default',
  size = 'md',
  icon: Icon,
  className = '',
  ...props
}) {
  return (
    <span
      className={`
        inline-flex items-center gap-1 rounded-md font-medium
        ${variants[variant]} ${sizes[size]} ${className}
      `}
      {...props}
    >
      {Icon && <Icon className="w-3 h-3 flex-shrink-0" />}
      {children}
    </span>
  )
}

/**
 * Get appropriate badge variant for asset type
 */
export const ASSET_BADGE_VARIANT = {
  EQUITY: 'blue',
  ETF: 'blue',
  MUTUAL_FUND: 'green',
  GOLD: 'amber',
  FD: 'purple',
  PPF: 'cyan',
  EPF: 'orange',
  NPS: 'cyan',
  REAL_ESTATE: 'red',
  CRYPTO: 'pink',
  CASH: 'gray',
  BOND: 'green',
}
