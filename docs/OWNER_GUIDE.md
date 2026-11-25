# Owner guide — Sri Lakshmi Narasimha Cloth and Readymade

Plain-language instructions for the shop owner (Suresh Babu). Keep this with the shop's
records.

## What this app does

It turns one Android phone into the shop's billing server and lets up to two other phones
bill over the shop's local Wi-Fi hotspot. No internet is needed for billing. All customer,
bill and wallet data stays on the shop's main phone.

## What you need

- 2–3 Android phones (Android 10 or later).
- The main phone should have a reliable battery and stay near the counter while the shop is
  open.
- Android Studio is needed only to install the app the first time; day-to-day use needs no
  computer.

## First-time setup (main phone)

1. Install the app on the phone that will stay in the shop.
2. Open it and choose **Main phone**.
3. Enter the shop name, address and owner name (pre-filled with Sri Lakshmi Narasimha
   Cloth and Readymade, Bustand Road, Penagalur, 516127, Suresh Babu).
4. Create an owner PIN of at least 4 digits. Confirm it. **This PIN is never stored in
   plaintext.** If you forget it, follow the recovery procedure below.
5. Turn on this phone's Wi-Fi hotspot in Android settings (the app cannot create it for you).
6. Tap **Start server**. The screen shows the local address and port (for example
   `https://192.168.43.1:8787`). This address may change if you restart the hotspot — check
   it each morning.
7. Tap **Show pairing QR** to show a QR code, or read out the address, port, pairing code and
   the first 8 characters of the security code to staff setting up other phones.

## Adding other phones

1. On the other phone, open Android Settings → Wi-Fi and connect to the main phone's hotspot.
   Mobile data can stay off.
2. Open the app and choose **Other phone**.
3. Scan the main phone's QR code, or enter the address, port, pairing code and security code
   manually.
4. Wait for **Connected**. If it shows **Server unavailable**, check that the hotspot is on,
   the main phone's server is running, and the address is correct; tap **Retry**.

## Making a bill

1. On any connected phone, open **New bill**.
2. Search for a customer by name or mobile, or tap **Add customer** to create one. Indian
   mobile numbers are validated.
3. Pick a category chip (Saree, Shirt, Jeans, etc.).
4. Enter the item code in the masked field. Only dots are shown — the code is never visible
   on screen, receipts or logs. The mapping is case-sensitive: `N R R` means cost ₹400; at a
   20% markup the suggested price is ₹480. Uppercase `A` and lowercase `a` are different.
   Unrecognised keys are rejected with a generic message.
5. Adjust quantity with the stepper. The suggested price comes from the code and the
   configured markup; you may override it if authorized — the override is recorded.
6. The screen shows subtotal, wallet used, net payable and projected cashback (5% of net
   after wallet, rounded to the nearest rupee, configurable by the owner).
7. Choose a payment method (Cash, UPI, Card, Other). This is recorded only — the app does
   not verify that a UPI payment arrived.
8. Tap **Confirm bill**. The server confirms and the bill is saved.

## Receipts

- After confirming, a customer-safe receipt is shown: shop name, bill number, date, items,
  amounts and cashback.
- It never shows the item code, buying cost, markup or profit.
- Tap share to send via WhatsApp or another app, or print where supported. The app confirms
  the handoff to another app — it cannot prove the customer received it.

## Records

- **Customers:** search, add, edit. Wallet balance and purchase history are shown.
- **Bills:** search by number, customer or phone; filter by date; view bill detail and status.
- **Wallet:** timestamped cashback credits and wallet debits with linked bills.
- Mistakes: use the owner-authorized reversal workflow. Confirmed records are never deleted.
  If cashback was already spent, reversing a bill may make the wallet negative — the owner
  decides whether to allow that.

## Overview (owner only)

- Requires the owner PIN. Shows sales, buying cost, profit and margin with date filters.
- Markup = profit ÷ cost. Margin = profit ÷ selling price. A 20% cost markup is not a 20%
  sales margin.
- Figures come from real bills — never demo data.

## Backup and restore (owner only)

1. Tap **Back up now** in Settings and choose a location (a separate USB drive, another phone,
   or any folder the document picker can reach).
2. Create a backup passphrase of at least 8 characters. **Write it down and keep it safe —
   it is not stored anywhere and cannot be recovered if forgotten.**
3. The backup is encrypted and authenticated. A wrong passphrase or a damaged file is
   rejected and never overwrites current data.
4. To restore: stop the server, choose the backup file, enter the passphrase, and confirm the
   destructive action. Connected clients should reconnect and refresh afterwards.
5. The Settings screen shows the real last successful backup time — never a fake status.

## Server

- Start/stop the server from Settings. A persistent notification shows the server is running.
- Android may stop the service under memory pressure or if you force-stop the app; restart it
  from Settings. This is not a 24/7 server.
- Do not expose the address to the internet or forward ports.

## Recovery

- Forgotten owner PIN: there is no "forgot PIN" that reveals it. The owner must reset it
  through the setup recovery procedure, which requires owner re-authentication where
  possible. Keep a written record of the PIN and the backup passphrase.
- Lost client phone: revoke its device in Settings on the main phone. Its sessions are
  killed immediately.
- Damaged or wrong-passphrase backup: current data is untouched; try another backup file.

## Demo data

A fresh install has no sample customers or bills. Any demo data appears only behind an
explicit demo mode and is never mixed with real shop records.
