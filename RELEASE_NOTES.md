# Focanopy 1.12.1 · Android permission-reduced standard build

This update changes the Android packages. Desktop/Linux packages are carried forward unchanged from 1.12.0.

- The recommended **Focanopy-Android-standard.apk** contains no Accessibility service declaration, Accessibility service implementation, or installed-launcher-app inventory queries.
- Stopwatch, tree animation, Pomodoro, session history, sync, notification controls and the optional floating tree remain available. Android app blocking is omitted from this build.
- App blocking is available only in the separate **Focanopy-Android-app-blocking.apk**. Its sensitive Accessibility declaration can cause Internet-sideload installation restrictions; keep Play Protect enabled.
- **Focanopy-Android-debug.apk** is a compatibility filename for the standard APK. Both builds retain the original application ID and signing identity, preserving in-place updates and local history.
- Removing the sensitive capability does not constitute Google certification or guarantee a Play Protect verdict. Google's developer guidance: https://developers.google.com/android/play-protect/warning-dev-guidance.

Validation: unit tests and lint for both packages; isolated standard-APK instrumentation verifies the absence of the service declaration and class, and exercises start/pause/resume/save plus Pomodoro automatic completion. Full app-blocking build remains separately buildable.
