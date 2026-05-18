// lib/widgets/charts/holding_price_chart.dart
import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import '../../services/api_client.dart';
import '../../models/app_models.dart';

class HoldingPriceChartDialog extends StatefulWidget {
  final Holding holding;

  const HoldingPriceChartDialog({super.key, required this.holding});

  @override
  State<HoldingPriceChartDialog> createState() => _HoldingPriceChartDialogState();
}

class _HoldingPriceChartDialogState extends State<HoldingPriceChartDialog> {
  List<Map<String, dynamic>> _priceHistory = [];
  bool _isLoading = true;
  int _selectedDays = 90;
  String? _error;

  final List<Map<String, dynamic>> _periods = [
    {'days': 7, 'label': '1W'},
    {'days': 30, 'label': '1M'},
    {'days': 90, 'label': '3M'},
    {'days': 180, 'label': '6M'},
    {'days': 365, 'label': '1Y'},
    {'days': 1825, 'label': '5Y'},
  ];

  @override
  void initState() {
    super.initState();
    _loadPriceHistory();
  }

  Future<void> _loadPriceHistory() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final response = await ApiClient.get(
        '/portfolio/holdings/${widget.holding.id}/price-history?days=$_selectedDays',
      );
      if (response is List) {
        setState(() {
          _priceHistory = List<Map<String, dynamic>>.from(response);
        });
      }
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Map<String, dynamic>? _calculateStats() {
    if (_priceHistory.isEmpty) return null;

    final prices = _priceHistory.map((p) => (p['close'] ?? 0).toDouble()).toList();
    final high = prices.reduce((a, b) => a > b ? a : b);
    final low = prices.reduce((a, b) => a < b ? a : b);
    final avg = prices.reduce((a, b) => a + b) / prices.length;
    final first = prices.first;
    final last = prices.last;
    final change = last - first;
    final changePct = first != 0 ? (change / first * 100) : 0;
    final isUp = change >= 0;

    return {
      'high': high,
      'low': low,
      'avg': avg,
      'change': change,
      'changePct': changePct,
      'isUp': isUp,
    };
  }

  String _formatPrice(double value) {
    return '₹${value.toStringAsFixed(2)}';
  }

