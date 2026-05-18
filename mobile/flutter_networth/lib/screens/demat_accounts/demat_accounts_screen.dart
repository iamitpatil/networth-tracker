// lib/screens/demat_accounts/demat_accounts_screen.dart
import 'package:flutter/material.dart';
import '../../services/api_client.dart';

class DematAccountsScreen extends StatefulWidget {
  const DematAccountsScreen({super.key});

  @override
  State<DematAccountsScreen> createState() => _DematAccountsScreenState();
}

class _DematAccountsScreenState extends State<DematAccountsScreen> {
  List<Map<String, dynamic>> _accounts = [];
  bool _isLoading = true;

  final _brokers = ['Zerodha', 'Groww', 'Upstox', 'ICICI Direct', 'HDFC Securities', 'Kotak Securities', 'Angel One', '5paisa', 'Other'];
  final _types = ['Equity', 'Commodity', 'Derivatives', 'Mutual Funds'];

  @override
  void initState() {
    super.initState();
    _loadAccounts();
  }

  Future<void> _loadAccounts() async {
    setState(() => _isLoading = true);
    try {
      final response = await ApiClient.get('/demat-accounts');
      if (response is List) _accounts = List<Map<String, dynamic>>.from(response);
    } catch (e) {
      // Handle error
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _showAddDialog() async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => _DematAccountDialog(brokers: _brokers, types: _types),
    );
    if (result != null) {
      try {
        await ApiClient.post('/demat-accounts', body: result);
        _loadAccounts();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red),
          );
        }
      }
    }
  }

  Future<void> _deleteAccount(String id) async {
    try {
      await ApiClient.delete('/demat-accounts/$id');
      _loadAccounts();
    } catch (e) {
      // Handle error
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Demat Accounts')),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _loadAccounts,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  // Stats
                  Row(
                    children: [
                      Expanded(child: _buildStat('Total', '${_accounts.length}', Colors.blue, Icons.account_balance_wallet)),
                      const SizedBox(width: 8),
                      Expanded(child: _buildStat('Brokers', '${_accounts.map((a) => a['brokerName']).toSet().length}', Colors.purple, Icons.business)),
                    ],
                  ),
                  const SizedBox(height: 16),
                  
                  if (_accounts.isEmpty)
                    Center(
                      child: Padding(
                        padding: const EdgeInsets.all(32),
                        child: Column(
                          children: [
                            Icon(Icons.account_balance_wallet, size: 64, color: Colors.grey[300]),
                            const SizedBox(height: 16),
                            const Text('No demat accounts', style: TextStyle(color: Colors.grey, fontSize: 16)),
                          ],
                        ),
                      ),
                    )
                  else
                    ..._accounts.map((account) => Card(
                      margin: const EdgeInsets.only(bottom: 12),
                      child: ListTile(
                        leading: CircleAvatar(
                          backgroundColor: Colors.purple.withOpacity(0.1),
                          child: const Icon(Icons.account_balance_wallet, color: Colors.purple),
                        ),
                        title: Row(
                          children: [
                            Text(account['brokerName']?.toString() ?? ''),
                            if (account['isDefault'] == true) ...[
                              const SizedBox(width: 8),
                              const Icon(Icons.star, color: Colors.amber, size: 16),
                            ],
                          ],
                        ),
                        subtitle: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('****${(account['accountNumber']?.toString() ?? '').length > 4 ? (account['accountNumber'] as String).substring(account['accountNumber'].toString().length - 4) : account['accountNumber']?.toString() ?? ''}'),
                            Text(account['accountType']?.toString() ?? '', style: const TextStyle(fontSize: 12)),
                          ],
                        ),
                        trailing: IconButton(
                          icon: const Icon(Icons.delete, color: Colors.red),
                          onPressed: () => _deleteAccount(account['id']?.toString() ?? ''),
                        ),
                      ),
                    )),
                ],
              ),
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _showAddDialog,
        icon: const Icon(Icons.add),
        label: const Text('Add Account'),
      ),
    );
  }

  Widget _buildStat(String label, String value, Color color, IconData icon) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: color.withOpacity(0.1), shape: BoxShape.circle),
              child: Icon(icon, color: color, size: 20),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
                Text(value, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _DematAccountDialog extends StatefulWidget {
  final List<String> brokers;
  final List<String> types;
  const _DematAccountDialog({required this.brokers, required this.types});

  @override
  State<_DematAccountDialog> createState() => _DematAccountDialogState();
}

class _DematAccountDialogState extends State<_DematAccountDialog> {
  final _formKey = GlobalKey<FormState>();
  final _accountNumberController = TextEditingController();
  final _descriptionController = TextEditingController();
  String _broker = 'Zerodha';
  String _type = 'Equity';
  bool _isDefault = false;

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
              const Text('Add Demat Account', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              DropdownButtonFormField<String>(
                value: _broker,
                decoration: const InputDecoration(labelText: 'Broker', border: OutlineInputBorder()),
                items: widget.brokers.map((b) => DropdownMenuItem(value: b, child: Text(b))).toList(),
                onChanged: (v) => setState(() => _broker = v!),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _accountNumberController,
                decoration: const InputDecoration(labelText: 'Account Number', border: OutlineInputBorder()),
                validator: (v) => v?.isEmpty ?? true ? 'Required' : null,
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: _type,
                decoration: const InputDecoration(labelText: 'Account Type', border: OutlineInputBorder()),
                items: widget.types.map((t) => DropdownMenuItem(value: t, child: Text(t))).toList(),
                onChanged: (v) => setState(() => _type = v!),
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _descriptionController,
                decoration: const InputDecoration(labelText: 'Description (Optional)', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              CheckboxListTile(
                title: const Text('Set as Default'),
                value: _isDefault,
                onChanged: (v) => setState(() => _isDefault = v ?? false),
                controlAffinity: ListTileControlAffinity.leading,
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
                            'brokerName': _broker,
                            'accountNumber': _accountNumberController.text,
                            'accountType': _type,
                            'description': _descriptionController.text,
                            'isDefault': _isDefault,
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
