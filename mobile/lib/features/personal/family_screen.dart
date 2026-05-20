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

  Future<void> _deleteFamily(String id, String name) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Family'),
        content: Text('Are you sure you want to delete "$name"? This action cannot be undone.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancel')),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      try {
        await ApiClient.delete('/families/$id');
        _loadData();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Family deleted'), backgroundColor: Colors.green),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
        }
      }
    }
  }

  Future<void> _leaveFamily(String id) async {
    try {
      await ApiClient.delete('/families/$id/leave');
      _loadData();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Left family successfully'), backgroundColor: Colors.green),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e')));
      }
    }
  }

  Future<void> _inviteMember(String familyId) async {
    final email = await showDialog<String>(
      context: context,
      builder: (context) {
        final controller = TextEditingController();
        return AlertDialog(
          title: const Text('Invite Member'),
          content: TextField(
            controller: controller,
            decoration: const InputDecoration(
              hintText: 'Email address',
              border: OutlineInputBorder(),
            ),
            keyboardType: TextInputType.emailAddress,
            autofocus: true,
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
            ElevatedButton(
              onPressed: () => Navigator.pop(context, controller.text),
              child: const Text('Invite'),
            ),
          ],
        );
      },
    );
    if (email != null && email.isNotEmpty) {
      try {
        await ApiClient.post('/families/$familyId/invite', body: {'email': email});
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Invitation sent to $email'), backgroundColor: Colors.green),
          );
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to invite: $e'), backgroundColor: Colors.red),
          );
        }
      }
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
                    ..._families.map((family) {
                      final familyId = family['id']?.toString() ?? '';
                      final isAdmin = family['isAdmin'] == true || family['isCreator'] == true;
                      return Card(
                        child: ExpansionTile(
                          leading: CircleAvatar(
                            backgroundColor: Colors.green.withOpacity(0.1),
                            child: const Icon(Icons.family_restroom, color: Colors.green),
                          ),
                          title: Row(
                            children: [
                              Expanded(child: Text(family['name']?.toString() ?? '')),
                              IconButton(
                                icon: const Icon(Icons.delete, color: Colors.red, size: 20),
                                tooltip: 'Delete family',
                                onPressed: () => _deleteFamily(familyId, family['name']?.toString() ?? ''),
                              ),
                            ],
                          ),
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
                            Padding(
                              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                              child: Row(
                                children: [
                                  if (!isAdmin)
                                    Expanded(
                                      child: OutlinedButton.icon(
                                        onPressed: () => _leaveFamily(familyId),
                                        icon: const Icon(Icons.exit_to_app, size: 18),
                                        label: const Text('Leave'),
                                        style: OutlinedButton.styleFrom(
                                          foregroundColor: Colors.orange,
                                          side: const BorderSide(color: Colors.orange),
                                        ),
                                      ),
                                    ),
                                  if (!isAdmin) const SizedBox(width: 8),
                                  Expanded(
                                    child: OutlinedButton.icon(
                                      onPressed: () => _inviteMember(familyId),
                                      icon: const Icon(Icons.person_add, size: 18),
                                      label: const Text('Invite'),
                                      style: OutlinedButton.styleFrom(
                                        foregroundColor: Colors.blue,
                                        side: const BorderSide(color: Colors.blue),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      );
                    }),
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
