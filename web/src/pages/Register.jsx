import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import {
  User, Mail, Lock, ArrowRight, Eye, EyeOff,
  Shield, CheckCircle2, PieChart, BarChart3, Target, Brain
} from 'lucide-react'
import AppLogo from '../components/AppLogo'

const BENEFITS = [
  { icon: CheckCircle2, text: 'Track 13+ asset types in one place' },
  { icon: CheckCircle2, text: 'Live prices from Upstox, NSE & AMFI' },
  { icon: CheckCircle2, text: 'AI-powered portfolio insights' },
  { icon: CheckCircle2, text: 'Tax computation with regime comparison' },
  { icon: CheckCircle2, text: 'Family net worth aggregation' },
  { icon: CheckCircle2, text: '100% self-hosted — your data stays local' },
]

export default function Register() {
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const { register } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await register(name, email, password)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  const strength = password.length === 0 ? -1 : password.length < 6 ? 0 : password.length < 8 ? 1 : /(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/.test(password) ? 3 : 2
  const strengthLabel = ['Weak', 'Fair', 'Good', 'Strong'][strength] || ''
  const strengthColor = ['bg-red-500', 'bg-amber-500', 'bg-blue-500', 'bg-green-500'][strength] || ''

  return (
    <div className="min-h-screen flex bg-[var(--bg)]">
      {/* Left panel — Benefits showcase */}
      <div className="hidden lg:flex lg:w-[55%] relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-violet-600 via-indigo-700 to-blue-900" />
        <div className="absolute inset-0 opacity-[0.07]"
          style={{ backgroundImage: 'radial-gradient(circle at 1px 1px, white 1px, transparent 0)', backgroundSize: '32px 32px' }} />

        <div className="absolute -top-24 -left-24 w-72 h-72 rounded-full bg-violet-400/20 blur-3xl animate-pulse" style={{ animationDuration: '5s' }} />
        <div className="absolute -bottom-32 -right-32 w-96 h-96 rounded-full bg-blue-400/15 blur-3xl animate-pulse" style={{ animationDuration: '7s' }} />

        <div className="relative z-10 flex flex-col justify-between p-12 w-full">
          <div>
            <AppLogo variant="full" dark />
          </div>

          <div className="flex-1 flex flex-col justify-center max-w-lg">
            <h2 className="text-3xl font-bold text-white leading-tight mb-2">
              Start tracking your<br />
              <span className="bg-gradient-to-r from-violet-200 to-blue-200 bg-clip-text text-transparent">
                complete financial picture
              </span>
            </h2>
            <p className="text-violet-200/70 text-sm mb-10">
              Join and get a unified view of all your investments, net worth, and financial goals.
            </p>

            <div className="space-y-4">
              {BENEFITS.map((b, i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className="w-6 h-6 rounded-full bg-green-400/20 flex items-center justify-center shrink-0">
                    <b.icon className="w-3.5 h-3.5 text-green-300" />
                  </div>
                  <p className="text-sm text-blue-100/90">{b.text}</p>
                </div>
              ))}
            </div>

            {/* Mini feature icons */}
            <div className="mt-10 flex items-center gap-4">
              {[PieChart, BarChart3, Target, Brain, Shield].map((Icon, i) => (
                <div key={i} className="w-10 h-10 rounded-lg bg-white/[0.07] flex items-center justify-center border border-white/[0.06]">
                  <Icon className="w-4.5 h-4.5 text-blue-200/50" />
                </div>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-1.5 pt-4 border-t border-white/10">
            <Shield className="w-3.5 h-3.5 text-green-300/60" />
            <span className="text-[10px] text-green-300/60 uppercase tracking-wider">Your data never leaves your machine</span>
          </div>
        </div>
      </div>

      {/* Right panel — Register form */}
      <div className="flex-1 flex items-center justify-center p-6 sm:p-8">
        <div className="w-full max-w-[380px]">
          {/* Mobile logo */}
          <div className="lg:hidden flex justify-center mb-10">
            <AppLogo variant="full" />
          </div>

          <div className="mb-8">
            <h2 className="text-2xl font-bold text-[var(--text)]">Create your account</h2>
            <p className="text-sm text-[var(--text-muted)] mt-1">Get started in under a minute</p>
          </div>

          {error && (
            <div className="mb-5 p-3.5 bg-red-500/10 border border-red-500/20 rounded-xl flex items-start gap-3">
              <div className="w-5 h-5 rounded-full bg-red-500/20 flex items-center justify-center shrink-0 mt-0.5">
                <span className="text-red-400 text-xs font-bold">!</span>
              </div>
              <p className="text-sm text-red-400">{error}</p>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-[var(--text)] mb-1.5">Full Name</label>
              <div className="relative">
                <User className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]" />
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full bg-[var(--bg-card)] border border-[var(--border)] rounded-xl pl-10 pr-4 py-3 text-sm text-[var(--text)] placeholder-[var(--text-secondary)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50 focus:border-[var(--primary)] transition"
                  placeholder="John Doe"
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-[var(--text)] mb-1.5">Email</label>
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full bg-[var(--bg-card)] border border-[var(--border)] rounded-xl pl-10 pr-4 py-3 text-sm text-[var(--text)] placeholder-[var(--text-secondary)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50 focus:border-[var(--primary)] transition"
                  placeholder="you@example.com"
                  required
                />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-[var(--text)] mb-1.5">Password</label>
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full bg-[var(--bg-card)] border border-[var(--border)] rounded-xl pl-10 pr-11 py-3 text-sm text-[var(--text)] placeholder-[var(--text-secondary)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50 focus:border-[var(--primary)] transition"
                  placeholder="Create a strong password"
                  required
                  minLength={6}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-[var(--text-muted)] hover:text-[var(--text)] transition"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
              {password.length > 0 && (
                <div className="mt-2 flex items-center gap-2">
                  <div className="flex-1 flex gap-1">
                    {[0, 1, 2, 3].map(i => (
                      <div key={i} className={`h-1 flex-1 rounded-full transition-colors ${i <= strength ? strengthColor : 'bg-[var(--border)]'}`} />
                    ))}
                  </div>
                  <span className={`text-xs ${strength >= 2 ? 'text-green-400' : strength === 1 ? 'text-amber-400' : 'text-red-400'}`}>{strengthLabel}</span>
                </div>
              )}
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 bg-gradient-to-r from-violet-500 to-indigo-600 hover:from-violet-600 hover:to-indigo-700 text-white font-medium py-3 rounded-xl transition-all disabled:opacity-60 shadow-lg shadow-violet-500/25 hover:shadow-violet-500/40 active:scale-[0.98]"
            >
              {loading ? (
                <span className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <>
                  Create Account <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-[var(--text-muted)]">
            Already have an account?{' '}
            <Link to="/login" className="text-[var(--primary)] hover:text-[var(--primary-hover)] font-medium transition">
              Sign in
            </Link>
          </p>

          {/* Mobile benefits */}
          <div className="lg:hidden mt-8 space-y-2.5">
            {BENEFITS.slice(0, 3).map((b, i) => (
              <div key={i} className="flex items-center gap-2.5 text-xs text-[var(--text-muted)]">
                <CheckCircle2 className="w-3.5 h-3.5 text-green-400 shrink-0" />
                {b.text}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
