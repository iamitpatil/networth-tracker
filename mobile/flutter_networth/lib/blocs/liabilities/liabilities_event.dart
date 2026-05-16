// lib/blocs/liabilities/liabilities_event.dart
part of 'liabilities_bloc.dart';

abstract class LiabilitiesEvent extends Equatable {
  const LiabilitiesEvent();

  @override
  List<Object?> get props => [];
}

class LiabilitiesLoadRequested extends LiabilitiesEvent {}

class LiabilitiesRefreshRequested extends LiabilitiesEvent {}

class LiabilitiesDeleteRequested extends LiabilitiesEvent {
  final String id;

  const LiabilitiesDeleteRequested({required this.id});

  @override
  List<Object?> get props => [id];
}