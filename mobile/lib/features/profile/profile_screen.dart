// lib/features/profile/profile_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
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
  bool _is2FASetupInProgress = false;
  String? _2faSecret;
  String? _2faQrCodeUrl;
  final _2faVerifyController = TextEditingController();

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

  bool get _is2FAEnabled => _user?['twoFactorEnabled'] == true;

  Future<void> _start2FASetup() async {
    setState(() => _is2FASetupInProgress = true);
    try {
      final response = await ApiClient.post('/auth/2fa/setup');
      if (response is Map<String, dynamic> && mounted) {
        setState(() {
          _2faSecret = response['secret']?.toString();
          _2faQrCodeUrl = response['qrCodeUrl']?.toString();
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error starting 2FA setup: $e'), backgroundColor: Colors.red),
        );
        setState(() => _is2FASetupInProgress = false);
      }
    }
  }

  Future<void> _verify2FASetup() async {
    final code = _2faVerifyController.text.trim();
    if (code.length != 6) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a 6-digit code'), backgroundColor: Colors.orange),
      );
      return;
    }
    try {
      await ApiClient.post('/auth/2fa/verify-setup', body: {'code': code});
      if (mounted) {
        setState(() {
          _is2FASetupInProgress = false;
          _2faSecret = null;
          _2faQrCodeUrl = null;
          _2faVerifyController.clear();
          if (_user != null) _user!['twoFactorEnabled'] = true;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Two-factor authentication enabled!'), backgroundColor: Colors.green),
        );
        await ApiClient.setUser(_user!);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Verification failed: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  Future<void> _disable2FA() async {
    final codeController = TextEditingController();
    final result = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Disable 2FA'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Enter your authenticator code to disable two-factor authentication.'),
            const SizedBox(height: 16),
            TextField(
              controller: codeController,
              keyboardType: TextInputType.number,
              textAlign: TextAlign.center,
              maxLength: 6,
              style: const TextStyle(fontSize: 24, fontFamily: 'monospace', letterSpacing: 8),
              decoration: const InputDecoration(
                labelText: 'Verification Code',
                hintText: '000000',
                counterText: '',
                border: OutlineInputBorder(),
              ),
              inputFormatters: [
                FilteringTextInputFormatter.digitsOnly,
                LengthLimitingTextInputFormatter(6),
              ],
            ),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () => Navigator.pop(context, codeController.text),
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red, foregroundColor: Colors.white),
            child: const Text('Disable'),
          ),
        ],
      ),
    );
    codeController.dispose();

    if (result != null && result.trim().length == 6) {
      try {
        await ApiClient.post('/auth/2fa/disable', body: {'code': result.trim()});
        if (mounted) {
          setState(() {
            if (_user != null) _user!['twoFactorEnabled'] = false;
          });
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Two-factor authentication disabled'), backgroundColor: Colors.green),
          );
          await ApiClient.setUser(_user!);
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
          const SizedBox(height: 16),

          // Two-Factor Authentication Card
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.shield, color: _is2FAEnabled ? Colors.green : Colors.grey),
                      const SizedBox(width: 8),
                      const Expanded(
                        child: Text(
                          'Two-Factor Authentication',
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                        ),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: _is2FAEnabled ? Colors.green.withOpacity(0.1) : Colors.grey.withOpacity(0.1),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Text(
                          _is2FAEnabled ? 'Enabled' : 'Disabled',
                          style: TextStyle(
                            color: _is2FAEnabled ? Colors.green : Colors.grey,
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  if (_is2FAEnabled) ...[
                    const Text(
                      'Your account is protected with two-factor authentication.',
                      style: TextStyle(fontSize: 13, color: Colors.grey),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: OutlinedButton(
                        onPressed: _disable2FA,
                        style: OutlinedButton.styleFrom(foregroundColor: Colors.red),
                        child: const Text('Disable 2FA'),
                      ),
                    ),
                  ] else if (_is2FASetupInProgress && _2faSecret != null) ...[
                    const Text(
                      'Enter this secret in your authenticator app (e.g. Google Authenticator):',
                      style: TextStyle(fontSize: 13, color: Colors.grey),
                    ),
                    const SizedBox(height: 8),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: Colors.grey[100],
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.grey[300]!),
                      ),
                      child: SelectableText(
                        _2faSecret!,
                        style: const TextStyle(fontFamily: 'monospace', fontSize: 16, fontWeight: FontWeight.bold),
                        textAlign: TextAlign.center,
                      ),
                    ),
                    const SizedBox(height: 16),
                    TextField(
                      controller: _2faVerifyController,
                      keyboardType: TextInputType.number,
                      textAlign: TextAlign.center,
                      maxLength: 6,
                      style: const TextStyle(fontSize: 24, fontFamily: 'monospace', letterSpacing: 8),
                      decoration: const InputDecoration(
                        labelText: 'Verification Code',
                        hintText: '000000',
                        counterText: '',
                        border: OutlineInputBorder(),
                      ),
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly,
                        LengthLimitingTextInputFormatter(6),
                      ],
                    ),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton(
                            onPressed: () {
                              setState(() {
                                _is2FASetupInProgress = false;
                                _2faSecret = null;
                                _2faQrCodeUrl = null;
                                _2faVerifyController.clear();
                              });
                            },
                            child: const Text('Cancel'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: ElevatedButton(
                            onPressed: _verify2FASetup,
                            child: const Text('Verify & Enable'),
                          ),
                        ),
                      ],
                    ),
                  ] else ...[
                    const Text(
                      'Add an extra layer of security to your account.',
                      style: TextStyle(fontSize: 13, color: Colors.grey),
                    ),
                    const SizedBox(height: 12),
                    SizedBox(
                      width: double.infinity,
                      child: ElevatedButton(
                        onPressed: _is2FASetupInProgress ? null : _start2FASetup,
                        child: _is2FASetupInProgress
                            ? const SizedBox(
                                height: 18,
                                width: 18,
                                child: CircularProgressIndicator(strokeWidth: 2),
                              )
                            : const Text('Enable 2FA'),
                      ),
                    ),
                  ],
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
