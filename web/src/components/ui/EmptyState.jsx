/**
 * Empty state for when there's no data.
 */
export default function EmptyState({
  icon: Icon,
  title = 'No data',
  description,
  action,
  className = '',
}) {
  return (
    <div
      className={`
        flex flex-col items-center justify-center text-center
        py-16 px-6
        bg-[var(--bg-card)] border border-[var(--border)] rounded-xl
        ${className}
      `}
    >
      {Icon && (
        <div className="w-14 h-14 rounded-full bg-[var(--input-bg)] flex items-center justify-center mb-4">
          <Icon className="w-7 h-7 text-[var(--text-muted)]" />
        </div>
      )}
      <h3 className="text-base font-semibold text-[var(--text)] mb-2">{title}</h3>
      {description && (
        <p className="text-sm text-[var(--text-muted)] max-w-sm mb-4">{description}</p>
      )}
      {action}
    </div>
  )
}
