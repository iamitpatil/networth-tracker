// lib/features/profile/profile_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_networth/core/services/api_client.dart';
import 'package:flutter_networth/features/accounts/demat_accounts_screen.dart';
import 'package:flutter_networth/features/personal/documents_screen.dart';

class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Map<String, dynamic>? _user;
  bool _isRefreshingSymbols = false;

  @override
  void initState() {
    super.initState();
    _loadUser();
  }

  Future<void> _loadUser() async {
    // Get user from local storage (saved from login response)
    final user = await ApiClient.getUser();
    if (user != null && mounted) {
      setState(() => _user = user);
    }
    // Optionally also try /auth/me for fresh data
    try {
      final response = await ApiClient.get('/auth/me').catchError((e) => null);
      if (response is Map<String, dynamic> && mounted) {
        setState(() => _user = response);
        await ApiClient.setUser(response);
      }
    } catch (e) {
      // Handle error - we already have local user data
    }
  }

  String _getInitials() {
    final name = _user?['name']?.toString() ?? '';
    if (name.isEmpty) return 'U';
    final parts = name.trim().split(' ');
    if (parts.length >= 2) {
      return '${parts[0][0]}${parts[1][0]}'.toUpperCase();
    }
    return name[0].toUpperCase();
  }

  Future<void> _refreshSymbols() async {
    setState(() => _isRefreshingSymbols = true);
    try {
      await ApiClient.post('/symbols/refresh');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Symbols refreshed!'), backgroundColor: Colors.green),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red),
        );
      }
    } finally {
      setState(() => _isRefreshingSymbols = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Profile')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // User Card
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 32,
                    backgroundColor: Colors.blue.withOpacity(0.1),
                    child: Text(
                      _getInitials(),
                      style: const TextStyle(
                        fontSize: 28,
                        fontWeight: FontWeight.bold,
                        color: Colors.blue,
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          _user?['name']?.toString() ?? 'User',
                          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          _user?['email']?.toString() ?? '',
                          style: TextStyle(color: Colors.grey[600], fontSize: 14),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Account Settings
          const Text('Account', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          _buildMenuItem(
            Icons.account_balance_wallet,
            'Demat Accounts',
            'Manage broker accounts',
            Colors.purple,
            () => Navigator.push(context, MaterialPageRoute(builder: (_) => const DematAccountsScreen())),
          ),
          _buildMenuItem(
            Icons.folder,
            'Documents',
            'View and manage files',
            Colors.orange,
            () => Navigator.push(context, MaterialPageRoute(builder: (_) => const DocumentsScreen())),
          ),
          
          const SizedBox(height: 24),

          // Data
          const Text('Data', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Card(
            child: ListTile(
              leading: CircleAvatar(
                backgroundColor: Colors.blue.withOpacity(0.1),
                child: _isRefreshingSymbols
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.refresh, color: Colors.blue),
              ),
              title: const Text('Refresh Symbols'),
              subtitle: const Text('Update NSE equities and mutual funds'),
              trailing: const Icon(Icons.chevron_right),
              onTap: _isRefreshingSymbols ? null : _refreshSymbols,
            ),
          ),

          const SizedBox(height: 24),

          // Logout
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: () async {
                await ApiClient.clearToken();
                if (mounted) Navigator.of(context).pushReplacementNamed('/');
              },
              icon: const Icon(Icons.logout),
              label: const Text('Logout'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 16),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMenuItem(IconData icon, String title, String subtitle, Color color, VoidCallback onTap) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: color.withOpacity(0.1),
          child: Icon(icon, color: color),
        ),
        title: Text(title),
        subtitle: Text(subtitle, style: const TextStyle(fontSize: 12)),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}
