// lib/core/utils/validators.dart
class Validators {
  static String? required(String? value, {String message = 'Required'}) {
    if (value == null || value.trim().isEmpty) return message;
    return null;
  }

  static String? email(String? value) {
    if (value == null || value.isEmpty) return 'Email is required';
    if (!value.contains('@') || !value.contains('.')) return 'Invalid email';
    return null;
  }

  static String? password(String? value, {int minLength = 8}) {
    if (value == null || value.isEmpty) return 'Password is required';
    if (value.length < minLength) return 'Min $minLength characters';
    return null;
  }

  static String? positiveNumber(String? value, {String fieldName = 'Value'}) {
    if (value == null || value.isEmpty) return '$fieldName is required';
    final num = double.tryParse(value);
    if (num == null) return 'Invalid number';
    if (num <= 0) return '$fieldName must be positive';
    return null;
  }

  static String? number(String? value, {String fieldName = 'Value'}) {
    if (value == null || value.isEmpty) return '$fieldName is required';
    if (double.tryParse(value) == null) return 'Invalid number';
    return null;
  }
}
