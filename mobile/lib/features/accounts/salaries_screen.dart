// lib/features/accounts/salaries_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_networth/core/services/api_client.dart';

class SalariesScreen extends StatefulWidget {
  const SalariesScreen({super.key});

  @override
  State<SalariesScreen> createState() => _SalariesScreenState();
}

class _SalariesScreenState extends State<SalariesScreen> {
  List<Map<String, dynamic>> _salaries = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadSalaries();
  }

  Future<void> _loadSalaries() async {
    setState(() => _isLoading = true);
    try {
      final response = await ApiClient.get('/salaries');
      if (response is List) _salaries = List<Map<String, dynamic>>.from(response);
    } catch (e) {
      // Handle error
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

  Future<void> _showAddDialog() async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => const _SalaryDialog(),
    );
    if (result != null) {
      try {
        await ApiClient.post('/salaries', body: result);
        _loadSalaries();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red),
          );
        }
      }
    }
  }

  Future<void> _showEditDialog(Map<String, dynamic> salary) async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => _SalaryDialog(existingSalary: salary),
    );
    if (result != null) {
      try {
        final id = salary['id']?.toString() ?? '';
        await ApiClient.put('/salaries/$id', body: result);
        _loadSalaries();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Salary updated!'), backgroundColor: Colors.green),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red),
          );
        }
      }
    }
  }

  Future<void> _deleteSalary(String id) async {
    try {
      await ApiClient.delete('/salaries/$id');
      _loadSalaries();
    } catch (e) {
      // Handle error
    }
  }

  @override
  Widget build(BuildContext context) {
    // Calculate current month total
    final now = DateTime.now();
    final currentMonthSalaries = _salaries.where((s) {
      final dateStr = s['payDate']?.toString();
      if (dateStr == null) return false;
      try {
        final date = DateTime.parse(dateStr);
        return date.year == now.year && date.month == now.month;
      } catch (e) {
        return false;
      }
    }).toList();

    final currentMonthTotal = currentMonthSalaries.fold<double>(
      0,
      (sum, s) => sum + ((s['amount'] ?? 0) as num).toDouble(),
    );

    return Scaffold(
      appBar: AppBar(title: const Text('Salaries')),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _loadSalaries,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  // Current Month Summary
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      gradient: const LinearGradient(
                        colors: [Color(0xFF10B981), Color(0xFF059669)],
                      ),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('This Month', style: TextStyle(color: Colors.white70)),
                        const SizedBox(height: 8),
                        Text(
                          _formatCurrency(currentMonthTotal),
                          style: const TextStyle(color: Colors.white, fontSize: 32, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '${currentMonthSalaries.length} payment${currentMonthSalaries.length != 1 ? "s" : ""}',
                          style: const TextStyle(color: Colors.white70, fontSize: 13),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),

                  if (_salaries.isEmpty)
                    Center(
                      child: Padding(
                        padding: const EdgeInsets.all(32),
                        child: Column(
                          children: [
                            Icon(Icons.account_balance_wallet, size: 64, color: Colors.grey[300]),
                            const SizedBox(height: 16),
                            const Text('No salary records', style: TextStyle(color: Colors.grey)),
                          ],
                        ),
                      ),
                    )
                  else
                    ..._salaries.map((salary) => Card(
                      margin: const EdgeInsets.only(bottom: 8),
                      child: ExpansionTile(
                        leading: CircleAvatar(
                          backgroundColor: Colors.green.withOpacity(0.1),
                          child: const Icon(Icons.work, color: Colors.green),
                        ),
                        title: Text(salary['employerName']?.toString() ?? ''),
                        subtitle: Text(salary['payDate']?.toString().split('T')[0] ?? ''),
                        trailing: Text(
                          _formatCurrency(((salary['amount'] ?? 0) as num).toDouble()),
                          style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.green, fontSize: 16),
                        ),
                        children: [
                          if (salary['grossPay'] != null || salary['netPay'] != null)
                            Padding(
                              padding: const EdgeInsets.all(16),
                              child: Column(
                                children: [
                                  if (salary['grossPay'] != null)
                                    _buildBreakdownRow('Gross Pay', ((salary['grossPay'] ?? 0) as num).toDouble(), Colors.blue),
                                  if (salary['netPay'] != null)
                                    _buildBreakdownRow('Net Pay', ((salary['netPay'] ?? 0) as num).toDouble(), Colors.green),
                                  if (salary['components'] != null && salary['components'] is Map) ...[
                                    const Divider(),
                                    ...((salary['components'] as Map).entries.map((e) => _buildBreakdownRow(
                                      e.key.toString(),
                                      ((e.value ?? 0) as num).toDouble(),
                                      Colors.grey[700]!,
                                    ))),
                                  ],
                                  const SizedBox(height: 8),
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      TextButton.icon(
                                        onPressed: () => _showEditDialog(salary),
                                        icon: const Icon(Icons.edit, color: Colors.blue),
                                        label: const Text('Edit', style: TextStyle(color: Colors.blue)),
                                      ),
                                      const SizedBox(width: 16),
                                      TextButton.icon(
                                        onPressed: () => _deleteSalary(salary['id']?.toString() ?? ''),
                                        icon: const Icon(Icons.delete, color: Colors.red),
                                        label: const Text('Delete', style: TextStyle(color: Colors.red)),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                        ],
                      ),
                    )),
                ],
              ),
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showAddDialog,
        icon: const Icon(Icons.add),
        label: const Text('Add Salary'),
      ),
    );
  }

  Widget _buildBreakdownRow(String label, double value, Color color) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: TextStyle(color: Colors.grey[700])),
          Text(_formatCurrency(value), style: TextStyle(fontWeight: FontWeight.bold, color: color)),
        ],
      ),
    );
  }
}

