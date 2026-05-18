// lib/screens/more/more_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/data_provider.dart';
import '../../services/api_client.dart';
import '../ai_chat/ai_chat_screen.dart';
import '../analytics/analytics_screen.dart';
import '../bank_accounts/bank_accounts_screen.dart';
import '../demat_accounts/demat_accounts_screen.dart';
import '../documents/documents_screen.dart';
import '../family/family_screen.dart';
import '../import/import_screen.dart';
import '../liabilities/liabilities_screen.dart';
import '../profile/profile_screen.dart';
import '../salaries/salaries_screen.dart';
import '../tax/tax_screen.dart';
import '../transactions/transactions_screen.dart';

class MoreScreen extends StatefulWidget {
  const MoreScreen({super.key});

  @override
  State<MoreScreen> createState() => _MoreScreenState();
}

class _MoreScreenState extends State<MoreScreen> {
  Map<String, dynamic>? _user;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  Future<void> _loadUser() async {
    final user = await ApiClient.getUser();
    if (mounted) {
      setState(() => _user = user);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Profile Card
        _buildProfileCard(context),
        
        const SizedBox(height: 24),
        
        // Quick Stats
        _buildStatsCard(context),
        
        const SizedBox(height: 24),
        
        // Portfolio
        _buildSection('Portfolio', [
          _buildMenuItem(context, Icons.receipt_long, 'Transactions', 'View all buy/sell', Colors.blue, const TransactionsScreen()),
          _buildMenuItem(context, Icons.analytics, 'Analytics', 'XIRR, CAGR, Risk metrics', Colors.purple, const AnalyticsScreen()),
          _buildMenuItem(context, Icons.credit_card, 'Liabilities', 'Loans and EMIs', Colors.red, const LiabilitiesScreen()),
          _buildMenuItem(context, Icons.receipt, 'Tax Planning', 'STCG, LTCG, 80C', Colors.orange, const TaxScreen()),
        ]),
        
        const SizedBox(height: 24),

        // Accounts & Banking
        _buildSection('Accounts & Banking', [
          _buildMenuItem(context, Icons.account_balance, 'Bank Accounts', 'Manage your accounts', Colors.indigo, const BankAccountsScreen()),
          _buildMenuItem(context, Icons.account_balance_wallet, 'Demat Accounts', 'Broker accounts', Colors.purple, const DematAccountsScreen()),
          _buildMenuItem(context, Icons.work, 'Salaries', 'Income tracking', Colors.green, const SalariesScreen()),
        ]),

        const SizedBox(height: 24),
        
        // Personal
        _buildSection('Personal', [
          _buildMenuItem(context, Icons.folder, 'Documents', 'Files and statements', Colors.amber, const DocumentsScreen()),
          _buildMenuItem(context, Icons.people, 'Family', 'Family members', Colors.pink, const FamilyScreen()),
          _buildMenuItem(context, Icons.upload_file, 'Import Data', 'CSV/PDF import', Colors.teal, const ImportScreen()),
        ]),
        
        const SizedBox(height: 24),
        
        // Tools
        _buildSection('Tools', [
          _buildMenuItem(context, Icons.smart_toy, 'AI Assistant', 'Get insights', Colors.cyan, const AIChatScreen()),
          _buildMenuItem(context, Icons.person, 'Profile & Settings', 'Account settings', Colors.blueGrey, const ProfileScreen()),
        ]),
        
        const SizedBox(height: 24),
        
        // Logout
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: () async {
              await ApiClient.clearToken();
              if (context.mounted) Navigator.of(context).pushReplacementNamed('/');
            },
            icon: const Icon(Icons.logout),
            label: const Text('Logout'),
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.red,
              foregroundColor: Colors.white,
              padding: const EdgeInsets.symmetric(vertical: 16),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            ),
          ),
        ),
        
        const SizedBox(height: 24),
        
        Center(
          child: Text(
            'Version 1.0.0',
            style: TextStyle(color: Colors.grey[600], fontSize: 12),
          ),
        ),
        const SizedBox(height: 16),
      ],
    );
  }

  Widget _buildProfileCard(BuildContext context) {
    final userName = _user?['name']?.toString() ?? 'Investor';
    final userEmail = _user?['email']?.toString() ?? '';
    final initial = userName.isNotEmpty ? userName[0].toUpperCase() : 'U';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Row(
          children: [
            Container(
              width: 60,
              height: 60,
              decoration: BoxDecoration(
                color: const Color(0xFF3B82F6).withOpacity(0.1),
                shape: BoxShape.circle,
              ),
              child: Center(
                child: Text(
                  initial,
                  style: const TextStyle(
                    color: Color(0xFF3B82F6),
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Welcome Back', style: TextStyle(color: Colors.grey, fontSize: 14)),
                  const SizedBox(height: 4),
                  Text(userName, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 20)),
                  if (userEmail.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 2),
                      child: Text(
                        userEmail,
                        style: TextStyle(color: Colors.grey[600], fontSize: 12),
                      ),
                    ),
                  const SizedBox(height: 4),
                  Consumer<DataProvider>(
                    builder: (context, provider, child) {
                      final healthScore = provider.healthScoreData;
                      if (healthScore != null) {
                        return Row(
                          children: [
                            Container(
                              width: 8,
                              height: 8,
                              decoration: BoxDecoration(
                                color: Color(healthScore.color),
                                shape: BoxShape.circle,
                              ),
                            ),
                            const SizedBox(width: 6),
                            Text(
                              'Health Score: ${healthScore.score}',
                              style: TextStyle(
                                fontSize: 12,
                                color: Color(healthScore.color),
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        );
                      }
                      return const SizedBox.shrink();
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatsCard(BuildContext context) {
    return Consumer<DataProvider>(
      builder: (context, provider, child) {
        return Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatItem('Holdings', provider.holdingsCount.toString(), Icons.pie_chart, Colors.blue),
                Container(height: 40, width: 1, color: Colors.grey[300]),
                _buildStatItem('Goals', provider.goals.length.toString(), Icons.flag, Colors.green),
                Container(height: 40, width: 1, color: Colors.grey[300]),
                _buildStatItem('Loans', provider.liabilities.length.toString(), Icons.credit_card, Colors.red),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildStatItem(String label, String value, IconData icon, Color color) {
    return Column(
      children: [
        Icon(icon, color: color, size: 24),
        const SizedBox(height: 8),
        Text(value, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 20)),
        const SizedBox(height: 4),
        Text(label, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
      ],
    );
  }

  Widget _buildSection(String title, List<Widget> items) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(left: 4, bottom: 8),
          child: Text(
            title,
            style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
          ),
        ),
        ...items,
      ],
    );
  }

  Widget _buildMenuItem(BuildContext context, IconData icon, String title, String subtitle, Color color, Widget screen) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: Container(
          padding: const EdgeInsets.all(8),
          decoration: BoxDecoration(
            color: color.withOpacity(0.1),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(icon, color: color),
        ),
        title: Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text(subtitle, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
        trailing: const Icon(Icons.chevron_right, color: Colors.grey),
        onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => screen)),
      ),
    );
  }
}
