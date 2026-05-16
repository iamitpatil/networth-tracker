// lib/screens/liabilities/liabilities_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_networth/blocs/liabilities/liabilities_bloc.dart';
import 'package:flutter_networth/models/liability_model.dart';
import 'package:intl/intl.dart';

class LiabilitiesScreen extends StatelessWidget {
  const LiabilitiesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (context) => LiabilitiesBloc()..add(LiabilitiesLoadRequested()),
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Loans & Liabilities'),
          actions: [
            IconButton(
              icon: const Icon(Icons.add),
              onPressed: () {
                // Navigate to add liability screen
              },
            ),
          ],
        ),
        body: BlocBuilder<LiabilitiesBloc, LiabilitiesState>(
          builder: (context, state) {
            if (state is LiabilitiesLoading) {
              return const Center(child: CircularProgressIndicator());
            }
            
            if (state is LiabilitiesError) {
              return _ErrorView(
                message: state.message,
                onRetry: () {
                  context.read<LiabilitiesBloc>().add(LiabilitiesLoadRequested());
                },
              );
            }
            
            if (state is LiabilitiesLoaded) {
              return _LiabilitiesContent(
                liabilities: state.liabilities,
                totalOutstanding: state.totalOutstanding,
                totalMonthlyEmi: state.totalMonthlyEmi,
              );
            }
            
            return const SizedBox.shrink();
          },
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: () {
            // Show add liability dialog
          },
          icon: const Icon(Icons.add),
          label: const Text('Add Loan'),
        ),
      ),
    );
  }
}

class _LiabilitiesContent extends StatelessWidget {
  final List<Liability> liabilities;
  final double totalOutstanding;
  final double totalMonthlyEmi;

  const _LiabilitiesContent({
    required this.liabilities,
    required this.totalOutstanding,
    required this.totalMonthlyEmi,
  });

