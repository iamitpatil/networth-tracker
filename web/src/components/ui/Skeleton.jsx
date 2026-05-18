/**
 * Skeleton loader primitives.
 */
export default function Skeleton({ className = '', ...props }) {
  return (
    <div
      className={`animate-pulse bg-[var(--input-bg)] rounded ${className}`}
      {...props}
    />
  )
}

export function StatCardSkeleton() {
  return (
    <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-5">
      <div className="flex items-start justify-between mb-3">
        <Skeleton className="h-4 w-20" />
        <Skeleton className="w-9 h-9 rounded-lg" />
      </div>
      <Skeleton className="h-8 w-32 mb-2" />
      <Skeleton className="h-3 w-24" />
    </div>
  )
}

export function TableSkeleton({ rows = 5, columns = 6 }) {
  return (
    <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl overflow-hidden">
      <div className="px-4 py-3 border-b border-[var(--border)] bg-[var(--bg)]/50">
        <Skeleton className="h-4 w-32" />
      </div>
      <div className="divide-y divide-[var(--border)]">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="px-4 py-3 flex items-center gap-4">
            {Array.from({ length: columns }).map((_, j) => (
              <Skeleton key={j} className="h-4 flex-1" />
            ))}
          </div>
        ))}
      </div>
    </div>
  )
}

export function ChartSkeleton({ height = 280 }) {
  return (
    <div
      className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6"
      style={{ height }}
    >
      <Skeleton className="h-5 w-32 mb-4" />
      <div className="flex items-end gap-2 h-[calc(100%-2rem)]">
        {[40, 60, 30, 75, 50, 85, 65, 45, 70, 55, 80, 60].map((h, i) => (
          <Skeleton key={i} className="flex-1 rounded-t" style={{ height: `${h}%` }} />
        ))}
      </div>
    </div>
  )
}

export function PageSkeleton() {
  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <div>
          <Skeleton className="h-8 w-48 mb-2" />
          <Skeleton className="h-4 w-64" />
        </div>
        <Skeleton className="h-10 w-32 rounded-lg" />
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <StatCardSkeleton key={i} />
        ))}
      </div>

      {/* Main content area */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2">
          <ChartSkeleton height={320} />
        </div>
        <div>
          <ChartSkeleton height={320} />
        </div>
      </div>

      <TableSkeleton rows={4} columns={5} />
    </div>
  )
}
