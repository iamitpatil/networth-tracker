import { useState, useEffect } from 'react'
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { TrendingUp, TrendingDown, Wallet, PiggyBank, CreditCard, Home, Car, Shield, AlertTriangle, GraduationCap, Briefcase } from 'lucide-react'
import client from '../api/client'
import { formatINR, formatPercent, formatDate } from '../utils/format'
import { ASSET_COLORS } from '../utils/colors'
import { Card, PageHeader, PageSkeleton, EmptyState, Badge, Tooltip as UITooltip } from '../components/ui'

const gradeColors = {
  'A+': '#22c55e', 'A': '#22c55e',
  'B+': '#84cc16', 'B': '#eab308',
  'C': '#f59e0b', 'D': '#ef4444',
}

const gradeLabels = {
  'A+': 'Excellent', 'A': 'Very Good',
  'B+': 'Good', 'B': 'Fair',
  'C': 'Needs Work', 'D': 'Poor',
}

const LIABILITY_ICONS = {
  HOME_LOAN: Home,
  CAR_LOAN: Car,
  CAR: Car,
  EDUCATION_LOAN: GraduationCap,
  PERSONAL_LOAN: Briefcase,
  CREDIT_CARD: CreditCard,
}

export default function NetWorth() {
  const [netWorthData, setNetWorthData] = useState(null)
  const [history, setHistory] = useState([])
  const [changeData, setChangeData] = useState(null)
  const [breakdown, setBreakdown] = useState(null)
  const [healthScore, setHealthScore] = useState(null)
  const [liabilities, setLiabilities] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchAll()
  }, [])

  async function fetchAll() {
    try {
      setLoading(true)
      const [nw, hist, change, bd, hs, libs] = await Promise.all([
        client.get('/net-worth'),
        client.get('/net-worth/history?days=90'),
        client.get('/net-worth/change?days=30'),
        client.get('/net-worth/breakdown'),
        client.get('/net-worth/health-score'),
        client.get('/liabilities').catch(() => ({ data: [] })),
      ])
      setNetWorthData(nw.data)
      setHistory(hist.data || [])
      setChangeData(change.data)
      setBreakdown(bd.data)
      setHealthScore(hs.data)
      setLiabilities(libs.data || [])
    } finally {
      setLoading(false)
    }
  }

  if (loading) return <PageSkeleton />

  const isPositiveChange = (changeData?.changePercent ?? 0) >= 0
  const maxAsset = breakdown?.assets?.reduce((max, a) => Math.max(max, a.value || 0), 0) || 1

  return (
    <div className="space-y-6">
      <PageHeader
        title="Net Worth"
        subtitle="Track your financial position over time"
      />

      {/* Main Stats Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Net Worth Hero Card */}
        <Card className="lg:col-span-2 bg-gradient-to-br from-blue-500/10 via-blue-500/5 to-transparent">
          <div className="flex items-center justify-between mb-2">
            <p className="text-sm text-[var(--text-muted)] font-medium">Total Net Worth</p>
            <div className="p-2 rounded-lg bg-blue-500/20">
              <Wallet className="w-5 h-5 text-blue-400" />
            </div>
          </div>
          <p className="text-3xl sm:text-4xl font-bold text-[var(--text)]">
            {formatINR(netWorthData?.netWorth)}
          </p>
          {changeData && (
            <div className="flex items-center gap-2 mt-3">
              <Badge
                variant={isPositiveChange ? 'green' : 'red'}
                icon={isPositiveChange ? TrendingUp : TrendingDown}
              >
                {formatPercent(changeData.changePercent)}
              </Badge>
              <span className="text-sm text-[var(--text-muted)]">
                {formatINR(Math.abs(changeData.changeAmount || 0), { compact: true })} in 30 days
              </span>
            </div>
          )}
          <div className="grid grid-cols-2 gap-4 mt-6 pt-6 border-t border-[var(--border)]">
            <div>
              <p className="text-xs text-[var(--text-muted)] mb-1">Total Assets</p>
              <p className="text-lg font-semibold text-green-400">
                {formatINR(netWorthData?.totalAssets, { compact: true })}
              </p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-muted)] mb-1">Total Liabilities</p>
              <p className="text-lg font-semibold text-red-400">
                {formatINR(netWorthData?.totalLiabilities, { compact: true })}
              </p>
            </div>
          </div>
        </Card>

        {/* Health Score Card */}
        <Card>
          <div className="flex items-center justify-between mb-4">
            <p className="text-sm text-[var(--text-muted)] font-medium">Financial Health</p>
            <UITooltip content="Score based on emergency fund, debt ratio, savings rate, and diversification">
              <div className="p-2 rounded-lg bg-purple-500/20 cursor-help">
                <Shield className="w-5 h-5 text-purple-400" />
              </div>
            </UITooltip>
          </div>
          <div className="flex items-center justify-center my-6">
            <div
              className="w-24 h-24 rounded-full flex flex-col items-center justify-center font-bold border-4"
              style={{
                borderColor: gradeColors[healthScore?.grade] || '#94a3b8',
                color: gradeColors[healthScore?.grade] || '#94a3b8',
              }}
            >
              <span className="text-3xl">{healthScore?.grade || '—'}</span>
              <span className="text-xs font-medium text-[var(--text-muted)]">
                {healthScore?.score?.toFixed(0) || 0}/100
              </span>
            </div>
          </div>
          <p className="text-center text-sm text-[var(--text-muted)]">
            {gradeLabels[healthScore?.grade] || 'Unrated'}
          </p>
          {healthScore?.recommendations?.length > 0 && (
            <div className="mt-3 pt-3 border-t border-[var(--border)]">
              <p className="text-xs font-medium text-[var(--text-muted)] mb-1">Top Recommendation</p>
              <p className="text-xs text-[var(--text)]">{healthScore.recommendations[0]}</p>
            </div>
          )}
        </Card>
      </div>

      {/* Net Worth History Chart */}
      <Card>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-base font-semibold">Net Worth History</h2>
          <Badge variant="default">Last 90 days</Badge>
        </div>
        {history.length === 0 ? (
          <EmptyState
            icon={TrendingUp}
            title="No history yet"
            description="Your net worth history will appear here as data accumulates."
          />
        ) : (
          <div style={{ height: 320 }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={history}>
                <defs>
                  <linearGradient id="netWorthGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#3b82f6" stopOpacity={0.4} />
                    <stop offset="100%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis
                  dataKey="date"
                  stroke="var(--text-muted)"
                  tick={{ fontSize: 11 }}
                  tickFormatter={(d) => formatDate(d).split(' ').slice(0, 2).join(' ')}
                />
                <YAxis
                  stroke="var(--text-muted)"
                  tick={{ fontSize: 11 }}
                  tickFormatter={(v) => formatINR(v, { compact: true })}
                />
                <Tooltip
                  contentStyle={{
                    background: 'var(--bg-card)',
                    border: '1px solid var(--border)',
                    borderRadius: '8px',
                    color: 'var(--text)',
                  }}
                  formatter={(v) => [formatINR(v), 'Net Worth']}
                  labelFormatter={formatDate}
                />
                <Area
                  type="monotone"
                  dataKey="netWorth"
                  stroke="#3b82f6"
                  strokeWidth={2}
                  fill="url(#netWorthGradient)"
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        )}
      </Card>

      {/* Asset Breakdown */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <h2 className="text-base font-semibold mb-4 flex items-center gap-2">
            <PiggyBank className="w-4 h-4 text-green-400" />
            Asset Breakdown
          </h2>
          {!breakdown?.assets?.length ? (
            <p className="text-sm text-[var(--text-muted)] py-4">No assets to display</p>
          ) : (
            <div className="space-y-3">
              {breakdown.assets.filter(a => a.value > 0).map((asset) => {
                const pct = (asset.value / maxAsset) * 100
                const color = ASSET_COLORS[asset.type] || '#3b82f6'
                return (
                  <div key={asset.type}>
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-sm font-medium">{asset.label || asset.type}</span>
                      <span className="text-sm text-[var(--text-muted)]">
                        {formatINR(asset.value, { compact: true })}
                      </span>
                    </div>
                    <div className="w-full bg-[var(--input-bg)] rounded-full h-2 overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all duration-500"
                        style={{ width: `${pct}%`, backgroundColor: color }}
                      />
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </Card>

        <Card>
          <h2 className="text-base font-semibold mb-4 flex items-center gap-2">
            <CreditCard className="w-4 h-4 text-red-400" />
            Liabilities
          </h2>
          {liabilities.length === 0 ? (
            <div className="text-center py-8">
              <Shield className="w-10 h-10 text-green-400 mx-auto mb-2" />
              <p className="text-sm font-medium">Debt Free!</p>
              <p className="text-xs text-[var(--text-muted)] mt-1">No outstanding liabilities</p>
            </div>
          ) : (
            <div className="space-y-3">
              {liabilities.map((liability) => {
                const Icon = LIABILITY_ICONS[liability.liabilityType] || CreditCard
                const progress = liability.originalAmount > 0
                  ? ((liability.originalAmount - liability.outstandingAmount) / liability.originalAmount) * 100
                  : 0
                return (
                  <div key={liability.id} className="p-3 rounded-lg bg-[var(--input-bg)]">
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-2">
                        <Icon className="w-4 h-4 text-red-400" />
                        <span className="font-medium text-sm">
                          {liability.lender || liability.liabilityType?.replace('_', ' ')}
                        </span>
                      </div>
                      <span className="text-sm font-semibold text-red-400">
                        {formatINR(liability.outstandingAmount, { compact: true })}
                      </span>
                    </div>
                    <div className="w-full bg-[var(--bg)] rounded-full h-1.5 overflow-hidden">
                      <div
                        className="h-full bg-green-500 rounded-full transition-all duration-500"
                        style={{ width: `${progress}%` }}
                      />
                    </div>
                    <p className="text-xs text-[var(--text-muted)] mt-1">
                      {progress.toFixed(0)}% paid off
                    </p>
                  </div>
                )
              })}
            </div>
          )}
        </Card>
      </div>
    </div>
  )
}
