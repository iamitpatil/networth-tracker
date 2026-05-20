import { useState, useEffect } from 'react'
import client from '../api/client'
import { toast } from 'sonner'
import {
  Landmark, Building2, CreditCard, Shield, PiggyBank, Briefcase,
  Plus, Trash2, Pencil, X, ChevronDown, ChevronUp, Eye, EyeOff
} from 'lucide-react'

const TABS = [
  { id: 'bank', label: 'Bank', icon: Landmark, color: 'blue' },
  { id: 'demat', label: 'Demat', icon: Building2, color: 'indigo' },
  { id: 'cc', label: 'Credit Cards', icon: CreditCard, color: 'red' },
  { id: 'nps', label: 'NPS/PRAN', icon: Shield, color: 'green' },
  { id: 'ppf', label: 'PPF', icon: PiggyBank, color: 'amber' },
  { id: 'epf', label: 'EPF', icon: Briefcase, color: 'purple' },
]

const fmt = (v) => v != null ? `₹${Number(v).toLocaleString('en-IN')}` : '—'

const CARD_NETWORKS = ['VISA', 'Mastercard', 'RuPay', 'AMEX']
const CARD_ISSUERS = ['HDFC Bank', 'ICICI Bank', 'SBI Card', 'Axis Bank', 'Kotak Bank', 'IDFC First', 'IndusInd Bank', 'RBL Bank', 'Yes Bank', 'Amex India', 'Citi Bank', 'Standard Chartered', 'HSBC']
const NPS_FUND_MANAGERS = ['SBI Pension Fund', 'LIC Pension Fund', 'HDFC Pension Fund', 'UTI Retirement Solutions', 'Kotak Mahindra Pension Fund', 'Birla Sun Life Pension', 'ICICI Prudential Pension Fund']

