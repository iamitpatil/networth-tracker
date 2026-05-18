// lib/screens/ai_chat/ai_chat_screen.dart
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../providers/data_provider.dart';
import '../../services/api_client.dart';
import '../../models/app_models.dart';

class AIChatScreen extends StatefulWidget {
  const AIChatScreen({super.key});

  @override
  State<AIChatScreen> createState() => _AIChatScreenState();
}

class _AIChatScreenState extends State<AIChatScreen> {
  final _controller = TextEditingController();
  final List<ChatMessage> _messages = [];
  bool _isLoading = false;

  final List<Map<String, String>> _quickQuestions = [
    {'icon': 'trending_up', 'question': 'How is my portfolio performing?'},
    {'icon': 'savings', 'question': 'Should I save more?'},
    {'icon': 'analytics', 'question': 'What is my asset allocation?'},
    {'icon': 'wb_sunny', 'question': 'Any tax saving tips?'},
  ];

  void _sendMessage(String message) async {
    if (message.isEmpty) return;

    setState(() {
      _messages.add(ChatMessage(
        id: DateTime.now().millisecondsSinceEpoch.toString(),
        role: 'user',
        content: message,
        timestamp: DateTime.now(),
      ));
      _isLoading = true;
      _controller.clear();
    });

    try {
      // Simulate AI response - in production, call actual AI API
      await Future.delayed(const Duration(seconds: 1));
      
      setState(() {
        _messages.add(ChatMessage(
          id: DateTime.now().millisecondsSinceEpoch.toString(),
          role: 'assistant',
          content: _generateResponse(message),
          timestamp: DateTime.now(),
        ));
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
        _messages.add(ChatMessage(
          id: DateTime.now().millisecondsSinceEpoch.toString(),
          role: 'assistant',
          content: 'Sorry, I encountered an error. Please try again.',
          timestamp: DateTime.now(),
        ));
      });
    }
  }

