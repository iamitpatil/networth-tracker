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
 * A price confirmation time, as short as it can be while still saying what matters.
 *
 * Sits under a figure in a table, so it has one line and no more: a time for something confirmed
 * today, because a quote from 09:16 and one from 15:32 are different things intraday; a word for
 * yesterday, since a NAV published last night is the newest one that exists for most of today; a
 * date for anything older, where the hour has stopped being interesting.
 *
 * Not `formatRelativeDate`: that answers in whole days and would call this morning's quote "Today",
 * which is exactly the distinction the stamp exists to make.
 */
export function formatAsOf(instant) {
  if (!instant) return ''
  const then = new Date(instant)
  if (isNaN(then.getTime())) return ''
  const now = new Date()
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  if (then >= startOfToday) {
    return then.toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' })
  }
  const startOfYesterday = new Date(startOfToday)
  startOfYesterday.setDate(startOfYesterday.getDate() - 1)
  if (then >= startOfYesterday) return 'yesterday'
  return then.toLocaleDateString('en-IN', then.getFullYear() === now.getFullYear()
    ? { day: '2-digit', month: 'short' }
    : { day: '2-digit', month: 'short', year: 'numeric' })
}

/**
 * A date-time as an `<input type="datetime-local">` value, in the browser's own zone.
 * Defaults to now.
 *
 * `new Date().toISOString().slice(0, 16)` looks like it does this but gives UTC, so a user in IST
 * saw the clock running 5.5 hours behind — and before 05:30 local, yesterday's date.
 */
export function dateTimeInputValue(date = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/**
 * A date as an `<input type="date">` value, in the browser's own zone. Defaults to today.
 */
export function dateInputValue(date = new Date()) {
  return dateTimeInputValue(date).slice(0, 10)
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
