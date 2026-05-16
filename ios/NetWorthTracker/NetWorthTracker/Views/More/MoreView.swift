//
//  MoreView.swift
//  NetWorthTracker
//

import SwiftUI

struct MoreView: View {
    var body: some View {
        NavigationView {
            List {
                Section("Finance") {
                    NavigationLink(destination: TaxView()) {
                        Label("Tax Planning", systemImage: "doc.text.fill")
                    }
                    
                    NavigationLink(destination: LiabilitiesView()) {
                        Label("Loans & Liabilities", systemImage: "creditcard.fill")
                    }
                    
                    NavigationLink(destination: BankAccountsView()) {
                        Label("Bank Accounts", systemImage: "building.columns.fill")
                    }
                }
                
                Section("Tools") {
                    NavigationLink(destination: AIChatView()) {
                        Label("AI Assistant", systemImage: "brain.fill")
                    }
                    
                    NavigationLink(destination: ImportView()) {
                        Label("Import Data", systemImage: "square.and.arrow.down.fill")
                    }
                }
                
                Section("Account") {
                    NavigationLink(destination: FamilyView()) {
                        Label("Family Dashboard", systemImage: "person.2.fill")
                    }
                    
                    NavigationLink(destination: SettingsView()) {
                        Label("Settings", systemImage: "gear")
                    }
                }
            }
            .navigationTitle("More")
        }
    }
}

// Placeholder views
struct TaxView: View {
    var body: some View {
        Text("Tax Planning")
            .navigationTitle("Tax")
    }
}

struct LiabilitiesView: View {
    var body: some View {
        Text("Loans & Liabilities")
            .navigationTitle("Liabilities")
    }
}

struct BankAccountsView: View {
    var body: some View {
        Text("Bank Accounts")
            .navigationTitle("Accounts")
    }
}

struct AIChatView: View {
    var body: some View {
        Text("AI Assistant")
            .navigationTitle("AI Chat")
    }
}

struct ImportView: View {
    var body: some View {
        Text("Import Data")
            .navigationTitle("Import")
    }
}

struct FamilyView: View {
    var body: some View {
        Text("Family Dashboard")
            .navigationTitle("Family")
    }
}

struct SettingsView: View {
    var body: some View {
        Text("Settings")
            .navigationTitle("Settings")
    }
}