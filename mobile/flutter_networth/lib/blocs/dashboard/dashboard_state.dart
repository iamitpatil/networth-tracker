// lib/blocs/dashboard/dashboard_state.dart
part of 'dashboard_bloc.dart';

abstract class DashboardState extends Equatable {
  const DashboardState();

  @override
  List<Object?> get props => [];
}

class DashboardInitial extends DashboardState {}

class DashboardLoading extends DashboardState {}

class DashboardLoaded extends DashboardState {
  final NetWorthBreakdown breakdown;
  final HealthScore healthScore;
  final List<Holding> holdings;

  const DashboardLoaded({
    required this.breakdown,
    required this.healthScore,
    required this.holdings,
  });

  @override
  List<Object?> get props => [breakdown, healthScore, holdings];

  List<Holding> get topHoldings {
    return holdings
        .where((h) => h.currentValue != null)
        .toList()
      ..sort((a, b) => (b.currentValue ?? 0).compareTo(a.currentValue ?? 0));
    return holdings.take(5).toList();
  }

  double get totalPnL {
    return holdings.fold(0, (sum, h) => sum + (h.unrealizedPnl ?? 0));
  }
}

class DashboardError extends DashboardState {
  final String message;

  const DashboardError({required this.message});

  @override
  List<Object?> get props => [message];
}