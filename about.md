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
remains. Read all five sections before touching any code.

========================================================================

1. # CORE TECHNICAL SPECIFICATIONS

Package Name : com.auto.odo
App Name : Odo
Min SDK : 24
Target SDK : 36 (Android 16)
Compile SDK : 36
Language : Kotlin (100%)
UI Toolkit : Jetpack Compose + Material 3
Navigation : androidx.navigation:navigation-compose (standard, NOT
navigation3)
Database : Room DB v2.6.1 (relational SQL, 5 tables)
Session State : Jetpack DataStore Preferences
DI Framework : Hilt (Dagger-Hilt) v2.52
Async Engine : Kotlin Coroutines + StateFlow (unidirectional flow)
Build System : Gradle with Kotlin DSL (build.gradle.kts)
KSP : v2.1.10-1.0.31 (used for Room & Hilt code generation)

All dependencies are declared in:
gradle/libs.versions.toml (version catalog)

Key dependency versions in the catalog:
androidGradlePlugin = 8.9.1
kotlin = 2.1.10
room = 2.6.1
hilt = 2.52
datastore = 1.1.1
navigationCompose = 2.8.5
androidxLifecycle = 2.10.0
androidxComposeBom = 2026.03.01

======================================================================== 2. DIRECTORY STRUCTURE & FILE MAP (com.auto.odo)
========================================================================

