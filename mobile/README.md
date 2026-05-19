# NetWorth Tracker - Mobile App

Cross-platform mobile companion for NetWorth Tracker, built with Flutter. Runs on iOS, Android, and Web from a single codebase.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Framework | Flutter 3, Dart |
| State Management | Provider (ChangeNotifier) |
| HTTP Client | http package |
| Local Storage | shared_preferences |
| Charts | fl_chart |
| Fonts | google_fonts (Inter) |

## Project Structure

```
mobile/lib/
├── main.dart                     # App entry point
├── core/
│   ├── constants/
│   │   ├── app_colors.dart       # Centralized color palette
│   │   └── app_config.dart       # API URL, storage keys
│   ├── services/
│   │   └── api_client.dart       # HTTP client with JWT + refresh
│   ├── theme/
│   │   └── app_theme.dart        # Material theme configuration
│   └── utils/
│       ├── formatters.dart       # Currency (INR), percentage, date, file size
│       └── validators.dart       # Email, password, number validation
├── data/
│   ├── models/
│   │   └── app_models.dart       # Holding, NetWorthData, Goal, etc.
│   └── repositories/
├── providers/
│   └── data_provider.dart        # ChangeNotifier for global state
├── features/                     # 19 screens organized by feature
│   ├── auth/                     # Login, Register
│   ├── dashboard/                # Dashboard with net worth, health score
│   ├── portfolio/                # Holdings, Transactions, Analytics
│   ├── accounts/                 # Bank Accounts, Demat Accounts, Salaries
│   ├── networth/                 # Net Worth, Goals, Liabilities, Tax
│   ├── personal/                 # Documents, Family, Import
│   ├── ai_chat/                  # AI Chat
│   ├── profile/                  # Profile & Settings
│   └── home/                     # Main navigation hub
└── widgets/
    ├── charts/
    │   ├── asset_allocation_chart.dart   # Pie chart for asset breakdown
    │   └── holding_price_chart.dart      # Interactive price chart
    ├── common/
    │   ├── error_view.dart
    │   ├── loading_view.dart
    │   ├── empty_state.dart
    │   └── stat_card.dart
    └── dialogs/
        ├── add_holding_dialog.dart
        ├── add_transaction_dialog.dart
        └── add_goal_dialog.dart
```

## Features

- **Dashboard** — Net worth summary, health score (A+ to D), asset allocation pie chart, top holdings
- **Holdings** — All asset types (Equity, MF, Gold, FD, PPF, EPF, NPS, Bonds, Crypto, Real Estate), filter tabs, P&L tracking
- **Net Worth** — Total wealth, asset category breakdown, historical charts
- **Goals** — Financial goal tracking with progress bars, target vs current
- **Transactions** — Buy/sell/SIP/dividend history
- **Analytics** — XIRR, CAGR, allocation breakdown
- **Tax** — LTCG/STCG calculations, 80C tracking
- **Liabilities** — Loan management, EMI schedules
- **Bank Accounts** — Savings, Current, FD, NRE, NRO
- **Salaries** — Income tracking with component breakdown
- **AI Chat** — Portfolio-aware Q&A via local LLM
- **Family** — Multi-member dashboard
- **Documents** — File upload and viewer
- **Import** — CSV/PDF import
- **Profile** — User settings

## Quick Start

### Prerequisites

- Flutter SDK 3.0+ ([Install guide](https://docs.flutter.dev/get-started/install))
- Backend running on `http://localhost:8080`

### Run

```bash
cd mobile

# Install dependencies
flutter pub get

# Run on connected device or emulator
flutter run

# Run on Chrome (web)
flutter run -d chrome    # Serves on port 8081

# Run on iOS Simulator
flutter run -d ios

# Run on Android Emulator
flutter run -d android
```

### Backend Connection

The app connects to the Spring Boot backend at `http://localhost:8080/api/v1` by default. Update `lib/core/constants/app_config.dart` for production:

```dart
static const String baseUrl = 'https://your-api.example.com/api/v1';
```

## Build for Production

```bash
# Android APK
flutter build apk --release
# Output: build/app/outputs/flutter-apk/app-release.apk

# Android App Bundle (Play Store)
flutter build appbundle --release

# iOS (requires Xcode + Apple Developer account)
flutter build ios --release

# Web
flutter build web --release
# Output: build/web/
```

## Architecture

```
Flutter App
    │
    ├── Provider (State Management)
    │   └── DataProvider (ChangeNotifier)
    │       ├── Holdings, Goals, Liabilities
    │       ├── Net Worth, Bank Accounts
    │       └── Auth state (JWT tokens)
    │
    ├── API Client (HTTP + JWT)
    │   ├── Auto-attaches Bearer token
    │   ├── Token refresh on 401
    │   └── Base URL from AppConfig
    │
    └── Feature Screens (19 total)
        └── Each screen reads from Provider
            and calls API Client for data
```

## License

MIT — Same as the main project.
