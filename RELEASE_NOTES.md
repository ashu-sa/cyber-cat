# Focanopy v1.3.0 — Android-first focus forest

- New product name: Focanopy (focus + canopy), across Android, macOS, Windows and Ubuntu. Existing package IDs, credentials and history remain compatible.
- Android is now the main MVP: cohesive forest palette, daily focus/tree cards, live next-tree progress, accessible icon controls and persistent bottom navigation.
- Typed task names survive closing/reopening the save popup and activity recreation. Saving still opens calendar history.
- Floating Finish stops the session before asking Android to open the save screen, protecting against blocked background launches.
- Calendar retains selected period across activity recreation; visible Day/Week/Month selection and garden highlights.
- Export saved sessions as JSON through Android’s document picker. Notification permission state is visible in Settings.
- Animation stops with a hidden app window; background timing and notification chronometer remain active.
- README rewritten around the Android MVP, engineering architecture, fresh native screenshots and a résumé-ready project description.

Android 8+, macOS 14+, Windows x64 and Ubuntu 24.04+ packages. Android APK retains the original debug signing key; desktop apps remain unsigned/ad-hoc signed. No Google Play release or physical OEM-device validation is claimed.
