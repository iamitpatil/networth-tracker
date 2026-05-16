//
//  MainTabView.swift
//  NetWorthTracker
//

import SwiftUI

struct MainTabView: View {
    @State private var selectedTab = 0
    
    var body: some View {
        TabView(selection: $selectedTab) {
            DashboardView()
                .tabItem {
                    Label("Dashboard", systemImage: "chart.pie.fill")
                }
                .tag(0)
            
            HoldingsView()
                .tabItem {
                    Label("Holdings", systemImage: "chart.line.uptrend.xyaxis")
                }
                .tag(1)
            
            NetWorthView()
                .tabItem {
                    Label("Net Worth", systemImage: "indianrupeesign.circle.fill")
                }
                .tag(2)
            
            GoalsView()
                .tabItem {
                    Label("Goals", systemImage: "target")
                }
                .tag(3)
            
            MoreView()
                .tabItem {
                    Label("More", systemImage: "ellipsis.circle.fill")
                }
                .tag(4)
        }
        .accentColor(.blue)
    }
}