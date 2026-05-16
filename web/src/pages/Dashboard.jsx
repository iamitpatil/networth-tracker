import { useState, useEffect } from 'react'
import axios from '../api/client'
import {
  DollarSign,
  TrendingUp,
  TrendingDown,
  Heart,
  Target,
  Activity,
  Lightbulb,
  Wallet,
  PieChart as PieChartIcon,
  ArrowUpRight,
  ArrowDownRight,
  Coins,
  Landmark,
  Gem,
  Building2,
  Bitcoin,
} from 'lucide-react'
import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
} from 'recharts'

const COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899']
const PIE_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899']

export default function Dashboard() {
  const [breakdown, setBreakdown] = useState(null)
  const [healthScore, setHealthScore] = useState(null)
  const [holdings, setHoldings] = useState([])
  const [liabilities, setLiabilities] = useState([])
  const [xirr, setXirr] = useState(null)
  const [goals, setGoals] = useState([])
  const [insights, setInsights] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [breakdownRes, healthRes, holdingsRes, liabilitiesRes, xirrRes, goalsRes, insightsRes] =
          await Promise.all([
            axios.get('/net-worth/breakdown'),
            axios.get('/net-worth/health-score'),
            axios.get('/portfolio/holdings'),
            axios.get('/liabilities'),
            axios.get('/analytics/xirr'),
            axios.get('/goals'),
            axios.get('/insights'),
          ])

        setBreakdown(breakdownRes.data)
        setHealthScore(healthRes.data)
        setHoldings(holdingsRes.data || [])
        setLiabilities(liabilitiesRes.data || [])
        setXirr(xirrRes.data)
        setGoals(goalsRes.data || [])
        setInsights(insightsRes.data || [])
      } catch (error) {
        console.error('Error fetching dashboard data:', error)
      } finally {
        setLoading(false)
      }
    }

    fetchData()
  }, [])

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(amount || 0)
  }

  const getAssetAllocation = () => {
    if (!breakdown) return []
    return [
      { name: 'Liquid Assets', value: breakdown.liquidAssets || 0 },
      { name: 'Equity', value: breakdown.equityValue || 0 },
      { name: 'Gold', value: breakdown.goldValue || 0 },
      { name: 'Real Estate', value: breakdown.realEstateValue || 0 },
      { name: 'Cash', value: breakdown.cashValue || 0 },
      { name: 'Crypto', value: breakdown.cryptoValue || 0 },
    ].filter((item) => item.value > 0)
  }

  const getTopHoldings = () => {
    return [...holdings]
      .sort((a, b) => (b.currentValue || 0) - (a.currentValue || 0))
      .slice(0, 5)
  }

  const getSipCount = () => {
    return holdings.filter((h) => h.isSip || h.sipAmount > 0).length
  }

  if (loading) {
    return (
      <div className="flex justify-center py-20 text-[var(--text-muted)]">Loading dashboard...</div>
    )
  }

  const assetAllocation = getAssetAllocation()
  const topHoldings = getTopHoldings()
  const sipCount = getSipCount()

  return (
    <div className="space-y-6">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-[var(--text)]">Dashboard</h1>
          <p className="text-[var(--text-muted)] mt-1">Your financial overview at a glance</p>
        </div>

        {/* Top Row - Main Metrics */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-6">
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="p-3 bg-[#3b82f6]/20 rounded-lg">
                <Wallet className="w-6 h-6 text-[#3b82f6]" />
              </div>
              <span className="text-[#10b981] flex items-center text-sm">
                <ArrowUpRight className="w-4 h-4 mr-1" />
                Net Worth
              </span>
            </div>
            <div className="text-3xl font-bold text-[var(--text)] mb-1">
              {formatCurrency(breakdown?.netWorth)}
            </div>
            <p className="text-[var(--text-muted)] text-sm">Total Net Worth</p>
          </div>

          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="p-3 bg-[#10b981]/20 rounded-lg">
                <TrendingUp className="w-6 h-6 text-[#10b981]" />
              </div>
              <span className="text-[#10b981] flex items-center text-sm">
                <ArrowUpRight className="w-4 h-4 mr-1" />
                Assets
              </span>
            </div>
            <div className="text-3xl font-bold text-[var(--text)] mb-1">
              {formatCurrency(breakdown?.totalAssets)}
            </div>
            <p className="text-[var(--text-muted)] text-sm">Total Assets</p>
          </div>

          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="p-3 bg-[#ef4444]/20 rounded-lg">
                <TrendingDown className="w-6 h-6 text-[#ef4444]" />
              </div>
              <span className="text-[#ef4444] flex items-center text-sm">
                <ArrowDownRight className="w-4 h-4 mr-1" />
                Liabilities
              </span>
            </div>
            <div className="text-3xl font-bold text-[var(--text)] mb-1">
              {formatCurrency(breakdown?.totalLiabilities)}
            </div>
            <p className="text-[var(--text-muted)] text-sm">Total Liabilities</p>
          </div>

          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="p-3 bg-[#8b5cf6]/20 rounded-lg">
                <Heart className="w-6 h-6 text-[#8b5cf6]" />
              </div>
              <span className="text-[#8b5cf6] flex items-center text-sm">
                <Activity className="w-4 h-4 mr-1" />
                Health
              </span>
            </div>
            <div className="text-3xl font-bold text-[var(--text)] mb-1">
              {healthScore?.score || 0}%
            </div>
            <p className="text-[var(--text-muted)] text-sm">Financial Health Score</p>
          </div>
        </div>

        {/* Second Row - Secondary Metrics */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="p-3 bg-[#f59e0b]/20 rounded-lg">
                <Activity className="w-6 h-6 text-[#f59e0b]" />
              </div>
              <span className="text-[var(--text-muted)] text-sm">Annual Return</span>
            </div>
            <div className="text-3xl font-bold text-[var(--text)] mb-1">
              {(xirr?.xirr || 0).toFixed(2)}%
            </div>
            <p className="text-[var(--text-muted)] text-sm">XIRR Returns</p>
          </div>

          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="p-3 bg-[#ec4899]/20 rounded-lg">
                <Target className="w-6 h-6 text-[#ec4899]" />
              </div>
              <span className="text-[var(--text-muted)] text-sm">Active Goals</span>
            </div>
            <div className="text-3xl font-bold text-[var(--text)] mb-1">{goals.length}</div>
            <p className="text-[var(--text-muted)] text-sm">Financial Goals</p>
          </div>

          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="p-3 bg-[#10b981]/20 rounded-lg">
                <Coins className="w-6 h-6 text-[#10b981]" />
              </div>
              <span className="text-[var(--text-muted)] text-sm">Recurring</span>
            </div>
            <div className="text-3xl font-bold text-[var(--text)] mb-1">{sipCount}</div>
            <p className="text-[var(--text-muted)] text-sm">SIP / Investments</p>
          </div>
        </div>

        {/* Middle Row - Charts and Insights */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
          {/* Asset Allocation Pie Chart */}
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-[var(--text)]">Asset Allocation</h2>
              <PieChartIcon className="w-5 h-5 text-[var(--text-muted)]" />
            </div>
            {assetAllocation.length > 0 ? (
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={assetAllocation}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={({ name, percent }) =>
                      `${name} ${(percent * 100).toFixed(0)}%`
                    }
                    outerRadius={100}
                    fill="#8884d8"
                    dataKey="value"
                  >
                    {assetAllocation.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={PIE_COLORS[index % PIE_COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(value) => formatCurrency(value)}
                    contentStyle={{
                      backgroundColor: 'var(--bg-card)',
                      border: '1px solid var(--border)',
                      borderRadius: '8px',
                      color: 'var(--text)',
                    }}
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-[300px] text-[var(--text-muted)]">
                No asset data available
              </div>
            )}
          </div>

          {/* AI Insights Section */}
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-xl font-semibold text-[var(--text)]">AI Insights</h2>
              <Lightbulb className="w-5 h-5 text-[var(--text-muted)]" />
            </div>
            <div className="space-y-4 max-h-[300px] overflow-y-auto custom-scrollbar">
              {insights.length > 0 ? (
                insights.map((insight, index) => (
                  <div
                    key={index}
                    className="bg-[var(--bg)] border border-[var(--border)] rounded-lg p-4 hover:border-[#3b82f6] transition-colors"
                  >
                    <div className="flex items-start gap-3">
                      <div className="p-2 bg-[#3b82f6]/20 rounded-lg flex-shrink-0">
                        <Lightbulb className="w-4 h-4 text-[#3b82f6]" />
                      </div>
                      <div>
                        <p className="text-[var(--text)] text-sm">{insight.message || insight.text || insight}</p>
                        {insight.type && (
                          <span className="inline-block mt-2 text-xs px-2 py-1 rounded-full bg-[#3b82f6]/20 text-[#3b82f6]">
                            {insight.type}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                ))
              ) : (
                <div className="flex items-center justify-center h-[200px] text-[var(--text-muted)]">
                  No insights available
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Bottom Row - Recent Holdings */}
        <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-xl font-semibold text-[var(--text)]">Top Holdings</h2>
            <div className="flex gap-2">
              <Landmark className="w-5 h-5 text-[var(--text-muted)]" />
            </div>
          </div>
          {topHoldings.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-[var(--border)]">
                    <th className="text-left text-[var(--text-muted)] text-sm font-medium pb-3">Asset</th>
                    <th className="text-left text-[var(--text-muted)] text-sm font-medium pb-3">Type</th>
                    <th className="text-right text-[var(--text-muted)] text-sm font-medium pb-3">Quantity</th>
                    <th className="text-right text-[var(--text-muted)] text-sm font-medium pb-3">Buy Price</th>
                    <th className="text-right text-[var(--text-muted)] text-sm font-medium pb-3">Current Price</th>
                    <th className="text-right text-[var(--text-muted)] text-sm font-medium pb-3">Value</th>
                    <th className="text-right text-[var(--text-muted)] text-sm font-medium pb-3">P&L</th>
                  </tr>
                </thead>
                <tbody>
                  {topHoldings.map((holding, index) => {
                    const value = holding.currentValue || (holding.quantity * holding.currentPrice) || 0
                    const cost = holding.buyPrice * holding.quantity || 0
                    const pl = value - cost
                    const plPercent = cost > 0 ? ((pl / cost) * 100).toFixed(2) : 0
                    const isPositive = pl >= 0

                    return (
                      <tr
                        key={index}
                        className="border-b border-[var(--border)]/50 hover:bg-[var(--bg)]/50 transition-colors"
                      >
                        <td className="py-4 text-[var(--text)] font-medium">{holding.name || holding.symbol}</td>
                        <td className="py-4 text-[var(--text-muted)] text-sm">{holding.type || 'Equity'}</td>
                        <td className="py-4 text-right text-[var(--text)] text-sm">
                          {holding.quantity?.toLocaleString('en-IN') || '-'}
                        </td>
                        <td className="py-4 text-right text-[var(--text)] text-sm">
                          {formatCurrency(holding.buyPrice)}
                        </td>
                        <td className="py-4 text-right text-[var(--text)] text-sm">
                          {formatCurrency(holding.currentPrice)}
                        </td>
                        <td className="py-4 text-right text-[var(--text)] font-medium">
                          {formatCurrency(value)}
                        </td>
                        <td className="py-4 text-right">
                          <span
                            className={`inline-flex items-center gap-1 text-sm font-medium ${
                              isPositive ? 'text-[#10b981]' : 'text-[#ef4444]'
                            }`}
                          >
                            {isPositive ? (
                              <ArrowUpRight className="w-3 h-3" />
                            ) : (
                              <ArrowDownRight className="w-3 h-3" />
                            )}
                            {isPositive ? '+' : ''}
                            {plPercent}%
                          </span>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="flex items-center justify-center h-[200px] text-[var(--text-muted)]">
              No holdings available
            </div>
          )}
        </div>

        {/* Asset Breakdown Cards */}
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4 mt-6">
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Coins className="w-4 h-4 text-[#3b82f6]" />
              <span className="text-[var(--text-muted)] text-xs">Liquid</span>
            </div>
            <div className="text-[var(--text)] font-semibold text-sm">
              {formatCurrency(breakdown?.liquidAssets)}
            </div>
          </div>
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <TrendingUp className="w-4 h-4 text-[#10b981]" />
              <span className="text-[var(--text-muted)] text-xs">Equity</span>
            </div>
            <div className="text-[var(--text)] font-semibold text-sm">
              {formatCurrency(breakdown?.equityValue)}
            </div>
          </div>
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Gem className="w-4 h-4 text-[#f59e0b]" />
              <span className="text-[var(--text-muted)] text-xs">Gold</span>
            </div>
            <div className="text-[var(--text)] font-semibold text-sm">
              {formatCurrency(breakdown?.goldValue)}
            </div>
          </div>
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Building2 className="w-4 h-4 text-[#ef4444]" />
              <span className="text-[var(--text-muted)] text-xs">Real Estate</span>
            </div>
            <div className="text-[var(--text)] font-semibold text-sm">
              {formatCurrency(breakdown?.realEstateValue)}
            </div>
          </div>
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <DollarSign className="w-4 h-4 text-[#8b5cf6]" />
              <span className="text-[var(--text-muted)] text-xs">Cash</span>
            </div>
            <div className="text-[var(--text)] font-semibold text-sm">
              {formatCurrency(breakdown?.cashValue)}
            </div>
          </div>
          <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-lg p-4">
            <div className="flex items-center gap-2 mb-2">
              <Bitcoin className="w-4 h-4 text-[#ec4899]" />
              <span className="text-[var(--text-muted)] text-xs">Crypto</span>
            </div>
            <div className="text-[var(--text)] font-semibold text-sm">
              {formatCurrency(breakdown?.cryptoValue)}
            </div>
          </div>
        </div>
    </div>
  )
}
