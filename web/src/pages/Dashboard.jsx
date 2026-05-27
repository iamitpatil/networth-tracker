import { useState, useEffect } from 'react'
import client from '../api/client'
import {
  TrendingUp, TrendingDown, Target, Activity, Lightbulb, Wallet,
  PieChart as PieChartIcon, ArrowUpRight, Coins, Landmark, Gem,
  Building2, Bitcoin, Receipt, Sparkles,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import {
  PieChart, Pie, Cell, Sector, Tooltip, ResponsiveContainer,
} from 'recharts'
import { formatINR, formatPercent } from '../utils/format'
import { CHART_PALETTE, ASSET_COLORS } from '../utils/colors'
import { Card, PageHeader, PageSkeleton, EmptyState, Tooltip as UITooltip } from '../components/ui'

const ASSET_ICONS = {
  EQUITY: Landmark,
  MUTUAL_FUND: PieChartIcon,
  GOLD: Gem,
  CRYPTO: Bitcoin,
  REAL_ESTATE: Building2,
  CASH: Wallet,
  FD: Receipt,
  PPF: Receipt,
  EPF: Receipt,
  NPS: Receipt,
  BOND: Receipt,
  ETF: Landmark,
}

export default function Dashboard() {
  const [breakdown, setBreakdown] = useState(null)
  const [healthScore, setHealthScore] = useState(null)
  const [holdings, setHoldings] = useState([])
  const [xirr, setXirr] = useState(null)
  const [goals, setGoals] = useState([])
  const [insights, setInsights] = useState([])
  const [loading, setLoading] = useState(true)

  async function fetchData() {
    try {
      const [breakdownRes, healthRes, holdingsRes, xirrRes, goalsRes, insightsRes] =
        await Promise.all([
          client.get('/net-worth/breakdown'),
          client.get('/net-worth/health-score').catch(() => ({ data: null })),
          client.get('/portfolio/holdings'),
          client.get('/analytics/xirr').catch(() => ({ data: null })),
          client.get('/goals').catch(() => ({ data: [] })),
          client.get('/insights').catch(() => ({ data: [] })),
        ])
      setBreakdown(breakdownRes.data)
      setHealthScore(healthRes.data)
      setHoldings(holdingsRes.data || [])
      setXirr(xirrRes.data)
      setGoals(goalsRes.data || [])
      setInsights(insightsRes.data || [])
    } catch {
      // toast handled
    } finally {
      setLoading(false)
    }
  }

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    fetchData()
  }, [])
  /* eslint-enable react-hooks/set-state-in-effect */

  if (loading) return <PageSkeleton />

  const netWorth = breakdown?.netWorth || 0
  const totalAssets = breakdown?.totalAssets || 0
  const totalLiabilities = breakdown?.totalLiabilities || 0
  const totalInvested = holdings.reduce((sum, h) => sum + (h.quantity * h.averageBuyPrice || 0), 0)
  const currentValue = holdings.reduce((sum, h) => sum + (h.currentValue || 0), 0)
  const totalPnL = currentValue - totalInvested
  const pnlPercent = totalInvested > 0 ? (totalPnL / totalInvested) * 100 : 0

  // Asset allocation for pie chart
  const allocation = breakdown ? [
    { name: 'Equity', value: breakdown.equityValue || 0, key: 'EQUITY' },
    { name: 'Mutual Funds', value: breakdown.mutualFundValue || 0, key: 'MUTUAL_FUND' },
    { name: 'Gold', value: breakdown.goldValue || 0, key: 'GOLD' },
    { name: 'Real Estate', value: breakdown.realEstateValue || 0, key: 'REAL_ESTATE' },
    { name: 'Cash', value: breakdown.cashValue || 0, key: 'CASH' },
    { name: 'Crypto', value: breakdown.cryptoValue || 0, key: 'CRYPTO' },
  ].filter(a => a.value > 0) : []

  // Top 5 holdings
  const topHoldings = [...holdings]
    .sort((a, b) => (b.currentValue || 0) - (a.currentValue || 0))
    .slice(0, 5)

  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <PageHeader
        title="Dashboard"
        subtitle="Your financial command center"
      />

      {/* Top Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card hover className="bg-gradient-to-br from-blue-500/10 via-transparent to-transparent">
          <div className="flex items-start justify-between mb-3">
            <p className="text-sm text-[var(--text-muted)] font-medium">Net Worth</p>
            <div className="p-2 rounded-lg bg-blue-500/20">
              <Wallet className="w-5 h-5 text-blue-400" />
            </div>
          </div>
          <p className="text-2xl font-bold mb-1">{formatINR(netWorth, { compact: true })}</p>
          <p className="text-xs text-[var(--text-muted)]">
            Assets {formatINR(totalAssets, { compact: true })} - Liabilities {formatINR(totalLiabilities, { compact: true })}
          </p>
        </Card>

        <Card hover>
          <div className="flex items-start justify-between mb-3">
            <p className="text-sm text-[var(--text-muted)] font-medium">Portfolio Value</p>
            <div className="p-2 rounded-lg bg-green-500/20">
              <Activity className="w-5 h-5 text-green-400" />
            </div>
          </div>
          <p className="text-2xl font-bold mb-1">{formatINR(currentValue, { compact: true })}</p>
          <div className={`text-xs flex items-center gap-1 ${totalPnL >= 0 ? 'text-green-400' : 'text-red-400'}`}>
            {totalPnL >= 0 ? <ArrowUpRight className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
            {formatINR(Math.abs(totalPnL), { compact: true })} ({formatPercent(pnlPercent)})
          </div>
        </Card>

        <Card hover>
          <div className="flex items-start justify-between mb-3">
            <UITooltip content="Extended Internal Rate of Return - annualized return considering all cash flows">
              <p className="text-sm text-[var(--text-muted)] font-medium cursor-help">XIRR</p>
            </UITooltip>
            <div className="p-2 rounded-lg bg-purple-500/20">
              <TrendingUp className="w-5 h-5 text-purple-400" />
            </div>
          </div>
          <p className="text-2xl font-bold text-purple-400 mb-1">
            {xirr?.xirr != null && isFinite(xirr.xirr) ? `${Math.abs(xirr.xirr * 100) > 9999 ? (xirr.xirr > 0 ? '>9,999' : '<-9,999') : (xirr.xirr * 100).toFixed(2)}%` : '—'}
          </p>
          <p className="text-xs text-[var(--text-muted)]">Annualized return</p>
        </Card>

        <Card hover>
          <div className="flex items-start justify-between mb-3">
            <p className="text-sm text-[var(--text-muted)] font-medium">Health Score</p>
            <div className="p-2 rounded-lg bg-amber-500/20">
              <Sparkles className="w-5 h-5 text-amber-400" />
            </div>
          </div>
          <p className="text-2xl font-bold text-amber-400 mb-1">
            {healthScore?.grade || '—'}
          </p>
          <p className="text-xs text-[var(--text-muted)]">
            {healthScore?.totalScore ? `${healthScore.totalScore.toFixed(0)}/100` : 'Not calculated'}
          </p>
        </Card>
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Asset Allocation Pie */}
        <Card className="lg:col-span-2">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold flex items-center gap-2">
              <PieChartIcon className="w-4 h-4 text-blue-400" />
              Asset Allocation
            </h2>
            <Link to="/net-worth" className="text-xs text-blue-400 hover:text-blue-300 flex items-center gap-1">
              Details <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>
          {allocation.length === 0 ? (
            <EmptyState
              icon={PieChartIcon}
              title="No assets yet"
              description="Add your first holding to see allocation."
              action={<Link to="/holdings" className="text-blue-400 hover:text-blue-300 text-sm">Add Holding →</Link>}
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div style={{ height: 280 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={allocation}
                      cx="50%"
                      cy="50%"
                      innerRadius={60}
                      outerRadius={100}
                      paddingAngle={1}
                      strokeWidth={0}
                      dataKey="value"
                      activeShape={(props) => <Sector {...props} outerRadius={props.outerRadius + 6} />}
                    >
                      {allocation.map((entry, idx) => (
                        <Cell key={idx} fill={ASSET_COLORS[entry.key] || CHART_PALETTE[idx % CHART_PALETTE.length]} cursor="pointer" />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        background: 'var(--bg-card)',
                        border: '1px solid var(--border)',
                        borderRadius: '8px',
                      }}
                      itemStyle={{ color: 'var(--text)' }}
                      formatter={(v) => formatINR(v)}
                    />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="space-y-2">
                {allocation.map((item, idx) => {
                  const pct = totalAssets > 0 ? (item.value / totalAssets) * 100 : 0
                  return (
                    <div key={idx} className="flex items-center justify-between p-2 rounded-lg hover:bg-[var(--input-bg)]/50 transition-colors">
                      <div className="flex items-center gap-2 min-w-0">
                        <div
                          className="w-3 h-3 rounded-full flex-shrink-0"
                          style={{ backgroundColor: ASSET_COLORS[item.key] || CHART_PALETTE[idx % CHART_PALETTE.length] }}
                        />
                        <span className="text-sm truncate">{item.name}</span>
                      </div>
                      <div className="text-right flex-shrink-0">
                        <p className="text-sm font-medium">{formatINR(item.value, { compact: true })}</p>
                        <p className="text-xs text-[var(--text-muted)]">{pct.toFixed(1)}%</p>
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          )}
        </Card>

        {/* AI Insights */}
        <Card className="bg-gradient-to-br from-purple-500/5 via-transparent to-blue-500/5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold flex items-center gap-2">
              <Sparkles className="w-4 h-4 text-purple-400" />
              AI Insights
            </h2>
            <Link to="/ai-chat" className="text-xs text-blue-400 hover:text-blue-300">
              Chat →
            </Link>
          </div>
          {insights.length === 0 ? (
            <div className="text-center py-8">
              <Lightbulb className="w-10 h-10 text-[var(--text-muted)] mx-auto mb-2" />
              <p className="text-sm text-[var(--text-muted)]">No insights yet</p>
            </div>
          ) : (
            <div className="space-y-3">
              {insights.slice(0, 4).map((insight, idx) => {
                const text = typeof insight === 'string' ? insight : insight.message || insight.text || JSON.stringify(insight)
                return (
                  <div key={idx} className="flex items-start gap-2 p-3 rounded-lg bg-[var(--input-bg)]/50">
                    <Lightbulb className="w-4 h-4 text-amber-400 flex-shrink-0 mt-0.5" />
                    <p className="text-sm text-[var(--text)]">{text}</p>
                  </div>
                )
              })}
            </div>
          )}
        </Card>
      </div>

      {/* Bottom Row: Top Holdings + Goals */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold flex items-center gap-2">
              <TrendingUp className="w-4 h-4 text-green-400" />
              Top Holdings
            </h2>
            <Link to="/holdings" className="text-xs text-blue-400 hover:text-blue-300 flex items-center gap-1">
              View all <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>
          {topHoldings.length === 0 ? (
            <EmptyState
              icon={Activity}
              title="No holdings"
              description="Add your first investment to track its performance."
            />
          ) : (
            <div className="space-y-2">
              {topHoldings.map((h) => {
                const Icon = ASSET_ICONS[h.assetType] || Coins
                const pnl = h.unrealizedPnl || 0
                const pnlPct = h.averageBuyPrice && h.currentPrice
                  ? ((h.currentPrice - h.averageBuyPrice) / h.averageBuyPrice) * 100
                  : 0
                return (
                  <div key={h.id} className="flex items-center justify-between p-3 rounded-lg hover:bg-[var(--input-bg)]/50 transition-colors">
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="p-2 rounded-lg bg-blue-500/10 flex-shrink-0">
                        <Icon className="w-4 h-4 text-blue-400" />
                      </div>
                      <div className="min-w-0">
                        <p className="text-sm font-medium truncate">{h.symbol}</p>
                        <p className="text-xs text-[var(--text-muted)] truncate">{h.name || h.assetType}</p>
                      </div>
                    </div>
                    <div className="text-right flex-shrink-0">
                      <p className="text-sm font-semibold">{formatINR(h.currentValue, { compact: true })}</p>
                      <p className={`text-xs ${pnl >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                        {formatPercent(pnlPct)}
                      </p>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </Card>

        <Card>
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold flex items-center gap-2">
              <Target className="w-4 h-4 text-blue-400" />
              Goals Progress
            </h2>
            <Link to="/goals" className="text-xs text-blue-400 hover:text-blue-300 flex items-center gap-1">
              View all <ArrowUpRight className="w-3 h-3" />
            </Link>
          </div>
          {goals.length === 0 ? (
            <EmptyState
              icon={Target}
              title="No goals"
              description="Set financial goals to track your progress."
            />
          ) : (
            <div className="space-y-3">
              {goals.slice(0, 4).map((goal) => {
                const progress = goal.targetAmount > 0
                  ? Math.min((goal.currentAmount / goal.targetAmount) * 100, 100)
                  : 0
                return (
                  <div key={goal.id}>
                    <div className="flex items-center justify-between mb-1">
                      <span className="text-sm font-medium truncate">{goal.name}</span>
                      <span className="text-xs text-[var(--text-muted)]">{progress.toFixed(0)}%</span>
                    </div>
                    <div className="w-full bg-[var(--input-bg)] rounded-full h-2 overflow-hidden shadow-inner">
                      <div
                        className="h-full bg-gradient-to-r from-blue-500 via-blue-400 to-blue-600 rounded-full transition-all duration-700 ease-out"
                        style={{ width: `${progress}%` }}
                      />
                    </div>
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