export default function AccountsHub() {
  const [activeTab, setActiveTab] = useState('bank')
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [editingId, setEditingId] = useState(null)
  const [form, setForm] = useState({})

  useEffect(() => { loadAll() }, [])

  const loadAll = async () => {
    try {
      const { data: d } = await client.get('/accounts')
      setData(d)
    } catch {} finally { setLoading(false) }
  }

  const resetForm = () => { setForm({}); setShowForm(false); setEditingId(null) }

  const handleCreate = async (endpoint, body) => {
    try {
      await client.post(`/accounts/${endpoint}`, body)
      toast.success('Account added')
      resetForm()
      loadAll()
    } catch (err) { toast.error('Failed to add', { description: err.response?.data?.message || err.message }) }
  }

  const handleUpdate = async (endpoint, id, body) => {
    try {
      await client.put(`/accounts/${endpoint}/${id}`, body)
      toast.success('Account updated')
      resetForm()
      loadAll()
    } catch (err) { toast.error('Failed to update', { description: err.response?.data?.message || err.message }) }
  }

  const handleDelete = async (endpoint, id, label) => {
    if (!confirm(`Delete ${label}?`)) return
    try {
      await client.delete(`/accounts/${endpoint}/${id}`)
      toast.success(`${label} deleted`)
      loadAll()
    } catch (err) { toast.error('Failed to delete', { description: err.response?.data?.message || err.message }) }
  }

  // For bank/demat we use existing endpoints
  const handleBankCreate = async (body) => {
    try { await client.post('/bank-accounts', body); toast.success('Bank account added'); resetForm(); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }
  const handleBankUpdate = async (id, body) => {
    try { await client.put(`/bank-accounts/${id}`, body); toast.success('Updated'); resetForm(); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }
  const handleBankDelete = async (id) => {
    if (!confirm('Delete bank account?')) return
    try { await client.delete(`/bank-accounts/${id}`); toast.success('Deleted'); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }
  const handleDematCreate = async (body) => {
    try { await client.post('/demat-accounts', body); toast.success('Demat account added'); resetForm(); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }
  const handleDematUpdate = async (id, body) => {
    try { await client.put(`/demat-accounts/${id}`, body); toast.success('Updated'); resetForm(); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }
  const handleDematDelete = async (id) => {
    if (!confirm('Delete demat account?')) return
    try { await client.delete(`/demat-accounts/${id}`); toast.success('Deleted'); loadAll() }
    catch (err) { toast.error('Failed', { description: err.response?.data?.message || err.message }) }
  }

  if (loading) return <div className="flex justify-center py-12 text-[var(--text-muted)]">Loading accounts...</div>

  const counts = data?.counts || {}

  return (
    <div className="space-y-4">
      {/* Summary bar */}
      <div className="flex items-center gap-4 flex-wrap">
        {TABS.map(tab => {
          const Icon = tab.icon
          const count = counts[tab.id === 'cc' ? 'creditCards' : tab.id === 'bank' ? 'bankAccounts' : tab.id === 'demat' ? 'dematAccounts' : `${tab.id}Accounts`] || 0
          const isActive = activeTab === tab.id
          return (
            <button key={tab.id} onClick={() => { setActiveTab(tab.id); resetForm() }}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-sm font-medium transition ${
                isActive ? `bg-${tab.color}-500/15 text-${tab.color}-400 ring-1 ring-${tab.color}-500/30`
                : 'bg-[var(--bg)] border border-[var(--border)] text-[var(--text-muted)] hover:text-[var(--text)]'}`}>
              <Icon className="w-4 h-4" />
              {tab.label}
              {count > 0 && <span className="text-xs opacity-70">({count})</span>}
            </button>
          )
        })}
        <span className="text-xs text-[var(--text-muted)] ml-auto">{counts.total || 0} total accounts</span>
      </div>

      {/* Content */}
      <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)]">
        <div className="p-4 flex items-center justify-between border-b border-[var(--border)]">
          <h3 className="font-semibold text-[var(--text)]">{TABS.find(t => t.id === activeTab)?.label} Accounts</h3>
          <button onClick={() => { setShowForm(!showForm); setEditingId(null); setForm({}) }}
            className={`px-3 py-1.5 rounded-lg text-xs font-medium transition inline-flex items-center gap-1.5 ${
              showForm ? 'bg-[var(--bg)] border border-[var(--border)] text-[var(--text-muted)]' : 'bg-blue-500 hover:bg-blue-600 text-white'}`}>
            {showForm ? <><X className="w-3.5 h-3.5" /> Cancel</> : <><Plus className="w-3.5 h-3.5" /> Add</>}
          </button>
        </div>

        {/* Forms */}
        {showForm && activeTab === 'cc' && <CreditCardForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleUpdate('credit-cards', editingId, f) : handleCreate('credit-cards', f)} onCancel={resetForm} />}
        {showForm && activeTab === 'nps' && <NpsForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleUpdate('nps', editingId, f) : handleCreate('nps', f)} onCancel={resetForm} />}
        {showForm && activeTab === 'ppf' && <PpfForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleUpdate('ppf', editingId, f) : handleCreate('ppf', f)} onCancel={resetForm} />}
        {showForm && activeTab === 'epf' && <EpfForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleUpdate('epf', editingId, f) : handleCreate('epf', f)} onCancel={resetForm} />}
        {showForm && activeTab === 'bank' && <BankForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleBankUpdate(editingId, f) : handleBankCreate(f)} onCancel={resetForm} />}
        {showForm && activeTab === 'demat' && <DematForm form={form} setForm={setForm} editingId={editingId}
          onSubmit={(f) => editingId ? handleDematUpdate(editingId, f) : handleDematCreate(f)} onCancel={resetForm} />}

        {/* Lists */}
        <div className="divide-y divide-[var(--border)]">
          {activeTab === 'bank' && (data?.bankAccounts || []).map(a => (
            <AccountRow key={a.id} icon={Landmark} color="blue" title={a.accountName} subtitle={`${a.bankName} • ${a.accountType || 'SAVINGS'}`}
              detail={fmt(a.balance)} extra={a.accountNumber ? `****${a.accountNumber.slice(-4)}` : ''}
              onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleBankDelete(a.id)} />
          ))}
          {activeTab === 'demat' && (data?.dematAccounts || []).map(a => (
            <AccountRow key={a.id} icon={Building2} color="indigo" title={a.brokerName} subtitle={a.accountType || 'Equity'}
              detail={a.isDefault ? 'Default' : ''} extra={a.accountNumber || ''}
              onEdit={() => { setForm({ brokerName: a.brokerName, accountType: a.accountType, description: a.description, isDefault: a.isDefault }); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleDematDelete(a.id)} />
          ))}
          {activeTab === 'cc' && (data?.creditCards || []).map(a => (
            <AccountRow key={a.id} icon={CreditCard} color="red" title={`${a.cardIssuer} ${a.cardName || ''}`}
              subtitle={`${a.cardNetwork || ''} • ****${a.cardLastFour || '????'}`}
              detail={a.creditLimit ? `Limit: ${fmt(a.creditLimit)}` : ''}
              extra={a.rewardType ? `${a.rewardType}` : ''}
              badge={a.isActive ? null : 'Inactive'}
              onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleDelete('credit-cards', a.id, `${a.cardIssuer} card`)} />
          ))}
          {activeTab === 'nps' && (data?.npsAccounts || []).map(a => (
            <AccountRow key={a.id} icon={Shield} color="green" title={`PRAN: ${a.pranNumber}`}
              subtitle={`${a.fundManager || 'Unknown'} • ${a.tier}`}
              detail={a.currentValue ? fmt(a.currentValue) : ''} extra={a.assetClass ? `Class ${a.assetClass}` : ''}
              onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleDelete('nps', a.id, `NPS ${a.pranNumber}`)} />
          ))}
          {activeTab === 'ppf' && (data?.ppfAccounts || []).map(a => (
            <AccountRow key={a.id} icon={PiggyBank} color="amber" title={`PPF - ${a.bankOrPostOffice}`}
              subtitle={`A/C: ${a.accountNumber}${a.branch ? ` • ${a.branch}` : ''}`}
              detail={a.currentBalance ? fmt(a.currentBalance) : ''}
              extra={a.maturityDate ? `Matures: ${new Date(a.maturityDate).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })}` : ''}
              onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleDelete('ppf', a.id, 'PPF account')} />
          ))}
          {activeTab === 'epf' && (data?.epfAccounts || []).map(a => (
            <AccountRow key={a.id} icon={Briefcase} color="purple" title={a.employerName || 'EPF Account'}
              subtitle={`UAN: ${a.uanNumber || '—'}${a.pfNumber ? ` • PF: ${a.pfNumber}` : ''}`}
              detail={a.currentBalance ? fmt(a.currentBalance) : ''}
              extra={a.isActive ? 'Active' : 'Inactive'}
              onEdit={() => { setForm(a); setEditingId(a.id); setShowForm(true) }}
              onDelete={() => handleDelete('epf', a.id, 'EPF account')} />
          ))}
        </div>

        {/* Empty state */}
        {((activeTab === 'bank' && !(data?.bankAccounts?.length)) ||
          (activeTab === 'demat' && !(data?.dematAccounts?.length)) ||
          (activeTab === 'cc' && !(data?.creditCards?.length)) ||
          (activeTab === 'nps' && !(data?.npsAccounts?.length)) ||
          (activeTab === 'ppf' && !(data?.ppfAccounts?.length)) ||
          (activeTab === 'epf' && !(data?.epfAccounts?.length))) && !showForm && (
          <div className="p-8 text-center text-[var(--text-muted)]">
            <p className="text-sm">No {TABS.find(t => t.id === activeTab)?.label.toLowerCase()} accounts yet</p>
            <button onClick={() => setShowForm(true)} className="text-xs text-[var(--primary)] mt-2 hover:underline">Add one</button>
          </div>
        )}
      </div>
    </div>
  )
}

function AccountRow({ icon: Icon, color, title, subtitle, detail, extra, badge, onEdit, onDelete }) {
  return (
    <div className="px-4 py-3 flex items-center gap-3 hover:bg-[var(--hover-bg)] transition">
      <div className={`w-9 h-9 rounded-lg bg-${color}-500/10 flex items-center justify-center shrink-0`}>
        <Icon className={`w-4.5 h-4.5 text-${color}-400`} />
      </div>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="text-sm font-medium text-[var(--text)] truncate">{title}</p>
          {badge && <span className="text-[10px] bg-[var(--hover-bg)] text-[var(--text-muted)] px-1.5 py-0.5 rounded">{badge}</span>}
        </div>
        <p className="text-xs text-[var(--text-muted)] truncate">{subtitle}</p>
      </div>
      {detail && <p className="text-sm font-semibold text-[var(--text)] shrink-0">{detail}</p>}
      {extra && <span className="text-xs text-[var(--text-muted)] shrink-0">{extra}</span>}
      <div className="flex items-center gap-1 shrink-0">
        <button onClick={onEdit} className="p-1.5 rounded hover:bg-blue-500/10 text-[var(--text-muted)] hover:text-blue-400 transition"><Pencil className="w-3.5 h-3.5" /></button>
        <button onClick={onDelete} className="p-1.5 rounded hover:bg-red-500/10 text-[var(--text-muted)] hover:text-red-400 transition"><Trash2 className="w-3.5 h-3.5" /></button>
      </div>
    </div>
  )
}

// ── Forms ──

function FormWrapper({ children, onSubmit, onCancel, editingId }) {
  return (
    <form onSubmit={(e) => { e.preventDefault(); onSubmit() }} className="p-4 border-b border-[var(--border)] bg-[var(--bg)]/50 space-y-3">
      {children}
      <div className="flex justify-end gap-2 pt-1">
        <button type="button" onClick={onCancel} className="px-3 py-1.5 text-xs text-[var(--text-muted)] border border-[var(--border)] rounded-lg hover:text-[var(--text)]">Cancel</button>
        <button type="submit" className="px-4 py-1.5 text-xs bg-blue-500 hover:bg-blue-600 text-white rounded-lg font-medium">{editingId ? 'Update' : 'Add'}</button>
      </div>
    </form>
  )
}

function Input({ label, value, onChange, type = 'text', placeholder, required }) {
  return (
    <div>
      <label className="block text-xs text-[var(--text-muted)] mb-1">{label}</label>
      <input type={type} value={value || ''} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} required={required}
        className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2 text-sm text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50" />
    </div>
  )
}

function Select({ label, value, onChange, options, required }) {
  return (
    <div>
      <label className="block text-xs text-[var(--text-muted)] mb-1">{label}</label>
      <select value={value || ''} onChange={(e) => onChange(e.target.value)} required={required}
        className="w-full bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-3 py-2 text-sm text-[var(--text)] focus:outline-none focus:ring-2 focus:ring-[var(--primary)]/50">
        <option value="">Select...</option>
        {options.map(o => <option key={typeof o === 'string' ? o : o.value} value={typeof o === 'string' ? o : o.value}>{typeof o === 'string' ? o : o.label}</option>)}
      </select>
    </div>
  )
}

function CreditCardForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Select label="Card Issuer *" value={form.cardIssuer} onChange={v => s('cardIssuer', v)} options={CARD_ISSUERS} required />
        <Input label="Card Name" value={form.cardName} onChange={v => s('cardName', v)} placeholder="e.g. Regalia, Simply Click" />
        <Select label="Network" value={form.cardNetwork} onChange={v => s('cardNetwork', v)} options={CARD_NETWORKS} />
        <Input label="Last 4 Digits" value={form.cardLastFour} onChange={v => s('cardLastFour', v.slice(0,4))} placeholder="1234" />
        <Input label="Credit Limit" value={form.creditLimit} onChange={v => s('creditLimit', v)} type="number" placeholder="300000" />
        <Input label="Billing Cycle Day" value={form.billingCycleDay} onChange={v => s('billingCycleDay', v)} type="number" placeholder="1-28" />
        <Input label="Payment Due Day" value={form.paymentDueDay} onChange={v => s('paymentDueDay', v)} type="number" placeholder="15" />
        <Select label="Reward Type" value={form.rewardType} onChange={v => s('rewardType', v)} options={['CASHBACK', 'POINTS', 'MILES']} />
        <Input label="Annual Fee" value={form.annualFee} onChange={v => s('annualFee', v)} type="number" placeholder="499" />
      </div>
    </FormWrapper>
  )
}

function NpsForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Input label="PRAN Number *" value={form.pranNumber} onChange={v => s('pranNumber', v.slice(0,12))} placeholder="12-digit PRAN" required />
        <Select label="Fund Manager" value={form.fundManager} onChange={v => s('fundManager', v)} options={NPS_FUND_MANAGERS} />
        <Select label="Tier *" value={form.tier} onChange={v => s('tier', v)} options={[{ value: 'TIER1', label: 'Tier I' }, { value: 'TIER2', label: 'Tier II' }]} required />
        <Select label="Scheme" value={form.schemePreference} onChange={v => s('schemePreference', v)} options={[{ value: 'ACTIVE', label: 'Active Choice' }, { value: 'AUTO', label: 'Auto Choice' }]} />
        <Select label="Asset Class" value={form.assetClass} onChange={v => s('assetClass', v)} options={[{ value: 'E', label: 'E - Equity' }, { value: 'C', label: 'C - Corporate Bond' }, { value: 'G', label: 'G - Govt Securities' }, { value: 'A', label: 'A - Alternate' }]} />
        <Input label="Opening Date" value={form.openingDate} onChange={v => s('openingDate', v)} type="date" />
        <Input label="Employer" value={form.employerName} onChange={v => s('employerName', v)} placeholder="Company name" />
        <Input label="Current Value" value={form.currentValue} onChange={v => s('currentValue', v)} type="number" placeholder="280000" />
      </div>
    </FormWrapper>
  )
}

function PpfForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Input label="Account Number *" value={form.accountNumber} onChange={v => s('accountNumber', v)} placeholder="PPF A/C number" required />
        <Input label="Bank / Post Office *" value={form.bankOrPostOffice} onChange={v => s('bankOrPostOffice', v)} placeholder="e.g. SBI, India Post" required />
        <Input label="Branch" value={form.branch} onChange={v => s('branch', v)} placeholder="Branch name" />
        <Input label="Opening Date" value={form.openingDate} onChange={v => s('openingDate', v)} type="date" />
        <Input label="Maturity Date" value={form.maturityDate} onChange={v => s('maturityDate', v)} type="date" />
        <Input label="Nominee" value={form.nominee} onChange={v => s('nominee', v)} placeholder="Nominee name" />
        <Input label="Current Balance" value={form.currentBalance} onChange={v => s('currentBalance', v)} type="number" placeholder="450000" />
        <Input label="This FY Deposit" value={form.currentFyDeposit} onChange={v => s('currentFyDeposit', v)} type="number" placeholder="Max 1.5L" />
        <Input label="Interest Rate %" value={form.interestRate} onChange={v => s('interestRate', v)} type="number" placeholder="7.1" />
      </div>
    </FormWrapper>
  )
}

function EpfForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Input label="UAN Number" value={form.uanNumber} onChange={v => s('uanNumber', v.slice(0,12))} placeholder="12-digit UAN" />
        <Input label="PF Number" value={form.pfNumber} onChange={v => s('pfNumber', v)} placeholder="Regional PF number" />
        <Input label="Employer *" value={form.employerName} onChange={v => s('employerName', v)} placeholder="Company name" required />
        <Input label="Date of Joining" value={form.dateOfJoining} onChange={v => s('dateOfJoining', v)} type="date" />
        <Input label="Employee Rate %" value={form.employeeContributionRate} onChange={v => s('employeeContributionRate', v)} type="number" placeholder="12" />
        <Input label="Employer Rate %" value={form.employerContributionRate} onChange={v => s('employerContributionRate', v)} type="number" placeholder="12" />
        <Input label="Current Balance" value={form.currentBalance} onChange={v => s('currentBalance', v)} type="number" placeholder="500000" />
        <Input label="Basic Salary" value={form.basicSalary} onChange={v => s('basicSalary', v)} type="number" placeholder="Monthly basic" />
      </div>
    </FormWrapper>
  )
}

function BankForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Input label="Account Name *" value={form.accountName} onChange={v => s('accountName', v)} placeholder="e.g. HDFC Savings" required />
        <Input label="Bank Name *" value={form.bankName} onChange={v => s('bankName', v)} placeholder="e.g. HDFC Bank" required />
        <Input label="Account Number" value={form.accountNumber} onChange={v => s('accountNumber', v)} placeholder="A/C number" />
        <Select label="Type" value={form.accountType} onChange={v => s('accountType', v)} options={['SAVINGS', 'CURRENT', 'FD', 'NRE', 'NRO']} />
        <Input label="IFSC Code" value={form.ifscCode} onChange={v => s('ifscCode', v)} placeholder="HDFC0001234" />
        <Input label="Branch" value={form.branch} onChange={v => s('branch', v)} placeholder="Branch name" />
        <Input label="Balance" value={form.balance} onChange={v => s('balance', v)} type="number" placeholder="285000" />
      </div>
    </FormWrapper>
  )
}

function DematForm({ form, setForm, editingId, onSubmit, onCancel }) {
  const s = (k, v) => setForm({ ...form, [k]: v })
  const brokers = ['Zerodha', 'Groww', 'Angel One', 'Upstox', 'ICICI Direct', 'HDFC Securities', 'Kotak Securities', 'Axis Direct', '5Paisa', 'Motilal Oswal', 'Sharekhan', 'Paytm Money', 'Dhan', 'INDmoney', 'Kite by Zerodha']
  return (
    <FormWrapper onSubmit={() => onSubmit(form)} onCancel={onCancel} editingId={editingId}>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        <Select label="Broker *" value={form.brokerName} onChange={v => s('brokerName', v)} options={brokers} required />
        <Input label="Account Number" value={form.accountNumber} onChange={v => s('accountNumber', v)} placeholder="Demat A/C number" />
        <Select label="Type" value={form.accountType} onChange={v => s('accountType', v)} options={['Equity', 'Commodity', 'Derivatives', 'Mutual Funds']} />
        <Input label="Description" value={form.description} onChange={v => s('description', v)} placeholder="Notes" />
      </div>
    </FormWrapper>
  )
}
