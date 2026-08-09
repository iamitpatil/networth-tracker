import { useState, useEffect } from 'react'
import { Target, Plus, Trash2, TrendingUp, Calendar, BarChart3, CheckCircle, AlertCircle, Link2 } from 'lucide-react'
import { toast } from 'sonner'
import client from '../api/client'
import { formatINR, formatPercent, formatDate } from '../utils/format'
import { Button, Card, Modal, ConfirmDialog, Input, Select, EmptyState, Badge, PageHeader, PageSkeleton } from '../components/ui'
import GoalHoldingLinker from '../components/GoalHoldingLinker'

const GOAL_TYPES = [
  { value: 'retirement', label: 'Retirement', emoji: '🏖️' },
  { value: 'house', label: 'House', emoji: '🏠' },
  { value: 'education', label: 'Education', emoji: '🎓' },
  { value: 'emergency', label: 'Emergency Fund', emoji: '🚨' },
  { value: 'fire', label: 'FIRE', emoji: '🔥' },
  { value: 'vehicle', label: 'Vehicle', emoji: '🚗' },
  { value: 'travel', label: 'Travel', emoji: '✈️' },
  { value: 'wedding', label: 'Wedding', emoji: '💒' },
  { value: 'other', label: 'Other', emoji: '🎯' },
]

const GOAL_TYPE_MAP = Object.fromEntries(GOAL_TYPES.map(t => [t.value, t]))

const RISK_PROFILES = [
  { value: 'conservative', label: 'Conservative' },
  { value: 'moderate', label: 'Moderate' },
  { value: 'aggressive', label: 'Aggressive' },
]

