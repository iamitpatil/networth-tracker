//
//  LoginView.swift
//  NetWorthTracker
//

import SwiftUI

struct LoginView: View {
    @EnvironmentObject var authVM: AuthViewModel
    @State private var email = ""
    @State private var password = ""
    @State private var isRegistering = false
    @State private var name = ""
    
    var body: some View {
        NavigationView {
            ZStack {
                // Background gradient
                LinearGradient(
                    colors: [Color.blue.opacity(0.3), Color.purple.opacity(0.3)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                VStack(spacing: 30) {
                    // Logo
                    VStack(spacing: 10) {
                        Image(systemName: "chart.line.uptrend.xyaxis.circle.fill")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 80, height: 80)
                            .foregroundColor(.blue)
                        
                        Text("NetWorth Tracker")
                            .font(.largeTitle)
                            .fontWeight(.bold)
                        
                        Text("Track your investments, build your wealth")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding(.top, 50)
                    
                    // Form
                    VStack(spacing: 20) {
                        if isRegistering {
                            TextField("Full Name", text: $name)
                                .textFieldStyle(RoundedBorderTextFieldStyle())
                                .textContentType(.name)
                                .autocapitalization(.words)
                        }
                        
                        TextField("Email", text: $email)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .textContentType(.emailAddress)
                            .keyboardType(.emailAddress)
                            .autocapitalization(.none)
                        
                        SecureField("Password", text: $password)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .textContentType(isRegistering ? .newPassword : .password)
                    }
                    .padding(.horizontal, 30)
                    
                    // Error message
                    if let error = authVM.errorMessage {
                        Text(error)
                            .foregroundColor(.red)
                            .font(.caption)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                    
                    // Action Button
                    Button(action: {
                        Task {
                            if isRegistering {
                                await authVM.register(name: name, email: email, password: password)
                            } else {
                                await authVM.login(email: email, password: password)
                            }
                        }
                    }) {
                        HStack {
                            if authVM.isLoading {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                Text(isRegistering ? "Create Account" : "Sign In")
                                    .fontWeight(.semibold)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(12)
                    }
                    .disabled(authVM.isLoading || email.isEmpty || password.isEmpty || (isRegistering && name.isEmpty))
                    .padding(.horizontal, 30)
                    
                    // Toggle mode
                    Button(action: {
                        isRegistering.toggle()
                        authVM.errorMessage = nil
                    }) {
                        Text(isRegistering ? "Already have an account? Sign In" : "Don't have an account? Create one")
                            .foregroundColor(.blue)
                    }
                    
                    Spacer()
                }
            }
            .navigationBarHidden(true)
        }
    }
}