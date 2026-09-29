<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="112" alt="Odo app icon" />

# Odo

**A private, offline-first vehicle logbook for Android.**<br/>
Track fuel, services, expenses and trips — or just point your camera at the pump.

[![Latest release](https://img.shields.io/github/v/release/maxcodl/Odo?include_prereleases&label=release&color=D4A373)](https://github.com/maxcodl/Odo/releases)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)

[**Download the latest APK**](https://github.com/maxcodl/Odo/releases/latest) · [Features](#features) · [Build](#build) · [Architecture](#architecture)

</div>

---

## Screenshots

<table>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/8935095e-5cfb-45ef-9d9c-c86b1eea264d" width="220" alt="Home dashboard" /><br/><sub><b>Home</b> — 30-day metrics & recent activity</sub></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/ebf8673f-cee5-48e7-93ef-ff847def42e0" width="220" alt="Log feed" /><br/><sub><b>Log feed</b> — every entry, filterable</sub></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/2d1eac57-bd8a-40be-b3a5-eb188c68309a" width="220" alt="Analytics" /><br/><sub><b>Analytics</b> — monthly spend & fuel economics</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="https://github.com/user-attachments/assets/56955854-0aeb-474e-a4f1-23c3eee85651"![Uploading Screenshot_20260928-170730.png…]()
 width="220" alt="Log a fill-up" /><br/><sub><b>Log fill-up</b> — rate prefilled, auto-calc</sub></td>
    <td align="center"><img src="docs/screenshots/scan-pump.png" width="220" alt="Pump display scanner" /><br/><sub><b>Pump scanner</b> — reads the 7-segment display</sub></td>
    <td align="center"><img src="https://github.com/user-attachments/assets/e8a7f39b-cc92-4677-9c12-f1c6245187e0" width="220" alt="Settings" /><br/><sub><b>Settings</b> — Monet, AMOLED, backups</sub></td>
  </tr>
</table>

---

## Features

### 📷 Scan instead of type

- **Pump display scanner** — a live camera finds the pump's LCD windows (Amount, Volume, Rate) inside their dark bezel and decodes the **7-segment digits directly**, segment by segment. A reading is only offered once `litres × rate = amount` checks out across consecutive frames.
- **Odometer scanner** — a two-pass read: first locate the `ODO` label or `km` unit, then re-read just that region enlarged, contrast-stretched and inverted. Readings below your last odometer value are rejected rather than guessed.
- **Gallery import** — both scanners also work on existing photos; printed receipts are parsed too.
- **100% on-device** — ML Kit's bundled model plus a custom decoder. No photo ever leaves the phone.

### ⛽ Logging

- **Fuel, service, expense, trip and odometer logs**, each with full create / edit / delete.
- **Smart fill-up form** — the fuel rate is prefilled from your last fill-up, and any two of quantity · rate · total calculate the third.
- **Partial-tank aware efficiency** — km/L (or mpg) is computed across partial fills correctly.
- **Odometer chronology checks** on every entry, so a typo can't break your stats.

### 📊 Insight

- **Home dashboard** — 30-day fuel cost, fill-up count, efficiency trend chart, and a recent-activity feed you can tap into, edit, or delete (with undo).
- **Analytics** — total running cost, cost per km, projected yearly cost, cost breakdown donut, and a **monthly spend chart with amounts on every bar**, fuel price history, and per-station price & efficiency.
- **Trip report** — business vs personal distance by month or year, with a mileage-claim CSV export.
- **Receipt photos** — attached receipts are stored privately in the app and shown on the fill-up.

### 📍 Location (optional, both can be switched off in Settings)

- **Pump detection** — logging a fill-up takes a GPS fix and fills in the station: first from stations you've logged within 150 m (offline), otherwise the nearest fuel station on OpenStreetMap. The spot is shown on a map.
- **Automatic trip log** — driving is detected with the phone's low-power motion sensors; GPS runs only while driving, and the trip is saved with its route, distance and start/end place names. Discard a trip from its notification.
- **No API keys** — maps are OpenStreetMap via osmdroid, station lookup uses the public Overpass API, place names use Android's built-in Geocoder.
- **Multi-vehicle** — cars and bikes, each with its own distance unit, fuel unit and currency; view one vehicle or all combined.

### 🔒 Your data

- **Offline-first** Room database — the app works fully without a network. The optional location features above are the only parts that go online (your coordinates are sent to OpenStreetMap / Android's geocoder).
- **Google Drive backup** on a WorkManager schedule, into the app's private Drive folder.
- **CSV import / export** for spreadsheets or migrating from other apps.

### 🎨 Made to fit your phone

- Material You (**Monet**) dynamic color, **AMOLED** true-black mode, edge-to-edge layout, floating nav bar in solid / blurry / glassy styles, and a title bar that hides while scrolling.

---

## How the pump scanner works

```mermaid
flowchart LR
    A[Camera frame] --> B[Crop to guide frame<br/>upright luma]
    B --> C[Find bright windows<br/>inside dark bezel]
    C --> D{Big window +<br/>2 small below?}
    D --> E[Split rows · deskew<br/>italic digits · cells]
    E --> F[Test 7 segments<br/>per digit]
    F --> G{volume × rate<br/>= amount?}
    G -- yes, 2 frames in a row --> H[✅ Offer values]
    G -- no --> I[ML Kit on binarised,<br/>thickened image]
```

The decoder lives in [`core/PumpDisplayReader.kt`](app/src/main/java/com/auto/odo/core/PumpDisplayReader.kt) and is plain Kotlin — its unit tests run against a synthetic display and a real pump photo on the JVM, no device needed.

---

## Build

Requirements: **JDK 17** and the **Android SDK (API 36)**. Point `sdk.dir` in `local.properties` at your SDK.

```bash
./gradlew testDebugUnitTest assembleDebug
```

If the wrapper is not executable after cloning: `chmod +x gradlew`.

### Release builds

Release builds require a signing keystore and are normally produced by CI (see [Releases](#releases)), not built locally. For a local signed build, provide these in `local.properties` or as environment variables — `keystore.path`, `keystore.password`, `key.alias`, `key.password` (or `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) — then run:

```bash
./gradlew assembleRelease
```

Release builds run through R8 minification and resource shrinking; see `app/proguard-rules.pro` for the keep rules needed by Room, Hilt, and the Google Drive API client.

---

## Architecture

Clean Architecture, MVVM, unidirectional data flow (StateFlow → Compose UI).

```mermaid
flowchart TB
    UI["presentation/ui<br/>Compose screens"] -->|events| VM["presentation/viewmodel<br/>StateFlow UI state"]
    VM -->|state| UI
    VM --> UC["domain<br/>use cases · repository interfaces"]
    UC --> DATA["data<br/>Room entities · DAOs · repositories"]
    VM --> CORE["core<br/>OCR & display readers · units · CSV · session · backup"]
```

| Layer                     | Contents                                                                                                               |
| ------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `data/`                   | Room entities, DAOs, `AppDatabase`, repository implementations                                                         |
| `domain/`                 | Repository interfaces and use cases (metrics, log feed assembly, odometer chronology validation)                       |
| `presentation/viewmodel/` | One `StateFlow`-backed UI state per screen; owns all business / interaction logic                                      |
| `presentation/ui/`        | Compose screens and reusable components, including the live camera scanner. Screens only talk to ViewModels            |
| `core/`                   | Pump display & odometer readers, text parsing, unit conversion, CSV import/export, DataStore session, Drive backup job |
| `di/`                     | Hilt modules wiring Room, DataStore, and networking                                                                    |

**Tech:** Kotlin · Jetpack Compose (Material 3) · Hilt · Room · DataStore · WorkManager · CameraX · ML Kit Text Recognition · Navigation Compose

### Key conventions

- Odometer values are stored internally in **kilometers**; fuel quantities in **liters**. UI/ViewModel layers convert to each vehicle's display units — never store display units in the database.
- The active vehicle is tracked centrally via DataStore (`UserSessionManager`); screens collect it rather than holding their own copy.
- Editing an existing log reuses the same Add-screen/ViewModel pair used for creation (loaded via an optional `editId` nav argument), rather than separate Edit-screen files, to avoid duplicating validation and calculation logic.
- Odometer chronology is validated on every fuel/service/trip/odometer insert or update; edits exclude their own prior value from that check.
- Bottom-tab navigation — including in-screen shortcuts like "View All" — goes through one `navigateToTab` function so tab back stacks are saved and restored consistently.

---

## Releases

Two GitHub Actions workflows build signed release artifacts:

- **`beta`** — triggered on push to the `beta` branch. Builds a signed APK, tags it `beta-v<versionName>-b<run number>`, and publishes it as a pre-release with an auto-generated changelog since the previous beta.
- **`stable`** — triggered by pushing a `v*` tag (e.g. `v1.0.4`). Builds a signed APK **and** AAB, and publishes a full release with a changelog since the previous stable tag.

Both changelogs are generated from commit messages between tags, not PR titles — write descriptive commit messages.

---

## Contributing

- Odometer values are stored internally in kilometers; fuel quantities in liters. Convert to display units only in UI code.
- CSV import replaces the local database — validate CSVs before deleting existing data.
- When adding a new log type or field to the edit flow, mirror the existing `AddFillUpViewModel` edit-mode pattern (`SavedStateHandle`-driven `editId`, load-by-id, update-instead-of-insert) rather than introducing a new pattern.
- Scanner changes: add a failing case to `PumpDisplayReaderTest` / `TextScannerTest` / `OdometerReaderTest` first — the parsers and decoders are pure Kotlin and fast to test.

<div align="center">
<sub>Built with ☕ by <a href="https://github.com/maxcodl">max</a></sub>
</div>