export default function Goals() {
  const [goals, setGoals] = useState([])
  const [progressMap, setProgressMap] = useState({})
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(null)
  const [linkingGoal, setLinkingGoal] = useState(null)
  const [form, setForm] = useState({
    name: '',
    type: 'retirement',
    targetAmount: '',
    targetDate: '',
    riskProfile: 'moderate',
  })

  async function fetchGoals() {
    setLoading(true)
    try {
      const res = await client.get('/goals')
      const goalsData = res.data || []
      setGoals(goalsData)
      await fetchProgressForGoals(goalsData)
    } catch {
      // toast handled by interceptor
    } finally {
      setLoading(false)
    }
  }

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    fetchGoals()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  /* eslint-enable react-hooks/set-state-in-effect */

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
    if (!form.name.trim()) {
      toast.error('Please enter a goal name')
      return
    }
    if (!form.targetAmount || parseFloat(form.targetAmount) <= 0) {
      toast.error('Please enter a valid target amount')
      return
    }

    setSubmitting(true)
    try {
      const res = await client.post('/goals', {
        name: form.name,
        goalType: form.type,
        targetAmount: parseFloat(form.targetAmount),
        targetDate: form.targetDate || null,
        riskProfile: form.riskProfile,
      })
      toast.success('Goal created successfully')
      const updated = [...goals, res.data]
      setGoals(updated)
      fetchProgressForGoals(updated)
      setShowModal(false)
      setForm({ name: '', type: 'retirement', targetAmount: '', targetDate: '', riskProfile: 'moderate' })
    } catch {
      // toast handled by interceptor
    } finally {
      setSubmitting(false)
    }
  }

  async function performDelete() {
    if (!confirmDelete) return
    try {
      await client.delete(`/goals/${confirmDelete.id}`)
      setGoals(goals.filter((g) => g.id !== confirmDelete.id))
      const newProgress = { ...progressMap }
      delete newProgress[confirmDelete.id]
      setProgressMap(newProgress)
      toast.success('Goal deleted')
    } catch {
      // toast handled by interceptor
    } finally {
      setConfirmDelete(null)
    }
  }

  if (loading) return <PageSkeleton />

  // Aggregate stats
  const totalTarget = goals.reduce((sum, g) => sum + (g.targetAmount || 0), 0)
  const totalCurrent = goals.reduce((sum, g) => {
    const p = progressMap[g.id]
    return sum + (p?.currentAmount || g.currentAmount || 0)
  }, 0)
  const onTrack = goals.filter(g => progressMap[g.id]?.isOnTrack).length

  return (
    <div className="space-y-6">
      <PageHeader
        title="Financial Goals"
        subtitle="Plan and track progress toward your financial aspirations"
        actions={
          <Button icon={Plus} onClick={() => setShowModal(true)}>
            Add Goal
          </Button>
        }
      />

      {/* Summary Cards */}
      {goals.length > 0 && (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Card className="p-5">
            <div className="flex items-start justify-between mb-2">
              <p className="text-sm text-[var(--text-muted)]">Total Goals</p>
              <Target className="w-5 h-5 text-blue-400" />
            </div>
            <p className="text-2xl font-bold">{goals.length}</p>
            <p className="text-xs text-[var(--text-muted)] mt-1">
              {onTrack} on track
            </p>
          </Card>
          <Card className="p-5">
            <div className="flex items-start justify-between mb-2">
              <p className="text-sm text-[var(--text-muted)]">Total Saved</p>
              <TrendingUp className="w-5 h-5 text-green-400" />
            </div>
            <p className="text-2xl font-bold text-green-400">{formatINR(totalCurrent, { compact: true })}</p>
            <p className="text-xs text-[var(--text-muted)] mt-1">
              {totalTarget > 0 ? formatPercent((totalCurrent / totalTarget) * 100, { signed: false }) : '0%'} of target
            </p>
          </Card>
          <Card className="p-5">
            <div className="flex items-start justify-between mb-2">
              <p className="text-sm text-[var(--text-muted)]">Total Target</p>
              <BarChart3 className="w-5 h-5 text-amber-400" />
            </div>
            <p className="text-2xl font-bold text-amber-400">{formatINR(totalTarget, { compact: true })}</p>
            <p className="text-xs text-[var(--text-muted)] mt-1">
              Across all goals
            </p>
          </Card>
        </div>
      )}

      {/* Goals Grid */}
      {goals.length === 0 ? (
        <EmptyState
          icon={Target}
          title="No goals yet"
          description="Start by adding your first financial goal. Whether it's saving for a house, retirement, or that dream vacation."
          action={<Button icon={Plus} onClick={() => setShowModal(true)}>Add Your First Goal</Button>}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {goals.map((goal) => {
            const progress = progressMap[goal.id]
            const currentAmount = progress?.currentAmount || goal.currentAmount || 0
            const progressPct = progress?.progressPercentage ||
              (goal.targetAmount > 0 ? (currentAmount / goal.targetAmount) * 100 : 0)
            const isOnTrack = progress?.isOnTrack
            const remainingDays = progress?.daysRemaining
            const typeInfo = GOAL_TYPE_MAP[goal.goalType] || GOAL_TYPE_MAP.other
            const barColor = progressPct >= 100 ? 'bg-green-500' :
                            isOnTrack === false ? 'bg-red-500' :
                            progressPct >= 75 ? 'bg-green-500' :
                            progressPct >= 50 ? 'bg-blue-500' :
                            'bg-amber-500'

            return (
              <Card key={goal.id} hover className="flex flex-col">
                {/* Header */}
                <div className="flex items-start justify-between mb-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-10 h-10 rounded-lg bg-blue-500/10 flex items-center justify-center flex-shrink-0">
                      <span className="text-lg">{typeInfo.emoji}</span>
                    </div>
                    <div className="min-w-0">
                      <h3 className="font-semibold text-base truncate">{goal.name}</h3>
                      <Badge variant="blue" size="sm">{typeInfo.label}</Badge>
                    </div>
                  </div>
                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => setLinkingGoal(goal)}
                      aria-label={`Link holdings to ${goal.name}`}
                      className="text-[var(--text-muted)] hover:text-blue-400 transition-colors p-1"
                      title="Link holdings"
                    >
                      <Link2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => setConfirmDelete(goal)}
                      aria-label={`Delete ${goal.name}`}
                      className="text-[var(--text-muted)] hover:text-red-400 transition-colors p-1"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>

                {/* Progress */}
                <div className="space-y-1.5 mb-3">
                  <div className="flex items-baseline justify-between">
                    <span className="text-sm text-[var(--text-muted)]">Progress</span>
                    <span className="text-sm font-medium">{progressPct.toFixed(1)}%</span>
                  </div>
                  <div className="w-full bg-[var(--input-bg)] rounded-full h-2 overflow-hidden shadow-inner">
                    <div
                      className={`h-full ${barColor} bg-gradient-to-r from-blue-500 via-blue-400 to-blue-600 rounded-full transition-all duration-700 ease-out`}
                      style={{ width: `${Math.min(progressPct, 100)}%` }}
                    />
                  </div>
                </div>

                {/* Amounts */}
                <div className="flex items-baseline justify-between mb-3 text-sm">
                  <span className="text-green-400 font-medium">{formatINR(currentAmount, { compact: true })}</span>
                  <span className="text-[var(--text-muted)]">/ {formatINR(goal.targetAmount, { compact: true })}</span>
                </div>

                {/* Footer */}
                <div className="mt-auto pt-3 border-t border-[var(--border)] flex items-center justify-between text-xs">
                  {goal.targetDate ? (
                    <span className="flex items-center gap-1 text-[var(--text-muted)]">
                      <Calendar className="w-3 h-3" />
                      {formatDate(goal.targetDate)}
                    </span>
                  ) : (
                    <span className="text-[var(--text-muted)]">No deadline</span>
                  )}
                  {isOnTrack != null && (
                    <Badge
                      variant={isOnTrack ? 'green' : 'red'}
                      icon={isOnTrack ? CheckCircle : AlertCircle}
                      size="sm"
                    >
                      {isOnTrack ? 'On track' : 'Behind'}
                    </Badge>
                  )}
                </div>
                {remainingDays != null && remainingDays > 0 && (
                  <p className="text-xs text-[var(--text-muted)] mt-2">
                    {remainingDays} days remaining
                  </p>
                )}
                {progress?.linkedHoldings > 0 && (
                  <p className="text-xs text-blue-400 mt-1 flex items-center gap-1 cursor-pointer hover:text-blue-300" onClick={() => setLinkingGoal(goal)}>
                    <Link2 className="w-3 h-3" /> {progress.linkedHoldings} holding{progress.linkedHoldings > 1 ? 's' : ''} linked
                  </p>
                )}
                {(!progress?.linkedHoldings || progress.linkedHoldings === 0) && (
                  <p className="text-xs text-[var(--text-secondary)] mt-1 flex items-center gap-1 cursor-pointer hover:text-blue-400" onClick={() => setLinkingGoal(goal)}>
                    <Link2 className="w-3 h-3" /> Link holdings to track progress
                  </p>
                )}
              </Card>
            )
          })}
        </div>
      )}

      {/* Add Goal Modal */}
      <Modal
        open={showModal}
        onClose={() => setShowModal(false)}
        title="Add New Goal"
        description="Set a target and track your progress over time."
        size="md"
        footer={
          <>
            <Button variant="ghost" onClick={() => setShowModal(false)} disabled={submitting}>
              Cancel
            </Button>
            <Button onClick={handleAddGoal} loading={submitting}>
              Create Goal
            </Button>
          </>
        }
      >
        <form onSubmit={handleAddGoal} className="space-y-4">
          <Input
            label="Goal Name"
            placeholder="e.g., Buy a house"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            required
            autoFocus
          />

          <Select
            label="Goal Type"
            value={form.type}
            onChange={(e) => setForm({ ...form, type: e.target.value })}
          >
            {GOAL_TYPES.map((t) => (
              <option key={t.value} value={t.value}>{t.emoji} {t.label}</option>
            ))}
          </Select>

          <Input
            label="Target Amount (₹)"
            type="number"
            placeholder="500000"
            value={form.targetAmount}
            onChange={(e) => setForm({ ...form, targetAmount: e.target.value })}
            required
            hint={form.targetAmount ? formatINR(parseFloat(form.targetAmount)) : 'Enter your target amount'}
          />

          <Input
            label="Target Date"
            type="date"
            value={form.targetDate}
            onChange={(e) => setForm({ ...form, targetDate: e.target.value })}
            hint="Optional - when do you want to achieve this?"
          />

          <Select
            label="Risk Profile"
            value={form.riskProfile}
            onChange={(e) => setForm({ ...form, riskProfile: e.target.value })}
            hint="Used to suggest appropriate investments"
          >
            {RISK_PROFILES.map((r) => (
              <option key={r.value} value={r.value}>{r.label}</option>
            ))}
          </Select>
        </form>
      </Modal>

      <ConfirmDialog
        open={!!confirmDelete}
        onClose={() => setConfirmDelete(null)}
        onConfirm={performDelete}
        title={`Delete "${confirmDelete?.name}"?`}
        description="This will permanently delete this goal and its progress history. This action cannot be undone."
        confirmText="Delete Goal"
      />

      {linkingGoal && (
        <GoalHoldingLinker
          goal={linkingGoal}
          onClose={() => setLinkingGoal(null)}
          onUpdate={() => fetchGoals()}
        />
      )}
    </div>
  )
}
