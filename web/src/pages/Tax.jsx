import React, { useState, useEffect } from 'react';
import { Calendar, TrendingDown, TrendingUp, DollarSign, Shield, Lightbulb, FileText, ChevronDown, ArrowRight } from 'lucide-react';
import client from '../api/client';

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

  useEffect(() => {
    fetchTaxData();
  }, [selectedFY]);

  const fetchTaxData = async () => {
    setLoading(true);
    try {
      const [summaryRes, harvestingRes, util80CRes] = await Promise.all([
        client.get(`/tax/summary/${selectedFY}`),
        client.get('/tax/harvesting-opportunities'),
        client.get('/tax/80c-utilization'),
      ]);

      setSummary(summaryRes.data);
      setHarvestingOpps(harvestingRes.data || []);
      setUtil80C(util80CRes.data);
    } catch (error) {
      console.error('Error fetching tax data:', error);
    } finally {
      setLoading(false);
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
          <p className="text-slate-400 text-sm mt-1">Manage your tax liability and optimize deductions</p>
        </div>
        <div className="relative">
          <button
            onClick={() => setDropdownOpen(!dropdownOpen)}
            className="flex items-center gap-2 bg-slate-700 border border-slate-600 rounded-lg px-4 py-2 hover:border-blue-500 transition-colors"
          >
            <Calendar className="w-4 h-4 text-blue-400" />
            <span className="font-medium text-sm">{selectedFY}</span>
            <ChevronDown className={`w-4 h-4 text-slate-400 transition-transform ${dropdownOpen ? 'rotate-180' : ''}`} />
          </button>
          {dropdownOpen && (
            <div className="absolute right-0 mt-2 w-44 bg-slate-700 border border-slate-600 rounded-lg shadow-xl z-10 overflow-hidden">
              {FINANCIAL_YEARS.map((fy) => (
                <button
                  key={fy}
                  onClick={() => { setSelectedFY(fy); setDropdownOpen(false); }}
                  className={`w-full text-left px-4 py-2 text-sm hover:bg-slate-600 transition-colors ${selectedFY === fy ? 'text-blue-400 bg-slate-600/50' : 'text-slate-300'}`}
                >
                  {fy}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center h-64 text-slate-400">Loading tax data...</div>
      ) : (
        <>
          {/* Summary Cards */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
              <p className="text-slate-400 text-sm">Total LTCG</p>
              <p className="text-2xl font-bold text-green-400 mt-1">{formatCurrency(totalLTCG)}</p>
            </div>
            <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
              <p className="text-slate-400 text-sm">Total STCG</p>
              <p className="text-2xl font-bold text-red-400 mt-1">{formatCurrency(totalSTCG)}</p>
            </div>
            <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
              <p className="text-slate-400 text-sm">Tax Liability</p>
              <p className="text-2xl font-bold text-amber-400 mt-1">{formatCurrency(totalTax)}</p>
            </div>
            <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
              <p className="text-slate-400 text-sm">80C Utilized</p>
              <p className="text-2xl font-bold text-blue-400 mt-1">{formatCurrency(util80C?.utilized)}</p>
            </div>
          </div>

          {/* 80C Utilization */}
          <div className="bg-slate-800 rounded-xl p-6 border border-slate-700">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <Shield className="w-5 h-5 text-blue-400" />
                80C Deduction Utilization
              </h2>
              <span className="text-sm text-slate-400">{formatCurrency(util80C?.utilized)} / ₹1,50,000</span>
            </div>
            <div className="w-full bg-slate-700 rounded-full h-3 mb-2">
              <div className="h-3 rounded-full transition-all duration-500" style={{ width: `${getUtilizationPercent()}%`, backgroundColor: getBarColor(getUtilizationPercent()) }} />
            </div>
            <div className="flex justify-between text-xs text-slate-400">
              <span>{getUtilizationPercent().toFixed(1)}% utilized</span>
              <span>{formatCurrency(remaining80C)} remaining</span>
            </div>
            {util80C?.breakdown && (
              <div className="flex gap-4 mt-4">
                {Object.entries(util80C.breakdown).map(([key, value]) => (
                  value > 0 && (
                    <div key={key} className="text-sm">
                      <span className="text-slate-400">{key}:</span>
                      <span className="text-slate-200 ml-1 font-medium">{formatCurrency(value)}</span>
                    </div>
                  )
                ))}
              </div>
            )}
          </div>

          {/* Capital Gains Table */}
          <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-700">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <FileText className="w-5 h-5 text-blue-400" />
                Capital Gains Details
              </h2>
            </div>
            <table className="w-full">
              <thead className="bg-slate-700/50 text-left">
                <tr>
                  <th className="px-4 py-3 text-sm font-medium text-slate-400">Asset</th>
                  <th className="px-4 py-3 text-sm font-medium text-slate-400">Type</th>
                  <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Gain Amount</th>
                  <th className="px-4 py-3 text-sm font-medium text-slate-400 text-right">Tax</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-700">
                {gainDetails.length === 0 ? (
                  <tr><td colSpan="4" className="px-4 py-12 text-center text-slate-500">No capital gains for this financial year</td></tr>
                ) : (
                  gainDetails.map((g, idx) => (
                    <tr key={idx} className="hover:bg-slate-700/30">
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

          {/* Tax Harvesting Opportunities */}
          <div className="bg-slate-800 rounded-xl p-6 border border-slate-700">
            <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
              <Lightbulb className="w-5 h-5 text-amber-400" />
              Tax Harvesting Opportunities
            </h2>
            {harvestingOpps.length === 0 ? (
              <p className="text-slate-500 text-center py-8">No harvesting opportunities available</p>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {harvestingOpps.map((opp, idx) => (
                  <div key={idx} className="bg-slate-700/50 border border-slate-700 rounded-lg p-4 hover:border-blue-500 transition-colors">
                    <div className="flex items-center justify-between mb-3">
                      <span className="font-semibold text-sm">{opp.symbol}</span>
                      <span className="text-xs text-slate-400">{opp.quantity} shares</span>
                    </div>
                    <div className="space-y-2">
                      <div className="flex justify-between text-sm">
                        <span className="text-slate-400">Current Loss</span>
                        <span className="text-red-400 font-medium">{formatCurrency(opp.currentLoss)}</span>
                      </div>
                      <div className="flex justify-between text-sm">
                        <span className="text-slate-400">Potential Savings</span>
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
          <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-700">
              <h2 className="text-lg font-semibold flex items-center gap-2">
                <FileText className="w-5 h-5 text-blue-400" />
                Tax Rules Reference
              </h2>
            </div>
            <table className="w-full">
              <thead className="bg-slate-700/50 text-left">
                <tr>
                  <th className="px-4 py-3 text-sm font-medium text-slate-400">Asset Type</th>
                  <th className="px-4 py-3 text-sm font-medium text-slate-400">Tax Rate</th>
                  <th className="px-4 py-3 text-sm font-medium text-slate-400">Threshold</th>
                  <th className="px-4 py-3 text-sm font-medium text-slate-400">Notes</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-700">
                {TAX_RULES.map((rule, idx) => (
                  <tr key={idx} className="hover:bg-slate-700/30">
                    <td className="px-4 py-3 font-medium text-sm">{rule.asset}</td>
                    <td className="px-4 py-3"><span className="px-2 py-1 rounded-full text-xs font-medium bg-blue-400/10 text-blue-400">{rule.rate}</span></td>
                    <td className="px-4 py-3 text-slate-400 text-sm">{rule.threshold}</td>
                    <td className="px-4 py-3 text-slate-400 text-sm">{rule.notes}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
