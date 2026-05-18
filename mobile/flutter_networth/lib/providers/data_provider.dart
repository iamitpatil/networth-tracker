// lib/providers/data_provider.dart
import 'package:flutter/foundation.dart';
import '../services/api_client.dart';
import '../models/app_models.dart';

class DataProvider extends ChangeNotifier {
  // Dashboard Data
  NetWorthData? _netWorthData;
  HealthScoreData? _healthScoreData;
  List<Holding> _holdings = [];
  List<Goal> _goals = [];
  List<Liability> _liabilities = [];
  
  // UI State
  bool _isLoading = false;
  String? _error;
  int _selectedNavIndex = 0;

  // Getters
  NetWorthData? get netWorthData => _netWorthData;
  HealthScoreData? get healthScoreData => _healthScoreData;
  List<Holding> get holdings => _holdings;
  List<Goal> get goals => _goals;
  List<Liability> get liabilities => _liabilities;
  bool get isLoading => _isLoading;
  String? get error => _error;
  int get selectedNavIndex => _selectedNavIndex;

  // Computed
  double get totalHoldingsValue => _holdings.fold(0, (sum, h) => sum + (h.currentValue ?? 0));
  double get totalPnL => _holdings.fold(0, (sum, h) => sum + (h.unrealizedPnl ?? 0));
  int get holdingsCount => _holdings.length;

  void setNavIndex(int index) {
    _selectedNavIndex = index;
    notifyListeners();
  }

  Future<void> loadDashboardData() async {
    _isLoading = true;
    _error = null;
    notifyListeners();

    try {
      // Load all dashboard data in parallel
      final results = await Future.wait([
        ApiClient.get('/net-worth/breakdown'),
        ApiClient.get('/net-worth/health-score'),
        ApiClient.get('/portfolio/holdings'),
        ApiClient.get('/goals'),
        ApiClient.get('/liabilities'),
      ]);

      _netWorthData = NetWorthData.fromJson(results[0]);
      _healthScoreData = HealthScoreData.fromJson(results[1]);
      
      if (results[2] is List) {
        _holdings = (results[2] as List).map((h) => Holding.fromJson(h)).toList();
      }
      
      if (results[3] is List) {
        _goals = (results[3] as List).map((g) => Goal.fromJson(g)).toList();
      }
      
      if (results[4] is List) {
        _liabilities = (results[4] as List).map((l) => Liability.fromJson(l)).toList();
      }

      _error = null;
    } catch (e) {
      _error = e.toString();
      if (kDebugMode) {
        print('Error loading dashboard: $e');
      }
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> refreshPrices() async {
    try {
      await ApiClient.post('/portfolio/refresh-prices');
      await loadDashboardData(); // Reload all data
    } catch (e) {
      _error = e.toString();
      notifyListeners();
    }
  }

  Future<void> loadHoldings() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await ApiClient.get('/portfolio/holdings');
      if (response is List) {
        _holdings = response.map((h) => Holding.fromJson(h)).toList();
      }
      _error = null;
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> addHolding(Map<String, dynamic> data) async {
    try {
      await ApiClient.post('/portfolio/holdings', body: data);
      await loadHoldings(); // Reload to get updated list
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      rethrow;
    }
  }

  Future<void> deleteHolding(String id) async {
    try {
      await ApiClient.delete('/portfolio/holdings/$id');
      _holdings.removeWhere((h) => h.id == id);
      notifyListeners();
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      rethrow;
    }
  }

  Future<void> loadGoals() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await ApiClient.get('/goals');
      if (response is List) {
        _goals = response.map((g) => Goal.fromJson(g)).toList();
      }
      _error = null;
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> addGoal(Map<String, dynamic> data) async {
    try {
      await ApiClient.post('/goals', body: data);
      await loadGoals();
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      rethrow;
    }
  }

  Future<void> deleteGoal(String id) async {
    try {
      await ApiClient.delete('/goals/$id');
      _goals.removeWhere((g) => g.id == id);
      notifyListeners();
    } catch (e) {
      _error = e.toString();
      notifyListeners();
      rethrow;
    }
  }

  Future<void> loadLiabilities() async {
    _isLoading = true;
    notifyListeners();

    try {
      final response = await ApiClient.get('/liabilities');
      if (response is List) {
        _liabilities = response.map((l) => Liability.fromJson(l)).toList();
      }
      _error = null;
    } catch (e) {
      _error = e.toString();
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  List<Holding> getHoldingsByType(String? type) {
    if (type == null || type == 'all') return _holdings;
    return _holdings.where((h) => h.assetType == type).toList();
  }

  List<Map<String, dynamic>> get assetAllocation {
    if (_netWorthData == null) return [];
    return _netWorthData!.assetAllocation;
  }

  void clearError() {
    _error = null;
    notifyListeners();
  }
}