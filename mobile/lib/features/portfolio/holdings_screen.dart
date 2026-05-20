// lib/features/portfolio/holdings_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:flutter_networth/providers/data_provider.dart';
import 'package:flutter_networth/data/models/app_models.dart';
import 'package:flutter_networth/widgets/dialogs/add_holding_dialog.dart';
import 'package:flutter_networth/widgets/dialogs/add_transaction_dialog.dart';
import 'package:flutter_networth/widgets/charts/holding_price_chart.dart';
import 'package:flutter_networth/core/services/api_client.dart';

class HoldingsScreen extends StatefulWidget {
  const HoldingsScreen({super.key});

  @override
  State<HoldingsScreen> createState() => HoldingsScreenState();
}

class HoldingsScreenState extends State<HoldingsScreen> {
  String _selectedFilter = 'all';

  bool _isChartLoading = true;
  List<Map<String, dynamic>> _investmentData = [];

  @override
  void initState() {
    super.initState();
    _loadInvestmentChart();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<DataProvider>().loadHoldings();
    });
  }

  Future<void> _loadInvestmentChart() async {
    try {
      final response = await ApiClient.get('/portfolio/investment-over-time?days=365');
      final List<dynamic> data = response is List ? response : (response['data'] ?? []);
      if (mounted) {
        setState(() {
          _investmentData = data.cast<Map<String, dynamic>>();
          _isChartLoading = false;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _isChartLoading = false;
        });
      }
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
        if (provider.isLoading && provider.holdings.isEmpty) {
          return const Center(child: CircularProgressIndicator());
        }

        if (provider.error != null && provider.holdings.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 64, color: Colors.red),
                const SizedBox(height: 16),
                Text('Error: ${provider.error}'),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () => provider.loadHoldings(),
                  child: const Text('Retry'),
                ),
              ],
            ),
          );
        }

        final holdings = provider.getHoldingsByType(_selectedFilter);
        final totalValue = provider.totalHoldingsValue;
        final totalPnL = provider.totalPnL;
        final holdingsCount = provider.holdingsCount;

        return RefreshIndicator(
          onRefresh: () => provider.loadHoldings(),
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              // Summary Card
              SliverToBoxAdapter(
                child: Container(
                  margin: const EdgeInsets.all(16),
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    gradient: const LinearGradient(
                      colors: [Color(0xFF10B981), Color(0xFF059669)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Text(
                                'Total Value',
                                style: TextStyle(
                                  color: Colors.white70,
                                  fontSize: 14,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                _formatCurrency(totalValue),
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 28,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              const Text(
                                'Holdings',
                                style: TextStyle(
                                  color: Colors.white70,
                                  fontSize: 14,
                                ),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '$holdingsCount',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 28,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 16),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                        decoration: BoxDecoration(
                          color: Colors.white.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(20),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(
                              totalPnL >= 0 ? Icons.arrow_upward : Icons.arrow_downward,
                              color: Colors.white,
                              size: 16,
                            ),
                            const SizedBox(width: 4),
                            Text(
                              '${totalPnL >= 0 ? '+' : ''}${_formatCurrency(totalPnL)}',
                              style: const TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              
              // Investment Growth Chart
              SliverToBoxAdapter(child: _buildInvestmentGrowthChart()),

              // Filter Chips
              SliverToBoxAdapter(
                child: Container(
                  height: 50,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    children: [
                      _buildFilterChip('All', 'all', Colors.blue),
                      _buildFilterChip('Equity', 'EQUITY', Colors.blue),
                      _buildFilterChip('MF', 'MUTUAL_FUND', Colors.green),
                      _buildFilterChip('Gold', 'GOLD', Colors.amber),
                      _buildFilterChip('FD', 'FD', Colors.purple),
                    ],
                  ),
                ),
              ),
              
              const SliverToBoxAdapter(child: SizedBox(height: 16)),
              
              // Holdings List
              if (holdings.isEmpty)
                const SliverFillRemaining(
                  hasScrollBody: false,
                  child: Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.pie_chart_outline, size: 64, color: Colors.grey),
                        SizedBox(height: 16),
                        Text(
                          'No holdings found',
                          style: TextStyle(
                            fontSize: 18,
                            color: Colors.grey,
                          ),
                        ),
                      ],
                    ),
                  ),
                )
              else
                SliverPadding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  sliver: SliverList.builder(
                    itemCount: holdings.length,
                    itemBuilder: (context, index) {
                      return _buildHoldingCard(holdings[index]);
                    },
                  ),
                ),
            ],
          ),
        );
    });
  }

  Future<void> showAddHoldingDialog() async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => const AddHoldingDialog(),
    );
    if (result != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Holding added successfully!'),
          backgroundColor: Colors.green,
        ),
      );
      context.read<DataProvider>().loadHoldings();
    }
  }

  String _formatLakhs(double value) {
    if (value >= 10000000) {
      return '${(value / 10000000).toStringAsFixed(1)}Cr';
    } else if (value >= 100000) {
      return '${(value / 100000).toStringAsFixed(0)}L';
    } else if (value >= 1000) {
      return '${(value / 1000).toStringAsFixed(0)}K';
    }
    return value.toStringAsFixed(0);
  }

  Widget _buildInvestmentGrowthChart() {
    if (_isChartLoading) {
      return const Padding(
        padding: EdgeInsets.symmetric(horizontal: 16),
        child: Card(
          child: SizedBox(
            height: 200,
            child: Center(child: CircularProgressIndicator()),
          ),
        ),
      );
    }

    if (_investmentData.isEmpty) {
      return const SizedBox.shrink();
    }

    final investedSpots = <FlSpot>[];
    final valueSpots = <FlSpot>[];

    for (int i = 0; i < _investmentData.length; i++) {
      final item = _investmentData[i];
      final invested = (item['invested'] as num).toDouble();
      final value = (item['value'] as num).toDouble();
      investedSpots.add(FlSpot(i.toDouble(), invested));
      valueSpots.add(FlSpot(i.toDouble(), value));
    }

    final allValues = [
      ...investedSpots.map((s) => s.y),
      ...valueSpots.map((s) => s.y),
    ];
    final minY = allValues.reduce((a, b) => a < b ? a : b) * 0.95;
    final maxY = allValues.reduce((a, b) => a > b ? a : b) * 1.05;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Card(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  const Text(
                    'Investment Growth',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const Spacer(),
                  Container(
                    width: 12,
                    height: 3,
                    color: Colors.blue,
                  ),
                  const SizedBox(width: 4),
                  Text('Invested', style: TextStyle(fontSize: 11, color: Colors.grey[600])),
                  const SizedBox(width: 12),
                  Container(
                    width: 12,
                    height: 3,
                    color: Colors.green,
                  ),
                  const SizedBox(width: 4),
                  Text('Value', style: TextStyle(fontSize: 11, color: Colors.grey[600])),
                ],
              ),
              const SizedBox(height: 16),
              SizedBox(
                height: 200,
                child: LineChart(
                  LineChartData(
                    minY: minY,
                    maxY: maxY,
                    gridData: FlGridData(
                      show: true,
                      drawVerticalLine: false,
                      horizontalInterval: (maxY - minY) / 4,
                      getDrawingHorizontalLine: (value) => FlLine(
                        color: Colors.grey[200]!,
                        strokeWidth: 1,
                      ),
                    ),
                    titlesData: FlTitlesData(
                      leftTitles: AxisTitles(
                        sideTitles: SideTitles(
                          showTitles: true,
                          reservedSize: 42,
                          interval: (maxY - minY) / 4,
                          getTitlesWidget: (value, meta) {
                            return Text(
                              _formatLakhs(value),
                              style: TextStyle(fontSize: 10, color: Colors.grey[500]),
                            );
                          },
                        ),
                      ),
                      bottomTitles: AxisTitles(
                        sideTitles: SideTitles(
                          showTitles: true,
                          reservedSize: 22,
                          interval: (_investmentData.length / 4).ceilToDouble(),
                          getTitlesWidget: (value, meta) {
                            final idx = value.toInt();
                            if (idx < 0 || idx >= _investmentData.length) {
                              return const SizedBox.shrink();
                            }
                            final dateStr = _investmentData[idx]['date'] as String;
                            final date = DateTime.tryParse(dateStr);
                            if (date == null) return const SizedBox.shrink();
                            final months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
                            return Text(
                              months[date.month - 1],
                              style: TextStyle(fontSize: 10, color: Colors.grey[500]),
                            );
                          },
                        ),
                      ),
                      topTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                      rightTitles: const AxisTitles(sideTitles: SideTitles(showTitles: false)),
                    ),
                    borderData: FlBorderData(show: false),
                    lineTouchData: LineTouchData(
                      touchTooltipData: LineTouchTooltipData(
                        getTooltipItems: (touchedSpots) {
                          return touchedSpots.map((spot) {
                            final label = spot.barIndex == 0 ? 'Invested' : 'Value';
                            return LineTooltipItem(
                              '$label: ₹${_formatLakhs(spot.y)}',
                              TextStyle(
                                color: spot.barIndex == 0 ? Colors.blue : Colors.green,
                                fontSize: 12,
                                fontWeight: FontWeight.w600,
                              ),
                            );
                          }).toList();
                        },
                      ),
                    ),
                    lineBarsData: [
                      // Invested line (blue, dashed)
                      LineChartBarData(
                        spots: investedSpots,
                        isCurved: true,
                        curveSmoothness: 0.3,
                        color: Colors.blue,
                        barWidth: 2,
                        dotData: const FlDotData(show: false),
                        dashArray: [6, 4],
                        belowBarData: BarAreaData(show: false),
                      ),
                      // Current Value line (green, solid, area fill)
                      LineChartBarData(
                        spots: valueSpots,
                        isCurved: true,
                        curveSmoothness: 0.3,
                        color: Colors.green,
                        barWidth: 2,
                        dotData: const FlDotData(show: false),
                        belowBarData: BarAreaData(
                          show: true,
                          color: Colors.green.withOpacity(0.12),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildFilterChip(String label, String value, Color color) {
    final isSelected = _selectedFilter == value;
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: GestureDetector(
        onTap: () {
          setState(() {
            _selectedFilter = value;
          });
        },
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          decoration: BoxDecoration(
            color: isSelected ? color : Colors.grey[200],
            borderRadius: BorderRadius.circular(20),
            boxShadow: isSelected ? [
              BoxShadow(
                color: color.withOpacity(0.3),
                blurRadius: 4,
                offset: const Offset(0, 2),
              ),
            ] : null,
          ),
          child: Text(
            label,
            style: TextStyle(
              color: isSelected ? Colors.white : Colors.grey[700],
              fontWeight: isSelected ? FontWeight.w600 : FontWeight.normal,
              fontSize: 13,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHoldingCard(Holding holding) {
    final isProfit = (holding.unrealizedPnl ?? 0) >= 0;
    final typeColor = Color(holding.assetTypeColor);
    final canShowChart = holding.assetType == 'EQUITY' || holding.assetType == 'MUTUAL_FUND' || holding.assetType == 'ETF';
    
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        onTap: canShowChart
            ? () {
                showDialog(
                  context: context,
                  builder: (context) => HoldingPriceChartDialog(holding: holding),
                );
              }
            : null,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: typeColor.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    holding.assetTypeLabel,
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w600,
                      color: typeColor,
                    ),
                  ),
                ),
                const Spacer(),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: isProfit ? Colors.green.withOpacity(0.1) : Colors.red.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        isProfit ? Icons.arrow_upward : Icons.arrow_downward,
                        size: 12,
                        color: isProfit ? Colors.green : Colors.red,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        '${holding.pnlPercentage?.toStringAsFixed(1) ?? '0.0'}%',
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: isProfit ? Colors.green : Colors.red,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: typeColor.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    _getTypeIcon(holding.assetType),
                    color: typeColor,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        holding.symbol,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      if (holding.name != null)
                        Text(
                          holding.name!,
                          style: TextStyle(
                            fontSize: 12,
                            color: Colors.grey[600],
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      const SizedBox(height: 4),
                      Text(
                        '${holding.quantity} units @ ₹${holding.averageBuyPrice.toStringAsFixed(2)}',
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey[500],
                        ),
                      ),
                    ],
                  ),
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      _formatCurrency(holding.currentValue ?? 0),
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 18,
                      ),
                    ),
                    if (holding.currentPrice != null)
                      Text(
                        'LTP: ₹${holding.currentPrice!.toStringAsFixed(2)}',
                        style: TextStyle(
                          fontSize: 12,
                          color: Colors.grey[600],
                        ),
                      ),
                    const SizedBox(height: 4),
                    Text(
                      '${isProfit ? '+' : ''}${_formatCurrency(holding.unrealizedPnl ?? 0)}',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: isProfit ? Colors.green : Colors.red,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const Divider(height: 24),
            // Action Buttons
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton.icon(
                  onPressed: () async {
                    final result = await showDialog<bool>(
                      context: context,
                      builder: (context) => AddTransactionDialog(holding: holding),
                    );
                    if (result == true && mounted) {
                      context.read<DataProvider>().loadHoldings();
                    }
                  },
                  icon: const Icon(Icons.swap_horiz, size: 18),
                  label: const Text('Add Txn'),
                  style: TextButton.styleFrom(
                    foregroundColor: Colors.blue,
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                  ),
                ),
                IconButton(
                  onPressed: () {
                    // Show holding details/history
                    _showHoldingDetails(context, holding);
                  },
                  icon: const Icon(Icons.more_vert),
                  color: Colors.grey,
                ),
              ],
            ),
            // Hint for clickable cards
            if (canShowChart)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.touch_app, size: 12, color: Colors.grey[400]),
                    const SizedBox(width: 4),
                    Text(
                      'Tap to view price chart',
                      style: TextStyle(fontSize: 11, color: Colors.grey[500]),
                    ),
                  ],
                ),
              ),
          ],
          ),
        ),
      ),
    );
  }

  void _showHoldingDetails(BuildContext context, Holding holding) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (sheetContext) => _HoldingDetailsSheet(
        holding: holding,
        formatCurrency: _formatCurrency,
        getTypeIcon: _getTypeIcon,
        buildStatCard: _buildStatCard,
        onDeleted: () {
          if (mounted) {
            context.read<DataProvider>().loadHoldings();
          }
        },
        onEdited: () {
          if (mounted) {
            context.read<DataProvider>().loadHoldings();
          }
        },
      ),
    );
  }

  Widget _buildStatCard(String label, String value, Color color) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey[600],
            ),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: color,
            ),
          ),
        ],
      ),
    );
  }

  IconData _getTypeIcon(String type) {
    switch (type) {
      case 'EQUITY':
        return Icons.show_chart;
      case 'MUTUAL_FUND':
        return Icons.account_balance;
      case 'GOLD':
        return Icons.monetization_on;
      case 'FD':
        return Icons.lock_clock;
      case 'PPF':
        return Icons.savings;
      case 'EPF':
        return Icons.work;
      case 'NPS':
        return Icons.account_balance_wallet;
      case 'REAL_ESTATE':
        return Icons.home;
      case 'CRYPTO':
        return Icons.currency_bitcoin;
      default:
        return Icons.category;
    }
  }
}

