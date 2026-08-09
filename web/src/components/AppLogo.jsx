/**
 * Reusable app logo with layered icon and gradient text.
 *
 * Variants:
 *   "full"   – icon + gradient app name + subtitle (login/register hero)
 *   "compact"– icon + gradient app name (sidebar, mobile header)
 *   "icon"   – icon only
 *
 * The icon renders as an SVG with a multi-layer chart/pulse motif inside
 * a rounded gradient container so it looks distinct from a generic Lucide icon.
 */
export default function AppLogo({ variant = 'compact', className = '', dark = false }) {
  const icon = (size = 'md') => {
    const dim = size === 'lg' ? 'w-12 h-12' : size === 'md' ? 'w-9 h-9' : 'w-7 h-7'
    const inner = size === 'lg' ? 'w-7 h-7' : size === 'md' ? 'w-5 h-5' : 'w-4 h-4'
    return (
      <div className={`${dim} rounded-xl bg-gradient-to-br from-blue-500 via-indigo-500 to-violet-600 flex items-center justify-center shadow-lg shadow-blue-500/25 relative overflow-hidden`}>
        {/* Subtle inner glow */}
        <div className="absolute inset-0 bg-gradient-to-t from-transparent to-white/10 rounded-xl" />
        <svg className={`${inner} relative z-10`} viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          {/* Bar chart base */}
          <rect x="3" y="14" width="3" height="7" rx="1" fill="white" opacity="0.5" />
          <rect x="8" y="10" width="3" height="11" rx="1" fill="white" opacity="0.7" />
          <rect x="13" y="6" width="3" height="15" rx="1" fill="white" opacity="0.85" />
          <rect x="18" y="3" width="3" height="18" rx="1" fill="white" />
          {/* Trend line overlay */}
          <path d="M4.5 12 L9.5 8 L14.5 5 L19.5 2" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" opacity="0.6" />
          <circle cx="19.5" cy="2" r="1.5" fill="white" opacity="0.8" />
        </svg>
      </div>
    )
  }

  const gradientName = (textSize = 'text-xl') => (
    <span className={`${textSize} font-bold tracking-tight bg-gradient-to-r from-blue-400 via-indigo-400 to-violet-400 bg-clip-text text-transparent`}>
      NetWorth
    </span>
  )

  const gradientNameWhite = (textSize = 'text-xl') => (
    <span className={`${textSize} font-bold tracking-tight bg-gradient-to-r from-white via-blue-100 to-violet-200 bg-clip-text text-transparent`}>
      NetWorth
    </span>
  )

  if (variant === 'icon') {
    return <div className={className}>{icon('md')}</div>
  }

  if (variant === 'full') {
    return (
      <div className={`flex items-center gap-3 ${className}`}>
        {icon('lg')}
        <div>
          <div className="flex items-baseline gap-1.5">
            {dark ? gradientNameWhite('text-2xl') : gradientName('text-2xl')}
            <span className={`text-2xl font-bold ${dark ? 'text-white/70' : 'text-[var(--text-muted)]'}`}>Tracker</span>
          </div>
          <p className={`text-xs ${dark ? 'text-blue-200/60' : 'text-[var(--text-muted)]'} -mt-0.5`}>Your Local Investment Manager</p>
        </div>
      </div>
    )
  }

  // compact (sidebar, mobile header)
  return (
    <div className={`flex items-center gap-2.5 ${className}`}>
      {icon('sm')}
      <div className="flex items-baseline gap-1">
        {dark ? gradientNameWhite('text-lg') : gradientName('text-lg')}
        <span className={`text-lg font-bold ${dark ? 'text-white/60' : 'text-[var(--text-muted)]'}`}>Tracker</span>
      </div>
    </div>
  )
}
