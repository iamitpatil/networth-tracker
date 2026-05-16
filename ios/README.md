//
//  README.md
//  NetWorthTracker iOS App
//

# Net Worth Tracker - iOS App

A native iOS app for the Indian Investment & Net-Worth Tracker built with SwiftUI.

## Features

- **Dashboard**: Real-time net worth, health score, asset allocation
- **Holdings**: Manage stocks, mutual funds, gold, FD, PPF, EPF, NPS
- **Net Worth**: Track wealth over time with detailed breakdown
- **Goals**: Financial goal tracking with progress
- **Tax**: LTCG/STCG calculations and 80C tracking
- **Liabilities**: Loan management and EMI schedules
- **AI Chat**: Portfolio insights and Q&A
- **Family Dashboard**: Multi-member net worth aggregation

## Requirements

- iOS 15.0+
- Xcode 14.0+
- Swift 5.7+
- Backend running at `http://localhost:8080`

## Architecture

```
NetWorthTracker/
├── Models/           # Data models (User, Holding, Transaction, etc.)
├── Views/           # SwiftUI views organized by feature
├── ViewModels/      # Business logic and state management
├── Services/        # API client and configuration
├── Utils/           # Helper functions and utilities
└── Resources/       # Assets, Info.plist
```

## API Integration

The app connects to your Spring Boot backend:
- Base URL: `http://localhost:8080/api/v1`
- Authentication: JWT Bearer tokens
- Real-time data with async/await

## Key Views

### Dashboard
- Net worth summary with assets vs liabilities
- Health score with visual indicator
- Asset allocation pie chart
- Top 5 holdings
- Pull-to-refresh

### Holdings
- List all investments by type
- Filter by asset category
- Swipe to delete
- View detailed P&L
- Add new holdings

### Net Worth
- Total wealth tracking
- Asset category breakdown
- Historical charts
- Liquid vs illiquid breakdown

### Goals
- Visual progress indicators
- Percentage completion
- Remaining amount calculation
- Category icons

## Usage

1. Open `NetWorthTracker.xcodeproj` in Xcode
2. Build and run on iOS Simulator or device
3. Login with your backend credentials
4. Explore portfolio data

## Backend Connection

The app expects the backend at `http://localhost:8080`. Update `APIConfig.swift` for production:

```swift
static let baseURL = "https://your-production-api.com/api/v1"
```

## Coming Soon

- [ ] Widget support
- [ ] Push notifications
- [ ] Biometric authentication
- [ ] Offline mode
- [ ] iPad optimization

## License

MIT License - Same as the main project