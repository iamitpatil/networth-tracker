import { Loader2 } from 'lucide-react'

const variants = {
  primary: 'bg-blue-500 hover:bg-blue-600 text-white border-transparent',
  secondary: 'bg-[var(--input-bg)] hover:bg-[var(--hover-bg)] text-[var(--text)] border border-[var(--border)]',
  danger: 'bg-red-500 hover:bg-red-600 text-white border-transparent',
  ghost: 'bg-transparent hover:bg-[var(--hover-bg)] text-[var(--text)] border-transparent',
  outline: 'bg-transparent hover:bg-[var(--hover-bg)] text-[var(--text)] border border-[var(--border)]',
  link: 'bg-transparent hover:text-blue-300 text-blue-400 border-transparent p-0',
}

const sizes = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
  lg: 'px-5 py-2.5 text-base',
  icon: 'p-2',
}

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  icon: Icon,
  iconRight: IconRight,
  className = '',
  type = 'button',
  ...props
}) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={`
        inline-flex items-center justify-center gap-2
        rounded-lg font-medium
        transition-all duration-150
        focus:outline-none focus:ring-2 focus:ring-blue-500/40
        disabled:opacity-50 disabled:cursor-not-allowed
        ${variants[variant]}
        ${variant === 'link' ? '' : sizes[size]}
        ${className}
      `}
      {...props}
    >
      {loading ? (
        <Loader2 className="w-4 h-4 animate-spin" />
      ) : Icon ? (
        <Icon className="w-4 h-4 flex-shrink-0" />
      ) : null}
      {children}
      {IconRight && !loading && <IconRight className="w-4 h-4 flex-shrink-0" />}
    </button>
  )
}
