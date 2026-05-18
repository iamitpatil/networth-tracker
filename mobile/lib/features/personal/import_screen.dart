// lib/features/personal/import_screen.dart
import 'package:flutter/material.dart';

class ImportScreen extends StatefulWidget {
  const ImportScreen({super.key});

  @override
  State<ImportScreen> createState() => _ImportScreenState();
}

class _ImportScreenState extends State<ImportScreen> {
  String _selectedSource = 'ZERODHA';

  final _sources = [
    {'value': 'ZERODHA', 'label': 'Zerodha', 'format': 'CSV', 'icon': Icons.show_chart, 'color': Colors.blue},
    {'value': 'GROWW', 'label': 'Groww', 'format': 'CSV', 'icon': Icons.trending_up, 'color': Colors.green},
    {'value': 'CAS', 'label': 'CAS Statement', 'format': 'PDF', 'icon': Icons.picture_as_pdf, 'color': Colors.red},
    {'value': 'BANK_STATEMENT', 'label': 'Bank Statement', 'format': 'PDF', 'icon': Icons.account_balance, 'color': Colors.purple},
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Import Data')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text('Select Source', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          
          // Source Cards
          ..._sources.map((source) {
            final isSelected = _selectedSource == source['value'];
            final color = source['color'] as Color;
            return Card(
              margin: const EdgeInsets.only(bottom: 8),
              color: isSelected ? color.withOpacity(0.1) : null,
              child: InkWell(
                onTap: () => setState(() => _selectedSource = source['value'] as String),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: color.withOpacity(0.1),
                          shape: BoxShape.circle,
                        ),
                        child: Icon(source['icon'] as IconData, color: color),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(source['label'] as String, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                            Text('Format: ${source['format']}', style: TextStyle(color: Colors.grey[600], fontSize: 12)),
                          ],
                        ),
                      ),
                      if (isSelected)
                        Icon(Icons.check_circle, color: color),
                    ],
                  ),
                ),
              ),
            );
          }),

          const SizedBox(height: 24),

          // Upload Area Placeholder
          Container(
            padding: const EdgeInsets.all(32),
            decoration: BoxDecoration(
              border: Border.all(color: Colors.grey[300]!, width: 2, style: BorderStyle.solid),
              borderRadius: BorderRadius.circular(12),
              color: Colors.grey[50],
            ),
            child: Column(
              children: [
                Icon(Icons.cloud_upload, size: 64, color: Colors.grey[400]),
                const SizedBox(height: 16),
                const Text('File Upload', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Text('Use the web app to upload files', style: TextStyle(color: Colors.grey[600], fontSize: 13)),
                const SizedBox(height: 16),
                ElevatedButton.icon(
                  onPressed: () {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Please use the web app for file imports')),
                    );
                  },
                  icon: const Icon(Icons.upload_file),
                  label: const Text('Select File'),
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // Format Guide
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.info_outline, color: Colors.blue[600]),
                      const SizedBox(width: 8),
                      const Text('Format Guide', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  _buildFormatRow('Zerodha', 'Download Trade History CSV from Console'),
                  _buildFormatRow('Groww', 'Export holdings from Groww Web'),
                  _buildFormatRow('CAS', 'Consolidated Account Statement PDF from CAMS/Karvy'),
                  _buildFormatRow('Bank Statement', 'Account statement PDF from your bank'),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFormatRow(String label, String description) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Icon(Icons.fiber_manual_record, size: 8, color: Colors.blue),
          const SizedBox(width: 8),
          Expanded(
            child: RichText(
              text: TextSpan(
                style: const TextStyle(color: Colors.black87, fontSize: 13),
                children: [
                  TextSpan(text: '$label: ', style: const TextStyle(fontWeight: FontWeight.bold)),
                  TextSpan(text: description),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
