import { useState, useRef, useEffect } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { toast } from 'sonner'
import client from '../api/client'
import {
  Send, Bot, User, FileText, Sparkles, Plus, Check, Trash2,
  TrendingUp, PieChart, Shield, Lightbulb, Loader2, Copy, RotateCcw,
  MessageSquare, Wallet, ArrowRight,
} from 'lucide-react'
import { PageHeader, Badge, Card } from '../components/ui'

const SUGGESTED_PROMPTS = {
  advice: [
    {
      icon: TrendingUp,
      title: 'Portfolio Analysis',
      prompt: 'Analyze my portfolio performance and suggest improvements',
      color: 'text-green-400 bg-green-500/10',
    },
    {
      icon: PieChart,
      title: 'Asset Allocation',
      prompt: 'How well is my portfolio diversified across asset classes?',
      color: 'text-blue-400 bg-blue-500/10',
    },
    {
      icon: Shield,
      title: 'Risk Assessment',
      prompt: 'What are the biggest risks in my current portfolio?',
      color: 'text-amber-400 bg-amber-500/10',
    },
    {
      icon: Lightbulb,
      title: 'Tax Optimization',
      prompt: 'Suggest tax-saving strategies for my investments',
      color: 'text-purple-400 bg-purple-500/10',
    },
    {
      icon: Wallet,
      title: 'Emergency Fund',
      prompt: 'How much should I keep in my emergency fund?',
      color: 'text-cyan-400 bg-cyan-500/10',
    },
    {
      icon: MessageSquare,
      title: 'Beginner Advice',
      prompt: 'Give me financial advice for someone starting their career',
      color: 'text-pink-400 bg-pink-500/10',
    },
  ],
  import: [
    {
      icon: FileText,
      title: 'Bought Stock',
      prompt: 'bought 10 shares of reliance at 2800 on jan 15 2025',
      color: 'text-green-400 bg-green-500/10',
    },
    {
      icon: FileText,
      title: 'Mutual Fund SIP',
      prompt: 'invested 5000 in axis bluechip fund on 1st feb',
      color: 'text-blue-400 bg-blue-500/10',
    },
    {
      icon: FileText,
      title: 'Sold Position',
      prompt: 'sold 5 shares of tcs at 4200 on 2025-03-01',
      color: 'text-red-400 bg-red-500/10',
    },
    {
      icon: FileText,
      title: 'Multiple Transactions',
      prompt: 'bought 20 hdfc bank on march 5 at 1600, also bought 10 infosys at 1500 on march 7',
      color: 'text-amber-400 bg-amber-500/10',
    },
  ],
}

