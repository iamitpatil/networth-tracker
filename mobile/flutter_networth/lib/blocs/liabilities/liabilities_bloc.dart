// lib/blocs/liabilities/liabilities_bloc.dart
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_networth/models/liability_model.dart';

part 'liabilities_event.dart';
part 'liabilities_state.dart';

class LiabilitiesBloc extends Bloc<LiabilitiesEvent, LiabilitiesState> {
  LiabilitiesBloc() : super(LiabilitiesInitial()) {
    on<LiabilitiesLoadRequested>(_onLoadRequested);
    on<LiabilitiesRefreshRequested>(_onRefreshRequested);
  }

  Future<void> _onLoadRequested(
    LiabilitiesLoadRequested event,
    Emitter<LiabilitiesState> emit,
  ) async {
    emit(LiabilitiesLoading());
    try {
      // Sample data - replace with API call
      await Future.delayed(const Duration(milliseconds: 500));
      
      final liabilities = [
        Liability(
          id: '1',
          liabilityType: LiabilityType.homeLoan,
          lender: 'HDFC Bank',
          originalAmount: 5000000,
          outstandingAmount: 3500000,
          interestRate: 8.5,
          monthlyEmi: 45000,
          startDate: '2020-01-01',
          endDate: '2040-01-01',
          nextEmiDate: DateTime.now().add(const Duration(days: 5)).toIso8601String(),
        ),
        Liability(
          id: '2',
          liabilityType: LiabilityType.carLoan,
          lender: 'ICICI Bank',
          originalAmount: 800000,
          outstandingAmount: 300000,
          interestRate: 9.0,
          monthlyEmi: 15000,
          startDate: '2022-01-01',
          endDate: '2025-01-01',
          nextEmiDate: DateTime.now().add(const Duration(days: 10)).toIso8601String(),
        ),
      ];

      final totalOutstanding = liabilities.fold<double>(
        0,
        (sum, l) => sum + l.outstandingAmount,
      );
      
      final totalMonthlyEmi = liabilities.fold<double>(
        0,
        (sum, l) => sum + (l.monthlyEmi ?? 0),
      );

      emit(LiabilitiesLoaded(
        liabilities: liabilities,
        totalOutstanding: totalOutstanding,
        totalMonthlyEmi: totalMonthlyEmi,
      ));
    } catch (e) {
      emit(LiabilitiesError(message: e.toString()));
    }
  }

  Future<void> _onRefreshRequested(
    LiabilitiesRefreshRequested event,
    Emitter<LiabilitiesState> emit,
  ) async {
    add(LiabilitiesLoadRequested());
  }
}