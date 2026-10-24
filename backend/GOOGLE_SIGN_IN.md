# Google sign-in setup

All four native clients use **Supabase Auth + Google OAuth with PKCE**. One Google **Web application** OAuth client supports Android, macOS, Windows and Ubuntu; no Google client secret belongs in an APK, executable, repository or chat.

## Hosted configuration status

Verified on 24 October 2026: Google is enabled in Supabase, nonce checks remain enabled, users must have an email, and both native redirect patterns below are saved. The authorization endpoint redirects to Google with the configured Web client and only `email profile` scopes. Google Cloud remains External / Testing; basic identity-only requests are exempt from the test-user allowlist restriction ([Google audience policy](https://support.google.com/cloud/answer/15549945?hl=en)). A full user-driven consent, native callback and cross-device sync test remains to be completed. No client secret is stored in the repository.

## One-time owner setup

1. Open [Google Auth Platform](https://console.cloud.google.com/auth/overview). Choose a Google Cloud project (or create a Focanopy project). Configure Branding with the app name **Focanopy** and your support/developer contact email. Set Audience to External if people outside your organization need to sign in. Basic identity-only sign-in can be used while in Testing without adding test users; if adding other scopes, review Google’s test-user restrictions. Publish to Production when your branding and public launch are ready.
2. Add only the basic scopes **openid**, **email**, and **profile**. The app does not request Drive, Gmail, calendar or contacts access.
3. Under **Clients → Create client**, choose **Web application**. Name it **Focanopy Supabase**. Add this **Authorized redirect URI** exactly:

   ```text
   https://fwlengqpmrtmpbrhtxis.supabase.co/auth/v1/callback
   ```

   Google sends the user to Supabase first, so native app schemes and loopback URLs do **not** belong in Google's redirect URI list. No JavaScript origin is needed for this browser-based native app flow.
4. Open [Supabase → Authentication → Sign In / Providers → Google](https://supabase.com/dashboard/project/fwlengqpmrtmpbrhtxis/auth/providers?provider=Google). Enter the Web client ID and client secret **directly into Supabase**, enable Google and save. Keep **Skip nonce checks** and **Allow users without an email** off. Do not change existing email, PIN gateway, RLS, project keys or PIN_PEPPER settings.
5. In [Supabase → Authentication → URL Configuration](https://supabase.com/dashboard/project/fwlengqpmrtmpbrhtxis/auth/url-configuration), add these two **Redirect URLs**, preserving existing entries:

   ```text
   focanopy://auth/**
   http://127.0.0.1:*/focanopy/**
   ```

   The first handles Android and macOS. The second is a Windows/Linux callback bound only to this computer, on a random port and random single-use path. PKCE protects the code exchange; apps also validate the callback destination and state. The desktop listener closes on completion, cancellation or timeout. Do not use a global `**` allowlist entry or alter the Site URL.

The account owner must create and enter the Google OAuth credential. Do not send the client secret in chat. The clients detect a disabled provider and show a setup message instead of opening a broken sign-in page.

## User experience

Open **Account / Sync → Continue with Google**, choose your account in your system browser, and return to Focanopy. There is no separate registration form or PIN for Google users. Use the same Google account on every device to see one combined journal with device labels. Saved sessions are retained in the existing OS-secured credential stores and account-specific caches, so sign-in is not shown on every launch.

The optional **Add this device’s existing sessions to my account** checkbox controls local-only history upload. Google accounts and the older synthetic username/PIN accounts are separate identities; the app does not silently merge unrelated accounts. Signing out returns to local-only history, and account switching is blocked during an active timer.

Android persists the pending verifier encrypted in its no-backup directory so the browser can return after activity/process recreation. Pending sign-ins expire after three minutes. Reopen Account / Sync to cancel a pending browser sign-in. macOS uses ASWebAuthenticationSession; Windows/Linux open the default browser and use a short-lived IPv4 loopback listener. No embedded login WebView is used.

## End-to-end verification after setup

- On each platform, sign in using a disposable Google test account. Save one session, then verify it appears on another device signed into that same Google account.
- Restart each app: it should retain sign-in and restore the same account cache.
- Cancel consent or the waiting flow: local history and timer state must remain unchanged.
- Try a second Google account: the first account's sessions must not be visible. Sign back in to recover its journal.
- Confirm an active timer prevents account switching, and test optional local-session upload separately.

Unit tests cover PKCE challenge generation, exact callback/state checks, duplicate parameter rejection, cancellation/timeout, loopback ownership, safe vault/cache adoption and Google profile names. They do not replace the final real Google consent check, which requires the owner's configured OAuth client.

References: [Supabase Google setup](https://supabase.com/docs/guides/auth/social-login/auth-google), [PKCE flow](https://supabase.com/docs/guides/auth/sessions/pkce-flow), [redirect allowlists](https://supabase.com/docs/guides/auth/redirect-urls).
