// lib/screens/ai_chat/ai_chat_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_networth/blocs/ai_chat/ai_chat_bloc.dart';
import 'package:flutter_networth/models/ai_chat_model.dart';
import 'package:flutter_networth/widgets/charts/chat_pie_chart.dart';
import 'package:intl/intl.dart';

class AIChatScreen extends StatelessWidget {
  const AIChatScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (context) => AIChatBloc()..add(AIChatLoadHistoryRequested()),
      child: Scaffold(
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
                _showClearChatDialog(context);
              },
            ),
            IconButton(
              icon: const Icon(Icons.more_vert),
              onPressed: () {
                // Show options menu
              },
            ),
          ],
        ),
        body: Column(
          children: [
            // Quick Questions
            const _QuickQuestionsSection(),
            
            // Chat Messages
            Expanded(
              child: BlocBuilder<AIChatBloc, AIChatState>(
                builder: (context, state) {
                  if (state is AIChatLoading) {
                    return const Center(child: CircularProgressIndicator());
                  }
                  
                  if (state is AIChatLoaded) {
                    return _ChatMessagesList(messages: state.messages);
                  }
                  
                  if (state is AIChatError) {
                    return _ErrorView(message: state.message);
                  }
                  
                  return const _EmptyChatView();
                },
              ),
            ),
            
            // Input Area
            const _ChatInputArea(),
          ],
        ),
      ),
    );
  }

  void _showClearChatDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Clear Chat History?'),
        content: const Text('This will delete all your previous conversations. This action cannot be undone.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              context.read<AIChatBloc>().add(AIChatClearHistoryRequested());
              Navigator.pop(context);
            },
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Clear'),
          ),
        ],
      ),
    );
  }
}

class _QuickQuestionsSection extends StatelessWidget {
  const _QuickQuestionsSection();

  @override
  Widget build(BuildContext context) {
    return Container(
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
              itemCount: quickQuestions.length,
              itemBuilder: (context, index) {
                final question = quickQuestions[index];
                return _QuickQuestionChip(question: question);
              },
            ),
          ),
        ],
      ),
    );
  }
}

class _QuickQuestionChip extends StatelessWidget {
  final QuickQuestion question;

  const _QuickQuestionChip({required this.question});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 140,
      margin: const EdgeInsets.symmetric(horizontal: 4),
      child: InkWell(
        onTap: () {
          context.read<AIChatBloc>().add(
            AIChatMessageSent(message: question.question),
          );
        },
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
                _getIconData(question.icon),
                size: 20,
                color: Colors.blue[700],
              ),
              const SizedBox(height: 6),
              Text(
                question.question,
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
}

class _ChatMessagesList extends StatelessWidget {
  final List<ChatMessage> messages;

  const _ChatMessagesList({required this.messages});

  @override
  Widget build(BuildContext context) {
    return ListView.builder(
      reverse: true,
      padding: const EdgeInsets.all(16),
      itemCount: messages.length,
      itemBuilder: (context, index) {
        final message = messages[messages.length - 1 - index];
        return _ChatMessageBubble(message: message);
      },
    );
  }
}

class _ChatMessageBubble extends StatelessWidget {
  final ChatMessage message;

  const _ChatMessageBubble({required this.message});

