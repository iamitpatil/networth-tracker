import { useEffect, useRef } from 'react'
import { X } from 'lucide-react'
import Button from './Button'

const sizes = {
  sm: 'max-w-md',
  md: 'max-w-lg',
  lg: 'max-w-2xl',
  xl: 'max-w-4xl',
  full: 'max-w-6xl',
}

export default function Modal({
  open,
  onClose,
  title,
  description,
  children,
  size = 'md',
  showClose = true,
  closeOnBackdrop = true,
  footer,
}) {
  const modalRef = useRef(null)

  useEffect(() => {
    if (!open) return

    // Scroll lock
    const originalOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    // Esc to close
    const handleKey = (e) => {
      if (e.key === 'Escape') onClose?.()
    }
    document.addEventListener('keydown', handleKey)

    // Focus trap (basic)
    const focusable = modalRef.current?.querySelectorAll(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    )
    focusable?.[0]?.focus()

    return () => {
      document.body.style.overflow = originalOverflow
      document.removeEventListener('keydown', handleKey)
    }
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 animate-in fade-in duration-200"
      onClick={closeOnBackdrop ? onClose : undefined}
      role="dialog"
      aria-modal="true"
      aria-labelledby={title ? 'modal-title' : undefined}
    >
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/60 backdrop-blur-sm" />

      {/* Modal */}
      <div
        ref={modalRef}
        className={`
          relative w-full ${sizes[size]} max-h-[90vh] overflow-hidden
          bg-[var(--bg-card)] border border-[var(--border)] rounded-xl shadow-2xl
          animate-in zoom-in-95 fade-in duration-200
          flex flex-col
        `}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        {(title || showClose) && (
          <div className="flex items-start justify-between px-6 py-4 border-b border-[var(--border)] flex-shrink-0">
            <div className="min-w-0 flex-1">
              {title && (
                <h2 id="modal-title" className="text-lg font-semibold text-[var(--text)]">
                  {title}
                </h2>
              )}
              {description && (
                <p className="text-sm text-[var(--text-muted)] mt-1">{description}</p>
              )}
            </div>
            {showClose && (
              <button
                onClick={onClose}
                aria-label="Close modal"
                className="p-1 rounded-lg hover:bg-[var(--hover-bg)] text-[var(--text-muted)] hover:text-[var(--text)] transition-colors ml-3"
              >
                <X className="w-5 h-5" />
              </button>
            )}
          </div>
        )}

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-6">{children}</div>

        {/* Footer */}
        {footer && (
          <div className="px-6 py-4 border-t border-[var(--border)] flex justify-end gap-2 flex-shrink-0">
            {footer}
          </div>
        )}
      </div>
    </div>
  )
}

/**
 * Confirmation dialog - replaces window.confirm()
 */
export function ConfirmDialog({
  open,
  onClose,
  onConfirm,
  title = 'Are you sure?',
  description,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  variant = 'danger',
  loading = false,
}) {
  const handleConfirm = async () => {
    await onConfirm?.()
    onClose?.()
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      size="sm"
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={loading}>
            {cancelText}
          </Button>
          <Button variant={variant} onClick={handleConfirm} loading={loading}>
            {confirmText}
          </Button>
        </>
      }
    >
      {/* Body intentionally empty - description shows in header */}
    </Modal>
  )
}
