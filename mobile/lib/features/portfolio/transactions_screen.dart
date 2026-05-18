// lib/features/portfolio/transactions_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:flutter_networth/providers/data_provider.dart';
import 'package:flutter_networth/core/services/api_client.dart';

class TransactionsScreen extends StatefulWidget {
  const TransactionsScreen({super.key});

  @override
  State<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends State<TransactionsScreen> {
  List<Map<String, dynamic>> _transactions = [];
  Map<String, dynamic> _holdingsMap = {};
  bool _isLoading = true;
  String? _error;
  String _selectedType = 'all';
  String _selectedAssetType = 'all';

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
        ApiClient.get('/portfolio/transactions'),
        ApiClient.get('/portfolio/holdings'),
      ]);

      // Build holdings map
      if (results[1] is List) {
        for (var h in results[1] as List) {
          _holdingsMap[h['id']] = h;
        }
      }

      if (results[0] is List) {
        _transactions = List<Map<String, dynamic>>.from(results[0] as List);
        _transactions.sort((a, b) {
          final dateA = a['transactionDate'] ?? '';
          final dateB = b['transactionDate'] ?? '';
          return dateB.compareTo(dateA);
        });
      }
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

  List<Map<String, dynamic>> get _filteredTransactions {
    return _transactions.where((t) {
      if (_selectedType != 'all' && t['transactionType'] != _selectedType) return false;
      if (_selectedAssetType != 'all') {
        final holding = _holdingsMap[t['holdingId']];
        if (holding == null || holding['assetType'] != _selectedAssetType) return false;
      }
      return true;
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    final filtered = _filteredTransactions;
    final totalBuys = filtered.where((t) => t['transactionType'] == 'BUY').length;
    final totalSells = filtered.where((t) => t['transactionType'] == 'SELL').length;
    final totalVolume = filtered.fold<double>(0, (sum, t) => sum + ((t['amount'] ?? 0) as num).toDouble());

    return Scaffold(
      appBar: AppBar(
        title: const Text('Transactions'),
        actions: [
          IconButton(icon: const Icon(Icons.refresh), onPressed: _loadData),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _loadData,
        child: Column(
          children: [
            // Summary Cards
            Container(
              padding: const EdgeInsets.all(16),
              child: Row(
                children: [
                  Expanded(child: _buildStatCard('Buys', '$totalBuys', Colors.green, Icons.arrow_upward)),
                  const SizedBox(width: 8),
                  Expanded(child: _buildStatCard('Sells', '$totalSells', Colors.red, Icons.arrow_downward)),
                  const SizedBox(width: 8),
                  Expanded(child: _buildStatCard('Volume', _formatCurrency(totalVolume), Colors.blue, Icons.bar_chart)),
                ],
              ),
            ),

            // Filters
            Container(
              height: 50,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  _buildFilterChip('All', 'all', _selectedType, (v) => setState(() => _selectedType = v)),
                  _buildFilterChip('Buy', 'BUY', _selectedType, (v) => setState(() => _selectedType = v), color: Colors.green),
                  _buildFilterChip('Sell', 'SELL', _selectedType, (v) => setState(() => _selectedType = v), color: Colors.red),
                  _buildFilterChip('SIP', 'SIP', _selectedType, (v) => setState(() => _selectedType = v), color: Colors.purple),
                ],
              ),
            ),

            // Transactions List
            Expanded(
              child: filtered.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.receipt_long, size: 64, color: Colors.grey[300]),
                          const SizedBox(height: 16),
                          const Text('No transactions found', style: TextStyle(color: Colors.grey, fontSize: 16)),
                        ],
                      ),
                    )
                  : ListView.builder(
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                      itemCount: filtered.length,
                      itemBuilder: (context, index) => _buildTransactionCard(filtered[index]),
                    ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatCard(String label, String value, Color color, IconData icon) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        children: [
          Icon(icon, color: color, size: 20),
          const SizedBox(height: 4),
          Text(value, style: TextStyle(fontWeight: FontWeight.bold, color: color, fontSize: 14)),
          Text(label, style: TextStyle(fontSize: 11, color: Colors.grey[600])),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String label, String value, String selected, Function(String) onTap, {Color color = Colors.blue}) {
    final isSelected = selected == value;
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: GestureDetector(
        onTap: () => onTap(value),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          decoration: BoxDecoration(
            color: isSelected ? color : Colors.grey[200],
            borderRadius: BorderRadius.circular(20),
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

  Widget _buildTransactionCard(Map<String, dynamic> txn) {
    final holding = _holdingsMap[txn['holdingId']];
    final type = txn['transactionType'] ?? 'BUY';
    final isBuy = type == 'BUY' || type == 'SIP' || type == 'LUMPSUM';
    final color = isBuy ? Colors.green : Colors.red;
    final amount = ((txn['amount'] ?? 0) as num).toDouble();
    final quantity = ((txn['quantity'] ?? 0) as num).toDouble();
    final price = ((txn['price'] ?? 0) as num).toDouble();

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: color.withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: Icon(isBuy ? Icons.arrow_upward : Icons.arrow_downward, color: color, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    holding?['symbol'] ?? 'Unknown',
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                  ),
                  Text(
                    '$type • ${quantity.toStringAsFixed(2)} @ ₹${price.toStringAsFixed(2)}',
                    style: TextStyle(fontSize: 12, color: Colors.grey[600]),
                  ),
                  Text(
                    txn['transactionDate']?.toString().split('T')[0] ?? '',
                    style: TextStyle(fontSize: 11, color: Colors.grey[500]),
                  ),
                ],
              ),
            ),
            Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: color.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    type,
                    style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: color),
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  _formatCurrency(amount),
                  style: TextStyle(fontWeight: FontWeight.bold, color: color, fontSize: 15),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