app/src/main/java/com/auto/odo/
├── MainActivity.kt (383 lines)
│ Entry point. Sets up edge-to-edge layout, NavHost with
│ composable routes, and a transparent glassy Floating Bottom
│ Navigation Bar (4 tabs). The NavHost is rendered underneath the
│ system bars to achieve a premium full-screen glass effect.
│ Uses Predictive Back (Android 16 / SDK 36).
│ IN PROGRESS: Add\*Screen routes are being extended with an
│ optional `editId` query arg (e.g. "add_fillup?editId={editId}")
│ so LogsFeedScreen's pencil icon can deep-link into edit mode.
│ See Section 5.
│
├── OdoApplication.kt (7 lines)
│ @HiltAndroidApp Application class.
│
├── core/
│ ├── UserSessionManager.kt (95 lines) — active vehicleId + UI prefs
│ ├── UnitConverter.kt (20 lines) — km<->miles, Liters<->Gallons
│ ├── UiState.kt (7 lines) — Loading | Success<T> | Error
│ ├── CsvManager.kt (576 lines) — CSV import/export engine
│ └── BackupWorker.kt (74 lines) — WorkManager-based backup job
│
├── data/
│ ├── AppDatabase.kt (44 lines) — Version 1, 5 entities
│ ├── dao/Daos.kt (208 lines) — 5 DAO interfaces, aggregate queries.
│ │ NEEDS: getById()/update() pairs per log type for edit
│ │ support (see Section 5).
│ ├── entity/
│ │ ├── VehicleEntity.kt (17 lines)
│ │ ├── FuelLogEntity.kt (32 lines) — odometer in KM, qty in Liters
│ │ ├── ServiceLogEntity.kt (28 lines)
│ │ ├── ExpenseLogEntity.kt (27 lines)
│ │ └── TripLogEntity.kt (28 lines)
│ └── repository/RepositoriesImpl.kt (76 lines)
│
├── domain/
│ ├── BackupFileInfo.kt (8 lines)
│ ├── repository/Repositories.kt (60 lines) — NEEDS getById/update
│ │ method signatures added per Section 5.
│ └── usecase/
│ ├── UseCases.kt (227 lines) — LogItem, metrics, feed,
│ │ ValidateOdometerUseCase. NEEDS an `excludeLogId` param
│ │ so editing a log doesn't fail chronology validation
│ │ against its own prior value.
│ └── ImportExportUseCase.kt (270 lines)
│
├── di/
│ ├── AppModule.kt (76 lines) — DataStore, Room, DAOs
│ └── NetworkModule.kt (26 lines)
│
└── presentation/
├── theme/Color.kt (22 lines), Theme.kt (88 lines)
│
├── viewmodel/
│ ├── DashboardViewModel.kt (153 lines)
│ ├── LogsFeedViewModel.kt (143 lines)
│ ├── AnalyticsViewModel.kt (304 lines)
│ ├── SettingsViewModel.kt (339 lines)
│ ├── MainViewModel.kt (41 lines)
│ ├── BackupViewModel.kt (716 lines)
│ ├── AddFillUpViewModel.kt (285 lines)
│ │ IN PROGRESS: gaining SavedStateHandle-driven edit mode
│ │ (load-by-id, update-instead-of-insert on save).
│ ├── AddServiceViewModel.kt (166 lines) — same edit-mode work
│ │ pending, mirror AddFillUpViewModel's pattern.
│ ├── AddExpenseViewModel.kt (96 lines) — same, pending.
│ ├── AddTripViewModel.kt (238 lines) — same, pending.
│ └── UpdateOdometerViewModel.kt (148 lines)
│
└── ui/
├── DashboardScreen.kt (832 lines)
├── LogsFeedScreen.kt (677 lines)
│ Contains LogDetailsFullScreen with Edit/Delete actions.
│ `onEdit` now forwards the tapped LogItem up via
│ `onNavigateToEdit` (previously a no-op TODO stub —
│ this was the root cause of the "pencil icon does
│ nothing" bug, fixed 2026-08-12).
├── AnalyticsScreen.kt (372 lines)
├── SettingsScreen.kt (1148 lines)
├── BackupScreen.kt (342 lines)
├── AddFillUpScreen.kt (365 lines) — title now reflects
│ Create vs Edit mode via `uiState.isEditMode`.
├── AddServiceScreen.kt (295 lines)
├── AddExpenseScreen.kt (280 lines)
├── AddTripScreen.kt (294 lines)
├── UpdateOdometerScreen.kt (179 lines)
└── VehicleIcons.kt (77 lines)

======================================================================== 3. CRITICAL ARCHITECTURAL RULES (must be respected in all future code)
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
or Odometer log insert OR UPDATE. When editing an existing log, pass
its own id so the bound query excludes the log's own prior value from
the chronology check — otherwise every edit will fail validation
against itself.

RULE 4 — CANVAS CHART PERFORMANCE
All coordinate math for the BezierChart (minX, maxX, minY, maxY,
paddedRangeY, scaled Offsets) must live inside remember(points){...}.
Never compute these values inline during recomposition.

RULE 5 — FLOW LIFECYCLE COLLECTION IN COMPOSE UI
All ViewModel StateFlows collected in Composables MUST use
.collectAsStateWithLifecycle().

RULE 6 — PREDICTIVE BACK (ANDROID 16 / SDK 36)
MainActivity and all navigation destinations must handle system back
using BackHandler{} or PredictiveBackHandler{}. Do NOT intercept
WindowInsetsCompat or use deprecated onBackPressed() overrides.

RULE 7 — ANIMATED FAB EXPANSION
The speed-dial multi-FAB on DashboardScreen must use AnimatedVisibility
with slideInVertically(initialOffsetY = { it }) + fadeIn() for entry.

RULE 8 — SINGLE IMMUTABLE UI STATE PER FORM
Every form screen must bind ALL field values inside ONE data class
UiState held by a single MutableStateFlow in the ViewModel.

RULE 9 — BIDIRECTIONAL FORM CALCULATIONS (DELTA CHECKS)
When fields auto-calculate each other (e.g., Quantity \* Price = Cost),
the ViewModel MUST check for `null` states to support keyboard
backspacing, and MUST use a delta check (`Math.abs(oldValue - newValue)

> 0.01`) before emitting new state to prevent infinite ping-pong
> recomposition loops.

RULE 10 — EDIT MODE IS SHARED-SCREEN, NOT A SEPARATE SCREEN
Editing an existing log reuses the same Add*Screen/Add*ViewModel pair
used for creation. The ViewModel reads an optional `editId` from
SavedStateHandle; if present, it loads the existing entity, converts
it to display units, seeds the form's UiState, and on save calls the
repository's `update` method (with the original id) instead of
`insert`. Do NOT build separate Edit*Screen/Edit*ViewModel files —
this duplicates the bidirectional calc logic (Rule 9) and the
validation logic (Rule 3) and will drift out of sync.

======================================================================== 4. IMPLEMENTATION PHASES & WHAT TO DO NEXT
========================================================================

COMPLETED (as of latest build):
[x] Project scaffolding, all 5 Room entities + DAOs
[x] AppDatabase, AppModule (Hilt), NetworkModule
[x] Repository interfaces + implementations
[x] UseCases: Metrics, RecentLogs, LogsFeed, OdometerValidation
[x] OdoTheme (M3 Dark/Light, Monet dynamic colors, AMOLED true black)
[x] Edge-to-Edge UI, floating nav bar, predictive back
[x] DashboardScreen, AnalyticsScreen, LogsFeedScreen
[x] AddFillUp/AddService/AddExpense/AddTrip creation flows
[x] DataStore UI Preferences
[x] CSV import/export (CsvManager, ImportExportUseCase)
[x] Backup screen + WorkManager backup job (BackupWorker, BackupViewModel)
[x] LogsFeedScreen detail view (LogDetailsFullScreen) with swipe/tap,
delete confirmation, and Edit/Delete actions in the top bar

IN PROGRESS — EDIT LOG FEATURE (Rule 10):
[x] LogsFeedScreen: wired real onNavigateToEdit callback (previously
a no-op TODO — root cause of "pencil icon does nothing")
[ ] MainActivity: add `?editId={editId}` optional nav arg to the four
Add\*Screen routes, resolve LogItem subtype -> correct route in
the Screen.Logs composable's onNavigateToEdit lambda
[ ] Repositories.kt / RepositoriesImpl.kt / Daos.kt: add
getXById(id) and updateX(entity) for Fuel, Service, Expense, Trip
[ ] ValidateOdometerUseCase: add optional excludeLogId param, update
DAO bound queries to filter WHERE id != :excludeLogId
[ ] AddFillUpViewModel: SavedStateHandle-driven edit mode (DONE —
see conversation patch, pending integration)
[ ] AddServiceViewModel / AddExpenseViewModel / AddTripViewModel:
mirror the AddFillUpViewModel edit-mode pattern
[ ] AddFillUpScreen / AddServiceScreen / AddExpenseScreen /
AddTripScreen: reflect isEditMode in TopAppBar title

PHASE 5 — POLISH & HARDENING:
[ ] Vico Charts Integration in AnalyticsScreen
[ ] BackHandler / PredictiveBackHandler on form screens for
unsaved-changes warning
[ ] Room Database Migrations (MigrationStrategy)
[ ] WorkManager task for scheduled maintenance reminders

PHASE 6 — ADVANCED FEATURES:
[ ] Receipt OCR (ML Kit Document Scanner / CameraX + text recognition)
[ ] Cloud Backup: Firebase Firestore or Drive API sync
[ ] Widgets: Glance API home-screen widget
[x] CSV Export/Import — DONE (CsvManager)
[ ] PDF Export

PHASE 7 — TESTING:
[ ] Local unit tests: UnitConverter, ValidateOdometerUseCase
(including new excludeLogId branch), bidirectional form logic
[ ] Room in-memory DB tests: DAO aggregate + getById/update queries
[ ] Compose UI tests: DashboardScreen empty state, LogsFeed
swipe-to-delete, edit-flow navigation

======================================================================== 5. KNOWN ISSUES / WATCH-OUTS FOR THE NEXT LLM
========================================================================

- Be cautious when adding heavy dependencies. Ensure versions align with
  gradle/libs.versions.toml (e.g., androidx.documentfile MUST be 1.0.1,
  not 1.0.2).

- EDIT FEATURE IN FLIGHT: the pencil icon in LogsFeedScreen's detail
  view is now wired to call back up to MainActivity's NavHost, but the
  route args, DAO/repository getById+update methods, and
  ValidateOdometerUseCase's exclude-self param are not all in place yet
  for every log type. AddFillUpViewModel has a reference patch; Service/
  Expense/Trip ViewModels still need the same treatment applied. Do not
  assume "edit" works end-to-end for all four log types until Section 4's
  IN PROGRESS checklist is fully checked off.

- Do not create separate Edit*ViewModel/Edit*Screen files — see Rule 10.

========================================================================
END OF ABOUT.MD — PROJECT ODO v1.1.0 (Build: 2026-08-12)
========================================================================
