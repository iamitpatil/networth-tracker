import { useState, useEffect } from 'react'
import { PieChart, Pie, Cell, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts'
import { TrendingUp, TrendingDown, Calendar, DollarSign, AlertCircle, CheckCircle, XCircle, Clock, BarChart3, PieChart as PieIcon, CalendarDays } from 'lucide-react'
import client from '../api/client'

const COLORS = ['#3b82f6', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316']

function Analytics() {
  const [activeTab, setActiveTab] = useState('returns')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

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
    setError(null)
    try {
      const [xirrRes, cagrRes, riskRes, assetRes, sectorRes, sipRes] = await Promise.all([
        client.get('/analytics/xirr'),
        client.get('/analytics/cagr'),
        client.get('/analytics/risk'),
        client.get('/analytics/allocation'),
        client.get('/analytics/allocation/sector'),
        client.get('/analytics/sip-calendar')
      ])

      setXirr(xirrRes.data)
      setCagr(cagrRes.data)
      setRisk(riskRes.data)
      setAssetAllocation(Array.isArray(assetRes.data) ? assetRes.data : assetRes.data?.data || [])
      setSectorAllocation(Array.isArray(sectorRes.data) ? sectorRes.data : sectorRes.data?.data || [])
      setSipCalendar(sipRes.data)
    } catch (err) {
      setError('Failed to load analytics data')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  const formatPercent = (val) => val != null ? `${(val * 100).toFixed(2)}%` : 'N/A'

  const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div style={{ backgroundColor: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 8, padding: 12 }}>
          <p style={{ color: 'var(--text)', margin: 0 }}>{label || payload[0].name}</p>
          {payload.map((entry, i) => (
            <p key={i} style={{ color: entry.color || 'var(--text)', margin: '4px 0' }}>
              {entry.name}: {typeof entry.value === 'number' ? entry.value.toFixed(2) : entry.value}
            </p>
          ))}
        </div>
      )
    }
    return null
  }

  const renderReturnsTab = () => (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 20, marginBottom: 32 }}>
        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <span style={labelStyle}>XIRR</span>
            <TrendingUp size={20} color="#22c55e" />
          </div>
          <div style={valueStyle}>{formatPercent(xirr)}</div>
        </div>

        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <span style={labelStyle}>CAGR</span>
            <TrendingUp size={20} color="#3b82f6" />
          </div>
          <div style={valueStyle}>{formatPercent(cagr)}</div>
        </div>

        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <span style={labelStyle}>Volatility</span>
            <TrendingDown size={20} color="#f59e0b" />
          </div>
          <div style={valueStyle}>{risk ? formatPercent(risk.volatility) : 'N/A'}</div>
        </div>

        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <span style={labelStyle}>Sharpe Ratio</span>
            <BarChart3 size={20} color="#8b5cf6" />
          </div>
          <div style={valueStyle}>{risk?.sharpe != null ? risk.sharpe.toFixed(2) : 'N/A'}</div>
        </div>
      </div>

      {risk?.maxDrawdown != null && (
        <div style={cardStyle}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
            <span style={labelStyle}>Maximum Drawdown</span>
            <AlertCircle size={20} color="#ef4444" />
          </div>
          <div style={{ fontSize: 32, fontWeight: 700, color: '#ef4444' }}>
            {formatPercent(risk.maxDrawdown)}
          </div>
          <p style={{ color: 'var(--text-muted)', fontSize: 14, marginTop: 8 }}>
            The largest peak-to-trough decline in portfolio value
          </p>
        </div>
      )}
    </div>
  )

  const renderAllocationTab = () => (
    <div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))', gap: 24 }}>
        <div style={cardStyle}>
          <h3 style={{ color: 'var(--text)', fontSize: 18, fontWeight: 600, marginBottom: 24, display: 'flex', alignItems: 'center', gap: 8 }}>
            <PieIcon size={20} /> Asset Allocation
          </h3>
          {assetAllocation.length > 0 ? (
            <ResponsiveContainer width="100%" height={320}>
              <PieChart>
                <Pie
                  data={assetAllocation}
                  cx="50%"
                  cy="50%"
                  innerRadius={70}
                  outerRadius={110}
                  paddingAngle={3}
                  dataKey="value"
                  nameKey="name"
                  label={({ name, percent }) => `${name} ${(percent * 100).toFixed(1)}%`}
                >
                  {assetAllocation.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color || COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip content={<CustomTooltip />} />
                <Legend wrapperStyle={{ color: 'var(--text-muted)' }} />
              </PieChart>
            </ResponsiveContainer>
          ) : (
            <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 40 }}>No asset allocation data</p>
          )}
        </div>

        <div style={cardStyle}>
          <h3 style={{ color: 'var(--text)', fontSize: 18, fontWeight: 600, marginBottom: 24, display: 'flex', alignItems: 'center', gap: 8 }}>
            <BarChart3 size={20} /> Sector Allocation
          </h3>
          {sectorAllocation.length > 0 ? (
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={sectorAllocation} layout="vertical" margin={{ left: 20 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" horizontal={false} />
                <XAxis type="number" stroke="var(--text-muted)" tick={{ fill: 'var(--text-muted)' }} />
                <YAxis
                  type="category"
                  dataKey="name"
                  stroke="var(--text-muted)"
                  tick={{ fill: 'var(--text-muted)', fontSize: 12 }}
                  width={100}
                />
                <Tooltip content={<CustomTooltip />} />
                <Bar dataKey="value" name="Allocation %" radius={[0, 6, 6, 0]}>
                  {sectorAllocation.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color || COLORS[index % COLORS.length]} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 40 }}>No sector allocation data</p>
          )}
        </div>
      </div>
    </div>
  )

  const renderSIPCalendarTab = () => {
    if (!sipCalendar) return <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 40 }}>No SIP data available</p>

    const { totalMonthlySIP, upcomingSIPs, missedSIPs, sipList } = sipCalendar

    return (
      <div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 20, marginBottom: 32 }}>
          <div style={cardStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span style={labelStyle}>Total Monthly SIP</span>
              <DollarSign size={20} color="#3b82f6" />
            </div>
            <div style={valueStyle}>₹{totalMonthlySIP?.toLocaleString('en-IN') || '0'}</div>
          </div>

          <div style={cardStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span style={labelStyle}>Upcoming SIPs</span>
              <Clock size={20} color="#f59e0b" />
            </div>
            <div style={{ ...valueStyle, color: '#f59e0b' }}>{upcomingSIPs || 0}</div>
          </div>

          <div style={cardStyle}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span style={labelStyle}>Missed SIPs</span>
              <XCircle size={20} color="#ef4444" />
            </div>
            <div style={{ ...valueStyle, color: '#ef4444' }}>{missedSIPs || 0}</div>
          </div>
        </div>

        <div style={cardStyle}>
          <h3 style={{ color: 'var(--text)', fontSize: 18, fontWeight: 600, marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
            <CalendarDays size={20} /> SIP List
          </h3>
          {sipList && sipList.length > 0 ? (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--border)' }}>
                    <th style={thStyle}>Symbol</th>
                    <th style={thStyle}>Amount</th>
                    <th style={thStyle}>Next SIP Date</th>
                    <th style={thStyle}>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {sipList.map((sip, index) => (
                    <tr key={index} style={{ borderBottom: '1px solid #1e293b' }}>
                      <td style={tdStyle}>
                        <span style={{ color: 'var(--text)', fontWeight: 500 }}>{sip.symbol}</span>
                      </td>
                      <td style={tdStyle}>₹{sip.amount?.toLocaleString('en-IN')}</td>
                      <td style={tdStyle}>{sip.nextSIPDate ? new Date(sip.nextSIPDate).toLocaleDateString('en-IN') : 'N/A'}</td>
                      <td style={tdStyle}>
                        {sip.isMissed ? (
                          <span style={badgeStyle('#ef4444')}><XCircle size={14} /> Missed</span>
                        ) : sip.isDue ? (
                          <span style={badgeStyle('#f59e0b')}><Clock size={14} /> Due</span>
                        ) : (
                          <span style={badgeStyle('#22c55e')}><CheckCircle size={14} /> Active</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p style={{ color: 'var(--text-muted)', textAlign: 'center', padding: 20 }}>No SIPs configured</p>
          )}
        </div>
      </div>
    )
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', padding: '64px 0' }}>
        <div style={{ color: 'var(--text-muted)', fontSize: 18 }}>Loading analytics...</div>
      </div>
    )
  }

  if (error) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', padding: '64px 0' }}>
        <div style={{ color: '#ef4444', fontSize: 18 }}>{error}</div>
      </div>
    )
  }

  const tabs = [
    { id: 'returns', label: 'Returns', icon: TrendingUp },
    { id: 'allocation', label: 'Allocation', icon: PieIcon },
    { id: 'sip', label: 'SIP Calendar', icon: CalendarDays }
  ]

  return (
    <div style={{ padding: '32px', display: 'flex', flexDirection: 'column', gap: '32px' }}>
        <h1 style={{ color: 'var(--text)', fontSize: 28, fontWeight: 700, marginBottom: 32 }}>Analytics</h1>

        <div style={{ display: 'flex', gap: 4, marginBottom: 32, backgroundColor: 'var(--bg-card)', borderRadius: 10, padding: 4, width: 'fit-content' }}>
          {tabs.map(tab => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 8,
                padding: '10px 20px',
                borderRadius: 8,
                border: 'none',
                backgroundColor: activeTab === tab.id ? '#3b82f6' : 'transparent',
                color: activeTab === tab.id ? '#ffffff' : 'var(--text-muted)',
                fontSize: 14,
                fontWeight: 500,
                cursor: 'pointer',
                transition: 'all 0.2s'
              }}
            >
              <tab.icon size={16} />
              {tab.label}
            </button>
          ))}
        </div>

        {activeTab === 'returns' && renderReturnsTab()}
        {activeTab === 'allocation' && renderAllocationTab()}
        {activeTab === 'sip' && renderSIPCalendarTab()}
      </div>
  )
}

const cardStyle = {
  backgroundColor: 'var(--bg-card)',
  borderRadius: 12,
  padding: 24,
  border: '1px solid var(--border)'
}

const labelStyle = {
  color: 'var(--text-muted)',
  fontSize: 14,
  fontWeight: 500,
  textTransform: 'uppercase',
  letterSpacing: '0.05em'
}

const valueStyle = {
  fontSize: 32,
  fontWeight: 700,
  color: 'var(--text)'
}

const thStyle = {
  padding: '12px 16px',
  textAlign: 'left',
  color: 'var(--text-muted)',
  fontSize: 12,
  fontWeight: 600,
  textTransform: 'uppercase',
  letterSpacing: '0.05em'
}

const tdStyle = {
  padding: '14px 16px',
  color: 'var(--text-muted)',
  fontSize: 14
}

const badgeStyle = (color) => ({
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  padding: '4px 12px',
  borderRadius: 20,
  fontSize: 12,
  fontWeight: 600,
  backgroundColor: `${color}20`,
  color: color,
  border: `1px solid ${color}40`
})

export default Analytics
