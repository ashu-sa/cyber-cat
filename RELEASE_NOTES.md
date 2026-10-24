# Focanopy 1.13.0 — Android Compose MVP

Android now has a native Jetpack Compose + Material 3 interface designed as the primary Focanopy experience.

- Warm cream and forest-green focus cards, clear square icon controls, animated play/pause changes, gentle progress transitions and rotating sync arrows.
- Optional 25-minute Flower Focus with 5/10-minute breaks, manual next-round starts, automatic round saving and cancellation that withers the flower. The ordinary focus timer remains the default.
- A month calendar with daily durations, Day/Week/Month totals, tree/garden rewards, per-device totals and session rename/delete/undo.
- Google profile bottom sheet and secure in-app Custom Tab sign-in with PKCE and encrypted remembered login. No username/PIN form in the Android interface. Google may require first-use account selection and consent.
- Compose session naming with category suggestions and custom names. Save opens the journal; dismiss keeps the completed timer available for Resume or Reset.
- Settings for the optional floating tree, opacity, animation, notification controls and local export. Main navigation remembers calendar selection and scroll state.

Recommended: **Focanopy-Android-standard.apk**. It excludes the Accessibility service and app-inventory queries. Optional app blocking is in **Focanopy-Android-app-blocking.apk**. Both keep the existing application ID and signing certificate for in-place updates. **Focanopy-Android-debug.apk** is an alias of the standard package. Keep Play Protect enabled; Google controls its verdict.

Validation: both Android variants build; 18 JVM tests per variant and lint pass. Compose UI tests on an isolated Android 15 emulator cover timer controls, save/rename, calendar/profile/settings, Pomodoro automatic completion, background timing and the standard package's absence of the Accessibility implementation. Live Google consent and multi-device sign-in require user verification.

This release updates Android. Desktop packages are carried forward unchanged from v1.12.1.
