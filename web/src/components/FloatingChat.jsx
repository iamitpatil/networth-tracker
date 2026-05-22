import { useState, useRef, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { toast } from 'sonner'
import client from '../api/client'
import {
  Send, Sparkles, FileText, X, Check, Minimize2, Maximize2,
  Loader2, User, RotateCcw, MessageSquare, CreditCard, Wallet,
  Building2, Briefcase, Search, Play, ArrowRight, Paperclip,
  History, Trash2, AlertTriangle, ThumbsUp, ThumbsDown, Bot,
  ExternalLink, Brain, ChevronDown, Wrench,
} from 'lucide-react'

// Tool icons/colors removed — using universal Wrench icon

let _msgId = 0
function nextId() { return ++_msgId }
function LiveToolCall({ toolCall, isActive }) {
  const [expanded, setExpanded] = useState(false)
  const toolName = toolCall.name || 'unknown'
  const elapsed = toolCall.durationMs != null ? (toolCall.durationMs / 1000).toFixed(1) + 's' : null
  const result = toolCall.result
  const isDone = toolCall.durationMs != null
  const resultStr = typeof result === 'string' ? result : JSON.stringify(result || {}, null, 2)

  return (
    <div className={`mt-1 rounded-lg border overflow-hidden text-xs transition-all ${
      isActive ? 'border-blue-500/40 bg-blue-500/5' : 'border-[var(--border)] bg-[var(--input-bg)]/50'
    }`}>
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-2 px-2.5 py-1.5 hover:bg-[var(--input-bg)] transition"
      >
        <Wrench className="w-3 h-3 text-blue-400 flex-shrink-0" />
        <span className="font-medium text-[11px] flex-1 text-left truncate">{toolName.replace(/_/g, ' ')}</span>
        {isActive && !isDone && <Loader2 className="w-3 h-3 text-blue-400 animate-spin" />}
        {isDone && <span className="text-green-400 font-mono font-bold text-[10px]">✓</span>}
        {elapsed && <span className="text-[var(--text-muted)] text-[10px]">{elapsed}</span>}
        <ArrowRight className={`w-3 h-3 text-[var(--text-muted)] transition-transform ${expanded ? 'rotate-90' : ''}`} />
      </button>
      {expanded && (
        <div className="px-2.5 pb-2 pt-0.5 space-y-1">
          <div className="text-[10px] text-[var(--text-muted)] font-mono bg-[var(--bg-card)] rounded p-1.5">
            <pre className="whitespace-pre-wrap break-all">{JSON.stringify(toolCall.arguments || {}, null, 2)}</pre>
          </div>
          {isDone && resultStr && (
            <div className="text-[10px] text-green-300 font-mono bg-[var(--bg-card)] rounded p-1.5 max-h-48 overflow-y-auto">
              <pre className="whitespace-pre-wrap break-all">{resultStr}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

// Completed tool call card (non-live)
function ToolCallCard({ toolCall, index }) {
  const [expanded, setExpanded] = useState(false)
  const toolName = toolCall.tool || toolCall.name || 'unknown'
  const duration = toolCall.durationMs ? (toolCall.durationMs / 1000).toFixed(1) + 's' : null
  const result = toolCall.result
  const isPending = toolCall.type === 'pending_approval'
  const status = result?.isError ? 'error' : isPending ? 'pending' : 'success'
  const resultStr = typeof result === 'string' ? result : JSON.stringify(result || {}, null, 2)

  return (
    <div className="mt-1 rounded-lg border border-[var(--border)] bg-[var(--input-bg)]/50 overflow-hidden text-xs">
      <button
        onClick={() => setExpanded(!expanded)}
        className="w-full flex items-center gap-2 px-2.5 py-1.5 hover:bg-[var(--input-bg)] transition"
      >
        <Wrench className="w-3 h-3 text-blue-400 flex-shrink-0" />
        <span className="font-medium text-[11px] flex-1 text-left truncate">{toolName.replace(/_/g, ' ')}</span>
        {status === 'success' && <span className="text-green-400 font-mono font-bold text-[10px]">✓</span>}
        {status === 'pending' && <span className="text-amber-400 font-mono font-bold text-[10px]">⏳</span>}
        {status === 'error' && <span className="text-red-400 font-mono font-bold text-[10px]">✗</span>}
        {duration && <span className="text-[var(--text-muted)] text-[10px]">{duration}</span>}
        <ArrowRight className={`w-3 h-3 text-[var(--text-muted)] transition-transform ${expanded ? 'rotate-90' : ''}`} />
      </button>
      {expanded && (
        <div className="px-2.5 pb-2 pt-0.5 space-y-1">
          <div className="text-[10px] text-[var(--text-muted)] font-mono bg-[var(--bg-card)] rounded p-1.5">
            <pre className="whitespace-pre-wrap break-all">{JSON.stringify(toolCall.arguments || {}, null, 2)}</pre>
          </div>
          <div className={`text-[10px] font-mono bg-[var(--bg-card)] rounded p-1.5 max-h-48 overflow-y-auto ${
            isPending ? 'text-amber-300' : 'text-green-300'
          }`}>
            <pre className="whitespace-pre-wrap break-all">{resultStr}</pre>
          </div>
        </div>
      )}
    </div>
  )
}

function PendingActionCard({ action, onConfirm, onReject, loading }) {
  const toolName = action.toolName || 'unknown'
  return (
    <div className="ml-8 mt-2 rounded-lg border border-amber-500/30 bg-amber-500/5 overflow-hidden">
      <div className="p-2.5 space-y-2">
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
          <button
            onClick={() => onConfirm(action.id)}
            disabled={loading}
            className="flex-1 flex items-center justify-center gap-1 text-[11px] px-2 py-1.5 rounded-lg bg-green-500 text-white hover:bg-green-600 transition disabled:opacity-50"
          >
            {loading ? <Loader2 className="w-3 h-3 animate-spin" /> : <ThumbsUp className="w-3 h-3" />}
            Approve
          </button>
          <button
            onClick={() => onReject(action.id)}
            disabled={loading}
            className="flex-1 flex items-center justify-center gap-1 text-[11px] px-2 py-1.5 rounded-lg bg-red-500/20 text-red-400 hover:bg-red-500/30 transition disabled:opacity-50"
          >
            <ThumbsDown className="w-3 h-3" />
            Reject
          </button>
        </div>
      </div>
    </div>
  )
}

// Transaction card — extracted from credit card bills etc.
function TransactionCard({ data }) {
  const transactions = data?.transactions || []
  const total = data?.totalAmountDue || data?.newCharges || 0
  const issuer = data?.cardIssuer || ''
  const cardType = data?.cardType || ''
  const lastFour = data?.cardLastFourDigits || ''

  if (!transactions.length) return null

  return (
    <div className="ml-8 mt-2 rounded-lg border border-purple-500/20 bg-purple-500/5 overflow-hidden">
      <div className="p-2.5 border-b border-purple-500/10">
        <div className="flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold">
              {issuer} {cardType} {lastFour ? '• ' + lastFour : ''}
            </p>
            <p className="text-[10px] text-[var(--text-muted)]">{transactions.length} transactions</p>
          </div>
          <p className="text-sm font-bold text-purple-400">₹{Number(total).toLocaleString()}</p>
        </div>
      </div>
      <div className="divide-y divide-purple-500/10 max-h-48 overflow-y-auto">
        {transactions.map((t, i) => (
          <div key={i} className="flex items-center justify-between px-2.5 py-1.5 text-[11px]">
            <div className="flex-1 min-w-0">
              <p className="truncate font-medium">{t.description || t.category || 'Transaction'}</p>
              <p className="text-[var(--text-muted)] text-[10px]">{t.category || ''} {t.date ? '• ' + t.date : ''}</p>
            </div>
            <p className={`font-mono font-medium ml-2 ${Number(t.amount) < 0 ? 'text-red-400' : 'text-green-400'}`}>
              {Number(t.amount) < 0 ? '' : '+'}₹{Math.abs(Number(t.amount)).toLocaleString()}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}

function FloatingReasoningCard({ step, isLive = false }) {
  const [expanded, setExpanded] = useState(isLive && !step.done)
  return (
    <div className={`ml-0 mt-1 rounded-lg border overflow-hidden text-xs ${
      !step.done ? 'border-purple-500/30 bg-purple-500/10' : 'border-purple-500/20 bg-purple-500/5'
    }`}>
      <button onClick={() => setExpanded(!expanded)} className="w-full flex items-center gap-2 px-2.5 py-1.5 hover:bg-[var(--input-bg)] transition">
        {!step.done && <Loader2 className="w-3 h-3 text-purple-400 animate-spin flex-shrink-0" />}
        {step.done && <Brain className="w-3 h-3 text-purple-400 flex-shrink-0" />}
        <span className="font-medium text-[11px] text-purple-400 flex-1 text-left truncate">
          Round {step.round} reasoning
        </span>
        {step.done && <span className="text-purple-400 font-bold text-[10px]">✓</span>}
        <ArrowRight className={`w-3 h-3 text-[var(--text-muted)] transition-transform ${expanded ? 'rotate-90' : ''}`} />
      </button>
      {expanded && (
        <div className="px-2.5 pb-2">
          <div className="text-[10px] text-[var(--text-muted)] leading-relaxed max-h-32 overflow-y-auto bg-[var(--bg-card)] rounded p-1.5 prose prose-xs prose-invert max-w-none prose-p:my-0.5 prose-ul:my-0.5 prose-ol:my-0.5 prose-li:my-0 prose-headings:my-0.5 prose-code:text-purple-300">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{step.content || ''}</ReactMarkdown>
            {!step.done && <span className="inline-block w-1 h-3 bg-purple-400 animate-pulse ml-0.5 align-middle" />}
          </div>
        </div>
      )}
    </div>
  )
}

function FloatingThinkingSection({ steps }) {
  const [open, setOpen] = useState(false)
  if (!steps || steps.length === 0) return null
  const toolCount = steps.filter(s => s.stepType === 'tool').length
  const reasoningCount = steps.filter(s => s.stepType === 'reasoning').length
  return (
    <div className="rounded-lg border border-[var(--border)] bg-[var(--bg-card)]/50 overflow-hidden">
      <button onClick={() => setOpen(!open)} className="w-full flex items-center gap-1.5 px-2 py-1.5 hover:bg-[var(--input-bg)] transition text-[10px]">
        <Brain className="w-3 h-3 text-purple-400 flex-shrink-0" />
        <span className="font-medium text-[var(--text-muted)] flex-1 text-left">
          Thinking{toolCount > 0 ? ` · ${toolCount} tool${toolCount > 1 ? 's' : ''}` : ''}{reasoningCount > 0 ? ` · ${reasoningCount} step${reasoningCount > 1 ? 's' : ''}` : ''}
        </span>
        <ArrowRight className={`w-3 h-3 text-[var(--text-muted)] transition-transform ${open ? 'rotate-90' : ''}`} />
      </button>
      {open && (
        <div className="px-2 pb-2 space-y-1">
          {steps.map((step, i) => {
            if (step.stepType === 'reasoning') return <FloatingReasoningCard key={`r-${i}`} step={step} />
            if (step.stepType === 'tool') return <ToolCallCard key={`t-${i}`} toolCall={step} index={i} />
            return null
          })}
        </div>
      )}
    </div>
  )
}

export default function FloatingChat() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [minimized, setMinimized] = useState(false)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [mcpTools, setMcpTools] = useState([])
  const [sessionId, setSessionId] = useState(null)
  const [sessions, setSessions] = useState([])
  const [showSessions, setShowSessions] = useState(false)
  const [sessionsLoading, setSessionsLoading] = useState(false)
  const [confirmLoading, setConfirmLoading] = useState(null)
  const [attachedFile, setAttachedFile] = useState(null)
  const [liveSteps, setLiveSteps] = useState([])
  const [liveThought, setLiveThought] = useState(null)
  const bottomRef = useRef(null)
  const textareaRef = useRef(null)
  const fileInputRef = useRef(null)

  const loadSessions = useCallback(async () => {
    setSessionsLoading(true)
    try {
      const { data } = await client.get('/ai/sessions')
      setSessions(data.sessions || [])
    } catch {}
    setSessionsLoading(false)
  }, [])

  const loadSession = useCallback(async (id) => {
    try {
      const { data } = await client.get(`/ai/sessions/${id}`)
      setSessionId(data.id)
      const msgs = []
      const traces = data.toolCallTraces || []
      let traceIdx = 0
      for (let i = 0; i < (data.messages || []).length; i++) {
        const m = data.messages[i]
        if (m.role === 'user') {
          msgs.push({ user: true, content: m.content, id: nextId() })
        } else if (m.role === 'assistant') {
          msgs.push({
            user: false,
            content: m.content,
            id: nextId(),
            toolCalls: traceIdx < traces.length ? traces[traceIdx++] : [],
          })
        }
      }
      setMessages(msgs)
      setShowSessions(false)
    } catch { toast.error('Failed to load session') }
  }, [])

  const deleteSession = useCallback(async (id, e) => {
    e.stopPropagation()
    try {
      await client.delete(`/ai/sessions/${id}`)
      setSessions(s => s.filter(s => s.id !== id))
      if (sessionId === id) { setSessionId(null); setMessages([]) }
      toast.success('Session deleted')
    } catch { toast.error('Failed to delete session') }
  }, [sessionId])

  const startNewSession = useCallback(() => {
    setSessionId(null)
    setMessages([])
    setShowSessions(false)
  }, [])

  useEffect(() => {
    if (!open) return
    client.get('/mcp/tools').then(({ data }) => setMcpTools(data)).catch(() => {})
    loadSessions()
  }, [open, loadSessions])

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 120) + 'px'
    }
  }, [input])

  useEffect(() => {
    setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
  }, [messages, loading, liveSteps])

  useEffect(() => {
    if (!open) return
    const handler = (e) => { if (e.key === 'Escape') setOpen(false) }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [open])

  // SSE streaming send
  const sendStreamMessage = async (text, file) => {
    const userMsg = { user: true, content: text, id: nextId(), file: file?.name }
    setMessages(prev => [...prev, userMsg])
    setLoading(true)
    setLiveSteps([])
    setLiveThought(null)
    setAttachedFile(null)

    try {
      const body = file
        ? new FormData()
        : { message: text, sessionId }

      if (file) {
        body.append('file', file)
        body.append('message', text)
        if (sessionId) body.append('sessionId', sessionId)
      }

      const headers = {}
      if (!file) {
        headers['Content-Type'] = 'application/json'
      }
      const token = localStorage.getItem('accessToken') || ''
      if (token) headers['Authorization'] = `Bearer ${token}`

      const url = file ? '/api/v1/ai/chat-upload-stream' : '/api/v1/ai/chat-stream'
      const fetchBody = file ? body : JSON.stringify(body)
      const resp = await fetch(url, { method: 'POST', headers, body: fetchBody })
      if (!resp.ok) throw new Error('SSE connection failed: ' + resp.status)
      if (!resp.body) throw new Error('Response body is not a stream')

      const botMsgId = nextId()
      let botMsg = { user: false, content: '', id: botMsgId, toolCalls: [], steps: [], pendingActionId: null, pendingToolName: null }
      let accTools = []
      let accSteps = []

      const reader = resp.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      let currentEvent = ''
      let pendingData = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          const trimmed = line.trim()
          if (!trimmed) { currentEvent = ''; pendingData = ''; continue }

          if (trimmed.startsWith('event:')) {
            currentEvent = trimmed.slice(6).trim()
            continue
          }

          if (trimmed.startsWith('data:')) {
            pendingData += trimmed.slice(5)
            try {
              const data = JSON.parse(pendingData)
              pendingData = ''
              if (currentEvent === 'thought') {
                setLiveThought(data.content || 'Thinking...')
              } else if (currentEvent === 'tool_start') {
                setLiveThought('')
                const toolStep = { stepType: 'tool', name: data.name, arguments: data.arguments }
                accTools = [...accTools, { name: data.name, arguments: data.arguments }]
                accSteps = [...accSteps, toolStep]
                setLiveSteps([...accSteps])
              } else if (currentEvent === 'tool_end') {
                accTools = accTools.map(x => x.name === data.name && !x.durationMs ? { ...x, durationMs: data.durationMs, result: data.result, type: data.type } : x)
                for (let i = accSteps.length - 1; i >= 0; i--) {
                  if (accSteps[i].stepType === 'tool' && accSteps[i].name === data.name && !accSteps[i].durationMs) {
                    accSteps[i] = { ...accSteps[i], durationMs: data.durationMs, result: data.result, type: data.type }
                    break
                  }
                }
                setLiveSteps([...accSteps])
                if (data.type === 'pending_approval') botMsg.pendingToolName = data.name
              } else if (currentEvent === 'reasoning') {
                accSteps = [...accSteps, { stepType: 'reasoning', round: data.round, content: data.content, done: true }]
                setLiveSteps([...accSteps])
                setLiveThought('Reasoning...')
              } else if (currentEvent === 'reasoning_delta') {
                const existing = accSteps.find(s => s.stepType === 'reasoning' && s.round === data.round && !s.done)
                if (existing) {
                  existing.content += data.token
                } else {
                  accSteps = [...accSteps, { stepType: 'reasoning', round: data.round, content: data.token, done: false }]
                }
                setLiveSteps([...accSteps])
                setLiveThought('Reasoning...')
              } else if (currentEvent === 'reasoning_done') {
                const existing = accSteps.find(s => s.stepType === 'reasoning' && s.round === data.round)
                if (existing) existing.done = true
                setLiveSteps([...accSteps])
                setLiveThought('Thinking...')
              } else if (currentEvent === 'response') {
                botMsg.content = data.content
                botMsg.sessionId = data.sessionId || sessionId
                if (data.pendingActionId) botMsg.pendingActionId = data.pendingActionId
                if (data.sessionId) setSessionId(data.sessionId)
                botMsg.toolCalls = [...accTools]
                botMsg.steps = [...accSteps]
                setMessages(prev => [...prev, { ...botMsg }])
                setLoading(false)
                setLiveSteps([])
                setLiveThought(null)
              } else if (currentEvent === 'error') {
                botMsg.content = '⚠️ ' + (data.message || 'Error')
                botMsg.isError = true
                botMsg.steps = [...accSteps]
                botMsg.toolCalls = [...accTools]
                setMessages(prev => [...prev, { ...botMsg }])
                setLoading(false)
                setLiveSteps([])
                setLiveThought(null)
              }
              currentEvent = ''
            } catch {
              // JSON incomplete — keep pendingData for next chunk
            }
          }
        }
      }

      // If no response/error event (stream closed unexpectedly), add fallback
      if (!botMsg.content && accTools.length > 0) {
        botMsg.toolCalls = accTools
        botMsg.steps = accSteps
        botMsg.content = 'Processing completed.'
        setMessages(prev => [...prev, { ...botMsg }])
      }
      loadSessions()
    } catch (err) {
      setMessages(prev => [...prev, {
        user: false, content: '⚠️ ' + (err.response?.data?.error || err.message || 'Could not reach AI server'),
        id: nextId(), isError: true,
      }])
    } finally {
      setLoading(false)
      setLiveSteps([])
      setLiveThought(null)
    }
  }

  const handleConfirmAction = async (actionId) => {
    setConfirmLoading(actionId)
    try {
      const { data } = await client.post(`/ai/actions/${actionId}/confirm`)
      const resultText = data.result
        ? `**✅ ${data.summary || 'Action'} completed**\n\`\`\`json\n${JSON.stringify(data.result, null, 2)}\n\`\`\``
        : `**✅ ${data.summary || 'Action'} completed**`
      setMessages(prev => prev.map(m => m.pendingActionId === actionId ? { ...m, pendingActionId: null, isConfirmed: true } : m))
      setMessages(prev => [...prev, { user: false, content: resultText, id: Date.now(), isConfirmed: true }])
      toast.success(data.summary || 'Action completed')
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Failed to execute action')
    }
    setConfirmLoading(null)
  }

  const handleRejectAction = async (actionId) => {
    setConfirmLoading(actionId)
    try {
      await client.post(`/ai/actions/${actionId}/reject`)
      setMessages(prev => prev.map(m => m.pendingActionId === actionId ? { ...m, pendingActionId: null, isConfirmed: true } : m))
      setMessages(prev => [...prev, { user: false, content: '**⛔ Action rejected**', id: Date.now(), isConfirmed: true }])
      toast.info('Action rejected')
    } catch (err) {
      toast.error(err.response?.data?.message || err.message || 'Failed to reject action')
    }
    setConfirmLoading(null)
  }

  const clearChat = () => { setSessionId(null); setMessages([]); setLoading(false); setLiveSteps([]); setLiveThought(null) }

  const sendMessage = (msg) => {
    const message = msg || input
    if (!message.trim() || loading) return
    setInput('')
    sendStreamMessage(message, attachedFile)
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent?.isComposing) { e.preventDefault(); sendMessage() }
  }

  const handleFileSelect = (e) => {
    const file = e.target.files?.[0]
    if (file) setAttachedFile(file)
  }

  const handleDrop = (e) => {
    e.preventDefault()
    const file = e.dataTransfer.files?.[0]
    if (file && (file.type.includes('text') || file.name.endsWith('.pdf') || file.name.endsWith('.csv') || file.name.endsWith('.txt'))) {
      setAttachedFile(file)
    }
  }

  const handleDragOver = (e) => e.preventDefault()

  // Extract transaction data from tool results
  const findTransactionData = (toolCalls) => {
    if (!toolCalls) return null
    for (const tc of toolCalls) {
      const result = tc.result
      if (result?.transactions || result?.cardIssuer) return result
      if (result?.items) {
        for (const item of result.items) {
          if (item?.transactions || item?.cardIssuer) return item
        }
      }
    }
    return null
  }

  return (
    <>
      <button
        onClick={() => { setOpen(!open); setMinimized(false) }}
        className={`fixed bottom-6 right-6 z-40 w-14 h-14 rounded-full shadow-lg flex items-center justify-center transition-all hover:scale-105 ${
          open ? 'bg-red-500 hover:bg-red-600 rotate-90' : 'bg-gradient-to-br from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700'
        }`}
        style={{ boxShadow: open ? '0 10px 30px rgba(239, 68, 68, 0.4)' : '0 10px 30px rgba(59, 130, 246, 0.4)' }}
      >
        {open ? <X className="w-6 h-6 text-white" /> : <Sparkles className="w-6 h-6 text-white" />}
        {!open && messages.filter(m => !m.user).length > 0 && (
          <span className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-medium">
            {messages.filter(m => !m.user).length}
          </span>
        )}
      </button>

      {open && (
        <div
          className={`fixed z-40 bg-[var(--bg-card)] border border-[var(--border)] rounded-2xl shadow-2xl flex flex-col overflow-hidden transition-all animate-in zoom-in-95 fade-in duration-200 ${
            minimized
              ? 'bottom-24 right-6 w-80 h-14'
              : 'bottom-24 right-6 w-[calc(100vw-3rem)] sm:w-[420px] h-[600px] max-h-[calc(100vh-8rem)]'
          }`}
          style={{ boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.4)' }}
        >
          {/* Header */}
          <div className="flex items-center justify-between gap-2 px-4 py-3 border-b border-[var(--border)] bg-gradient-to-r from-blue-500/5 to-purple-500/5 flex-shrink-0">
            <div className="flex items-center gap-2 min-w-0 flex-1">
              <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center flex-shrink-0">
                <Bot className="w-4 h-4 text-white" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="font-semibold text-sm truncate">
                  {sessionId ? `Session ${sessions.findIndex(s => s.id === sessionId) + 1}` : 'AI Assistant'}
                </p>
                <p className="text-[10px] text-[var(--text-muted)]">
                  Unified AI — documents, portfolio, advice
                </p>
              </div>
            </div>

            {!minimized && (
              <button
                onClick={() => setShowSessions(!showSessions)}
                className="p-1.5 rounded-md text-[var(--text-muted)] hover:text-[var(--text)] hover:bg-[var(--input-bg)] transition relative"
                title="Conversations"
              >
                <History className="w-3.5 h-3.5" />
                {sessions.length > 0 && (
                  <span className="absolute -top-0.5 -right-0.5 w-3.5 h-3.5 bg-blue-500 text-white text-[8px] rounded-full flex items-center justify-center font-bold">
                    {sessions.length}
                  </span>
                )}
              </button>
            )}

            <div className="flex items-center gap-0.5 flex-shrink-0">
              {messages.length > 0 && !minimized && (
                <button onClick={clearChat} className="p-1.5 rounded-md text-[var(--text-muted)] hover:text-red-400 hover:bg-red-500/10 transition" title="New conversation">
                  <RotateCcw className="w-3.5 h-3.5" />
                </button>
              )}
              {!minimized && (
                <button onClick={() => { setOpen(false); navigate('/ai-chat') }} className="p-1.5 rounded-md text-[var(--text-muted)] hover:text-blue-400 hover:bg-blue-500/10 transition" title="Open full view">
                  <ExternalLink className="w-3.5 h-3.5" />
                </button>
              )}
              <button onClick={() => setMinimized(!minimized)} className="p-1.5 rounded-md text-[var(--text-muted)] hover:text-[var(--text)] hover:bg-[var(--input-bg)] transition">
                {minimized ? <Maximize2 className="w-3.5 h-3.5" /> : <Minimize2 className="w-3.5 h-3.5" />}
              </button>
              <button onClick={() => setOpen(false)} className="p-1.5 rounded-md text-[var(--text-muted)] hover:text-red-400 hover:bg-red-500/10 transition">
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          {!minimized && (
            <>
              {showSessions && (
                <div className="border-b border-[var(--border)] bg-[var(--bg-card)] p-3 max-h-40 overflow-y-auto flex-shrink-0">
                  <div className="flex items-center justify-between mb-2">
                    <p className="text-xs font-semibold text-[var(--text-secondary)] uppercase tracking-wider">Conversations</p>
                    <button onClick={startNewSession} className="text-[11px] text-blue-400 hover:text-blue-300 transition font-medium">+ New</button>
                  </div>
                  {sessions.length === 0 && <p className="text-[11px] text-[var(--text-muted)]">{sessionsLoading ? 'Loading...' : 'No saved conversations'}</p>}
                  <div className="space-y-1">
                    {sessions.map((s) => (
                      <div key={s.id} onClick={() => loadSession(s.id)}
                        className={`flex items-center gap-2 px-2 py-1.5 rounded-lg cursor-pointer transition text-xs ${s.id === sessionId ? 'bg-blue-500/10 text-blue-400' : 'hover:bg-[var(--input-bg)] text-[var(--text)]'}`}
                      >
                        <MessageSquare className="w-3 h-3 shrink-0" />
                        <span className="flex-1 truncate">{s.title || 'Untitled'}</span>
                        <span className="text-[10px] text-[var(--text-muted)]">{s.messageCount} msgs</span>
                        <button onClick={(e) => deleteSession(s.id, e)} className="p-0.5 rounded text-[var(--text-muted)] hover:text-red-400 hover:bg-red-500/10 transition">
                          <Trash2 className="w-3 h-3" />
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div className="flex-1 overflow-y-auto p-3 space-y-3" onDrop={handleDrop} onDragOver={handleDragOver}>
                {messages.length === 0 && liveSteps.length === 0 ? (
                  <div className="flex flex-col items-center justify-center h-full px-4 py-8 text-center">
                    <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center mb-3">
                      <Bot className="w-6 h-6 text-white" />
                    </div>
                    <p className="font-semibold text-sm mb-1">How can I help you?</p>
                    <p className="text-xs text-[var(--text-muted)] mb-4">
                      Upload a document, ask about your portfolio, or get financial advice
                    </p>
                    <div className="space-y-2 w-full">
                      {['What stocks do I hold?', 'Here is my credit card bill', 'Record salary Rs 75,000 from Acme Corp'].map((p) => (
                        <button key={p} onClick={() => sendMessage(p)}
                          className="w-full text-left text-xs px-3 py-2 rounded-lg border border-[var(--border)] hover:border-blue-500/30 hover:bg-[var(--input-bg)]/50 transition"
                        >{p}</button>
                      ))}
                    </div>
                  </div>
                ) : (
                  messages.map((msg, i) => (
                    <div key={msg.id || i}>
                      <div className={`flex gap-2 ${msg.user ? 'flex-row-reverse' : ''}`}>
                        <div className={`w-6 h-6 rounded-md flex items-center justify-center flex-shrink-0 ${
                          msg.user ? 'bg-blue-500' : msg.isError ? 'bg-red-500/20' : 'bg-gradient-to-br from-blue-500 to-purple-600'
                        }`}>
                          {msg.user ? <User className="w-3 h-3 text-white" /> : <Bot className="w-3 h-3 text-white" />}
                        </div>
                        <div className={`max-w-[80%] rounded-xl px-3 py-2 text-sm ${
                          msg.user ? 'bg-blue-500 text-white rounded-tr-sm' :
                          msg.isError ? 'bg-red-500/10 border border-red-500/30 text-[var(--text)] rounded-tl-sm' :
                          'bg-[var(--input-bg)] text-[var(--text)] rounded-tl-sm'
                        }`}>
                          {msg.user ? (
                            <div>
                              {msg.file && <p className="text-[10px] opacity-70 mb-0.5">📎 {msg.file}</p>}
                              <p className="whitespace-pre-wrap text-xs break-words">{msg.content}</p>
                            </div>
                          ) : (
                            <div className="prose prose-xs prose-invert max-w-none text-xs prose-p:my-1 prose-ul:my-1 prose-li:my-0">
                              <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                            </div>
                          )}
                        </div>
                      </div>

                      {!msg.user && msg.steps?.length > 0 && (
                        <div className="ml-8 mt-1.5">
                          <FloatingThinkingSection steps={msg.steps} />
                        </div>
                      )}

                      {!msg.user && msg.pendingActionId && !msg.isConfirmed && (
                        <PendingActionCard
                          action={{
                            id: msg.pendingActionId,
                            toolName: msg.pendingToolName || (msg.toolCalls || []).find(tc => tc.type === 'pending_approval')?.name || '',
                            summary: 'This action needs your approval.',
                          }}
                          onConfirm={handleConfirmAction}
                          onReject={handleRejectAction}
                          loading={confirmLoading === msg.pendingActionId}
                        />
                      )}
                    </div>
                  ))
                )}

                {/* Live steps: ordered reasoning + tools */}
                {loading && (
                  <div className="space-y-1">
                    <div className="flex gap-2 mb-1">
                      <div className="w-6 h-6 rounded-md bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center flex-shrink-0">
                        <Bot className="w-3 h-3 text-white" />
                      </div>
                      {liveSteps.length === 0 && (
                        <div className="bg-[var(--input-bg)] rounded-xl rounded-tl-sm px-3 py-2">
                          <div className="flex items-center gap-2 text-xs">
                            <Loader2 className="w-3 h-3 text-blue-400 animate-spin" />
                            <span className="text-[var(--text-muted)]">{liveThought || 'Processing...'}</span>
                          </div>
                        </div>
                      )}
                    </div>
                    <div className="ml-8 space-y-1">
                      {liveSteps.map((step, i) => {
                        if (step.stepType === 'reasoning') {
                          return <FloatingReasoningCard key={`ls-${i}`} step={step} isLive={true} />
                        }
                        if (step.stepType === 'tool') {
                          return <LiveToolCall key={`ls-${i}`} toolCall={step} isActive={step.durationMs == null} />
                        }
                        return null
                      })}
                    </div>
                    {liveSteps.length > 0 && liveSteps.every(s => s.done !== false && s.durationMs !== undefined) && (
                      <div className="ml-8 flex items-center gap-2 text-[10px] text-[var(--text-muted)]">
                        <Loader2 className="w-2.5 h-2.5 text-blue-400 animate-spin" />
                        <span>{liveThought || 'Deciding next step...'}</span>
                      </div>
                    )}
                  </div>
                )}

                <div ref={bottomRef} />
              </div>

              {/* Input */}
              <div className="border-t border-[var(--border)] p-3 flex-shrink-0">
                <div className="relative">
                  {attachedFile && (
                    <div className="flex items-center gap-2 mb-1.5 px-2 py-1 rounded-lg bg-blue-500/10 border border-blue-500/20 text-xs">
                      <FileText className="w-3 h-3 text-blue-400" />
                      <span className="flex-1 truncate">{attachedFile.name}</span>
                      <button onClick={() => setAttachedFile(null)} className="text-[var(--text-muted)] hover:text-red-400 transition">
                        <X className="w-3 h-3" />
                      </button>
                    </div>
                  )}
                  <div className="flex gap-1.5 items-end">
                    <button
                      onClick={() => fileInputRef.current?.click()}
                      className="p-2 rounded-lg text-[var(--text-muted)] hover:text-blue-400 hover:bg-blue-500/10 transition"
                      title="Attach file"
                    >
                      <Paperclip className="w-4 h-4" />
                    </button>
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept=".txt,.csv,.pdf"
                      onChange={handleFileSelect}
                      className="hidden"
                    />
                    <textarea
                      ref={textareaRef}
                      rows={1}
                      value={input}
                      onChange={(e) => setInput(e.target.value)}
                      onKeyDown={handleKeyDown}
                      placeholder="Ask anything — upload docs, query portfolio, get advice..."
                      disabled={loading}
                      className="flex-1 bg-[var(--input-bg)] border border-[var(--border)] rounded-xl px-3 py-2 text-sm text-[var(--text)] placeholder:text-[var(--text-muted)] focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500 resize-none transition-all min-h-[36px] max-h-[120px]"
                      style={{ overflow: 'hidden' }}
                    />
                    <button
                      onClick={() => sendMessage()}
                      disabled={loading || (!input.trim() && !attachedFile)}
                      className={`p-2 rounded-lg transition-all ${
                        (input.trim() || attachedFile) && !loading
                          ? 'bg-blue-500 hover:bg-blue-600 text-white'
                          : 'bg-[var(--input-bg)] text-[var(--text-muted)]'
                      }`}
                    >
                      {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              </div>
            </>
          )}
        </div>
      )}
    </>
  )
}
