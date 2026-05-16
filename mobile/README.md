# Mobile Apps - Net Worth Tracker

This directory contains cross-platform mobile apps for the Net Worth Tracker.

## 📱 Flutter App (Recommended)

**Location:** `mobile/flutter_networth/`

### Why Flutter?
- ✅ **Single codebase** for iOS & Android
- ✅ **Faster development** with hot reload
- ✅ **Beautiful UI** with Material Design
- ✅ **Better performance** than React Native
- ✅ **Easy integration** with backend
- ✅ **Good documentation** and community

### Features Included:
- **Full REST API integration** with JWT auth
- **Dashboard**: Net worth, health score, asset allocation charts
- **Holdings**: Manage all asset types (Equity, MF, Gold, FD, PPF, EPF, NPS)
- **Net Worth**: Asset breakdown with visual charts
- **Goals**: Financial goal tracking
- **State Management**: Flutter BLoC pattern
- **Charts**: FL Chart library for beautiful visualizations
- **Currency**: INR (₹) formatting throughout

### Tech Stack:
```yaml
flutter: ^3.0.0
flutter_bloc: ^8.1.3      # State management
dio: ^5.4.0               # HTTP client
fl_chart: ^0.66.0         # Charts
retrofit: ^4.0.3          # Type-safe APIs
hive: ^2.2.3              # Local storage
```

### How to Run:
```bash
cd mobile/flutter_networth

# Install dependencies
flutter pub get

# Generate API code
flutter pub run build_runner build

# Run on iOS Simulator
flutter run

# Run on Android Emulator  
flutter run

# Build release APK
flutter build apk

# Build iOS release
flutter build ios
```

### API Integration:
```dart
// Auto-generated from Retrofit annotations
@GET('/net-worth/breakdown')
Future<NetWorthBreakdown> getNetWorthBreakdown();
```

---

## 🍎 iOS App (SwiftUI)

**Location:** `ios/NetWorthTracker/`

### Features:
- **Native iOS design** with SwiftUI
- **iOS 15+ support**
- **Same features** as Flutter version
- **Charts**: SwiftUI Charts (iOS 16+)
- **Backend**: Connects to Spring Boot API

### How to Open:
```bash
cd ios/NetWorthTracker
open NetWorthTracker.xcodeproj
```

---

## 🎯 Recommendation: Use Flutter!

**Flutter is the better choice because:**

1. **Cross-Platform**: One codebase = iOS + Android
2. **Fast Development**: Hot reload, single language (Dart)
3. **Native Performance**: Compiled to native ARM code
4. **Beautiful UI**: Rich widget library, consistent across platforms
5. **Easy Maintenance**: Update once, deploy everywhere
6. **Future-Proof**: Google's official framework, growing fast
7. **Cost-Effective**: Develop for both platforms with one team

### Comparison:

| Feature | Flutter | Native iOS | React Native |
|---------|---------|-----------|--------------|
| Code Sharing | 100% | 0% | ~70% |
| Performance | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| Development Speed | ⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ |
| UI Quality | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| Learning Curve | Medium | High | Medium |
| Community | Growing | Mature | Mature |

---

## 🚀 Quick Start with Flutter

1. **Install Flutter:**
   ```bash
   https://docs.flutter.dev/get-started/install
   ```

2. **Setup Project:**
   ```bash
   cd mobile/flutter_networth
   flutter pub get
   ```

3. **Configure Backend:**
   - Update `lib/services/api_service.dart` with your backend URL
   - Default: `http://localhost:8080/api/v1`

4. **Generate API Code:**
   ```bash
   flutter pub run build_runner build --delete-conflicting-outputs
   ```

5. **Run App:**
   ```bash
   flutter run
   ```

---

## 📊 App Features

Both apps include:

### Authentication
- Login/Register screens
- JWT token management
- Auto-login with saved token
- Secure logout

### Dashboard
- Total net worth card
- Assets vs liabilities
- Health score with grade
- Asset allocation pie chart
- Top 5 holdings list
- Pull-to-refresh

### Holdings
- All investments list
- Filter by asset type
- P&L tracking (realized & unrealized)
- Add new holdings
- Delete holdings
- View holding details

### Net Worth
- Total wealth tracking
- Asset category breakdown
- Visual progress bars
- Historical charts

### Goals
- Financial goals tracking
- Progress indicators
- Target vs current amount
- Visual progress bars

### More Section
- Tax planning (LTCG/STCG)
- Loans & liabilities
- Bank accounts
- AI Chat
- Import data
- Family dashboard
- Settings

---

## 🔌 Backend Connection

All mobile apps connect to your existing Spring Boot backend:

```
Mobile App → HTTP/REST → Spring Boot Backend
                ↓
         JWT Authentication
                ↓
     PostgreSQL + Redis
```

**Default API URL:** `http://localhost:8080/api/v1`

**For Production:** Update to your deployed backend URL.

---

## 📱 Build for Production

### Android APK:
```bash
cd mobile/flutter_networth
flutter build apk --release
# Output: build/app/outputs/flutter-apk/app-release.apk
```

### iOS App Store:
```bash
cd mobile/flutter_networth
flutter build ios --release
# Then use Xcode to upload to App Store
```

---

## 🎨 Screenshots (Coming Soon)

- Dashboard with charts
- Holdings list with P&L
- Net worth breakdown
- Goals progress

---

## 🆘 Support

For Flutter issues:
- [Flutter Documentation](https://docs.flutter.dev)
- [Flutter Community](https://flutter.dev/community)

For iOS issues:
- [Apple Developer](https://developer.apple.com)

---

**Recommendation: Go with Flutter!** 🚀

It's the best choice for your app - beautiful, fast, and works on both platforms with one codebase.