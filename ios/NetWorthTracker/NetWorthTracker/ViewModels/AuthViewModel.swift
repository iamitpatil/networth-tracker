//
//  AuthViewModel.swift
//  NetWorthTracker
//

import SwiftUI
import Combine

@MainActor
class AuthViewModel: ObservableObject {
    @Published var isAuthenticated = false
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var user: User?
    
    private let apiClient = APIClient.shared
    
    init() {
        checkAuthentication()
    }
    
    func checkAuthentication() {
        if apiClient.isAuthenticated() {
            isAuthenticated = true
            // Could load user details here
        }
    }
    
    func login(email: String, password: String) async {
        isLoading = true
        errorMessage = nil
        
        do {
            let request = LoginRequest(email: email, password: password)
            let response: AuthResponse = try await apiClient.post(APIConfig.Endpoints.login, body: request)
            
            apiClient.setAccessToken(response.accessToken)
            user = response.user
            isAuthenticated = true
        } catch APIError.unauthorized {
            errorMessage = "Invalid email or password"
        } catch {
            errorMessage = "Login failed: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
    
    func register(name: String, email: String, password: String) async {
        isLoading = true
        errorMessage = nil
        
        do {
            let request = RegisterRequest(name: name, email: email, password: password)
            let response: AuthResponse = try await apiClient.post(APIConfig.Endpoints.register, body: request)
            
            apiClient.setAccessToken(response.accessToken)
            user = response.user
            isAuthenticated = true
        } catch let APIError.serverError(_, message) {
            errorMessage = message
        } catch {
            errorMessage = "Registration failed: \(error.localizedDescription)"
        }
        
        isLoading = false
    }
    
    func logout() {
        apiClient.clearAccessToken()
        user = nil
        isAuthenticated = false
    }
}