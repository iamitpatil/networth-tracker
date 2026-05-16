//
//  NetWorthTrackerApp.swift
//  NetWorthTracker
//
//  Created by OpenCode on 17/05/26.
//

import SwiftUI

@main
struct NetWorthTrackerApp: App {
    @StateObject private var authVM = AuthViewModel()
    
    var body: some Scene {
        WindowGroup {
            Group {
                if authVM.isAuthenticated {
                    MainTabView()
                        .environmentObject(authVM)
                } else {
                    LoginView()
                        .environmentObject(authVM)
                }
            }
        }
    }
}