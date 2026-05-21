import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { toast } from 'sonner'
import client from '../api/client'
import {
  Send, Sparkles, FileText, X, Check, Minimize2, Maximize2,
  Loader2, User, RotateCcw, MessageSquare, CreditCard, Wallet,
  Building2, Briefcase, Search, Play, ArrowRight,
} from 'lucide-react'

const TOOL_ICONS = {
  classify_document: FileText,
  extract_credit_card_bill: CreditCard,
  extract_salary_slip: Wallet,
  extract_bank_statement: Building2,
  extract_cas: Briefcase,
  extract_form16: FileText,
  extract_generic: FileText,
  resolve_entity: Search,
  search_holdings: Search,
  search_accounts: Search,
  search_credit_cards: Search,
  create_transaction: Play,
  update_cc_spend: CreditCard,
  update_salary: Wallet,
}

export default function FloatingChat() {
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [minimized, setMinimized] = useState(false)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [mode, setMode] = useState('advice')
  const [loading, setLoading] = useState(false)
  const [executing, setExecuting] = useState(null)
  const [mcpTools, setMcpTools] = useState([])
  const bottomRef = useRef(null)
  const textareaRef = useRef(null)

  useEffect(() => {
    if (!open) return
    client.get('/mcp/tools').then(({ data }) => setMcpTools(data)).catch(() => {})
  }, [open])

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 120) + 'px'
    }
  }, [input])

  useEffect(() => {
    if (messages.length > 0) {
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
    }
  }, [messages, loading])

  // ESC to close
  useEffect(() => {
    if (!open) return
    const handler = (e) => {
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [open])

  const sendMessage = async (msg) => {
    const message = msg || input
    if (!message.trim() || loading) return
    setInput('')

    const userMsg = { user: true, content: message, id: Date.now() }
    const updated = [...messages, userMsg]
    setMessages(updated)
    setLoading(true)

    try {
      const { data } = await client.post('/ai/chat', {
        message, mode,
        history: messages.slice(-10).map(m => ({ user: m.user, content: m.content })),
      })
      const botMsg = { user: false, content: data.response, id: Date.now() + 1 }
      const csvLines = data.response.split('\n').filter(l => l.trim() && !l.startsWith('CSV:') && !l.startsWith('csv:') && l.split(',').length >= 5)
      if (mode === 'import' && csvLines.length > 0) {
        botMsg.csvLines = csvLines.map(l => {
          const p = l.replace(/^"|"$/g, '').split(',').map(s => s.replace(/^"|"$/g, '').trim())
          return { symbol: p[0], type: p[1], qty: p[2], price: p[3], date: p[4] }
        })
      }
      setMessages([...updated, botMsg])
    } catch (err) {
      setMessages([...updated, {
        user: false,
        content: '⚠️ ' + (err.response?.data?.error || err.message || 'Could not reach AI server'),
        id: Date.now() + 1,
        isError: true,
      }])
    } finally {
      setLoading(false)
    }
  }

  const confirmCsv = async (botIndex) => {
    const botMsg = messages[botIndex]
    if (!botMsg.csvLines) return
    const csvText = botMsg.csvLines.map(l => `${l.symbol},${l.type},${l.qty},${l.price},${l.date}`).join('\n')
    setExecuting(botIndex)

    try {
      const { data } = await client.post('/ai/execute', { csv: csvText })
      const success = (data.results || []).filter(r => r.status === 'success')
      const errors = (data.results || []).filter(r => r.status === 'error')
      let resultText = ''
      if (success.length) resultText += '**Added:**\n' + success.map(r => '- ' + r.message).join('\n') + '\n'
      if (errors.length) resultText += '**Errors:**\n' + errors.map(r => '- ' + r.message).join('\n')
      const updated = [...messages]
      updated[botIndex] = { ...botMsg, executed: true, executeResult: resultText || 'No transactions processed.' }
      setMessages(updated)
      if (success.length > 0) toast.success(`Added ${success.length} transaction(s)`)
    } catch (err) {
      const updated = [...messages]
      updated[botIndex] = { ...botMsg, executed: true, executeResult: 'Error: ' + (err.response?.data?.message || err.message) }
      setMessages(updated)
    } finally {
      setExecuting(null)
    }
  }

  const clearChat = () => {
    setMessages([])
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  return (
    <>
      {/* Floating Button */}
      <button
        onClick={() => { setOpen(!open); setMinimized(false) }}
        aria-label={open ? 'Close AI chat' : 'Open AI chat'}
        className={`fixed bottom-6 right-6 z-40 w-14 h-14 rounded-full shadow-lg flex items-center justify-center transition-all hover:scale-105 ${
          open
            ? 'bg-red-500 hover:bg-red-600 rotate-90'
            : 'bg-gradient-to-br from-blue-500 to-purple-600 hover:from-blue-600 hover:to-purple-700'
        }`}
        style={{
          boxShadow: open
            ? '0 10px 30px rgba(239, 68, 68, 0.4)'
            : '0 10px 30px rgba(59, 130, 246, 0.4)'
        }}
      >
        {open ? <X className="w-6 h-6 text-white" /> : <Sparkles className="w-6 h-6 text-white" />}
        {!open && messages.length > 0 && (
          <span className="absolute -top-1 -right-1 w-5 h-5 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-medium">
            {messages.filter(m => !m.user).length}
          </span>
        )}
      </button>

      {/* Chat Panel */}
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
                <Sparkles className="w-4 h-4 text-white" />
              </div>
              <div className="min-w-0">
                <p className="font-semibold text-sm truncate">AI Assistant</p>
                <p className="text-[10px] text-[var(--text-muted)]">
                  {mode === 'advice' ? 'Advisor mode' : 'Import mode'}
                </p>
              </div>
            </div>

            {/* Mode toggle (only when not minimized) */}
            {!minimized && (
              <div className="inline-flex p-0.5 bg-[var(--input-bg)] rounded-md flex-shrink-0">
                <button
                  onClick={() => { setMode('advice'); setMessages([]) }}
                  className={`flex items-center gap-1 px-2 py-1 rounded text-[11px] font-medium transition-all ${
                    mode === 'advice'
                      ? 'bg-[var(--bg-card)] text-blue-400 shadow-sm'
                      : 'text-[var(--text-muted)] hover:text-[var(--text)]'
                  }`}
                >
                  <Sparkles className="w-3 h-3" />
                  Advice
                </button>
                <button
                  onClick={() => { setMode('import'); setMessages([]) }}
                  className={`flex items-center gap-1 px-2 py-1 rounded text-[11px] font-medium transition-all ${
                    mode === 'import'
                      ? 'bg-[var(--bg-card)] text-blue-400 shadow-sm'
                      : 'text-[var(--text-muted)] hover:text-[var(--text)]'
                  }`}
                >
                  <FileText className="w-3 h-3" />
                  Import
                </button>
              </div>
            )}

            {/* Window controls */}
            <div className="flex items-center gap-0.5 flex-shrink-0">
              {messages.length > 0 && !minimized && (
                <button
                  onClick={clearChat}
                  aria-label="Clear chat"
                  className="p-1.5 rounded-md text-[var(--text-muted)] hover:text-red-400 hover:bg-red-500/10 transition"
                  title="Clear conversation"
                >
                  <RotateCcw className="w-3.5 h-3.5" />
                </button>
              )}
              <button
                onClick={() => setMinimized(!minimized)}
                aria-label={minimized ? 'Maximize' : 'Minimize'}
                className="p-1.5 rounded-md text-[var(--text-muted)] hover:text-[var(--text)] hover:bg-[var(--input-bg)] transition"
                title={minimized ? 'Maximize' : 'Minimize'}
              >
                {minimized ? <Maximize2 className="w-3.5 h-3.5" /> : <Minimize2 className="w-3.5 h-3.5" />}
              </button>
              <button
                onClick={() => setOpen(false)}
                aria-label="Close chat"
                className="p-1.5 rounded-md text-[var(--text-muted)] hover:text-red-400 hover:bg-red-500/10 transition"
                title="Close (Esc)"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Body (hidden when minimized) */}
          {!minimized && (
            <>
              {/* Messages */}
              <div className="flex-1 overflow-y-auto p-3 space-y-3">
                {messages.length === 0 ? (
                  <div className="flex flex-col items-center justify-center h-full px-4 py-8 text-center">
                    <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center mb-3">
                      <Sparkles className="w-6 h-6 text-white" />
                    </div>
                    <p className="font-semibold text-sm mb-1">
                      {mode === 'advice' ? 'Ask me anything' : 'Add a transaction'}
                    </p>
                    <p className="text-xs text-[var(--text-muted)] mb-4">
                      {mode === 'advice'
                        ? 'I can analyze your portfolio and give advice'
                        : 'Describe a transaction in plain English'}
                    </p>
                    <div className="space-y-2 w-full">
                      {(mode === 'advice'
                        ? ['How is my portfolio performing?', 'Should I rebalance?']
                        : ['bought 10 reliance at 2800', 'sold 5 tcs at 4200 today']
                      ).map((p) => (
                        <button
                          key={p}
                          onClick={() => sendMessage(p)}
                          className="w-full text-left text-xs px-3 py-2 rounded-lg border border-[var(--border)] hover:border-blue-500/30 hover:bg-[var(--input-bg)]/50 transition"
                        >
                          {p}
                        </button>
                      ))}
                      {mcpTools.length > 0 && (
                        <>
                          <div className="pt-2">
                            <p className="text-[10px] font-semibold text-[var(--text-secondary)] uppercase tracking-wider mb-1.5">Quick Actions</p>
                          </div>
                          <div className="grid grid-cols-2 gap-1.5">
                            {mcpTools.slice(0, 6).map((tool) => {
                              const Icon = TOOL_ICONS[tool.name] || Sparkles
                              const label = tool.name.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
                              return (
                                <button
                                  key={tool.name}
                                  onClick={() => navigate('/smart-import')}
                                  title={tool.description}
                                  className="flex items-center gap-1.5 text-[11px] px-2.5 py-1.5 rounded-lg border border-[var(--border)] hover:border-blue-500/30 hover:bg-blue-500/5 transition"
                                >
                                  <Icon className="w-3 h-3 shrink-0 text-blue-400" />
                                  <span className="truncate">{label}</span>
                                </button>
                              )
                            })}
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                ) : (
                  messages.map((msg, i) => (
                    <div key={msg.id || i}>
                      <div className={`flex gap-2 ${msg.user ? 'flex-row-reverse' : ''}`}>
                        <div className={`w-6 h-6 rounded-md flex items-center justify-center flex-shrink-0 ${
                          msg.user ? 'bg-blue-500' :
                          msg.isError ? 'bg-red-500/20' :
                          'bg-gradient-to-br from-blue-500 to-purple-600'
                        }`}>
                          {msg.user ? (
                            <User className="w-3 h-3 text-white" />
                          ) : (
                            <Sparkles className="w-3 h-3 text-white" />
                          )}
                        </div>
                        <div className={`max-w-[80%] rounded-xl px-3 py-2 text-sm ${
                          msg.user
                            ? 'bg-blue-500 text-white rounded-tr-sm'
                            : msg.isError
                              ? 'bg-red-500/10 border border-red-500/30 text-[var(--text)] rounded-tl-sm'
                              : 'bg-[var(--input-bg)] text-[var(--text)] rounded-tl-sm'
                        }`}>
                          {msg.user ? (
                            <p className="whitespace-pre-wrap text-xs break-words">{msg.content}</p>
                          ) : (
                            <div className="prose prose-xs prose-invert max-w-none text-xs prose-p:my-1 prose-ul:my-1 prose-li:my-0">
                              <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                            </div>
                          )}
                        </div>
                      </div>

                      {!msg.user && msg.csvLines && !msg.executed && (
                        <div className="ml-8 mt-2 space-y-2">
                          <div className="bg-green-500/5 border border-green-500/20 rounded-lg p-2 text-xs">
                            <p className="font-medium text-green-400 mb-1.5">{msg.csvLines.length} transaction(s)</p>
                            {msg.csvLines.map((t, j) => (
                              <div key={j} className="flex gap-2 items-center text-[11px] py-0.5">
                                <span className={`font-mono font-medium ${t.type === 'BUY' || t.type === 'SIP' ? 'text-green-400' : t.type === 'SELL' ? 'text-red-400' : 'text-blue-400'}`}>{t.type}</span>
                                <span className="font-mono">{t.symbol}</span>
                                <span className="text-[var(--text-muted)]">{t.qty} × ₹{t.price}</span>
                              </div>
                            ))}
                          </div>
                          <button
                            onClick={() => confirmCsv(i)}
                            disabled={executing === i}
                            className="w-full flex items-center justify-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-green-500 text-white hover:bg-green-600 transition disabled:opacity-50"
                          >
                            {executing === i ? (
                              <Loader2 className="w-3 h-3 animate-spin" />
                            ) : (
                              <Check className="w-3 h-3" />
                            )}
                            Add to Portfolio
                          </button>
                        </div>
                      )}

                      {!msg.user && msg.executed && msg.executeResult && (
                        <div className="ml-8 mt-2">
                          <div className="bg-green-500/5 border border-green-500/20 rounded-lg px-2 py-1.5 text-xs">
                            <div className="prose prose-xs prose-invert max-w-none">
                              <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.executeResult}</ReactMarkdown>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  ))
                )}

                {loading && (
                  <div className="flex gap-2">
                    <div className="w-6 h-6 rounded-md bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center flex-shrink-0">
                      <Sparkles className="w-3 h-3 text-white" />
                    </div>
                    <div className="bg-[var(--input-bg)] rounded-xl rounded-tl-sm px-3 py-2">
                      <div className="flex gap-1">
                        <div className="w-1.5 h-1.5 bg-[var(--text-muted)] rounded-full animate-bounce" style={{animationDelay: '0ms'}} />
                        <div className="w-1.5 h-1.5 bg-[var(--text-muted)] rounded-full animate-bounce" style={{animationDelay: '150ms'}} />
                        <div className="w-1.5 h-1.5 bg-[var(--text-muted)] rounded-full animate-bounce" style={{animationDelay: '300ms'}} />
                      </div>
                    </div>
                  </div>
                )}

                <div ref={bottomRef} />
              </div>

              {/* Input */}
              <div className="border-t border-[var(--border)] p-3 flex-shrink-0">
                <div className="relative">
                  <textarea
                    ref={textareaRef}
                    rows={1}
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder={mode === 'advice' ? 'Ask anything...' : 'Describe transaction...'}
                    disabled={loading}
                    className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-xl pl-3 pr-10 py-2 text-sm text-[var(--text)] placeholder:text-[var(--text-muted)] focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500 resize-none transition-all min-h-[36px] max-h-[120px]"
                    style={{ overflow: 'hidden' }}
                  />
                  <button
                    onClick={() => sendMessage()}
                    disabled={loading || !input.trim()}
                    aria-label="Send"
                    className={`absolute right-1.5 bottom-1.5 p-1.5 rounded-lg transition-all ${
                      input.trim() && !loading
                        ? 'bg-blue-500 hover:bg-blue-600 text-white'
                        : 'bg-[var(--input-bg)] text-[var(--text-muted)]'
                    }`}
                  >
                    {loading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Send className="w-3.5 h-3.5" />}
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      )}
    </>
  )
}
