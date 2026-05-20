// lib/features/auth/login_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_networth/core/services/api_client.dart';
import 'package:flutter_networth/data/models/app_models.dart';

class LoginScreen extends StatefulWidget {
  final VoidCallback onLoginSuccess;
  final VoidCallback? onShowRegister;

  const LoginScreen({super.key, required this.onLoginSuccess, this.onShowRegister});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _totpController = TextEditingController();
  bool _isLoading = false;
  bool _needs2FA = false;
  bool _obscurePassword = true;
  String? _errorMessage;

  // Design constants
  static const _primaryBlue = Color(0xFF3B82F6);
  static const _darkText = Color(0xFF1E293B);
  static const _mutedText = Color(0xFF64748B);
  static const _inputFill = Color(0xFFF8FAFC);
  static const _inputBorder = Color(0xFFE2E8F0);

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _totpController.dispose();
    super.dispose();
  }

  Future<void> _login() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final body = <String, dynamic>{
        'email': _emailController.text,
        'password': _passwordController.text,
      };
      if (_needs2FA) {
        body['twoFactorCode'] = _totpController.text;
      }

      final response = await ApiClient.post('/auth/login', body: body);

      await ApiClient.setToken(response['accessToken']);
      // Save user info from response
      await ApiClient.setUser({
        'name': response['name'] ?? '',
        'email': response['email'] ?? '',
        'userId': response['userId'] ?? '',
      });

      widget.onLoginSuccess();
    } catch (e) {
      final errorMsg = e.toString().toLowerCase();
      if (errorMsg.contains('two-factor') && errorMsg.contains('required')) {
        setState(() {
          _needs2FA = true;
        });
      } else {
        setState(() {
          _errorMessage = _needs2FA ? 'Invalid verification code' : 'Invalid email or password';
        });
      }
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  void _fillDemoCredentials() {
    _emailController.text = 'test@example.com';
    _passwordController.text = 'password123';
  }

  InputDecoration _buildInputDecoration({
    required String hint,
    required IconData prefixIcon,
    Widget? suffix,
  }) {
    return InputDecoration(
      hintText: hint,
      hintStyle: const TextStyle(color: _mutedText, fontSize: 15),
      prefixIcon: Icon(prefixIcon, color: _mutedText, size: 20),
      suffixIcon: suffix,
      filled: true,
      fillColor: _inputFill,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _inputBorder),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _inputBorder),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _primaryBlue, width: 2),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 48),

              // --- Logo area ---
              Center(
                child: Column(
                  children: [
                    Container(
                      width: 72,
                      height: 72,
                      decoration: BoxDecoration(
                        gradient: const LinearGradient(
                          colors: [Color(0xFF3B82F6), Color(0xFF4F46E5)],
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        ),
                        borderRadius: BorderRadius.circular(18),
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF3B82F6).withValues(alpha: 0.3),
                            blurRadius: 20,
                            offset: const Offset(0, 8),
                          ),
                        ],
                      ),
                      child: const Icon(
                        Icons.bar_chart_rounded,
                        color: Colors.white,
                        size: 36,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: const [
                        Text(
                          'NetWorth',
                          style: TextStyle(
                            fontSize: 26,
                            fontWeight: FontWeight.w800,
                            color: _primaryBlue,
                            letterSpacing: -0.5,
                          ),
                        ),
                        SizedBox(width: 4),
                        Text(
                          'Tracker',
                          style: TextStyle(
                            fontSize: 26,
                            fontWeight: FontWeight.w800,
                            color: _mutedText,
                            letterSpacing: -0.5,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'Your Local Investment Manager',
                      style: TextStyle(
                        fontSize: 14,
                        color: _mutedText,
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 40),

              // --- Welcome text ---
              const Text(
                'Welcome back',
                style: TextStyle(
                  fontSize: 22,
                  fontWeight: FontWeight.w700,
                  color: _darkText,
                ),
              ),
              const SizedBox(height: 4),
              const Text(
                'Sign in to continue',
                style: TextStyle(
                  fontSize: 15,
                  color: _mutedText,
                ),
              ),

              const SizedBox(height: 24),

              // --- Error message ---
              if (_errorMessage != null) ...[
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFEF2F2),
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: const Color(0xFFFECACA)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.error_outline, color: Color(0xFFEF4444), size: 20),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          _errorMessage!,
                          style: const TextStyle(color: Color(0xFFDC2626), fontSize: 14),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
              ],

              // --- Email field ---
              TextField(
                controller: _emailController,
                keyboardType: TextInputType.emailAddress,
                style: const TextStyle(fontSize: 15, color: _darkText),
                decoration: _buildInputDecoration(
                  hint: 'Email address',
                  prefixIcon: Icons.email_outlined,
                ),
              ),
              const SizedBox(height: 16),

              // --- Password field ---
              TextField(
                controller: _passwordController,
                obscureText: _obscurePassword,
                style: const TextStyle(fontSize: 15, color: _darkText),
                decoration: _buildInputDecoration(
                  hint: 'Password',
                  prefixIcon: Icons.lock_outline,
                  suffix: GestureDetector(
                    onTap: () => setState(() => _obscurePassword = !_obscurePassword),
                    child: Icon(
                      _obscurePassword ? Icons.visibility_off_outlined : Icons.visibility_outlined,
                      color: _mutedText,
                      size: 20,
                    ),
                  ),
                ),
              ),

              // --- 2FA TOTP field ---
              AnimatedSize(
                duration: const Duration(milliseconds: 300),
                curve: Curves.easeInOut,
                child: _needs2FA
                    ? Column(
                        children: [
                          const SizedBox(height: 16),
                          Text(
                            'Enter the 6-digit code from your authenticator app',
                            style: const TextStyle(color: _mutedText, fontSize: 14),
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: 12),
                          TextField(
                            controller: _totpController,
                            keyboardType: TextInputType.number,
                            textAlign: TextAlign.center,
                            maxLength: 6,
                            style: const TextStyle(
                              fontSize: 24,
                              fontFamily: 'monospace',
                              letterSpacing: 8,
                              fontWeight: FontWeight.bold,
                              color: _darkText,
                            ),
                            decoration: InputDecoration(
                              hintText: '000000',
                              hintStyle: TextStyle(
                                color: _mutedText.withValues(alpha: 0.4),
                                fontSize: 24,
                                fontFamily: 'monospace',
                                letterSpacing: 8,
                              ),
                              counterText: '',
                              prefixIcon: const Icon(Icons.shield_outlined, color: _mutedText, size: 20),
                              filled: true,
                              fillColor: _inputFill,
                              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(color: _inputBorder),
                              ),
                              enabledBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(color: _inputBorder),
                              ),
                              focusedBorder: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                                borderSide: const BorderSide(color: _primaryBlue, width: 2),
                              ),
                            ),
                            inputFormatters: [
                              FilteringTextInputFormatter.digitsOnly,
                              LengthLimitingTextInputFormatter(6),
                            ],
                          ),
                          const SizedBox(height: 4),
                          Center(
                            child: TextButton(
                              onPressed: () {
                                setState(() {
                                  _needs2FA = false;
                                  _totpController.clear();
                                  _errorMessage = null;
                                });
                              },
                              style: TextButton.styleFrom(foregroundColor: _primaryBlue),
                              child: const Text(
                                'Back to login',
                                style: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
                              ),
                            ),
                          ),
                        ],
                      )
                    : const SizedBox.shrink(),
              ),

              const SizedBox(height: 24),

              // --- Sign in button ---
              SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton(
                  onPressed: _isLoading ? null : _login,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _primaryBlue,
                    disabledBackgroundColor: _primaryBlue.withValues(alpha: 0.6),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    elevation: 0,
                  ),
                  child: _isLoading
                      ? const SizedBox(
                          height: 22,
                          width: 22,
                          child: CircularProgressIndicator(
                            strokeWidth: 2.5,
                            valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                          ),
                        )
                      : const Text(
                          'Sign In',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                ),
              ),

              const SizedBox(height: 24),

              // --- Demo credentials card ---
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: const Color(0xFFF1F5F9),
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: _inputBorder),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.info_outline, color: _mutedText, size: 18),
                        const SizedBox(width: 8),
                        const Text(
                          'Demo Credentials',
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            color: _darkText,
                            fontSize: 14,
                          ),
                        ),
                        const Spacer(),
                        GestureDetector(
                          onTap: _fillDemoCredentials,
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                            decoration: BoxDecoration(
                              color: _primaryBlue.withValues(alpha: 0.1),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: const Text(
                              'Fill',
                              style: TextStyle(
                                color: _primaryBlue,
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    const Text(
                      'test@example.com  /  password123',
                      style: TextStyle(
                        color: _mutedText,
                        fontSize: 13,
                        fontFamily: 'monospace',
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 24),

              // --- Register link ---
              Center(
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text(
                      "Don't have an account?",
                      style: TextStyle(color: _mutedText, fontSize: 14),
                    ),
                    TextButton(
                      onPressed: () => widget.onShowRegister?.call(),
                      style: TextButton.styleFrom(
                        foregroundColor: _primaryBlue,
                        padding: const EdgeInsets.symmetric(horizontal: 6),
                        minimumSize: Size.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                      child: const Text(
                        'Create one',
                        style: TextStyle(
                          fontWeight: FontWeight.w600,
                          fontSize: 14,
                        ),
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }
}
