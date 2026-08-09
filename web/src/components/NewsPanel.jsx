import { useState, useEffect } from 'react'
import { ExternalLink, Loader2, Newspaper, X } from 'lucide-react'
import client from '../api/client'

export default function NewsPanel({ holding, onClose }) {
  const [news, setNews] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    if (!holding) return
    setLoading(true)
    setError(null)
    const symbol = holding.symbol?.replace(/\.(NS|BO)$/, '')
    client.get(`/news/search?q=${encodeURIComponent(symbol + ' stock')}&limit=10`)
      .then(res => setNews(res.data || []))
      .catch(err => setError(err.message || 'Failed to load news'))
      .finally(() => setLoading(false))
  }, [holding])
  /* eslint-enable react-hooks/set-state-in-effect */

  const timeAgo = (dateStr) => {
    const now = new Date()
    const date = new Date(dateStr)
    const diffMs = now - date
    const diffHrs = Math.floor(diffMs / 3600000)
    if (diffHrs < 1) return 'Just now'
    if (diffHrs < 24) return `${diffHrs}h ago`
    const diffDays = Math.floor(diffHrs / 24)
    if (diffDays < 7) return `${diffDays}d ago`
    return date.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' })
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={onClose}>
      <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] w-full max-w-lg mx-4 max-h-[80vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="flex items-center justify-between p-4 border-b border-[var(--border)] shrink-0">
          <div className="flex items-center gap-2">
            <Newspaper className="w-5 h-5 text-blue-400" />
            <div>
              <h3 className="text-lg font-semibold">News</h3>
              <p className="text-sm text-[var(--text-muted)]">{holding.symbol} — {holding.name}</p>
            </div>
          </div>
          <button onClick={onClose} className="text-[var(--text-secondary)] hover:text-white transition p-1">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="overflow-y-auto flex-1 p-4">
          {loading ? (
            <div className="flex justify-center py-12">
              <Loader2 className="w-6 h-6 animate-spin text-[var(--text-muted)]" />
            </div>
          ) : error ? (
            <div className="text-center py-12">
              <p className="text-sm text-red-400 mb-2">Failed to load news</p>
              <p className="text-xs text-[var(--text-muted)]">{error}</p>
            </div>
          ) : news.length === 0 ? (
            <div className="text-center py-12">
              <Newspaper className="w-12 h-12 text-[var(--text-muted)] mx-auto mb-3 opacity-50" />
              <p className="text-sm text-[var(--text-secondary)]">No news found for {holding.symbol}</p>
            </div>
          ) : (
            <div className="space-y-3">
              {news.map((item, i) => (
                <a
                  key={i}
                  href={item.link}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block p-3 rounded-lg bg-[var(--bg)]/50 hover:bg-[var(--hover-bg)] transition group"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium leading-snug group-hover:text-blue-400 transition line-clamp-2">
                        {item.title}
                      </p>
                      <div className="flex items-center gap-2 mt-1.5 text-xs text-[var(--text-muted)]">
                        <span>{item.source || 'Google News'}</span>
                        {item.pubDate && (
                          <>
                            <span>·</span>
                            <span>{timeAgo(item.pubDate)}</span>
                          </>
                        )}
                      </div>
                      {item.description && (
                        <p className="text-xs text-[var(--text-secondary)] mt-1 line-clamp-2">
                          {item.description}
                        </p>
                      )}
                    </div>
                    <ExternalLink className="w-4 h-4 text-[var(--text-muted)] shrink-0 mt-0.5 opacity-0 group-hover:opacity-100 transition" />
                  </div>
                </a>
              ))}
            </div>
          )}
        </div>

        <div className="p-3 border-t border-[var(--border)] text-center text-[10px] text-[var(--text-muted)] shrink-0">
          Powered by Google News
        </div>
      </div>
    </div>
  )
}
