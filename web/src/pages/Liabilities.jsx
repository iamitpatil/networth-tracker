import { useState, useEffect } from 'react'
import client from '../api/client'
import { Plus, Trash2, CheckCircle, XCircle, CreditCard, X } from 'lucide-react'

const LIABILITY_TYPES = [
  { value: 'home_loan', label: 'Home Loan' },
  { value: 'car_loan', label: 'Car Loan' },
  { value: 'education_loan', label: 'Education Loan' },
  { value: 'credit_card', label: 'Credit Card' },
  { value: 'personal_loan', label: 'Personal Loan' },
]

export default function Liabilities() {
  const [totalLiabilities, setTotalLiabilities] = useState(0)
  const [loading, setLoading] = useState(true)
  const [showForm, setShowForm] = useState(false)
  const [localLiabilities, setLocalLiabilities] = useState([])
  const [selectedLiability, setSelectedLiability] = useState(null)
  const [emiSchedule, setEmiSchedule] = useState([])
  const [form, setForm] = useState({
    liabilityType: 'home_loan',
    lender: '',
    originalAmount: '',
    interestRate: '',
    tenureMonths: '',
    startDate: new Date().toISOString().slice(0, 10),
  })

  useEffect(() => {
    client.get('/net-worth/breakdown')
      .then((res) => setTotalLiabilities(res.data.totalLiabilities || 0))
      .catch(console.error)

    client.get('/liabilities')
      .then((res) => setLocalLiabilities(res.data || []))
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  const handleSubmit = async (e) => {
    e.preventDefault()
    try {
      const { data } = await client.post('/liabilities', {
        liabilityType: form.liabilityType,
        lender: form.lender,
        originalAmount: parseFloat(form.originalAmount),
        interestRate: parseFloat(form.interestRate),
        tenureMonths: parseInt(form.tenureMonths),
        startDate: form.startDate,
      })

      setLocalLiabilities([...localLiabilities, data])
      setSelectedLiability(data)
      setShowForm(false)
      setForm({
        liabilityType: 'home_loan',
        lender: '',
        originalAmount: '',
        interestRate: '',
        tenureMonths: '',
        startDate: new Date().toISOString().slice(0, 10),
      })

      fetchEmiSchedule(data.id)
    } catch (err) {
      console.error('Failed to create liability', err)
    }
  }

  const fetchEmiSchedule = async (id) => {
    try {
      const { data } = await client.get(`/liabilities/${id}/emi-schedule`)
      setEmiSchedule(data)
    } catch (err) {
      console.error('Failed to fetch EMI schedule', err)
    }
  }

  const handleMarkPaid = async (emi) => {
    if (!selectedLiability) return
    try {
      await client.post(`/liabilities/${selectedLiability.id}/emi-pay`, {
        paymentDate: emi.dueDate, // LocalDate as YYYY-MM-DD string
        amount: emi.totalEmi,
      })
      fetchEmiSchedule(selectedLiability.id)
    } catch (err) {
      console.error('Failed to mark EMI as paid', err)
    }
  }

  const handleDelete = async (id) => {
    if (confirm('Delete this liability?')) {
      try {
        await client.delete(`/liabilities/${id}`)
        setLocalLiabilities(localLiabilities.filter((l) => l.id !== id))
        if (selectedLiability?.id === id) {
          setSelectedLiability(null)
          setEmiSchedule([])
        }
      } catch (err) {
        console.error('Failed to delete liability', err)
      }
    }
  }

  if (loading) return <div className="flex justify-center py-20 text-slate-400">Loading...</div>

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Liabilities</h1>
          <p className="text-slate-400 text-sm mt-1">Manage your loans and EMIs</p>
        </div>
        <button
          onClick={() => setShowForm(!showForm)}
          className={`px-4 py-2 rounded-lg flex items-center gap-2 transition ${showForm ? 'bg-slate-600 hover:bg-slate-500' : 'bg-blue-500 hover:bg-blue-600'}`}
        >
          <Plus className="w-4 h-4" /> {showForm ? 'Cancel' : 'Add Liability'}
        </button>
      </div>

      <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
        <p className="text-slate-400 text-sm">Total Liabilities</p>
        <p className="text-2xl font-bold mt-1 text-red-400">Rs. {totalLiabilities.toLocaleString('en-IN')}</p>
      </div>

      {showForm && (
        <div className="bg-slate-800 rounded-xl p-6 border border-slate-700">
          <h3 className="text-lg font-semibold mb-4">Add New Liability</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm text-slate-400 mb-1">Liability Type</label>
                <select
                  value={form.liabilityType}
                  onChange={(e) => setForm({ ...form, liabilityType: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                  required
                >
                  {LIABILITY_TYPES.map((t) => (
                    <option key={t.value} value={t.value}>{t.label}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Lender</label>
                <input
                  type="text"
                  value={form.lender}
                  onChange={(e) => setForm({ ...form, lender: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                  placeholder="e.g., HDFC Bank"
                  required
                />
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Original Amount</label>
                <input
                  type="number"
                  step="any"
                  value={form.originalAmount}
                  onChange={(e) => setForm({ ...form, originalAmount: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                  placeholder="500000"
                  required
                />
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Outstanding Amount</label>
                <input
                  type="number"
                  step="any"
                  value={form.outstandingAmount}
                  onChange={(e) => setForm({ ...form, outstandingAmount: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                  placeholder="450000"
                  required
                />
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Interest Rate (%)</label>
                <input
                  type="number"
                  step="any"
                  value={form.interestRate}
                  onChange={(e) => setForm({ ...form, interestRate: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                  placeholder="8.5"
                  required
                />
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Tenure (months)</label>
                <input
                  type="number"
                  value={form.tenureMonths}
                  onChange={(e) => setForm({ ...form, tenureMonths: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                  placeholder="240"
                  required
                />
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Start Date</label>
                <input
                  type="date"
                  value={form.startDate}
                  onChange={(e) => setForm({ ...form, startDate: e.target.value })}
                  className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5"
                  required
                />
              </div>
            </div>

            <div className="flex justify-end gap-3 mt-6">
              <button
                type="button"
                onClick={() => setShowForm(false)}
                className="px-4 py-2 bg-slate-700 rounded-lg hover:bg-slate-600 transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-6 py-2 bg-blue-500 hover:bg-blue-600 rounded-lg font-medium transition"
              >
                Add Liability
              </button>
            </div>
          </form>
        </div>
      )}

      {localLiabilities.length > 0 && (
        <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-700">
            <h3 className="text-lg font-semibold">Your Liabilities</h3>
          </div>
          <table className="w-full">
            <thead className="bg-slate-700/50 text-left">
              <tr>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">Type</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">Lender</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Original</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">EMI</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Outstanding</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700">
              {localLiabilities.map((liability) => (
                <tr key={liability.id} className="hover:bg-slate-700/30">
                  <td className="px-4 py-3">
                    <span className="text-xs bg-slate-700 px-2 py-1 rounded">
                      {LIABILITY_TYPES.find((t) => t.value === liability.liabilityType)?.label || liability.liabilityType}
                    </span>
                  </td>
                  <td className="px-4 py-3">{liability.lender}</td>
                  <td className="px-4 py-3 text-right">Rs. {liability.originalAmount?.toLocaleString('en-IN')}</td>
                  <td className="px-4 py-3 text-right">Rs. {liability.monthlyEmi?.toLocaleString('en-IN')}</td>
                  <td className="px-4 py-3 text-right">Rs. {liability.outstandingAmount?.toLocaleString('en-IN')}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      <button
                        onClick={() => {
                          setSelectedLiability(liability)
                          fetchEmiSchedule(liability.id)
                        }}
                        className="text-blue-400 hover:text-blue-300 transition text-sm"
                      >
                        View EMIs
                      </button>
                      <button
                        onClick={() => handleDelete(liability.id)}
                        className="text-slate-500 hover:text-red-400 transition"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {selectedLiability && emiSchedule.length > 0 && (
        <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden">
          <div className="px-4 py-3 border-b border-slate-700 flex items-center justify-between">
            <h3 className="text-lg font-semibold">EMI Schedule - {selectedLiability.lender}</h3>
            <button
              onClick={() => {
                setSelectedLiability(null)
                setEmiSchedule([])
              }}
              className="text-slate-500 hover:text-slate-300 transition"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
          <table className="w-full">
            <thead className="bg-slate-700/50 text-left">
              <tr>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">EMI #</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">Due Date</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Principal</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Interest</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Total EMI</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">Status</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700">
              {emiSchedule.map((emi) => (
                <tr key={emi.id} className="hover:bg-slate-700/30">
                  <td className="px-4 py-3">{emi.emiNumber}</td>
                  <td className="px-4 py-3">{new Date(emi.dueDate).toLocaleDateString('en-IN')}</td>
                  <td className="px-4 py-3 text-right">Rs. {emi.principal?.toLocaleString('en-IN')}</td>
                  <td className="px-4 py-3 text-right">Rs. {emi.interest?.toLocaleString('en-IN')}</td>
                  <td className="px-4 py-3 text-right font-medium">Rs. {emi.totalEmi?.toLocaleString('en-IN')}</td>
                  <td className="px-4 py-3">
                    {emi.paid ? (
                      <span className="flex items-center gap-1 text-green-400 text-sm">
                        <CheckCircle className="w-4 h-4" /> Paid
                      </span>
                    ) : (
                      <span className="flex items-center gap-1 text-red-400 text-sm">
                        <XCircle className="w-4 h-4" /> Unpaid
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    {!emi.paid && (
                      <button
                        onClick={() => handleMarkPaid(emi)}
                        className="px-3 py-1.5 bg-green-500/20 text-green-400 rounded-lg text-sm hover:bg-green-500/30 transition"
                      >
                        Mark Paid
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {localLiabilities.length === 0 && !showForm && (
        <div className="bg-slate-800 rounded-xl p-12 border border-slate-700 text-center">
          <CreditCard className="w-12 h-12 text-slate-600 mx-auto mb-4" />
          <p className="text-slate-400 mb-2">No liabilities added yet</p>
          <p className="text-slate-500 text-sm">Click "Add Liability" to get started</p>
        </div>
      )}
    </div>
  )
}
