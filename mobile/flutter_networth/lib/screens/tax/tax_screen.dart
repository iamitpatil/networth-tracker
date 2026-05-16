// lib/screens/tax/tax_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:flutter_networth/blocs/tax/tax_bloc.dart';
import 'package:flutter_networth/models/tax_model.dart';
import 'package:flutter_networth/widgets/cards/info_card.dart';
import 'package:flutter_networth/widgets/charts/pie_chart.dart';
import 'package:intl/intl.dart';

class TaxScreen extends StatelessWidget {
  const TaxScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocProvider(
      create: (context) => TaxBloc()..add(TaxLoadRequested()),
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Tax Planning'),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: () {
                context.read<TaxBloc>().add(TaxRefreshRequested());
              },
            ),
          ],
        ),
        body: BlocBuilder<TaxBloc, TaxState>(
          builder: (context, state) {
            if (state is TaxLoading) {
              return const Center(child: CircularProgressIndicator());
            }
            
            if (state is TaxError) {
              return Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(Icons.error_outline, size: 64, color: Colors.red[300]),
                    const SizedBox(height: 16),
                    Text(state.message),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      onPressed: () {
                        context.read<TaxBloc>().add(TaxLoadRequested());
                      },
                      child: const Text('Retry'),
                    ),
                  ],
                ),
              );
            }
            
            if (state is TaxLoaded) {
              return _TaxContent(
                summary: state.summary,
                harvestingOpportunities: state.harvestingOpportunities,
                deduction80C: state.deduction80C,
              );
            }
            
            return const SizedBox.shrink();
          },
        ),
      ),
    );
  }
}

class _TaxContent extends StatelessWidget {
  final TaxSummary summary;
  final List<TaxHarvestingOpportunity> harvestingOpportunities;
  final Deduction80C? deduction80C;

  const _TaxContent({
    required this.summary,
    required this.harvestingOpportunities,
    this.deduction80C,
  });

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Financial Year Selector
          _FinancialYearSelector(
            currentYear: summary.financialYear,
          ),
          
          const SizedBox(height: 20),
          
          // Tax Summary Cards
          _TaxSummaryCards(summary: summary),
          
          const SizedBox(height: 20),
          
          // Tax Liability
          _TaxLiabilityCard(liability: summary.taxLiability),
          
          const SizedBox(height: 20),
          
          // Tax Harvesting
          if (harvestingOpportunities.isNotEmpty)
            _TaxHarvestingSection(opportunities: harvestingOpportunities),
          
          const SizedBox(height: 20),
          
          // 80C Deductions
          if (deduction80C != null)
            _Deduction80CSection(deduction: deduction80C!),
          
          const SizedBox(height: 20),
          
          // Tax Rates Reference
          _TaxRatesSection(),
        ],
      ),
    );
  }
}

class _FinancialYearSelector extends StatelessWidget {
  final String currentYear;

  const _FinancialYearSelector({required this.currentYear});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: Colors.blue.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          const Icon(Icons.calendar_today, color: Colors.blue),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Financial Year',
                  style: Theme.of(context).textTheme.bodySmall,
                ),
                Text(
                  currentYear,
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.arrow_drop_down),
            onPressed: () {
              // Show FY selector
            },
          ),
        ],
      ),
    );
  }
}

class _TaxSummaryCards extends StatelessWidget {
  final TaxSummary summary;

  const _TaxSummaryCards({required this.summary});

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: InfoCard(
            title: 'STCG',
            value: _formatCurrency(summary.stcg.total),
            subtitle: 'Short Term',
            icon: Icons.trending_up,
            color: Colors.orange,
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: InfoCard(
            title: 'LTCG',
            value: _formatCurrency(summary.ltcg.total),
            subtitle: 'Long Term',
            icon: Icons.trending_up,
            color: Colors.green,
          ),
        ),
      ],
    );
  }

  String _formatCurrency(double value) {
    final formatter = NumberFormat.currency(
      symbol: '₹',
      locale: 'en_IN',
      decimalDigits: 0,
    );
    return formatter.format(value);
  }
}

class _TaxLiabilityCard extends StatelessWidget {
  final TaxLiability liability;

  const _TaxLiabilityCard({required this.liability});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Tax Liability',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            _buildTaxRow('STCG Tax', liability.stcgTax, Colors.orange),
            const SizedBox(height: 8),
            _buildTaxRow('LTCG Tax', liability.ltcgTax, Colors.green),
            const Divider(height: 24),
            _buildTaxRow('Total Tax', liability.total, Colors.red, isBold: true),
          ],
        ),
      ),
    );
  }

  Widget _buildTaxRow(String label, double value, Color color, {bool isBold = false}) {
    final formatter = NumberFormat.currency(
      symbol: '₹',
      locale: 'en_IN',
      decimalDigits: 0,
    );
    
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Row(
          children: [
            Container(
              width: 12,
              height: 12,
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(width: 8),
            Text(
              label,
              style: TextStyle(
                fontWeight: isBold ? FontWeight.bold : FontWeight.normal,
              ),
            ),
          ],
        ),
        Text(
          formatter.format(value),
          style: TextStyle(
            fontWeight: isBold ? FontWeight.bold : FontWeight.w600,
            fontSize: isBold ? 18 : 16,
            color: isBold ? Colors.red : null,
          ),
        ),
      ],
    );
  }
}

class _TaxHarvestingSection extends StatelessWidget {
  final List<TaxHarvestingOpportunity> opportunities;

  const _TaxHarvestingSection({required this.opportunities});

