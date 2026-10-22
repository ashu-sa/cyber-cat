# Cyber Cat accounts and sync

Live project: [cyber-cat](https://supabase.com/dashboard/project/fwlengqpmrtmpbrhtxis), owned by **ashu-sa**, Cyber Cat Free organization, Mumbai (`ap-south-1`).

## Google sign-in

The native clients also support Google through Supabase OAuth + PKCE. The hosted provider must be configured by the owner before Google login works; follow [Google sign-in setup](GOOGLE_SIGN_IN.md). Google credentials use the same sync API, RLS and per-account caches; no schema or PIN gateway changes are required.

## App experience

Open **Account / Sync** (Android: Settings), choose a username and **four-digit PIN**, then Create account or Sign in. The same credentials work on Android, macOS, Windows and Linux. The app keeps the login in the OS credential vault and opens normally on later launches. The PIN is never saved on the device.

A signed-in journal combines completed sessions from all devices and displays source labels and per-device totals. The active timer belongs to its device. Overlapping sessions are additive. Offline changes persist in an account-specific cache and retry while the app is open. Android also tries syncing after a local edit; it does not run a separate background polling service.

Existing local history uploads only when the sign-in checkbox is selected. Local-only history remains in its original file. Sign out restores that history; account caches and pending edits stay isolated so they can resume after signing in again. Finish an active timer before switching accounts. Account settings include manual sync and explicit conflict choices: Keep my changes / Use cloud changes.

There is no email step or email recovery. Keep your username and PIN safe. A four-digit PIN has 10,000 combinations; the server limits attempts rather than treating it as a strong password.

## Backend

Apply SQL migrations in filename order. Deploy `supabase/functions/cybercat-account/index.ts` with JWT verification **off** because login is public. It validates inputs and enforces server-side username/IP attempt buckets. Set a cryptographically random **PIN_PEPPER** in Edge Function Secrets. Preserve it: changing it invalidates existing PIN-derived passwords. Do not put it in source, apps, logs or screenshots.

The gateway derives the internal Supabase password with HMAC-SHA256 using the server-only pepper, username and PIN. It creates a confirmed internal identity through Supabase Admin Auth and returns a normal user session. Clients never receive an administrator key or derived password. Synthetic `username@accounts.cybercat.invalid` addresses are internal identifiers, not mail destinations. Supabase's internal password length policy remains enabled. Five consecutive account attempts per 15 minutes and 30 IP attempts per 15 minutes are permitted; successful authentication clears the account attempt bucket. Error messages are generic. The server-side pepper and rate limits do not increase the PIN's underlying entropy.

Only the public publishable key and project URL are shipped in apps. History requests use the user's access token. `focus_sessions` has owner-only RLS. Private attempt buckets are not exposed to client roles; only the service-role gateway can call their helpers.

`sync_focus_sessions` uses invoker permissions and a transaction. It validates ownership through RLS, processes up to 100 changes, uses stable UUIDs, compares revisions, retains deletion tombstones, and returns all owned rows including tombstones. Conflicting edits remain in the local outbox until reviewed. Edits made during a network request remain pending. Milliseconds since Unix epoch are the common wire format; Swift/Python local time formats are converted explicitly.

Credential storage: macOS Keychain; Android AES-GCM encrypted credentials with an Android Keystore key and no-backup storage; Windows Credential Manager / Ubuntu Secret Service through keyring. Desktop sign-in fails clearly if no secure vault is available. No plaintext credential fallback is used. Android backup remains disabled.

## Verification

Desktop unit tests cover offline recovery/replay, account isolation, tombstones/undo, edits during upload, conflict review and vault failures. Swift clock/history tests still run via `macos/test.sh`. `macos/Tests/CloudIntegration.swift` is a manual live test using disposable credentials and a separate Keychain service. Android's `CloudInstrumentation` uses a separate `.qa` application ID via `-PcloudQa` and checks real sign-in, shared totals and encrypted credential restoration. Live backend tests also exercise cross-account isolation and anonymous denial. Never run integration tests using a person's account.

Production builds remain unsigned/debug-signed as documented in the root README. OS trust prompts may appear for unsigned applications or changed signing identities. Windows/Linux vault behavior still needs real-user desktop testing beyond CI packaging.
