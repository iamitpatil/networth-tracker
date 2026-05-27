import { useState, useEffect, useMemo } from 'react'
import client from '../api/client'
import { Plus, X, Pencil, Trash2, Loader2, Briefcase, Upload, FileText, Download } from 'lucide-react'
import { ConfirmDialog } from '../components/ui/Modal'
import StyledSelect from '../components/ui/StyledSelect'
import { PageSkeleton } from '../components/ui'
import { formatINR } from '../utils/format'

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
  const [expandedRow] = useState(null)
  const [salaryDocs, setSalaryDocs] = useState({})
  const [confirmDialog, setConfirmDialog] = useState({ open: false, title: '', description: '', onConfirm: null })

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
        } catch { /* ignored */ }
      }
      setSalaryDocs(docs)
    } catch (e) { console.error(e) }
    finally { setLoading(false) }
  }

  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => { load() }, [])

  const resetForm = () => setForm({ employerName: '', amount: '', bankAccountId: '', payDate: new Date().toISOString().slice(0, 10), notes: '', components: { earnings: {}, deductions: {} } })

  const openCreate = () => { resetForm(); setEditing(null); setParsedData(null); setShowForm(true) }
  const openEdit = (s) => {
    setForm({ ...s, amount: s.amount, components: s.components || { earnings: {}, deductions: {} } })
    setParsedData(null); setEditing(s.id); setShowForm(true)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSaving(true)
    try {
      const components = form.components || null
      // Clean empty components
      const cleanedComponents = components && (
        Object.values(components).some(v => typeof v === 'object' ? Object.keys(v).length > 0 : v != null)
      ) ? components : null
      const payload = {
        employerName: form.employerName,
        amount: parseFloat(form.amount) || 0,
        bankAccountId: form.bankAccountId || null,
        payDate: form.payDate || null,
        notes: form.notes || null,
        components: cleanedComponents,
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
          } catch { /* ignored */ }
        }
      }
      setShowForm(false); setParsedData(null)
    } catch (e) { console.error(e) }
    finally { setSaving(false) }
  }

  const handleDelete = (id) => {
    setConfirmDialog({
      open: true,
      title: 'Delete this salary record?',
      description: 'This action cannot be undone.',
      onConfirm: async () => {
        try {
          await client.delete(`/salaries/${id}`)
          setSalaries((p) => p.filter((s) => s.id !== id))
        } catch (e) { console.error(e) }
      },
    })
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
      // Build components from parsed data
      let components = data.components || {}
      if (data.earnings || data.deductions) {
        components = {}
        if (data.earnings && typeof data.earnings === 'object') components.earnings = { ...data.earnings }
        if (data.deductions && typeof data.deductions === 'object') components.deductions = { ...data.deductions }
      }
      // Ensure earnings/deductions structure
      if (!components.earnings && !components.deductions) {
        // Flat components — separate into earnings/deductions by guessing
        const flat = { ...components }
        const earnings = {}
        const deductions = {}
        for (const [k, v] of Object.entries(flat)) {
          if (typeof v === 'object') continue
          const lk = k.toLowerCase()
          if (lk.includes('tax') || lk.includes('deduction') || lk.includes('pf') || lk.includes('esi') || lk.includes('pt') || lk.includes('nps') || Number(v) < 0) {
            deductions[k] = Math.abs(Number(v) || 0)
          } else {
            earnings[k] = Number(v) || 0
          }
        }
        components = { earnings, deductions }
      }
      setForm((f) => ({
        ...f,
        employerName: data.employerName || f.employerName,
        amount: data.netPay ? String(data.netPay) : f.amount,
        payDate: data.payDate ? String(data.payDate).slice(0, 10) : f.payDate,
        components,
      }))
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
    } catch { /* ignored */ }
  }

  const totalMonthly = useMemo(() =>
    salaries.filter(s => {
      if (!s.payDate) return false
      const d = new Date(s.payDate)
      const now = new Date()
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear()
    }).reduce((sum, s) => sum + (parseFloat(s.amount) || 0), 0), [salaries])

  if (loading) return <PageSkeleton />

  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Salary Records</h1>
          <p className="text-[var(--text-muted)] text-sm mt-1">
            {salaries.length} records · This month: Rs. {totalMonthly.toLocaleString('en-IN')}
          </p>
        </div>
        <div className="flex gap-2">
          <label className={`px-4 py-2 rounded-lg flex items-center gap-2 transition cursor-pointer ${uploading ? 'bg-[var(--input-bg)]' : 'bg-emerald-500 hover:bg-emerald-600'}`}>
            {uploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
            {uploading ? 'Parsing...' : 'Upload Slip'}
            <input type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={handleUpload} className="hidden" disabled={uploading} />
          </label>
          <button onClick={() => { if (showForm) { setShowForm(false); resetForm(); setParsedData(null) } else openCreate() }}
            className={`px-4 py-2 rounded-lg flex items-center gap-2 transition ${showForm ? 'bg-[var(--input-bg)] hover:bg-[var(--hover-bg)]' : 'bg-blue-500 hover:bg-blue-600'}`}>
            {showForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
            {showForm ? 'Cancel' : 'Add Salary'}
          </button>
        </div>
      </div>

      {showForm && (
        <form onSubmit={handleSubmit} className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)] space-y-4">
          <h3 className="text-lg font-semibold">{editing ? 'Edit' : parsedData ? 'Salary from Slip' : 'Add'} Salary Record</h3>

          {/* Upload PDF area */}
          {!editing && (
            <div className={`border-2 border-dashed rounded-lg p-4 transition ${parsedData ? 'border-green-500/30 bg-green-500/5' : 'border-[var(--border)] hover:border-blue-500/30'}`}>
              {parsedData ? (
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2 text-sm text-green-400">
                    <FileText className="w-4 h-4" />
                    <span>Salary slip parsed — fields pre-filled below</span>
                  </div>
                  <button type="button" onClick={() => setParsedData(null)} className="text-xs text-[var(--text-muted)] hover:text-red-400 transition">Clear</button>
                </div>
              ) : (
                <label className="flex flex-col items-center gap-2 cursor-pointer">
                  <Upload className="w-6 h-6 text-[var(--text-muted)]" />
                  <span className="text-sm text-[var(--text-muted)]">
                    {uploading ? 'Parsing salary slip...' : 'Drop salary slip PDF here or click to upload'}
                  </span>
                  <span className="text-xs text-[var(--text-secondary)]">PDF, PNG, JPG supported — will auto-fill fields</span>
                  <input type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={handleUpload} className="hidden" disabled={uploading} />
                  {uploading && <Loader2 className="w-4 h-4 animate-spin text-blue-400" />}
                </label>
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
              <StyledSelect label="Bank Account (optional)" value={form.bankAccountId || ''} onChange={(v) => setForm({ ...form, bankAccountId: v })}
                placeholder="None"
                options={accounts.map(a => ({ value: a.id, label: `${a.accountName} (${a.bankName})` }))} />
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

          {/* Editable salary components */}
          <EditableSalaryComponentsForm components={form.components} onChange={(c) => setForm({ ...form, components: c })} />

          <div className="flex justify-end gap-3 pt-2">
            <button type="button" onClick={() => { setShowForm(false); resetForm(); setParsedData(null) }} className="px-4 py-2 bg-[var(--input-bg)] rounded-lg hover:bg-[var(--input-bg)] transition">Cancel</button>
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
<div className="overflow-x-auto scrollbar-thin">
            <table className="w-full min-w-[700px]">
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
                      <p className="text-green-400">{formatINR(s.amount)}</p>
                      <p className="text-xs text-[var(--text-muted)]">{s.frequency}</p>
                    </td>
                    <td className="px-4 py-3 text-sm">
                      {accounts.find(a => a.id === s.bankAccountId)?.accountName || '-'}
                    </td>
                    <td className="px-4 py-3 text-sm">
                      {s.payDate ? new Date(s.payDate).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }) : '-'}
                    </td>
                    <td className="px-4 py-3 text-sm">
                      {s.components && Object.entries(s.components).length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {Object.entries(s.components).slice(0, 3).map(([key, val]) => (
                            <span key={key} className="px-1.5 py-0.5 bg-[var(--input-bg)] rounded text-xs">
                              {key}: {formatINR(val)}
                            </span>
                          ))}
                        </div>
                      ) : (
                        <span className="text-xs text-[var(--text-muted)]">-</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      {s.paySlipUrl ? (
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
                        <button onClick={() => handleDelete(s.id)} aria-label="Delete salary" className="p-1.5 text-[var(--text-secondary)] hover:text-red-400 transition"><Trash2 className="w-4 h-4" /></button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <ConfirmDialog
        open={confirmDialog.open}
        onClose={() => setConfirmDialog(d => ({ ...d, open: false }))}
        onConfirm={confirmDialog.onConfirm}
        title={confirmDialog.title}
        description={confirmDialog.description}
      />

      {expandedRow && salaries.find(s => s.id === expandedRow)?.components && (
        <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] p-4 space-y-3">
          <p className="text-sm font-medium text-emerald-400">Salary Breakdown</p>
          <SalaryComponents components={salaries.find(s => s.id === expandedRow).components} />
        </div>
      )}
    </div>
  )
}

/**
 * Editable salary components form for both manual entry and PDF upload.
 * Supports earnings/deductions sections with add/remove rows.
 */
function EditableSalaryComponentsForm({ components, onChange }) {
  const comps = components || { earnings: {}, deductions: {} }
  const hasStructure = comps.earnings || comps.deductions
  const earnings = hasStructure ? (comps.earnings || {}) : {}
  const deductions = hasStructure ? (comps.deductions || {}) : {}

  const [newEarningKey, setNewEarningKey] = useState('')
  const [newDeductionKey, setNewDeductionKey] = useState('')

  const updateComponent = (section, key, value) => {
    const updated = { ...comps, [section]: { ...comps[section], [key]: Number(value) || 0 } }
    onChange(updated)
  }

  const addComponent = (section, key) => {
    if (!key.trim()) return
    const updated = { ...comps, [section]: { ...comps[section], [key.trim()]: 0 } }
    onChange(updated)
    if (section === 'earnings') setNewEarningKey('')
    else setNewDeductionKey('')
  }

  const removeComponent = (section, key) => {
    const sectionData = { ...comps[section] }
    delete sectionData[key]
    onChange({ ...comps, [section]: sectionData })
  }

  const renderSection = (title, section, items, isDeduction) => {
    const total = Object.values(items).reduce((s, v) => s + (Number(v) || 0), 0)
    return (
      <div>
        <div className="flex items-center justify-between mb-2">
          <p className={`text-sm font-semibold ${isDeduction ? 'text-red-400' : 'text-green-400'}`}>{title}</p>
          <span className={`text-xs font-medium ${isDeduction ? 'text-red-400' : 'text-green-400'}`}>
            Total: Rs. {total.toLocaleString('en-IN')}
          </span>
        </div>
        <div className="space-y-1.5 pl-3 border-l-2 border-[var(--border)]">
          {Object.entries(items).map(([k, v]) => (
            <div key={k} className="flex items-center gap-2">
              <span className="text-xs text-[var(--text-muted)] flex-1 truncate">{k}</span>
              <input type="number" value={v}
                onChange={(e) => updateComponent(section, k, e.target.value)}
                className={`w-28 bg-[var(--input-bg)] border border-[var(--border)] rounded px-2 py-1 text-xs text-right ${isDeduction ? 'text-red-400' : 'text-[var(--text)]'}`} />
              <button type="button" onClick={() => removeComponent(section, k)}
                className="text-[var(--text-secondary)] hover:text-red-400 transition p-0.5">
                <X className="w-3 h-3" />
              </button>
            </div>
          ))}
          {/* Add new row */}
          <div className="flex items-center gap-2 pt-1">
            <input type="text" value={isDeduction ? newDeductionKey : newEarningKey}
              onChange={(e) => isDeduction ? setNewDeductionKey(e.target.value) : setNewEarningKey(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addComponent(section, isDeduction ? newDeductionKey : newEarningKey) } }}
              placeholder={`Add ${isDeduction ? 'deduction' : 'earning'}...`}
              className="flex-1 bg-[var(--input-bg)] border border-dashed border-[var(--border)] rounded px-2 py-1 text-xs text-[var(--text)] placeholder-[var(--text-secondary)]" />
            <button type="button"
              onClick={() => addComponent(section, isDeduction ? newDeductionKey : newEarningKey)}
              className="text-xs text-blue-400 hover:text-blue-300 transition px-2 py-1">+ Add</button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="bg-[var(--bg)]/50 rounded-lg p-4 space-y-4">
      <p className="text-sm font-medium text-[var(--text)]">Salary Components</p>
      {renderSection('Earnings', 'earnings', earnings, false)}
      {renderSection('Deductions', 'deductions', deductions, true)}
    </div>
  )
}

/**
 * Renders salary components — handles both flat and nested (earnings/deductions) formats.
 * Flat:   { "basic": 74000, "hra": 37000, "income_tax": -11920 }
 * Nested: { "earnings": { "Basic": 110000, "HRA": 44000 }, "deductions": { "PF": 1800, "Tax": 78380 } }
 */
function SalaryComponents({ components }) {
  if (!components || typeof components !== 'object') return null
  const entries = Object.entries(components)

  // Check if nested (values are objects)
  const isNested = entries.some(([, v]) => v && typeof v === 'object')

  if (isNested) {
    return (
      <div className="space-y-3">
        {entries.map(([section, items]) => {
          if (!items || typeof items !== 'object') {
            return (
              <div key={section} className="flex justify-between text-sm">
                <span className="text-[var(--text-muted)]">{section.replace(/_/g, ' ')}</span>
                <span className="font-medium">Rs. {Number(items).toLocaleString('en-IN')}</span>
              </div>
            )
          }
          const isDeduction = section.toLowerCase().includes('deduction')
          const sectionTotal = Object.values(items).reduce((s, v) => s + (Number(v) || 0), 0)
          return (
            <div key={section}>
              <p className={`text-xs font-semibold mb-1.5 ${isDeduction ? 'text-red-400' : 'text-green-400'}`}>
                {section.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}
              </p>
              <div className="space-y-1 pl-2 border-l-2 border-[var(--border)]">
                {Object.entries(items).map(([k, v]) => (
                  <div key={k} className="flex justify-between text-sm">
                    <span className="text-[var(--text-muted)]">{k}</span>
                    <span className={`font-medium ${isDeduction ? 'text-red-400' : ''}`}>
                      {isDeduction ? '- ' : ''}Rs. {Math.abs(Number(v) || 0).toLocaleString('en-IN')}
                    </span>
                  </div>
                ))}
                <div className="flex justify-between text-sm pt-1 border-t border-[var(--border)]/50 font-semibold">
                  <span className="text-[var(--text-muted)]">Total {section.replace(/_/g, ' ')}</span>
                  <span className={isDeduction ? 'text-red-400' : 'text-green-400'}>
                    {isDeduction ? '- ' : ''}Rs. {Math.abs(sectionTotal).toLocaleString('en-IN')}
                  </span>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    )
  }

  // Flat format
  return (
    <div className="space-y-1">
      {entries.map(([k, v]) => {
        const num = Number(v) || 0
        const isDeduction = num < 0 || k.toLowerCase().includes('tax') || k.toLowerCase().includes('deduction') || k.toLowerCase().includes('pf')
        return (
          <div key={k} className="flex justify-between text-sm">
            <span className="text-[var(--text-muted)]">{k.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}</span>
            <span className={`font-medium ${isDeduction ? 'text-red-400' : ''}`}>
              {isDeduction && num > 0 ? '- ' : ''}Rs. {Math.abs(num).toLocaleString('en-IN')}
            </span>
          </div>
        )
      })}
    </div>
  )
}
