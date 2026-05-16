//
//  GoalsView.swift
//  NetWorthTracker
//

import SwiftUI

struct GoalsView: View {
    @StateObject private var viewModel = GoalsViewModel()
    @State private var showingAddGoal = false
    
    var body: some View {
        NavigationView {
            List {
                ForEach(viewModel.goals) { goal in
                    GoalRow(goal: goal)
                }
            }
            .listStyle(InsetGroupedListStyle())
            .navigationTitle("Financial Goals")
            .toolbar {
                Button(action: { showingAddGoal = true }) {
                    Image(systemName: "plus.circle.fill")
                }
            }
            .sheet(isPresented: $showingAddGoal) {
                AddGoalView()
            }
        }
    }
}

struct GoalRow: View {
    let goal: Goal
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: goal.goalType.icon)
                    .font(.title2)
                    .foregroundColor(.blue)
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(goal.name)
                        .font(.headline)
                    
                    Text(goal.goalType.displayName)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                if goal.isCompleted {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundColor(.green)
                }
            }
            
            // Progress
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(formatCurrency(goal.currentAmount))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                    
                    Spacer()
                    
                    Text(formatCurrency(goal.targetAmount))
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.gray.opacity(0.2))
                            .cornerRadius(4)
                        
                        Rectangle()
                            .fill(goal.isCompleted ? Color.green : Color.blue)
                            .frame(width: geometry.size.width * CGFloat(goal.progressPercentage / 100))
                            .cornerRadius(4)
                            .animation(.easeInOut, value: goal.progressPercentage)
                    }
                }
                .frame(height: 8)
                
                HStack {
                    Text("\(Int(goal.progressPercentage))% achieved")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Spacer()
                    
                    if goal.remainingAmount > 0 {
                        Text("\(formatCurrency(goal.remainingAmount)) remaining")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
        }
        .padding(.vertical, 8)
    }
}

struct AddGoalView: View {
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationView {
            Text("Add Goal Form")
                .navigationTitle("New Goal")
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Cancel") { dismiss() }
                    }
                }
        }
    }
}

@MainActor
class GoalsViewModel: ObservableObject {
    @Published var goals: [Goal] = []
    
    init() {
        // Sample data for preview
        goals = [
            Goal(
                id: "1",
                name: "Emergency Fund",
                targetAmount: 500000,
                currentAmount: 300000,
                targetDate: "2025-12-31",
                goalType: .emergency,
                riskProfile: .conservative,
                holdings: []
            ),
            Goal(
                id: "2",
                name: "Dream Home",
                targetAmount: 5000000,
                currentAmount: 1500000,
                targetDate: "2028-06-30",
                goalType: .house,
                riskProfile: .moderate,
                holdings: []
            )
        ]
    }
}