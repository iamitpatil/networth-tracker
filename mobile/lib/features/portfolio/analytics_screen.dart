// lib/features/portfolio/analytics_screen.dart
import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:flutter_networth/core/services/api_client.dart';

class AnalyticsScreen extends StatefulWidget {
  const AnalyticsScreen({super.key});

  @override
  State<AnalyticsScreen> createState() => _AnalyticsScreenState();
}

class _AnalyticsScreenState extends State<AnalyticsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  Map<String, dynamic>? _xirr;
  Map<String, dynamic>? _cagr;
  Map<String, dynamic>? _risk;
  List<dynamic>? _allocation;
  List<dynamic>? _sectorAllocation;
  Map<String, dynamic>? _sipData;
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final results = await Future.wait([
        ApiClient.get('/analytics/xirr').catchError((e) => null),
        ApiClient.get('/analytics/cagr').catchError((e) => null),
        ApiClient.get('/analytics/risk').catchError((e) => null),
        ApiClient.get('/analytics/allocation').catchError((e) => null),
        ApiClient.get('/analytics/allocation/sector').catchError((e) => null),
        ApiClient.get('/analytics/sip-calendar').catchError((e) => null),
      ]);
      _xirr = results[0] as Map<String, dynamic>?;
      _cagr = results[1] as Map<String, dynamic>?;
      _risk = results[2] as Map<String, dynamic>?;
      _allocation = results[3] as List<dynamic>?;
      _sectorAllocation = results[4] as List<dynamic>?;
      _sipData = results[5] as Map<String, dynamic>?;
    } catch (e) {
      // Ignore - show empty state
    } finally {
      setState(() => _isLoading = false);
    }
  }

  String _formatPct(dynamic value) {
    if (value == null) return '0%';
    return '${(value as num).toStringAsFixed(2)}%';
  }

  String _formatCurrency(double value) {
    if (value >= 10000000) return '₹${(value / 10000000).toStringAsFixed(2)}Cr';
    if (value >= 100000) return '₹${(value / 100000).toStringAsFixed(2)}L';
    if (value >= 1000) return '₹${(value / 1000).toStringAsFixed(1)}K';
    return '₹${value.toStringAsFixed(0)}';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Analytics'),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(text: 'Returns', icon: Icon(Icons.trending_up, size: 18)),
            Tab(text: 'Allocation', icon: Icon(Icons.pie_chart, size: 18)),
            Tab(text: 'SIP', icon: Icon(Icons.calendar_today, size: 18)),
          ],
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : TabBarView(
              controller: _tabController,
              children: [
                _buildReturnsTab(),
                _buildAllocationTab(),
                _buildSipTab(),
              ],
            ),
    );
  }

  Widget _buildReturnsTab() {
    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Performance Metrics
          const Text('Performance', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(child: _buildMetricCard('XIRR', _formatPct(_xirr?['xirr']), Colors.blue, Icons.show_chart)),
              const SizedBox(width: 12),
              Expanded(child: _buildMetricCard('CAGR', _formatPct(_cagr?['cagr']), Colors.green, Icons.trending_up)),
            ],
          ),
          const SizedBox(height: 24),
          
          // Risk Metrics
          const Text('Risk Metrics', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(child: _buildMetricCard('Volatility', _formatPct(_risk?['volatility']), Colors.orange, Icons.show_chart)),
              const SizedBox(width: 12),
              Expanded(child: _buildMetricCard('Sharpe', '${(_risk?['sharpeRatio'] ?? 0).toStringAsFixed(2)}', Colors.purple, Icons.bar_chart)),
            ],
          ),
          const SizedBox(height: 12),
          _buildMetricCard('Max Drawdown', _formatPct(_risk?['maxDrawdown']), Colors.red, Icons.trending_down, fullWidth: true),
          
          const SizedBox(height: 24),
          
          // Info card
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.info_outline, color: Colors.blue[600]),
                      const SizedBox(width: 8),
                      const Text('About These Metrics', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  _buildInfoRow('XIRR', 'Time-weighted return considering cash flows'),
                  _buildInfoRow('CAGR', 'Compound Annual Growth Rate'),
                  _buildInfoRow('Volatility', 'Standard deviation of returns'),
                  _buildInfoRow('Sharpe Ratio', 'Risk-adjusted return (higher is better)'),
                  _buildInfoRow('Max Drawdown', 'Largest peak-to-trough decline'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAllocationTab() {
    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Asset Allocation Pie Chart
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Asset Allocation', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 16),
                  if (_allocation == null || _allocation!.isEmpty)
                    const Center(child: Padding(padding: EdgeInsets.all(20), child: Text('No data available')))
                  else
                    SizedBox(
                      height: 250,
                      child: PieChart(
                        PieChartData(
                          sections: _allocation!.asMap().entries.map((entry) {
                            final colors = [Colors.blue, Colors.green, Colors.orange, Colors.purple, Colors.red, Colors.cyan];
                            final color = colors[entry.key % colors.length];
                            final pct = (entry.value['percentage'] ?? 0).toDouble();
                            return PieChartSectionData(
                              color: color,
                              value: pct,
                              title: '${pct.toStringAsFixed(1)}%',
                              radius: 80,
                              titleStyle: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12),
                            );
                          }).toList(),
                          sectionsSpace: 2,
                          centerSpaceRadius: 40,
                        ),
                      ),
                    ),
                  if (_allocation != null && _allocation!.isNotEmpty) ...[
                    const SizedBox(height: 16),
                    ..._allocation!.asMap().entries.map((entry) {
                      final colors = [Colors.blue, Colors.green, Colors.orange, Colors.purple, Colors.red, Colors.cyan];
                      final color = colors[entry.key % colors.length];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Row(
                          children: [
                            Container(width: 12, height: 12, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
                            const SizedBox(width: 8),
                            Expanded(child: Text(entry.value['assetType']?.toString() ?? '')),
                            Text(_formatCurrency((entry.value['value'] ?? 0).toDouble())),
                            const SizedBox(width: 8),
                            Text('${(entry.value['percentage'] ?? 0).toStringAsFixed(1)}%', style: const TextStyle(fontWeight: FontWeight.bold)),
                          ],
                        ),
                      );
                    }),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          
          // Sector Allocation
          if (_sectorAllocation != null && _sectorAllocation!.isNotEmpty)
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('Sector Allocation', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 12),
                    ..._sectorAllocation!.map((s) => Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(s['sector']?.toString() ?? '', style: const TextStyle(fontWeight: FontWeight.w600)),
                              Text('${(s['percentage'] ?? 0).toStringAsFixed(1)}%'),
                            ],
                          ),
                          const SizedBox(height: 4),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: LinearProgressIndicator(
                              value: ((s['percentage'] ?? 0) / 100).clamp(0.0, 1.0),
                              minHeight: 6,
                              backgroundColor: Colors.grey[200],
                              valueColor: const AlwaysStoppedAnimation<Color>(Colors.blue),
                            ),
                          ),
                        ],
                      ),
                    )),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildSipTab() {
    final sipList = _sipData?['sipList'] as List? ?? [];
    final monthlySIP = (_sipData?['totalMonthlySIP'] ?? 0).toDouble();
    final upcoming = sipList.where((s) => s['status'] == 'UPCOMING').length;
    final missed = sipList.where((s) => s['status'] == 'MISSED').length;

    return RefreshIndicator(
      onRefresh: _loadData,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Summary
          Card(
            color: Colors.purple[50],
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                children: [
                  const Text('Monthly SIP', style: TextStyle(fontSize: 14, color: Colors.grey)),
                  const SizedBox(height: 8),
                  Text(_formatCurrency(monthlySIP), style: const TextStyle(fontSize: 28, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      _buildSipStat('Upcoming', upcoming.toString(), Colors.green),
                      _buildSipStat('Missed', missed.toString(), Colors.red),
                      _buildSipStat('Total', sipList.length.toString(), Colors.blue),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          
          if (sipList.isEmpty)
            const Padding(
              padding: EdgeInsets.all(32),
              child: Center(child: Text('No SIPs configured', style: TextStyle(color: Colors.grey))),
            )
          else
            ...sipList.map((sip) => Card(
              margin: const EdgeInsets.only(bottom: 8),
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: _getSipStatusColor(sip['status']).withOpacity(0.2),
                  child: Icon(Icons.repeat, color: _getSipStatusColor(sip['status'])),
                ),
                title: Text(sip['symbol']?.toString() ?? ''),
                subtitle: Text('Next: ${sip['nextSIPDate']?.toString().split('T')[0] ?? 'N/A'}'),
                trailing: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(_formatCurrency(((sip['amount'] ?? 0) as num).toDouble()), style: const TextStyle(fontWeight: FontWeight.bold)),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: _getSipStatusColor(sip['status']).withOpacity(0.1),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        sip['status']?.toString() ?? '',
                        style: TextStyle(fontSize: 10, color: _getSipStatusColor(sip['status']), fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ),
              ),
            )),
        ],
      ),
    );
  }

  Widget _buildMetricCard(String label, String value, Color color, IconData icon, {bool fullWidth = false}) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: color.withOpacity(0.1), shape: BoxShape.circle),
              child: Icon(icon, color: color),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
                const SizedBox(height: 4),
                Text(value, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color)),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String description) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(width: 90, child: Text(label, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13))),
          Expanded(child: Text(description, style: TextStyle(fontSize: 13, color: Colors.grey[700]))),
        ],
      ),
    );
  }

  Widget _buildSipStat(String label, String value, Color color) {
    return Column(
      children: [
        Text(value, style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: color)),
        Text(label, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
      ],
    );
  }

  Color _getSipStatusColor(String? status) {
    switch (status) {
      case 'UPCOMING': return Colors.green;
      case 'MISSED': return Colors.red;
      case 'COMPLETED': return Colors.blue;
      default: return Colors.grey;
    }
  }
}
