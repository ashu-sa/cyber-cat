# Focanopy v1.5.0 — Google sign-in support

- Adds **Continue with Google** on native Android, macOS, Windows and Ubuntu account screens.
- Uses the system browser and PKCE, with validated callbacks, cancellation, a three-minute timeout, and the existing OS-secured credential stores.
- Google users share the same account-scoped journal and device-labelled sessions across all platforms. Local-session upload remains an explicit choice. Existing username/PIN accounts remain available and separate.
- Android retains pending sign-in securely across activity/process recreation; macOS uses ASWebAuthenticationSession; Windows/Linux use an IPv4 loopback callback on a random port and single-use path.

**Owner setup is still required:** the hosted Supabase project's Google provider is disabled and has no Google OAuth client configured. Complete `backend/GOOGLE_SIGN_IN.md` before live Google login can succeed. The apps show a clear setup message until it is enabled. End-to-end Google consent and cross-device login remain pending that configuration; callback, account isolation and existing timer tests are automated.

No Google client secret is included in any app or repository. Existing history, credentials and signing identities remain compatible. Android is a debug-signed testing APK; desktop packages remain unsigned/ad-hoc signed.
