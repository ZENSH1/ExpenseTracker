# ExpenseTracker

A modern Android expense tracking application built with Jetpack Compose and Firebase. ExpenseTracker lets you create multiple budgets, categorize income and expenses, share trackers with other users, and export professional reports — all synced in real time across devices.

**Claude (by Anthropic) was used to accelerate the development process**, assisting with code generation,and documentation.

---

## Features

- **Multi-Tracker Support** — Create and manage multiple independent budgets (e.g., "Monthly Budget", "Vacation Fund").
- **Income & Expense Tracking** — Log transactions under categorized sources with automatic balance calculations.
- **Transaction Sources** — Group transactions into named categories like "Salary", "Groceries", or "Rent", each with running totals.
- **Tracker Sharing** — Share any tracker with other users by their user ID for collaborative budget management.
- **Real-Time Sync** — All data is synchronized in real time through Firebase Firestore, so changes appear instantly across devices.
- **Export Reports** — Generate polished CSV (Excel-compatible) and PDF reports with summaries, source breakdowns, and full transaction history.
- **Google Authentication** — Secure sign-in via Google using the modern AndroidX Credential Manager API.
- **Account Management** — View profile details, sign out, or permanently delete your account and all associated data.
- **Analytics & Crash Reporting** — Integrated Firebase Analytics and Crashlytics for usage insights and stability monitoring.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose (Material 3) |
| Architecture | Clean Architecture — MVVM + Use Cases |
| Backend | Firebase (Firestore, Auth, Analytics, Crashlytics, Performance, Remote Config) |
| Dependency Injection | Koin |
| Authentication | Google Sign-In via AndroidX Credentials API |
| Navigation | AndroidX Navigation 3 with shared element transitions |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 |

---

## Project Structure

