//
//  DashboardViewModel.swift
//  NetWorthTracker
//

import SwiftUI
import Combine

@MainActor
class DashboardViewModel: ObservableObject {
    @Published var breakdown: NetWorthBreakdown?
    @Published var healthScore: HealthScore?
    @Published var holdings: [Holding] = []
    @Published var netWorthHistory: [NetWorthHistory] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    private let apiClient = APIClient.shared
    
    func loadDashboardData() async {
        isLoading = true
        errorMessage = nil
        
        do {
            async let breakdownTask: NetWorthBreakdown = apiClient.get(APIConfig.Endpoints.netWorthBreakdown)
            async let healthTask: HealthScore = apiClient.get(APIConfig.Endpoints.healthScore)
            async let holdingsTask: [Holding] = apiClient.get(APIConfig.Endpoints.holdings)
            
            let (breakdownResult, healthResult, holdingsResult) = try await (breakdownTask, healthTask, holdingsTask)
            
            breakdown = breakdownResult
            healthScore = healthResult
            holdings = holdingsResult
        } catch {
            errorMessage = "Failed to load dashboard: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
    
    func refreshPrices() async {
        do {
            let _: EmptyResponse = try await apiClient.post(APIConfig.Endpoints.refreshPrices)
            await loadDashboardData()
        } catch {
            errorMessage = "Failed to refresh prices"
        }
    }
    
    var topHoldings: [Holding] {
        holdings.sorted { ($0.currentValue ?? 0) > ($1.currentValue ?? 0) }.prefix(5).map { $0 }
    }
    
    var totalPnL: Double {
        holdings.reduce(0) { $0 + ($1.unrealizedPnl ?? 0) }
    }
}