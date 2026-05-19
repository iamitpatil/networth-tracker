import { useState, useRef, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import {
  Eye, EyeOff, Mail, Lock, ArrowRight, ShieldCheck,
  PieChart, Target, Brain, Wallet, BarChart3, Shield, Users, Receipt,
  ChevronRight
} from 'lucide-react'
import AppLogo from '../components/AppLogo'

const FEATURES = [
  { icon: PieChart, label: 'Multi-Asset Portfolio', desc: 'Equity, MF, Gold, Crypto, FD, Bonds & more' },
  { icon: BarChart3, label: 'Live Market Prices', desc: 'Real-time data from Upstox, NSE & AMFI' },
  { icon: Target, label: 'Goal Tracking', desc: 'Link holdings to goals with auto-progress' },
  { icon: Brain, label: 'AI-Powered Insights', desc: 'Local LLM for portfolio Q&A and analysis' },
  { icon: Receipt, label: 'Tax Engine', desc: 'LTCG/STCG, regime comparison & 80C tracking' },
  { icon: Users, label: 'Family Dashboard', desc: 'Aggregate net worth across family members' },
]

const STATS = [
  { value: '13+', label: 'Asset Types' },
  { value: '8', label: 'Data Providers' },
  { value: '18', label: 'Pages' },
  { value: '100%', label: 'Self-Hosted' },
]

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [twoFactorCode, setTwoFactorCode] = useState('')
  const [needs2FA, setNeeds2FA] = useState(false)
  const [error, setError] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [activeFeature, setActiveFeature] = useState(0)
  const { login } = useAuth()
  const navigate = useNavigate()
  const totpRef = useRef(null)

  useEffect(() => {
    if (needs2FA && totpRef.current) totpRef.current.focus()
  }, [needs2FA])

  // Auto-rotate featured highlight
  useEffect(() => {
    const t = setInterval(() => setActiveFeature(i => (i + 1) % FEATURES.length), 3000)
    return () => clearInterval(t)
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await login(email, password, needs2FA ? twoFactorCode : undefined)
      navigate('/dashboard')
    } catch (err) {
      const msg = err.response?.data?.message || err.message || ''
      if (msg.toLowerCase().includes('two-factor') && msg.toLowerCase().includes('required')) {
        setNeeds2FA(true)
        setError('')
      } else {
        setError(msg || 'Invalid email or password')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex bg-[var(--bg)]">
      {/* Left panel — Platform showcase */}
      <div className="hidden lg:flex lg:w-[55%] relative overflow-hidden">
        {/* Background layers */}
        <div className="absolute inset-0 bg-gradient-to-br from-blue-600 via-indigo-700 to-violet-900" />
        <div className="absolute inset-0 opacity-[0.07]"
          style={{ backgroundImage: 'radial-gradient(circle at 1px 1px, white 1px, transparent 0)', backgroundSize: '32px 32px' }} />

        {/* Floating orbs */}
        <div className="absolute -top-24 -right-24 w-72 h-72 rounded-full bg-blue-400/20 blur-3xl animate-pulse" style={{ animationDuration: '5s' }} />
        <div className="absolute -bottom-32 -left-32 w-96 h-96 rounded-full bg-violet-400/15 blur-3xl animate-pulse" style={{ animationDuration: '7s' }} />
        <div className="absolute top-1/3 right-1/4 w-48 h-48 rounded-full bg-indigo-300/10 blur-2xl animate-pulse" style={{ animationDuration: '4s' }} />

        <div className="relative z-10 flex flex-col justify-between p-12 w-full">
          {/* Top — Logo & tagline */}
          <div>
            <AppLogo variant="full" dark />
          </div>

          {/* Center — Feature cards */}
          <div className="flex-1 flex flex-col justify-center max-w-lg">
            <h2 className="text-3xl font-bold text-white leading-tight mb-2">
              Everything you need to<br />
              <span className="bg-gradient-to-r from-blue-200 to-violet-200 bg-clip-text text-transparent">
                manage your wealth
              </span>
            </h2>
            <p className="text-blue-200/70 text-sm mb-8">
              A self-hosted platform built for Indian investors. Track all your investments, get AI insights, and plan taxes — all running locally on your machine.
            </p>

            <div className="space-y-2.5">
              {FEATURES.map((f, i) => {
                const Icon = f.icon
                const isActive = i === activeFeature
                return (
                  <div
                    key={i}
                    onMouseEnter={() => setActiveFeature(i)}
                    className={`flex items-center gap-3.5 px-4 py-3 rounded-xl transition-all duration-300 cursor-default ${
                      isActive
                        ? 'bg-white/15 backdrop-blur-sm border border-white/10 shadow-lg shadow-black/10'
                        : 'bg-transparent hover:bg-white/5'
                    }`}
                  >
                    <div className={`w-9 h-9 rounded-lg flex items-center justify-center shrink-0 transition-colors duration-300 ${
                      isActive ? 'bg-white/20' : 'bg-white/[0.07]'
                    }`}>
                      <Icon className={`w-4.5 h-4.5 transition-colors duration-300 ${isActive ? 'text-white' : 'text-blue-200/60'}`} />
                    </div>
                    <div className="min-w-0">
                      <p className={`text-sm font-medium transition-colors duration-300 ${isActive ? 'text-white' : 'text-blue-100/80'}`}>{f.label}</p>
                      <p className={`text-xs transition-all duration-300 overflow-hidden ${
                        isActive ? 'text-blue-200/70 max-h-6 opacity-100 mt-0.5' : 'max-h-0 opacity-0'
                      }`}>{f.desc}</p>
                    </div>
                    {isActive && <ChevronRight className="w-4 h-4 text-white/40 ml-auto shrink-0" />}
                  </div>
                )
              })}
            </div>
          </div>

          {/* Bottom — Stats bar */}
          <div className="flex items-center gap-6 pt-4 border-t border-white/10">
            {STATS.map((s, i) => (
              <div key={i} className="text-center">
                <p className="text-lg font-bold text-white">{s.value}</p>
                <p className="text-[10px] text-blue-200/50 uppercase tracking-wider">{s.label}</p>
              </div>
            ))}
            <div className="ml-auto flex items-center gap-1.5">
              <Shield className="w-3.5 h-3.5 text-green-300/60" />
              <span className="text-[10px] text-green-300/60 uppercase tracking-wider">Data stays on your machine</span>
            </div>
          </div>
        </div>
      </div>

      {/* Right panel — Login form */}
      <div className="flex-1 flex items-center justify-center p-6 sm:p-8">
        <div className="w-full max-w-[380px]">
          {/* Mobile logo */}
          <div className="lg:hidden flex justify-center mb-10">
            <AppLogo variant="full" />
          </div>

          <div className="mb-8">
            <h2 className="text-2xl font-bold text-[var(--text)]">Welcome back</h2>
            <p className="text-sm text-[var(--text-muted)] mt-1">Sign in to continue to your dashboard</p>
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
                  placeholder="Enter your password"
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-[var(--text-muted)] hover:text-[var(--text)] transition"
                >
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {needs2FA && (
              <div className="animate-in fade-in slide-in-from-top-2 duration-300">
                <label className="block text-sm font-medium text-[var(--text)] mb-1.5">Two-Factor Code</label>
                <div className="relative">
                  <ShieldCheck className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-[var(--text-muted)]" />
                  <input
                    ref={totpRef}
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    maxLength={6}
                    value={twoFactorCode}
                    onChange={(e) => setTwoFactorCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                    className="w-full bg-[var(--bg-card)] border border-[var(--border)] rounded-xl pl-10 pr-4 py-3 text-sm text-[var(--text)] placeholder-[var(--text-secondary)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50 focus:border-[var(--primary)] transition tracking-[0.3em] text-center font-mono"
                    placeholder="000000"
                    required
                  />
                </div>
                <p className="text-xs text-[var(--text-muted)] mt-1.5">Enter the 6-digit code from your authenticator app</p>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 bg-gradient-to-r from-blue-500 to-indigo-600 hover:from-blue-600 hover:to-indigo-700 text-white font-medium py-3 rounded-xl transition-all disabled:opacity-60 shadow-lg shadow-blue-500/25 hover:shadow-blue-500/40 active:scale-[0.98]"
            >
              {loading ? (
                <span className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
              ) : (
                <>
                  {needs2FA ? 'Verify & Sign In' : 'Sign In'} <ArrowRight className="w-4 h-4" />
                </>
              )}
            </button>
          </form>

          {/* Divider */}
          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-[var(--border)]" />
            </div>
            <div className="relative flex justify-center text-xs">
              <span className="bg-[var(--bg)] px-3 text-[var(--text-muted)]">or try the demo</span>
            </div>
          </div>

          {/* Demo credentials hint */}
          <div className="p-3.5 rounded-xl bg-[var(--bg-card)] border border-[var(--border)] flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-indigo-500/15 flex items-center justify-center shrink-0">
              <Wallet className="w-4 h-4 text-indigo-400" />
            </div>
            <div className="min-w-0">
              <p className="text-xs text-[var(--text-muted)]">Demo Account</p>
              <p className="text-sm text-[var(--text)] font-mono truncate">demo@networth.app</p>
            </div>
            <button
              type="button"
              onClick={() => { setEmail('demo@networth.app'); setPassword('Demo@1234') }}
              className="ml-auto text-xs text-[var(--primary)] hover:text-[var(--primary-hover)] font-medium shrink-0 transition"
            >
              Fill
            </button>
          </div>

          <p className="mt-6 text-center text-sm text-[var(--text-muted)]">
            Don&apos;t have an account?{' '}
            <Link to="/register" className="text-[var(--primary)] hover:text-[var(--primary-hover)] font-medium transition">
              Create one
            </Link>
          </p>

          {/* Mobile-only feature pills */}
          <div className="lg:hidden mt-8 flex flex-wrap justify-center gap-2">
            {FEATURES.slice(0, 4).map((f, i) => {
              const Icon = f.icon
              return (
                <span key={i} className="inline-flex items-center gap-1.5 text-[11px] text-[var(--text-muted)] bg-[var(--bg-card)] border border-[var(--border)] rounded-full px-3 py-1.5">
                  <Icon className="w-3 h-3" /> {f.label}
                </span>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}
