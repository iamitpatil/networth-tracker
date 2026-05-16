//
//  HoldingsView.swift
//  NetWorthTracker
//

import SwiftUI

struct HoldingsView: View {
    @StateObject private var viewModel = HoldingsViewModel()
    @State private var showingAddHolding = false
    
    var body: some View {
        NavigationView {
            List {
                // Summary Section
                Section {
                    HoldingsSummaryView(viewModel: viewModel)
                }
                
                // Asset Type Filter
                Section {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            FilterChip(
                                title: "All",
                                isSelected: viewModel.selectedAssetType == nil,
                                action: {
                                    viewModel.selectedAssetType = nil
                                    viewModel.filterHoldings()
                                }
                            )
                            
                            ForEach(AssetType.allCases, id: \.self) { type in
                                FilterChip(
                                    title: type.displayName,
                                    isSelected: viewModel.selectedAssetType == type,
                                    action: {
                                        viewModel.selectedAssetType = type
                                        viewModel.filterHoldings()
                                    }
                                )
                            }
                        }
                        .padding(.horizontal)
                    }
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                }
                
                // Holdings List
                Section(header: Text("Holdings")) {
                    ForEach(viewModel.filteredHoldings) { holding in
                        NavigationLink(destination: HoldingDetailView(holding: holding)) {
                            HoldingListRow(holding: holding)
                        }
                    }
                    .onDelete { indexSet in
                        for index in indexSet {
                            let holding = viewModel.filteredHoldings[index]
                            Task {
                                await viewModel.deleteHolding(id: holding.id)
                            }
                        }
                    }
                }
            }
            .listStyle(InsetGroupedListStyle())
            .navigationTitle("Holdings")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingAddHolding = true }) {
                        Image(systemName: "plus.circle.fill")
                            .font(.title3)
                    }
                }
            }
            .sheet(isPresented: $showingAddHolding) {
                AddHoldingView()
            }
            .refreshable {
                await viewModel.loadHoldings()
            }
        }
        .task {
            await viewModel.loadHoldings()
        }
    }
}

struct HoldingsSummaryView: View {
    let viewModel: HoldingsViewModel
    
    var body: some View {
        HStack(spacing: 20) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Total Value")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text(formatCurrency(viewModel.totalValue(for: nil)))
                    .font(.title2)
                    .fontWeight(.bold)
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 8) {
                Text("Holdings")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("\(viewModel.holdings.count)")
                    .font(.title2)
                    .fontWeight(.bold)
            }
        }
        .padding()
        .background(Color.blue.opacity(0.1))
        .cornerRadius(12)
    }
}

struct FilterChip: View {
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .fontWeight(isSelected ? .semibold : .regular)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color.blue : Color.gray.opacity(0.2))
                .foregroundColor(isSelected ? .white : .primary)
                .cornerRadius(20)
        }
    }
}

struct HoldingListRow: View {
    let holding: Holding
    