```
app/src/main/java/com/xs/expensetracker/
│
├── ExpenseApp.kt                  # Application class — initializes Koin DI
├── MainActivity.kt                # Single-activity entry point
│
├── data/
│   ├── enums/
│   │   └── TransactionType.kt     # INCOME / EXPENSE enum
│   └── models/
│       ├── Tracker.kt             # Budget tracker (name, owner, grandTotal, sharedWith)
│       ├── TransactionSource.kt   # Income/expense category with running total
│       └── TransactionReceipt.kt  # Individual transaction record
│
├── repo/
│   ├── AuthRepository.kt          # Auth interface (observe state, sign in/out, delete)
│   ├── AuthRepositoryImpl.kt      # Firebase Auth implementation
│   ├── ExpenseTrackerRepository.kt    # Data interface (trackers, sources, receipts)
│   └── ExpenseTrackerRepositoryImpl.kt # Firestore implementation with atomic transactions
│
├── usecases/
│   ├── AuthUseCase.kt             # Authentication business logic
│   ├── TrackerUseCase.kt          # Tracker CRUD with validation and logging
│   ├── SourceUseCase.kt           # Source CRUD with validation and logging
│   └── ReceiptUseCase.kt          # Receipt CRUD with validation and logging
│
├── di/
│   ├── appModules.kt              # Aggregates all Koin modules
│   ├── firebaseModule.kt          # Firebase and GoogleAuthManager singletons
│   ├── repositoryModule.kt        # Repository bindings
│   ├── viewModelModule.kt         # ViewModel definitions
│   ├── useCasesModule.kt          # Use case singletons
│   └── loggerModule.kt            # AppLogger singleton
│
├── ui/
│   ├── components/
│   │   ├── AppNavigator.kt        # Navigation host with shared transitions
│   │   ├── modals/
│   │   │   ├── AddReceiptModal.kt # Receipt create/edit bottom sheet
│   │   │   └── AddSourceModal.kt  # Source create/edit bottom sheet
│   │   └── reusables/
│   │       ├── ActionButton.kt    # Styled action button
│   │       ├── GoogleGLogo.kt     # Google "G" logo drawable
│   │       ├── InfoRow.kt         # Label-value row for profile/details
│   │       ├── ModalTextField.kt  # Themed text field for modals
│   │       ├── NavCard.kt         # Navigation card for dashboard
│   │       ├── QuickActionButton.kt # Quick action shortcut button
│   │       ├── ReceiptCard.kt     # Transaction list item card
│   │       ├── ReceiptField.kt    # Receipt detail field
│   │       ├── ReceiptFormModal.kt # Full receipt form modal
│   │       └── SectionLabel.kt    # Section header label
│   │
│   ├── screens/
│   │   ├── SplashScreen.kt        # Auth state check → route to login or home
│   │   ├── AuthScreen.kt          # Google sign-in with animated gradient UI
│   │   ├── TrackerSelectionScreen.kt # List/create/manage trackers
│   │   ├── HomeScreen.kt          # Dashboard — balance, quick actions, export
│   │   ├── ProfileScreen.kt       # User info, sign out, delete account
│   │   ├── SourcesScreen.kt       # Browse/filter income & expense sources
│   │   └── ReceiptsScreen.kt      # Browse/filter transaction receipts
│   │
│   ├── viewmodels/
│   │   ├── AuthViewModel.kt       # Auth state management
│   │   └── TransactionsViewModel.kt # Tracker, source, and receipt state + export
│   │
│   └── theme/
│       ├── Color.kt               # Dark theme color palette
│       ├── Theme.kt               # Material 3 theme configuration
│       └── Type.kt                # Typography definitions
│
└── utils/
    ├── AppLogger.kt               # Centralized logging (Analytics + Crashlytics)
    ├── ExportManager.kt           # CSV and PDF report generation
    ├── FirebaseConst.kt           # Firestore collection/field name constants
    ├── GoogleAuthManager.kt       # Credential Manager sign-in helper
    ├── PreviewScreens.kt          # Compose preview utilities
    ├── RemoteConfigManager.kt     # Firebase Remote Config wrapper
    ├── SharedKeys.kt              # Shared element transition keys
    ├── Utils.kt                   # General utility functions
    ├── events/
    │   ├── TrackerUiEvent.kt      # Loading / Success / Error events for trackers
    │   └── TransactionUiEvent.kt  # Loading / Success / Error events for transactions
    └── sealed/
        └── AppRoute.kt            # Navigation route definitions
```

---

## Firestore Data Model

```
trackers/{trackerId}
├── name: String
├── ownerId: String
├── grandTotal: Double          # net balance (income − expenses)
├── sharedWith: List<String>    # user IDs with access
├── createdAt: Timestamp
│
└── sources/{sourceId}
    ├── name: String
    ├── type: "INCOME" | "EXPENSE"
    ├── totalAmount: Double     # sum of receipts in this source
    ├── createdAt: Timestamp
    │
    └── receipts/{receiptId}
        ├── name: String
        ├── description: String
        ├── amount: Double
        ├── type: "INCOME" | "EXPENSE"
        ├── date: String
        └── createdAt: Timestamp
```

All financial writes (add, update, delete receipt) use **Firestore transactions** to atomically update the source total and tracker grand total, ensuring data consistency.

---

## Navigation Flow

```
Splash → Auth Check
           ├── Not signed in → AuthScreen (Google Sign-In)
           └── Signed in → TrackerSelectionScreen
                              └── Select tracker → HomeScreen
                                                     ├── SourcesScreen
                                                     ├── ReceiptsScreen
                                                     └── ProfileScreen
```

Screen transitions use 350ms slide + fade animations with shared element transitions for a polished feel.

---

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. Add your own `google-services.json` from the Firebase Console to `app/`.
4. Set up a Firebase project with Authentication (Google provider), Firestore, Analytics, Crashlytics, and Performance Monitoring enabled.
5. Update the `GOOGLE_WEB_CLIENT_ID` in the app constants with your OAuth 2.0 Web Client ID.
6. Build and run on a device or emulator running Android 7.0+.

---
