// lib/blocs/tax/tax_bloc.dart
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_networth/models/tax_model.dart';
import 'package:flutter_networth/services/api_client.dart';

part 'tax_event.dart';
part 'tax_state.dart';

class TaxBloc extends Bloc<TaxEvent, TaxState> {
  final ApiClient _apiClient;

  TaxBloc({required ApiClient apiClient})
      : _apiClient = apiClient,
        super(TaxInitial()) {
    on<TaxLoadRequested>(_onLoadRequested);
    on<TaxRefreshRequested>(_onRefreshRequested);
  }

  Future<void> _onLoadRequested(
    TaxLoadRequested event,
    Emitter<TaxState> emit,
  ) async {
    emit(TaxLoading());
    try {
      // Fetch tax data from API
      // For now using sample data
      await Future.delayed(const Duration(milliseconds: 500));
      
      final summary = TaxSummary(
        financialYear: '2024-25',
        stcg: CapitalGains(equity: 25000, debt: 5000),
        ltcg: CapitalGains(equity: 100000, debt: 30000),
        taxLiability: TaxLiability(
          stcgTax: 5000,
          ltcgTax: 12500,
          total: 17500,
        ),
      );
      
      final harvestingOpportunities = [
        TaxHarvestingOpportunity(
          id: '1',
          holdingId: 'h1',
          symbol: 'RELIANCE',
          unrealizedGain: 50000,
          daysToLtcg: 0,
          action: 'HARVEST',
          reason: 'Book tax-free gain',
        ),
      ];
      
      final deduction80C = Deduction80C(
        limit: 150000,
        utilized: 120000,
        remaining: 30000,
        breakdown: [
          DeductionItem(section: '80C', category: 'PPF', amount: 50000),
          DeductionItem(section: '80C', category: 'ELSS', amount: 50000),
          DeductionItem(section: '80C', category: 'Life Insurance', amount: 20000),
        ],
      );
      
      emit(TaxLoaded(
        summary: summary,
        harvestingOpportunities: harvestingOpportunities,
        deduction80C: deduction80C,
      ));
    } catch (e) {
      emit(TaxError(message: e.toString()));
    }
  }

  Future<void> _onRefreshRequested(
    TaxRefreshRequested event,
    Emitter<TaxState> emit,
  ) async {
    if (state is TaxLoaded) {
      final currentState = state as TaxLoaded;
      try {
        // Refresh data
        add(TaxLoadRequested());
      } catch (e) {
        emit(TaxError(message: e.toString()));
        emit(currentState);
      }
    }
  }
}