    var body: some View {
        HStack(spacing: 15) {
            // Icon
            ZStack {
                Circle()
                    .fill(colorForAssetType(holding.assetType).opacity(0.2))
                    .frame(width: 44, height: 44)
                
                Image(systemName: iconForAssetType(holding.assetType))
                    .font(.title3)
                    .foregroundColor(colorForAssetType(holding.assetType))
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(holding.symbol)
                    .font(.headline)
                
                if let name = holding.name {
                    Text(name)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                
                Text("\(String(format: "%.2f", holding.quantity)) units • ₹\(String(format: "%.2f", holding.averageBuyPrice))")
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 4) {
                Text(formatCurrency(holding.currentValue ?? 0))
                    .font(.subheadline)
                    .fontWeight(.semibold)
                
                if let pnl = holding.unrealizedPnl {
                    HStack(spacing: 2) {
                        Image(systemName: pnl >= 0 ? "arrow.up" : "arrow.down")
                            .font(.caption2)
                        Text(formatCurrency(abs(pnl)))
                            .font(.caption)
                    }
                    .foregroundColor(pnl >= 0 ? .green : .red)
                }
            }
        }
        .padding(.vertical, 4)
    }
    
    func colorForAssetType(_ type: AssetType) -> Color {
        switch type {
        case .equity: return .blue
        case .mutualFund: return .green
        case .gold: return .yellow
        case .fixedDeposit: return .purple
        case .ppf: return .teal
        case .epf: return .orange
        case .nps: return .pink
        default: return .gray
        }
    }
    
    func iconForAssetType(_ type: AssetType) -> String {
        switch type {
        case .equity: return "chart.line.uptrend.xyaxis"
        case .mutualFund: return "arrow.up.arrow.down"
        case .gold: return "diamond.fill"
        case .fixedDeposit: return "banknote.fill"
        case .ppf: return "shield.fill"
        case .epf: return "building.columns.fill"
        case .nps: return "person.2.fill"
        default: return "dollarsign.circle.fill"
        }
    }
}

struct HoldingDetailView: View {
    let holding: Holding
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                VStack(spacing: 10) {
                    Text(holding.symbol)
                        .font(.largeTitle)
                        .fontWeight(.bold)
                    
                    if let name = holding.name {
                        Text(name)
                            .font(.title3)
                            .foregroundColor(.secondary)
                    }
                    
                    Text(holding.assetType.displayName)
                        .font(.caption)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 4)
                        .background(Color.blue.opacity(0.1))
                        .foregroundColor(.blue)
                        .cornerRadius(8)
                }
                .padding()
                
                // Value Card
                VStack(spacing: 15) {
                    HStack {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("Current Value")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(formatCurrency(holding.currentValue ?? 0))
                                .font(.title)
                                .fontWeight(.bold)
                        }
                        
                        Spacer()
                        
                        if let pnl = holding.unrealizedPnl {
                            VStack(alignment: .trailing, spacing: 5) {
                                Text(pnl >= 0 ? "Profit" : "Loss")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                Text(formatCurrency(abs(pnl)))
                                    .font(.title3)
                                    .fontWeight(.semibold)
                                    .foregroundColor(pnl >= 0 ? .green : .red)
                            }
                        }
                    }
                    
                    Divider()
                    
                    HStack {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("Invested")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text(formatCurrency(holding.quantity * holding.averageBuyPrice))
                                .font(.subheadline)
                        }
                        
                        Spacer()
                        
                        VStack(alignment: .trailing, spacing: 5) {
                            Text("Quantity")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text("\(String(format: "%.4f", holding.quantity))")
                                .font(.subheadline)
                        }
                    }
                }
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(16)
                .shadow(color: .black.opacity(0.1), radius: 5)
                .padding(.horizontal)
                
                // Price Info
                VStack(alignment: .leading, spacing: 15) {
                    Text("Price Details")
                        .font(.headline)
                    
                    HStack {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("Current Price")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text("₹\(String(format: "%.2f", holding.currentPrice ?? 0))")
                                .font(.subheadline)
                                .fontWeight(.medium)
                        }
                        
                        Spacer()
                        
                        VStack(alignment: .trailing, spacing: 5) {
                            Text("Buy Price")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            Text("₹\(String(format: "%.2f", holding.averageBuyPrice))")
                                .font(.subheadline)
                        }
                    }
                    
                    if let dayChange = holding.dayChangePct {
                        HStack {
                            Text("Day Change")
                                .font(.caption)
                                .foregroundColor(.secondary)
                            
                            Spacer()
                            
                            Text(String(format: "%.2f%%", dayChange))
                                .font(.subheadline)
                                .fontWeight(.medium)
                                .foregroundColor(dayChange >= 0 ? .green : .red)
                        }
                    }
                }
                .padding()
                .background(Color(.systemBackground))
                .cornerRadius(16)
                .shadow(color: .black.opacity(0.1), radius: 5)
                .padding(.horizontal)
                
                Spacer()
            }
        }
        .navigationTitle("Details")
        .navigationBarTitleDisplayMode(.inline)
    }
}