# SLN Cloth Shop — Retail billing app with embedded local server

**Sri Lakshmi Narasimha Cloth and Readymade**
Bustand Road, Penagalur, 516127
Owner: Suresh Babu

A production-quality native Android retail billing application (Kotlin, Jetpack Compose,
Material 3) with an embedded local HTTPS server (Ktor) in the same APK. One phone runs
**Main phone** mode (Room/SQLite source of truth + server); 2–3 other phones run **Other
phone** client mode over a local hotspot. No cloud, no Firebase, no internet required for
normal billing.

## Status

Source code is complete and structured for Android Studio. **The APK has not been built in
this environment** — there is no JDK, Gradle, or Android SDK available, and the public Maven
repositories are unreachable from this sandbox, so a normal Gradle dependency resolution /
build could not be performed. See [docs/TESTING.md](docs/TESTING.md) for what remains
unverified and the exact steps to build with Android Studio on a machine that has the SDK.

Do not treat any screen, button or number in the UI prototype as real functionality: there
is no demo data on a fresh install (sample data only behind an explicit demo mode).

## Technology

- Language: Kotlin, Java 17
- UI: Jetpack Compose + Material 3 (Compose BOM 2024.09.03)
- Local database: Room 2.6.1 (SQLite), schema v2, migrations, FKs + indices
- Embedded server: Ktor 2.3.12 CIO over HTTPS (CIO engine, self-signed cert, pinned TLS)
- Serialization: kotlinx.serialization 1.6.3
- Crypto: BouncyCastle 1.78.1 (cert gen), PBKDF2-HMAC-SHA256 310k iterations, AES-256-GCM
- QR: zxing 3.5.3 core + zxing-android-embedded 4.3.0
- minSdk 29, targetSdk 35, compileSdk 35
- Package: `com.slncloth.shop`

## Architecture

One APK, two modes:

- **Main phone** — Room database (single source of truth), embedded Ktor HTTPS server
  running in a foreground service (`connectedDevice` type), all business logic enforced
  server-side. The main phone bills through the same server operations as clients.
- **Other phone** — pinned-HTTPS API client. Room used only as a read cache and for
  unsent drafts; never spends cached wallet offline. Reconciles via `requestStatus`.

Key design decisions (full detail in `docs/`):

- Money stored as integer **paise**; no floating point.
- Unique bill numbers (`SLN-yymmdd-####`), append-only ledger, explicit reversals.
- Idempotent bill creation via client-generated `request_key` + `request_hash`; conflicting
  payloads rejected with 409.
- Wallet overspend prevented server-side inside one SQLite transaction; concurrent redemptions
  serialized.
- TLS with certificate fingerprint pinned at pairing (TOFU + owner compares first 8 chars).
  No trust-all, no secrets in QR/URLs/logs.
- Owner PIN salted with PBKDF2-HMAC-SHA256 (310k); never stored plaintext. Rate limit /
  lockout (5 failures / 15 min). Sessions 8 h.
- Encrypted, authenticated backups (PBKDF2 + AES-256-GCM) via Android SAF; owner-only
  destructive restore; passphrase unrecoverable if forgotten.

## Security status

The design enforces server-side authorization and pinned TLS, but **the full end-to-end
security story (pairing, fingerprint comparison UI, backup restore flow, foreground-service
behavior on real devices) has not been exercised on physical hardware**. Until tested, treat
the build as **not production-ready** for payments involving real money.

## Building

### Prerequisites
- Android Studio (Giraffe or later) with Android SDK 35, build-tools, and a JDK 17.
- An Android device or emulator (API 29+) for testing.

### Steps
1. Open the project in Android Studio.
2. Sync Gradle (requires network access to Maven Central / Google Maven).
3. Build debug APK:
   `./gradlew :app:assembleDebug`
4. Install on a device:
   `adb install app/build/outputs/apk/debug/app-debug.apk`

### Release signing
See `docs/RELEASE_SIGNING.md` guidance (keys are not included in the repo).

## Deliverables in this repo

1. Complete Android Studio project (app + embedded server source).
2. `data/db/` — entities, DAOs, migrations, schema v2.
3. `server/` — Ktor module, all routes, billing/wallet/overview/admin services, auth.
4. `client/` — pinned HTTPS API client, session store, pairing, draft repository.
5. `backup/` — encrypted backup/restore (VACUUM INTO snapshot + PBKDF2/AES-256-GCM).
6. `receipt/` — customer-safe PDF receipt + share/print.
7. `docs/` — architecture, security, testing, owner guide.
8. `app/src/test/` — unit-test directory (tests to be added).

## Documentation

- `docs/ARCHITECTURE.md` — modules, data flow, threading
- `docs/SECURITY.md` — threat model, crypto, pairing, limitations
- `docs/TESTING.md` — build steps, 2–3 phone test plan, unverified items
- `docs/OWNER_GUIDE.md` — plain-language install and daily-use guide

## Acceptance tests (planned, not yet run on devices)

1. 3 phones over hotspot, mobile data off — all see the same new customer and bill.
2. Type `N R R` into masked code — three dots only; suggested price ₹480 at 20% markup; no
   code/cost on bill or receipt.
3. Uppercase `A` vs lowercase `a` differ; unconfigured keys rejected without revealing input.
4. Concurrent wallet redemption from two phones — server prevents overspend, clear conflict.
5. Network interruption + retry with same request ID — at most one bill/ledger set; draft
   reconciled.
6. Direct API calls cannot reach protected cost/profit/backup/settings.
7. Backup restore verifies; wrong passphrase or damaged backup does not overwrite data.
8. Server restart/reconnect without data loss.
