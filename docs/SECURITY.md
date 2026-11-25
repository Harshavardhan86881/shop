# Security

## Threat model

- Adversary is on the same local Wi-Fi/hotspot or has physical access to a phone.
- The main phone's database is the source of truth; clients are not trusted with financial
  decisions.
- Owner credentials and staff/device pairing must resist guessing and device loss.

## Cryptography

- Owner PIN: salted PBKDF2-HMAC-SHA256, 310,000 iterations, stored as hash only. Minimum
  4 digits. Rate limit: 5 failures → 15-minute lockout. Recovery: owner reset procedure
  (documented in OWNER_GUIDE.md); no plaintext PIN anywhere.
- Sessions: 256-bit random tokens, SHA-256 stored, 8 h TTL, revoked on logout/deactivation.
- Devices: 256-bit random device tokens stored as SHA-256; revocable per device; deactivating
  a device kills its sessions.
- Pairing token: single-use, 10-minute TTL.
- TLS: self-signed RSA certificate per install, PKCS#12 identity. Fingerprint pinned exactly
  at pairing (TOFU). Client pins the full SHA-256 fingerprint before sending any token.
  Manual pairing: owner compares the first 8 hex characters of the fingerprint shown on the
  main phone. No trust-all, no cleartext fallback.
- Keystore: device token and session stored in Android Keystore-backed AES-256-GCM
  (`SecureStore`); key non-exportable, invalidated if lock screen is reset.
- Backups: PBKDF2-HMAC-SHA256 (310k) + AES-256-GCM with a user-chosen passphrase. The
  passphrase is never stored; forgetting it makes the backup unrecoverable by design.
  GCM authenticates the ciphertext — tampered/damaged backups fail to decrypt.

## Authorization (server-enforced, not UI-hidden)

Owner only: buying cost, secret-code mapping, profit/margin, user management, settings,
backups/restores, audit, device revocation, bill reversal, wallet adjustment.
Staff: permitted billing/customer data only. All protected routes check `requireOwner()` in
the Ktor handler.

## What is NOT protected / limitations

- The server certificate is self-signed. Fingerprint verification at pairing is the trust
  anchor; until the comparison UI is exercised on real devices, man-in-the-middle on the
  local network is theoretically possible if a user ignores a fingerprint mismatch.
- Third-party keyboards can log the secret code; the app disables suggestions/autofill where
  supported but cannot fully control external keyboards.
- Android may stop the foreground service under memory pressure or after force stop; the
  server is unavailable until restarted. No 24/7 uptime.
- Local network only: the server is never exposed to the public internet and ports are not
  forwarded.
- The app does not verify that a UPI/cash payment actually arrived; the payment method is
  recorded only.

## Data exposure

- Customer names, phones, wallet balances and bill totals appear on staff screens.
- Buying cost, markup, profit and margin never appear on the New Bill screen, customer
  receipts, or staff screens.
- PINs, session tokens and secret codes never appear in logs, notifications, QR codes or
  URLs. Error messages are generic.
