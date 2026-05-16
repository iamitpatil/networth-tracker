// lib/blocs/tax/tax_state.dart
part of 'tax_bloc.dart';

abstract class TaxState extends Equatable {
  const TaxState();

  @override
  List<Object?> get props => [];
}

class TaxInitial extends TaxState {}

class TaxLoading extends TaxState {}

class TaxLoaded extends TaxState {
  final TaxSummary summary;
  final List<TaxHarvestingOpportunity> harvestingOpportunities;
  final Deduction80C? deduction80C;

  const TaxLoaded({
    required this.summary,
    required this.harvestingOpportunities,
    this.deduction80C,
  });

  @override
  List<Object?> get props => [summary, harvestingOpportunities, deduction80C];
}

class TaxError extends TaxState {
  final String message;

  const TaxError({required this.message});

  @override
  List<Object?> get props => [message];
}