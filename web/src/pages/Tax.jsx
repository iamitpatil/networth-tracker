import React, { useState, useEffect, useRef } from 'react';
import { Calendar, TrendingDown, TrendingUp, DollarSign, Shield, Lightbulb, FileText, ChevronDown, ArrowRight, Upload, Loader2, Trash2, CheckCircle2, X } from 'lucide-react';
import { toast } from 'sonner';
import client from '../api/client';
import { ConfirmDialog } from '../components/ui';

const FINANCIAL_YEARS = ['2024-2025', '2023-2024', '2022-2023', '2021-2022', '2020-2021'];

const TAX_RULES = [
  { asset: 'Equity LTCG', rate: '12.5%', threshold: 'Above ₹1.25L', notes: 'Long term (>1 year)' },
  { asset: 'Equity STCG', rate: '20%', threshold: 'Full amount', notes: 'Short term (≤1 year)' },
  { asset: 'Debt Funds', rate: 'Slab Rate', threshold: 'Full amount', notes: 'As per income tax slab' },
  { asset: 'Crypto/NFT', rate: '30%', threshold: 'Full amount', notes: 'Flat rate + 4% cess' },
  { asset: '80C Deduction', rate: 'Max ₹1.5L', threshold: 'Section 80C', notes: 'ELSS, PPF, EPF, etc.' },
];

