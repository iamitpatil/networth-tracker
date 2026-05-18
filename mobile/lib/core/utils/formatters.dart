// lib/core/utils/formatters.dart
class Formatters {
  /// Format currency in Indian style (₹ with Cr/L/K suffixes)
  static String currency(double? value) {
    if (value == null) return '₹0';
    final absValue = value.abs();
    final sign = value < 0 ? '-' : '';
    if (absValue >= 10000000) return '$sign₹${(absValue / 10000000).toStringAsFixed(2)}Cr';
    if (absValue >= 100000) return '$sign₹${(absValue / 100000).toStringAsFixed(2)}L';
    if (absValue >= 1000) return '$sign₹${(absValue / 1000).toStringAsFixed(1)}K';
    return '$sign₹${absValue.toStringAsFixed(0)}';
  }

  /// Format currency with full precision (no abbreviation)
  static String currencyFull(double? value) {
    if (value == null) return '₹0';
    final absValue = value.abs();
    final sign = value < 0 ? '-' : '';
    // Indian number system: 1,23,45,678
    final formatted = absValue.toStringAsFixed(2);
    final parts = formatted.split('.');
    final whole = parts[0];
    final decimal = parts.length > 1 ? '.${parts[1]}' : '';
    
    // Add commas in Indian style
    String result = '';
    for (int i = 0; i < whole.length; i++) {
      if (i == 3) result = ',$result';
      else if (i > 3 && (i - 3) % 2 == 0) result = ',$result';
      result = '${whole[whole.length - 1 - i]}$result';
    }
    
    return '$sign₹$result$decimal';
  }

  /// Format percentage with + or - sign
  static String percentage(double? value, {int decimals = 2}) {
    if (value == null) return '0%';
    final sign = value >= 0 ? '+' : '';
    return '$sign${value.toStringAsFixed(decimals)}%';
  }

  /// Format file size from bytes
  static String fileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
    return '${(bytes / 1024 / 1024 / 1024).toStringAsFixed(1)} GB';
  }

  /// Format date in dd MMM yyyy format
  static String date(String? dateStr) {
    if (dateStr == null || dateStr.isEmpty) return '';
    try {
      final date = DateTime.parse(dateStr);
      const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      return '${date.day.toString().padLeft(2, '0')} ${months[date.month - 1]} ${date.year}';
    } catch (e) {
      return dateStr.split('T').first;
    }
  }

  /// Format time as HH:mm AM/PM
  static String time(DateTime dateTime) {
    final hour = dateTime.hour > 12 ? dateTime.hour - 12 : (dateTime.hour == 0 ? 12 : dateTime.hour);
    final minute = dateTime.minute.toString().padLeft(2, '0');
    final period = dateTime.hour >= 12 ? 'PM' : 'AM';
    return '$hour:$minute $period';
  }

  /// Get safe double from dynamic value
  static double safeDouble(dynamic value) {
    if (value == null) return 0;
    if (value is num) return value.toDouble();
    if (value is String) return double.tryParse(value) ?? 0;
    return 0;
  }
}
