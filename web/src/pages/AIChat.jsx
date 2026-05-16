import { useState, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import axios from '../api/client'
import { Send, Bot, User, FileText, Sparkles, Plus, CheckCircle, XCircle, Check } from 'lucide-react'

export default function AIChat() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [mode, setMode] = useState('advice')
  const [loading, setLoading] = useState(false)
  const [executing, setExecuting] = useState(null)
  const bottomRef = useRef(null)

  const examples = {
    advice: [
      'Give me financial advice for a beginner',
      'How should I diversify my portfolio?',
      'What are tax-efficient investment options?',
    ],
    import: [
      'bought 10 shares of reliance at 2800 on jan 15 2025',
      'invested 5000 in axis bluechip fund on 1st feb',
      'sold 5 shares of tcs at 4200 on 2025-03-01',
    ],
  }

  const sendMessage = async (msg) => {
    const message = msg || input
    if (!message.trim() || loading) return
    setInput('')

    const userMsg = { user: true, content: message, id: Date.now() }
    const updated = [...messages, userMsg]
    setMessages(updated)
    setLoading(true)

    try {
      const { data } = await axios.post('/ai/chat', {
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
      setMessages([...updated, { user: false, content: 'Error: ' + (err.response?.data?.error || err.message || 'Could not reach AI server'), id: Date.now() + 1 }])
    } finally {
      setLoading(false)
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
    }
  }

  const confirmCsv = async (botIndex) => {
    const botMsg = messages[botIndex]
    if (!botMsg.csvLines) return
    const csvText = botMsg.csvLines.map(l => `${l.symbol},${l.type},${l.qty},${l.price},${l.date}`).join('\n')
    setExecuting(botIndex)

    try {
      const { data } = await axios.post('/ai/execute', { csv: csvText })
      const success = (data.results || []).filter(r => r.status === 'success')
      const errors = (data.results || []).filter(r => r.status === 'error')
      let resultText = ''
      if (success.length) resultText += '**Added:**\n' + success.map(r => '- ' + r.message).join('\n') + '\n'
      if (errors.length) resultText += '**Errors:**\n' + errors.map(r => '- ' + r.message).join('\n')
      const updated = [...messages]
      updated[botIndex] = { ...botMsg, executed: true, executeResult: resultText || 'No transactions processed.' }
      setMessages(updated)
    } catch (err) {
      const updated = [...messages]
      updated[botIndex] = { ...botMsg, executed: true, executeResult: 'Error: ' + (err.response?.data?.message || err.message) }
      setMessages(updated)
    } finally {
      setExecuting(null)
    }
  }

  return (
    <div className="max-w-4xl mx-auto">
      <div className="flex items-center gap-3 mb-6">
        <Bot className="w-8 h-8 text-blue-500" />
        <div>
          <h1 className="text-2xl font-bold text-theme">AI Chat</h1>
          <p className="text-theme-secondary text-sm">Financial advisor & transaction parser</p>
        </div>
      </div>

      <div className="flex gap-2 mb-4">
        <button
          onClick={() => { setMode('advice'); setMessages([]) }}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg transition ${mode === 'advice' ? 'bg-blue-500 text-white' : 'bg-[var(--bg-card)] text-theme border border-[var(--border)]'}`}
        >
          <Sparkles className="w-4 h-4" />
          Advisor
        </button>
        <button
          onClick={() => { setMode('import'); setMessages([]) }}
          className={`flex items-center gap-2 px-4 py-2 rounded-lg transition ${mode === 'import' ? 'bg-blue-500 text-white' : 'bg-[var(--bg-card)] text-theme border border-[var(--border)]'}`}
        >
          <FileText className="w-4 h-4" />
          Import Parser
        </button>
      </div>

      <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-xl overflow-hidden">
        <div className="h-[500px] overflow-y-auto p-4 space-y-4">
          {messages.length === 0 && (
            <div className="text-center py-12">
              <Bot className="w-12 h-12 mx-auto text-theme-secondary mb-3" />
              <p className="text-theme-secondary">
                {mode === 'advice' ? 'Ask me for financial advice or portfolio analysis' : 'Describe a transaction and I will add it to your portfolio'}
              </p>
              <div className="flex flex-wrap gap-2 justify-center mt-4">
                {examples[mode].map((ex) => (
                  <button key={ex} onClick={() => sendMessage(ex)}
                    className="text-sm px-3 py-1.5 rounded-full bg-blue-500/10 text-blue-400 hover:bg-blue-500/20 transition border border-blue-500/20"
                  >{ex}</button>
                ))}
              </div>
            </div>
          )}

          {messages.map((msg, i) => (
            <div key={msg.id || i}>
              <div className={`flex gap-3 ${msg.user ? 'justify-end' : ''}`}>
                {!msg.user && (
                  <div className="w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center shrink-0">
                    <Bot className="w-4 h-4 text-blue-400" />
                  </div>
                )}
                <div className={`max-w-[80%] rounded-xl px-4 py-2.5 ${msg.user ? 'bg-blue-500 text-white rounded-br-sm' : 'bg-gray-100 dark:bg-gray-800 text-theme rounded-bl-sm'}`}>
                  {msg.user ? (
                    <p className="text-sm whitespace-pre-wrap">{msg.content}</p>
                  ) : (
                    <div className="prose prose-sm dark:prose-invert max-w-none">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                    </div>
                  )}
                </div>
                {msg.user && (
                  <div className="w-8 h-8 rounded-full bg-blue-500 flex items-center justify-center shrink-0">
                    <User className="w-4 h-4 text-white" />
                  </div>
                )}
              </div>

              {!msg.user && msg.csvLines && !msg.executed && (
                <div className="ml-11 mt-2 space-y-2">
                  <div className="bg-green-500/5 border border-green-500/20 rounded-lg p-3">
                    <p className="text-xs font-medium text-green-500 mb-2">Transaction Preview — review before adding:</p>
                    <div className="space-y-1.5">
                      {msg.csvLines.map((t, j) => (
                        <div key={j} className="flex items-center gap-3 text-sm bg-[var(--bg)] rounded px-3 py-2">
                          <span className="font-mono font-medium text-theme w-28">{t.symbol}</span>
                          <span className={`font-medium w-16 text-center ${t.type === 'BUY' ? 'text-green-500' : t.type === 'SELL' ? 'text-red-500' : 'text-blue-500'}`}>{t.type}</span>
                          <span className="text-theme-muted">{t.qty} shares</span>
                          <span className="text-theme-muted">@ ₹{parseFloat(t.price).toLocaleString()}</span>
                          <span className="text-theme-muted ml-auto">{t.date}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                  <button
                    onClick={() => confirmCsv(i)}
                    disabled={executing === i}
                    className="flex items-center gap-1.5 text-sm px-4 py-2 rounded-lg bg-green-500 text-white hover:bg-green-600 transition disabled:opacity-50"
                  >
                    {executing === i ? (
                      <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    ) : (
                      <Check className="w-4 h-4" />
                    )}
                    Confirm & Add to Portfolio
                  </button>
                </div>
              )}

              {!msg.user && msg.executed && msg.executeResult && (
                <div className="ml-11 mt-2">
                  <div className="bg-green-500/5 border border-green-500/20 rounded-lg px-4 py-3 text-sm text-theme">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.executeResult}</ReactMarkdown>
                  </div>
                </div>
              )}
            </div>
          ))}

          {loading && (
            <div className="flex gap-3">
              <div className="w-8 h-8 rounded-full bg-blue-500/20 flex items-center justify-center">
                <Bot className="w-4 h-4 text-blue-400" />
              </div>
              <div className="bg-gray-100 dark:bg-gray-800 rounded-xl rounded-bl-sm px-4 py-2.5">
                <div className="flex gap-1">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '0ms'}} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '150ms'}} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '300ms'}} />
                </div>
              </div>
            </div>
          )}

          <div ref={bottomRef} />
        </div>

        <div className="border-t border-[var(--border)] p-4">
          <div className="flex gap-2">
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
              placeholder={mode === 'advice' ? 'Ask for financial advice...' : 'Describe a transaction...'}
              className="flex-1 bg-[var(--bg)] border border-[var(--border)] rounded-lg px-4 py-2.5 text-theme placeholder-theme-secondary focus:outline-none focus:border-blue-500"
            />
            <button
              onClick={() => sendMessage()}
              disabled={loading || !input.trim()}
              className="bg-blue-500 text-white px-4 py-2.5 rounded-lg hover:bg-blue-600 transition disabled:opacity-50"
            >
              <Send className="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
