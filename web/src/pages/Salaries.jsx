import { useState, useEffect, useMemo } from 'react'
import client from '../api/client'
import { Plus, X, Pencil, Trash2, Loader2, Briefcase, Banknote, Upload, ChevronDown, ChevronRight, FileText, Download } from 'lucide-react'

export default function Salaries() {
  const [salaries, setSalaries] = useState([])
  const [accounts, setAccounts] = useState([])
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState({})
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [parsedData, setParsedData] = useState(null)
  const [expandedRow, setExpandedRow] = useState(null)
  const [salaryDocs, setSalaryDocs] = useState({})

  const load = async () => {
    try {
      const [s, a] = await Promise.all([
        client.get('/salaries'),
        client.get('/bank-accounts'),
      ])
      setSalaries(s.data || [])
      setAccounts(a.data || [])
      const docs = {}
      for (const sal of s.data || []) {
        try {
          const res = await client.get(`/salaries/${sal.id}/document`)
          docs[sal.id] = res.data
        } catch {}
      }
      setSalaryDocs(docs)
    } catch (e) { console.error(e) }
    finally { setLoading(false) }
  }

  useEffect(() => { load() }, [])

  const resetForm = () => setForm({ employerName: '', amount: '', bankAccountId: '', payDate: new Date().toISOString().slice(0, 10), notes: '' })

  const openCreate = () => { resetForm(); setEditing(null); setParsedData(null); setShowForm(true) }
  const openEdit = (s) => { setForm({ ...s, amount: s.amount }); setParsedData(null); setEditing(s.id); setShowForm(true) }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const payload = {
        ...form,
        amount: parseFloat(form.amount) || 0,
        bankAccountId: form.bankAccountId || null,
        components: parsedData?.components || null,
        documentId: parsedData?.documentId || null,
      }
      if (editing) {
        const { data } = await client.put(`/salaries/${editing}`, payload)
        setSalaries((p) => p.map((s) => s.id === editing ? data : s))
      } else {
        const { data } = await client.post('/salaries', payload)
        setSalaries((p) => [data, ...p])
        if (payload.documentId) {
          try {
            const res = await client.get(`/salaries/${data.id}/document`)
            setSalaryDocs((d) => ({ ...d, [data.id]: res.data }))
          } catch {}
        }
      }
      setShowForm(false); setParsedData(null)
    } catch (e) { console.error(e) }
    finally { setSaving(false) }
  }

  const handleDelete = async (id) => {
    if (!confirm('Delete this salary record?')) return
    try {
      await client.delete(`/salaries/${id}`)
      setSalaries((p) => p.filter((s) => s.id !== id))
    } catch (e) { console.error(e) }
  }

  const handleUpload = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return
    setUploading(true)
    setParsedData(null)
    try {
      const fd = new FormData()
      fd.append('file', file)
      const { data } = await client.post('/salaries/parse-slip', fd, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setParsedData(data)
      if (data.employerName) setForm((f) => ({ ...f, employerName: data.employerName }))
      if (data.netPay) setForm((f) => ({ ...f, amount: String(data.netPay) }))
      if (data.payDate) setForm((f) => ({ ...f, payDate: String(data.payDate).slice(0, 10) }))
      setShowForm(true); setEditing(null)
    } catch (err) { console.error(err) }
    finally { setUploading(false) }
  }

  const handleDownload = async (salaryId) => {
    const doc = salaryDocs[salaryId]
    if (!doc) return
    try {
      const res = await client.get(`/documents/${doc.id}/download`, { responseType: 'blob' })
      const url = window.URL.createObjectURL(new Blob([res.data]))
      const a = document.createElement('a')
      a.href = url; a.download = doc.originalFilename
      document.body.appendChild(a); a.click()
      a.remove(); window.URL.revokeObjectURL(url)
    } catch {}
  }

  const totalMonthly = useMemo(() =>
    salaries.filter(s => {
      if (!s.payDate) return false
      const d = new Date(s.payDate)
      const now = new Date()
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()
    }).reduce((sum, s) => sum + (parseFloat(s.amount) || 0), 0), [salaries])

  if (loading) return <div className="flex justify-center py-20 text-slate-400">Loading...</div>

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Salary Records</h1>
          <p className="text-slate-400 text-sm mt-1">
            {salaries.length} records · This month: Rs. {totalMonthly.toLocaleString('en-IN')}
          </p>
        </div>
        <div className="flex gap-2">
          <label className={`px-4 py-2 rounded-lg flex items-center gap-2 transition cursor-pointer ${uploading ? 'bg-slate-600' : 'bg-emerald-500 hover:bg-emerald-600'}`}>
            {uploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
            {uploading ? 'Parsing...' : 'Upload Slip'}
            <input type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={handleUpload} className="hidden" disabled={uploading} />
          </label>
          <button onClick={() => { if (showForm) { setShowForm(false); resetForm(); setParsedData(null) } else openCreate() }}
            className={`px-4 py-2 rounded-lg flex items-center gap-2 transition ${showForm ? 'bg-slate-600 hover:bg-slate-500' : 'bg-blue-500 hover:bg-blue-600'}`}>
            {showForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
            {showForm ? 'Cancel' : 'Add Salary'}
          </button>
        </div>
      </div>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] space-y-4">
          <h3 className="text-lg font-semibold">{editing ? 'Edit' : parsedData ? 'Salary from Slip' : 'Add'} Salary Record</h3>

          {parsedData && parsedData.components && (
            <div className="bg-[var(--bg)]/50 rounded-lg p-4 space-y-1.5">
              <p className="text-sm font-medium text-emerald-400 mb-2">Parsed Components</p>
              {Object.entries(parsedData.components).map(([k, v]) => (
                <div key={k} className="flex justify-between text-sm">
                  <span className="text-[var(--text-muted)]">{k}</span>
                  <span className="font-medium">{v != null ? `Rs. ${Number(v).toLocaleString('en-IN')}` : '-'}</span>
                </div>
              ))}
              {parsedData.grossPay != null && (
                <div className="flex justify-between text-sm pt-2 border-t border-[var(--border)] mt-2">
                  <span className="font-medium">Gross Pay</span>
                  <span className="font-medium">Rs. {Number(parsedData.grossPay).toLocaleString('en-IN')}</span>
                </div>
              )}
              {parsedData.netPay != null && (
                <div className="flex justify-between text-sm font-bold text-green-400">
                  <span>Net Pay</span>
                  <span>Rs. {Number(parsedData.netPay).toLocaleString('en-IN')}</span>
                </div>
              )}
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm text-[var(--text-muted)] mb-1">Employer Name</label>
              <input type="text" value={form.employerName || ''} onChange={(e) => setForm({ ...form, employerName: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" required />
            </div>
            <div>
              <label className="block text-sm text-[var(--text-muted)] mb-1">Amount (Rs.)</label>
              <input type="number" step="any" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" required />
            </div>
            <div>
              <label className="block text-sm text-[var(--text-muted)] mb-1">Bank Account (optional)</label>
              <select value={form.bankAccountId || ''} onChange={(e) => setForm({ ...form, bankAccountId: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5">
                <option value="">None</option>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.accountName} ({a.bankName})</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-[var(--text-muted)] mb-1">Pay Date</label>
              <input type="date" value={form.payDate || ''} onChange={(e) => setForm({ ...form, payDate: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" />
            </div>
            <div className="md:col-span-2">
              <label className="block text-sm text-[var(--text-muted)] mb-1">Notes</label>
              <textarea value={form.notes || ''} onChange={(e) => setForm({ ...form, notes: e.target.value })} className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2.5" rows={2} />
            </div>
          </div>
          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={() => { setShowForm(false); resetForm(); setParsedData(null) }} className="px-4 py-2 bg-slate-700 rounded-lg hover:bg-slate-600 transition">Cancel</button>
            <button type="submit" disabled={saving || !form.employerName || !form.amount}
              className="px-6 py-2 bg-blue-500 rounded-lg hover:bg-blue-600 transition disabled:opacity-50 flex items-center gap-2">
              {saving && <Loader2 className="w-4 h-4 animate-spin" />}
              {editing ? 'Update' : 'Add'}
            </button>
          </div>
        </form>
      )}

      <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
        {salaries.length === 0 ? (
          <div className="text-center py-16 text-[var(--text-secondary)]">
            <Briefcase className="w-16 h-16 mx-auto mb-4 opacity-40" />
            <p className="text-lg">No salary records yet</p>
            <p className="text-sm mt-1">Add your salary or upload a slip to track income</p>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-[var(--bg)]/50 text-left">
              <tr>
                <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Employer</th>
                <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Amount</th>
                <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Bank Account</th>
                <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Pay Date</th>
                <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Components</th>
                <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Pay Slip</th>
                <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--border)]">
              {salaries.map((s) => (
                <tr key={s.id} className="hover:bg-[var(--hover-bg)]">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <Briefcase className="w-4 h-4 text-blue-400" />
                      <div>
                        <p className="font-medium">{s.employerName}</p>
                        {s.notes && <p className="text-xs text-[var(--text-muted)]">{s.notes}</p>}
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-right font-medium">
                    <div className="flex items-center justify-end gap-1">
                      <Banknote className="w-4 h-4 text-green-400" />
                      Rs. {(parseFloat(s.amount) || 0).toLocaleString('en-IN')}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                    {s.bankAccountId ? accounts.find(a => a.id === s.bankAccountId)?.accountName || '-' : '-'}
                  </td>
                  <td className="px-4 py-3 text-sm text-[var(--text-secondary)]">
                    {s.payDate ? new Date(s.payDate).toLocaleDateString('en-IN') : '-'}
                  </td>
                  <td className="px-4 py-3">
                    {s.components && typeof s.components === 'object' && Object.keys(s.components).length > 0 ? (
                      <button onClick={() => setExpandedRow(expandedRow === s.id ? null : s.id)}
                        className="flex items-center gap-1 text-sm text-blue-400 hover:text-blue-300 transition">
                        {expandedRow === s.id ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                        {Object.keys(s.components).length} items
                      </button>
                    ) : (
                      <span className="text-xs text-[var(--text-muted)]">-</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {salaryDocs[s.id] ? (
                      <button onClick={() => handleDownload(s.id)}
                        className="flex items-center gap-1 text-sm text-emerald-400 hover:text-emerald-300 transition">
                        <Download className="w-4 h-4" /> Slip
                      </button>
                    ) : (
                      <span className="text-xs text-[var(--text-muted)]">-</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-1">
                      <button onClick={() => openEdit(s)} className="p-1.5 text-[var(--text-secondary)] hover:text-blue-400 transition"><Pencil className="w-4 h-4" /></button>
                      <button onClick={() => handleDelete(s.id)} className="p-1.5 text-[var(--text-secondary)] hover:text-red-400 transition"><Trash2 className="w-4 h-4" /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {expandedRow && salaries.find(s => s.id === expandedRow)?.components && (
        <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] p-4 space-y-1.5">
          <p className="text-sm font-medium text-emerald-400 mb-2">Salary Breakdown</p>
          {Object.entries(salaries.find(s => s.id === expandedRow).components).map(([k, v]) => (
            <div key={k} className="flex justify-between text-sm">
              <span className="text-[var(--text-muted)]">{k}</span>
              <span className="font-medium">{v != null ? `Rs. ${Number(v).toLocaleString('en-IN')}` : '-'}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
