import { useState, useEffect, useCallback } from 'react'
import client from '../api/client'
import { useFamilyView } from '../context/FamilyViewContext'
import { Users, Plus, X, Check, Ban, LogOut, Trash2, Mail, UserPlus, Loader2, ChevronDown, ChevronRight } from 'lucide-react'
import { ConfirmDialog } from '../components/ui/Modal'
import { PageSkeleton } from '../components/ui'

export default function Family() {
  const { fetchPending, fetchFamilies } = useFamilyView()
  const [families, setFamilies] = useState([])
  const [invitations, setInvitations] = useState([])
  const [loading, setLoading] = useState(true)
  const [showCreate, setShowCreate] = useState(false)
  const [familyName, setFamilyName] = useState('')
  const [expanded, setExpanded] = useState({})
  const [members, setMembers] = useState({})
  const [inviteEmail, setInviteEmail] = useState({})
  const [sending, setSending] = useState({})
  const [creating, setCreating] = useState(false)
  const [confirmDialog, setConfirmDialog] = useState({ open: false, title: '', description: '', onConfirm: null })

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [f, i] = await Promise.all([
        client.get('/families'),
        client.get('/families/invitations/pending'),
      ])
      setFamilies(f.data || [])
      setInvitations(i.data || [])
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }, [])

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => { load() }, [load])
  /* eslint-enable react-hooks/set-state-in-effect */

  const toggleExpand = async (familyId) => {
    if (expanded[familyId]) {
      setExpanded((p) => ({ ...p, [familyId]: false }))
      return
    }
    setExpanded((p) => ({ ...p, [familyId]: true }))
    if (!members[familyId]) {
      try {
        const { data } = await client.get(`/families/${familyId}/members`)
        setMembers((p) => ({ ...p, [familyId]: data }))
      } catch (e) { console.error(e) }
    }
  }

  const handleCreate = async (e) => {
    e.preventDefault()
    if (!familyName.trim()) return
    setCreating(true)
    try {
      await client.post('/families', { name: familyName.trim() })
      setFamilyName('')
      setShowCreate(false)
      await load()
      await fetchFamilies()
    } catch (e) { console.error(e) }
    finally { setCreating(false) }
  }

  const handleInvite = async (familyId) => {
    const email = inviteEmail[familyId]
    if (!email?.trim()) return
    setSending((p) => ({ ...p, [familyId]: true }))
    try {
      await client.post(`/families/${familyId}/invite`, { email: email.trim() })
      setInviteEmail((p) => ({ ...p, [familyId]: '' }))
      const { data } = await client.get(`/families/${familyId}/members`)
      setMembers((p) => ({ ...p, [familyId]: data }))
    } catch (e) { console.error(e) }
    finally { setSending((p) => ({ ...p, [familyId]: false })) }
  }

  const handleRespond = async (membershipId, accept) => {
    try {
      await client.post(`/families/invitations/${membershipId}/respond`, { accept })
      await load()
      await fetchPending()
    } catch (e) { console.error(e) }
  }

  const handleLeave = (familyId) => {
    setConfirmDialog({
      open: true,
      title: 'Leave this family?',
      description: 'This action cannot be undone.',
      onConfirm: async () => {
        try {
          await client.delete(`/families/${familyId}/leave`)
          setMembers((p) => { const n = { ...p }; delete n[familyId]; return n })
          await load()
          await fetchFamilies()
        } catch (e) { console.error(e) }
      },
    })
  }

  const handleDelete = (familyId) => {
    setConfirmDialog({
      open: true,
      title: 'Delete this family permanently?',
      description: 'This action cannot be undone.',
      onConfirm: async () => {
        try {
          await client.delete(`/families/${familyId}`)
          setMembers((p) => { const n = { ...p }; delete n[familyId]; return n })
          await load()
          await fetchFamilies()
        } catch (e) { console.error(e) }
      },
    })
  }

  if (loading) return <PageSkeleton />

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Family</h1>
          <p className="text-[var(--text-muted)] text-sm mt-1">Manage families and invite members to view combined finances</p>
        </div>
        <button
          onClick={() => setShowCreate(!showCreate)}
          className="px-4 py-2 rounded-lg flex items-center gap-2 transition bg-blue-500 hover:bg-blue-600"
        >
          {showCreate ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
          {showCreate ? 'Cancel' : 'Create Family'}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
          <div className="flex gap-3 items-end">
            <div className="flex-1">
              <label className="block text-sm text-[var(--text-muted)] mb-1">Family Name</label>
              <input
                type="text" value={familyName} onChange={(e) => setFamilyName(e.target.value)}
                className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5"
                placeholder="e.g., My Family" required
              />
            </div>
            <button
              type="submit" disabled={creating || !familyName.trim()}
              className="px-4 py-2.5 bg-blue-500 rounded-lg hover:bg-blue-600 transition disabled:opacity-50 flex items-center gap-2"
            >
              {creating && <Loader2 className="w-4 h-4 animate-spin" />}
              Create
            </button>
          </div>
        </form>
      )}

      {invitations.length > 0 && (
        <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
          <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
            <Mail className="w-5 h-5 text-amber-400" />
            Pending Invitations ({invitations.length})
          </h2>
          <div className="space-y-3">
            {invitations.map((inv) => {
              const family = families.find((f) => f.id === inv.familyId)
              return (
                <div key={inv.id} className="flex items-center justify-between p-3 rounded-lg bg-[var(--bg)]/50">
                  <div>
                    <p className="font-medium">{family?.name || 'Unknown Family'}</p>
                    <p className="text-sm text-[var(--text-muted)]">Invited by {inv.invitedBy ? 'a family member' : 'Unknown'}</p>
                  </div>
                  <div className="flex gap-2">
                    <button onClick={() => handleRespond(inv.id, true)}
                      className="px-3 py-1.5 bg-green-500/20 text-green-400 rounded-lg hover:bg-green-500/30 transition flex items-center gap-1 text-sm">
                      <Check className="w-4 h-4" /> Accept
                    </button>
                    <button onClick={() => handleRespond(inv.id, false)}
                      className="px-3 py-1.5 bg-red-500/20 text-red-400 rounded-lg hover:bg-red-500/30 transition flex items-center gap-1 text-sm">
                      <Ban className="w-4 h-4" /> Decline
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      )}

      {families.length === 0 && invitations.length === 0 ? (
        <div className="text-center py-16 text-[var(--text-secondary)]">
          <Users className="w-16 h-16 mx-auto mb-4 opacity-40" />
          <p className="text-lg">No families yet</p>
          <p className="text-sm mt-1">Create a family to start tracking combined finances</p>
        </div>
      ) : (
        <div className="space-y-4">
          {families.map((family) => (
            <div key={family.id} className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
              <button
                onClick={() => toggleExpand(family.id)}
                className="w-full flex items-center justify-between p-4 hover:bg-[var(--hover-bg)] transition text-left"
              >
                <div className="flex items-center gap-3">
                  <Users className="w-5 h-5 text-blue-400" />
                  <div>
                    <p className="font-semibold">{family.name}</p>
                    <p className="text-xs text-[var(--text-muted)]">
                      {family.approvedCount} approved · {family.memberCount} total members
                      {family.createdByName ? ` · Created by ${family.createdByName}` : ''}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <div className="flex gap-1">
                    <button onClick={(e) => { e.stopPropagation(); handleLeave(family.id) }}
                      className="p-1.5 text-[var(--text-secondary)] hover:text-red-400 transition" title="Leave">
                      <LogOut className="w-4 h-4" />
                    </button>
                    <button onClick={(e) => { e.stopPropagation(); handleDelete(family.id) }}
                      className="p-1.5 text-[var(--text-secondary)] hover:text-red-400 transition" title="Delete">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                  {expanded[family.id] ? <ChevronDown className="w-5 h-5 text-[var(--text-muted)]" /> : <ChevronRight className="w-5 h-5 text-[var(--text-muted)]" />}
                </div>
              </button>

              {expanded[family.id] && (
                <div className="border-t border-[var(--border)] p-4 space-y-4">
                  <div className="flex gap-2 items-end">
                    <div className="flex-1">
                      <label className="block text-xs text-[var(--text-muted)] mb-1">Invite by Email</label>
                      <input
                        type="email" value={inviteEmail[family.id] || ''}
                        onChange={(e) => setInviteEmail((p) => ({ ...p, [family.id]: e.target.value }))}
                        className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2 text-sm"
                        placeholder="email@example.com"
                      />
                    </div>
                    <button onClick={() => handleInvite(family.id)}
                      disabled={sending[family.id] || !inviteEmail[family.id]?.trim()}
                      className="px-3 py-2 bg-blue-500 rounded-lg hover:bg-blue-600 transition disabled:opacity-50 text-sm flex items-center gap-1">
                      {sending[family.id] ? <Loader2 className="w-4 h-4 animate-spin" /> : <UserPlus className="w-4 h-4" />}
                      Invite
                    </button>
                  </div>

                  <div className="space-y-2">
                    {(members[family.id] || []).length === 0 ? (
                      <p className="text-sm text-[var(--text-muted)] text-center py-4">No members yet</p>
                    ) : (
                      (members[family.id] || []).map((m) => (
                        <div key={m.id} className="flex items-center justify-between p-2.5 rounded-lg bg-[var(--bg)]/30">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-full bg-gradient-to-br from-blue-500 to-blue-600 flex items-center justify-center text-white text-xs font-medium">
                              {(m.userName || m.userEmail || '?').charAt(0).toUpperCase()}
                            </div>
                            <div>
                              <p className="text-sm font-medium">{m.userName || 'Unknown'}</p>
                              <p className="text-xs text-[var(--text-muted)]">{m.userEmail}</p>
                            </div>
                          </div>
                          <div className="flex items-center gap-2">
                            <span className={`text-xs px-2 py-0.5 rounded ${
                              m.status === 'APPROVED' ? 'bg-green-500/20 text-green-400' :
                              m.status === 'PENDING' ? 'bg-amber-500/20 text-amber-400' :
                              'bg-red-500/20 text-red-400'
                            }`}>
                              {m.status}
                            </span>
                            {m.role && m.role !== 'MEMBER' && (
                              <span className="text-xs bg-blue-500/20 text-blue-400 px-2 py-0.5 rounded">
                                {m.role}
                              </span>
                            )}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}

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
