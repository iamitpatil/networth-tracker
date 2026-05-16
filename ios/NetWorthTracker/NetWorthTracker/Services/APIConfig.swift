//
//  APIConfig.swift
//  NetWorthTracker
//

import Foundation

enum APIConfig {
    static let baseURL = "http://localhost:8080/api/v1"
    
    enum Endpoints {
        // Auth
        static let login = "/auth/login"
        static let register = "/auth/register"
        static let refresh = "/auth/refresh"
        
        // Portfolio
        static let holdings = "/portfolio/holdings"
        static let transactions = "/portfolio/transactions"
        static let summary = "/portfolio/summary"
        
        // Net Worth
        static let netWorth = "/net-worth"
        static let netWorthBreakdown = "/net-worth/breakdown"
        static let netWorthHistory = "/net-worth/history"
        static let healthScore = "/net-worth/health-score"
        
        // Market Data
        static let symbols = "/symbols"
        static let refreshPrices = "/portfolio/refresh-prices"
        
        // Goals
        static let goals = "/goals"
        
        // Bank Accounts
        static let bankAccounts = "/bank-accounts"
        
        // Tax
        static let taxSummary = "/tax/summary"
        static let capitalGains = "/tax/capital-gains"
        
        // Liabilities
        static let liabilities = "/liabilities"
        
        // AI
        static let aiChat = "/ai/chat"
        static let aiInsights = "/ai/insights"
        
        // Family
        static let family = "/family"
        
        // Import
        static let importJob = "/import"
    }
}