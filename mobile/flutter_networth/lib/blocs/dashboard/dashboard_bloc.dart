// lib/blocs/dashboard/dashboard_bloc.dart
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_networth/models/networth_model.dart';
import 'package:flutter_networth/models/holding_model.dart';
import 'package:flutter_networth/services/api_client.dart';

part 'dashboard_event.dart';
part 'dashboard_state.dart';

class DashboardBloc extends Bloc<DashboardEvent, DashboardState> {
  final ApiClient _apiClient;

  DashboardBloc({required ApiClient apiClient})
      : _apiClient = apiClient,
        super(DashboardInitial()) {
    on<DashboardLoadRequested>(_onLoadRequested);
    on<DashboardRefreshRequested>(_onRefreshRequested);
  }

  Future<void> _onLoadRequested(
    DashboardLoadRequested event,
    Emitter<DashboardState> emit,
  ) async {
    emit(DashboardLoading());
    try {
      final breakdown = await _apiClient.getNetWorthBreakdown();
      final healthScore = await _apiClient.getHealthScore();
      final holdings = await _apiClient.getHoldings();
      
      emit(DashboardLoaded(
        breakdown: breakdown,
        healthScore: healthScore,
        holdings: holdings,
      ));
    } catch (e) {
      emit(DashboardError(message: e.toString()));
    }
  }

  Future<void> _onRefreshRequested(
    DashboardRefreshRequested event,
    Emitter<DashboardState> emit,
  ) async {
    if (state is DashboardLoaded) {
      final currentState = state as DashboardLoaded;
      try {
        await _apiClient.refreshPrices();
        final breakdown = await _apiClient.getNetWorthBreakdown();
        final healthScore = await _apiClient.getHealthScore();
        final holdings = await _apiClient.getHoldings();
        
        emit(DashboardLoaded(
          breakdown: breakdown,
          healthScore: healthScore,
          holdings: holdings,
        ));
      } catch (e) {
        emit(DashboardError(message: e.toString()));
        emit(currentState);
      }
    }
  }
}