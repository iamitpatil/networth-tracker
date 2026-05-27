/**
 * Form input with label and error display.
 */
export default function Input({
  label,
  error,
  hint,
  required,
  type = 'text',
  className = '',
  id,
  ...props
}) {
  const inputId = id || `input-${Math.random().toString(36).slice(2, 9)}`

  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-[var(--text)]">
          {label}
          {required && <span className="text-red-400 ml-0.5">*</span>}
        </label>
      )}
      <input
        id={inputId}
        type={type}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined}
        className={`
          w-full px-3 py-2.5 rounded-lg
          bg-[var(--input-bg)] border border-[var(--border)]
          text-[var(--text)] placeholder:text-[var(--text-muted)]
          focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500
          transition-all duration-200
          ${error ? 'border-red-500 focus:ring-red-500/40 focus:border-red-500' : ''}
          ${props.disabled ? 'opacity-50 cursor-not-allowed' : ''}
        `}
        {...props}
      />
      {error ? (
        <p id={`${inputId}-error`} className="text-xs text-red-400">{error}</p>
      ) : hint ? (
        <p id={`${inputId}-hint`} className="text-xs text-[var(--text-muted)]">{hint}</p>
      ) : null}
    </div>
  )
}

export function Select({ label, error, hint, required, children, className = '', id, ...props }) {
  const selectId = id || `select-${Math.random().toString(36).slice(2, 9)}`

  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      {label && (
        <label htmlFor={selectId} className="text-sm font-medium text-[var(--text)]">
          {label}
          {required && <span className="text-red-400 ml-0.5">*</span>}
        </label>
      )}
      <select
        id={selectId}
        className={`
          w-full px-3 py-2 rounded-lg
          bg-[var(--input-bg)] border border-[var(--border)]
          text-[var(--text)]
          focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500
          transition-colors
          ${error ? 'border-red-500' : ''}
        `}
        {...props}
      >
        {children}
      </select>
      {error ? (
        <p className="text-xs text-red-400">{error}</p>
      ) : hint ? (
        <p className="text-xs text-[var(--text-muted)]">{hint}</p>
      ) : null}
    </div>
  )
}

export function Textarea({ label, error, hint, required, className = '', id, ...props }) {
  const textareaId = id || `textarea-${Math.random().toString(36).slice(2, 9)}`

  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      {label && (
        <label htmlFor={textareaId} className="text-sm font-medium text-[var(--text)]">
          {label}
          {required && <span className="text-red-400 ml-0.5">*</span>}
        </label>
      )}
      <textarea
        id={textareaId}
        className={`
          w-full px-3 py-2 rounded-lg resize-none
          bg-[var(--input-bg)] border border-[var(--border)]
          text-[var(--text)] placeholder:text-[var(--text-muted)]
          focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500
          transition-colors
          ${error ? 'border-red-500' : ''}
        `}
        {...props}
      />
      {error ? (
        <p className="text-xs text-red-400">{error}</p>
      ) : hint ? (
        <p className="text-xs text-[var(--text-muted)]">{hint}</p>
      ) : null}
    </div>
  )
}