  @override
  Widget build(BuildContext context) {
    final stats = _calculateStats();
    final lineColor = stats?['isUp'] == true ? Colors.green : Colors.red;
    final fillColor = lineColor.withOpacity(0.2);

    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Container(
        width: MediaQuery.of(context).size.width * 0.95,
        constraints: const BoxConstraints(maxWidth: 700, maxHeight: 700),
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header
            Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: Color(widget.holding.assetTypeColor).withOpacity(0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    _getTypeIcon(widget.holding.assetType),
                    color: Color(widget.holding.assetTypeColor),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.holding.symbol,
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                      ),
                      if (widget.holding.name != null)
                        Text(
                          widget.holding.name!,
                          style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => Navigator.pop(context),
                  icon: const Icon(Icons.close),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Period Selector
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: _periods.map((period) {
                  final isSelected = _selectedDays == period['days'];
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: GestureDetector(
                      onTap: () {
                        setState(() => _selectedDays = period['days']);
                        _loadPriceHistory();
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
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 16),

            // Chart Body
            Expanded(
              child: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : _error != null
                      ? _buildErrorView()
                      : _priceHistory.isEmpty
                          ? _buildEmptyView()
                          : _buildChartContent(stats!, lineColor, fillColor),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorView() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.error_outline, size: 48, color: Colors.red),
          const SizedBox(height: 12),
          Text(_error ?? 'Error loading chart', textAlign: TextAlign.center),
          const SizedBox(height: 12),
          ElevatedButton(onPressed: _loadPriceHistory, child: const Text('Retry')),
        ],
      ),
    );
  }

  Widget _buildEmptyView() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.show_chart, size: 64, color: Colors.grey[300]),
          const SizedBox(height: 16),
          const Text('No price history available', style: TextStyle(color: Colors.grey)),
          const SizedBox(height: 8),
          Text(
            'Try refreshing prices for this holding',
            style: TextStyle(color: Colors.grey[500], fontSize: 12),
          ),
        ],
      ),
    );
  }

  Widget _buildChartContent(Map<String, dynamic> stats, Color lineColor, Color fillColor) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Stats Row
        Row(
          children: [
            Expanded(child: _buildStatBox('High', _formatPrice(stats['high']), Colors.green)),
            const SizedBox(width: 8),
            Expanded(child: _buildStatBox('Low', _formatPrice(stats['low']), Colors.red)),
            const SizedBox(width: 8),
            Expanded(child: _buildStatBox('Avg', _formatPrice(stats['avg']), Colors.blue)),
            const SizedBox(width: 8),
            Expanded(
              child: _buildStatBox(
                'Change',
                '${stats['isUp'] ? '+' : ''}${stats['changePct'].toStringAsFixed(2)}%',
                stats['isUp'] ? Colors.green : Colors.red,
              ),
            ),
          ],
        ),
        const SizedBox(height: 24),

        // Chart
        Expanded(
          child: Padding(
            padding: const EdgeInsets.only(right: 8),
            child: LineChart(
              LineChartData(
                lineBarsData: [
                  LineChartBarData(
                    spots: _priceHistory.asMap().entries.map((entry) {
                      return FlSpot(
                        entry.key.toDouble(),
                        (entry.value['close'] ?? 0).toDouble(),
                      );
                    }).toList(),
                    isCurved: true,
                    color: lineColor,
                    barWidth: 2.5,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      gradient: LinearGradient(
                        colors: [fillColor, fillColor.withOpacity(0)],
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                      ),
                    ),
                  ),
                ],
                gridData: FlGridData(
                  show: true,
                  drawVerticalLine: false,
                  horizontalInterval: (stats['high'] - stats['low']) / 4,
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
                      reservedSize: 50,
                      getTitlesWidget: (value, meta) {
                        return Text(
                          '₹${value.toStringAsFixed(0)}',
                          style: TextStyle(fontSize: 10, color: Colors.grey[600]),
                        );
                      },
                    ),
                  ),
                  bottomTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 30,
                      interval: (_priceHistory.length / 5).ceilToDouble(),
                      getTitlesWidget: (value, meta) {
                        final idx = value.toInt();
                        if (idx < 0 || idx >= _priceHistory.length) return const SizedBox.shrink();
                        final date = _priceHistory[idx]['date']?.toString() ?? '';
                        final parts = date.split('-');
                        if (parts.length >= 3) {
                          return Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Text(
                              '${parts[1]}/${parts[2]}',
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
                        if (idx < 0 || idx >= _priceHistory.length) {
                          return const LineTooltipItem('', TextStyle());
                        }
                        final point = _priceHistory[idx];
                        return LineTooltipItem(
                          '${point['date']}\n₹${spot.y.toStringAsFixed(2)}',
                          const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                        );
                      }).toList();
                    },
                  ),
                ),
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),

        // Current holding info
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Colors.blue.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(
            children: [
              Icon(Icons.info_outline, color: Colors.blue[700], size: 18),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Holdings: ${widget.holding.quantity} units @ ₹${widget.holding.averageBuyPrice.toStringAsFixed(2)} avg',
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildStatBox(String label, String value, Color color) {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        children: [
          Text(label, style: TextStyle(fontSize: 10, color: Colors.grey[700])),
          const SizedBox(height: 2),
          Text(
            value,
            style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold, color: color),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  IconData _getTypeIcon(String type) {
    switch (type) {
      case 'EQUITY': return Icons.show_chart;
      case 'MUTUAL_FUND': return Icons.account_balance;
      case 'GOLD': return Icons.monetization_on;
      case 'FD': return Icons.lock_clock;
      default: return Icons.category;
    }
  }
}
