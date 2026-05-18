// lib/features/personal/family_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_networth/core/services/api_client.dart';

class FamilyScreen extends StatefulWidget {
  const FamilyScreen({super.key});

  @override
  State<FamilyScreen> createState() => _FamilyScreenState();
}

class _FamilyScreenState extends State<FamilyScreen> {
  List<Map<String, dynamic>> _families = [];
  List<Map<String, dynamic>> _invitations = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    try {
      final results = await Future.wait([
        ApiClient.get('/families'),
        ApiClient.get('/families/invitations/pending').catchError((e) => []),
      ]);
      if (results[0] is List) _families = List<Map<String, dynamic>>.from(results[0] as List);
      if (results[1] is List) _invitations = List<Map<String, dynamic>>.from(results[1] as List);
    } catch (e) {
      // Handle error
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _createFamily() async {
    final name = await showDialog<String>(
      context: context,
      builder: (context) {
        final controller = TextEditingController();
        return AlertDialog(
          title: const Text('Create Family'),
          content: TextField(
            controller: controller,
            decoration: const InputDecoration(hintText: 'Family name', border: OutlineInputBorder()),
            autofocus: true,
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
            ElevatedButton(
              onPressed: () => Navigator.pop(context, controller.text),
              child: const Text('Create'),
            ),
          ],
        );
      },
    );
    if (name != null && name.isNotEmpty) {
      try {
        await ApiClient.post('/families', body: {'name': name});
        _loadData();
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
        }
      }
    }
  }

  Future<void> _respondInvitation(String id, bool accept) async {
    try {
      await ApiClient.post('/families/invitations/$id/respond', body: {'accept': accept});
      _loadData();
    } catch (e) {
      // Handle error
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Family')),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: _loadData,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  // Pending Invitations
                  if (_invitations.isNotEmpty) ...[
                    const Text('Pending Invitations', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    ..._invitations.map((inv) => Card(
                      color: Colors.amber[50],
                      child: ListTile(
                        leading: const Icon(Icons.mail, color: Colors.amber),
                        title: Text(inv['familyName']?.toString() ?? ''),
                        subtitle: Text('Invited by ${inv['invitedByName'] ?? "user"}'),
                        trailing: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.check, color: Colors.green),
                              onPressed: () => _respondInvitation(inv['id']?.toString() ?? '', true),
                            ),
                            IconButton(
                              icon: const Icon(Icons.close, color: Colors.red),
                              onPressed: () => _respondInvitation(inv['id']?.toString() ?? '', false),
                            ),
                          ],
                        ),
                      ),
                    )),
                    const SizedBox(height: 24),
                  ],

                  // My Families
                  const Text('My Families', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  if (_families.isEmpty)
                    Center(
                      child: Padding(
                        padding: const EdgeInsets.all(32),
                        child: Column(
                          children: [
                            Icon(Icons.people, size: 64, color: Colors.grey[300]),
                            const SizedBox(height: 16),
                            const Text('No families yet', style: TextStyle(color: Colors.grey)),
                          ],
                        ),
                      ),
                    )
                  else
                    ..._families.map((family) => Card(
                      child: ExpansionTile(
                        leading: CircleAvatar(
                          backgroundColor: Colors.green.withOpacity(0.1),
                          child: const Icon(Icons.family_restroom, color: Colors.green),
                        ),
                        title: Text(family['name']?.toString() ?? ''),
                        subtitle: Text('${family['approvedCount'] ?? 0} members'),
                        children: [
                          if (family['members'] != null)
                            ...((family['members'] as List?) ?? []).map((m) => ListTile(
                              dense: true,
                              leading: const Icon(Icons.person),
                              title: Text(m['userName']?.toString() ?? ''),
                              subtitle: Text(m['userEmail']?.toString() ?? ''),
                              trailing: Text(
                                m['status']?.toString() ?? '',
                                style: TextStyle(
                                  color: m['status'] == 'APPROVED' ? Colors.green : Colors.orange,
                                  fontSize: 12,
                                ),
                              ),
                            )),
                        ],
                      ),
                    )),
                ],
              ),
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _createFamily,
        icon: const Icon(Icons.add),
        label: const Text('Create Family'),
      ),
    );
  }
}
