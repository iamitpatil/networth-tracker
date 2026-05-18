// lib/features/networth/tax_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_networth/core/services/api_client.dart';

class TaxScreen extends StatefulWidget {
  const TaxScreen({super.key});

  @override
  State<TaxScreen> createState() => _TaxScreenState();
}

class _TaxScreenState extends State<TaxScreen> {
  Map<String, dynamic>? _summary;
  Map<String, dynamic>? _util80C;
  List<dynamic> _harvestingOpps = [];
  bool _isLoading = true;
  String? _error;
  String _selectedFY = '2024-2025';

  final List<String> _financialYears = ['2024-2025', '2023-2024', '2022-2023', '2021-2022', '2020-2021'];

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });

    try {
      final results = await Future.wait([
        ApiClient.get('/tax/summary/$_selectedFY').catchError((e) => null),
        ApiClient.get('/tax/80c-utilization').catchError((e) => null),
        ApiClient.get('/tax/harvesting-opportunities').catchError((e) => []),
      ]);

      _summary = results[0] is Map ? Map<String, dynamic>.from(results[0] as Map) : null;
      _util80C = results[1] is Map ? Map<String, dynamic>.from(results[1] as Map) : null;
      _harvestingOpps = results[2] is List ? results[2] as List : [];
    } catch (e) {
      _error = e.toString();
    } finally {
      setState(() => _isLoading = false);
    }
  }

  String _formatCurrency(double value) {
    if (value >= 10000000) return '₹${(value / 10000000).toStringAsFixed(2)}Cr';
    if (value >= 100000) return '₹${(value / 100000).toStringAsFixed(2)}L';
    if (value >= 1000) return '₹${(value / 1000).toStringAsFixed(1)}K';
    return '₹${value.toStringAsFixed(0)}';
  }

  double _safeDouble(dynamic value) {
    if (value == null) return 0;
    if (value is num) return value.toDouble();
    return 0;
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (_error != null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Tax Planning')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 64, color: Colors.red),
              const SizedBox(height: 16),
              Text('Error: $_error', textAlign: TextAlign.center),
              const SizedBox(height: 16),
              ElevatedButton(onPressed: _loadData, child: const Text('Retry')),
            ],
          ),
        ),
      );
    }

    // Extract data safely
    final equity = (_summary?['equity'] as Map?) ?? {};
    final gold = (_summary?['gold'] as Map?) ?? {};
    final crypto = (_summary?['crypto'] as Map?) ?? {};
    final realEstate = (_summary?['realEstate'] as Map?) ?? {};
    final debt = (_summary?['debt'] as Map?) ?? {};

    final ltcgEquity = _safeDouble(equity['ltcg']);
    final stcgEquity = _safeDouble(equity['stcg']);
    final taxLTCG = _safeDouble(equity['taxOnLTCG']);
    final taxSTCG = _safeDouble(equity['taxOnSTCG']);
    final goldGains = _safeDouble(gold['gains']);
    final cryptoGains = _safeDouble(crypto['gains']);
    final cryptoTax = _safeDouble(crypto['tax']);
    final realEstateGains = _safeDouble(realEstate['gains']);
    final totalTax = _safeDouble(_summary?['totalTax']);
    final cess = _safeDouble(_summary?['cess']);

    final totalLTCG = ltcgEquity + goldGains + realEstateGains;
    final totalSTCG = stcgEquity;

    final utilized80C = _safeDouble(_util80C?['utilized']);
    final limit80C = _safeDouble(_util80C?['limit']) > 0 ? _safeDouble(_util80C?['limit']) : 150000.0;
    final remaining80C = _safeDouble(_util80C?['remaining']);
    final pct80C = (utilized80C / limit80C * 100).clamp(0.0, 100.0);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Tax Planning'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _loadData),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _loadData,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Financial Year Selector
            _buildFYSelector(),
            const SizedBox(height: 16),

            // Summary Cards
            Row(
              children: [
                Expanded(
                  child: _buildSummaryCard(
                    'LTCG',
                    _formatCurrency(totalLTCG),
                    'Long-term gains',
                    Colors.green,
                    Icons.trending_up,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildSummaryCard(
                    'STCG',
                    _formatCurrency(totalSTCG),
                    'Short-term gains',
                    Colors.orange,
                    Icons.trending_up,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            _buildSummaryCard(
              'Total Tax',
              _formatCurrency(totalTax),
              'For $_selectedFY',
              Colors.red,
              Icons.receipt_long,
              fullWidth: true,
            ),
            const SizedBox(height: 20),

            // Detailed Tax Breakdown
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Tax Breakdown', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 16),
                    if (ltcgEquity > 0) _buildTaxRow('Equity LTCG', ltcgEquity, taxLTCG, Colors.green),
                    if (stcgEquity > 0) _buildTaxRow('Equity STCG', stcgEquity, taxSTCG, Colors.orange),
                    if (goldGains > 0) _buildTaxRow('Gold LTCG', goldGains, 0, Colors.amber),
                    if (cryptoGains > 0) _buildTaxRow('Crypto', cryptoGains, cryptoTax, Colors.purple),
                    if (realEstateGains > 0) _buildTaxRow('Real Estate', realEstateGains, 0, Colors.red),
                    if (ltcgEquity == 0 && stcgEquity == 0 && goldGains == 0 && cryptoGains == 0 && realEstateGains == 0)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        child: Center(
                          child: Column(
                            children: [
                              Icon(Icons.check_circle, size: 48, color: Colors.green[300]),
                              const SizedBox(height: 8),
                              Text('No realized gains for $_selectedFY', style: TextStyle(color: Colors.grey[600])),
                            ],
                          ),
                        ),
                      ),
                    if (cess > 0) ...[
                      const Divider(),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Health & Education Cess (4%)', style: TextStyle(fontSize: 13, color: Colors.grey)),
                          Text(_formatCurrency(cess), style: const TextStyle(fontWeight: FontWeight.w600)),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            // 80C Utilization
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.account_balance, color: Colors.purple[600]),
                        const SizedBox(width: 8),
                        const Text('Section 80C Deductions', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                      ],
                    ),
                    const SizedBox(height: 16),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: pct80C / 100,
                        minHeight: 10,
                        backgroundColor: Colors.grey[200],
                        valueColor: AlwaysStoppedAnimation<Color>(
                          pct80C >= 100 ? Colors.green : (pct80C >= 80 ? Colors.orange : Colors.red),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('${pct80C.toStringAsFixed(1)}% utilized', style: TextStyle(color: Colors.grey[600])),
                        Text('Limit: ₹1.5L', style: TextStyle(color: Colors.grey[600], fontSize: 12)),
                      ],
                    ),
                    const SizedBox(height: 16),

                    // Breakdown
                    if (_util80C?['breakdown'] is Map)
                      ...(_util80C!['breakdown'] as Map).entries.map((e) {
                        final amount = _safeDouble(e.value);
                        return Padding(
                          padding: const EdgeInsets.symmetric(vertical: 4),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Row(
                                children: [
                                  Icon(
                                    amount > 0 ? Icons.check_circle : Icons.circle_outlined,
                                    size: 16,
                                    color: amount > 0 ? Colors.green : Colors.grey[400],
                                  ),
                                  const SizedBox(width: 8),
                                  Text(e.key.toString(), style: const TextStyle(fontSize: 13)),
                                ],
                              ),
                              Text(_formatCurrency(amount), style: const TextStyle(fontWeight: FontWeight.w500)),
                            ],
                          ),
                        );
                      }),
                    
                    const Divider(height: 24),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text('Utilized', style: TextStyle(fontWeight: FontWeight.w600)),
                        Text(_formatCurrency(utilized80C), style: const TextStyle(fontWeight: FontWeight.bold)),
                      ],
                    ),
                    const SizedBox(height: 4),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        const Text('Remaining', style: TextStyle(fontWeight: FontWeight.w600)),
                        Text(
                          _formatCurrency(remaining80C),
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            color: remaining80C > 0 ? Colors.orange[700] : Colors.green[700],
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),

            // Tax Harvesting
            if (_harvestingOpps.isNotEmpty) ...[
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.savings, color: Colors.green[600]),
                          const SizedBox(width: 8),
                          const Text(
                            'Tax Harvesting Opportunities',
                            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                      const SizedBox(height: 12),
                      ..._harvestingOpps.map((opp) => Container(
                        margin: const EdgeInsets.only(bottom: 8),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Colors.green.withOpacity(0.05),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: Colors.green.withOpacity(0.2)),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.lightbulb_outline, color: Colors.green),
                            const SizedBox(width: 12),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    opp['symbol']?.toString() ?? '',
                                    style: const TextStyle(fontWeight: FontWeight.bold),
                                  ),
                                  if (opp['reason'] != null)
                                    Text(
                                      opp['reason'].toString(),
                                      style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                                    ),
                                ],
                              ),
                            ),
                            if (opp['unrealizedGain'] != null)
                              Text(
                                _formatCurrency(_safeDouble(opp['unrealizedGain'])),
                                style: TextStyle(fontWeight: FontWeight.bold, color: Colors.green[700]),
                              ),
                          ],
                        ),
                      )),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 20),
            ],

            // Tax Rules Reference
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Tax Rules Reference',
                      style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 12),
                    _buildRuleRow('Equity LTCG', '12.5% above ₹1.25L', '> 1 year holding'),
                    _buildRuleRow('Equity STCG', '20% flat', '≤ 1 year holding'),
                    _buildRuleRow('Debt Funds', 'As per slab', 'Indexation removed'),
                    _buildRuleRow('Crypto/NFT', '30% flat + 4% cess', 'No deductions'),
                    _buildRuleRow('80C Limit', 'Max ₹1.5L', 'ELSS, PPF, EPF, etc.'),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildFYSelector() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.blue.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.calendar_today, color: Colors.blue),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Financial Year', style: TextStyle(fontSize: 12, color: Colors.grey[600])),
                DropdownButton<String>(
                  value: _selectedFY,
                  isDense: true,
                  underline: const SizedBox.shrink(),
                  items: _financialYears.map((fy) => DropdownMenuItem(
                    value: fy,
                    child: Text(fy, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                  )).toList(),
                  onChanged: (value) {
                    if (value != null) {
                      setState(() => _selectedFY = value);
                      _loadData();
                    }
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSummaryCard(String title, String value, String subtitle, Color color, IconData icon, {bool fullWidth = false}) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: fullWidth
            ? Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(color: color.withOpacity(0.1), shape: BoxShape.circle),
                    child: Icon(icon, color: color),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(title, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
                        Text(value, style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: color)),
                        Text(subtitle, style: TextStyle(fontSize: 11, color: Colors.grey[500])),
                      ],
                    ),
                  ),
                ],
              )
            : Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(icon, color: color, size: 20),
                      const SizedBox(width: 6),
                      Text(title, style: TextStyle(color: color, fontWeight: FontWeight.w600)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(value, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 4),
                  Text(subtitle, style: TextStyle(fontSize: 11, color: Colors.grey[600])),
                ],
              ),
      ),
    );
  }

  Widget _buildTaxRow(String label, double gains, double tax, Color color) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(width: 10, height: 10, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
                  const SizedBox(width: 8),
                  Text(label, style: const TextStyle(fontWeight: FontWeight.w600)),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(_formatCurrency(gains), style: const TextStyle(fontWeight: FontWeight.bold)),
                  Text('Tax: ${_formatCurrency(tax)}', style: TextStyle(fontSize: 11, color: Colors.grey[600])),
                ],
              ),
            ],
          ),
          const Divider(height: 16),
        ],
      ),
    );
  }

  Widget _buildRuleRow(String asset, String rate, String notes) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Expanded(
            flex: 2,
            child: Text(asset, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
          ),
          Expanded(
            flex: 2,
            child: Text(rate, style: TextStyle(color: Colors.blue[700], fontSize: 13, fontWeight: FontWeight.w500)),
          ),
          Expanded(
            flex: 3,
            child: Text(notes, style: TextStyle(fontSize: 11, color: Colors.grey[600])),
          ),
        ],
      ),
    );
  }
}