  @override
  Widget build(BuildContext context) {
    final isUser = message.role.isUser;
    
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
                  // Message Content
                  _buildMessageContent(context),
                  
                  const SizedBox(height: 4),
                  
                  // Timestamp
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

  Widget _buildMessageContent(BuildContext context) {
    switch (message.type) {
      case MessageType.chart:
        if (message.chartData != null && message.chartData!.isNotEmpty) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                message.content,
                style: TextStyle(
                  color: message.role.isUser ? Colors.white : Colors.black87,
                ),
              ),
              const SizedBox(height: 12),
              SizedBox(
                height: 200,
                child: ChatPieChart(data: message.chartData!),
              ),
            ],
          );
        }
        break;
      case MessageType.table:
        if (message.tableData != null && message.tableData!.isNotEmpty) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                message.content,
                style: TextStyle(
                  color: message.role.isUser ? Colors.white : Colors.black87,
                ),
              ),
              const SizedBox(height: 12),
              _buildTableWidget(),
            ],
          );
        }
        break;
      case MessageType.error:
        return Text(
          message.content,
          style: const TextStyle(
            color: Colors.red,
            fontWeight: FontWeight.w500,
          ),
        );
      default:
        break;
    }
    
    return Text(
      message.content,
      style: TextStyle(
        color: message.role.isUser ? Colors.white : Colors.black87,
        fontSize: 15,
      ),
    );
  }

  Widget _buildTableWidget() {
    if (message.tableData == null || message.tableData!.isEmpty) {
      return const SizedBox.shrink();
    }

    final columns = message.tableData!.first.data.keys.toList();
    
    return Container(
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.grey[300]!),
      ),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: DataTable(
          columns: columns.map((col) => DataColumn(
            label: Text(
              col,
              style: const TextStyle(fontWeight: FontWeight.bold),
            ),
          )).toList(),
          rows: message.tableData!.map((row) {
            return DataRow(
              cells: columns.map((col) {
                final value = row.data[col];
                return DataCell(Text(value?.toString() ?? ''));
              }).toList(),
            );
          }).toList(),
        ),
      ),
    );
  }

  String _formatTime(DateTime time) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final messageDate = DateTime(time.year, time.month, time.day);
    
    if (messageDate == today) {
      return DateFormat('h:mm a').format(time);
    } else {
      return DateFormat('MMM d, h:mm a').format(time);
    }
  }
}

class _ChatInputArea extends StatefulWidget {
  const _ChatInputArea();

  @override
  State<_ChatInputArea> createState() => _ChatInputAreaState();
}

class _ChatInputAreaState extends State<_ChatInputArea> {
  final _controller = TextEditingController();
  bool _isComposing = false;

  @override
  Widget build(BuildContext context) {
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
              onPressed: () {
                // Show attachment options
              },
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
                onChanged: (text) {
                  setState(() {
                    _isComposing = text.isNotEmpty;
                  });
                },
                onSubmitted: _isComposing ? (_) => _sendMessage() : null,
              ),
            ),
            const SizedBox(width: 8),
            BlocBuilder<AIChatBloc, AIChatState>(
              builder: (context, state) {
                final isLoading = state is AIChatLoading;
                
                return AnimatedContainer(
                  duration: const Duration(milliseconds: 200),
                  child: Material(
                    borderRadius: BorderRadius.circular(24),
                    color: _isComposing && !isLoading
                        ? Colors.blue[600]
                        : Colors.grey[300],
                    child: InkWell(
                      borderRadius: BorderRadius.circular(24),
                      onTap: _isComposing && !isLoading ? _sendMessage : null,
                      child: Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(24),
                        ),
                        child: isLoading
                            ? const Padding(
                                padding: EdgeInsets.all(12),
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                                ),
                              )
                            : Icon(
                                Icons.send,
                                color: _isComposing
                                    ? Colors.white
                                    : Colors.grey[500],
                              ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  void _sendMessage() {
    final message = _controller.text.trim();
    if (message.isNotEmpty) {
      context.read<AIChatBloc>().add(AIChatMessageSent(message: message));
      _controller.clear();
      setState(() {
        _isComposing = false;
      });
    }
  }
}

class _EmptyChatView extends StatelessWidget {
  const _EmptyChatView();

  @override
  Widget build(BuildContext context) {
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
}

class _ErrorView extends StatelessWidget {
  final String message;

  const _ErrorView({required this.message});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.error_outline,
            size: 64,
            color: Colors.red[300],
          ),
          const SizedBox(height: 16),
          Text(
            message,
            textAlign: TextAlign.center,
            style: TextStyle(color: Colors.red[700]),
          ),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: () {
              context.read<AIChatBloc>().add(AIChatLoadHistoryRequested());
            },
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}