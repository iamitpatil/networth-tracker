import { useState, useEffect, useRef } from 'react';
import { Calendar, Shield, Lightbulb, FileText, ChevronDown, ArrowRight, Upload, Loader2, Trash2, CheckCircle2, X } from 'lucide-react';
import { toast } from 'sonner';
import client from '../api/client';
import { ConfirmDialog } from '../components/ui';


export default function Tax() {
  // Populated from /tax/financial-years so the selector can only offer years the backend
  // has rules for. `unverifiedFYs` are shown with a caveat rather than presented as settled.
  const [financialYears, setFinancialYears] = useState([]);
  const [unverifiedFYs, setUnverifiedFYs] = useState([]);
  // Years where more than one regime existed. Before FY 2020-21 there was only the old
  // regime, so a comparison is meaningless and the button is hidden.
  const [comparableFYs, setComparableFYs] = useState([]);
  // Rates and deductions for the selected year, so nothing on this page hardcodes a figure
  // that is only correct for one of the 27 years the selector offers.
  const [yearRules, setYearRules] = useState(null);
  const [selectedFY, setSelectedFY] = useState(null);
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

  // Load the supported years first; the tax fetch below waits for a selectedFY.
  const fetchFinancialYears = async () => {
    try {
      const { data } = await client.get('/tax/financial-years');
      const years = data?.financialYears || [];
      setFinancialYears(years);
      setUnverifiedFYs(data?.unverified || []);
      setComparableFYs(data?.comparable || []);
      // Prefer the current financial year, falling back to the newest we have rules for.
      setSelectedFY(years.includes(data?.current) ? data.current : years[0] || null);
    } catch (error) {
      console.error('Error fetching supported financial years:', error);
      setLoading(false);
    }
  };

  const fetchTaxData = async () => {
    if (!selectedFY) return;
    setLoading(true);
    try {
      const [summaryRes, harvestingRes, util80CRes, rulesRes, regimeRes, form16Res, itrRes] = await Promise.all([
        client.get(`/tax/summary/${selectedFY}`),
        client.get(`/tax/harvesting-opportunities?financialYear=${selectedFY}`),
        client.get(`/tax/80c-utilization?financialYear=${selectedFY}`),
        client.get(`/tax/rules/${selectedFY}`),
        client.get('/tax/regime'),
        client.get('/tax/form16'),
        client.get('/tax/itr'),
      ]);

      setSummary(summaryRes.data);
      setHarvestingOpps(harvestingRes.data || []);
      setUtil80C(util80CRes.data);
      setYearRules(rulesRes.data);
      setTaxRegime(regimeRes.data?.regime || 'NEW');
      setForm16s(form16Res.data || []);
      setItrFilings(itrRes.data || []);
    } catch (error) {
      console.error('Error fetching tax data:', error);
    } finally {
      setLoading(false);
    }
  };

  /* eslint-disable react-hooks/set-state-in-effect */
  useEffect(() => {
    fetchFinancialYears();
  }, []);

  useEffect(() => {
    fetchTaxData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedFY]);
  /* eslint-enable react-hooks/set-state-in-effect */

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
        // Without this the backend defaults to the current year, so a comparison for a
        // past year would silently be computed with today's rates.
        financialYear: selectedFY,
      });
      setComparison(data);
      setShowRegimeCompare(true);
    } catch (e) {
      console.error('Compare failed', e);
      // Surface the reason. The backend explains, for instance, that the new regime did
      // not exist before FY 2020-21 - previously this failed silently.
      toast.error(e?.response?.data?.message || 'Could not compare regimes');
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
      await client.post('/tax/itr/upload', formData, {
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
    } catch {
      // toast handled by interceptor
    } finally {
      setConfirmAction(null);
    }
  };

  // Percent from a rate fraction: 0.125 -> "12.5%". Empty rates render as "Exempt", which is
  // the real position for equity LTCG between FY 2004-05 and FY 2017-18 under s.10(38).
  const pct = (rate) => {
    if (rate == null) return '—';
    const n = Number(rate);
    return n === 0 ? 'Exempt' : `${+(n * 100).toFixed(4)}%`;
  };

  const taxRulesForYear = () => {
    const cg = yearRules?.capitalGains;
    if (!cg) return [];
    const lakh = (v) => v == null ? '—' : `₹${(Number(v) / 100000).toFixed(2).replace(/\.00$/, '')}L`;
    return [
      { asset: 'Equity LTCG', rate: pct(cg.equityLtcgRate), threshold: Number(cg.equityLtcgExemption) > 0 ? `Above ${lakh(cg.equityLtcgExemption)}` : 'Full amount', notes: 'Long term' },
      { asset: 'Equity STCG', rate: pct(cg.equityStcgRate), threshold: 'Full amount', notes: 'Short term' },
      { asset: 'Gold / Property LTCG', rate: pct(cg.otherAssetLtcgRate), threshold: 'Full amount', notes: 'Long term' },
      { asset: 'Debt Funds', rate: 'Slab Rate', threshold: 'Full amount', notes: 'As per income tax slab' },
      { asset: 'Crypto / VDA', rate: pct(cg.cryptoRate), threshold: 'Full amount', notes: `Flat rate + ${pct(yearRules.cessRate)} cess` },
      { asset: '80C Deduction', rate: `Max ${lakh(yearRules.deductions?.limit80C)}`, threshold: 'Section 80C', notes: 'ELSS, PPF, EPF, etc.' },
    ];
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

  // Long-term totals use each class's own longTermGains where the backend reports it; the
  // previous version added gold and property *total* gains here, folding short-term gains
  // into an LTCG figure.
  const lt = (cls) => Number(cls?.longTermGains ?? cls?.gains ?? 0);
  const st = (cls) => Number(cls?.shortTermGains ?? 0);
  const totalLTCG = Number(summary?.equity?.ltcg || 0) + lt(summary?.gold) + lt(summary?.realEstate);
  const totalSTCG = Number(summary?.equity?.stcg || 0) + st(summary?.gold) + st(summary?.realEstate);

  // Realised losses, which the API now returns. Nothing displayed them before, so a user who
  // had booked losses saw no sign of them even though they reduce the tax due.
  // Regimes that existed in the selected year. Falls back to both while the rules load so
  // the selector does not flicker empty.
  const availableRegimes = yearRules?.regimes
    ? ['NEW', 'OLD'].filter((r) => yearRules.regimes[r])
    : ['NEW', 'OLD'];

  const equityLtcl = Number(summary?.equity?.ltcl || 0);
  const equityStcl = Number(summary?.equity?.stcl || 0);
  const totalLosses = equityLtcl + equityStcl
    + Number(summary?.gold?.losses || 0) + Number(summary?.realEstate?.losses || 0)
    + Number(summary?.debt?.losses || 0);
  const unabsorbedLoss = Number(summary?.equity?.unabsorbedShortTermLoss || 0)
    + Number(summary?.equity?.unabsorbedLongTermLoss || 0);
  const totalTax = summary?.totalTax || 0;
  const remaining80C = 150000 - (util80C?.utilized || 0);

  const gainDetails = [];
  const push = (asset, type, amount, tax, note) => {
    if (Number(amount) > 0) gainDetails.push({ asset, type, amount: Number(amount), tax: Number(tax || 0), note });
  };
  push('Equity LTCG', 'LTCG', summary?.equity?.ltcg, summary?.equity?.taxOnLTCG);
  push('Equity STCG', 'STCG', summary?.equity?.stcg, summary?.equity?.taxOnSTCG);
  // Losses are rows too. They carry no tax, but hiding them made the numbers unexplainable.
  push('Equity LTCL (loss)', 'LTCG', equityLtcl, 0, 'Offsets long-term gains');
  push('Equity STCL (loss)', 'STCG', equityStcl, 0, 'Offsets short-term, then long-term gains');

  // Gold, property and debt now carry real tax rather than a hardcoded zero. Where the class
  // is slab-rated the backend says so instead of inventing a figure.
  [['Gold / SGB', summary?.gold], ['Real Estate', summary?.realEstate], ['Debt', summary?.debt]]
    .forEach(([label, cls]) => {
      const slabNote = cls?.taxAtSlabRate ? 'Short-term portion taxed at your slab rate' : undefined;
      push(`${label} LTCG`, 'LTCG', cls?.longTermGains, cls?.tax, slabNote);
      push(`${label} STCG`, 'STCG', cls?.shortTermGains, 0, slabNote || 'Taxed at your slab rate');
    });

  push('Crypto / VDA', 'STCG', summary?.crypto?.gains, summary?.crypto?.tax,
    summary?.crypto?.lossSetOffAllowed === false ? 'Losses cannot be set off (s.115BBH)' : undefined);

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
            <span className="font-medium text-sm">{selectedFY || '—'}</span>
            <ChevronDown className={`w-4 h-4 text-[var(--text-muted)] transition-transform ${dropdownOpen ? 'rotate-180' : ''}`} />
          </button>
          {dropdownOpen && (
            <div className="absolute right-0 mt-2 w-56 max-h-72 overflow-y-auto bg-[var(--input-bg)] border border-[var(--border)] rounded-lg shadow-xl z-10 overflow-hidden">
              {financialYears.map((fy) => (
                <button
                  key={fy}
                  onClick={() => { setSelectedFY(fy); setDropdownOpen(false); }}
                  className={`w-full text-left px-4 py-2 text-sm hover:bg-[var(--hover-bg)] transition-colors ${selectedFY === fy ? 'text-blue-400 bg-[var(--hover-bg)]' : 'text-[var(--text)]'}`}
                  title={unverifiedFYs.includes(fy) ? 'Rates for this year are unverified - confirm before filing' : undefined}
                >
                  {fy}
                  {unverifiedFYs.includes(fy) && <span className="ml-2 text-[10px] text-amber-400">unverified</span>}
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
          {/* Rates for most historical years are best-effort. Saying so where the figures are
              actually shown matters more than a badge tucked inside the year dropdown. */}
          {unverifiedFYs.includes(selectedFY) && (
            <div className="bg-amber-400/10 border border-amber-400/30 rounded-xl px-4 py-3 flex items-start gap-2">
              <Shield className="w-4 h-4 text-amber-400 mt-0.5 shrink-0" />
              <p className="text-sm text-[var(--text-secondary)]">
                <span className="text-amber-400 font-medium">Rates for {selectedFY} are unverified.</span>{' '}
                Slab structures are reliable, but surcharge details and some transition years need
                confirmation before these figures are used for a filing.
                {yearRules?.note && <span className="block text-xs text-[var(--text-muted)] mt-1">{yearRules.note}</span>}
              </p>
            </div>
          )}

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
              {totalLosses > 0 ? (
                <>
                  <p className="text-[var(--text-muted)] text-sm">Realised Losses</p>
                  <p className="text-2xl font-bold text-red-400 mt-1">{formatCurrency(totalLosses)}</p>
                  <p className="text-[10px] text-[var(--text-muted)] mt-1">
                    {unabsorbedLoss > 0
                      ? `${formatCurrency(unabsorbedLoss)} unabsorbed, carries forward 8 years`
                      : 'Fully set off against this year\u2019s gains'}
                  </p>
                </>
              ) : (
                <>
                  <p className="text-[var(--text-muted)] text-sm">80C Utilized</p>
                  <p className="text-2xl font-bold text-blue-400 mt-1">{formatCurrency(util80C?.utilized)}</p>
                </>
              )}
            </div>
          </div>

          {/* Tax Regime Selector */}
          <div className="bg-[var(--bg-card)] rounded-xl p-6 border border-[var(--border)]">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <Shield className="w-5 h-5 text-purple-400" />
                Tax Regime
              </h2>
              {comparableFYs.includes(selectedFY) ? (
                <button onClick={compareRegimes} className="text-sm text-blue-400 hover:text-blue-300 transition-colors">
                  Compare Both →
                </button>
              ) : (
                <span className="text-xs text-[var(--text-muted)]" title="The new regime under section 115BAC begins FY 2020-2021">
                  Only one regime existed in {selectedFY}
                </span>
              )}
            </div>
            {/* One card per regime that actually existed in the selected year. Rendering both
                unconditionally showed a NEW Regime card for pre-2020 years with
                "Std Deduction: ₹0", because the API has no NEW block for those years and
                formatCurrency turns undefined into zero. The new regime under s.115BAC
                begins FY 2020-21. */}
            <div className="flex gap-3">
              {availableRegimes.map((regime) => {
                const rules = yearRules?.regimes?.[regime];
                return (
                  <button
                    key={regime}
                    onClick={() => updateRegime(regime)}
                    className={`flex-1 p-4 rounded-lg border-2 transition-all ${taxRegime === regime ? 'border-blue-500 bg-blue-500/10' : 'border-[var(--border)] hover:border-[var(--border)]'}`}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-semibold">{regime} Regime</span>
                      {taxRegime === regime && <CheckCircle2 className="w-5 h-5 text-blue-400" />}
                    </div>
                    {/* Described from the year's own rules rather than a fixed string, so it
                        cannot contradict the figures beneath it. */}
                    <p className="text-xs text-[var(--text-muted)] text-left">
                      {rules?.allowsDeductions
                        ? 'Higher slabs, but 80C, HRA and other deductions allowed'
                        : 'Lower slabs, no 80C or HRA'}
                    </p>
                    {/* A genuine zero (the old regime had no standard deduction from FY
                        2005-06 to FY 2017-18) must not read like missing data, which is how
                        an absent value rendered before. */}
                    <p className="text-xs text-[var(--text-secondary)] mt-1 text-left">
                      Std Deduction: {Number(rules?.standardDeduction) > 0
                        ? formatCurrency(rules.standardDeduction)
                        : 'none this year'}
                    </p>
                    {Number(rules?.maxRebate) > 0 && (
                      <p className="text-xs text-[var(--text-secondary)] text-left">
                        Rebate u/s 87A: up to {formatCurrency(rules.maxRebate)} below {formatCurrency(rules.rebateThreshold)}
                      </p>
                    )}
                  </button>
                );
              })}
            </div>
            {availableRegimes.length === 1 && (
              <p className="text-xs text-[var(--text-muted)] mt-3">
                Only the {availableRegimes[0]} regime existed in {selectedFY}. The new regime
                under section 115BAC begins FY 2020-2021.
                {taxRegime !== availableRegimes[0] && (
                  <span className="text-amber-400">
                    {' '}Your saved preference ({taxRegime}) does not apply to this year.
                  </span>
                )}
              </p>
            )}
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
                      <td className={`px-4 py-3 text-right font-medium text-sm ${g.asset.includes('loss') ? 'text-red-400' : 'text-green-400'}`}>{formatCurrency(g.amount)}</td>
                      <td className="px-4 py-3 text-right font-medium text-sm text-amber-400">
                        {formatCurrency(g.tax)}
                        {g.note && <span className="block text-[10px] text-[var(--text-muted)] font-normal mt-0.5">{g.note}</span>}
                      </td>
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
                {harvestingOpps.map((opp, idx) => {
                  // Three distinct kinds, previously conflated under one "Current Loss" label.
                  const isLoss = opp.type === 'LOSS_HARVEST';
                  const isWait = opp.type === 'WAIT_FOR_LTCG';
                  const badge = isLoss ? 'Book loss' : isWait ? 'Wait' : 'Tax-free gain';
                  const badgeClass = isLoss
                    ? 'bg-red-400/10 text-red-400'
                    : isWait
                      ? 'bg-amber-400/10 text-amber-400'
                      : 'bg-green-400/10 text-green-400';
                  return (
                    <div key={opp.holdingId || idx} className="bg-[var(--input-bg)] border border-[var(--border)] rounded-lg p-4 hover:border-blue-500 transition-colors">
                      <div className="flex items-center justify-between mb-3">
                        <span className="font-semibold text-sm">{opp.symbol}</span>
                        <span className={`px-2 py-0.5 rounded text-[10px] font-medium ${badgeClass}`}>{badge}</span>
                      </div>
                      <div className="space-y-2">
                        <div className="flex justify-between text-sm">
                          <span className="text-[var(--text-muted)]">{Number(opp.quantity ?? 0)} units</span>
                          <span className="text-[var(--text-muted)] text-xs">{opp.holdingDays}d held</span>
                        </div>
                        <div className="flex justify-between text-sm">
                          <span className="text-[var(--text-muted)]">{isLoss ? 'Unrealised Loss' : 'Unrealised Gain'}</span>
                          <span className={`font-medium ${isLoss ? 'text-red-400' : 'text-green-400'}`}>
                            {formatCurrency(isLoss ? opp.currentLoss : opp.unrealizedGain)}
                          </span>
                        </div>
                        <div className="flex justify-between text-sm">
                          <span className="text-[var(--text-muted)]">{isWait ? 'Tax If Sold Now' : 'Tax Saved'}</span>
                          <span className={`font-medium ${isWait ? 'text-amber-400' : 'text-green-400'}`}>
                            {formatCurrency(isWait ? opp.taxIfSoldNow : opp.potentialSavings)}
                          </span>
                        </div>
                        {opp.reason && (
                          <div className="flex items-start gap-1 text-xs text-blue-400 mt-2">
                            <ArrowRight className="w-3 h-3 mt-0.5 shrink-0" />
                            <span>{opp.reason}</span>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
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
                  {taxRulesForYear().map((rule, idx) => (
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
                  {Number(comparison.oldRegime?.rebate) > 0 && (
                    <p className="text-xs text-[var(--text-muted)]">Rebate u/s 87A: {formatCurrency(comparison.oldRegime.rebate)}</p>
                  )}
                  {Number(comparison.oldRegime?.marginalRelief) > 0 && (
                    <p className="text-xs text-blue-400">Marginal relief: {formatCurrency(comparison.oldRegime.marginalRelief)}</p>
                  )}
                  {Number(comparison.oldRegime?.surcharge) > 0 && (
                    <p className="text-xs text-[var(--text-muted)]">Surcharge: {formatCurrency(comparison.oldRegime.surcharge)}</p>
                  )}
                  {comparison.recommended === 'OLD' && <p className="text-xs text-green-400 mt-2">✓ Recommended</p>}
                </div>
                <div className={`p-4 rounded-lg border-2 ${comparison.recommended === 'NEW' ? 'border-green-500 bg-green-500/10' : 'border-[var(--border)]'}`}>
                  <p className="text-sm text-[var(--text-muted)] mb-2">NEW Regime</p>
                  <p className="text-2xl font-bold">{formatCurrency(comparison.newRegime?.totalTax)}</p>
                  <p className="text-xs text-[var(--text-muted)] mt-1">Taxable: {formatCurrency(comparison.newRegime?.taxableIncome)}</p>
                  {Number(comparison.newRegime?.rebate) > 0 && (
                    <p className="text-xs text-[var(--text-muted)]">Rebate u/s 87A: {formatCurrency(comparison.newRegime.rebate)}</p>
                  )}
                  {/* Relief matters here: without it, income just over the rebate threshold
                      attracts more tax than the extra income earned. */}
                  {Number(comparison.newRegime?.marginalRelief) > 0 && (
                    <p className="text-xs text-blue-400">Marginal relief: {formatCurrency(comparison.newRegime.marginalRelief)}</p>
                  )}
                  {Number(comparison.newRegime?.surcharge) > 0 && (
                    <p className="text-xs text-[var(--text-muted)]">Surcharge: {formatCurrency(comparison.newRegime.surcharge)}</p>
                  )}
                  {comparison.recommended === 'NEW' && <p className="text-xs text-green-400 mt-2">✓ Recommended</p>}
                </div>
              </div>
              <div className="p-3 rounded-lg bg-blue-500/10 border border-blue-500/30">
                <p className="text-sm">
                  💡 For <span className="font-semibold">{comparison.financialYear || selectedFY}</span>, the{' '}
                  <span className="font-semibold text-blue-400">{comparison.recommended} Regime</span> saves you
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
