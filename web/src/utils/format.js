/**
 * Format a number as Indian Rupees.
 * @param {number} value
 * @param {Object} options
 * @param {boolean} options.compact - If true, use Cr/L/K suffixes
 * @param {number} options.decimals - Decimal places (default 0 if integer, 2 if not)
 * @returns {string}
 */
export function formatINR(value, options = {}) {
  if (value == null || isNaN(value)) return '₹0'
  const num = Number(value)
  const { compact = false, decimals } = options
  const abs = Math.abs(num)
  const sign = num < 0 ? '-' : ''

  if (compact) {
    if (abs >= 10000000) return `${sign}₹${(abs / 10000000).toFixed(2)}Cr`
    if (abs >= 100000) return `${sign}₹${(abs / 100000).toFixed(2)}L`
    if (abs >= 1000) return `${sign}₹${(abs / 1000).toFixed(1)}K`
    return `${sign}₹${abs.toFixed(decimals ?? 0)}`
  }

  const formatter = new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: decimals ?? (Number.isInteger(num) ? 0 : 2),
    minimumFractionDigits: decimals ?? 0,
  })
  return formatter.format(num)
}

/**
 * Format as percentage with optional + sign.
 * @param {number} value - Value (e.g. 12.5 for 12.5%)
 * @param {Object} options
 * @param {boolean} options.signed - If true, show + for positives
 */
export function formatPercent(value, { signed = true, decimals = 2 } = {}) {
  if (value == null || isNaN(value)) return '0%'
  const sign = signed && value > 0 ? '+' : ''
  return `${sign}${Number(value).toFixed(decimals)}%`
}

/**
 * Format a date in dd MMM yyyy format.
 */
export function formatDate(dateStr) {
  if (!dateStr) return ''
  try {
    const date = new Date(dateStr)
    return date.toLocaleDateString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    })
  } catch {
    return dateStr
  }
}

/**
 * Format date as relative time (e.g. "2 days ago").
 */
export function formatRelativeDate(dateStr) {
  if (!dateStr) return ''
  try {
    const date = new Date(dateStr)
    const now = new Date()
    const diffSec = (now - date) / 1000
    const diffDays = Math.floor(diffSec / 86400)
    if (diffDays === 0) return 'Today'
    if (diffDays === 1) return 'Yesterday'
    if (diffDays < 7) return `${diffDays} days ago`
    if (diffDays < 30) return `${Math.floor(diffDays / 7)} weeks ago`
    if (diffDays < 365) return `${Math.floor(diffDays / 30)} months ago`
    return `${Math.floor(diffDays / 365)} years ago`
  } catch {
    return formatDate(dateStr)
  }
}

/**
 * Format file size in bytes.
 */
export function formatBytes(bytes) {
  if (!bytes) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(1)} GB`
}