  String _generateResponse(String question) {
    final provider = context.read<DataProvider>();
    final netWorth = provider.netWorthData?.netWorth ?? 0;
    final holdingsCount = provider.holdingsCount;
    final totalPnL = provider.totalPnL;

    if (question.toLowerCase().contains('portfolio') || question.toLowerCase().contains('performing')) {
      return '''Your portfolio is doing great! 

Current Status:
• Total Net Worth: ₹${(netWorth / 100000).toStringAsFixed(2)}L
• Total Holdings: $holdingsCount
• Overall P&L: ${totalPnL >= 0 ? '+' : ''}₹${(totalPnL / 1000).toStringAsFixed(1)}K

Your investments are well-diversified across multiple asset classes.''';}
    
    if (question.toLowerCase().contains('save') || question.toLowerCase().contains('saving')) {
      return '''Based on your current portfolio, here are my savings recommendations:

1. You have a healthy net worth of ₹${(netWorth / 100000).toStringAsFixed(2)}L
2. Consider increasing your emergency fund to cover 6 months of expenses
3. Your current asset allocation looks balanced

Would you like specific advice on increasing your savings rate?''';}
    
    if (question.toLowerCase().contains('asset') || question.toLowerCase().contains('allocation')) {
      final allocation = provider.assetAllocation;
      String breakdown = 'Your current asset allocation:\n\n';
      for (var item in allocation) {
        breakdown += '• ${item['name']}: ₹${(item['value'] / 100000).toStringAsFixed(2)}L\n';
      }
      breakdown += '\nThis is a good diversified mix!';
      return breakdown;}
    
    if (question.toLowerCase().contains('tax') || question.toLowerCase().contains('saving')) {
      return '''Here are some tax-saving tips for FY 2024-25:

1. Maximize your Section 80C limit of ₹1.5L
2. Consider NPS for additional ₹50K deduction under 80CCD(1B)
3. Health insurance premiums under 80D
4. ELSS mutual funds for tax saving with growth potential

Would you like detailed guidance on any of these?''';}
    
    return '''Thanks for your question! 

I'm your AI financial assistant. I can help you with:
• Portfolio analysis and insights
• Investment recommendations
• Tax planning strategies
• Financial goal tracking
• Asset allocation review

What would you like to know about your finances?''';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.psychology, color: Colors.blue),
            SizedBox(width: 8),
            Text('AI Assistant'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_outline),
            onPressed: () {
              setState(() {
                _messages.clear();
              });
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // Quick Questions
          if (_messages.isEmpty)
            Container(
              padding: const EdgeInsets.symmetric(vertical: 12),
              decoration: BoxDecoration(
                color: Colors.grey[50],
                border: Border(
                  bottom: BorderSide(color: Colors.grey[200]!),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: Text(
                      'Quick Questions',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: Colors.grey[600],
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    height: 80,
                    child: ListView.builder(
                      scrollDirection: Axis.horizontal,
                      padding: const EdgeInsets.symmetric(horizontal: 12),
                      itemCount: _quickQuestions.length,
                      itemBuilder: (context, index) {
                        return _buildQuickQuestionChip(_quickQuestions[index]);
                      },
                    ),
                  ),
                ],
              ),
            ),
          
          // Chat Messages
          Expanded(
            child: _messages.isEmpty
                ? _buildEmptyView()
                : ListView.builder(
                    reverse: true,
                    padding: const EdgeInsets.all(16),
                    itemCount: _messages.length,
                    itemBuilder: (context, index) {
                      return _buildMessageBubble(_messages[_messages.length - 1 - index]);
                    },
                  ),
          ),
          
          // Loading Indicator
          if (_isLoading)
            Container(
              padding: const EdgeInsets.all(8),
              child: Row(
                children: [
                  const SizedBox(width: 48),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    decoration: BoxDecoration(
                      color: Colors.grey[200],
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Row(
                      children: [
                        SizedBox(
                          width: 16,
                          height: 16,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            valueColor: AlwaysStoppedAnimation<Color>(Colors.grey[600]!),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          'Thinking...',
                          style: TextStyle(
                            color: Colors.grey[600],
                            fontSize: 14,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          
          // Input Area
          _buildInputArea(),
        ],
      ),
    );
  }

  Widget _buildQuickQuestionChip(Map<String, String> question) {
    return Container(
      width: 140,
      margin: const EdgeInsets.symmetric(horizontal: 4),
      child: InkWell(
        onTap: () => _sendMessage(question['question']!),
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: Colors.blue.withOpacity(0.1),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: Colors.blue.withOpacity(0.2)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                _getIconData(question['icon']!),
                size: 20,
                color: Colors.blue[700],
              ),
              const SizedBox(height: 6),
              Text(
                question['question']!,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  color: Colors.blue[900],
                ),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }

  IconData _getIconData(String iconName) {
    switch (iconName) {
      case 'account_balance_wallet':
        return Icons.account_balance_wallet;
      case 'trending_up':
        return Icons.trending_up;
      case 'savings':
        return Icons.savings;
      case 'analytics':
        return Icons.analytics;
      case 'wb_sunny':
        return Icons.wb_sunny;
      default:
        return Icons.help_outline;
    }
  }

  Widget _buildEmptyView() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.chat_bubble_outline,
            size: 64,
            color: Colors.grey[300],
          ),
          const SizedBox(height: 16),
          Text(
            'Start a conversation',
            style: TextStyle(
              fontSize: 18,
              fontWeight: FontWeight.w600,
              color: Colors.grey[600],
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'Ask me anything about your portfolio',
            style: TextStyle(
              fontSize: 14,
              color: Colors.grey[500],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMessageBubble(ChatMessage message) {
    final isUser = message.role == 'user';
    
    return Container(
      margin: const EdgeInsets.only(bottom: 16),
      child: Row(
        mainAxisAlignment: isUser ? MainAxisAlignment.end : MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!isUser) ...[
            CircleAvatar(
              radius: 16,
              backgroundColor: Colors.blue[100],
              child: Icon(
                Icons.psychology,
                size: 18,
                color: Colors.blue[700],
              ),
            ),
            const SizedBox(width: 8),
          ],
          
          Flexible(
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              decoration: BoxDecoration(
                color: isUser ? Colors.blue[600] : Colors.grey[200],
                borderRadius: BorderRadius.only(
                  topLeft: const Radius.circular(16),
                  topRight: const Radius.circular(16),
                  bottomLeft: Radius.circular(isUser ? 16 : 4),
                  bottomRight: Radius.circular(isUser ? 4 : 16),
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    message.content,
                    style: TextStyle(
                      color: isUser ? Colors.white : Colors.black87,
                      fontSize: 15,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    _formatTime(message.timestamp),
                    style: TextStyle(
                      fontSize: 10,
                      color: isUser ? Colors.white70 : Colors.grey[600],
                    ),
                  ),
                ],
              ),
            ),
          ),
          
          if (isUser) ...[
            const SizedBox(width: 8),
            CircleAvatar(
              radius: 16,
              backgroundColor: Colors.grey[300],
              child: Icon(
                Icons.person,
                size: 18,
                color: Colors.grey[700],
              ),
            ),
          ],
        ],
      ),
    );
  }

  String _formatTime(DateTime time) {
    final hour = time.hour > 12 ? time.hour - 12 : (time.hour == 0 ? 12 : time.hour);
    final minute = time.minute.toString().padLeft(2, '0');
    final period = time.hour >= 12 ? 'PM' : 'AM';
    return '$hour:$minute $period';
  }

  Widget _buildInputArea() {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border(
          top: BorderSide(color: Colors.grey[200]!),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 10,
            offset: const Offset(0, -5),
          ),
        ],
      ),
      child: SafeArea(
        child: Row(
          children: [
            IconButton(
              icon: const Icon(Icons.attach_file),
              onPressed: () {},
            ),
            Expanded(
              child: TextField(
                controller: _controller,
                decoration: InputDecoration(
                  hintText: 'Ask anything about your portfolio...',
                  hintStyle: TextStyle(color: Colors.grey[400]),
                  filled: true,
                  fillColor: Colors.grey[100],
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(24),
                    borderSide: BorderSide.none,
                  ),
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 20,
                    vertical: 14,
                  ),
                ),
                onSubmitted: (text) => _sendMessage(text),
              ),
            ),
            const SizedBox(width: 8),
            Material(
              borderRadius: BorderRadius.circular(24),
              color: _controller.text.isNotEmpty ? Colors.blue[600] : Colors.grey[300],
              child: InkWell(
                borderRadius: BorderRadius.circular(24),
                onTap: () => _sendMessage(_controller.text),
                child: Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Icon(
                    Icons.send,
                    color: _controller.text.isNotEmpty ? Colors.white : Colors.grey[500],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
