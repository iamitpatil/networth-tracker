import { useState, useEffect } from 'react'
import { toast } from 'sonner'
import client from '../api/client'
import { Link2, Unlink, RefreshCw, Loader2, ExternalLink } from 'lucide-react'
import { ConfirmDialog } from './ui/Modal'

export default function ZerodhaSync({ onSyncComplete }) {
  const [status, setStatus] = useState(null)
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const [confirmDialog, setConfirmDialog] = useState({ open: false, title: '', description: '', onConfirm: null })

  useEffect(() => {
    client.get('/brokers/zerodha/status')
      .then(res => setStatus(res.data))
      .catch(() => setStatus({ connected: false }))
      .finally(() => setLoading(false))
  }, [])

  // Handle Kite Connect OAuth callback (redirected back with request_token)
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const requestToken = params.get('request_token')
    const kiteStatus = params.get('status')
    if (requestToken && kiteStatus === 'success') {
      setConnecting(true)
      // Clean URL
      window.history.replaceState({}, '', window.location.pathname)
      client.post('/brokers/zerodha/callback', { request_token: requestToken })
        .then(res => {
          if (res.data?.success) {
            toast.success('Zerodha connected', { description: res.data.brokerUserName || '' })
            setStatus({ connected: true, status: 'ACTIVE', brokerUserName: res.data.brokerUserName })
          } else {
            toast.error('Connection failed', { description: res.data?.message })
          }
        })
        .catch(err => toast.error('Connection failed', { description: err.message }))
        .finally(() => setConnecting(false))
    }
  }, [])

  const handleConnect = async () => {
    try {
      const { data } = await client.get('/brokers/zerodha/auth-url')
      window.location.href = data.url
    } catch (err) {
      toast.error('Failed to get auth URL', { description: err.message })
    }
  }

  const handleSync = async () => {
    setSyncing(true)
    try {
      const { data } = await client.post('/brokers/zerodha/sync')
      if (data.success) {
        toast.success('Holdings synced', { description: data.message })
        setStatus(prev => ({ ...prev, lastSyncedAt: new Date().toISOString() }))
        onSyncComplete?.()
      } else {
        toast.error('Sync failed', { description: data.message })
        if (data.message?.includes('expired') || data.message?.includes('reconnect')) {
          setStatus(prev => ({ ...prev, status: 'TOKEN_EXPIRED' }))
        }
      }
    } catch (err) {
      toast.error('Sync failed', { description: err.message })
    } finally {
      setSyncing(false)
    }
  }

  const handleDisconnect = () => {
    setConfirmDialog({
      open: true,
      title: 'Disconnect Zerodha?',
      description: 'Your imported holdings will remain.',
      onConfirm: async () => {
        try {
          await client.post('/brokers/zerodha/disconnect')
          setStatus({ connected: false })
          toast.success('Zerodha disconnected')
        } catch (err) {
          toast.error('Failed to disconnect', { description: err.message })
        }
      },
    })
  }

  if (loading) return null

  const isConnected = status?.connected && status?.status === 'ACTIVE'
  const isExpired = status?.connected && status?.status === 'TOKEN_EXPIRED'

  return (
    <div className="bg-[var(--bg-card)] rounded-xl p-4 border border-[var(--border)]">
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-lg ${isConnected ? 'bg-green-500/20' : isExpired ? 'bg-amber-500/20' : 'bg-[var(--hover-bg)]'}`}>
            <Link2 className={`w-5 h-5 ${isConnected ? 'text-green-400' : isExpired ? 'text-amber-400' : 'text-[var(--text-muted)]'}`} />
          </div>
          <div>
            <p className="text-sm font-medium">
              {isConnected ? 'Zerodha Connected' : isExpired ? 'Zerodha Session Expired' : 'Import from Zerodha'}
            </p>
            <p className="text-xs text-[var(--text-muted)]">
              {isConnected && status.brokerUserName && `${status.brokerUserName} · `}
              {isConnected && status.lastSyncedAt && `Last synced ${new Date(status.lastSyncedAt).toLocaleDateString('en-IN')}`}
              {isExpired && 'Please reconnect to sync holdings'}
              {!status?.connected && 'Connect your Zerodha account to auto-import holdings'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {isConnected && (
            <>
              <button
                onClick={handleSync}
                disabled={syncing}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm bg-blue-500/20 text-blue-400 hover:bg-blue-500/30 transition disabled:opacity-50"
              >
                {syncing ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
                {syncing ? 'Syncing...' : 'Sync'}
              </button>
              <button
                onClick={handleDisconnect}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm text-[var(--text-muted)] hover:text-red-400 hover:bg-red-500/10 transition"
              >
                <Unlink className="w-3.5 h-3.5" />
              </button>
            </>
          )}
          {(isExpired || !status?.connected) && (
            <button
              onClick={handleConnect}
              disabled={connecting}
              className="flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-sm bg-blue-500 hover:bg-blue-600 text-white transition disabled:opacity-50"
            >
              {connecting ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <ExternalLink className="w-3.5 h-3.5" />}
              {connecting ? 'Connecting...' : isExpired ? 'Reconnect' : 'Connect Zerodha'}
            </button>
          )}
        </div>
      </div>

      <ConfirmDialog
        open={confirmDialog.open}
        onClose={() => setConfirmDialog(d => ({ ...d, open: false }))}
        onConfirm={confirmDialog.onConfirm}
        title={confirmDialog.title}
        description={confirmDialog.description}
      />
    </div>
  )
}