class _HoldingDetailsSheet extends StatefulWidget {
  final Holding holding;
  final String Function(double) formatCurrency;
  final IconData Function(String) getTypeIcon;
  final Widget Function(String, String, Color) buildStatCard;
  final VoidCallback onDeleted;
  final VoidCallback onEdited;

  const _HoldingDetailsSheet({
    required this.holding,
    required this.formatCurrency,
    required this.getTypeIcon,
    required this.buildStatCard,
    required this.onDeleted,
    required this.onEdited,
  });

  @override
  State<_HoldingDetailsSheet> createState() => _HoldingDetailsSheetState();
}

class _HoldingDetailsSheetState extends State<_HoldingDetailsSheet> {
  List<Transaction> _transactions = [];
  bool _isLoadingTxns = true;
  String? _txnError;
  bool _isBackfilling = false;

  List<Map<String, dynamic>> _newsItems = [];
  bool _isLoadingNews = true;
  String? _newsError;

  bool get _canBackfill =>
      widget.holding.assetType == 'EQUITY' ||
      widget.holding.assetType == 'ETF' ||
      widget.holding.assetType == 'MUTUAL_FUND';

  bool get _showNews =>
      widget.holding.assetType == 'EQUITY' ||
      widget.holding.assetType == 'ETF' ||
      widget.holding.assetType == 'MUTUAL_FUND';

