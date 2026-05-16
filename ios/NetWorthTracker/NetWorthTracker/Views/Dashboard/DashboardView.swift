//
//  DashboardView.swift
//  NetWorthTracker
//

import SwiftUI
import Charts

struct DashboardView: View {
    @StateObject private var viewModel = DashboardViewModel()
    @EnvironmentObject var authVM: AuthViewModel
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    if viewModel.isLoading {
                        ProgressView("Loading...")
                            .padding()
                    } else if let error = viewModel.errorMessage {
                        ErrorView(message: error, retryAction: {
                            Task { await viewModel.loadDashboardData() }
                        })
                    } else {
                        // Net Worth Card
                        NetWorthCard(breakdown: viewModel.breakdown)
                        
                        // Health Score
                        if let health = viewModel.healthScore {
                            HealthScoreCard(healthScore: health)
                        }
                        
                        // Asset Allocation Chart
                        if let breakdown = viewModel.breakdown {
                            AssetAllocationSection(breakdown: breakdown)
                        }
                        
                        // Top Holdings
                        TopHoldingsSection(holdings: viewModel.topHoldings)
                    }
                }
                .padding()
            }
            .navigationTitle("Dashboard")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: {
                        authVM.logout()
                    }) {
                        Image(systemName: "person.crop.circle.badge.xmark")
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        Task { await viewModel.refreshPrices() }
                    }) {
                        Image(systemName: "arrow.clockwise")
                    }
                    .disabled(viewModel.isLoading)
                }
            }
            .refreshable {
                await viewModel.loadDashboardData()
            }
        }
        .task {
            await viewModel.loadDashboardData()
        }
    }
}

struct NetWorthCard: View {
    let breakdown: NetWorthBreakdown?
    
    var body: some View {
        VStack(spacing: 15) {
            HStack {
                VStack(alignment: .leading, spacing: 5) {
                    Text("Net Worth")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    
                    Text(formatCurrency(breakdown?.netWorth ?? 0))
                        .font(.system(size: 32, weight: .bold))
                }
                
                Spacer()
                
                Image(systemName: "chart.pie.fill")
                    .font(.title2)
                    .foregroundColor(.blue)
            }
            
            Divider()
            
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Assets")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text(formatCurrency(breakdown?.totalAssets ?? 0))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.green)
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 3) {
                    Text("Liabilities")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text(formatCurrency(breakdown?.totalLiabilities ?? 0))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.red)
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 5)
    }
}

struct HealthScoreCard: View {
    let healthScore: HealthScore
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 5) {
                Text("Health Score")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                
                HStack(alignment: .lastTextBaseline, spacing: 5) {
                    Text("\(healthScore.score)%")
                        .font(.system(size: 28, weight: .bold))
                    
                    if let grade = healthScore.grade {
                        Text(grade)
                            .font(.title3)
                            .fontWeight(.bold)
                            .foregroundColor(gradeColor(grade))
                    }
                }
            }
            
            Spacer()
            
            // Circular progress
            ZStack {
                Circle()
                    .stroke(Color.gray.opacity(0.2), lineWidth: 8)
                
                Circle()
                    .trim(from: 0, to: CGFloat(healthScore.score) / 100)
                    .stroke(healthColor, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .animation(.easeInOut, value: healthScore.score)
            }
            .frame(width: 60, height: 60)
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 5)
    }
    
    var healthColor: Color {
        switch healthScore.score {
        case 80...100: return .green
        case 60..<80: return .yellow
        case 40..<60: return .orange
        default: return .red
        }
    }
    
    func gradeColor(_ grade: String) -> Color {
        switch grade {
        case "A+", "A": return .green
        case "B+", "B": return .blue
        case "C": return .orange
        default: return .red
        }
    }
}

struct AssetAllocationSection: View {
    let breakdown: NetWorthBreakdown
    
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("Asset Allocation")
                .font(.headline)
            
            if #available(iOS 16.0, *) {
                Chart(breakdown.assetAllocation, id: \.name) { item in
                    SectorMark(
                        angle: .value("Value", item.value),
                        innerRadius: .ratio(0.6)
                    )
                    .foregroundStyle(by: .value("Type", item.name))
                }
                .frame(height: 200)
            } else {
                // Fallback for iOS 15
                HStack {
                    ForEach(breakdown.assetAllocation, id: \.name) { item in
                        VStack {
                            Circle()
                                .fill(colorFor(item.color))
                                .frame(width: 12, height: 12)
                            Text(item.name)
                                .font(.caption)
                            Text(formatCurrency(item.value))
                                .font(.caption2)
                        }
                    }
                }
            }
            
            // Allocation list
            VStack(spacing: 10) {
                ForEach(breakdown.assetAllocation.prefix(5), id: \.name) { item in
                    HStack {
                        Circle()
                            .fill(colorFor(item.color))
                            .frame(width: 10, height: 10)
                        
                        Text(item.name)
                            .font(.subheadline)
                        
                        Spacer()
                        
                        Text(formatCurrency(item.value))
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 5)
    }
    
    func colorFor(_ colorName: String) -> Color {
        switch colorName {
        case "blue": return .blue
        case "green": return .green
        case "yellow": return .yellow
        case "orange": return .orange
        case "purple": return .purple
        case "pink": return .pink
        case "teal": return .teal
        case "indigo": return .indigo
        case "cyan": return .cyan
        case "mint": return .mint
        case "brown": return .brown
        default: return .gray
        }
    }
}

struct TopHoldingsSection: View {
    let holdings: [Holding]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 15) {
            Text("Top Holdings")
                .font(.headline)
            
            VStack(spacing: 12) {
                ForEach(holdings) { holding in
                    HoldingRow(holding: holding)
                }
            }
        }
        .padding()
        .background(Color(.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 5)
    }
}

struct HoldingRow: View {
    let holding: Holding
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(holding.symbol)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                
                if let name = holding.name {
                    Text(name)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
                
                Text(holding.assetType.displayName)
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(4)
            }
            
            Spacer()
            
            VStack(alignment: .trailing, spacing: 4) {
                Text(formatCurrency(holding.currentValue ?? 0))
                    .font(.subheadline)
                    .fontWeight(.medium)
                
                if let pnl = holding.unrealizedPnl {
                    HStack(spacing: 2) {
                        Image(systemName: pnl >= 0 ? "arrow.up" : "arrow.down")
                            .font(.caption2)
                        Text(formatCurrency(abs(pnl)))
                            .font(.caption)
                    }
                    .foregroundColor(pnl >= 0 ? .green : .red)
                }
                
                if let pnlPct = holding.pnlPercentage {
                    Text(String(format: "%.2f%%", pnlPct))
                        .font(.caption2)
                        .foregroundColor(pnlPct >= 0 ? .green : .red)
                }
            }
        }
        .padding(.vertical, 8)
    }
}

struct ErrorView: View {
    let message: String
    let retryAction: () -> Void
    
    var body: some View {
        VStack(spacing: 15) {
            Image(systemName: "exclamationmark.triangle")
                .font(.largeTitle)
                .foregroundColor(.orange)
            
            Text("Something went wrong")
                .font(.headline)
            
            Text(message)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            Button("Try Again", action: retryAction)
                .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

func formatCurrency(_ value: Double) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .currency
    formatter.currencySymbol = "₹"
    formatter.locale = Locale(identifier: "en_IN")
    formatter.maximumFractionDigits = 0
    return formatter.string(from: NSNumber(value: value)) ?? "₹0"
}