export default function Tax() {
  const [selectedFY, setSelectedFY] = useState('2024-2025');
  const [summary, setSummary] = useState(null);
  const [harvestingOpps, setHarvestingOpps] = useState([]);
  const [util80C, setUtil80C] = useState(null);
  const [loading, setLoading] = useState(true);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [taxRegime, setTaxRegime] = useState('NEW');
  const [form16s, setForm16s] = useState([]);
  const [itrFilings, setItrFilings] = useState([]);
  const [uploadingForm16, setUploadingForm16] = useState(false);
  const [uploadingITR, setUploadingITR] = useState(false);
  const [showRegimeCompare, setShowRegimeCompare] = useState(false);
  const [comparison, setComparison] = useState(null);
  const form16InputRef = useRef(null);
  const itrInputRef = useRef(null);

  useEffect(() => {
    fetchTaxData();
  }, [selectedFY]);

  const fetchTaxData = async () => {
    setLoading(true);
    try {
      const [summaryRes, harvestingRes, util80CRes, regimeRes, form16Res, itrRes] = await Promise.all([
        client.get(`/tax/summary/${selectedFY}`),
        client.get('/tax/harvesting-opportunities'),
        client.get('/tax/80c-utilization'),
        client.get('/tax/regime'),
        client.get('/tax/form16'),
        client.get('/tax/itr'),
      ]);

      setSummary(summaryRes.data);
      setHarvestingOpps(harvestingRes.data || []);
      setUtil80C(util80CRes.data);
      setTaxRegime(regimeRes.data?.regime || 'NEW');
      setForm16s(form16Res.data || []);
      setItrFilings(itrRes.data || []);
    } catch (error) {
      console.error('Error fetching tax data:', error);
    } finally {
      setLoading(false);
    }
  };

  const updateRegime = async (newRegime) => {
    try {
      await client.put('/tax/regime', { regime: newRegime });
      setTaxRegime(newRegime);
    } catch (e) {
      console.error('Failed to update regime', e);
    }
  };

  const compareRegimes = async () => {
    try {
      const gross = (summary?.equity?.ltcg || 0) + (summary?.equity?.stcg || 0) + 1500000; // Demo input
      const { data } = await client.post('/tax/regime/compare', {
        grossSalary: gross,
        totalDeductions: util80C?.utilized || 0,
        hraExemption: 0,
      });
      setComparison(data);
      setShowRegimeCompare(true);
    } catch (e) {
      console.error('Compare failed', e);
    }
  };

  const uploadForm16 = async (file) => {
    if (!file) return;
    setUploadingForm16(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('financialYear', selectedFY);
      const { data } = await client.post('/tax/form16/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      toast.success('Form 16 uploaded', {
        description: `Parsed with ${data.parseConfidence}% confidence. Please review and verify.`,
      });
      await fetchTaxData();
    } catch (e) {
      toast.error('Failed to upload Form 16', {
        description: e?.response?.data?.error || e?.message || 'Unknown error',
      });
    } finally {
      setUploadingForm16(false);
    }
  };

  const uploadITR = async (file) => {
    if (!file) return;
    setUploadingITR(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('financialYear', selectedFY);
      formData.append('filingType', 'ORIGINAL');
      const { data } = await client.post('/tax/itr/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      });
      toast.success('ITR uploaded', { description: 'Please complete the form details.' });
      await fetchTaxData();
    } catch (e) {
      toast.error('Failed to upload ITR', {
        description: e?.response?.data?.error || e?.message || 'Unknown error',
      });
    } finally {
      setUploadingITR(false);
    }
  };

  const [confirmAction, setConfirmAction] = useState(null);

  const requestDeleteForm16 = (id) => {
    setConfirmAction({
      type: 'form16',
      id,
      title: 'Delete Form 16?',
      description: 'This will permanently delete this Form 16 record.',
    });
  };

  const requestDeleteItr = (id) => {
    setConfirmAction({
      type: 'itr',
      id,
      title: 'Delete ITR record?',
      description: 'This will permanently delete this ITR filing record.',
    });
  };

  const performDelete = async () => {
    if (!confirmAction) return;
    try {
      const endpoint = confirmAction.type === 'form16' ? 'form16' : 'itr';
      await client.delete(`/tax/${endpoint}/${confirmAction.id}`);
      toast.success(`${confirmAction.type === 'form16' ? 'Form 16' : 'ITR record'} deleted`);
      await fetchTaxData();
    } catch (e) {
      // toast handled by interceptor
    } finally {
      setConfirmAction(null);
    }
  };

  const formatCurrency = (amount) => {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount || 0);
  };

  const getUtilizationPercent = () => {
    if (!util80C) return 0;
    return Math.min(((util80C.utilized || 0) / 150000) * 100, 100);
  };

  const getBarColor = (percent) => {
    if (percent >= 80) return '#ef4444';
    if (percent >= 60) return '#f59e0b';
    return '#22c55e';
  };

  const totalLTCG = (summary?.equity?.ltcg || 0) + (summary?.gold?.gains || 0) + (summary?.realEstate?.gains || 0);
  const totalSTCG = summary?.equity?.stcg || 0;
  const totalTax = summary?.totalTax || 0;
  const remaining80C = 150000 - (util80C?.utilized || 0);

  const gainDetails = [];
  if (summary?.equity?.ltcg > 0) gainDetails.push({ asset: 'Equity LTCG', type: 'LTCG', amount: summary.equity.ltcg, tax: summary.equity.taxOnLTCG || 0 });
  if (summary?.equity?.stcg > 0) gainDetails.push({ asset: 'Equity STCG', type: 'STCG', amount: summary.equity.stcg, tax: summary.equity.taxOnSTCG || 0 });
  if (summary?.gold?.gains > 0) gainDetails.push({ asset: 'Gold', type: 'LTCG', amount: summary.gold.gains, tax: 0 });
  if (summary?.crypto?.gains > 0) gainDetails.push({ asset: 'Crypto', type: 'STCG', amount: summary.crypto.gains, tax: summary.crypto.tax || 0 });
  if (summary?.realEstate?.gains > 0) gainDetails.push({ asset: 'Real Estate', type: 'LTCG', amount: summary.realEstate.gains, tax: 0 });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Tax Planning</h1>
          <p className="text-[var(--text-muted)] text-sm mt-1">Manage your tax liability and optimize deductions</p>
        </div>
        <div className="relative">
          <button
            onClick={() => setDropdownOpen(!dropdownOpen)}
            className="flex items-center gap-2 bg-[var(--input-bg)] border border-[var(--border)] rounded-lg px-4 py-2 hover:border-blue-500 transition-colors"
          >
            <Calendar className="w-4 h-4 text-blue-400" />
            <span className="font-medium text-sm">{selectedFY}</span>
            <ChevronDown className={`w-4 h-4 text-[var(--text-muted)] transition-transform ${dropdownOpen ? 'rotate-180' : ''}`} />
          </button>
          {dropdownOpen && (
            <div className="absolute right-0 mt-2 w-44 bg-[var(--input-bg)] border border-[var(--border)] rounded-lg shadow-xl z-10 overflow-hidden">
              {FINANCIAL_YEARS.map((fy) => (
                <button
                  key={fy}
                  onClick={() => { setSelectedFY(fy); setDropdownOpen(false); }}
                  className={`w-full text-left px-4 py-2 text-sm hover:bg-[var(--hover-bg)] transition-colors ${selectedFY === fy ? 'text-blue-400 bg-[var(--hover-bg)]' : 'text-[var(--text)]'}`}
                >
                  {fy}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64 text-[var(--text-muted)]">Loading tax data...</div>
      ) : (
        <>
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
              <p className="text-[var(--text-muted)] text-sm">Total LTCG</p>
              <p className="text-2xl font-bold text-green-400 mt-1">{formatCurrency(totalLTCG)}</p>
            </div>
            <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
              <p className="text-[var(--text-muted)] text-sm">Total STCG</p>
              <p className="text-2xl font-bold text-red-400 mt-1">{formatCurrency(totalSTCG)}</p>
            </div>
            <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
              <p className="text-[var(--text-muted)] text-sm">Tax Liability</p>
              <p className="text-2xl font-bold text-amber-400 mt-1">{formatCurrency(totalTax)}</p>
            </div>
            <div className="bg-[var(--bg-card)] rounded-xl p-5 border border-[var(--border)]">
              <p className="text-[var(--text-muted)] text-sm">80C Utilized</p>
              <p className="text-2xl font-bold text-blue-400 mt-1">{formatCurrency(util80C?.utilized)}</p>
            </div>
          </div>

          {/* Tax Regime Selector */}
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <Shield className="w-5 h-5 text-purple-400" />
                Tax Regime
              </h2>
              <button onClick={compareRegimes} className="text-sm text-blue-400 hover:text-blue-300 transition-colors">
                Compare Both →
              </button>
            </div>
            <div className="flex gap-3">
              <button
                onClick={() => updateRegime('NEW')}
                className={`flex-1 p-4 rounded-lg border-2 transition-all ${taxRegime === 'NEW' ? 'border-blue-500 bg-blue-500/10' : 'border-[var(--border)] hover:border-[var(--border)]'}`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="font-semibold">NEW Regime</span>
                  {taxRegime === 'NEW' && <CheckCircle2 className="w-5 h-5 text-blue-400" />}
                </div>
                <p className="text-xs text-[var(--text-muted)] text-left">Lower slabs, no 80C/HRA. Default for FY 2023-24+</p>
                <p className="text-xs text-[var(--text-secondary)] mt-1 text-left">Std Deduction: ₹75,000</p>
              </button>
              <button
                onClick={() => updateRegime('OLD')}
                className={`flex-1 p-4 rounded-lg border-2 transition-all ${taxRegime === 'OLD' ? 'border-blue-500 bg-blue-500/10' : 'border-[var(--border)] hover:border-[var(--border)]'}`}
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="font-semibold">OLD Regime</span>
                  {taxRegime === 'OLD' && <CheckCircle2 className="w-5 h-5 text-blue-400" />}
                </div>
                <p className="text-xs text-[var(--text-muted)] text-left">Higher slabs but 80C, HRA, etc. allowed</p>
                <p className="text-xs text-[var(--text-secondary)] mt-1 text-left">Std Deduction: ₹50,000</p>
              </button>
            </div>
          </div>

          {/* Form 16 Section */}
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <FileText className="w-5 h-5 text-green-400" />
                Form 16 (TDS Certificate)
              </h2>
              <button
                onClick={() => form16InputRef.current?.click()}
                disabled={uploadingForm16}
                className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-blue-500/20 text-blue-400 hover:bg-blue-500/30 transition-colors text-sm"
              >
                {uploadingForm16 ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                {uploadingForm16 ? 'Parsing...' : 'Upload Form 16'}
              </button>
              <input
                ref={form16InputRef}
                type="file"
                accept=".pdf"
                className="hidden"
                onChange={(e) => uploadForm16(e.target.files[0])}
              />
            </div>
            {form16s.length === 0 ? (
              <p className="text-[var(--text-secondary)] text-sm">No Form 16 uploaded yet. Upload your employer's TDS certificate to auto-fill tax details.</p>
            ) : (
              <div className="space-y-2">
                {form16s.map((f) => (
                  <div key={f.id} className="flex items-center justify-between p-3 bg-[var(--hover-bg)] rounded-lg">
                    <div>
                      <p className="font-medium">{f.employerName || 'Unknown Employer'}</p>
                      <p className="text-xs text-[var(--text-muted)] mt-0.5">
                        FY {f.financialYear} • Gross: {formatCurrency(f.grossSalary)} • TDS: {formatCurrency(f.tdsTotal)}
                        {f.parseConfidence != null && ` • Confidence: ${f.parseConfidence}%`}
                      </p>
                    </div>
                    <button onClick={() => requestDeleteForm16(f.id)} className="text-red-400 hover:text-red-300" aria-label="Delete Form 16">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* ITR Filings */}
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <FileText className="w-5 h-5 text-cyan-400" />
                ITR Filings
              </h2>
              <button
                onClick={() => itrInputRef.current?.click()}
                disabled={uploadingITR}
                className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-cyan-500/20 text-cyan-400 hover:bg-cyan-500/30 transition-colors text-sm"
              >
                {uploadingITR ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                {uploadingITR ? 'Uploading...' : 'Upload ITR'}
              </button>
              <input
                ref={itrInputRef}
                type="file"
                accept=".pdf"
                className="hidden"
                onChange={(e) => uploadITR(e.target.files[0])}
              />
            </div>
            {itrFilings.length === 0 ? (
              <p className="text-[var(--text-secondary)] text-sm">No ITR filings yet. Upload your ITR-V acknowledgement after filing.</p>
            ) : (
              <div className="space-y-2">
                {itrFilings.map((itr) => (
                  <div key={itr.id} className="flex items-center justify-between p-3 bg-[var(--hover-bg)] rounded-lg">
                    <div>
                      <p className="font-medium">
                        {itr.itrFormType || 'ITR'} • FY {itr.financialYear}
                        {itr.filingType !== 'ORIGINAL' && <span className="text-xs text-amber-400 ml-2">({itr.filingType})</span>}
                      </p>
                      <p className="text-xs text-[var(--text-muted)] mt-0.5">
                        {itr.acknowledgementNumber && `Ack: ${itr.acknowledgementNumber}`}
                        {itr.filingDate && ` • Filed: ${itr.filingDate}`}
                        {itr.eVerified && <span className="text-green-400 ml-2">✓ Verified</span>}
                      </p>
                    </div>
                    <button onClick={() => requestDeleteItr(itr.id)} className="text-red-400 hover:text-red-300" aria-label="Delete ITR record">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* 80C Utilization */}
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <Shield className="w-5 h-5 text-blue-400" />
                80C Deduction Utilization
              </h2>
              <span className="text-sm text-[var(--text-muted)]">{formatCurrency(util80C?.utilized)} / ₹1,50,000</span>
            </div>
            <div className="w-full bg-[var(--input-bg)] rounded-full h-3 mb-2">
              <div className="h-3 rounded-full transition-all duration-500" style={{ width: `${getUtilizationPercent()}%`, backgroundColor: getBarColor(getUtilizationPercent()) }} />
            </div>
            <div className="flex justify-between text-xs text-[var(--text-muted)]">
              <span>{getUtilizationPercent().toFixed(1)}% utilized</span>
              <span>{formatCurrency(remaining80C)} remaining</span>
            </div>
            {util80C?.breakdown && (
              <div className="flex gap-4 mt-4">
                {Object.entries(util80C.breakdown).map(([key, value]) => (
                  value > 0 && (
                    <div key={key} className="text-sm">
                      <span className="text-[var(--text-muted)]">{key}:</span>
                      <span className="text-[var(--text)] ml-1 font-medium">{formatCurrency(value)}</span>
                    </div>
                  )
                ))}
              </div>
            )}
          </div>

          {/* Capital Gains Table */}
          <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
            <div className="px-4 py-3 border-b border-[var(--border)]">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <FileText className="w-5 h-5 text-blue-400" />
                Capital Gains Details
              </h2>
            </div>
            <div className="overflow-x-auto scrollbar-thin">
            <table className="w-full min-w-[500px]">
              <thead className="bg-[var(--input-bg)] text-left">
                <tr>
                  <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Asset</th>
                  <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Type</th>
                  <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Gain Amount</th>
                  <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)] text-right">Tax</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--border)]">
                {gainDetails.length === 0 ? (
                  <tr><td colSpan="4" className="px-4 py-12 text-center text-[var(--text-secondary)]">No capital gains for this financial year</td></tr>
                ) : (
                  gainDetails.map((g, idx) => (
                    <tr key={idx} className="hover:bg-[var(--hover-bg)]">
                      <td className="px-4 py-3 font-medium text-sm">{g.asset}</td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-1 rounded text-xs font-medium ${g.type === 'LTCG' ? 'bg-green-400/10 text-green-400' : 'bg-red-400/10 text-red-400'}`}>{g.type}</span>
                      </td>
                      <td className={`px-4 py-3 text-right font-medium text-sm ${g.amount >= 0 ? 'text-green-400' : 'text-red-400'}`}>{formatCurrency(g.amount)}</td>
                      <td className="px-4 py-3 text-right font-medium text-sm text-amber-400">{formatCurrency(g.tax)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
            </div>
          </div>

          {/* Tax Harvesting Opportunities */}
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
            <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
              <Lightbulb className="w-5 h-5 text-amber-400" />
              Tax Harvesting Opportunities
            </h2>
            {harvestingOpps.length === 0 ? (
              <p className="text-[var(--text-secondary)] text-center py-8">No harvesting opportunities available</p>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {harvestingOpps.map((opp, idx) => (
                  <div key={idx} className="bg-[var(--input-bg)] border border-[var(--border)] rounded-lg p-4 hover:border-blue-500 transition-colors">
                    <div className="flex items-center justify-between mb-3">
                      <span className="font-semibold text-sm">{opp.symbol}</span>
                      <span className="text-xs text-[var(--text-muted)]">{opp.quantity} shares</span>
                    </div>
                    <div className="space-y-2">
                      <div className="flex justify-between text-sm">
                        <span className="text-[var(--text-muted)]">Current Loss</span>
                        <span className="text-red-400 font-medium">{formatCurrency(opp.currentLoss)}</span>
                      </div>
                      <div className="flex justify-between text-sm">
                        <span className="text-[var(--text-muted)]">Potential Savings</span>
                        <span className="text-green-400 font-medium">{formatCurrency(opp.potentialSavings)}</span>
                      </div>
                      <div className="flex items-center gap-1 text-xs text-blue-400 mt-2">
                        <ArrowRight className="w-3 h-3" />
                        <span>Harvest to offset gains</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Tax Rules Reference */}
          <div className="bg-[var(--bg-card)] rounded-xl border border-[var(--border)] overflow-hidden">
            <div className="px-4 py-3 border-b border-[var(--border)]">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <FileText className="w-5 h-5 text-blue-400" />
                Tax Rules Reference
              </h2>
            </div>
            <div className="overflow-x-auto scrollbar-thin">
              <table className="w-full min-w-[500px]">
                <thead className="bg-[var(--input-bg)] text-left">
                  <tr>
                    <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Asset Type</th>
                    <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Tax Rate</th>
                    <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Threshold</th>
                    <th className="px-4 py-3 text-sm font-medium text-[var(--text-muted)]">Notes</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--border)]">
                  {TAX_RULES.map((rule, idx) => (
                    <tr key={idx} className="hover:bg-[var(--hover-bg)]">
                      <td className="px-4 py-3 font-medium text-sm">{rule.asset}</td>
                      <td className="px-4 py-3"><span className="px-2 py-1 rounded-full text-xs font-medium bg-blue-400/10 text-blue-400">{rule.rate}</span></td>
                      <td className="px-4 py-3 text-[var(--text-muted)] text-sm">{rule.threshold}</td>
                      <td className="px-4 py-3 text-[var(--text-muted)] text-sm">{rule.notes}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}

      <ConfirmDialog
        open={!!confirmAction}
        onClose={() => setConfirmAction(null)}
        onConfirm={performDelete}
        title={confirmAction?.title}
        description={confirmAction?.description}
        confirmText="Delete"
      />

      {/* Regime Compare Modal */}
      {showRegimeCompare && comparison && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setShowRegimeCompare(false)}>
          <div className="bg-[var(--bg-card)] rounded-xl max-w-2xl w-full mx-4 border border-[var(--border)]" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-6 border-b border-[var(--border)]">
              <h3 className="text-lg font-semibold">Tax Regime Comparison</h3>
              <button onClick={() => setShowRegimeCompare(false)} className="text-[var(--text-muted)] hover:text-white">
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div className={`p-4 rounded-lg border-2 ${comparison.recommended === 'OLD' ? 'border-green-500 bg-green-500/10' : 'border-[var(--border)]'}`}>
                  <p className="text-sm text-[var(--text-muted)] mb-2">OLD Regime</p>
                  <p className="text-2xl font-bold">{formatCurrency(comparison.oldRegime?.totalTax)}</p>
                  <p className="text-xs text-[var(--text-muted)] mt-1">Taxable: {formatCurrency(comparison.oldRegime?.taxableIncome)}</p>
                  {comparison.recommended === 'OLD' && <p className="text-xs text-green-400 mt-2">✓ Recommended</p>}
                </div>
                <div className={`p-4 rounded-lg border-2 ${comparison.recommended === 'NEW' ? 'border-green-500 bg-green-500/10' : 'border-[var(--border)]'}`}>
                  <p className="text-sm text-[var(--text-muted)] mb-2">NEW Regime</p>
                  <p className="text-2xl font-bold">{formatCurrency(comparison.newRegime?.totalTax)}</p>
                  <p className="text-xs text-[var(--text-muted)] mt-1">Taxable: {formatCurrency(comparison.newRegime?.taxableIncome)}</p>
                  {comparison.recommended === 'NEW' && <p className="text-xs text-green-400 mt-2">✓ Recommended</p>}
                </div>
              </div>
              <div className="p-3 rounded-lg bg-blue-500/10 border border-blue-500/30">
                <p className="text-sm">
                  💡 The <span className="font-semibold text-blue-400">{comparison.recommended} Regime</span> saves you
                  <span className="font-semibold text-green-400"> {formatCurrency(comparison.savings)}</span> in this scenario.
                </p>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
