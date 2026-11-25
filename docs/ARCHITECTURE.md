# Architecture

## Process model

A single APK contains both the UI and the embedded server. At launch the app reads
`AppPrefs.mode`:

- `MAIN` — the phone owns the Room database and runs the Ktor HTTPS server in a foreground
  service (`LocalServerService`, type `connectedDevice`). The server binds only to the local
  interface chosen by the owner (default: the hotspot interface; fallback `127.0.0.1`).
- `CLIENT` — the phone has no authoritative database. It talks to the main phone over pinned
  HTTPS. Room is used only as a read cache + draft store.

The main phone bills through the exact same server routes as clients — there is no
“local bypass” path.

## Modules

```
core/          money (paise), secret-code codec, formatting, IDs, wire DTOs
data/db/       Room entities, DAOs, AppDatabase, DraftDao, migrations, DatabaseProvider
security/      PinHasher (PBKDF2), SecureStore (Keystore AES-GCM), PinnedTls
server/tls/    ServerTls (self-signed RSA + PKCS#12 identity + fingerprint)
server/        ServerAuth (roles, sessions, lockout, audit), AuthRepository,
               CustomerRepository, BillingService, WalletService, OverviewService,
               AdminService, ShopServer (Ktor routes), ServerController, LocalServerService
client/        ApiClient (pinned HTTPS), ShopApi (typed), SessionStore, HealthProbe,
               PairingManager, DraftRepository, ConnectionMonitor
backup/        BackupCrypto (PBKDF2 + AES-256-GCM), BackupManager (VACUUM INTO + SAF)
receipt/       ReceiptPdf (customer-safe PDF), ReceiptShare (share sheet / print)
ui/            Compose screens: setup, bill, records, overview, settings; theme; navigator
```

## Data layer

- SQLite via Room, schema v2, migrations are pure SQL and never destructive.
- Money as integer paise everywhere.
- Stable IDs: UUIDs for customers/users/devices/drafts; auto-increment Long for bills.
- FKs and indices on `bills(customer_id)`, `bill_items(bill_id)`, `wallet_ledger(customer_id)`,
  `index_bills_created_at`.
- `bills.request_key` UNIQUE + `bills.request_hash` for idempotency.
- `wallet_ledger` append-only: `direction` CREDIT/DEBIT, `balance_after`, linked `bill_id`.
- Reversals mark `bills.status = REVERSED` with reason/by/at and append `REVERSAL` ledger
  entries; confirmed records are never deleted.

## Bill confirmation (atomic)

One SQLite transaction on the server:

1. authenticate + authorize (Bearer session)
2. validate items, prices, totals against the server's own cost table
3. check idempotency: same `request_key` + same `request_hash` → return original result
   (409 on conflicting payload)
4. re-check wallet balance inside the transaction (serializes concurrent redemptions)
5. insert bill + items, append CREDIT/DEBIT ledger entries, snapshot cashback rate/amount,
   write audit log
6. commit → return `BillDto`

## Client drafts + reconciliation

Unsent/uncertain bills are persisted locally (`DraftRepository`, `DraftEntity`) with status
`LOCAL`, `SENDING`, `UNCERTAIN`, `REJECTED`, `DONE`. A draft is never marked complete until
the server confirms. On reconnect the client calls `requestStatus/{key}` before retrying, so
a network interruption + retry creates at most one bill and one ledger set. Cached wallet
balances are never spent offline.

## Server binding & networking

- Binds to a single IPv4 interface (hotspot address preferred, never `0.0.0.0`).
- TLS only; cleartext disabled (`usesCleartextTraffic="false"`).
- Port selection: configured port, then +1..+9; remembers the port actually used.
- The advertised `baseUrl` + fingerprint are shown to clients as QR or manual entry. The
  address is never hard-coded.

## Foreground service

`LocalServerService` runs the server with a persistent low-priority notification and a
Stop action. `stopWithTask="false"`. NOT sticky (`START_NOT_STICKY`) — if the system kills it,
it does not resurrect silently. Android 14+ declares `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
No 24/7 uptime promise; force-stop stops the server.
