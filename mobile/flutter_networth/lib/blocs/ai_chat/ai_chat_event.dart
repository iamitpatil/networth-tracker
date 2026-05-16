// lib/blocs/ai_chat/ai_chat_event.dart
part of 'ai_chat_bloc.dart';

abstract class AIChatEvent extends Equatable {
  const AIChatEvent();

  @override
  List<Object?> get props => [];
}

class AIChatLoadHistoryRequested extends AIChatEvent {}

class AIChatMessageSent extends AIChatEvent {
  final String message;

  const AIChatMessageSent({required this.message});

  @override
  List<Object?> get props => [message];
}

class AIChatClearHistoryRequested extends AIChatEvent {}