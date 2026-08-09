/**
 * Consistent page header used at the top of every page.
 */
export default function PageHeader({ title, subtitle, actions, children }) {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 pb-2 animate-in slide-in-up duration-300">
      <div className="min-w-0">
        <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-[var(--text)]">
          {title}
        </h1>
        {subtitle && (
          <p className="text-sm text-[var(--text-muted)] mt-1">{subtitle}</p>
        )}
        {children}
      </div>
      {actions && (
        <div className="flex items-center gap-2 flex-wrap">
          {actions}
        </div>
      )}
    </div>
  )
}
