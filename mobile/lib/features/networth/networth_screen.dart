// lib/features/networth/networth_screen.dart
import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:provider/provider.dart';
import 'package:flutter_networth/providers/data_provider.dart';
import 'package:flutter_networth/data/models/app_models.dart';
import 'package:flutter_networth/widgets/charts/asset_allocation_chart.dart';

class NetWorthScreen extends StatefulWidget {
  const NetWorthScreen({super.key});

  @override
  State<NetWorthScreen> createState() => _NetWorthScreenState();
}

class _NetWorthScreenState extends State<NetWorthScreen> {
  List<Map<String, dynamic>> _netWorthHistory = [];
  bool _historyLoading = true;
  String? _historyError;
  int _selectedDays = 90;

  final List<Map<String, dynamic>> _periods = [
    {'days': 30, 'label': '30D'},
    {'days': 90, 'label': '90D'},
    {'days': 365, 'label': '1Y'},
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<DataProvider>().loadDashboardData();
      _loadNetWorthHistory();
    });
  }

  Future<void> _loadNetWorthHistory() async {
    setState(() {
      _historyLoading = true;
      _historyError = null;
    });
    try {
      final data = await context.read<DataProvider>().loadNetWorthHistory(_selectedDays);
      setState(() {
        _netWorthHistory = List<Map<String, dynamic>>.from(data);
      });
    } catch (e) {
      setState(() => _historyError = e.toString());
    } finally {
      setState(() => _historyLoading = false);
    }
  }

  String _formatCurrency(double value) {
    if (value >= 10000000) {
      return '₹${(value / 10000000).toStringAsFixed(2)}Cr';
    } else if (value >= 100000) {
      return '₹${(value / 100000).toStringAsFixed(2)}L';
    } else if (value >= 1000) {
      return '₹${(value / 1000).toStringAsFixed(1)}K';
    } else {
      return '₹${value.toStringAsFixed(0)}';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<DataProvider>(
      builder: (context, provider, child) {
        final data = provider.netWorthData;
        final healthScore = provider.healthScoreData;

        if (provider.isLoading && data == null) {
          return const Center(child: CircularProgressIndicator());
        }

        if (provider.error != null && data == null) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 64, color: Colors.red),
                const SizedBox(height: 16),
                Text('Error: ${provider.error}'),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () => provider.loadDashboardData(),
                  child: const Text('Retry'),
                ),
              ],
            ),
          );
        }

        if (data == null) {
          return const Center(child: Text('No data available'));
        }

        return RefreshIndicator(
          onRefresh: () => provider.loadDashboardData(),
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Net Worth Header
                Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF3B82F6), Color(0xFF2563EB)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Column(
                    children: [
                      const Text(
                        'Total Net Worth',
                        style: TextStyle(
                          color: Colors.white70,
                          fontSize: 16,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        _formatCurrency(data.netWorth),
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 42,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 20),
                      Row(
                        children: [
                          Expanded(
                            child: Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: Colors.white.withOpacity(0.15),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Column(
                                children: [
                                  const Icon(Icons.arrow_upward, color: Colors.white),
                                  const SizedBox(height: 4),
                                  const Text(
                                    'Assets',
                                    style: TextStyle(
                                      color: Colors.white70,
                                      fontSize: 12,
                                    ),
                                  ),
                                  Text(
                                    _formatCurrency(data.totalAssets),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 18,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: Colors.white.withOpacity(0.15),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Column(
                                children: [
                                  const Icon(Icons.arrow_downward, color: Colors.white),
                                  const SizedBox(height: 4),
                                  const Text(
                                    'Liabilities',
                                    style: TextStyle(
                                      color: Colors.white70,
                                      fontSize: 12,
                                    ),
                                  ),
                                  Text(
                                    _formatCurrency(data.totalLiabilities),
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 18,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                
                const SizedBox(height: 24),
                
                // Health Score Card
                if (healthScore != null)
                  _buildHealthScoreCard(healthScore),
                
                const SizedBox(height: 24),

                // Net Worth Trend Chart
                _buildNetWorthTrendCard(),

                const SizedBox(height: 24),
                
                // Asset Allocation Chart
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text(
                          'Asset Allocation',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        const SizedBox(height: 16),
                        AssetAllocationChart(data: data.assetAllocation),
                      ],
                    ),
                  ),
                ),
                
                const SizedBox(height: 24),
                
                // Asset Breakdown
                const Text(
                  'Asset Breakdown',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 16),
                
                ..._buildAssetCategories(data),
                
                const SizedBox(height: 24),
                
                // Liabilities Section
                if (provider.liabilities.isNotEmpty) ...[
                  const Text(
                    'Liabilities',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 16),
                  ...provider.liabilities.map((l) => _buildLiabilityCard(l)),
                ],
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildHealthScoreCard(HealthScoreData healthScore) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              width: 70,
              height: 70,
              decoration: BoxDecoration(
                color: Color(healthScore.color).withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      healthScore.grade,
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: Color(healthScore.color),
                      ),
                    ),
                    Text(
                      '${healthScore.score}',
                      style: TextStyle(
                        fontSize: 12,
                        color: Color(healthScore.color),
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Financial Health Score',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    healthScore.status,
                    style: TextStyle(
                      fontSize: 14,
                      color: Color(healthScore.color),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _formatLakhs(double value) {
    if (value >= 10000000) {
      return '${(value / 10000000).toStringAsFixed(1)}Cr';
    } else if (value >= 100000) {
      return '${(value / 100000).toStringAsFixed(1)}L';
    } else if (value >= 1000) {
      return '${(value / 1000).toStringAsFixed(0)}K';
    } else {
      return value.toStringAsFixed(0);
    }
  }

  Widget _buildNetWorthTrendCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Net Worth Trend',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),
            // Period Selector
            Row(
              children: _periods.map((period) {
                final isSelected = _selectedDays == period['days'];
                return Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: GestureDetector(
                    onTap: () {
                      setState(() => _selectedDays = period['days']);
                      _loadNetWorthHistory();
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                      decoration: BoxDecoration(
                        color: isSelected ? Colors.blue : Colors.grey[200],
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(
                        period['label'],
                        style: TextStyle(
                          color: isSelected ? Colors.white : Colors.grey[700],
                          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
            const SizedBox(height: 16),
            // Chart area
            SizedBox(
              height: 200,
              child: _historyLoading
                  ? const Center(child: CircularProgressIndicator())
                  : _historyError != null
                      ? Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.error_outline, size: 32, color: Colors.red),
                              const SizedBox(height: 8),
                              Text(
                                'Failed to load history',
                                style: TextStyle(color: Colors.grey[600], fontSize: 13),
                              ),
                              const SizedBox(height: 8),
                              TextButton(
                                onPressed: _loadNetWorthHistory,
                                child: const Text('Retry'),
                              ),
                            ],
                          ),
                        )
                      : _netWorthHistory.isEmpty
                          ? Center(
                              child: Text(
                                'No history data available',
                                style: TextStyle(color: Colors.grey[500]),
                              ),
                            )
                          : _buildNetWorthChart(),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNetWorthChart() {
    final spots = _netWorthHistory.asMap().entries.map((entry) {
      return FlSpot(
        entry.key.toDouble(),
        (entry.value['netWorth'] ?? 0).toDouble(),
      );
    }).toList();

    final values = spots.map((s) => s.y).toList();
    final minY = values.reduce((a, b) => a < b ? a : b);
    final maxY = values.reduce((a, b) => a > b ? a : b);
    final range = maxY - minY;
    final chartMinY = range > 0 ? minY - range * 0.05 : minY * 0.95;
    final chartMaxY = range > 0 ? maxY + range * 0.05 : maxY * 1.05;
    final yInterval = range > 0 ? range / 4 : maxY / 4;

    return LineChart(
      LineChartData(
        minY: chartMinY,
        maxY: chartMaxY,
        lineBarsData: [
          LineChartBarData(
            spots: spots,
            isCurved: true,
            color: Colors.blue,
            barWidth: 2.5,
            dotData: const FlDotData(show: false),
            belowBarData: BarAreaData(
              show: true,
              gradient: LinearGradient(
                colors: [
                  Colors.blue.withOpacity(0.3),
                  Colors.blue.withOpacity(0.0),
                ],
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
              ),
            ),
          ),
        ],
        gridData: FlGridData(
          show: true,
          drawVerticalLine: false,
          horizontalInterval: yInterval > 0 ? yInterval : 1,
          getDrawingHorizontalLine: (value) => FlLine(
            color: Colors.grey[200]!,
            strokeWidth: 1,
          ),
        ),
        titlesData: FlTitlesData(
          topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          leftTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 48,
              interval: yInterval > 0 ? yInterval : null,
              getTitlesWidget: (value, meta) {
                if (value == meta.min || value == meta.max) {
                  return const SizedBox.shrink();
                }
                return Padding(
                  padding: const EdgeInsets.only(right: 4),
                  child: Text(
                    _formatLakhs(value),
                    style: TextStyle(fontSize: 10, color: Colors.grey[600]),
                  ),
                );
              },
            ),
          ),
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              reservedSize: 30,
              interval: (_netWorthHistory.length / 5).ceilToDouble(),
              getTitlesWidget: (value, meta) {
                final idx = value.toInt();
                if (idx < 0 || idx >= _netWorthHistory.length) {
                  return const SizedBox.shrink();
                }
                final date = _netWorthHistory[idx]['date']?.toString() ?? '';
                final parts = date.split('-');
                if (parts.length >= 3) {
                  return Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      '${parts[2]}/${parts[1]}',
                      style: TextStyle(fontSize: 10, color: Colors.grey[600]),
                    ),
                  );
                }
                return const SizedBox.shrink();
              },
            ),
          ),
        ),
        borderData: FlBorderData(
          show: true,
          border: Border(
            bottom: BorderSide(color: Colors.grey[300]!),
            left: BorderSide(color: Colors.grey[300]!),
          ),
        ),
        lineTouchData: LineTouchData(
          enabled: true,
          touchTooltipData: LineTouchTooltipData(
            tooltipBgColor: Colors.black87,
            getTooltipItems: (touchedSpots) {
              return touchedSpots.map((spot) {
                final idx = spot.x.toInt();
                if (idx < 0 || idx >= _netWorthHistory.length) {
                  return const LineTooltipItem('', TextStyle());
                }
                final point = _netWorthHistory[idx];
                return LineTooltipItem(
                  '${point['date']}\n₹${_formatLakhs(spot.y)}',
                  const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                );
              }).toList();
            },
          ),
        ),
      ),
    );
  }

  List<Widget> _buildAssetCategories(NetWorthData data) {
    final categories = [
      {'name': 'Equity', 'value': data.equityValue, 'color': const Color(0xFF3B82F6), 'icon': Icons.show_chart},
      {'name': 'Mutual Funds', 'value': data.mutualFundValue, 'color': const Color(0xFF10B981), 'icon': Icons.account_balance},
      {'name': 'Gold', 'value': data.goldValue, 'color': const Color(0xFFF59E0B), 'icon': Icons.monetization_on},
      {'name': 'Real Estate', 'value': data.realEstateValue, 'color': const Color(0xFFEF4444), 'icon': Icons.home},
      {'name': 'Cash', 'value': data.cashValue, 'color': const Color(0xFF8B5CF6), 'icon': Icons.account_balance_wallet},
      {'name': 'Crypto', 'value': data.cryptoValue, 'color': const Color(0xFFEC4899), 'icon': Icons.currency_bitcoin},
      {'name': 'Fixed Deposit', 'value': data.fdValue, 'color': const Color(0xFF8B5CF6), 'icon': Icons.lock_clock},
      {'name': 'PPF', 'value': data.ppfValue, 'color': const Color(0xFF14B8A6), 'icon': Icons.savings},
      {'name': 'EPF', 'value': data.epfValue, 'color': const Color(0xFFF97316), 'icon': Icons.work},
      {'name': 'NPS', 'value': data.npsValue, 'color': const Color(0xFF06B6D4), 'icon': Icons.account_balance_wallet},
    ].where((item) => (item['value'] as double) > 0).toList();

    final total = data.totalAssets;

    return categories.map((category) {
      final percentage = total > 0 
          ? ((category['value'] as double) / total * 100).toStringAsFixed(1)
          : '0.0';
      
      return Card(
        margin: const EdgeInsets.only(bottom: 12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: (category['color'] as Color).withOpacity(0.1),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  category['icon'] as IconData,
                  color: category['color'] as Color,
                ),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      category['name'] as String,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 16,
                      ),
                    ),
                    const SizedBox(height: 8),
                    ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: LinearProgressIndicator(
                        value: total > 0 ? (category['value'] as double) / total : 0,
                        minHeight: 6,
                        backgroundColor: Colors.grey[200],
                        valueColor: AlwaysStoppedAnimation<Color>(category['color'] as Color),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    _formatCurrency(category['value'] as double),
                    style: const TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 16,
                    ),
                  ),
                  Text(
                    '$percentage%',
                    style: TextStyle(
                      color: Colors.grey[600],
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      );
    }).toList();
  }

  Widget _buildLiabilityCard(Liability liability) {
    final progress = liability.progressPercentage / 100;
    
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: Colors.red.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.money_off, color: Colors.red),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        liability.lender,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      Text(
                        liability.liabilityTypeLabel,
                        style: TextStyle(
                          color: Colors.grey[600],
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: progress,
                minHeight: 8,
                backgroundColor: Colors.grey[200],
                valueColor: AlwaysStoppedAnimation<Color>(
                  progress >= 0.75 ? Colors.green : 
                  progress >= 0.5 ? Colors.blue : 
                  progress >= 0.25 ? Colors.orange : Colors.red,
                ),
              ),
            ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '${liability.progressPercentage.toStringAsFixed(1)}% paid',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
                Text(
                  _formatCurrency(liability.outstandingAmount),
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
