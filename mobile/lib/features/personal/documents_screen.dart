// lib/features/personal/documents_screen.dart
import 'dart:convert';
import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:flutter_networth/core/services/api_client.dart';

class DocumentsScreen extends StatefulWidget {
  const DocumentsScreen({super.key});

  @override
  State<DocumentsScreen> createState() => _DocumentsScreenState();
}

class _DocumentsScreenState extends State<DocumentsScreen> {
  List<Map<String, dynamic>> _documents = [];
  Map<String, List<Map<String, dynamic>>> _groupedDocs = {};
  bool _isLoading = true;
  bool _groupedView = true;

  @override
  void initState() {
    super.initState();
    _loadDocuments();
  }

  Future<void> _loadDocuments() async {
    setState(() => _isLoading = true);
    try {
      final results = await Future.wait([
        ApiClient.get('/documents'),
        ApiClient.get('/documents/grouped').catchError((e) => {}),
      ]);
      if (results[0] is List) {
        _documents = List<Map<String, dynamic>>.from(results[0] as List);
      }
      if (results[1] is Map) {
        final groupedMap = results[1] as Map;
        _groupedDocs = {};
        groupedMap.forEach((key, value) {
          if (value is List) {
            _groupedDocs[key.toString()] = List<Map<String, dynamic>>.from(value);
          }
        });
      }
    } catch (e) {
      // Handle error
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _deleteDocument(String id) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Document?'),
        content: const Text('This cannot be undone.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Delete', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await ApiClient.delete('/documents/$id');
      _loadDocuments();
    } catch (e) {
      // Handle error
    }
  }

  Future<void> _viewDocument(Map<String, dynamic> doc) async {
    try {
      final token = await ApiClient.getToken();
      final id = doc['id']?.toString() ?? '';
      final filename = doc['originalFilename']?.toString() ?? 'document';
      
      // Show loading
      showDialog(
        context: context,
        barrierDismissible: false,
        builder: (_) => const Center(child: CircularProgressIndicator()),
      );

      final response = await http.get(
        Uri.parse('${ApiClient.baseUrl}/documents/$id/view'),
        headers: {'Authorization': 'Bearer $token'},
      );

      if (mounted) Navigator.pop(context); // Close loading

      if (response.statusCode == 200) {
        final contentType = doc['contentType']?.toString() ?? 'application/octet-stream';
        _openInViewer(response.bodyBytes, filename, contentType);
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to load document (${response.statusCode})')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        Navigator.pop(context); // Close loading if still open
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  void _openInViewer(List<int> bytes, String filename, String contentType) {
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Container(
          width: MediaQuery.of(context).size.width * 0.9,
          height: MediaQuery.of(context).size.height * 0.85,
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              // Header
              Row(
                children: [
                  const Icon(Icons.description, color: Colors.blue),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      filename,
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              const Divider(),

              // Content
              Expanded(child: _buildContentView(bytes, contentType, filename)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildContentView(List<int> bytes, String contentType, String filename) {
    if (contentType.startsWith('image/')) {
      return InteractiveViewer(
        child: Center(
          child: Image.memory(
            Uint8List.fromList(bytes),
            errorBuilder: (_, __, ___) => const Center(child: Text('Failed to load image')),
          ),
        ),
      );
    }

    if (contentType == 'application/pdf' || filename.toLowerCase().endsWith('.pdf')) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.picture_as_pdf, size: 80, color: Colors.red),
            const SizedBox(height: 16),
            Text(filename, style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            Text(
              'PDF file (${_formatBytes(bytes.length)})',
              style: TextStyle(color: Colors.grey[600]),
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: () {
                // Open in new tab on web
                final token = ApiClient.getToken();
                token.then((t) {
                  final url = '${ApiClient.baseUrl}/documents/${_getDocId(filename)}/view';
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: const Text('Use download to view PDF on mobile'),
                      action: SnackBarAction(
                        label: 'OK',
                        onPressed: () {},
                      ),
                    ),
                  );
                });
              },
              icon: const Icon(Icons.open_in_new),
              label: const Text('Download to View'),
            ),
          ],
        ),
      );
    }

    if (contentType.startsWith('text/')) {
      try {
        final text = utf8.decode(bytes);
        return SingleChildScrollView(
          child: Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Colors.grey[100],
              borderRadius: BorderRadius.circular(8),
            ),
            child: SelectableText(
              text,
              style: const TextStyle(fontFamily: 'monospace', fontSize: 13),
            ),
          ),
        );
      } catch (e) {
        // Fall through
      }
    }

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(_getFileIcon(filename), size: 80, color: Colors.grey),
          const SizedBox(height: 16),
          Text(filename, style: const TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Text(
            '${_formatBytes(bytes.length)} • $contentType',
            style: TextStyle(color: Colors.grey[600], fontSize: 12),
          ),
          const SizedBox(height: 16),
          const Text(
            'Preview not available for this file type',
            style: TextStyle(color: Colors.grey),
          ),
        ],
      ),
    );
  }

  String _getDocId(String filename) => filename;

  String _formatBytes(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    return '${(bytes / 1024 / 1024).toStringAsFixed(1)} MB';
  }

  Color _getCategoryColor(String category) {
    switch (category) {
      case 'INVOICE': return Colors.blue;
      case 'ID_PROOF': return Colors.green;
      case 'STATEMENT': return Colors.orange;
      case 'REPORT': return Colors.purple;
      default: return Colors.grey;
    }
  }

  IconData _getFileIcon(String? filename) {
    if (filename == null) return Icons.insert_drive_file;
    final ext = filename.split('.').last.toLowerCase();
    if (['pdf'].contains(ext)) return Icons.picture_as_pdf;
    if (['jpg', 'jpeg', 'png', 'gif'].contains(ext)) return Icons.image;
    if (['doc', 'docx'].contains(ext)) return Icons.description;
    if (['xls', 'xlsx'].contains(ext)) return Icons.table_chart;
    return Icons.insert_drive_file;
  }

  @override
  Widget build(BuildContext context) {
    final totalSize = _documents.fold<int>(0, (sum, d) => sum + ((d['fileSize'] ?? 0) as int));

    return Scaffold(
      appBar: AppBar(
        title: const Text('Documents'),
        actions: [
          IconButton(
            icon: Icon(_groupedView ? Icons.list : Icons.folder_open),
            tooltip: _groupedView ? 'Show flat list' : 'Show grouped',
            onPressed: () => setState(() => _groupedView = !_groupedView),
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _loadDocuments,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  // Summary
                  Row(
                    children: [
                      Expanded(child: _buildStat('Total Files', '${_documents.length}', Colors.blue, Icons.folder)),
                      const SizedBox(width: 8),
                      Expanded(child: _buildStat('Storage', _formatBytes(totalSize), Colors.green, Icons.storage)),
                    ],
                  ),
                  const SizedBox(height: 16),

                  if (_documents.isEmpty)
                    Center(
                      child: Padding(
                        padding: const EdgeInsets.all(32),
                        child: Column(
                          children: [
                            Icon(Icons.folder_open, size: 64, color: Colors.grey[300]),
                            const SizedBox(height: 16),
                            const Text('No documents', style: TextStyle(color: Colors.grey, fontSize: 16)),
                            const SizedBox(height: 8),
                            const Text('Upload from web app', style: TextStyle(color: Colors.grey, fontSize: 12)),
                          ],
                        ),
                      ),
                    )
                  else if (_groupedView && _groupedDocs.isNotEmpty)
                    ..._groupedDocs.entries.expand((entry) => [
                      Padding(
                        padding: const EdgeInsets.only(top: 8, bottom: 4),
                        child: Text(
                          entry.key,
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                        ),
                      ),
                      ...entry.value.map((doc) => _buildDocCard(doc)),
                    ])
                  else
                    ..._documents.map((doc) {
                      final category = doc['category']?.toString() ?? 'OTHER';
                      final color = _getCategoryColor(category);
                      return Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: InkWell(
                          onTap: () => _viewDocument(doc),
                          borderRadius: BorderRadius.circular(12),
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Row(
                              children: [
                                CircleAvatar(
                                  backgroundColor: color.withOpacity(0.1),
                                  child: Icon(_getFileIcon(doc['originalFilename']), color: color),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        doc['originalFilename']?.toString() ?? '',
                                        style: const TextStyle(fontWeight: FontWeight.w600),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                      const SizedBox(height: 4),
                                      Row(
                                        children: [
                                          Container(
                                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                            decoration: BoxDecoration(
                                              color: color.withOpacity(0.1),
                                              borderRadius: BorderRadius.circular(4),
                                            ),
                                            child: Text(
                                              category,
                                              style: TextStyle(fontSize: 10, color: color, fontWeight: FontWeight.bold),
                                            ),
                                          ),
                                          const SizedBox(width: 8),
                                          Text(_formatBytes((doc['fileSize'] ?? 0) as int), style: const TextStyle(fontSize: 12)),
                                        ],
                                      ),
                                    ],
                                  ),
                                ),
                                IconButton(
                                  icon: const Icon(Icons.visibility, color: Colors.green),
                                  tooltip: 'View',
                                  onPressed: () => _viewDocument(doc),
                                ),
                                IconButton(
                                  icon: const Icon(Icons.delete, color: Colors.red),
                                  tooltip: 'Delete',
                                  onPressed: () => _deleteDocument(doc['id']?.toString() ?? ''),
                                ),
                              ],
                            ),
                          ),
                        ),
                      );
                    }),
                ],
              ),
            ),
    );
  }

  Widget _buildStat(String label, String value, Color color, IconData icon) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(icon, color: color, size: 28),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: TextStyle(fontSize: 12, color: Colors.grey[600])),
                Text(value, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color)),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDocCard(Map<String, dynamic> doc) {
    final category = doc['category']?.toString() ?? 'OTHER';
    final color = _getCategoryColor(category);
    return Card(
      margin: const EdgeInsets.only(bottom: 6),
      child: InkWell(
        onTap: () => _viewDocument(doc),
        borderRadius: BorderRadius.circular(12),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Row(
            children: [
              CircleAvatar(
                radius: 18,
                backgroundColor: color.withOpacity(0.1),
                child: Icon(_getFileIcon(doc['originalFilename']), color: color, size: 18),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      doc['originalFilename']?.toString() ?? '',
                      style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 2),
                    Row(
                      children: [
                        Text(
                          category,
                          style: TextStyle(fontSize: 10, color: color, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(width: 8),
                        Text(_formatBytes((doc['fileSize'] ?? 0) as int), style: const TextStyle(fontSize: 10, color: Colors.grey)),
                      ],
                    ),
                  ],
                ),
              ),
              IconButton(
                icon: const Icon(Icons.visibility, color: Colors.green, size: 18),
                tooltip: 'View',
                onPressed: () => _viewDocument(doc),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
              ),
              const SizedBox(width: 8),
              IconButton(
                icon: const Icon(Icons.delete, color: Colors.red, size: 18),
                tooltip: 'Delete',
                onPressed: () => _deleteDocument(doc['id']?.toString() ?? ''),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
