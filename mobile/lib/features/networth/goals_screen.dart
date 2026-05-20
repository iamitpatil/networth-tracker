// lib/features/networth/goals_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:flutter_networth/providers/data_provider.dart';
import 'package:flutter_networth/data/models/app_models.dart';
import 'package:flutter_networth/core/services/api_client.dart';
import 'package:flutter_networth/widgets/dialogs/add_goal_dialog.dart';

class GoalsScreen extends StatefulWidget {
  const GoalsScreen({super.key});

  @override
  State<GoalsScreen> createState() => GoalsScreenState();
}

class GoalsScreenState extends State<GoalsScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      context.read<DataProvider>().loadGoals();
    });
  }

  String _formatCurrency(double value) {
    if (value >= 10000000) {
      return '₹${(value / 10000000).toStringAsFixed(2)}Cr';
    } else if (value >= 100000) {
      return '₹${(value / 100000).toStringAsFixed(2)}L';
    } else if (value >= 1000) {
      return '₹${(value / 1000).toStringAsFixed(1)}K';
    } else {
      return '₹${value.toStringAsFixed(0)}';
    }
  }

  Color _getGoalColor(String goalType) {
    switch (goalType) {
      case 'emergency':
        return const Color(0xFFEF4444);
      case 'retirement':
        return const Color(0xFF3B82F6);
      case 'house':
        return const Color(0xFF10B981);
      case 'education':
        return const Color(0xFFF59E0B);
      case 'fire':
        return const Color(0xFFEC4899);
      case 'travel':
        return const Color(0xFF06B6D4);
      case 'vehicle':
        return const Color(0xFF8B5CF6);
      default:
        return const Color(0xFF6B7280);
    }
  }

  IconData _getGoalIcon(String goalType) {
    switch (goalType) {
      case 'emergency':
        return Icons.emergency;
      case 'retirement':
        return Icons.beach_access;
      case 'house':
        return Icons.home;
      case 'education':
        return Icons.school;
      case 'fire':
        return Icons.local_fire_department;
      case 'travel':
        return Icons.flight;
      case 'vehicle':
        return Icons.directions_car;
      default:
        return Icons.flag;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Consumer<DataProvider>(
      builder: (context, provider, child) {
        final goals = provider.goals;

        if (provider.isLoading && goals.isEmpty) {
          return const Center(child: CircularProgressIndicator());
        }

        if (provider.error != null && goals.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.error_outline, size: 64, color: Colors.red),
                const SizedBox(height: 16),
                Text('Error: ${provider.error}'),
                const SizedBox(height: 16),
                ElevatedButton(
                  onPressed: () => provider.loadGoals(),
                  child: const Text('Retry'),
                ),
              ],
            ),
          );
        }

        if (goals.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                const Icon(Icons.flag, size: 64, color: Colors.grey),
                const SizedBox(height: 16),
                const Text(
                  'No goals yet',
                  style: TextStyle(
                    fontSize: 24,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                const Text(
                  'Add your first financial goal',
                  style: TextStyle(
                    color: Colors.grey,
                  ),
                ),
                const SizedBox(height: 24),
                ElevatedButton.icon(
                  onPressed: () => showAddGoalDialog(),
                  icon: const Icon(Icons.add),
                  label: const Text('Add Goal'),
                ),
              ],
            ),
          );
        }

        return RefreshIndicator(
          onRefresh: () => provider.loadGoals(),
          child: ListView.builder(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            itemCount: goals.length,
            itemBuilder: (context, index) {
              return _buildGoalCard(goals[index]);
            },
          ),
        );
      },
    );
  }

  Future<void> showAddGoalDialog() async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => const AddGoalDialog(),
    );
    
    if (result != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Goal created successfully!'),
          backgroundColor: Colors.green,
        ),
      );
      context.read<DataProvider>().loadGoals();
    }
  }

  Future<void> _showDeleteConfirmation(BuildContext context, Goal goal) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Goal'),
        content: Text("Delete goal '${goal.name}'?"),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Delete'),
          ),
        ],
      ),
    );

    if (confirmed == true && context.mounted) {
      try {
        await context.read<DataProvider>().deleteGoal(goal.id);
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text("Goal '${goal.name}' deleted successfully"),
              backgroundColor: Colors.green,
            ),
          );
        }
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Error deleting goal: $e'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    }
  }

  Future<void> _showEditGoalDialog(BuildContext context, Goal goal) async {
    final result = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (context) => _EditGoalDialog(goal: goal),
    );

    if (result != null && context.mounted) {
      try {
        await ApiClient.put('/goals/${goal.id}', body: result);
        if (context.mounted) {
          context.read<DataProvider>().loadGoals();
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Goal updated successfully!'),
              backgroundColor: Colors.green,
            ),
          );
        }
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Error updating goal: $e'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    }
  }

  Widget _buildGoalCard(Goal goal) {
    final color = _getGoalColor(goal.goalType);
    final icon = _getGoalIcon(goal.goalType);
    final progress = goal.progressPercentage / 100;

    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: color.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(icon, color: color),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        goal.name,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 18,
                        ),
                      ),
                      Text(
                        goal.goalTypeLabel,
                        style: TextStyle(
                          color: color,
                          fontWeight: FontWeight.w600,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => _showEditGoalDialog(context, goal),
                  icon: const Icon(Icons.edit, size: 20),
                  color: Colors.blue,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
                  tooltip: 'Edit goal',
                ),
                IconButton(
                  onPressed: () => _showDeleteConfirmation(context, goal),
                  icon: const Icon(Icons.delete, size: 20),
                  color: Colors.red,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
                  tooltip: 'Delete goal',
                ),
                if (goal.isCompleted)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.green.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(Icons.check_circle, size: 14, color: Colors.green),
                        SizedBox(width: 4),
                        Text(
                          'Completed',
                          style: TextStyle(
                            color: Colors.green,
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'Current',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[600],
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _formatCurrency(goal.currentAmount),
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                  ],
                ),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      'Target',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey[600],
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      _formatCurrency(goal.targetAmount),
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 16),
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: LinearProgressIndicator(
                value: progress,
                minHeight: 8,
                backgroundColor: Colors.grey[200],
                valueColor: AlwaysStoppedAnimation<Color>(color),
              ),
            ),
            const SizedBox(height: 8),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '${goal.progressPercentage.toStringAsFixed(1)}% completed',
                  style: TextStyle(
                    fontSize: 12,
                    color: color,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                Text(
                  '${_formatCurrency(goal.remainingAmount)} remaining',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey[600],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _EditGoalDialog extends StatefulWidget {
  final Goal goal;

  const _EditGoalDialog({required this.goal});

  @override
  State<_EditGoalDialog> createState() => _EditGoalDialogState();
}

class _EditGoalDialogState extends State<_EditGoalDialog> {
  final _formKey = GlobalKey<FormState>();
  late String _selectedGoalType;
  late final TextEditingController _nameController;
  late final TextEditingController _targetAmountController;
  late DateTime _targetDate;
  bool _isLoading = false;

  final List<Map<String, dynamic>> _goalTypes = [
    {'value': 'emergency', 'label': 'Emergency Fund', 'icon': Icons.emergency, 'color': Colors.red},
    {'value': 'retirement', 'label': 'Retirement', 'icon': Icons.beach_access, 'color': Colors.blue},
    {'value': 'house', 'label': 'Buy a House', 'icon': Icons.home, 'color': Colors.green},
    {'value': 'education', 'label': 'Education', 'icon': Icons.school, 'color': Colors.purple},
    {'value': 'fire', 'label': 'FIRE', 'icon': Icons.local_fire_department, 'color': Colors.orange},
    {'value': 'travel', 'label': 'Travel', 'icon': Icons.flight, 'color': Colors.cyan},
    {'value': 'vehicle', 'label': 'Vehicle', 'icon': Icons.directions_car, 'color': Colors.indigo},
    {'value': 'other', 'label': 'Other', 'icon': Icons.flag, 'color': Colors.grey},
  ];

  @override
  void initState() {
    super.initState();
    _selectedGoalType = widget.goal.goalType;
    _nameController = TextEditingController(text: widget.goal.name);
    _targetAmountController = TextEditingController(text: widget.goal.targetAmount.toString());
    _targetDate = widget.goal.targetDate != null
        ? DateTime.tryParse(widget.goal.targetDate!) ?? DateTime.now().add(const Duration(days: 365 * 5))
        : DateTime.now().add(const Duration(days: 365 * 5));
  }

  @override
  void dispose() {
    _nameController.dispose();
    _targetAmountController.dispose();
    super.dispose();
  }

  Future<void> _selectDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _targetDate,
      firstDate: DateTime.now(),
      lastDate: DateTime(2050),
    );
    if (picked != null) {
      setState(() => _targetDate = picked);
    }
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;

    setState(() => _isLoading = true);

    final data = {
      'name': _nameController.text,
      'targetAmount': double.parse(_targetAmountController.text),
      'targetDate': '${_targetDate.year}-${_targetDate.month.toString().padLeft(2, '0')}-${_targetDate.day.toString().padLeft(2, '0')}',
      'goalType': _selectedGoalType,
    };

    if (mounted) {
      Navigator.pop(context, data);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      child: Container(
        width: double.infinity,
        constraints: const BoxConstraints(maxWidth: 450, maxHeight: 600),
        child: Column(
          children: [
            // Header
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [Colors.blue[400]!, Colors.blue[600]!],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: const BorderRadius.vertical(top: Radius.circular(20)),
              ),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: Colors.white.withOpacity(0.2),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.edit, color: Colors.white),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Edit Goal',
                          style: TextStyle(
                            fontSize: 20,
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                          ),
                        ),
                        Text(
                          'Update your financial target',
                          style: TextStyle(
                            fontSize: 13,
                            color: Colors.white70,
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close, color: Colors.white),
                  ),
                ],
              ),
            ),

            // Form
            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(20),
                child: Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // Goal Type Selection
                      const Text(
                        'Goal Type',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 8),
                      SizedBox(
                        height: 100,
                        child: ListView.builder(
                          scrollDirection: Axis.horizontal,
                          itemCount: _goalTypes.length,
                          itemBuilder: (context, index) {
                            final type = _goalTypes[index];
                            final isSelected = _selectedGoalType == type['value'];
                            final color = type['color'] as Color;

                            return GestureDetector(
                              onTap: () {
                                setState(() {
                                  _selectedGoalType = type['value'];
                                });
                              },
                              child: Container(
                                width: 90,
                                margin: const EdgeInsets.only(right: 8),
                                padding: const EdgeInsets.all(8),
                                decoration: BoxDecoration(
                                  color: isSelected ? color.withOpacity(0.1) : Colors.grey[50],
                                  borderRadius: BorderRadius.circular(12),
                                  border: Border.all(
                                    color: isSelected ? color : Colors.transparent,
                                    width: 2,
                                  ),
                                ),
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    Icon(
                                      type['icon'] as IconData,
                                      color: isSelected ? color : Colors.grey,
                                      size: 28,
                                    ),
                                    const SizedBox(height: 6),
                                    Text(
                                      type['label'] as String,
                                      style: TextStyle(
                                        fontSize: 10,
                                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                                        color: isSelected ? color : Colors.grey[600],
                                      ),
                                      textAlign: TextAlign.center,
                                      maxLines: 2,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ],
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                      const SizedBox(height: 20),

                      // Goal Name
                      TextFormField(
                        controller: _nameController,
                        decoration: InputDecoration(
                          labelText: 'Goal Name',
                          hintText: 'e.g., Buy Dream Home',
                          prefixIcon: const Icon(Icons.edit),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        validator: (value) {
                          if (value == null || value.isEmpty) {
                            return 'Please enter goal name';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // Target Amount
                      TextFormField(
                        controller: _targetAmountController,
                        keyboardType: TextInputType.number,
                        inputFormatters: [
                          FilteringTextInputFormatter.allow(RegExp(r'^\d*\.?\d*')),
                        ],
                        decoration: InputDecoration(
                          labelText: 'Target Amount (\u20b9)',
                          hintText: 'e.g., 5000000',
                          prefixIcon: const Icon(Icons.currency_rupee),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        validator: (value) {
                          if (value == null || value.isEmpty) {
                            return 'Please enter target amount';
                          }
                          if (double.tryParse(value) == null || double.parse(value) <= 0) {
                            return 'Please enter valid amount';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),

                      // Target Date
                      TextFormField(
                        readOnly: true,
                        onTap: _selectDate,
                        decoration: InputDecoration(
                          labelText: 'Target Date',
                          hintText: 'When do you want to achieve this?',
                          prefixIcon: const Icon(Icons.calendar_today),
                          suffixIcon: const Icon(Icons.arrow_drop_down),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                        ),
                        controller: TextEditingController(
                          text: '${_targetDate.day}/${_targetDate.month}/${_targetDate.year}',
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),

            // Footer Buttons
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: Colors.grey[50],
                borderRadius: const BorderRadius.vertical(bottom: Radius.circular(20)),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(context),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: const Text('Cancel'),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    flex: 2,
                    child: ElevatedButton(
                      onPressed: _isLoading ? null : _submit,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.blue,
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                      ),
                      child: _isLoading
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                              ),
                            )
                          : const Text(
                              'Update Goal',
                              style: TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
