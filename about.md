========================================================================
PROJECT ODO: VEHICLE EXPENSE & FLEET TRACKER
========================================================================

Odo is a modern, high-performance Android application built from scratch
to track vehicle logs, fuel entries, services, expenses, and trips. It
follows a Clean Architecture design using MVVM, Jetpack Compose, Room
Database, Hilt Dependency Injection, and Jetpack DataStore.

THIS FILE IS INTENDED AS A HANDOFF DOCUMENT. Any LLM reading this can
instantly understand the state of the project, the architectural
decisions already in place, known implementation notes, and what work
remains. Read all four sections before touching any code.

========================================================================
1. CORE TECHNICAL SPECIFICATIONS
========================================================================

  Package Name   : com.auto.odo
  App Name       : Odo
  Min SDK        : 24
  Target SDK     : 36 (Android 16)
  Compile SDK    : 36
  Language       : Kotlin (100%)
  UI Toolkit     : Jetpack Compose + Material 3
  Navigation     : androidx.navigation:navigation-compose (standard, NOT
                   navigation3)
  Database       : Room DB v2.6.1 (relational SQL, 5 tables)
  Session State  : Jetpack DataStore Preferences
  DI Framework   : Hilt (Dagger-Hilt) v2.52
  Async Engine   : Kotlin Coroutines + StateFlow (unidirectional flow)
  Build System   : Gradle with Kotlin DSL (build.gradle.kts)
  KSP            : v2.1.10-1.0.31 (used for Room & Hilt code generation)

All dependencies are declared in:
  gradle/libs.versions.toml  (version catalog)

Key dependency versions in the catalog:
  androidGradlePlugin = 8.9.1
  kotlin              = 2.1.10
  room                = 2.6.1
  hilt                = 2.52
  datastore           = 1.1.1
  navigationCompose   = 2.8.5
  androidxLifecycle   = 2.10.0
  androidxComposeBom  = 2026.03.01

========================================================================
2. DIRECTORY STRUCTURE & FILE MAP (com.auto.odo)
========================================================================

app/src/main/java/com/auto/odo/
├── MainActivity.kt
│     Entry point. Sets up edge-to-edge layout, NavHost with
│     composable routes, and a transparent glassy Floating Bottom 
│     Navigation Bar (4 tabs). The NavHost is rendered underneath the 
│     system bars to achieve a premium full-screen glass effect.
│     Uses Predictive Back (Android 16 / SDK 36) — BackHandler /
│     PredictiveBackHandler must be used; do NOT intercept old system
│     back events.
│
├── OdoApplication.kt
│     @HiltAndroidApp Application class. Required for Hilt to work.
│
├── core/
│   ├── UserSessionManager.kt
│   │     Wraps Jetpack DataStore Preferences to persist the
│   │     currently-selected vehicle ID (Long) as a reactive Flow.
│   │     Also handles UI preferences (NavBarStyle, ThemeMode,
│   │     FullScreenStatusBar, AutoHideTitleBar, ShowVehicleIcon).
│   │
│   ├── UnitConverter.kt
│   │     Pure utility object. Converts between km<->miles and
│   │     Liters<->Gallons.
│   │
│   └── UiState.kt
│         Generic sealed interface: Loading | Success<T> | Error.
│
├── data/
│   ├── AppDatabase.kt
│   │     @Database abstract class. Version 1. Declares all 5
│   │     entities and exposes DAO accessors.
│   │
│   ├── dao/
│   │   └── Daos.kt
│   │         Contains 5 DAO interfaces. Extensively uses aggregate 
│   │         queries (SUM, MIN, MAX) to calculate Analytics metrics
│   │         at the SQL layer for maximum performance.
│   │
│   ├── entity/
│   │   ├── VehicleEntity.kt
│   │   ├── FuelLogEntity.kt (odometer stored in KM, quantity in Liters)
│   │   ├── ServiceLogEntity.kt
│   │   ├── ExpenseLogEntity.kt
│   │   └── TripLogEntity.kt
│   │
│   └── repository/
│       └── RepositoriesImpl.kt
│             Concrete implementations of all 5 repository interfaces.
│
├── domain/
│   ├── repository/
│   │   └── Repositories.kt
│   │
│   └── usecase/
│       └── UseCases.kt
│             LogItem (Feed wrapper), GetRolling30DayMetricsUseCase,
│             GetRecentLogsUseCase, GetLogsFeedUseCase,
│             ValidateOdometerUseCase.
│
├── di/
│   └── AppModule.kt
│         @InstallIn(SingletonComponent). Provides DataStore, Room, DAOs.
│
└── presentation/
    ├── theme/
    │   ├── Color.kt
    │   └── Theme.kt (OdoTheme composable with Monet/AMOLED support)
    │
    ├── viewmodel/
    │   ├── DashboardViewModel.kt
    │   ├── LogsFeedViewModel.kt
    │   ├── AnalyticsViewModel.kt
    │   │     Math engine. Polls aggregate queries from DAOs and 
    │   │     calculates cost breakdowns and cost-per-distance metrics.
    │   ├── SettingsViewModel.kt
    │   ├── AddFillUpViewModel.kt
    │   │     Bidirectional engine with delta-threshold recursion limits.
    │   ├── AddServiceViewModel.kt
    │   ├── AddExpenseViewModel.kt
    │   ├── AddTripViewModel.kt
    │   └── UpdateOdometerViewModel.kt
    │
    └── ui/
        ├── DashboardScreen.kt
        │     Exposed dropdown, metrics summaries, Bezier chart, and 
        │     multi-FAB. Features dynamic Outlined vehicle icons and 
        │     a standardized AddVehicleBottomSheet.
        ├── LogsFeedScreen.kt
        ├── AnalyticsScreen.kt
        │     Displays aggregated cost breakdowns and efficiency 
        │     data wrapped in a themed Scaffold.
        ├── SettingsScreen.kt
        │     Controls UI preferences written directly to DataStore.
        ├── AddFillUpScreen.kt
        ├── AddServiceScreen.kt
        ├── AddExpenseScreen.kt
        ├── AddTripScreen.kt
        └── UpdateOdometerScreen.kt

