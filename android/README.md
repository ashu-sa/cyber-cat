# Focanopy for Android

A native **Kotlin Android app**, with a native growing-tree companion. Optional shared accounts securely sync history across devices. No Electron or web view. Supports **Android 8.0 (API 26) and newer**; targets Android 15 (API 35).

## Install

Copy `Focanopy-Android-standard.apk` to your phone, open it, and permit installation from that source if Android asks. This is a locally debug-signed build for personal testing, not a Play Store release. A production release should be signed with your own release key.

The **standard APK** is the recommended download. It contains no Accessibility service or app-blocking implementation, and does not query the installed launcher-app inventory. Stopwatch, Pomodoro, history, sync, notification controls, and the optional floating tree still work. `Focanopy-Android-debug.apk` is a compatibility filename for the same standard build.

### Play Protect and the optional app-blocking build

The separate `Focanopy-Android-app-blocking.apk` includes the Accessibility service used to detect the foreground app and return to Home when a blocked app opens. It declares window-state events, not screen-content retrieval. Even so, Google treats Accessibility as sensitive access and can block Internet-sideloaded apps declaring it. See [Google's developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance).

Use the standard APK if you do not need app blocking. Keep Play Protect enabled. Removing a sensitive capability does not certify an APK or guarantee Google's current verdict; a developer appeal may still be needed if the standard build is flagged. Both packages retain the original application ID and signing key for in-place updates. Switching to standard does not erase session history.

1. Launch Focanopy and allow notifications when asked.
2. Tap ▶ to start. Tap Ⅱ to pause and ▶ to resume.
3. The ongoing notification shows a system-managed live chronometer, with Pause/Resume and Finish actions.
4. Finish opens the naming popup. Choose Job Apply, Upskill, Interview Prep, or Work, or type any custom name.
5. Save opens the calendar journal. Keep for later dismisses naming without discarding the finished session; Resume and Reset remain available.

Notifications are the default background experience. You do **not** need floating-window permission for stopwatch functionality. On Android 13+, denying notification permission can hide the notification from the shade even while the foreground service is running; use Settings → Notification settings to allow it.

## Optional floating tree

Open **Settings → Floating tree**, grant Android’s **Display over other apps** permission, then return to Focanopy. A small transparent tree appears above ordinary apps:

- Drag the tree to move it; position is remembered.
- ▶ / Ⅱ starts, resumes, or pauses.
- ✓ finishes the session and opens naming.
- × hides the pet without stopping an active session.
- Tap the tree itself to open the app.
- Settings also provides an opacity slider (30–100%), animation switch, and Show/Hide controls.

If the OS does not allow an overlay, notification controls remain available. Sensitive system screens, banking apps, lock screens, some manufacturers, and managed devices may hide overlays. No Accessibility service is requested. If launching the naming screen from the overlay is blocked by Android, the finished session remains available from the notification.

## Compose MVP interface

The main focus screen, journal, settings, profile bottom sheet, and in-app session naming use Jetpack Compose and Material 3. Page transitions, changing focus messages, progress, play/pause icons, session-list changes and sync arrows animate through Compose. System animation scaling is respected. The existing native tree renderer is hosted through `AndroidView`; timer state remains in `Repository` / `TimerEngine` and the foreground service, independent of composition and screen refreshes.

The profile icon starts Google OAuth with PKCE in a secure Custom Tab, styled to match Focanopy. It returns through the existing exact callback and retains encrypted credentials in Android Keystore. No Google password is entered in the app and there is no separate registration form. When logged in, the profile sheet shows the saved account; the icon-only sync button uploads pending sessions and downloads device history. Existing local-only sessions are not silently imported. Actual Google account consent and cross-device validation require a user-driven sign-in; the tests cover OAuth callback handling, not personal Google credentials.

## Journal

A month calendar shows daily active totals (`2h`, `1h 15m`, `45m`). Select a date to inspect its sessions; Day/Week/Month buttons show totals for the selected period. The selected Day/Week/Month total, trees or garden reward, and per-device totals are displayed. Open a session’s ⋮ menu to rename or delete; deletion requires confirmation and offers Undo. Weeks start Monday; totals use each session’s local start date, excluding pauses.

## Timing and lifecycle

Live elapsed time comes from `SystemClock.elapsedRealtime()` (including deep sleep), not a tick counter. The notification uses Android’s own chronometer rather than rebuilding every second. The service checkpoints on transitions and every 15 seconds. History uses `AtomicFile` replacement in app-private storage. Preferences and session checkpoints are local to the app. Optional account sync uses INTERNET permission; credentials are encrypted with Android Keystore.

Swiping the app out of Recents does not intentionally stop the foreground service. Android’s Force stop, the system foreground-app Stop control, reboot, or a manufacturer battery manager can stop it. Reopening Focanopy restores its checkpoint; recovery on the same boot uses monotonic elapsed time, and recovery after reboot uses wall-clock time (manual clock changes can affect that recovery). The service does not auto-launch from boot. An idle service stops unless the optional pet is shown.

The service declares Android 14+ `specialUse` foreground-service type for a user-started stopwatch and optional overlay. Distribution through Google Play requires the relevant foreground-service declaration/review; the included APK has not been submitted to Google Play.

## Build

Open this folder in Android Studio. Install Android SDK Platform 35 and use JDK 17+ (Android Studio’s bundled JBR works).

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On Windows, use `gradlew.bat` with the same tasks. Gradle wrapper 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21. The first build downloads build dependencies. Set `ANDROID_HOME` or configure the SDK path through Android Studio.

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Verification

Both standard and app-blocking builds pass lint and 18 JVM tests each. Four Compose UI tests on the isolated Android 15 QA emulator cover timer controls, save/rename, calendar/profile/settings, Pomodoro automatic completion, background timing and the standard APK’s exclusion of the Accessibility blocker. See **Compose device tests** below for the current commands. Screenshots use sample sessions, never personal activity.

Physical-phone notification/overlay behavior, manufacturer battery policies, deep-sleep/reboot recovery, and real Google consent across devices still require device testing. Earlier View-based instrumentation files are historical and do not validate the new Compose screens.

## Android references

- [Foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Draw-over-other-apps permission](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION)
- [Monotonic clock including deep sleep](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime())

## Shared Google account

Tap the profile icon to connect a Google account. Encrypted credentials are retained in Android Keystore and the profile sheet shows the remembered account. Tap the sync arrows to upload/download sessions across devices signed into the same account. There is no Android username/PIN form. Existing synthetic username/PIN identities remain separate from Google identities; local sessions are not silently imported. See the [backend guide](../backend/GOOGLE_SIGN_IN.md).

## Focus forest

The trunk, leaves and crown grow continuously across the full hour. Each completed tree stays visible beside the next sprout. Saved sessions add together per day, including synced sessions from other devices. Eight saved hours in a day earns a Garden badge. Calendar days show time and tree rewards; week/month summaries count garden days. Rewards recalculate after edits/deletions. Pausing stops water and turns the current plant dry and dormant; resuming restores green foliage and watering. Disable animation for a still companion.

## Android MVP (v1.3)

Android is Focanopy’s flagship application. The home screen includes saved daily time, tree counts, the live companion and progress to the next tree. Persistent bottom navigation opens Timer, Journal and Settings. Notification permissions and optional floating controls are visible in Settings; neither overlay permission nor an account is required for core focus timing.

Settings → **Export session history** opens Android’s document picker to save the visible history as JSON. This export contains task names, times, durations and device metadata; credentials and PINs are never exported. The Compose save dialog retains its draft through Activity recreation. Package ID and Keystore aliases retain the original namespace for upgrade compatibility.

Floating controls support dragging from the tree, timer, button surfaces and empty space. Movement beyond the drag threshold suppresses the click action; a regular click/tap still controls the stopwatch. Pausing never removes earned trees or saved focus time.

Closing the save popup preserves the unsaved session. Use **Resume Timer** to continue it without counting the waiting/naming gap, or **Reset Timer** to clear only that unsaved timer. Save remains available.

## Build variants

```bash
# Recommended standard package, without Accessibility/app blocking
./gradlew :app:assembleStandard :app:testStandardUnitTest :app:lintStandard
# Separate optional app-blocking package
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

`-PstandardQa` creates the isolated permission-reduced QA app; the AndroidX instrumentation runner executes `ComposeMvpTest`.

### Compose device tests

Use a disposable emulator installation with the `.qa` application ID:

```sh
./gradlew -PstandardQa -PcomposeQa :app:assembleStandardQa :app:assembleStandardQaAndroidTest
adb -s emulator-5556 install -r app/build/outputs/apk/standardQa/app-standardQa.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/standardQa/app-standardQa-androidTest.apk
adb -s emulator-5556 shell pm grant com.cybercat.stopwatch.qa android.permission.POST_NOTIFICATIONS
adb -s emulator-5556 shell am instrument -w -e class com.cybercat.stopwatch.ComposeMvpTest com.cybercat.stopwatch.qa.test/androidx.test.runner.AndroidJUnitRunner
```

These tests use Compose semantics to exercise actual start/pause/resume/save/rename, calendar totals, profile/settings, automatic Pomodoro saving, background timing, and the standard package’s absence of the Accessibility blocker. The older custom View-based instrumentation is historical; use these Compose tests for the current UI.
