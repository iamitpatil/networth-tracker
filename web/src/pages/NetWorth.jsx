import React, { useState, useEffect } from 'react';
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { TrendingUp, TrendingDown, Wallet, PiggyBank, CreditCard, Home, Car, Shield, AlertTriangle, IndianRupee } from 'lucide-react';
import client from '../api/client';

const gradeColors = {
  'A+': '#22c55e',
  'A': '#22c55e',
  'B+': '#84cc16',
  'B': '#eab308',
  'C': '#f59e0b',
  'D': '#ef4444'
};

function NetWorth() {
  const [netWorthData, setNetWorthData] = useState(null);
  const [history, setHistory] = useState([]);
  const [changeData, setChangeData] = useState(null);
  const [breakdown, setBreakdown] = useState(null);
  const [healthScore, setHealthScore] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    async function fetchAll() {
      try {
        setLoading(true);
        const [nw, hist, change, bd, hs] = await Promise.all([
          client.get('/net-worth'),
          client.get('/net-worth/history?days=90'),
          client.get('/net-worth/change?days=30'),
          client.get('/net-worth/breakdown'),
          client.get('/net-worth/health-score')
        ]);
        setNetWorthData(nw.data);
        setHistory(hist.data);
        setChangeData(change.data);
        setBreakdown(bd.data);
        setHealthScore(hs.data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }
    fetchAll();
  }, []);

  const formatCurrency = (val) => {
    if (val == null) return 'Rs. 0';
    const num = typeof val === 'number' ? val : parseFloat(val) || 0;
    return `Rs. ${num.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '64px 0' }}>
        <div>Loading...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '64px 0' }}>
        <div style={{ color: '#ef4444' }}>Error: {error}</div>
      </div>
    );
  }

  const isPositiveChange = changeData?.changePercent >= 0;
  const maxAsset = breakdown?.assets?.reduce((max, a) => a.value > max ? a.value : max, 0) || 1;

  return (
    <div style={{ padding: '24px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
        {/* Header */}
        <div style={{ marginBottom: '32px' }}>
          <h1 style={{ fontSize: '28px', fontWeight: 'bold', marginBottom: '8px' }}>Net Worth</h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '14px' }}>Track your financial position over time</p>
        </div>

        {/* Main Net Worth Display */}
        <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '12px', padding: '24px', marginBottom: '24px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '16px' }}>
            <div style={{ background: '#3b82f6', padding: '10px', borderRadius: '8px' }}>
              <IndianRupee size={24} color="#fff" />
            </div>
            <div>
              <p style={{ color: 'var(--text-muted)', fontSize: '14px', marginBottom: '4px' }}>Total Net Worth</p>
              <h2 style={{ fontSize: '36px', fontWeight: 'bold' }}>
                {formatCurrency(netWorthData?.netWorth)}
              </h2>
            </div>
          </div>
          {changeData && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '4px',
                background: isPositiveChange ? 'rgba(34,197,94,0.15)' : 'rgba(239,68,68,0.15)',
                padding: '4px 12px',
                borderRadius: '20px'
              }}>
                {isPositiveChange ? <TrendingUp size={16} color="#22c55e" /> : <TrendingDown size={16} color="#ef4444" />}
                <span style={{ color: isPositiveChange ? '#22c55e' : '#ef4444', fontSize: '14px', fontWeight: '600' }}>
                  {isPositiveChange ? '+' : ''}{changeData.changePercent?.toFixed(2)}%
                </span>
              </div>
              <span style={{ color: 'var(--text-muted)', fontSize: '13px' }}>vs last 30 days</span>
            </div>
          )}
        </div>

        {/* Assets and Liabilities Summary Cards */}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '16px', marginBottom: '24px' }}>
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '12px', padding: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
              <div style={{ background: 'rgba(34,197,94,0.15)', padding: '8px', borderRadius: '8px' }}>
                <Wallet size={20} color="#22c55e" />
              </div>
              <p style={{ color: 'var(--text-muted)', fontSize: '14px' }}>Total Assets</p>
            </div>
            <p style={{ fontSize: '24px', fontWeight: 'bold', color: '#22c55e' }}>
              {formatCurrency(netWorthData?.totalAssets)}
            </p>
          </div>
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '12px', padding: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
              <div style={{ background: 'rgba(239,68,68,0.15)', padding: '8px', borderRadius: '8px' }}>
                <CreditCard size={20} color="#ef4444" />
              </div>
              <p style={{ color: 'var(--text-muted)', fontSize: '14px' }}>Total Liabilities</p>
            </div>
            <p style={{ fontSize: '24px', fontWeight: 'bold', color: '#ef4444' }}>
              {formatCurrency(netWorthData?.totalLiabilities)}
            </p>
          </div>
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '12px', padding: '20px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' }}>
              <div style={{ background: 'rgba(59,130,246,0.15)', padding: '8px', borderRadius: '8px' }}>
                <PiggyBank size={20} color="#3b82f6" />
              </div>
              <p style={{ color: 'var(--text-muted)', fontSize: '14px' }}>Net Worth</p>
            </div>
            <p style={{ fontSize: '24px', fontWeight: 'bold', color: '#3b82f6' }}>
              {formatCurrency(netWorthData?.netWorth)}
            </p>
          </div>
        </div>

        {/* Grid for Chart and Health Score */}
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '24px', marginBottom: '24px' }}>
          {/* Net Worth History Chart */}
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '12px', padding: '24px' }}>
            <h3 style={{ fontSize: '18px', fontWeight: '600', marginBottom: '20px' }}>Net Worth History (90 Days)</h3>
            <ResponsiveContainer width="100%" height={300}>
              <AreaChart data={history}>
                <defs>
                  <linearGradient id="netWorthGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                    <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis
                  dataKey="date"
                  stroke="var(--text-muted)"
                  tick={{ fontSize: 12 }}
                  tickFormatter={(val) => new Date(val).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                />
                <YAxis
                  stroke="var(--text-muted)"
                  tick={{ fontSize: 12 }}
                  tickFormatter={(val) => `Rs ${(val / 100000).toFixed(0)}L`}
                />
                <Tooltip
                  contentStyle={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '8px', color: 'var(--text)' }}
                  formatter={(val) => [formatCurrency(val), 'Net Worth']}
                  labelFormatter={(val) => new Date(val).toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}
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

          {/* Health Score Card */}
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '12px', padding: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
              <div style={{ background: 'rgba(59,130,246,0.15)', padding: '8px', borderRadius: '8px' }}>
                <Shield size={20} color="#3b82f6" />
              </div>
              <h3 style={{ fontSize: '18px', fontWeight: '600' }}>Health Score</h3>
            </div>
            {healthScore && (
              <div style={{ textAlign: 'center' }}>
                <div style={{
                  width: '120px',
                  height: '120px',
                  borderRadius: '50%',
                  border: `8px solid ${gradeColors[healthScore.grade] || '#3b82f6'}`,
                  display: 'flex',
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  margin: '0 auto 16px'
                }}>
                  <span style={{ fontSize: '32px', fontWeight: 'bold' }}>{healthScore.score}</span>
                  <span style={{ fontSize: '20px', fontWeight: 'bold', color: gradeColors[healthScore.grade] }}>
                    {healthScore.grade}
                  </span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', justifyContent: 'center' }}>
                  {healthScore.score >= 80 ? (
                    <Shield size={16} color="#22c55e" />
                  ) : healthScore.score >= 60 ? (
                    <Shield size={16} color="#eab308" />
                  ) : (
                    <AlertTriangle size={16} color="#ef4444" />
                  )}
                  <span style={{ color: 'var(--text-muted)', fontSize: '14px' }}>
                    {healthScore.score >= 80 ? 'Excellent' : healthScore.score >= 60 ? 'Good' : 'Needs Improvement'}
                  </span>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Asset Breakdown and Liabilities */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px' }}>
          {/* Asset Breakdown */}
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '12px', padding: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
              <div style={{ background: 'rgba(34,197,94,0.15)', padding: '8px', borderRadius: '8px' }}>
                <Home size={20} color="#22c55e" />
              </div>
              <h3 style={{ fontSize: '18px', fontWeight: '600' }}>Asset Breakdown</h3>
            </div>
            {breakdown?.assets?.map((asset, idx) => (
              <div key={idx} style={{ marginBottom: '16px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                  <span style={{ fontSize: '14px', color: 'var(--text)' }}>{asset.category}</span>
                  <span style={{ fontSize: '14px', color: 'var(--text-muted)' }}>{formatCurrency(asset.value)}</span>
                </div>
                <div style={{ width: '100%', height: '8px', background: 'var(--border)', borderRadius: '4px', overflow: 'hidden' }}>
                  <div style={{
                    width: `${(asset.value / maxAsset) * 100}%`,
                    height: '100%',
                    background: '#3b82f6',
                    borderRadius: '4px',
                    transition: 'width 0.3s ease'
                  }} />
                </div>
              </div>
            ))}
          </div>

          {/* Liabilities Summary */}
          <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: '12px', padding: '24px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '20px' }}>
              <div style={{ background: 'rgba(239,68,68,0.15)', padding: '8px', borderRadius: '8px' }}>
                <CreditCard size={20} color="#ef4444" />
              </div>
              <h3 style={{ fontSize: '18px', fontWeight: '600' }}>Liabilities</h3>
            </div>
            {breakdown?.liabilities?.map((liability, idx) => (
              <div key={idx} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 0', borderBottom: '1px solid var(--border)' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <Car size={18} color="var(--text-muted)" />
                  <div>
                    <p style={{ fontSize: '14px', color: 'var(--text)' }}>{liability.category}</p>
                    <p style={{ fontSize: '12px', color: 'var(--text-muted)' }}>{liability.count || 1} item(s)</p>
                  </div>
                </div>
                <span style={{ fontSize: '14px', color: '#ef4444', fontWeight: '600' }}>
                  -{formatCurrency(liability.value)}
                </span>
              </div>
            ))}
            <div style={{ display: 'flex', justifyContent: 'space-between', padding: '16px 0 0', borderTop: '1px solid var(--border)', marginTop: '12px' }}>
              <span style={{ fontSize: '16px', fontWeight: '600', color: 'var(--text)' }}>Total</span>
              <span style={{ fontSize: '16px', fontWeight: 'bold', color: '#ef4444' }}>
                {formatCurrency(netWorthData?.totalLiabilities)}
              </span>
            </div>
          </div>
        </div>
      </div>
  );
}

export default NetWorth;
