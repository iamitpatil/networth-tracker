//
//  NetWorthView.swift
//  NetWorthTracker
//

import SwiftUI
import Charts

struct NetWorthView: View {
    @StateObject private var viewModel = NetWorthViewModel()
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    if viewModel.isLoading {
                        ProgressView()
                    } else if let breakdown = viewModel.breakdown {
                        // Total Net Worth
                        TotalNetWorthCard(breakdown: breakdown)
                        
                        // Assets & Liabilities
                        AssetsLiabilitiesSection(breakdown: breakdown)
                        
                        // Asset Breakdown
                        AssetBreakdownSection(breakdown: breakdown)
                        
                        // Net Worth History Chart
                        NetWorthHistorySection(history: viewModel.history)
                    }
                }
                .padding()
            }
            .navigationTitle("Net Worth")
            .refreshable {
                await viewModel.loadData()
            }
        }
        .task {
            await viewModel.loadData()
        }
    }
}

struct TotalNetWorthCard: View {
    let breakdown: NetWorthBreakdown
    
    var body: some View {
        VStack(spacing: 20) {
            Text("Total Net Worth")
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            Text(formatCurrency(breakdown.netWorth))
                .font(.system(size: 44, weight: .bold))
            
            HStack(spacing: 30) {
                VStack(spacing: 5) {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.title2)
                        .foregroundColor(.green)
                    Text("Assets")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text(formatCurrency(breakdown.totalAssets))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                }
                
                VStack(spacing: 5) {
                    Image(systemName: "arrow.down.circle.fill")
                        .font(.title2)
                        .foregroundColor(.red)
                    Text("Liabilities")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text(formatCurrency(breakdown.totalLiabilities))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                }
            }
        }
        .padding()
        .background(Color.blue.opacity(0.1))
        .cornerRadius(20)
    }
}

struct AssetsLiabilitiesSection: View {
    let breakdown: NetWorthBreakdown
    
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("Breakdown")
                .font(.headline)
            
            HStack(spacing: 15) {
                // Assets Card
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Image(systemName: "creditcard.fill")
                            .foregroundColor(.green)
                        Text("Assets")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                    }
                    
                    Text(formatCurrency(breakdown.totalAssets))
                        .font(.title3)
                        .fontWeight(.bold)
                    
                    Text("\(Int((breakdown.totalAssets / (breakdown.netWorth + breakdown.totalLiabilities)) * 100))%")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(Color.green.opacity(0.1))
                .cornerRadius(16)
                
                // Liabilities Card
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Image(systemName: "creditcard")
                            .foregroundColor(.red)
                        Text("Liabilities")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                    }
                    
                    Text(formatCurrency(breakdown.totalLiabilities))
                        .font(.title3)
                        .fontWeight(.bold)
                    
                    Text("\(Int((breakdown.totalLiabilities / breakdown.totalAssets) * 100))%")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(Color.red.opacity(0.1))
                .cornerRadius(16)
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 5)
    }
}

struct AssetBreakdownSection: View {
    let breakdown: NetWorthBreakdown
    
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("Asset Categories")
                .font(.headline)
            
            VStack(spacing: 12) {
                AssetRow(name: "Equity", value: breakdown.equityValue, total: breakdown.totalAssets, color: .blue, icon: "chart.line.uptrend.xyaxis")
                AssetRow(name: "Mutual Funds", value: breakdown.mutualFundValue, total: breakdown.totalAssets, color: .green, icon: "arrow.up.arrow.down")
                AssetRow(name: "Gold", value: breakdown.goldValue, total: breakdown.totalAssets, color: .yellow, icon: "diamond.fill")
                AssetRow(name: "Real Estate", value: breakdown.realEstateValue, total: breakdown.totalAssets, color: .orange, icon: "house.fill")
                AssetRow(name: "Fixed Deposits", value: breakdown.fdValue, total: breakdown.totalAssets, color: .purple, icon: "banknote.fill")
                AssetRow(name: "Cash", value: breakdown.cashValue, total: breakdown.totalAssets, color: .cyan, icon: "banknote")
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 5)
    }
}

struct AssetRow: View {
    let name: String
    let value: Double
    let total: Double
    let color: Color
    let icon: String
    
    var percentage: Double {
        guard total > 0 else { return 0 }
        return (value / total) * 100
    }
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(color)
                .frame(width: 32)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(name)
                    .font(.subheadline)
                    .fontWeight(.medium)
                
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.gray.opacity(0.2))
                            .cornerRadius(2)
                        
                        Rectangle()
                            .fill(color)
                            .frame(width: geometry.size.width * CGFloat(percentage / 100))
                            .cornerRadius(2)
                    }
                }
                .frame(height: 4)
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 2) {
                Text(formatCurrency(value))
                    .font(.subheadline)
                    .fontWeight(.medium)
                Text(String(format: "%.1f%%", percentage))
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

struct NetWorthHistorySection: View {
    let history: [NetWorthHistory]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("Net Worth History")
                .font(.headline)
            
            if #available(iOS 16.0, *), !history.isEmpty {
                Chart(history) { item in
                    LineMark(
                        x: .value("Date", item.date, unit: .day),
                        y: .value("Value", item.value)
                    )
                    .foregroundStyle(.blue)
                    .interpolationMethod(.catmullRom)
                }
                .frame(height: 200)
            } else {
                Text("Historical data will appear here")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding()
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.05), radius: 5)
    }
}

@MainActor
class NetWorthViewModel: ObservableObject {
    @Published var breakdown: NetWorthBreakdown?
    @Published var history: [NetWorthHistory] = []
    @Published var isLoading = false
    
    func loadData() async {
        isLoading = true
        // API calls would go here
        isLoading = false
    }
}