export default function AIChat() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [mode, setMode] = useState('advice')
  const [loading, setLoading] = useState(false)
  const [executing, setExecuting] = useState(null)
  const bottomRef = useRef(null)
  const textareaRef = useRef(null)
  const inputContainerRef = useRef(null)

  // Auto-resize textarea
  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 200) + 'px'
    }
  }, [input])

  // Scroll to bottom on new messages
  useEffect(() => {
    if (messages.length > 0) {
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
    }
  }, [messages, loading])

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
      const botMsg = { user: false, content: data.response, id: Date.now() + 1, mode }
      const csvLines = data.response.split('\n').filter(l => l.trim() && !l.startsWith('CSV:') && !l.startsWith('csv:') && l.split(',').length >= 5)
      if (mode === 'import' && csvLines.length > 0) {
        botMsg.csvLines = csvLines.map(l => {
          const p = l.replace(/^"|"$/g, '').split(',').map(s => s.replace(/^"|"$/g, '').trim())
          return { symbol: p[0], type: p[1], qty: p[2], price: p[3], date: p[4] }
        })
      }
      setMessages([...updated, botMsg])
    } catch (err) {
      const errorMsg = err.response?.data?.error || err.message || 'Could not reach AI server'
      setMessages([...updated, {
        user: false,
        content: `⚠️ ${errorMsg}\n\nMake sure llama-server is running on port 8082.`,
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
      if (success.length > 0) {
        toast.success(`Added ${success.length} transaction(s) to portfolio`)
      }
    } catch (err) {
      const updated = [...messages]
      updated[botIndex] = { ...botMsg, executed: true, executeResult: 'Error: ' + (err.response?.data?.message || err.message) }
      setMessages(updated)
    } finally {
      setExecuting(null)
    }
  }

  const clearChat = () => {
    if (messages.length === 0) return
    if (confirm('Clear conversation?')) {
      setMessages([])
      toast.success('Chat cleared')
    }
  }

  const copyMessage = (content) => {
    navigator.clipboard.writeText(content)
    toast.success('Copied to clipboard')
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  return (
    <div className="flex flex-col h-[calc(100vh-8rem)] max-w-5xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between gap-3 pb-4 border-b border-[var(--border)]">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center">
            <Sparkles className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-[var(--text)]">AI Assistant</h1>
            <p className="text-xs text-[var(--text-muted)]">
              {mode === 'advice' ? 'Financial advisor & portfolio analyst' : 'Convert text to transactions'}
            </p>
          </div>
        </div>

        {/* Mode toggle + Clear */}
        <div className="flex items-center gap-2">
          <div className="inline-flex p-1 bg-[var(--input-bg)] rounded-lg">
            <button
              onClick={() => { setMode('advice'); setMessages([]) }}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition-all ${
                mode === 'advice'
                  ? 'bg-[var(--bg-card)] text-blue-400 shadow-sm'
                  : 'text-[var(--text-muted)] hover:text-[var(--text)]'
              }`}
            >
              <Sparkles className="w-3.5 h-3.5" />
              Advisor
            </button>
            <button
              onClick={() => { setMode('import'); setMessages([]) }}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition-all ${
                mode === 'import'
                  ? 'bg-[var(--bg-card)] text-blue-400 shadow-sm'
                  : 'text-[var(--text-muted)] hover:text-[var(--text)]'
              }`}
            >
              <FileText className="w-3.5 h-3.5" />
              Importer
            </button>
          </div>
          {messages.length > 0 && (
            <button
              onClick={clearChat}
              aria-label="Clear chat"
              className="p-2 rounded-lg text-[var(--text-muted)] hover:text-red-400 hover:bg-red-500/10 transition"
              title="Clear conversation"
            >
              <RotateCcw className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>

      {/* Messages Area */}
      <div className="flex-1 overflow-y-auto py-6 pr-2 -mr-2">
        {messages.length === 0 ? (
          /* Welcome State */
          <div className="h-full flex flex-col items-center justify-center px-4">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center mb-6">
              <Sparkles className="w-8 h-8 text-white" />
            </div>
            <h2 className="text-2xl font-bold mb-2 text-center">
              {mode === 'advice' ? 'How can I help you today?' : 'What transaction should I record?'}
            </h2>
            <p className="text-sm text-[var(--text-muted)] mb-8 text-center max-w-md">
              {mode === 'advice'
                ? "I have access to your portfolio data and can provide personalized financial advice."
                : "Describe your transactions in plain English and I'll add them to your portfolio."}
            </p>

            {/* Suggested Prompts Grid */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 w-full max-w-2xl">
              {SUGGESTED_PROMPTS[mode].map((prompt) => {
                const Icon = prompt.icon
                return (
                  <button
                    key={prompt.title}
                    onClick={() => sendMessage(prompt.prompt)}
                    className="group text-left p-4 rounded-xl border border-[var(--border)] hover:border-blue-500/30 bg-[var(--bg-card)] hover:bg-[var(--input-bg)]/50 transition-all"
                  >
                    <div className="flex items-start gap-3">
                      <div className={`p-2 rounded-lg ${prompt.color} group-hover:scale-110 transition-transform`}>
                        <Icon className="w-4 h-4" />
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="font-medium text-sm mb-1">{prompt.title}</p>
                        <p className="text-xs text-[var(--text-muted)] line-clamp-2">{prompt.prompt}</p>
                      </div>
                      <ArrowRight className="w-4 h-4 text-[var(--text-muted)] opacity-0 group-hover:opacity-100 transition-opacity" />
                    </div>
                  </button>
                )
              })}
            </div>
          </div>
        ) : (
          /* Messages */
          <div className="space-y-6 px-2">
            {messages.map((msg, i) => (
              <div key={msg.id || i} className="group">
                <div className={`flex gap-3 ${msg.user ? 'flex-row-reverse' : ''}`}>
                  {/* Avatar */}
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center flex-shrink-0 ${
                    msg.user
                      ? 'bg-blue-500'
                      : msg.isError
                        ? 'bg-red-500/20'
                        : 'bg-gradient-to-br from-blue-500 to-purple-600'
                  }`}>
                    {msg.user ? (
                      <User className="w-4 h-4 text-white" />
                    ) : (
                      <Sparkles className="w-4 h-4 text-white" />
                    )}
                  </div>

                  {/* Bubble */}
                  <div className={`flex flex-col gap-2 max-w-[85%] min-w-0 ${msg.user ? 'items-end' : 'items-start'}`}>
                    <div className={`px-4 py-2.5 rounded-2xl ${
                      msg.user
                        ? 'bg-blue-500 text-white rounded-tr-sm'
                        : msg.isError
                          ? 'bg-red-500/10 border border-red-500/30 text-[var(--text)] rounded-tl-sm'
                          : 'bg-[var(--bg-card)] border border-[var(--border)] text-[var(--text)] rounded-tl-sm'
                    }`}>
                      {msg.user ? (
                        <p className="text-sm whitespace-pre-wrap break-words">{msg.content}</p>
                      ) : (
                        <div className="prose prose-sm prose-invert max-w-none prose-p:my-2 prose-ul:my-2 prose-li:my-0.5 prose-headings:my-3 prose-pre:my-2">
                          <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                        </div>
                      )}
                    </div>

                    {/* Action buttons (on hover for bot messages) */}
                    {!msg.user && (
                      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
                        <button
                          onClick={() => copyMessage(msg.content)}
                          className="p-1.5 rounded-md text-[var(--text-muted)] hover:text-[var(--text)] hover:bg-[var(--input-bg)] transition"
                          title="Copy message"
                        >
                          <Copy className="w-3 h-3" />
                        </button>
                      </div>
                    )}
                  </div>
                </div>

                {/* CSV Preview (Import Mode) */}
                {!msg.user && msg.csvLines && !msg.executed && (
                  <div className="ml-11 mt-3 space-y-2">
                    <div className="bg-gradient-to-br from-green-500/5 to-emerald-500/5 border border-green-500/20 rounded-xl p-4">
                      <div className="flex items-center gap-2 mb-3">
                        <FileText className="w-4 h-4 text-green-400" />
                        <p className="text-sm font-medium text-green-400">
                          Detected {msg.csvLines.length} transaction{msg.csvLines.length > 1 ? 's' : ''}
                        </p>
                      </div>
                      <div className="space-y-2">
                        {msg.csvLines.map((t, j) => (
                          <div key={j} className="flex items-center gap-3 text-sm bg-[var(--bg)] rounded-lg px-3 py-2 border border-[var(--border)]">
                            <Badge variant={t.type === 'BUY' || t.type === 'SIP' ? 'green' : t.type === 'SELL' ? 'red' : 'blue'} size="sm">
                              {t.type}
                            </Badge>
                            <span className="font-mono font-medium flex-1">{t.symbol}</span>
                            <span className="text-[var(--text-muted)]">{t.qty} × ₹{parseFloat(t.price).toLocaleString('en-IN')}</span>
                            <span className="text-xs text-[var(--text-muted)]">{t.date}</span>
                          </div>
                        ))}
                      </div>
                      <button
                        onClick={() => confirmCsv(i)}
                        disabled={executing === i}
                        className="w-full mt-3 flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-green-500 hover:bg-green-600 text-white font-medium transition disabled:opacity-50"
                      >
                        {executing === i ? (
                          <>
                            <Loader2 className="w-4 h-4 animate-spin" />
                            Adding to portfolio...
                          </>
                        ) : (
                          <>
                            <Check className="w-4 h-4" />
                            Add to Portfolio
                          </>
                        )}
                      </button>
                    </div>
                  </div>
                )}

                {/* Execution Result */}
                {!msg.user && msg.executed && msg.executeResult && (
                  <div className="ml-11 mt-3">
                    <div className="bg-green-500/5 border border-green-500/20 rounded-xl px-4 py-3 text-sm">
                      <div className="prose prose-sm prose-invert max-w-none">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.executeResult}</ReactMarkdown>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            ))}

            {/* Typing indicator */}
            {loading && (
              <div className="flex gap-3">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center flex-shrink-0">
                  <Sparkles className="w-4 h-4 text-white" />
                </div>
                <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-2xl rounded-tl-sm px-4 py-3">
                  <div className="flex gap-1">
                    <div className="w-2 h-2 bg-[var(--text-muted)] rounded-full animate-bounce" style={{animationDelay: '0ms'}} />
                    <div className="w-2 h-2 bg-[var(--text-muted)] rounded-full animate-bounce" style={{animationDelay: '150ms'}} />
                    <div className="w-2 h-2 bg-[var(--text-muted)] rounded-full animate-bounce" style={{animationDelay: '300ms'}} />
                  </div>
                </div>
              </div>
            )}

            <div ref={bottomRef} />
          </div>
        )}
      </div>

      {/* Input Area */}
      <div ref={inputContainerRef} className="border-t border-[var(--border)] pt-4 pb-2">
        <div className="relative">
          <textarea
            ref={textareaRef}
            rows={1}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={mode === 'advice'
              ? 'Ask anything about your finances... (Shift+Enter for new line)'
              : 'Describe your transactions... (Shift+Enter for new line)'}
            disabled={loading}
            className="w-full bg-[var(--bg-card)] border border-[var(--border)] rounded-2xl pl-4 pr-14 py-3 text-[var(--text)] placeholder:text-[var(--text-muted)] focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500 resize-none transition-all min-h-[44px] max-h-[200px]"
            style={{ overflow: 'hidden' }}
          />
          <button
            onClick={() => sendMessage()}
            disabled={loading || !input.trim()}
            aria-label="Send message"
            className={`absolute right-2 bottom-2 p-2 rounded-xl transition-all ${
              input.trim() && !loading
                ? 'bg-blue-500 hover:bg-blue-600 text-white scale-100'
                : 'bg-[var(--input-bg)] text-[var(--text-muted)] scale-90'
            }`}
          >
            {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
          </button>
        </div>
        <p className="text-xs text-[var(--text-muted)] mt-2 text-center">
          AI responses may be inaccurate. Always verify financial decisions.
        </p>
      </div>
    </div>
  )
}
