# Odo

Odo is an Android vehicle logbook built with Kotlin, Jetpack Compose, Hilt, Room, and DataStore. It tracks vehicles, fuel fill-ups, services, expenses, trips, and odometer updates, with dashboard analytics, a unified log feed, CSV import/export, and Google Drive backup.

## Features

- **Multi-vehicle tracking** with per-vehicle distance unit, fuel unit, and currency settings.
- **Fuel, service, expense, trip, and manual odometer logs**, each with full create and edit support (edit reuses the same form used for creation, seeded from the existing entry).
- **Dashboard metrics** for recent fuel cost, fill-up counts, and efficiency trends.
- **Unified log feed** with per-type filtering, tap-to-view details, swipe-to-delete with undo, and in-place editing via the detail view.
- **Settings screen** for vehicle management, UI preferences (theme, nav bar style, title bar behavior), and CSV import/export.
- **Google Drive backup** — a WorkManager-scheduled job zips the local database and syncs it to the signed-in Google account's app-data folder.
- **Room persistence** with DataStore-backed session and UI preferences.

## Build

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

If the wrapper is not executable after cloning, run:

```bash
chmod +x gradlew
```

### Release builds

Release builds require a signing keystore and are normally produced by CI (see [Releases](#releases) below), not built locally. If you do need a local signed build, provide these in `local.properties` or as environment variables: `keystore.path`, `keystore.password`, `key.alias`, `key.password` (or `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), then run:

```bash
./gradlew assembleRelease
```

Release builds run through R8 minification and resource shrinking; see `app/proguard-rules.pro` for the keep rules needed by Room, Hilt, and the Google Drive API client.

## Architecture

Clean Architecture, MVVM, unidirectional data flow (StateFlow → Compose UI):

- `data/` — Room entities, DAOs, `AppDatabase`, and repository implementations.
- `domain/` — repository interfaces and use cases (metrics, log feed assembly, odometer chronology validation).
- `presentation/viewmodel/` — one `StateFlow`-backed UI state per screen; owns all business/interaction logic.
- `presentation/ui/` — Compose screens and reusable UI components. ViewModels are the only thing screens talk to.
- `core/` — shared utilities: unit conversion, CSV import/export, session preferences (DataStore), and the Drive backup `WorkManager` job.
- `di/` — Hilt modules wiring Room, DataStore, and networking.

### Key conventions

- Odometer values are stored internally in **kilometers**; fuel quantities are stored internally in **liters**. UI/ViewModel layers convert to each vehicle's display units — never store display units in the database.
- The active vehicle is tracked centrally via DataStore (`UserSessionManager`); screens collect it rather than holding their own copy.
- Editing an existing log reuses the same Add-screen/ViewModel pair used for creation (loaded via an optional `editId` nav argument), rather than separate Edit-screen files, to avoid duplicating validation and calculation logic.
- Odometer chronology is validated on every fuel/service/trip/odometer insert or update; edits exclude their own prior value from that check.

## Releases

Two GitHub Actions workflows build signed release artifacts:

- **`beta`** — triggered on push to the `beta` branch. Builds a signed APK, tags it `beta-v<versionName>-b<run number>`, and publishes it as a pre-release with an auto-generated changelog since the previous beta.
- **`stable`** — triggered by pushing a `v*` tag (e.g. `v1.0.4`). Builds a signed APK **and** AAB, and publishes a full release with a changelog since the previous stable tag.

Both changelogs are generated from commit messages between tags, not PR titles — write descriptive commit messages.

## Notes for contributors

- Odometer values are stored internally in kilometers.
- Fuel quantities are stored internally in liters.
- UI code should convert stored values to each vehicle's display units before rendering.
- CSV import replaces the local database — validate CSVs before deleting existing data.
- When adding a new log type or field to the edit flow, mirror the existing `AddFillUpViewModel` edit-mode pattern (`SavedStateHandle`-driven `editId`, load-by-id, update-instead-of-insert) rather than introducing a new pattern.