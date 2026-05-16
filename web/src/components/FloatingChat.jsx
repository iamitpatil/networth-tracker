import { useState, useRef } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import axios from '../api/client'
import { Send, Bot, User, FileText, Sparkles, Plus, X, Check } from 'lucide-react'

export default function FloatingChat() {
  const [open, setOpen] = useState(false)
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [mode, setMode] = useState('advice')
  const [loading, setLoading] = useState(false)
  const [executing, setExecuting] = useState(null)
  const bottomRef = useRef(null)

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
    <>
      <button
        onClick={() => setOpen(!open)}
        className="fixed bottom-6 right-6 z-50 w-14 h-14 rounded-full bg-blue-500 hover:bg-blue-600 text-white shadow-lg flex items-center justify-center transition shadow-blue-500/25"
      >
        {open ? <X className="w-6 h-6" /> : <Bot className="w-6 h-6" />}
      </button>

      {open && (
        <div className="fixed bottom-24 right-6 z-50 w-96 h-[550px] bg-[var(--bg-card)] border border-[var(--border)] rounded-2xl shadow-2xl flex flex-col overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--border)] shrink-0">
            <div className="flex items-center gap-2">
              <Bot className="w-5 h-5 text-blue-500" />
              <span className="font-semibold text-sm text-theme">AI Chat</span>
            </div>
            <div className="flex gap-1">
              <button
                onClick={() => { setMode('advice'); setMessages([]) }}
                className={`text-xs px-2 py-1 rounded ${mode === 'advice' ? 'bg-blue-500 text-white' : 'text-theme-secondary hover:text-theme'}`}
              >Advisor</button>
              <button
                onClick={() => { setMode('import'); setMessages([]) }}
                className={`text-xs px-2 py-1 rounded ${mode === 'import' ? 'bg-blue-500 text-white' : 'text-theme-secondary hover:text-theme'}`}
              >Import</button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-3 space-y-3">
            {messages.length === 0 && (
              <div className="text-center py-8">
                <Bot className="w-8 h-8 mx-auto text-theme-secondary mb-2" />
                <p className="text-xs text-theme-secondary">
                  {mode === 'advice' ? 'Ask for financial advice' : 'Describe a transaction'}
                </p>
              </div>
            )}

            {messages.map((msg, i) => (
              <div key={msg.id || i}>
                <div className={`flex gap-2 ${msg.user ? 'justify-end' : ''}`}>
                  {!msg.user && (
                    <div className="w-6 h-6 rounded-full bg-blue-500/20 flex items-center justify-center shrink-0">
                      <Bot className="w-3 h-3 text-blue-400" />
                    </div>
                  )}
                  <div className={`max-w-[85%] rounded-xl px-3 py-2 text-sm ${msg.user ? 'bg-blue-500 text-white rounded-br-sm' : 'bg-gray-100 dark:bg-gray-800 text-theme rounded-bl-sm'}`}>
                    {msg.user ? (
                      <p className="whitespace-pre-wrap text-xs">{msg.content}</p>
                    ) : (
                      <div className="prose prose-xs dark:prose-invert max-w-none">
                        <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                      </div>
                    )}
                  </div>
                  {msg.user && (
                    <div className="w-6 h-6 rounded-full bg-blue-500 flex items-center justify-center shrink-0">
                      <User className="w-3 h-3 text-white" />
                    </div>
                  )}
                </div>

                {!msg.user && msg.csvLines && !msg.executed && (
                  <div className="ml-8 mt-2 space-y-2">
                    <div className="bg-green-500/5 border border-green-500/20 rounded-lg p-2 text-xs">
                      <p className="font-medium text-green-500 mb-1">Preview:</p>
                      {msg.csvLines.map((t, j) => (
                        <div key={j} className="flex gap-2 text-theme py-0.5">
                          <span className="text-green-400 font-mono">{t.symbol}</span>
                          <span className={t.type === 'BUY' ? 'text-green-400' : 'text-red-400'}>{t.type}</span>
                          <span className="text-theme-muted">{t.qty} × {t.price}</span>
                          <span className="text-theme-muted">{t.date}</span>
                        </div>
                      ))}
                    </div>
                    <button
                      onClick={() => confirmCsv(i)}
                      disabled={executing === i}
                      className="flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-lg bg-green-500 text-white hover:bg-green-600 transition disabled:opacity-50"
                    >
                      {executing === i ? (
                        <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" />
                      ) : (
                        <Check className="w-3 h-3" />
                      )}
                      Confirm & Add
                    </button>
                  </div>
                )}

                {!msg.user && msg.executed && msg.executeResult && (
                  <div className="ml-8 mt-2">
                    <div className="bg-green-500/5 border border-green-500/20 rounded-lg px-3 py-2 text-xs text-theme">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.executeResult}</ReactMarkdown>
                    </div>
                  </div>
                )}
              </div>
            ))}

            {loading && (
              <div className="flex gap-2">
                <div className="w-6 h-6 rounded-full bg-blue-500/20 flex items-center justify-center">
                  <Bot className="w-3 h-3 text-blue-400" />
                </div>
                <div className="bg-gray-100 dark:bg-gray-800 rounded-xl rounded-bl-sm px-3 py-2">
                  <div className="flex gap-1">
                    <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '0ms'}} />
                    <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '150ms'}} />
                    <div className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce" style={{animationDelay: '300ms'}} />
                  </div>
                </div>
              </div>
            )}

            <div ref={bottomRef} />
          </div>

          <div className="border-t border-[var(--border)] p-3 shrink-0">
            <div className="flex gap-2">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
                placeholder={mode === 'advice' ? 'Ask anything...' : 'Describe transaction...'}
                className="flex-1 bg-[var(--bg)] border border-[var(--border)] rounded-lg px-3 py-2 text-sm text-theme placeholder-theme-secondary focus:outline-none focus:border-blue-500"
              />
              <button
                onClick={() => sendMessage()}
                disabled={loading || !input.trim()}
                className="bg-blue-500 text-white p-2 rounded-lg hover:bg-blue-600 transition disabled:opacity-50"
              >
                <Send className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
