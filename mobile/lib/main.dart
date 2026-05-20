// lib/main.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
// google_fonts removed — using system fonts for Flutter 3.44 compat
import 'package:provider/provider.dart';
import 'package:flutter_networth/features/auth/login_screen.dart';
import 'package:flutter_networth/features/auth/register_screen.dart';
import 'package:flutter_networth/features/dashboard/dashboard_screen.dart';
import 'package:flutter_networth/features/portfolio/holdings_screen.dart';
import 'package:flutter_networth/features/networth/networth_screen.dart';
import 'package:flutter_networth/features/networth/goals_screen.dart';
import 'package:flutter_networth/features/home/more_screen.dart';
import 'package:flutter_networth/core/services/api_client.dart';
import 'package:flutter_networth/providers/data_provider.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);
  
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.dark,
      systemNavigationBarColor: Colors.white,
      systemNavigationBarIconBrightness: Brightness.dark,
    ),
  );
  
  runApp(
    ChangeNotifierProvider(
      create: (context) => DataProvider(),
      child: const NetWorthApp(),
    ),
  );
}

class NetWorthApp extends StatelessWidget {
  const NetWorthApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Net Worth Tracker',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF3B82F6),
          brightness: Brightness.light,
          surfaceTint: Colors.transparent,
        ),
        textTheme: const TextTheme(),
        appBarTheme: const AppBarTheme(
          centerTitle: true,
          elevation: 0,
          scrolledUnderElevation: 0.5,
          backgroundColor: Colors.white,
          titleTextStyle: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w600,
            color: Color(0xFF1E293B),
          ),
        ),
        cardTheme: CardThemeData(
          elevation: 0,
          color: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: const BorderSide(color: Color(0xFFE2E8F0), width: 0.5),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: const Color(0xFFF8FAFC),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Color(0xFFE2E8F0)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: Color(0xFF3B82F6), width: 1.5),
          ),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: const Color(0xFF3B82F6),
            foregroundColor: Colors.white,
            padding: const EdgeInsets.symmetric(vertical: 16),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
        ),
        floatingActionButtonTheme: FloatingActionButtonThemeData(
          backgroundColor: const Color(0xFF3B82F6),
          foregroundColor: Colors.white,
          elevation: 2,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
        bottomSheetTheme: const BottomSheetThemeData(
          backgroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
          ),
        ),
        dividerTheme: const DividerThemeData(
          color: Color(0xFFF1F5F9),
          thickness: 1,
        ),
      ),
      home: const AuthWrapper(),
    );
  }
}

class AuthWrapper extends StatefulWidget {
  const AuthWrapper({super.key});

  @override
  State<AuthWrapper> createState() => _AuthWrapperState();
}

class _AuthWrapperState extends State<AuthWrapper> {
  bool _isLoading = true;
  bool _isAuthenticated = false;
  bool _showRegister = false;

  @override
  void initState() {
    super.initState();
    _checkAuth();
  }

  Future<void> _checkAuth() async {
    final isAuth = await ApiClient.isAuthenticated();
    setState(() {
      _isAuthenticated = isAuth;
      _isLoading = false;
    });
  }

  void _onAuthSuccess() {
    setState(() {
      _isAuthenticated = true;
      _showRegister = false;
    });
  }

  void _onLogout() {
    ApiClient.clearToken();
    setState(() {
      _isAuthenticated = false;
      _showRegister = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return const Scaffold(
        body: Center(child: CircularProgressIndicator()),
      );
    }

    if (_isAuthenticated) {
      return MainNavigationScreen(onLogout: _onLogout);
    } else if (_showRegister) {
      return RegisterScreen(
        onRegisterSuccess: _onAuthSuccess,
        onBackToLogin: () => setState(() => _showRegister = false),
      );
    } else {
      return LoginScreen(
        onLoginSuccess: _onAuthSuccess,
        onShowRegister: () => setState(() => _showRegister = true),
      );
    }
  }
}

class MainNavigationScreen extends StatefulWidget {
  final VoidCallback onLogout;

  const MainNavigationScreen({super.key, required this.onLogout});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  int _selectedIndex = 0;

  final GlobalKey<HoldingsScreenState> _holdingsKey = GlobalKey<HoldingsScreenState>();
  final GlobalKey<GoalsScreenState> _goalsKey = GlobalKey<GoalsScreenState>();

  final List<String> _titles = [
    'Dashboard',
    'Holdings',
    'Net Worth',
    'Goals',
    'More',
  ];

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  Widget _buildBody() {
    // Use IndexedStack to preserve state, but wrap each screen in ScrollConfiguration
    // to ensure scrolling works properly
    return IndexedStack(
      index: _selectedIndex,
      children: [
        const DashboardScreen(),
        HoldingsScreen(key: _holdingsKey),
        const NetWorthScreen(),
        GoalsScreen(key: _goalsKey),
        const MoreScreen(),
      ],
    );
  }

  Widget? _buildFab() {
    switch (_selectedIndex) {
      case 1:
        return FloatingActionButton.extended(
          onPressed: () {
            _holdingsKey.currentState?.showAddHoldingDialog();
          },
          icon: const Icon(Icons.add),
          label: const Text('Add Holding'),
          backgroundColor: const Color(0xFF3B82F6),
        );
      case 3:
        return FloatingActionButton.extended(
          onPressed: () {
            _goalsKey.currentState?.showAddGoalDialog();
          },
          icon: const Icon(Icons.add),
          label: const Text('Add Goal'),
          backgroundColor: const Color(0xFF10B981),
        );
      default:
        return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_titles[_selectedIndex]),
        actions: _selectedIndex == 0 ? [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: () {
              context.read<DataProvider>().loadDashboardData();
            },
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: widget.onLogout,
          ),
        ] : null,
      ),
      body: _buildBody(),
      floatingActionButton: _buildFab(),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: _onItemTapped,
        height: 65,
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        indicatorColor: const Color(0xFF3B82F6).withAlpha(25),
        backgroundColor: Colors.white,
        surfaceTintColor: Colors.transparent,
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.dashboard_outlined),
            selectedIcon: const Icon(Icons.dashboard, color: Color(0xFF3B82F6)),
            label: 'Dashboard',
          ),
          NavigationDestination(
            icon: const Icon(Icons.pie_chart_outline),
            selectedIcon: const Icon(Icons.pie_chart, color: Color(0xFF3B82F6)),
            label: 'Holdings',
          ),
          NavigationDestination(
            icon: const Icon(Icons.account_balance_wallet_outlined),
            selectedIcon: const Icon(Icons.account_balance_wallet, color: Color(0xFF3B82F6)),
            label: 'Net Worth',
          ),
          NavigationDestination(
            icon: const Icon(Icons.flag_outlined),
            selectedIcon: const Icon(Icons.flag, color: Color(0xFF3B82F6)),
            label: 'Goals',
          ),
          NavigationDestination(
            icon: const Icon(Icons.more_horiz_outlined),
            selectedIcon: const Icon(Icons.more_horiz, color: Color(0xFF3B82F6)),
            label: 'More',
          ),
        ],
      ),
    );
  }
}