  @override
  Widget build(BuildContext context) {
    final harvestable = opportunities.where((o) => o.isHarvestable).toList();
    final nearLtcg = opportunities.where((o) => o.isNearLtcg).toList();

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.savings, color: Colors.green[600]),
                const SizedBox(width: 8),
                Text(
                  'Tax Harvesting Opportunities',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            
            if (harvestable.isNotEmpty) ...[
              Text(
                'Ready to Harvest',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: Colors.green[700],
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              ...harvestable.map((o) => _HarvestingCard(opportunity: o)),
              const SizedBox(height: 16),
            ],
            
            if (nearLtcg.isNotEmpty) ...[
              Text(
                'Near LTCG Threshold',
                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: Colors.orange[700],
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(height: 8),
              ...nearLtcg.map((o) => _HarvestingCard(opportunity: o)),
            ],
          ],
        ),
      ),
    );
  }
}

class _HarvestingCard extends StatelessWidget {
  final TaxHarvestingOpportunity opportunity;

  const _HarvestingCard({required this.opportunity});

  @override
  Widget build(BuildContext context) {
    final formatter = NumberFormat.currency(
      symbol: '₹',
      locale: 'en_IN',
      decimalDigits: 0,
    );

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: opportunity.isHarvestable
            ? Colors.green.withOpacity(0.1)
            : Colors.orange.withOpacity(0.1),
        borderRadius: BorderRadius.circular(8),
        border: Border.all(
          color: opportunity.isHarvestable
              ? Colors.green.withOpacity(0.3)
              : Colors.orange.withOpacity(0.3),
        ),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: opportunity.isHarvestable ? Colors.green : Colors.orange,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Icon(
              opportunity.isHarvestable ? Icons.check : Icons.access_time,
              color: Colors.white,
              size: 20,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  opportunity.symbol,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 16,
                  ),
                ),
                Text(
                  opportunity.reason,
                  style: TextStyle(
                    color: Colors.grey[600],
                    fontSize: 12,
                  ),
                ),
                if (opportunity.daysToLtcg > 0)
                  Text(
                    '${opportunity.daysToLtcg} days to LTCG',
                    style: TextStyle(
                      color: Colors.orange[700],
                      fontSize: 11,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
              ],
            ),
          ),
          Text(
            formatter.format(opportunity.unrealizedGain),
            style: TextStyle(
              fontWeight: FontWeight.bold,
              color: opportunity.isHarvestable ? Colors.green[700] : Colors.orange[700],
              fontSize: 16,
            ),
          ),
        ],
      ),
    );
  }
}

class _Deduction80CSection extends StatelessWidget {
  final Deduction80C deduction;

  const _Deduction80CSection({required this.deduction});

  @override
  Widget build(BuildContext context) {
    final formatter = NumberFormat.currency(
      symbol: '₹',
      locale: 'en_IN',
      decimalDigits: 0,
    );

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.account_balance, color: Colors.purple[600]),
                const SizedBox(width: 8),
                Text(
                  'Section 80C Deductions',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            
            // Progress Bar
            ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: deduction.utilizationPercentage / 100,
                minHeight: 8,
                backgroundColor: Colors.grey[200],
                valueColor: AlwaysStoppedAnimation<Color>(
                  deduction.utilizationPercentage >= 100
                      ? Colors.green
                      : deduction.utilizationPercentage >= 80
                          ? Colors.orange
                          : Colors.red,
                ),
              ),
            ),
            const SizedBox(height: 8),
            
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '${deduction.utilizationPercentage.toStringAsFixed(1)}% utilized',
                  style: TextStyle(
                    color: Colors.grey[600],
                    fontWeight: FontWeight.w500,
                  ),
                ),
                Text(
                  'Limit: ${formatter.format(deduction.limit)}',
                  style: TextStyle(
                    color: Colors.grey[600],
                    fontSize: 12,
                  ),
                ),
              ],
            ),
            
            const SizedBox(height: 16),
            
            // Breakdown
            ...deduction.breakdown.map((item) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Icon(Icons.check_circle, 
                        size: 16, 
                        color: Colors.green[600],
                      ),
                      const SizedBox(width: 8),
                      Text(item.category),
                    ],
                  ),
                  Text(
                    formatter.format(item.amount),
                    style: const TextStyle(fontWeight: FontWeight.w500),
                  ),
                ],
              ),
            )),
            
            const Divider(height: 24),
            
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Remaining Limit',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
                Text(
                  formatter.format(deduction.remaining),
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    color: deduction.remaining > 0 ? Colors.orange[700] : Colors.green[700],
                    fontSize: 18,
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

class _TaxRatesSection extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Tax Rates Reference (FY 2024-25)',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            ...taxRates.map((rate) => _TaxRateRow(rate: rate)),
          ],
        ),
      ),
    );
  }
}

class _TaxRateRow extends StatelessWidget {
  final TaxRateInfo rate;

  const _TaxRateRow({required this.rate});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.grey[50],
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.grey[200]!),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            rate.assetType,
            style: const TextStyle(
              fontWeight: FontWeight.bold,
              fontSize: 15,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'STCG',
                      style: TextStyle(
                        fontSize: 11,
                        color: Colors.grey[600],
                      ),
                    ),
                    Text(
                      rate.stcgRate,
                      style: TextStyle(
                        fontWeight: FontWeight.w500,
                        color: Colors.orange[700],
                      ),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'LTCG',
                      style: TextStyle(
                        fontSize: 11,
                        color: Colors.grey[600],
                      ),
                    ),
                    Text(
                      rate.ltcgRate,
                      style: TextStyle(
                        fontWeight: FontWeight.w500,
                        color: Colors.green[700],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (rate.notes != null) ...[
            const SizedBox(height: 8),
            Text(
              'Note: ${rate.notes}',
              style: TextStyle(
                fontSize: 11,
                color: Colors.grey[600],
                fontStyle: FontStyle.italic,
              ),
            ),
          ],
        ],
      ),
    );
  }
}