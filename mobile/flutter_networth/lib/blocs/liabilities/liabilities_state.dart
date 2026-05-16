// lib/blocs/liabilities/liabilities_state.dart
part of 'liabilities_bloc.dart';

abstract class LiabilitiesState extends Equatable {
  const LiabilitiesState();

  @override
  List<Object?> get props => [];
}

class LiabilitiesInitial extends LiabilitiesState {}

class LiabilitiesLoading extends LiabilitiesState {}

class LiabilitiesLoaded extends LiabilitiesState {
  final List<Liability> liabilities;
  final double totalOutstanding;
  final double totalMonthlyEmi;

  const LiabilitiesLoaded({
    required this.liabilities,
    required this.totalOutstanding,
    required this.totalMonthlyEmi,
  });

  @override
  List<Object?> get props => [liabilities, totalOutstanding, totalMonthlyEmi];
}

class LiabilitiesError extends LiabilitiesState {
  final String message;

  const LiabilitiesError({required this.message});

  @override
  List<Object?> get props => [message];
}