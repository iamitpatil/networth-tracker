// lib/widgets/charts/asset_allocation_chart.dart
import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';

class AssetAllocationChart extends StatelessWidget {
  final List<Map<String, dynamic>> data;

  const AssetAllocationChart({
    super.key,
    required this.data,
  });

  @override
  Widget build(BuildContext context) {
    if (data.isEmpty) {
      return const SizedBox(
        height: 200,
        child: Center(
          child: Text(
            'No data available',
            style: TextStyle(color: Colors.grey),
          ),
        ),
      );
    }

    final total = data.fold<double>(0, (sum, item) => sum + (item['value'] as double));
    
    return Column(
      children: [
        SizedBox(
          height: 200,
          child: PieChart(
            PieChartData(
              sectionsSpace: 2,
              centerSpaceRadius: 40,
              sections: data.map((item) {
                final value = item['value'] as double;
                final color = Color(item['color'] as int);
                final percentage = total > 0 ? (value / total * 100) : 0;
                
                return PieChartSectionData(
                  color: color,
                  value: value,
                  title: percentage > 5 ? '${percentage.toStringAsFixed(1)}%' : '',
                  radius: 80,
                  titleStyle: const TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                  badgeWidget: percentage > 10
                      ? _buildIcon(item['name'] as String, color)
                      : null,
                  badgePositionPercentageOffset: 1.2,
                );
              }).toList(),
              pieTouchData: PieTouchData(
                touchCallback: (FlTouchEvent event, pieTouchResponse) {},
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),
        // Legend
        Wrap(
          spacing: 16,
          runSpacing: 8,
          children: data.map((item) {
            final color = Color(item['color'] as int);
            final name = item['name'] as String;
            final value = item['value'] as double;
            
            return Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
                Text(
                  name,
                  style: const TextStyle(fontSize: 12),
                ),
              ],
            );
          }).toList(),
        ),
      ],
    );
  }

  Widget _buildIcon(String name, Color color) {
    IconData iconData;
    switch (name.toLowerCase()) {
      case 'equity':
        iconData = Icons.show_chart;
        break;
      case 'mutual funds':
        iconData = Icons.account_balance;
        break;
      case 'gold':
        iconData = Icons.monetization_on;
        break;
      case 'real estate':
        iconData = Icons.home;
        break;
      case 'cash':
        iconData = Icons.account_balance_wallet;
        break;
      default:
        iconData = Icons.pie_chart;
    }

    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: color.withOpacity(0.2),
        shape: BoxShape.circle,
      ),
      child: Icon(
        iconData,
        size: 16,
        color: color,
      ),
    );
  }
}
