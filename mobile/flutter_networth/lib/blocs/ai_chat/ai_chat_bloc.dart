// lib/blocs/ai_chat/ai_chat_bloc.dart
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:equatable/equatable.dart';
import 'package:flutter_networth/models/ai_chat_model.dart';

part 'ai_chat_event.dart';
part 'ai_chat_state.dart';

class AIChatBloc extends Bloc<AIChatEvent, AIChatState> {
  AIChatBloc() : super(AIChatInitial()) {
    on<AIChatLoadHistoryRequested>(_onLoadHistoryRequested);
    on<AIChatMessageSent>(_onMessageSent);
    on<AIChatClearHistoryRequested>(_onClearHistoryRequested);
  }

  Future<void> _onLoadHistoryRequested(
    AIChatLoadHistoryRequested event,
    Emitter<AIChatState> emit,
  ) async {
    emit(AIChatLoading());
    try {
      // Load from API or local storage
      await Future.delayed(const Duration(milliseconds: 300));
      
      // Sample welcome message
      final messages = [
        ChatMessage.assistant(
          content: 'Hello! I\'m your AI financial assistant. I can help you with:\n\n'
              '• Portfolio analysis and insights\n'
              '• Tax optimization strategies\n'
              '• Investment recommendations\n'
              '• Financial goal planning\n\n'
              'What would you like to know?',
        ),
      ];
      
      emit(AIChatLoaded(messages: messages));
    } catch (e) {
      emit(AIChatError(message: e.toString()));
    }
  }

  Future<void> _onMessageSent(
    AIChatMessageSent event,
    Emitter<AIChatState> emit,
  ) async {
    if (state is! AIChatLoaded) return;
    
    final currentState = state as AIChatLoaded;
    final userMessage = ChatMessage.user(content: event.message);
    
    // Add user message immediately
    emit(AIChatLoaded(
      messages: [...currentState.messages, userMessage],
      isTyping: true,
    ));
    
    // Simulate AI response
    await Future.delayed(const Duration(seconds: 1));
    
    final aiResponse = _generateResponse(event.message);
    
    emit(AIChatLoaded(
      messages: [...currentState.messages, userMessage, aiResponse],
    ));
  }

  Future<void> _onClearHistoryRequested(
    AIChatClearHistoryRequested event,
    Emitter<AIChatState> emit,
  ) async {
    emit(AIChatLoading());
    await Future.delayed(const Duration(milliseconds: 200));
    emit(const AIChatLoaded(messages: []));
  }

  ChatMessage _generateResponse(String userMessage) {
    final lowerMessage = userMessage.toLowerCase();
    
    if (lowerMessage.contains('net worth') || lowerMessage.contains('networth')) {
      return ChatMessage.assistant(
        content: 'Your current net worth is **₹50,00,000**\n\n'
            '• **Assets**: ₹60,00,000\n'
            '• **Liabilities**: ₹10,00,000\n\n'
            'Your net worth has grown by 12% this year. Great job! 🎉',
      );
    }
    
    if (lowerMessage.contains('stock') || lowerMessage.contains('equity')) {
      return ChatMessage.assistant(
        content: 'Here are your top performing stocks:\n\n'
            '1. **RELIANCE** - +18.5% (₹2,80,000)\n'
            '2. **HDFC Bank** - +12.3% (₹1,50,000)\n'
            '3. **Infosys** - +8.7% (₹95,000)\n\n'
            'Your equity portfolio is up **15.2%** overall!',
      );
    }
    
    if (lowerMessage.contains('tax') || lowerMessage.contains('save tax')) {
      return ChatMessage.assistant(
        content: 'Here are some tax optimization opportunities:\n\n'
            '• **Tax Harvesting**: Book gains in RELIANCE (₹50,000) to utilize LTCG exemption\n'
            '• **80C Investment**: You have ₹30,000 remaining. Consider ELSS or PPF\n'
            '• **NPS**: Additional ₹50,000 deduction under Section 80CCD(1B)\n\n'
            'Potential tax savings: **₹12,500**',
      );
    }
    
    return ChatMessage.assistant(
      content: 'I understand you\'re asking about "$userMessage". '
          'I\'m continuously learning to provide better insights about your portfolio. '
          'Is there something specific about your investments you\'d like to know?',
    );
  }
}