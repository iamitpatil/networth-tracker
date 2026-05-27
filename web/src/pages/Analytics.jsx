import { useState, useEffect } from 'react'
import { PieChart, Pie, Cell, Sector, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts'
import { TrendingUp, Calendar, AlertCircle, CheckCircle, XCircle, Clock, BarChart3, PieChart as PieIcon, CalendarDays, Info } from 'lucide-react'
import client from '../api/client'
import { formatINR, formatDate } from '../utils/format'
import { CHART_PALETTE } from '../utils/colors'
import { Card, PageHeader, PageSkeleton, EmptyState, Badge, Tooltip as UITooltip } from '../components/ui'

export default function Analytics() {
  const [activeTab, setActiveTab] = useState('returns')
  const [loading, setLoading] = useState(true)
  const [xirr, setXirr] = useState(null)
  const [cagr, setCagr] = useState(null)
  const [risk, setRisk] = useState(null)
  const [assetAllocation, setAssetAllocation] = useState([])
  const [sectorAllocation, setSectorAllocation] = useState([])
  const [sipCalendar, setSipCalendar] = useState(null)

  useEffect(() => {
    fetchAllData()
  }, [])

  async function fetchAllData() {
    setLoading(true)
    try {
      const [xirrRes, cagrRes, riskRes, assetRes, sectorRes, sipRes] = await Promise.all([
        client.get('/analytics/xirr').catch(() => ({ data: null })),
        client.get('/analytics/cagr').catch(() => ({ data: null })),
        client.get('/analytics/risk').catch(() => ({ data: null })),
        client.get('/analytics/allocation').catch(() => ({ data: [] })),
        client.get('/analytics/allocation/sector').catch(() => ({ data: [] })),
        client.get('/analytics/sip-calendar').catch(() => ({ data: null })),
      ])
      setXirr(xirrRes.data)
      setCagr(cagrRes.data)
      setRisk(riskRes.data)
      setAssetAllocation(Array.isArray(assetRes.data) ? assetRes.data : assetRes.data?.data || [])
      setSectorAllocation(Array.isArray(sectorRes.data) ? sectorRes.data : sectorRes.data?.data || [])
      setSipCalendar(sipRes.data)
    } finally {
      setLoading(false)
    }
  }

  // Backend returns XIRR/CAGR as decimal (0.12 = 12%); we display as percent
  const formatRate = (val) => {
    if (val == null || !isFinite(val)) return '—'
    const pct = val * 100
    if (Math.abs(pct) > 9999) return pct > 0 ? '>9,999%' : '<-9,999%'
    return `${pct.toFixed(2)}%`
  }
  const formatPlain = (val, dec = 2) => val != null ? Number(val).toFixed(dec) : '—'

  if (loading) return <PageSkeleton />

  const tabs = [
    { id: 'returns', label: 'Returns', icon: TrendingUp },
    { id: 'allocation', label: 'Allocation', icon: PieIcon },
    { id: 'sip', label: 'SIP Calendar', icon: CalendarDays },
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="Analytics"
        subtitle="Deep insights into your portfolio performance and risk"
      />

      {/* Tabs */}
      <div className="border-b border-[var(--border)]">
        <div className="flex gap-1 overflow-x-auto">
          {tabs.map((tab) => {
            const Icon = tab.icon
            const isActive = activeTab === tab.id
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`
                  flex items-center gap-2 px-4 py-2.5 text-sm font-medium
                  border-b-2 transition-colors -mb-px
                  ${isActive
                    ? 'border-blue-500 text-blue-400'
                    : 'border-transparent text-[var(--text-muted)] hover:text-[var(--text)]'
                  }
                `}
                role="tab"
                aria-selected={isActive}
              >
                <Icon className="w-4 h-4" />
                {tab.label}
              </button>
            )
          })}
        </div>
      </div>

      {/* Returns Tab */}
      {activeTab === 'returns' && (
        <div className="space-y-4">
          {/* Return Metrics */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <Card className="p-5">
              <div className="flex items-start justify-between mb-3">
                <UITooltip content="Extended Internal Rate of Return - time-weighted returns considering cash flows">
                  <span className="text-sm text-[var(--text-muted)] flex items-center gap-1 cursor-help">
                    XIRR <Info className="w-3 h-3" />
                  </span>
                </UITooltip>
                <TrendingUp className="w-5 h-5 text-green-400" />
              </div>
              <p className="text-2xl font-bold text-green-400">{formatRate(xirr?.xirr)}</p>
              <p className="text-xs text-[var(--text-muted)] mt-1">Annualized return</p>
            </Card>

            <Card className="p-5">
              <div className="flex items-start justify-between mb-3">
                <UITooltip content="Compound Annual Growth Rate over your investment period">
                  <span className="text-sm text-[var(--text-muted)] flex items-center gap-1 cursor-help">
                    CAGR <Info className="w-3 h-3" />
                  </span>
                </UITooltip>
                <BarChart3 className="w-5 h-5 text-blue-400" />
              </div>
              <p className="text-2xl font-bold text-blue-400">{formatRate(cagr?.cagr)}</p>
              <p className="text-xs text-[var(--text-muted)] mt-1">Compound growth</p>
            </Card>

            <Card className="p-5">
              <div className="flex items-start justify-between mb-3">
                <UITooltip content="Sharpe Ratio - higher is better. > 1 is good, > 2 is great">
                  <span className="text-sm text-[var(--text-muted)] flex items-center gap-1 cursor-help">
                    Sharpe Ratio <Info className="w-3 h-3" />
                  </span>
                </UITooltip>
                <TrendingUp className="w-5 h-5 text-purple-400" />
              </div>
              <p className="text-2xl font-bold text-purple-400">{formatPlain(risk?.sharpeRatio)}</p>
              <p className="text-xs text-[var(--text-muted)] mt-1">Risk-adjusted return</p>
            </Card>
          </div>

          {/* Risk Metrics */}
          {risk && (
            <Card>
              <h3 className="text-base font-semibold mb-4 flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-amber-400" />
                Risk Metrics
              </h3>
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <UITooltip content="Standard deviation of returns - measures price fluctuation">
                    <p className="text-xs text-[var(--text-muted)] flex items-center gap-1 cursor-help">
                      Volatility <Info className="w-3 h-3" />
                    </p>
                  </UITooltip>
                  <p className="text-lg font-semibold mt-1">{formatPlain(risk.volatility ? risk.volatility * 100 : null)}%</p>
                </div>
                <div>
                  <UITooltip content="Worst peak-to-trough decline in portfolio value">
                    <p className="text-xs text-[var(--text-muted)] flex items-center gap-1 cursor-help">
                      Max Drawdown <Info className="w-3 h-3" />
                    </p>
                  </UITooltip>
                  <p className="text-lg font-semibold text-red-400 mt-1">
                    {formatPlain(risk.maxDrawdown ? risk.maxDrawdown * 100 : null)}%
                  </p>
                </div>
                <div>
                  <p className="text-xs text-[var(--text-muted)]">Beta</p>
                  <p className="text-lg font-semibold mt-1">{formatPlain(risk.beta) || '—'}</p>
                </div>
              </div>
            </Card>
          )}
        </div>
      )}

      {/* Allocation Tab */}
      {activeTab === 'allocation' && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card>
            <h3 className="text-base font-semibold mb-4 flex items-center gap-2">
              <PieIcon className="w-4 h-4 text-blue-400" />
              Asset Class Allocation
            </h3>
            {assetAllocation.length === 0 ? (
              <p className="text-sm text-[var(--text-muted)] py-8 text-center">No allocation data</p>
            ) : (
              <div style={{ height: 280 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={assetAllocation}
                      cx="50%"
                      cy="50%"
                      innerRadius={50}
                      outerRadius={90}
                      paddingAngle={1}
                      strokeWidth={0}
                      dataKey="value"
                      nameKey="assetType"
                      activeShape={(props) => <Sector {...props} outerRadius={props.outerRadius + 6} />}
                    >
                      {assetAllocation.map((_, idx) => (
                        <Cell key={idx} fill={CHART_PALETTE[idx % CHART_PALETTE.length]} cursor="pointer" />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        background: 'var(--bg-card)',
                        border: '1px solid var(--border)',
                        borderRadius: '8px',
                        color: 'var(--text)',
                      }}
                      itemStyle={{ color: 'var(--text)' }}
                      formatter={(v) => formatINR(v)}
                    />
                    <Legend verticalAlign="bottom" height={36} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            )}
          </Card>

          <Card>
            <h3 className="text-base font-semibold mb-4 flex items-center gap-2">
              <BarChart3 className="w-4 h-4 text-purple-400" />
              Sector Allocation
            </h3>
            {sectorAllocation.length === 0 ? (
              <p className="text-sm text-[var(--text-muted)] py-8 text-center">No sector data</p>
            ) : (
              <div style={{ height: 280 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={sectorAllocation} layout="vertical" margin={{ left: 80 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis type="number" stroke="var(--text-muted)" tickFormatter={(v) => formatINR(v, { compact: true })} />
                    <YAxis type="category" dataKey="sector" stroke="var(--text-muted)" width={80} tick={{ fontSize: 12 }} />
                    <Tooltip
                      contentStyle={{
                        background: 'var(--bg-card)',
                        border: '1px solid var(--border)',
                        borderRadius: '8px',
                      }}
                      formatter={(v) => formatINR(v)}
                    />
                    <Bar dataKey="value" fill="#a855f7" radius={[0, 4, 4, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </Card>
        </div>
      )}

      {/* SIP Calendar Tab */}
      {activeTab === 'sip' && (
        <div className="space-y-4">
          {sipCalendar && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <Card className="p-5">
                <div className="flex items-start justify-between mb-2">
                  <span className="text-sm text-[var(--text-muted)]">Monthly SIP</span>
                  <Calendar className="w-5 h-5 text-blue-400" />
                </div>
                <p className="text-2xl font-bold">{formatINR(sipCalendar.totalMonthlySIP, { compact: true })}</p>
              </Card>
              <Card className="p-5">
                <div className="flex items-start justify-between mb-2">
                  <span className="text-sm text-[var(--text-muted)]">Upcoming</span>
                  <Clock className="w-5 h-5 text-amber-400" />
                </div>
                <p className="text-2xl font-bold text-amber-400">{sipCalendar.upcoming || 0}</p>
              </Card>
              <Card className="p-5">
                <div className="flex items-start justify-between mb-2">
                  <span className="text-sm text-[var(--text-muted)]">Missed</span>
                  <XCircle className="w-5 h-5 text-red-400" />
                </div>
                <p className="text-2xl font-bold text-red-400">{sipCalendar.missed || 0}</p>
              </Card>
            </div>
          )}

          <Card>
            <h3 className="text-base font-semibold mb-4">SIP Schedule</h3>
            {!sipCalendar?.sipList?.length ? (
              <EmptyState
                icon={Calendar}
                title="No SIPs configured"
                description="Set up Systematic Investment Plans to automate your investments."
              />
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead>
                    <tr className="border-b border-[var(--border)]">
                      <th className="text-left py-2.5 px-3 text-xs font-medium text-[var(--text-muted)]">Symbol</th>
                      <th className="text-right py-2.5 px-3 text-xs font-medium text-[var(--text-muted)]">Amount</th>
                      <th className="text-left py-2.5 px-3 text-xs font-medium text-[var(--text-muted)]">Next Date</th>
                      <th className="text-left py-2.5 px-3 text-xs font-medium text-[var(--text-muted)]">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--border)]">
                    {sipCalendar.sipList.map((sip, idx) => {
                      const statusConfig = {
                        UPCOMING: { variant: 'green', icon: CheckCircle },
                        MISSED: { variant: 'red', icon: XCircle },
                        COMPLETED: { variant: 'blue', icon: CheckCircle },
                      }[sip.status] || { variant: 'gray', icon: Clock }
                      return (
                        <tr key={idx} className="hover:bg-[var(--input-bg)]/30 transition-colors">
                          <td className="py-3 px-3 font-medium text-sm">{sip.symbol}</td>
                          <td className="py-3 px-3 text-right text-sm">{formatINR(sip.amount)}</td>
                          <td className="py-3 px-3 text-sm text-[var(--text-muted)]">
                            {formatDate(sip.nextSIPDate)}
                          </td>
                          <td className="py-3 px-3">
                            <Badge variant={statusConfig.variant} icon={statusConfig.icon} size="sm">
                              {sip.status}
                            </Badge>
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </Card>
        </div>
      )}
    </div>
  )
}
