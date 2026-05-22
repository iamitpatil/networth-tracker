import { useState, useRef, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { toast } from 'sonner'
import client from '../api/client'
import { PdfPasswordModal } from '../components/ui/Modal'
import {
  Send, Bot, User, FileText, Sparkles, Trash2,
  TrendingUp, PieChart, Shield, Loader2, Copy, RotateCcw,
  MessageSquare, Wallet, ArrowRight, Paperclip, History,
  Search, CreditCard, Briefcase, Building2, Play,
  ThumbsUp, ThumbsDown, AlertTriangle, ChevronDown, Brain, Wrench,
} from 'lucide-react'

const SUGGESTED_PROMPTS = [
  { icon: TrendingUp, title: 'Portfolio Analysis', prompt: 'Analyze my portfolio performance and suggest improvements', color: 'text-green-400 bg-green-500/10' },
  { icon: PieChart, title: 'Asset Allocation', prompt: 'How well is my portfolio diversified?', color: 'text-blue-400 bg-blue-500/10' },
  { icon: Shield, title: 'Risk Assessment', prompt: 'What are the biggest risks in my portfolio?', color: 'text-amber-400 bg-amber-500/10' },
  { icon: Wallet, title: 'Credit Card Bill', prompt: 'Here is my credit card bill: Rs 5,000 at Amazon, Rs 2,000 at Swiggy, total Rs 7,000. HDFC Visa ending 1234.', color: 'text-purple-400 bg-purple-500/10' },
]

let _msgId = 0
function nextId() { return ++_msgId }

function AgentSubToolCard({ step }) {
  const [expanded, setExpanded] = useState(false)
  const toolDone = step.durationMs != null
  const toolDur = toolDone ? (step.durationMs / 1000).toFixed(1) + 's' : null
  const argsStr = step.arguments ? JSON.stringify(step.arguments, null, 2) : ''
  // result may be a JSON string or object
  const resultStr = step.result
    ? (typeof step.result === 'string' ? step.result : JSON.stringify(step.result, null, 2))
    : ''

  return (
    <div className={`rounded-lg border overflow-hidden text-[10px] ${
      toolDone ? 'border-[var(--border)] bg-[var(--input-bg)]/30' : 'border-blue-500/30 bg-blue-500/5'
    }`}>
      <button onClick={() => setExpanded(!expanded)} className="w-full flex items-center gap-2 px-2 py-1.5 hover:bg-[var(--input-bg)] transition">
        <Wrench className="w-3 h-3 text-blue-400 flex-shrink-0" />
        <span className="flex-1 text-left truncate">{(step.name || '').replace(/_/g, ' ')}</span>
        {!toolDone && <Loader2 className="w-3 h-3 text-blue-400 animate-spin" />}
        {toolDone && <span className="text-green-400 font-bold">✓</span>}
        {toolDur && <span className="text-[var(--text-muted)]">{toolDur}</span>}
        <ChevronDown className={`w-2.5 h-2.5 text-[var(--text-muted)] transition-transform ${expanded ? '' : '-rotate-90'}`} />
      </button>
      {expanded && (
        <div className="px-2 pb-1.5 space-y-1">
          {argsStr && (
            <div className="text-[9px] text-[var(--text-muted)] font-mono bg-[var(--bg-card)] rounded p-1 max-h-20 overflow-y-auto">
              <pre className="whitespace-pre-wrap break-all">{argsStr}</pre>
            </div>
          )}
          {toolDone && resultStr && (
            <div className="text-[9px] text-green-300 font-mono bg-[var(--bg-card)] rounded p-1 max-h-40 overflow-y-auto">
              <pre className="whitespace-pre-wrap break-all">{resultStr}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

function LiveToolCall({ tc, isActive }) {
  const [expanded, setExpanded] = useState(tc.isAgent || false)
  const name = tc.name || 'unknown'
  const done = tc.durationMs != null
  const dur = done ? (tc.durationMs / 1000).toFixed(1) + 's' : null
  const resultStr = tc.result ? JSON.stringify(tc.result, null, 2) : ''
  const hasAgentSteps = tc.agentSteps && tc.agentSteps.length > 0

  return (
    <div className={`rounded-lg border overflow-hidden text-xs ${
      tc.isAgent ? 'border-cyan-500/30 bg-cyan-500/5' :
      isActive ? 'border-blue-500/40 bg-blue-500/5' : 'border-[var(--border)] bg-[var(--input-bg)]/50'
    }`}>
      <button onClick={() => setExpanded(!expanded)} className="w-full flex items-center gap-2 px-3 py-2 hover:bg-[var(--input-bg)] transition">
        {tc.isAgent
          ? <Bot className="w-3.5 h-3.5 text-cyan-400 flex-shrink-0" />
          : <Wrench className="w-3.5 h-3.5 text-blue-400 flex-shrink-0" />}
        <span className={`font-medium text-xs flex-1 text-left truncate ${tc.isAgent ? 'text-cyan-400' : ''}`}>
          {tc.isAgent ? name.replace(/_/g, ' ') + ' agent' : name.replace(/_/g, ' ')}
        </span>
        {isActive && !done && <Loader2 className="w-3 h-3 text-blue-400 animate-spin" />}
        {done && <span className="text-green-400 font-bold text-[10px]">✓</span>}
        {dur && <span className="text-[var(--text-muted)] text-[10px]">{dur}</span>}
        <ChevronDown className={`w-3 h-3 text-[var(--text-muted)] transition-transform ${expanded ? '' : '-rotate-90'}`} />
      </button>
      {expanded && (
        <div className="px-3 pb-2 space-y-1.5">
          {/* Agent sub-steps (ordered timeline) */}
          {hasAgentSteps && (
            <div className="space-y-1">
              {tc.agentSteps.map((step, i) => {
                if (step.type === 'step') {
                  return (
                    <div key={i} className="flex items-center gap-1.5 text-[10px] text-cyan-300 px-1">
                      <span className="text-cyan-500">{'>'}</span>
                      <span>{step.text}</span>
                    </div>
                  )
                }
                if (step.type === 'reasoning') {
                  const agentReasoningIdx = tc.agentSteps.filter((s, j) => s.type === 'reasoning' && j <= i).length
                  return <ReasoningCard key={i} step={{ ...step, round: 'Agent ' + agentReasoningIdx }} isLive={!step.done} />
                }
                if (step.type === 'tool') {
                  return <AgentSubToolCard key={i} step={step} />
                }
                return null
              })}
            </div>
          )}
          {/* Arguments + Result (for non-agent tools) */}
          {done && !hasAgentSteps && (
            <>
              <div className="text-[10px] text-[var(--text-muted)] font-mono bg-[var(--bg-card)] rounded p-1.5 break-all">
                <pre className="whitespace-pre-wrap">{JSON.stringify(tc.arguments || {}, null, 2)}</pre>
              </div>
              <div className="text-[10px] text-green-300 font-mono bg-[var(--bg-card)] rounded p-1.5 max-h-60 overflow-y-auto break-all">
                <pre className="whitespace-pre-wrap">{resultStr}</pre>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}

function ReasoningCard({ step, isLive = false }) {
  const [expanded, setExpanded] = useState(isLive && !step.done)
  const charCount = step.content?.length || 0

  return (
    <div className={`rounded-lg border overflow-hidden text-xs transition-all ${
      !step.done ? 'border-purple-500/30 bg-purple-500/10' : 'border-purple-500/20 bg-purple-500/5'
    }`}>
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-2 px-3 py-2 hover:bg-[var(--input-bg)] transition"
      >
        {!step.done && <Loader2 className="w-3.5 h-3.5 text-purple-400 animate-spin flex-shrink-0" />}
        {step.done && <Brain className="w-3.5 h-3.5 text-purple-400 flex-shrink-0" />}
        <span className="font-medium text-xs text-purple-400 flex-1 text-left truncate">
          Round {step.round} reasoning
        </span>
        {step.done && <span className="text-purple-400 font-bold text-[10px]">✓</span>}
        {!step.done && <span className="text-purple-300 text-[10px]">{charCount} chars</span>}
        <ChevronDown className={`w-3 h-3 text-[var(--text-muted)] transition-transform ${expanded ? '' : '-rotate-90'}`} />
      </button>
      {expanded && (
        <div className="px-3 pb-2">
          <div className="text-[11px] text-[var(--text-muted)] leading-relaxed max-h-48 overflow-y-auto bg-[var(--bg-card)] rounded p-2 prose prose-xs prose-invert max-w-none prose-p:my-1 prose-ul:my-1 prose-ol:my-1 prose-li:my-0 prose-headings:my-1 prose-pre:my-1 prose-code:text-purple-300">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{step.content || ''}</ReactMarkdown>
            {!step.done && <span className="inline-block w-1.5 h-3.5 bg-purple-400 animate-pulse ml-0.5 align-middle" />}
          </div>
        </div>
      )}
    </div>
  )
}

function ToolCallCard({ tc }) {
  const [expanded, setExpanded] = useState(false)
  const name = tc.tool || tc.name || 'unknown'
  const dur = tc.durationMs != null ? (tc.durationMs / 1000).toFixed(1) + 's' : null
  const pending = tc.type === 'pending_approval'
  const isError = tc.type === 'error' || tc.result?.isError
  const resultStr = tc.result ? JSON.stringify(tc.result, null, 2) : ''
  const hasAgentSteps = tc.agentSteps && tc.agentSteps.length > 0

  return (
    <div className={`rounded-lg border overflow-hidden text-xs ${
      tc.isAgent ? 'border-cyan-500/20 bg-cyan-500/5' : 'border-[var(--border)] bg-[var(--input-bg)]/50'
    }`}>
      <button onClick={() => setExpanded(!expanded)} className="w-full flex items-center gap-2 px-3 py-2 hover:bg-[var(--input-bg)] transition">
        {tc.isAgent
          ? <Bot className="w-3.5 h-3.5 text-cyan-400 flex-shrink-0" />
          : <Wrench className="w-3.5 h-3.5 text-blue-400 flex-shrink-0" />}
        <span className={`font-medium text-xs flex-1 text-left truncate ${tc.isAgent ? 'text-cyan-400' : ''}`}>
          {tc.isAgent ? name.replace(/_/g, ' ') + ' agent' : name.replace(/_/g, ' ')}
        </span>
        {!pending && !isError && <span className="text-green-400 font-bold text-[10px]">✓</span>}
        {pending && <span className="text-amber-400 font-bold text-[10px]">⏳</span>}
        {isError && <span className="text-red-400 font-bold text-[10px]">✗</span>}
        {dur && <span className="text-[var(--text-muted)] text-[10px]">{dur}</span>}
        <ChevronDown className={`w-3 h-3 text-[var(--text-muted)] transition-transform ${expanded ? '' : '-rotate-90'}`} />
      </button>
      {expanded && (
        <div className="px-3 pb-2 space-y-1.5">
          {/* Agent sub-steps (ordered) */}
          {hasAgentSteps && (
            <div className="space-y-1">
              {tc.agentSteps.map((step, i) => {
                if (step.type === 'step') {
                  return (
                    <div key={i} className="flex items-center gap-1.5 text-[10px] text-cyan-300 px-1">
                      <span className="text-cyan-500">{'>'}</span>
                      <span>{step.text}</span>
                    </div>
                  )
                }
                if (step.type === 'reasoning') {
                  const agentReasoningIdx = tc.agentSteps.filter((s, j) => s.type === 'reasoning' && j <= i).length
                  return <ReasoningCard key={i} step={{ ...step, round: 'Agent ' + agentReasoningIdx }} />
                }
                if (step.type === 'tool') {
                  return <AgentSubToolCard key={i} step={step} />
                }
                return null
              })}
            </div>
          )}
          {/* Regular tool args + result */}
          {!hasAgentSteps && (
            <>
              <div className="text-[10px] text-[var(--text-muted)] font-mono bg-[var(--bg-card)] rounded p-1.5 break-all">
                <pre className="whitespace-pre-wrap">{JSON.stringify(tc.arguments || {}, null, 2)}</pre>
              </div>
              <div className={`text-[10px] font-mono bg-[var(--bg-card)] rounded p-1.5 max-h-60 overflow-y-auto break-all ${pending ? 'text-amber-300' : isError ? 'text-red-300' : 'text-green-300'}`}>
                <pre className="whitespace-pre-wrap">{resultStr}</pre>
              </div>
            </>
          )}
        </div>
      )}
    </div>
  )
}

function TransactionCard({ data }) {
  const txns = data?.transactions || []
  if (!txns.length) return null
  const total = data?.totalAmountDue || data?.newCharges || 0
  return (
    <div className="rounded-lg border border-purple-500/20 bg-purple-500/5 overflow-hidden">
      <div className="p-3 border-b border-purple-500/10 flex items-center justify-between">
        <div>
          <p className="text-sm font-semibold">{data.cardIssuer || ''} {data.cardType || ''} {data.cardLastFourDigits ? '• ' + data.cardLastFourDigits : ''}</p>
          <p className="text-[11px] text-[var(--text-muted)]">{txns.length} transactions</p>
        </div>
        <p className="text-lg font-bold text-purple-400">₹{Number(total).toLocaleString()}</p>
      </div>
      <div className="divide-y divide-purple-500/10 max-h-60 overflow-y-auto">
        {txns.map((t, i) => (
          <div key={i} className="flex items-center justify-between px-3 py-2 text-sm">
            <div className="flex-1 min-w-0">
              <p className="truncate font-medium">{t.description || t.category || 'Transaction'}</p>
              <p className="text-[11px] text-[var(--text-muted)]">{t.category || ''}{t.date ? ' • ' + t.date : ''}</p>
            </div>
            <p className={`font-mono font-medium ml-2 ${Number(t.amount) < 0 ? 'text-red-400' : 'text-green-400'}`}>
              {Number(t.amount) < 0 ? '−' : '+'}₹{Math.abs(Number(t.amount)).toLocaleString()}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}

function ThinkingSection({ steps, defaultOpen = false }) {
  const [open, setOpen] = useState(defaultOpen)

  if (!steps || steps.length === 0) return null

  const toolCount = steps.filter(s => s.stepType === 'tool').length
  const reasoningCount = steps.filter(s => s.stepType === 'reasoning').length

  return (
    <div className="rounded-xl border border-[var(--border)] bg-[var(--bg-card)]/50 overflow-hidden">
      <button
        onClick={() => setOpen(!open)}
        className="w-full flex items-center gap-2 px-3 py-2 hover:bg-[var(--input-bg)] transition text-xs"
      >
        <Brain className="w-3.5 h-3.5 text-purple-400 flex-shrink-0" />
        <span className="font-medium text-[var(--text-muted)] flex-1 text-left">
          Thinking
          {toolCount > 0 ? ` \u00b7 ${toolCount} tool${toolCount > 1 ? 's' : ''}` : ''}
          {reasoningCount > 0 ? ` \u00b7 ${reasoningCount} reasoning step${reasoningCount > 1 ? 's' : ''}` : ''}
        </span>
        <ChevronDown className={`w-3.5 h-3.5 text-[var(--text-muted)] transition-transform ${open ? '' : '-rotate-90'}`} />
      </button>
      {open && (
        <div className="px-3 pb-3 space-y-1.5">
          {steps.map((step, i) => {
            if (step.stepType === 'reasoning') {
              return <ReasoningCard key={`r-${i}`} step={step} />
            }
            if (step.stepType === 'tool') {
              return <ToolCallCard key={`t-${i}`} tc={step} />
            }
            return null
          })}
        </div>
      )}
    </div>
  )
}

function PendingActionCard({ action, onConfirm, onReject, loading }) {
  const toolName = action.toolName || 'unknown'
  return (
    <div className="rounded-lg border border-amber-500/30 bg-amber-500/5 overflow-hidden">
      <div className="p-3 space-y-2">
        <div className="flex items-center gap-2">
          <div className="w-6 h-6 rounded bg-amber-500/20 flex items-center justify-center flex-shrink-0">
            <Wrench className="w-3 h-3 text-amber-400" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-medium text-xs truncate">{toolName.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}</p>
            <p className="text-[10px] text-[var(--text-muted)]">Needs approval</p>
          </div>
          <AlertTriangle className="w-4 h-4 text-amber-400 flex-shrink-0" />
        </div>
        {action.summary && <p className="text-[11px] text-[var(--text)]">{action.summary}</p>}
        <div className="flex gap-1.5">
          <button onClick={() => onConfirm(action.id)} disabled={loading}
            className="flex-1 flex items-center justify-center gap-1 text-[11px] px-2 py-1.5 rounded-lg bg-green-500 text-white hover:bg-green-600 transition disabled:opacity-50">
            {loading ? <Loader2 className="w-3 h-3 animate-spin" /> : <ThumbsUp className="w-3 h-3" />} Approve
          </button>
          <button onClick={() => onReject(action.id)} disabled={loading}
            className="flex-1 flex items-center justify-center gap-1 text-[11px] px-2 py-1.5 rounded-lg bg-red-500/20 text-red-400 hover:bg-red-500/30 transition disabled:opacity-50">
            <ThumbsDown className="w-3 h-3" /> Reject
          </button>
        </div>
      </div>
    </div>
  )
}

export default function AIChat() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [sessionId, setSessionId] = useState(null)
  const [confirmLoading, setConfirmLoading] = useState(null)
  const [attachedFile, setAttachedFile] = useState(null)
  const [pdfPasswordModal, setPdfPasswordModal] = useState({ open: false, file: null, message: '', error: '' })
  const [liveSteps, setLiveSteps] = useState([])  // ordered: reasoning + tool steps
  const [liveThought, setLiveThought] = useState('')
  const [sessions, setSessions] = useState([])
  const [showSessions, setShowSessions] = useState(false)
  const bottomRef = useRef(null)
  const textareaRef = useRef(null)
  const fileInputRef = useRef(null)

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 200) + 'px'
    }
  }, [input])

  useEffect(() => {
    setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 50)
  }, [messages, loading, liveSteps])

  useEffect(() => { loadSessions() }, [])

  const sendStream = async (text, file, pdfPassword) => {
    const userMsgId = nextId()
    const userMsg = { user: true, content: text, id: userMsgId, file: file?.name }
    setMessages(prev => [...prev, userMsg])
    setLoading(true)
    setLiveSteps([])
    setLiveThought('')
    setAttachedFile(null)

    try {
      const token = localStorage.getItem('accessToken') || ''
      const headers = { 'Authorization': `Bearer ${token}` }
      const isForm = !!file
      const url = isForm ? '/api/v1/ai/chat-upload-stream' : '/api/v1/ai/chat-stream'
      const body = isForm
        ? (() => {
            const fd = new FormData()
            fd.append('file', file)
            fd.append('message', text)
            if (sessionId) fd.append('sessionId', sessionId)
            if (pdfPassword) fd.append('password', pdfPassword)
            return fd
          })()
        : (headers['Content-Type'] = 'application/json', JSON.stringify({ message: text, sessionId }))

      const resp = await fetch(url, { method: 'POST', headers, body })
      // Handle password-protected PDF
      if (resp.status === 422) {
        const errData = await resp.json()
        if (errData.error === 'PASSWORD_REQUIRED') {
          setLoading(false)
          // Remove the user message we optimistically added
          setMessages(prev => prev.filter(m => m.id !== userMsgId))
          setPdfPasswordModal({ open: true, file, message: text, error: pdfPassword ? 'Incorrect password. Please try again.' : '' })
          return
        }
      }
      if (!resp.ok) throw new Error('SSE connection failed: ' + resp.status)
      if (!resp.body) throw new Error('Response body is not a stream')
      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = '', currentEvent = '', pendingData = ''
      let botContent = '', botSessionId = sessionId, botPendingActionId = null
      const botMsgId = nextId()
      let accTools = []     // flat list for bot message toolCalls
      let accSteps = []     // ordered timeline of reasoning + tools

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''
        for (const line of lines) {
          const t = line.trim()
          if (!t) {
            currentEvent = ''
            pendingData = ''
            continue
          }
          if (t.startsWith('event:')) { currentEvent = t.slice(6).trim(); continue }
          if (t.startsWith('data:')) {
            pendingData += t.slice(5)
            try {
              const d = JSON.parse(pendingData)
              pendingData = ''
              if (currentEvent === 'thought') {
                setLiveThought(d.content || 'Thinking...')
              } else if (currentEvent === 'tool_start') {
                setLiveThought('')
                const toolStep = { stepType: 'tool', name: d.name, arguments: d.arguments }
                accTools = [...accTools, { name: d.name, arguments: d.arguments }]
                accSteps = [...accSteps, toolStep]
                setLiveSteps([...accSteps])
              } else if (currentEvent === 'tool_end') {
                // Update the tool in accTools
                accTools = accTools.map(x => x.name === d.name && !x.durationMs ? { ...x, durationMs: d.durationMs, result: d.result, type: d.type } : x)
                // Update the matching step in accSteps
                for (let i = accSteps.length - 1; i >= 0; i--) {
                  if (accSteps[i].stepType === 'tool' && accSteps[i].name === d.name && !accSteps[i].durationMs) {
                    accSteps[i] = { ...accSteps[i], durationMs: d.durationMs, result: d.result, type: d.type }
                    break
                  }
                }
                setLiveSteps([...accSteps])
              } else if (currentEvent === 'reasoning') {
                // Full reasoning block (non-streaming fallback)
                accSteps = [...accSteps, { stepType: 'reasoning', round: d.round, content: d.content, done: true }]
                setLiveSteps([...accSteps])
                setLiveThought('Reasoning...')
              } else if (currentEvent === 'reasoning_delta') {
                // Token-by-token streaming reasoning
                const round = d.round
                const existing = accSteps.find(s => s.stepType === 'reasoning' && s.round === round && !s.done)
                if (existing) {
                  existing.content += d.token
                } else {
                  accSteps = [...accSteps, { stepType: 'reasoning', round, content: d.token, done: false }]
                }
                setLiveSteps([...accSteps])
                setLiveThought('Reasoning...')
              } else if (currentEvent === 'reasoning_done') {
                const existing = accSteps.find(s => s.stepType === 'reasoning' && s.round === d.round)
                if (existing) existing.done = true
                setLiveSteps([...accSteps])
                setLiveThought('Thinking...')
              } else if (currentEvent === 'agent_start') {
                // Mark the current tool step as an agent
                const toolStep = accSteps.findLast(s => s.stepType === 'tool' && s.name === d.name)
                if (toolStep) { toolStep.isAgent = true; toolStep.agentId = d.agentId; toolStep.agentSteps = [] }
                setLiveSteps([...accSteps])
              } else if (currentEvent === 'agent_step') {
                const toolStep = accSteps.findLast(s => s.stepType === 'tool' && s.agentId === d.agentId)
                if (toolStep) {
                  if (!toolStep.agentSteps) toolStep.agentSteps = []
                  toolStep.agentSteps.push({ type: 'step', text: d.step })
                  setLiveSteps([...accSteps])
                }
                setLiveThought(d.step)
              } else if (currentEvent === 'agent_tool_start') {
                const toolStep = accSteps.findLast(s => s.stepType === 'tool' && s.agentId === d.agentId)
                if (toolStep) {
                  if (!toolStep.agentSteps) toolStep.agentSteps = []
                  toolStep.agentSteps.push({ type: 'tool', name: d.name, arguments: d.arguments, durationMs: null })
                  setLiveSteps([...accSteps])
                }
                setLiveThought('Agent calling ' + (d.name || '').replace(/_/g, ' ') + '...')
              } else if (currentEvent === 'agent_tool_end') {
                const toolStep = accSteps.findLast(s => s.stepType === 'tool' && s.agentId === d.agentId)
                if (toolStep?.agentSteps) {
                  const agentTool = toolStep.agentSteps.findLast(s => s.type === 'tool' && s.name === d.name && s.durationMs == null)
                  if (agentTool) {
                    agentTool.durationMs = d.durationMs
                    agentTool.result = d.result
                  }
                  setLiveSteps([...accSteps])
                }
              } else if (currentEvent === 'agent_reasoning_delta') {
                const toolStep = accSteps.findLast(s => s.stepType === 'tool' && s.agentId === d.agentId)
                if (toolStep) {
                  if (!toolStep.agentSteps) toolStep.agentSteps = []
                  const lastReasoning = toolStep.agentSteps.findLast(s => s.type === 'reasoning' && !s.done)
                  if (lastReasoning) {
                    lastReasoning.content += d.token
                  } else {
                    toolStep.agentSteps.push({ type: 'reasoning', content: d.token, done: false })
                  }
                  setLiveSteps([...accSteps])
                }
              } else if (currentEvent === 'agent_reasoning_done') {
                const toolStep = accSteps.findLast(s => s.stepType === 'tool' && s.agentId === d.agentId)
                if (toolStep?.agentSteps) {
                  const lastReasoning = toolStep.agentSteps.findLast(s => s.type === 'reasoning')
                  if (lastReasoning) lastReasoning.done = true
                  setLiveSteps([...accSteps])
                }
              } else if (currentEvent === 'agent_end') {
                const toolStep = accSteps.findLast(s => s.stepType === 'tool' && s.agentId === d.agentId)
                if (toolStep) toolStep.agentDone = true
                setLiveSteps([...accSteps])
              } else if (currentEvent === 'response') {
                botContent = d.content || ''
                botSessionId = d.sessionId || sessionId
                if (d.pendingActionId) botPendingActionId = d.pendingActionId
                if (d.sessionId) setSessionId(d.sessionId)
                setMessages(prev => [...prev, {
                  user: false, content: botContent, id: botMsgId,
                  toolCalls: [...accTools],
                  steps: [...accSteps],
                  pendingActionId: botPendingActionId,
                }])
                setLoading(false)
                setLiveSteps([])
                setLiveThought('')
              } else if (currentEvent === 'error') {
                botContent = '⚠️ ' + (d.message || 'Error')
                setMessages(prev => [...prev, {
                  user: false, content: botContent, id: botMsgId,
                  isError: true, steps: [...accSteps], toolCalls: [...accTools],
                }])
                setLoading(false)
                setLiveSteps([])
                setLiveThought('')
              }
              currentEvent = ''
            } catch {
              // JSON incomplete across TCP chunks — keep pendingData
            }
          }
        }
      }

      // If no response/error event was received (stream closed unexpectedly),
      // add whatever we have as a fallback
      if (!botContent && accTools.length > 0) {
        setMessages(prev => [...prev, {
          user: false, content: 'Processing completed. Check the tool results above.',
          id: botMsgId, toolCalls: [...accTools],
        }])
      }
      loadSessions()
    } catch (err) {
      try {
        const { data } = await client.post('/ai/chat-v2', { message: text, sessionId })
        setMessages(prev => [...prev, { user: false, content: data.response, id: nextId(), toolCalls: data.toolCalls || [], pendingActionId: data.pendingActionId || null }])
        if (data.sessionId) setSessionId(data.sessionId)
      } catch (fallbackErr) {
        setMessages(prev => [...prev, { user: false, content: '⚠️ ' + (fallbackErr?.response?.data?.message || fallbackErr.message || 'Error'), id: nextId(), isError: true }])
      }
    } finally {
      setLoading(false)
      setLiveSteps([])
      setLiveThought('')
    }
  }

  const sendMessage = (msg) => {
    const message = msg || input
    if (!message.trim() || loading) return
    setInput('')
    sendStream(message, attachedFile)
  }

  const handlePdfPassword = (password) => {
    const { file, message } = pdfPasswordModal
    setPdfPasswordModal({ open: false, file: null, message: '', error: '' })
    if (file && message) sendStream(message, file, password)
  }

  const handleConfirmAction = async (actionId) => {
    if (confirmLoading) return
    setConfirmLoading(actionId)
    try {
      const { data } = await client.post(`/ai/actions/${actionId}/confirm`)
      const t = data.result ? `**✅ ${data.summary || 'Action'} completed**\n\`\`\`json\n${JSON.stringify(data.result, null, 2)}\n\`\`\`` : `**✅ ${data.summary || 'Action'} completed**`
      setMessages(prev => prev.map(m => m.pendingActionId === actionId ? { ...m, pendingActionId: null, isConfirmed: true } : m))
      setMessages(prev => [...prev, { user: false, content: t, id: nextId(), isConfirmed: true }])
      toast.success(data.summary || 'Action completed')
    } catch (err) { toast.error(err.response?.data?.message || err.message) }
    setConfirmLoading(null)
  }

  const handleRejectAction = async (actionId) => {
    if (confirmLoading) return
    setConfirmLoading(actionId)
    try {
      await client.post(`/ai/actions/${actionId}/reject`)
      setMessages(prev => prev.map(m => m.pendingActionId === actionId ? { ...m, pendingActionId: null, isConfirmed: true } : m))
      setMessages(prev => [...prev, { user: false, content: '**⛔ Action rejected**', id: nextId(), isConfirmed: true }])
      toast.info('Action rejected')
    } catch (err) { toast.error(err.response?.data?.message || err.message) }
    setConfirmLoading(null)
  }

  const loadSessions = async () => {
    try { const { data } = await client.get('/ai/sessions'); setSessions(data.sessions || []) } catch {}
  }

  const loadSession = async (id) => {
    if (loading) return
    try {
      const { data } = await client.get(`/ai/sessions/${id}`)
      setSessionId(data.id)
      const msgs = []; const traces = data.toolCallTraces || []; let ti = 0
      for (let i = 0; i < (data.messages || []).length; i++) {
        const m = data.messages[i]
        if (m.role === 'user') msgs.push({ user: true, content: m.content, id: nextId() })
        else if (m.role === 'assistant') msgs.push({ user: false, content: m.content, id: nextId(), toolCalls: ti < traces.length ? traces[ti++] : [] })
      }
      setMessages(msgs)
      setShowSessions(false)
    } catch { toast.error('Failed to load session') }
  }

  const deleteSession = async (id, e) => {
    e.stopPropagation()
    try { await client.delete(`/ai/sessions/${id}`); setSessions(s => s.filter(x => x.id !== id)); if (sessionId === id) { setSessionId(null); setMessages([]) }; toast.success('Deleted') } catch {}
  }

  const clearChat = () => { setSessionId(null); setMessages([]); setLoading(false); setLiveSteps([]); setLiveThought('') }
  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent?.isComposing) {
      e.preventDefault()
      sendMessage()
    }
  }

  const findTxns = (tcs) => {
    if (!tcs) return null
    for (const tc of tcs) {
      const r = tc.result
      if (r?.transactions || r?.cardIssuer) return r
      if (r?.items) for (const item of r.items) { if (item?.transactions || item?.cardIssuer) return item }
    }
    return null
  }

  const headerSessionNum = sessionId ? (sessions.findIndex(s => s.id === sessionId) + 1) || 1 : null

  return (
    <div className="flex flex-col h-[calc(100vh-8rem)] max-w-5xl mx-auto">
      <div className="flex items-center justify-between gap-3 pb-4 border-b border-[var(--border)]">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
            <Bot className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-[var(--text)]">AI Assistant</h1>
            <p className="text-xs text-[var(--text-muted)]">
              {headerSessionNum ? `Session #${headerSessionNum}` : 'Unified AI — docs, portfolio, advice'}
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button onClick={() => setShowSessions(!showSessions)} className="p-2 rounded-lg text-[var(--text-muted)] hover:text-[var(--text)] hover:bg-[var(--input-bg)] transition relative" title="History">
            <History className="w-4 h-4" />
            {sessions.length > 0 && <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-blue-500 text-white text-[8px] rounded-full flex items-center justify-center font-bold">{sessions.length}</span>}
          </button>
          {messages.length > 0 && (
            <button onClick={clearChat} className="p-2 rounded-lg text-[var(--text-muted)] hover:text-red-400 hover:bg-red-500/10 transition" title="New conversation">
              <RotateCcw className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {showSessions && (
        <div className="border-b border-[var(--border)] bg-[var(--bg-card)] p-3 max-h-40 overflow-y-auto">
          <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-2">Conversations</p>
          <div className="space-y-1">
            {sessions.length === 0 && <p className="text-xs text-[var(--text-muted)]">No saved conversations</p>}
            {sessions.map(s => (
              <div key={s.id} onClick={() => loadSession(s.id)}
                className={`flex items-center gap-2 px-2 py-1.5 rounded-lg cursor-pointer transition text-xs ${s.id === sessionId ? 'bg-blue-500/10 text-blue-400' : 'hover:bg-[var(--input-bg)]'}`}>
                <MessageSquare className="w-3 h-3 shrink-0" />
                <span className="flex-1 truncate">{s.title || 'Untitled'}</span>
                <span className="text-[10px] text-[var(--text-muted)]">{s.messageCount} msgs</span>
                <button onClick={(e) => deleteSession(s.id, e)} className="p-0.5 rounded text-[var(--text-muted)] hover:text-red-400"><Trash2 className="w-3 h-3" /></button>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto py-6 pr-2 -mr-2">
        {messages.length === 0 && liveSteps.length === 0 ? (
          <div className="h-full flex flex-col items-center justify-center px-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center mb-6">
              <Bot className="w-8 h-8 text-white" />
            </div>
            <h2 className="text-2xl font-bold mb-2 text-center">How can I help you?</h2>
            <p className="text-sm text-[var(--text-muted)] mb-8 text-center max-w-md">Upload docs, query your portfolio, or get financial advice — all in one place.</p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full max-w-2xl">
              {SUGGESTED_PROMPTS.map((p) => {
                const Icon = p.icon
                return (
                  <button key={p.title} onClick={() => sendMessage(p.prompt)}
                    className="group text-left p-4 rounded-xl border border-[var(--border)] hover:border-blue-500/30 bg-[var(--bg-card)] hover:bg-[var(--input-bg)]/50 transition-all">
                    <div className="flex items-start gap-3">
                      <div className={`p-2 rounded-lg ${p.color} group-hover:scale-110 transition-transform`}><Icon className="w-4 h-4" /></div>
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-sm mb-1">{p.title}</p>
                        <p className="text-xs text-[var(--text-muted)] line-clamp-2">{p.prompt}</p>
                      </div>
                      <ArrowRight className="w-4 h-4 text-[var(--text-muted)] opacity-0 group-hover:opacity-100 transition-opacity" />
                    </div>
                  </button>
                )
              })}
            </div>
          </div>
        ) : (
          <div className="space-y-6 px-2">
            {messages.map((msg) => (
              <div key={msg.id} className="group">
                <div className={`flex gap-3 ${msg.user ? 'flex-row-reverse' : ''}`}>
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${msg.user ? 'bg-blue-500' : msg.isError ? 'bg-red-500/20' : 'bg-gradient-to-br from-blue-500 to-purple-600'}`}>
                    {msg.user ? <User className="w-4 h-4 text-white" /> : <Bot className="w-4 h-4 text-white" />}
                  </div>
                  <div className={`flex flex-col gap-2 max-w-[85%] min-w-0 ${msg.user ? 'items-end' : 'items-start'}`}>
                    <div className={`px-4 py-2.5 rounded-2xl ${msg.user ? 'bg-blue-500 text-white rounded-tr-sm' : msg.isError ? 'bg-red-500/10 border border-red-500/30 text-[var(--text)] rounded-tl-sm' : 'bg-[var(--bg-card)] border border-[var(--border)] text-[var(--text)] rounded-tl-sm'}`}>
                      {msg.user ? (
                        <div>
                          {msg.file && <p className="text-xs opacity-70 mb-1">📎 {msg.file}</p>}
                          <p className="text-sm whitespace-pre-wrap break-words">{msg.content}</p>
                        </div>
                      ) : (
                        msg.content ? (
                          <div className="prose prose-sm prose-invert max-w-none prose-p:my-2 prose-ul:my-2 prose-li:my-0.5 prose-headings:my-3 prose-pre:my-2">
                            <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                          </div>
                        ) : null
                      )}
                    </div>
                    {!msg.user && msg.content && (
                      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button onClick={() => { navigator.clipboard.writeText(msg.content); toast.success('Copied') }} className="p-1 rounded-md text-[var(--text-muted)] hover:text-[var(--text)] hover:bg-[var(--input-bg)] transition"><Copy className="w-3 h-3" /></button>
                      </div>
                    )}
                  </div>
                </div>

                {!msg.user && msg.steps?.length > 0 && (
                  <div className="ml-11 mt-3">
                    <ThinkingSection steps={msg.steps} defaultOpen={false} />
                  </div>
                )}

                {!msg.user && msg.pendingActionId && !msg.isConfirmed && (
                  <div className="ml-11 mt-3">
                    {(() => {
                      const pendingTc = (msg.toolCalls || []).find(tc => tc.type === 'pending_approval')
                      const toolName = pendingTc?.tool || pendingTc?.name || ''
                      let summary = 'This action needs your approval.'
                      try {
                        const r = pendingTc?.result
                        if (typeof r === 'string') { const p = JSON.parse(r); summary = p.message || p.summary || summary }
                        else if (r?.message) summary = r.message
                        else if (r?.summary) summary = r.summary
                      } catch {}
                      return <PendingActionCard action={{ id: msg.pendingActionId, toolName, summary }} onConfirm={handleConfirmAction} onReject={handleRejectAction} loading={confirmLoading === msg.pendingActionId} />
                    })()}
                  </div>
                )}
              </div>
            ))}

            {loading && (
              <div className="flex gap-3">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center flex-shrink-0"><Bot className="w-4 h-4 text-white" /></div>
                <div className="flex-1 space-y-1.5">
                  {/* Ordered live steps: reasoning + tools interleaved */}
                  {liveSteps.map((step, i) => {
                    if (step.stepType === 'reasoning') {
                      return <ReasoningCard key={`ls-${i}`} step={step} isLive={true} />
                    }
                    if (step.stepType === 'tool') {
                      return <LiveToolCall key={`ls-${i}`} tc={step} isActive={step.durationMs == null} />
                    }
                    return null
                  })}
                  {/* Status indicator when no steps visible yet */}
                  {liveSteps.length === 0 && (
                    <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-2xl rounded-tl-sm px-4 py-3">
                      <div className="flex items-center gap-2 text-sm">
                        <Loader2 className="w-4 h-4 text-blue-400 animate-spin" />
                        <span className="text-[var(--text-muted)]">{liveThought || 'Processing...'}</span>
                      </div>
                    </div>
                  )}
                  {/* Status between steps */}
                  {liveSteps.length > 0 && liveSteps[liveSteps.length - 1]?.done !== false && liveSteps[liveSteps.length - 1]?.durationMs !== undefined && (
                    <div className="flex items-center gap-2 text-xs text-[var(--text-muted)] px-1">
                      <Loader2 className="w-3 h-3 text-blue-400 animate-spin" />
                      <span>{liveThought || 'Deciding next step...'}</span>
                    </div>
                  )}
                </div>
              </div>
            )}

            <div ref={bottomRef} />
          </div>
        )}
      </div>

      <div className="border-t border-[var(--border)] pt-4 pb-2">
        <div className="flex items-end gap-2">
          <button onClick={() => fileInputRef.current?.click()} className="p-3 rounded-xl text-[var(--text-muted)] hover:text-blue-400 hover:bg-blue-500/10 transition" title="Attach file">
            <Paperclip className="w-5 h-5" />
          </button>
          <input ref={fileInputRef} type="file" accept=".txt,.csv,.pdf" onChange={(e) => { const f = e.target.files?.[0]; if (f) setAttachedFile(f) }} className="hidden" />
          <div className="flex-1 relative">
            {attachedFile && (
              <div className="flex items-center gap-2 mb-2 px-3 py-1.5 rounded-lg bg-blue-500/10 border border-blue-500/20 text-xs">
                <FileText className="w-3 h-3 text-blue-400" />
                <span className="flex-1 truncate">{attachedFile.name}</span>
                <button onClick={() => setAttachedFile(null)} className="text-[var(--text-muted)] hover:text-red-400"><Trash2 className="w-3 h-3" /></button>
              </div>
            )}
            <textarea ref={textareaRef} rows={1} value={input} onChange={(e) => setInput(e.target.value)} onKeyDown={handleKeyDown}
              placeholder="Ask anything — upload docs, query portfolio, get advice..."
              disabled={loading}
              className="w-full bg-[var(--bg-card)] border border-[var(--border)] rounded-2xl pl-4 pr-14 py-3 text-[var(--text)] placeholder:text-[var(--text-muted)] focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500 resize-none transition-all min-h-[44px] max-h-[200px]" />
          </div>
          <button onClick={() => sendMessage()} disabled={loading || (!input.trim() && !attachedFile)}
            className={`p-3 rounded-xl transition-all ${(input.trim() || attachedFile) && !loading ? 'bg-blue-500 hover:bg-blue-600 text-white' : 'bg-[var(--input-bg)] text-[var(--text-muted)]'}`}>
            {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Send className="w-5 h-5" />}
          </button>
        </div>
        <p className="text-xs text-[var(--text-muted)] mt-2 text-center">AI responses may be inaccurate. Always verify financial decisions.</p>
      </div>

      <PdfPasswordModal
        open={pdfPasswordModal.open}
        onClose={() => setPdfPasswordModal({ open: false, file: null, message: '', error: '' })}
        onSubmit={handlePdfPassword}
        fileName={pdfPasswordModal.file?.name}
        error={pdfPasswordModal.error}
      />
    </div>
  )
}
