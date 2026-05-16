import { useState, useEffect } from 'react'
import { Target, Plus, Trash2, TrendingUp, DollarSign, Calendar, BarChart3, X, Loader2, CheckCircle, AlertCircle } from 'lucide-react'
import client from '../api/client'

const GOAL_TYPES = [
  { value: 'retirement', label: 'Retirement' },
  { value: 'house', label: 'House' },
  { value: 'education', label: 'Education' },
  { value: 'emergency', label: 'Emergency Fund' },
  { value: 'fire', label: 'FIRE' },
  { value: 'vehicle', label: 'Vehicle' },
  { value: 'travel', label: 'Travel' },
  { value: 'wedding', label: 'Wedding' },
]

const RISK_PROFILES = [
  { value: 'conservative', label: 'Conservative' },
  { value: 'moderate', label: 'Moderate' },
  { value: 'aggressive', label: 'Aggressive' },
]

function Goals() {
  const [goals, setGoals] = useState([])
  const [progressMap, setProgressMap] = useState({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showModal, setShowModal] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [form, setForm] = useState({
    name: '',
    type: 'retirement',
    targetAmount: '',
    currentAmount: '',
    targetDate: '',
    riskProfile: 'moderate',
  })

  useEffect(() => {
    fetchGoals()
  }, [])

  async function fetchGoals() {
    setLoading(true)
    setError(null)
    try {
      const res = await client.get('/goals')
      const goalsData = res.data || []
      setGoals(goalsData)
      await fetchProgressForGoals(goalsData)
    } catch (err) {
      setError('Failed to load goals')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  async function fetchProgressForGoals(goalsList) {
    const progress = {}
    await Promise.all(
      goalsList.map(async (goal) => {
        try {
          const res = await client.get(`/goals/${goal.id}/progress`)
          progress[goal.id] = res.data
        } catch {
          progress[goal.id] = null
        }
      })
    )
    setProgressMap(progress)
  }

  async function handleAddGoal(e) {
    e.preventDefault()
    setSubmitting(true)
    try {
      const res = await client.post('/goals', {
        name: form.name,
        goalType: form.type,
        targetAmount: parseFloat(form.targetAmount),
        targetDate: form.targetDate ? form.targetDate : null,
        riskProfile: form.riskProfile,
      })
      setGoals([...goals, res.data])
      await fetchProgressForGoals([...goals, res.data])
      setShowModal(false)
      setForm({ name: '', type: 'retirement', targetAmount: '', currentAmount: '', targetDate: '', riskProfile: 'moderate' })
    } catch (err) {
      console.error('Failed to add goal:', err)
    } finally {
      setSubmitting(false)
    }
  }

  async function handleDeleteGoal(id) {
    if (!confirm('Are you sure you want to delete this goal?')) return
    try {
      await client.delete(`/goals/${id}`)
      setGoals(goals.filter((g) => g.id !== id))
      const newProgress = { ...progressMap }
      delete newProgress[id]
      setProgressMap(newProgress)
    } catch (err) {
      console.error('Failed to delete goal:', err)
    }
  }

  const totalTarget = goals.reduce((sum, g) => sum + (g.targetAmount || 0), 0)
  const totalCurrent = goals.reduce((sum, g) => sum + (g.currentAmount || 0), 0)
  const overallProgress = totalTarget > 0 ? (totalCurrent / totalTarget) * 100 : 0

  const getProgressPercent = (goal) => {
    if (!goal.targetAmount) return 0
    return Math.min((goal.currentAmount / goal.targetAmount) * 100, 100)
  }

  const formatCurrency = (val) => {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: 'INR',
      maximumFractionDigits: 0,
    }).format(val || 0)
  }

  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', padding: '64px 0' }}>
        <div style={{ color: 'var(--text-muted)', fontSize: 18 }}>Loading goals...</div>
      </div>
    )
  }

  return (
    <div style={{ padding: '32px', display: 'flex', flexDirection: 'column', gap: '32px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 32 }}>
          <div>
            <h1 style={{ color: 'var(--text)', fontSize: 28, fontWeight: 700, marginBottom: 4 }}>Financial Goals</h1>
            <p style={{ color: 'var(--text-muted)', fontSize: 14 }}>Track and manage your financial goals</p>
          </div>
          <button
            onClick={() => setShowModal(true)}
            style={{
              display: 'flex', alignItems: 'center', gap: 8,
              padding: '10px 20px', borderRadius: 8, border: 'none',
              backgroundColor: '#3b82f6', color: '#fff', fontSize: 14, fontWeight: 600,
              cursor: 'pointer', transition: 'all 0.2s',
            }}
          >
            <Plus size={18} /> Add Goal
          </button>
        </div>

        {error && (
          <div style={{ backgroundColor: 'var(--bg-card)', border: '1px solid #ef4444', borderRadius: 8, padding: 12, marginBottom: 24, display: 'flex', alignItems: 'center', gap: 8 }}>
            <AlertCircle size={18} color="#ef4444" />
            <span style={{ color: '#ef4444' }}>{error}</span>
          </div>
        )}

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 20, marginBottom: 32 }}>
          <div style={{ backgroundColor: 'var(--bg-card)', borderRadius: 12, padding: 24, border: '1px solid var(--border)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span style={{ color: 'var(--text-muted)', fontSize: 14, fontWeight: 500 }}>Total Goals</span>
              <Target size={20} color="#3b82f6" />
            </div>
            <div style={{ fontSize: 32, fontWeight: 700, color: 'var(--text)' }}>{goals.length}</div>
          </div>
          <div style={{ backgroundColor: 'var(--bg-card)', borderRadius: 12, padding: 24, border: '1px solid var(--border)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span style={{ color: 'var(--text-muted)', fontSize: 14, fontWeight: 500 }}>Total Target</span>
              <DollarSign size={20} color="#22c55e" />
            </div>
            <div style={{ fontSize: 32, fontWeight: 700, color: 'var(--text)' }}>{formatCurrency(totalTarget)}</div>
          </div>
          <div style={{ backgroundColor: 'var(--bg-card)', borderRadius: 12, padding: 24, border: '1px solid var(--border)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
              <span style={{ color: 'var(--text-muted)', fontSize: 14, fontWeight: 500 }}>Overall Progress</span>
              <BarChart3 size={20} color="#f59e0b" />
            </div>
            <div style={{ fontSize: 32, fontWeight: 700, color: 'var(--text)' }}>{overallProgress.toFixed(1)}%</div>
            <div style={{ width: '100%', height: 6, backgroundColor: 'var(--border)', borderRadius: 3, marginTop: 8, overflow: 'hidden' }}>
              <div style={{ width: `${overallProgress}%`, height: '100%', backgroundColor: '#22c55e', borderRadius: 3, transition: 'width 0.3s' }} />
            </div>
          </div>
        </div>

        {goals.length === 0 ? (
          <div style={{ backgroundColor: 'var(--bg-card)', borderRadius: 12, padding: 60, border: '1px solid var(--border)', textAlign: 'center' }}>
            <Target size={48} color="var(--border)" style={{ marginBottom: 16 }} />
            <p style={{ color: 'var(--text-muted)', fontSize: 18 }}>No goals yet. Add your first financial goal!</p>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(360px, 1fr))', gap: 20 }}>
            {goals.map((goal) => {
              const progress = getProgressPercent(goal)
              const goalProgress = progressMap[goal.id]
              return (
                <div key={goal.id} style={{ backgroundColor: 'var(--bg-card)', borderRadius: 12, padding: 24, border: '1px solid var(--border)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
                    <div>
                      <h3 style={{ color: 'var(--text)', fontSize: 18, fontWeight: 600, marginBottom: 4 }}>{goal.name}</h3>
                      <span style={{ display: 'inline-flex', alignItems: 'center', padding: '2px 10px', borderRadius: 12, fontSize: 12, fontWeight: 600, backgroundColor: 'rgba(59,130,246,0.2)', color: '#3b82f6' }}>
                        {GOAL_TYPES.find((t) => t.value === goal.type)?.label || goal.type}
                      </span>
                    </div>
                    <button
                      onClick={() => handleDeleteGoal(goal.id)}
                      style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer', padding: 4, borderRadius: 4, transition: 'color 0.2s' }}
                      onMouseEnter={(e) => (e.target.style.color = '#ef4444')}
                      onMouseLeave={(e) => (e.target.style.color = 'var(--text-muted)')}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>

                  <div style={{ marginBottom: 16 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                      <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>Progress</span>
                      <span style={{ color: 'var(--text)', fontSize: 13, fontWeight: 600 }}>{progress.toFixed(1)}%</span>
                    </div>
                    <div style={{ width: '100%', height: 8, backgroundColor: 'var(--border)', borderRadius: 4, overflow: 'hidden' }}>
                      <div
                        style={{
                          width: `${progress}%`,
                          height: '100%',
                          backgroundColor: progress >= 100 ? '#22c55e' : '#3b82f6',
                          borderRadius: 4,
                          transition: 'width 0.3s',
                        }}
                      />
                    </div>
                  </div>

                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 12 }}>
                    <div>
                      <p style={{ color: 'var(--text-muted)', fontSize: 12, marginBottom: 2 }}>Current</p>
                      <p style={{ color: '#22c55e', fontSize: 14, fontWeight: 600 }}>{formatCurrency(goal.currentAmount)}</p>
                    </div>
                    <div>
                      <p style={{ color: 'var(--text-muted)', fontSize: 12, marginBottom: 2 }}>Target</p>
                      <p style={{ color: 'var(--text)', fontSize: 14, fontWeight: 600 }}>{formatCurrency(goal.targetAmount)}</p>
                    </div>
                  </div>

                  {goal.targetDate && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
                      <Calendar size={14} color="var(--text-muted)" />
                      <span style={{ color: 'var(--text-muted)', fontSize: 13 }}>
                        Target: {new Date(goal.targetDate).toLocaleDateString('en-IN', { year: 'numeric', month: 'short', day: 'numeric' })}
                      </span>
                    </div>
                  )}

                  {goal.riskProfile && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <TrendingUp size={14} color="var(--text-muted)" />
                      <span style={{ color: 'var(--text-muted)', fontSize: 13, textTransform: 'capitalize' }}>{goal.riskProfile}</span>
                    </div>
                  )}

                  {goalProgress && (
                    <div style={{ marginTop: 12, padding: 12, backgroundColor: 'var(--bg)', borderRadius: 8, border: '1px solid var(--border)' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
                        <CheckCircle size={14} color="#22c55e" />
                        <span style={{ color: 'var(--text)', fontSize: 13, fontWeight: 600 }}>Progress Details</span>
                      </div>
                      {goalProgress.monthlyRequired && (
                        <p style={{ color: 'var(--text-muted)', fontSize: 12 }}>Monthly Required: <span style={{ color: 'var(--text)' }}>{formatCurrency(goalProgress.monthlyRequired)}</span></p>
                      )}
                      {goalProgress.projectedDate && (
                        <p style={{ color: 'var(--text-muted)', fontSize: 12 }}>Projected: <span style={{ color: 'var(--text)' }}>{new Date(goalProgress.projectedDate).toLocaleDateString('en-IN')}</span></p>
                      )}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}

        {showModal && (
          <div style={{
            position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
            backgroundColor: 'rgba(0,0,0,0.7)', display: 'flex', justifyContent: 'center', alignItems: 'center',
            zIndex: 1000,
          }}>
            <div style={{ backgroundColor: 'var(--bg-card)', borderRadius: 12, padding: 32, border: '1px solid var(--border)', width: '100%', maxWidth: 500 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
                <h2 style={{ color: 'var(--text)', fontSize: 22, fontWeight: 700 }}>Add New Goal</h2>
                <button onClick={() => setShowModal(false)} style={{ background: 'none', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}>
                  <X size={20} />
                </button>
              </div>
              <form onSubmit={handleAddGoal}>
                <div style={{ marginBottom: 16 }}>
                  <label style={{ color: 'var(--text-muted)', fontSize: 13, display: 'block', marginBottom: 6 }}>Goal Name</label>
                  <input
                    type="text" required value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    style={{ width: '100%', padding: 10, borderRadius: 8, border: '1px solid var(--border)', backgroundColor: 'var(--bg)', color: 'var(--text)', fontSize: 14 }}
                    placeholder="e.g., Retirement Fund"
                  />
                </div>
                <div style={{ marginBottom: 16 }}>
                  <label style={{ color: 'var(--text-muted)', fontSize: 13, display: 'block', marginBottom: 6 }}>Goal Type</label>
                  <select
                    value={form.type}
                    onChange={(e) => setForm({ ...form, type: e.target.value })}
                    style={{ width: '100%', padding: 10, borderRadius: 8, border: '1px solid var(--border)', backgroundColor: 'var(--bg)', color: 'var(--text)', fontSize: 14 }}
                  >
                    {GOAL_TYPES.map((t) => (
                      <option key={t.value} value={t.value}>{t.label}</option>
                    ))}
                  </select>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
                  <div>
                    <label style={{ color: 'var(--text-muted)', fontSize: 13, display: 'block', marginBottom: 6 }}>Target Amount</label>
                    <input
                      type="number" required value={form.targetAmount}
                      onChange={(e) => setForm({ ...form, targetAmount: e.target.value })}
                      style={{ width: '100%', padding: 10, borderRadius: 8, border: '1px solid var(--border)', backgroundColor: 'var(--bg)', color: 'var(--text)', fontSize: 14 }}
                      placeholder="1000000"
                    />
                  </div>
                  <div>
                    <label style={{ color: 'var(--text-muted)', fontSize: 13, display: 'block', marginBottom: 6 }}>Current Amount</label>
                    <input
                      type="number" value={form.currentAmount}
                      onChange={(e) => setForm({ ...form, currentAmount: e.target.value })}
                      style={{ width: '100%', padding: 10, borderRadius: 8, border: '1px solid var(--border)', backgroundColor: 'var(--bg)', color: 'var(--text)', fontSize: 14 }}
                      placeholder="0"
                    />
                  </div>
                </div>
                <div style={{ marginBottom: 16 }}>
                  <label style={{ color: 'var(--text-muted)', fontSize: 13, display: 'block', marginBottom: 6 }}>Target Date</label>
                  <input
                    type="date" required value={form.targetDate}
                    onChange={(e) => setForm({ ...form, targetDate: e.target.value })}
                    style={{ width: '100%', padding: 10, borderRadius: 8, border: '1px solid var(--border)', backgroundColor: 'var(--bg)', color: 'var(--text)', fontSize: 14 }}
                  />
                </div>
                <div style={{ marginBottom: 24 }}>
                  <label style={{ color: 'var(--text-muted)', fontSize: 13, display: 'block', marginBottom: 6 }}>Risk Profile</label>
                  <select
                    value={form.riskProfile}
                    onChange={(e) => setForm({ ...form, riskProfile: e.target.value })}
                    style={{ width: '100%', padding: 10, borderRadius: 8, border: '1px solid var(--border)', backgroundColor: 'var(--bg)', color: 'var(--text)', fontSize: 14 }}
                  >
                    {RISK_PROFILES.map((r) => (
                      <option key={r.value} value={r.value}>{r.label}</option>
                    ))}
                  </select>
                </div>
                <div style={{ display: 'flex', gap: 12 }}>
                  <button
                    type="button" onClick={() => setShowModal(false)}
                    style={{ flex: 1, padding: '10px 20px', borderRadius: 8, border: '1px solid var(--border)', backgroundColor: 'transparent', color: 'var(--text-muted)', fontSize: 14, fontWeight: 500, cursor: 'pointer' }}
                  >
                    Cancel
                  </button>
                  <button
                    type="submit" disabled={submitting}
                    style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8, padding: '10px 20px', borderRadius: 8, border: 'none', backgroundColor: '#3b82f6', color: '#fff', fontSize: 14, fontWeight: 600, cursor: submitting ? 'not-allowed' : 'pointer', opacity: submitting ? 0.7 : 1 }}
                  >
                    {submitting ? <Loader2 size={16} style={{ animation: 'spin 1s linear infinite' }} /> : <Plus size={16} />}
                    Add Goal
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
  )
}

export default Goals
