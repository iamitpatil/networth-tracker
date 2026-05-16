// lib/blocs/tax/tax_event.dart
part of 'tax_bloc.dart';

abstract class TaxEvent extends Equatable {
  const TaxEvent();

  @override
  List<Object?> get props => [];
}

class TaxLoadRequested extends TaxEvent {}

class TaxRefreshRequested extends TaxEvent {}

class TaxFinancialYearChanged extends TaxEvent {
  final String year;

  const TaxFinancialYearChanged({required this.year});

  @override
  List<Object?> get props => [year];
}