  @override
  Widget build(BuildContext context) {
    final formatter = NumberFormat.currency(
      symbol: '₹',
      locale: 'en_IN',
      decimalDigits: 0,
    );

    return CustomScrollView(
      slivers: [
        // Summary Section
        SliverToBoxAdapter(
          child: Container(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [Colors.red[400]!, Colors.red[600]!],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(
                  color: Colors.red.withOpacity(0.3),
                  blurRadius: 10,
                  offset: const Offset(0, 4),
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Total Outstanding',
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 14,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  formatter.format(totalOutstanding),
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 32,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Monthly EMI',
                            style: TextStyle(
                              color: Colors.white70,
                              fontSize: 12,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            formatter.format(totalMonthlyEmi),
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text(
                            'Active Loans',
                            style: TextStyle(
                              color: Colors.white70,
                              fontSize: 12,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            '${liabilities.length}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 18,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),

        // DTI Ratio Card
        SliverToBoxAdapter(
          child: _DTIRatioCard(
            liabilities: totalOutstanding,
            // Would need income data from backend
            monthlyIncome: 150000,
          ),
        ),

        // Liabilities List
        SliverList(
          delegate: SliverChildBuilderDelegate(
            (context, index) {
              final liability = liabilities[index];
              return _LiabilityCard(liability: liability);
            },
            childCount: liabilities.length,
          ),
        ),

        const SliverPadding(padding: EdgeInsets.only(bottom: 80)),
      ],
    );
  }
}

class _DTIRatioCard extends StatelessWidget {
  final double liabilities;
  final double monthlyIncome;

  const _DTIRatioCard({
    required this.liabilities,
    required this.monthlyIncome,
  });

  @override
  Widget build(BuildContext context) {
    final dtiRatio = (liabilities / monthlyIncome) * 100;
    final isHealthy = dtiRatio <= 40;

    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: isHealthy ? Colors.green.withOpacity(0.1) : Colors.orange.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isHealthy ? Colors.green.withOpacity(0.3) : Colors.orange.withOpacity(0.3),
        ),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: isHealthy ? Colors.green : Colors.orange,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(
              isHealthy ? Icons.check : Icons.warning,
              color: Colors.white,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text(
                  'Debt-to-Income Ratio',
                  style: TextStyle(
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  isHealthy
                      ? 'Your debt is well managed'
                      : 'Consider reducing debt burden',
                  style: TextStyle(
                    color: Colors.grey[600],
                    fontSize: 12,
                  ),
                ),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: BoxDecoration(
              color: isHealthy ? Colors.green : Colors.orange,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Text(
              '${dtiRatio.toStringAsFixed(1)}%',
              style: const TextStyle(
                color: Colors.white,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _LiabilityCard extends StatelessWidget {
  final Liability liability;

  const _LiabilityCard({required this.liability});

  @override
  Widget build(BuildContext context) {
    final formatter = NumberFormat.currency(
      symbol: '₹',
      locale: 'en_IN',
      decimalDigits: 0,
    );

    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: InkWell(
        onTap: () {
          // Navigate to liability detail
        },
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: BoxDecoration(
                      color: _getTypeColor().withOpacity(0.1),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Icon(
                      _getTypeIcon(),
                      color: _getTypeColor(),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          liability.lender,
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                            fontSize: 16,
                          ),
                        ),
                        Text(
                          liability.liabilityType.displayName,
                          style: TextStyle(
                            color: Colors.grey[600],
                            fontSize: 13,
                          ),
                        ),
                      ],
                    ),
                  ),
                  if (liability.isOverdue)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: Colors.red,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: const Text(
                        'OVERDUE',
                        style: TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 16),
              
              // Progress Bar
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: LinearProgressIndicator(
                  value: liability.progressPercentage / 100,
                  minHeight: 6,
                  backgroundColor: Colors.grey[200],
                  valueColor: AlwaysStoppedAnimation<Color>(_getProgressColor()),
                ),
              ),
              const SizedBox(height: 8),
              
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '${liability.progressPercentage.toStringAsFixed(1)}% paid',
                    style: TextStyle(
                      color: Colors.grey[600],
                      fontSize: 12,
                    ),
                  ),
                  Text(
                    formatter.format(liability.paidAmount),
                    style: const TextStyle(
                      fontWeight: FontWeight.w500,
                      fontSize: 12,
                    ),
                  ),
                ],
              ),
              
              const Divider(height: 24),
              
              Row(
                children: [
                  Expanded(
                    child: _InfoItem(
                      label: 'Outstanding',
                      value: formatter.format(liability.outstandingAmount),
                      color: Colors.red[700]!,
                    ),
                  ),
                  Expanded(
                    child: _InfoItem(
                      label: 'Interest Rate',
                      value: '${liability.interestRate.toStringAsFixed(2)}%',
                      color: Colors.blue[700]!,
                    ),
                  ),
                  if (liability.monthlyEmi != null)
                    Expanded(
                      child: _InfoItem(
                        label: 'Monthly EMI',
                        value: formatter.format(liability.monthlyEmi!),
                        color: Colors.purple[700]!,
                      ),
                    ),
                ],
              ),
              
              if (liability.formattedNextEmiDate != null) ...[
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: liability.isOverdue
                        ? Colors.red.withOpacity(0.1)
                        : Colors.blue.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.calendar_today,
                        size: 14,
                        color: liability.isOverdue ? Colors.red : Colors.blue,
                      ),
                      const SizedBox(width: 6),
                      Text(
                        'Next EMI: ${liability.formattedNextEmiDate}',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.w500,
                          color: liability.isOverdue ? Colors.red : Colors.blue[700],
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Color _getTypeColor() {
    switch (liability.liabilityType) {
      case LiabilityType.homeLoan:
        return Colors.blue;
      case LiabilityType.carLoan:
        return Colors.green;
      case LiabilityType.personalLoan:
        return Colors.orange;
      case LiabilityType.educationLoan:
        return Colors.purple;
      case LiabilityType.creditCard:
        return Colors.red;
    }
  }

  Color _getProgressColor() {
    final percentage = liability.progressPercentage;
    if (percentage >= 75) return Colors.green;
    if (percentage >= 50) return Colors.blue;
    if (percentage >= 25) return Colors.orange;
    return Colors.red;
  }

  IconData _getTypeIcon() {
    switch (liability.liabilityType) {
      case LiabilityType.homeLoan:
        return Icons.home;
      case LiabilityType.carLoan:
        return Icons.directions_car;
      case LiabilityType.personalLoan:
        return Icons.person;
      case LiabilityType.educationLoan:
        return Icons.school;
      case LiabilityType.creditCard:
        return Icons.credit_card;
    }
  }
}

class _InfoItem extends StatelessWidget {
  final String label;
  final String value;
  final Color color;

  const _InfoItem({
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: TextStyle(
            fontSize: 11,
            color: Colors.grey[600],
          ),
        ),
        const SizedBox(height: 4),
        Text(
          value,
          style: TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 14,
            color: color,
          ),
        ),
      ],
    );
  }
}

class _ErrorView extends StatelessWidget {
  final String message;
  final VoidCallback onRetry;

  const _ErrorView({required this.message, required this.onRetry});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.error_outline, size: 64, color: Colors.red[300]),
          const SizedBox(height: 16),
          Text(message),
          const SizedBox(height: 16),
          ElevatedButton(
            onPressed: onRetry,
            child: const Text('Retry'),
          ),
        ],
      ),
    );
  }
}