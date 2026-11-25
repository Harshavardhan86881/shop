# Testing

## Build (not yet performed in this environment)

This sandbox has no JDK, Gradle, or Android SDK, and the public Maven repositories are
unreachable, so the project could **not** be compiled or built here. To build:

1. Install Android Studio with SDK 35, build-tools, and JDK 17.
2. Open `/home/user/shop` in Android Studio.
3. Sync Gradle (needs network to Maven Central / Google Maven).
4. `./gradlew :app:assembleDebug`
5. `adb install app/build/outputs/apk/debug/app-debug.apk`

Only claim an APK exists if it has actually been built and installed. This repo contains
source only until a real build is performed.

## Unit tests

`app/src/test/java/com/slncloth/shop` exists but is empty. Suggested tests:

- `Money` / `Pricing`: paise formatting, markup → suggested price, cashback rounding.
- `SecretCodeCodec`: mapping D=1…a=9, R=0; case sensitivity; `mask()`; unassigned keys.
- `PinHasher`: constant-time compare, PBKDF2 output length.
- `BillingService`/`WalletService`: atomic transaction, idempotency (409 on conflict),
  wallet overspend guard, reversal ledger.
- `BackupCrypto`: round-trip encrypt/decrypt, wrong passphrase fails, tampered ciphertext
  fails.
- `ServerController`: bind-address/port selection, loopback fallback.

## Real-device test plan (2–3 phones)

Prerequisites: actual Android phones, Android Studio, USB debugging.

Phone A = Main phone, Phones B/C = clients.

1. Turn on Phone A hotspot in Android settings (manual, as documented). Mobile data OFF on
   all phones (Android will warn "no internet" — expected, ignore for billing).
2. Phone A: Main phone → create shop + owner PIN → start server → note address/port → show QR.
3. Phone B/C: connect to Phone A's hotspot → Other phone → scan QR or enter address/port +
   pairing code + first 8 chars of fingerprint → expect "Connected".
4. Acceptance test 1: add a customer on Phone A; refresh on B/C → all see the same customer.
   Create a bill on Phone B; refresh on A/C → all see the same bill.
5. Acceptance test 2: type N R R in masked code on New Bill → three dots visible; suggested
   price ₹480 at 20% markup; no code or cost on bill/receipt.
6. Acceptance test 3: uppercase A vs lowercase a → different digits; an unconfigured key →
   generic message, input not echoed.
7. Acceptance test 4: two phones simultaneously redeem the same wallet balance → one
   succeeds, the other gets a clear conflict; ledger totals remain correct.
8. Acceptance test 5: disable Wi-Fi mid-confirmation, retry with same request ID → at most
   one bill and one ledger set; draft remains; status reconciles on reconnect.
9. Acceptance test 6: call protected API endpoints directly (cost/profit/backup/settings)
   without an owner session → 401/403.
10. Acceptance test 7: create an encrypted backup; restore with wrong passphrase → rejected;
    restore with correct passphrase → wallet balances, bills, customer history intact.
11. Acceptance test 8: stop/restart the server on Phone A → clients reconnect; no data loss.

## Battery / background restrictions

- Test with the app in the background and the screen locked: server should keep running via
  the foreground-service notification (until Android kills it).
- Test "Don't keep activities" and battery optimization on/off.
- Do not promise 24/7 uptime; document that force-stop kills the server.

## Models / versions tested

None yet — no physical devices were available in this environment. Fill in actual models and
Android versions after real-device testing.
