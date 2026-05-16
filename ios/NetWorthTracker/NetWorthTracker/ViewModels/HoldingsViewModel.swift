//
//  HoldingsViewModel.swift
//  NetWorthTracker
//

import SwiftUI

@MainActor
class HoldingsViewModel: ObservableObject {
    @Published var holdings: [Holding] = []
    @Published var filteredHoldings: [Holding] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var selectedAssetType: AssetType?
    
    private let apiClient = APIClient.shared
    
    func loadHoldings() async {
        isLoading = true
        errorMessage = nil
        
        do {
            holdings = try await apiClient.get(APIConfig.Endpoints.holdings)
            filterHoldings()
        } catch {
            errorMessage = "Failed to load holdings: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
    
    func filterHoldings() {
        if let selectedType = selectedAssetType {
            filteredHoldings = holdings.filter { $0.assetType == selectedType }
        } else {
            filteredHoldings = holdings
        }
    }
    
    func deleteHolding(id: String) async {
        do {
            let _: EmptyResponse = try await apiClient.delete("\(APIConfig.Endpoints.holdings)/\(id)")
            await loadHoldings()
        } catch {
            errorMessage = "Failed to delete holding"
        }
    }
    
    func totalValue(for type: AssetType?) -> Double {
        let targetHoldings = type == nil ? holdings : holdings.filter { $0.assetType == type }
        return targetHoldings.reduce(0) { $0 + ($1.currentValue ?? 0) }
    }
    
    var assetTypeBreakdown: [(type: AssetType, value: Double, percentage: Double)] {
        let total = totalValue(for: nil)
        guard total > 0 else { return [] }
        
        return AssetType.allCases.compactMap { type in
            let value = totalValue(for: type)
            guard value > 0 else { return nil }
            return (type, value, (value / total) * 100)
        }.sorted { $0.value > $1.value }
    }
}