  @override
  void initState() {
    super.initState();
    _loadTransactions();
    if (_showNews) {
      _loadNews();
    }
  }

  Future<void> _loadNews() async {
    try {
      final response = await ApiClient.get(
        '/news/search?q=${Uri.encodeComponent(widget.holding.symbol)}',
      );
      final List<dynamic> data = response is List ? response : [];
      if (mounted) {
        setState(() {
          _newsItems = data
              .take(5)
              .map((e) => Map<String, dynamic>.from(e as Map))
              .toList();
          _isLoadingNews = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _newsError = e.toString();
          _isLoadingNews = false;
        });
      }
    }
  }

  Future<void> _backfillPrices() async {
    setState(() => _isBackfilling = true);
    try {
      await ApiClient.post('/market/backfill-holding/${widget.holding.id}');
      if (mounted) {
        setState(() => _isBackfilling = false);
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Price backfill started successfully'),
            backgroundColor: Colors.green,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isBackfilling = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Backfill failed: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _loadTransactions() async {
    try {
      final response = await ApiClient.get(
        '/portfolio/holdings/${widget.holding.id}/transactions',
      );
      final List<dynamic> data = response is List ? response : (response['data'] ?? []);
      if (mounted) {
        setState(() {
          _transactions = data.map((e) => Transaction.fromJson(e)).toList();
          _isLoadingTxns = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _txnError = e.toString();
          _isLoadingTxns = false;
        });
      }
    }
  }

  Future<void> _deleteHolding() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Holding'),
        content: Text(
          'Are you sure you want to delete "${widget.holding.symbol}"? This action cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      try {
        await context.read<DataProvider>().deleteHolding(widget.holding.id);
        if (mounted) {
          Navigator.pop(context);
          widget.onDeleted();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Holding deleted successfully'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Failed to delete: $e'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    }
  }

  Future<void> _editHolding() async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => _EditHoldingDialog(holding: widget.holding),
    );
    if (result != null && mounted) {
      try {
        await ApiClient.put(
          '/portfolio/holdings/${widget.holding.id}',
          body: result,
        );
        if (mounted) {
          Navigator.pop(context);
          widget.onEdited();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Holding updated successfully'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Failed to update: $e'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    }
  }

  String _formatDate(String dateStr) {
    try {
      final date = DateTime.parse(dateStr);
      return '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
    } catch (_) {
      return dateStr;
    }
  }

  @override
  Widget build(BuildContext context) {
    final holding = widget.holding;

    return DraggableScrollableSheet(
      initialChildSize: 0.6,
      minChildSize: 0.3,
      maxChildSize: 0.9,
      expand: false,
      builder: (context, scrollController) {
        return Container(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Handle
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  decoration: BoxDecoration(
                    color: Colors.grey[300],
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              const SizedBox(height: 20),

              // Header
              Row(
                children: [
                  Container(
                    width: 50,
                    height: 50,
                    decoration: BoxDecoration(
                      color: Color(holding.assetTypeColor).withOpacity(0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(
                      widget.getTypeIcon(holding.assetType),
                      color: Color(holding.assetTypeColor),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          holding.symbol,
                          style: const TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        if (holding.name != null)
                          Text(
                            holding.name!,
                            style: TextStyle(
                              fontSize: 14,
                              color: Colors.grey[600],
                            ),
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

              // Edit & Delete Buttons
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _editHolding,
                      icon: const Icon(Icons.edit, size: 18),
                      label: const Text('Edit'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.blue,
                        side: const BorderSide(color: Colors.blue),
                        padding: const EdgeInsets.symmetric(vertical: 10),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _deleteHolding,
                      icon: const Icon(Icons.delete, size: 18),
                      label: const Text('Delete'),
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.red,
                        side: const BorderSide(color: Colors.red),
                        padding: const EdgeInsets.symmetric(vertical: 10),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
              if (_canBackfill) ...[
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: _isBackfilling ? null : _backfillPrices,
                    icon: _isBackfilling
                        ? const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Icon(Icons.history, size: 18),
                    label: Text(_isBackfilling ? 'Backfilling...' : 'Backfill Prices'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.orange,
                      side: const BorderSide(color: Colors.orange),
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 16),

              // Stats Grid
              Row(
                children: [
                  Expanded(
                    child: widget.buildStatCard('Current Value', widget.formatCurrency(holding.currentValue ?? 0), Colors.blue),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: widget.buildStatCard('Invested', widget.formatCurrency(holding.investedValue), Colors.purple),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: widget.buildStatCard(
                      'P&L',
                      '${holding.unrealizedPnl != null && holding.unrealizedPnl! >= 0 ? '+' : ''}${widget.formatCurrency(holding.unrealizedPnl ?? 0)}',
                      (holding.unrealizedPnl ?? 0) >= 0 ? Colors.green : Colors.red,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: widget.buildStatCard('Quantity', '${holding.quantity}', Colors.orange),
                  ),
                ],
              ),
              const SizedBox(height: 24),

              // Transaction History & News
              Expanded(
                child: ListView(
                  controller: scrollController,
                  children: [
                    const Text(
                      'Transaction History',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 12),
                    _buildTransactionSection(),
                    if (_showNews) ...[
                      const SizedBox(height: 24),
                      const Text(
                        'News',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      const SizedBox(height: 12),
                      _buildNewsSection(),
                    ],
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _buildTransactionSection() {
    if (_isLoadingTxns) {
      return const Padding(
        padding: EdgeInsets.all(32),
        child: Center(child: CircularProgressIndicator()),
      );
    }

    if (_txnError != null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(Icons.error_outline, size: 48, color: Colors.grey[300]),
            const SizedBox(height: 12),
            Text(
              'Failed to load transactions',
              style: TextStyle(color: Colors.grey[600]),
            ),
            const SizedBox(height: 8),
            TextButton(
              onPressed: () {
                setState(() {
                  _isLoadingTxns = true;
                  _txnError = null;
                });
                _loadTransactions();
              },
              child: const Text('Retry'),
            ),
          ],
        ),
      );
    }

    if (_transactions.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(Icons.history, size: 48, color: Colors.grey[300]),
            const SizedBox(height: 12),
            Text(
              'No transactions yet',
              style: TextStyle(color: Colors.grey[600]),
            ),
          ],
        ),
      );
    }

    return Column(
      children: _transactions.map((txn) {
        final isBuy = txn.type.toUpperCase() == 'BUY';
        return Column(
          children: [
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: isBuy ? Colors.green.withOpacity(0.1) : Colors.red.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  txn.type.toUpperCase(),
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: isBuy ? Colors.green : Colors.red,
                  ),
                ),
              ),
              title: Text(
                '${txn.quantity} units @ \u20B9${txn.price.toStringAsFixed(2)}',
                style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
              ),
              subtitle: Text(
                _formatDate(txn.date),
                style: TextStyle(fontSize: 12, color: Colors.grey[500]),
              ),
              trailing: Text(
                '\u20B9${txn.amount.toStringAsFixed(2)}',
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            const Divider(height: 1),
          ],
        );
      }).toList(),
    );
  }

  Widget _buildNewsSection() {
    if (_isLoadingNews) {
      return const Padding(
        padding: EdgeInsets.all(16),
        child: Center(child: CircularProgressIndicator()),
      );
    }

    if (_newsError != null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(Icons.error_outline, size: 40, color: Colors.grey[300]),
            const SizedBox(height: 8),
            Text(
              'Failed to load news',
              style: TextStyle(color: Colors.grey[600]),
            ),
          ],
        ),
      );
    }

    if (_newsItems.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Icon(Icons.newspaper, size: 40, color: Colors.grey[300]),
            const SizedBox(height: 8),
            Text(
              'No recent news',
              style: TextStyle(color: Colors.grey[600]),
            ),
          ],
        ),
      );
    }

    return Column(
      children: _newsItems.map((news) {
        return ListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(
            news['title']?.toString() ?? '',
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
          ),
          subtitle: Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Row(
              children: [
                if (news['source'] != null) ...[
                  Text(
                    news['source'].toString(),
                    style: TextStyle(fontSize: 12, color: Colors.blue[600], fontWeight: FontWeight.w500),
                  ),
                  const SizedBox(width: 8),
                ],
                if (news['pubDate'] != null)
                  Text(
                    _formatNewsDate(news['pubDate'].toString()),
                    style: TextStyle(fontSize: 11, color: Colors.grey[500]),
                  ),
              ],
            ),
          ),
          leading: Icon(Icons.article, color: Colors.blue[300]),
        );
      }).toList(),
    );
  }

  String _formatNewsDate(String dateStr) {
    try {
      final date = DateTime.parse(dateStr);
      final now = DateTime.now();
      final diff = now.difference(date);
      if (diff.inHours < 1) return '${diff.inMinutes}m ago';
      if (diff.inHours < 24) return '${diff.inHours}h ago';
      if (diff.inDays < 7) return '${diff.inDays}d ago';
      return '${date.day}/${date.month}/${date.year}';
    } catch (_) {
      return dateStr;
    }
  }
}

class _EditHoldingDialog extends StatefulWidget {
  final Holding holding;

  const _EditHoldingDialog({required this.holding});

  @override
  State<_EditHoldingDialog> createState() => _EditHoldingDialogState();
}

class _EditHoldingDialogState extends State<_EditHoldingDialog> {
  late final TextEditingController _symbolController;
  late final TextEditingController _nameController;
  late final TextEditingController _quantityController;
  late final TextEditingController _avgPriceController;
  final _formKey = GlobalKey<FormState>();

  @override
  void initState() {
    super.initState();
    _symbolController = TextEditingController(text: widget.holding.symbol);
    _nameController = TextEditingController(text: widget.holding.name ?? '');
    _quantityController = TextEditingController(text: widget.holding.quantity.toString());
    _avgPriceController = TextEditingController(text: widget.holding.averageBuyPrice.toString());
  }

  @override
  void dispose() {
    _symbolController.dispose();
    _nameController.dispose();
    _quantityController.dispose();
    _avgPriceController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Edit Holding'),
      content: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextFormField(
                controller: _symbolController,
                decoration: const InputDecoration(
                  labelText: 'Symbol',
                  border: OutlineInputBorder(),
                ),
                validator: (v) => v == null || v.trim().isEmpty ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _nameController,
                decoration: const InputDecoration(
                  labelText: 'Name',
                  border: OutlineInputBorder(),
                ),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _quantityController,
                decoration: const InputDecoration(
                  labelText: 'Quantity',
                  border: OutlineInputBorder(),
                ),
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                validator: (v) {
                  if (v == null || v.trim().isEmpty) return 'Required';
                  if (double.tryParse(v) == null) return 'Invalid number';
                  if (double.parse(v) <= 0) return 'Must be > 0';
                  return null;
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _avgPriceController,
                decoration: const InputDecoration(
                  labelText: 'Avg Buy Price',
                  border: OutlineInputBorder(),
                ),
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                validator: (v) {
                  if (v == null || v.trim().isEmpty) return 'Required';
                  if (double.tryParse(v) == null) return 'Invalid number';
                  if (double.parse(v) <= 0) return 'Must be > 0';
                  return null;
                },
              ),
            ],
          ),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
        ElevatedButton(
          onPressed: () {
            if (_formKey.currentState!.validate()) {
              Navigator.pop(context, {
                'symbol': _symbolController.text.trim(),
                'name': _nameController.text.trim(),
                'quantity': double.parse(_quantityController.text.trim()),
                'averageBuyPrice': double.parse(_avgPriceController.text.trim()),
              });
            }
          },
          style: ElevatedButton.styleFrom(backgroundColor: Colors.blue),
          child: const Text('Save'),
        ),
      ],
    );
  }
}
