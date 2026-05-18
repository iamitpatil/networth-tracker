// lib/core/constants/app_config.dart
class AppConfig {
  static const String appName = 'Net Worth Tracker';
  static const String appVersion = '1.0.0';
  
  // API
  static const String apiBaseUrl = 'http://localhost:8080/api/v1';
  static const Duration apiTimeout = Duration(seconds: 30);
  
  // Storage keys
  static const String tokenKey = 'token';
  static const String userKey = 'user';
}
