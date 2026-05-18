// lib/widgets/dialogs/add_holding_dialog.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../models/app_models.dart';

class AddHoldingDialog extends StatefulWidget {
  const AddHoldingDialog({super.key});

  @override
  State<AddHoldingDialog> createState() => _AddHoldingDialogState();
}

class _AddHoldingDialogState extends State<AddHoldingDialog> {
  final _formKey = GlobalKey<FormState>();
  String _selectedAssetType = 'EQUITY';
  final _symbolController = TextEditingController();
  final _nameController = TextEditingController();
  final _quantityController = TextEditingController();
  final _avgPriceController = TextEditingController();
  bool _isLoading = false;

  final List<Map<String, dynamic>> _assetTypes = [
    {'value': 'EQUITY', 'label': 'Stock / Equity', 'icon': Icons.show_chart, 'color': Colors.blue},
    {'value': 'MUTUAL_FUND', 'label': 'Mutual Fund', 'icon': Icons.account_balance, 'color': Colors.green},
    {'value': 'GOLD', 'label': 'Gold', 'icon': Icons.monetization_on, 'color': Colors.amber},
    {'value': 'FD', 'label': 'Fixed Deposit', 'icon': Icons.lock_clock, 'color': Colors.purple},
    {'value': 'PPF', 'label': 'PPF', 'icon': Icons.savings, 'color': Color(0xFF14B8A6)},
    {'value': 'EPF', 'label': 'EPF', 'icon': Icons.work, 'color': Color(0xFFF97316)},
    {'value': 'NPS', 'label': 'NPS', 'icon': Icons.account_balance_wallet, 'color': Color(0xFF06B6D4)},
    {'value': 'REAL_ESTATE', 'label': 'Real Estate', 'icon': Icons.home, 'color': Colors.red},
    {'value': 'CRYPTO', 'label': 'Crypto', 'icon': Icons.currency_bitcoin, 'color': Color(0xFFEC4899)},
    {'value': 'CASH', 'label': 'Cash / Savings', 'icon': Icons.money, 'color': Color(0xFF8B5CF6)},
  ];

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isLoading = true);

    try {
      // TODO: Call API to add holding
      await Future.delayed(const Duration(seconds: 1)); // Simulate API call

      if (mounted) {
        Navigator.pop(context, {
          'assetType': _selectedAssetType,
          'symbol': _symbolController.text.toUpperCase(),
          'name': _nameController.text,
          'quantity': double.parse(_quantityController.text),
          'averageBuyPrice': double.parse(_avgPriceController.text),
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Container(
        width: double.infinity,
        constraints: const BoxConstraints(maxWidth: 450, maxHeight: 600),
        child: Column(
          children: [
            // Header
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.blue.withOpacity(0.1),
                borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
              ),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Colors.blue,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.add_circle,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Add New Holding',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          'Track your investment',
                          style: TextStyle(
                            fontSize: 13,
                            color: Colors.grey,
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
            ),

            // Form
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(20),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Asset Type Selection
                      const Text(
                        'Asset Type',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Container(
                        height: 90,
                        child: ListView.builder(
                          scrollDirection: Axis.horizontal,
                          itemCount: _assetTypes.length,
                          itemBuilder: (context, index) {
                            final type = _assetTypes[index];
                            final isSelected = _selectedAssetType == type['value'];
                            final color = type['color'] as Color;

                            return GestureDetector(
                              onTap: () => setState(() => _selectedAssetType = type['value']),
                              child: Container(
                                width: 80,
                                margin: const EdgeInsets.only(right: 8),
                                padding: const EdgeInsets.all(8),
                                decoration: BoxDecoration(
                                  color: isSelected ? color.withOpacity(0.1) : Colors.grey[50],
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(
                                    color: isSelected ? color : Colors.transparent,
                                    width: 2,
                                  ),
                                ),
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    Icon(
                                      type['icon'] as IconData,
                                      color: isSelected ? color : Colors.grey,
                                      size: 24,
                                    ),
                                    const SizedBox(height: 4),
                                    Text(
                                      type['label'] as String,
                                      style: TextStyle(
                                        fontSize: 10,
                                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                        color: isSelected ? color : Colors.grey[600],
                                      ),
                                      textAlign: TextAlign.center,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                      const SizedBox(height: 20),

                      // Symbol/Ticker
                      TextFormField(
                        controller: _symbolController,
                        textCapitalization: TextCapitalization.characters,
                        decoration: InputDecoration(
                          labelText: _selectedAssetType == 'MUTUAL_FUND' ? 'ISIN / Fund Code' : 'Symbol / Ticker',
                          hintText: _selectedAssetType == 'MUTUAL_FUND' ? 'e.g., INF209K01LN7' : 'e.g., RELIANCE.NS',
                          prefixIcon: const Icon(Icons.label),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        validator: (value) {
                          if (value == null || value.isEmpty) {
                            return 'Please enter symbol';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // Name (Optional)
                      TextFormField(
                        controller: _nameController,
                        decoration: InputDecoration(
                          labelText: 'Name (Optional)',
                          hintText: 'e.g., Reliance Industries',
                          prefixIcon: const Icon(Icons.business),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),

                      // Quantity
                      TextFormField(
                        controller: _quantityController,
                        keyboardType: TextInputType.number,
                        inputFormatters: [
                          FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d*')),
                        ],
                        decoration: InputDecoration(
                          labelText: 'Quantity',
                          hintText: 'Number of units',
                          prefixIcon: const Icon(Icons.numbers),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        validator: (value) {
                          if (value == null || value.isEmpty) {
                            return 'Please enter quantity';
                          }
                          if (double.tryParse(value) == null || double.parse(value) <= 0) {
                            return 'Please enter valid quantity';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // Average Buy Price
                      TextFormField(
                        controller: _avgPriceController,
                        keyboardType: TextInputType.number,
                        inputFormatters: [
                          FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d*')),
                        ],
                        decoration: InputDecoration(
                          labelText: 'Average Buy Price (₹)',
                          hintText: 'Price per unit',
                          prefixIcon: const Icon(Icons.currency_rupee),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        validator: (value) {
                          if (value == null || value.isEmpty) {
                            return 'Please enter buy price';
                          }
                          if (double.tryParse(value) == null || double.parse(value) <= 0) {
                            return 'Please enter valid price';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 24),

                      // Investment Preview
                      Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: Colors.blue.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            const Text(
                              'Total Investment',
                              style: TextStyle(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            Text(
                              '₹${_calculateTotal()}',
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 18,
                                color: Colors.blue,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),

            // Footer Buttons
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.grey[50],
                borderRadius: const BorderRadius.vertical(bottom: Radius.circular(20)),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(context),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: const Text('Cancel'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: ElevatedButton(
                      onPressed: _isLoading ? null : _submit,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.blue,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: _isLoading
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                              ),
                            )
                          : const Text(
                              'Add Holding',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
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

  String _calculateTotal() {
    final quantity = double.tryParse(_quantityController.text) ?? 0;
    final price = double.tryParse(_avgPriceController.text) ?? 0;
    final total = quantity * price;
    if (total == 0) return '0.00';
    return total.toStringAsFixed(2);
  }
}
