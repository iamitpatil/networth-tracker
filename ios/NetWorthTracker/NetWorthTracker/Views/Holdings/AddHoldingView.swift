//
//  AddHoldingView.swift
//  NetWorthTracker
//

import SwiftUI

struct AddHoldingView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var selectedAssetType: AssetType = .equity
    @State private var symbol = ""
    @State private var name = ""
    @State private var quantity = ""
    @State private var price = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    
    var body: some View {
        NavigationView {
            Form {
                Section("Asset Type") {
                    Picker("Type", selection: $selectedAssetType) {
                        ForEach(AssetType.allCases, id: \.self) { type in
                            Text(type.displayName).tag(type)
                        }
                    }
                    .pickerStyle(MenuPickerStyle())
                }
                
                Section("Details") {
                    TextField("Symbol", text: $symbol)
                        .autocapitalization(.allCharacters)
                    
                    if selectedAssetType == .equity || selectedAssetType == .mutualFund {
                        TextField("Name (Optional)", text: $name)
                    }
                }
                
                Section("Investment") {
                    TextField("Quantity", text: $quantity)
                        .keyboardType(.decimalPad)
                    
                    TextField("Price per Unit (₹)", text: $price)
                        .keyboardType(.decimalPad)
                }
                
                if let error = errorMessage {
                    Section {
                        Text(error)
                            .foregroundColor(.red)
                    }
                }
                
                Section {
                    Button(action: addHolding) {
                        HStack {
                            Spacer()
                            if isLoading {
                                ProgressView()
                            } else {
                                Text("Add Holding")
                                    .fontWeight(.semibold)
                            }
                            Spacer()
                        }
                    }
                    .disabled(!isValid || isLoading)
                }
            }
            .navigationTitle("Add Holding")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        dismiss()
                    }
                }
            }
        }
    }
    
    var isValid: Bool {
        !symbol.isEmpty &&
        !quantity.isEmpty &&
        !price.isEmpty &&
        Double(quantity) != nil &&
        Double(price) != nil
    }
    
    func addHolding() {
        // Implementation would call API
        dismiss()
    }
}