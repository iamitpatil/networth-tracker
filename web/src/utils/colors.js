/**
 * Standardized color palette for charts and UI.
 * Use these instead of hardcoding hex values.
 */
export const CHART_PALETTE = [
  '#3b82f6', // blue
  '#22c55e', // green
  '#f59e0b', // amber
  '#a855f7', // purple
  '#ef4444', // red
  '#06b6d4', // cyan
  '#14b8a6', // teal
  '#f97316', // orange
  '#ec4899', // pink
  '#84cc16', // lime
]

/**
 * Asset type colors - used for badges, charts, etc.
 */
export const ASSET_COLORS = {
  EQUITY: '#3b82f6',
  MUTUAL_FUND: '#22c55e',
  GOLD: '#f59e0b',
  FD: '#a855f7',
  PPF: '#14b8a6',
  EPF: '#f97316',
  NPS: '#06b6d4',
  REAL_ESTATE: '#ef4444',
  CRYPTO: '#ec4899',
  CASH: '#8b5cf6',
  ETF: '#3b82f6',
  BOND: '#84cc16',
}

/**
 * Status colors.
 */
export const STATUS_COLORS = {
  success: '#22c55e',
  warning: '#f59e0b',
  error: '#ef4444',
  info: '#06b6d4',
}

/**
 * P&L color based on value.
 */
export function getPnLColor(value) {
  if (value > 0) return '#22c55e'
  if (value < 0) return '#ef4444'
  return '#94a3b8'
}