class _SalaryDialog extends StatefulWidget {
  final Map<String, dynamic>? existingSalary;
  const _SalaryDialog({this.existingSalary});

  @override
  State<_SalaryDialog> createState() => _SalaryDialogState();
}

class _SalaryDialogState extends State<_SalaryDialog> {
  final _formKey = GlobalKey<FormState>();
  final _employerController = TextEditingController();
  final _amountController = TextEditingController();
  final _notesController = TextEditingController();
  DateTime _payDate = DateTime.now();

  bool get _isEditing => widget.existingSalary != null;

  @override
  void initState() {
    super.initState();
    if (widget.existingSalary != null) {
      final s = widget.existingSalary!;
      _employerController.text = s['employerName']?.toString() ?? '';
      _amountController.text = (s['amount'] ?? '').toString();
      _notesController.text = s['notes']?.toString() ?? '';
      final dateStr = s['payDate']?.toString();
      if (dateStr != null) {
        try {
          _payDate = DateTime.parse(dateStr);
        } catch (_) {}
      }
    }
  }

  Future<void> _selectDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _payDate,
      firstDate: DateTime(2000),
      lastDate: DateTime.now(),
    );
    if (picked != null) setState(() => _payDate = picked);
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      child: Container(
        width: double.infinity,
        constraints: const BoxConstraints(maxWidth: 400),
        padding: const EdgeInsets.all(20),
        child: Form(
          key: _formKey,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(_isEditing ? 'Edit Salary' : 'Add Salary', style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              Flexible(
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      TextFormField(
                        controller: _employerController,
                        decoration: const InputDecoration(labelText: 'Employer', border: OutlineInputBorder()),
                        validator: (v) => v?.isEmpty ?? true ? 'Required' : null,
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: _amountController,
                        keyboardType: TextInputType.number,
                        inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d*'))],
                        decoration: const InputDecoration(labelText: 'Amount', prefixText: '₹ ', border: OutlineInputBorder()),
                        validator: (v) => v?.isEmpty ?? true ? 'Required' : null,
                      ),
                      const SizedBox(height: 12),
                      InkWell(
                        onTap: _selectDate,
                        child: InputDecorator(
                          decoration: const InputDecoration(labelText: 'Pay Date', border: OutlineInputBorder()),
                          child: Text('${_payDate.year}-${_payDate.month.toString().padLeft(2, '0')}-${_payDate.day.toString().padLeft(2, '0')}'),
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextFormField(
                        controller: _notesController,
                        decoration: const InputDecoration(labelText: 'Notes (Optional)', border: OutlineInputBorder()),
                        maxLines: 2,
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(child: OutlinedButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel'))),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () {
                        if (_formKey.currentState!.validate()) {
                          Navigator.pop(context, {
                            'employerName': _employerController.text,
                            'amount': double.parse(_amountController.text),
                            'payDate': _payDate.toIso8601String(),
                            'notes': _notesController.text,
                          });
                        }
                      },
                      child: const Text('Save'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
