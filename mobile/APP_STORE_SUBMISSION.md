# App Store Submission Guide

## Net Worth Tracker - Flutter Mobile App

---

## 📱 App Information

### Basic Information
- **App Name**: Net Worth Tracker
- **Subtitle**: Track investments & build wealth
- **Primary Category**: Finance
- **Secondary Category**: Productivity
- **Bundle ID**: com.networth.tracker
- **SKU**: networth-tracker-001

### Description
```
Track your investments, manage your portfolio, and build your wealth with Net Worth Tracker - the ultimate personal finance app for Indian investors.

KEY FEATURES:

📊 COMPREHENSIVE PORTFOLIO TRACKING
- Monitor all your investments in one place
- Track Stocks, Mutual Funds, Gold, FD, PPF, EPF, NPS, Real Estate, Crypto, and more
- Real-time price updates from NSE/BSE
- View gains/losses with beautiful charts

💰 NET WORTH DASHBOARD
- See your total net worth at a glance
- Asset vs Liability breakdown
- Financial health score with personalized insights
- Historical net worth tracking

🎯 FINANCIAL GOALS
- Set and track financial goals
- Monitor progress with visual indicators
- Plan for retirement, home buying, education, and more

💡 AI-POWERED INSIGHTS
- Chat with your AI financial assistant
- Get personalized investment recommendations
- Tax optimization strategies
- Portfolio analysis and rebalancing suggestions

💸 SMART TAX PLANNING
- Track LTCG/STCG calculations
- Tax harvesting opportunities
- Section 80C deduction tracking
- Indian tax rules compliance

🏦 LOAN & LIABILITY MANAGEMENT
- Track all your loans and EMIs
- EMI schedule and payment reminders
- Prepayment calculator
- Debt-to-income ratio monitoring

🔒 SECURE & PRIVATE
- Biometric authentication
- Secure JWT token-based login
- Local data encryption
- Your financial data stays private

WHY NET WORTH TRACKER?

✓ Designed specifically for Indian investors
✓ Supports all major Indian asset classes
✓ Real-time market data integration
✓ Beautiful, intuitive interface
✓ 100% free with no ads

Start building your wealth today with Net Worth Tracker!

---

Built for Indian investors who want to take control of their financial future.
```

### Keywords
net worth, portfolio tracker, investment tracker, stock market, mutual funds, Indian finance, wealth management, personal finance, tax planning, financial goals, EMI calculator, SIP tracker

---

## 🎨 Screenshots Required

### iPhone (6.5" Display - iPhone 14 Pro Max)
1. **Dashboard** - Net worth summary with charts
2. **Holdings** - Investment list with P&L
3. **Asset Allocation** - Pie chart breakdown
4. **Goals** - Financial goals tracking
5. **AI Chat** - Assistant conversation

### iPhone (5.5" Display - iPhone 8 Plus)
1. Dashboard
2. Holdings
3. Tax Planning
4. Goals
5. Liabilities

### iPad (12.9" Display - iPad Pro)
1. Dashboard (landscape)
2. Holdings split view
3. Charts & Analytics
4. Goals overview
5. Tax details

### Android (Phone)
Same as iPhone screenshots

### Android (Tablet)
Same as iPad screenshots

---

## 📐 Screenshot Specifications

### iOS
- **6.5" iPhone**: 1290 x 2796 pixels
- **5.5" iPhone**: 1242 x 2208 pixels
- **12.9" iPad**: 2048 x 2732 pixels

### Android
- **Phone**: 1080 x 1920 pixels (9:16)
- **Tablet**: 2048 x 2732 pixels (4:3)

---

## 🎨 App Icon

### iOS
- **1024x1024**: App Store
- **180x180**: iPhone
- **167x167**: iPad Pro
- **152x152**: iPad
- **120x120**: iPhone (old)

### Android
- **512x512**: Google Play
- **192x192**: xxxhdpi
- **144x144**: xxhdpi
- **96x96**: xhdpi
- **72x72**: hdpi
- **48x48**: mdpi

---

## 🔐 App Privacy Details

### Data Collection
- **Purchases**: No
- **Location**: No
- **Contact Info**: Email (Optional)
- **Contacts**: No
- **User Content**: Photos/Videos (Optional for documents)
- **Identifiers**: Device ID
- **Usage Data**: Product Interaction
- **Diagnostics**: Crash Data, Performance Data

### Data Usage
- **Third-party advertising**: No
- **Developer's advertising or marketing**: No
- **Analytics**: Yes (Crash reports)
- **Product personalization**: Yes
- **App functionality**: Yes
- **Other purposes**: No