========================================================================
3. CRITICAL ARCHITECTURAL RULES (must be respected in all future code)
========================================================================

RULE 1 — BASE UNIT STANDARD IN DATABASE
  All odometer/distance values written to Room are in KILOMETERS.
  All fuel volume values written to Room are in LITERS.
  Conversion to user-preferred units (miles, gallons) happens ONLY in
  ViewModels and UI layers via UnitConverter. Never store display units.

RULE 2 — SHARED VEHICLE SELECTION VIA DATASTORE
  The active vehicleId is the single source of truth, stored in
  DataStore by UserSessionManager. Any screen needing vehicle context
  must collect sessionManager.currentVehicleId and flatMap into its
  data query. Never store vehicleId in ViewModel local state alone.

RULE 3 — CHRONOLOGICAL ODOMETER VALIDATION
  ValidateOdometerUseCase must be called before any Fuel, Service, Trip,
  or Odometer log insert. It checks across all log tables for date-ordered
  bounds to prevent timeline regression.

RULE 4 — CANVAS CHART PERFORMANCE
  All coordinate math for the BezierChart (minX, maxX, minY, maxY,
  paddedRangeY, scaled Offsets) must live inside remember(points){...}.
  Never compute these values inline during recomposition.

RULE 5 — FLOW LIFECYCLE COLLECTION IN COMPOSE UI
  All ViewModel StateFlows collected in Composables MUST use
  .collectAsStateWithLifecycle() from the lifecycle-runtime-compose dependency.

RULE 6 — PREDICTIVE BACK (ANDROID 16 / SDK 36)
  MainActivity and all navigation destinations must handle system back
  using BackHandler{} or PredictiveBackHandler{}. Do NOT intercept WindowInsetsCompat
  or use deprecated onBackPressed() overrides.

RULE 7 — ANIMATED FAB EXPANSION
  The speed-dial multi-FAB on DashboardScreen must use AnimatedVisibility
  with slideInVertically(initialOffsetY = { it }) + fadeIn() for entry.

RULE 8 — SINGLE IMMUTABLE UI STATE PER FORM
  Every form screen must bind ALL field values inside ONE data class UiState 
  held by a single MutableStateFlow in the ViewModel.

RULE 9 — BIDIRECTIONAL FORM CALCULATIONS (DELTA CHECKS)
  When fields auto-calculate each other (e.g., Quantity * Price = Cost), 
  the ViewModel MUST check for `null` states to support keyboard backspacing,
  and MUST use a delta check (`Math.abs(oldValue - newValue) > 0.01`) before 
  emitting new state to prevent infinite ping-pong recomposition loops.

========================================================================
4. IMPLEMENTATION PHASES & WHAT TO DO NEXT
========================================================================

COMPLETED (as of latest build):
  [x] Project scaffolding (android CLI, package com.auto.odo)
  [x] All 5 Room entities + DAOs with aggregate & bound queries
  [x] AppDatabase, AppModule (Hilt)
  [x] Repository interfaces + implementations
  [x] UseCases: Metrics, RecentLogs, LogsFeed, OdometerValidation
  [x] OdoTheme (M3 Dark/Light, Monet dynamic colors, AMOLED true black)
  [x] Edge-to-Edge UI: Transparent top bars, custom LazyColumn padding (160dp)
      to clear floating navigation elements.
  [x] DashboardScreen: vehicle selector, metrics, Bezier chart, speed-dial FAB
  [x] Analytics feature: DAOs updated with aggregations, ViewModel math engine, 
      and themable UI summary screen.
  [x] LogsFeedScreen: filter chips, swipe-to-delete, empty states
  [x] AddFillUpScreen: Bi-directional cost engine with delta-loop protection.
  [x] DataStore UI Preferences: Toggles for AppTheme, NavBar style, and dynamic 
      Dashboard vehicle icons.

PHASE 5 — POLISH & HARDENING:
  [ ] Vico Charts Integration: Replace placeholder text in AnalyticsScreen with 
      rich, interactive charting components for cost-per-km trend lines.
  [ ] Add BackHandler / PredictiveBackHandler to all input form screens
      to warn users about unsaved changes.
  [ ] Room Database Migrations: add MigrationStrategy for future versions.
  [ ] Add WorkManager task for scheduled maintenance reminders.

PHASE 6 — ADVANCED FEATURES:
  [ ] Receipt OCR: ML Kit Document Scanner or CameraX + text recognition.
  [ ] Cloud Backup: Firebase Firestore or Drive API sync for multi-device access.
  [ ] Widgets: Glance API home-screen widget showing last odo reading.
  [ ] CSV/PDF Export: Export logs per vehicle to share.

PHASE 7 — TESTING:
  [ ] Local unit tests: UnitConverter, ValidateOdometerUseCase, bidirectional logic.
  [ ] Room in-memory DB tests: DAO aggregate queries, FK cascades.
  [ ] Compose UI tests: DashboardScreen empty state, LogsFeed swipe-to-delete.

========================================================================
5. KNOWN ISSUES / WATCH-OUTS FOR THE NEXT LLM
========================================================================

- Be cautious when adding heavy dependencies. Ensure versions align with 
  gradle/libs.versions.toml (e.g., androidx.documentfile MUST be 1.0.1, 
  not 1.0.2).

========================================================================
END OF ABOUT.MD — PROJECT ODO v1.0.2 (Build: 2026-06-12)
========================================================================