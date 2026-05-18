import { formatINR, formatPercent } from '../../utils/format'

/**
 * Basic Card primitive - the foundation of all card UIs.
 */
export default function Card({ children, className = '', hover = false, ...props }) {
  return (
    <div
      className={`
        bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6
        ${hover ? 'transition-colors hover:border-blue-500/30' : ''}
        ${className}
      `}
      {...props}
    >
      {children}
    </div>
  )
}

/**
 * Stat card for displaying key metrics (Net Worth, Holdings count, etc.)
 */
export function StatCard({
  label,
  value,
  delta,
  icon: Icon,
  iconColor = 'text-blue-400',
  iconBg = 'bg-blue-500/10',
  trend,
  subtitle,
  format = 'currency',
  loading = false,
  onClick,
}) {
  const displayValue = format === 'currency' ? formatINR(value) :
                       format === 'percent' ? formatPercent(value) :
                       format === 'number' ? Number(value).toLocaleString('en-IN') :
                       value

  const cardClass = onClick
    ? 'bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-5 transition-all hover:border-blue-500/30 cursor-pointer'
    : 'bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-5'

  if (loading) {
    return (
      <div className={cardClass}>
        <div className="flex items-start justify-between mb-3">
          <div className="h-4 w-20 bg-[var(--input-bg)] rounded animate-pulse" />
          {Icon && <div className="w-9 h-9 rounded-lg bg-[var(--input-bg)] animate-pulse" />}
        </div>
        <div className="h-8 w-32 bg-[var(--input-bg)] rounded animate-pulse mb-2" />
        <div className="h-3 w-24 bg-[var(--input-bg)] rounded animate-pulse" />
      </div>
    )
  }

  return (
    <div className={cardClass} onClick={onClick}>
      <div className="flex items-start justify-between mb-3">
        <p className="text-sm text-[var(--text-muted)] font-medium">{label}</p>
        {Icon && (
          <div className={`p-2 rounded-lg ${iconBg}`}>
            <Icon className={`w-5 h-5 ${iconColor}`} />
          </div>
        )}
      </div>
      <p className="text-2xl font-bold text-[var(--text)] mb-1">{displayValue}</p>
      {(delta != null || trend != null || subtitle) && (
        <div className="flex items-center gap-2 text-xs">
          {delta != null && (
            <span className={delta > 0 ? 'text-green-400' : delta < 0 ? 'text-red-400' : 'text-[var(--text-muted)]'}>
              {delta > 0 ? '↑' : delta < 0 ? '↓' : ''} {formatPercent(Math.abs(delta), { signed: false })}
            </span>
          )}
          {subtitle && <span className="text-[var(--text-muted)]">{subtitle}</span>}
        </div>
      )}
    </div>
  )
}