### Data Linked to You
- Email address
- Device ID
- Usage data

### Data Not Linked to You
- Crash logs
- Performance data

---

## 🚀 Build & Upload

### iOS App Store

#### 1. Archive in Xcode
```bash
cd mobile/flutter_networth
flutter build ios --release
```

#### 2. Open Xcode
```bash
open ios/Runner.xcworkspace
```

#### 3. Configure Signing
- Select Runner target
- Go to Signing & Capabilities
- Select your team
- Set Bundle Identifier

#### 4. Archive & Upload
- Product → Archive
- Distribute App → App Store Connect
- Upload

### Android Play Store

#### 1. Build Release AAB
```bash
cd mobile/flutter_networth
flutter build appbundle --release
```

#### 2. Sign the App
```bash
# Key already created
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore android/app/key.jks \
  build/app/outputs/bundle/release/app-release.aab \
  key_alias
```

#### 3. Upload to Play Console
- Go to [Google Play Console](https://play.google.com/console)
- Create new release
- Upload AAB file
- Fill in store listing
- Submit for review

---

## ✅ Pre-Launch Checklist

### iOS
- [ ] App Store Connect account ($99/year)
- [ ] App privacy details filled
- [ ] Screenshots for all devices
- [ ] App icon all sizes
- [ ] App description & keywords
- [ ] Support URL
- [ ] Marketing URL (optional)
- [ ] App binary uploaded
- [ ] Beta testing with TestFlight
- [ ] App Review guidelines checked

### Android
- [ ] Google Play Console account ($25 one-time)
- [ ] Privacy policy URL
- [ ] App category selected
- [ ] Content rating completed
- [ ] Target audience defined
- [ ] Store listing complete
- [ ] Screenshots uploaded
- [ ] Feature graphic (1024x500)
- [ ] App binary uploaded
- [ ] Internal testing track

---

## 📋 App Review Guidelines

### iOS App Store
1. **Performance**: App must be stable, no crashes
2. **Legal**: All required licenses
3. **Business**: No misleading pricing
4. **Design**: Follow Human Interface Guidelines
5. **Security**: Protect user data
6. **Content**: Appropriate for all ages

### Google Play Store
1. **Policy Compliance**: Follow Google Play policies
2. **Content Ratings**: Accurate rating
3. **Intellectual Property**: All content owned/licensed
4. **Privacy**: Clear privacy policy
5. **Ads**: No deceptive ads
6. **Security**: Secure data handling

---

## 🔔 Push Notifications Setup

### iOS (APNs)
1. Enable Push Notifications in Xcode
2. Create APNs Auth Key in Apple Developer Portal
3. Upload to Firebase Console
4. Add Firebase config to app

### Android (FCM)
1. Add Firebase to project
2. Download `google-services.json`
3. Place in `android/app/`
4. Configure in app

---

## 📱 Widget Configuration

### iOS Widget
- **Small**: 1 holding or net worth summary
- **Medium**: Top 3 holdings
- **Large**: Asset allocation chart

### Android Widget
- **2x1**: Net worth summary
- **4x1**: Top holdings scroll
- **4x2**: Asset breakdown

---

## 🌐 Backend Configuration

### Production API URL
Update `lib/services/api_config.dart`:
```dart
static const String baseUrl = 'https://your-api-domain.com/api/v1';
```

### Environment Variables
```dart
// Development
static const bool isProduction = false;
static const String apiBaseUrl = 'http://localhost:8080/api/v1';

// Production  
static const bool isProduction = true;
static const String apiBaseUrl = 'https://api.networth.app/api/v1';
```

---

## 🎨 Branding Assets

### Colors
- **Primary**: #3B82F6 (Blue)
- **Secondary**: #10B981 (Green)
- **Accent**: #F59E0B (Amber)
- **Background**: #FFFFFF (Light) / #0F172A (Dark)

### Fonts
- **iOS**: SF Pro Display / SF Pro Text
- **Android**: Roboto

---

## 📞 Support Information

### Support Email
support@networth.app

### Support URL
https://networth.app/support

### Marketing URL
https://networth.app

### Privacy Policy URL
https://networth.app/privacy

---

## 🎯 Post-Launch Marketing

### Social Media
- Twitter: @NetWorthApp
- Instagram: @networth.app
- LinkedIn: Net Worth Tracker

### App Store Optimization (ASO)
- Monitor keyword rankings
- A/B test screenshots
- Respond to reviews
- Update regularly

### Analytics
- Track downloads
- Monitor retention
- Analyze crashes
- User engagement metrics

---

Ready to launch your Net Worth Tracker app! 🚀📱