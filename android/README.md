# Focanopy for Android

A native **Kotlin Android app**, with a native growing-tree companion. Optional shared accounts securely sync history across devices. No Electron or web view. Supports **Android 8.0 (API 26) and newer**; targets Android 15 (API 35).

## Install

Copy `Focanopy-Android-debug.apk` to your phone, open it, and permit installation from that source if Android asks. This is a locally debug-signed build for personal testing, not a Play Store release. A production release should be signed with your own release key.

1. Launch Focanopy and allow notifications when asked.
2. Tap ▶ to start. Tap Ⅱ to pause and ▶ to resume.
3. The ongoing notification shows a system-managed live chronometer, with Pause/Resume and Finish actions.
4. Finish opens the naming popup. Choose Job Apply, Upskill, Interview Prep, or Work, or type any custom name.
5. Save opens the calendar journal at the session’s date. Close dismisses naming without discarding the finished session.

Notifications are the default background experience. You do **not** need floating-window permission for stopwatch functionality. On Android 13+, denying notification permission can hide the notification from the shade even while the foreground service is running; use Settings → Notification settings to allow it.

## Optional floating tree

Open **Settings → Enable floating tree…**, grant Android’s **Display over other apps** permission, then return to Focanopy. A small transparent tree appears above ordinary apps:

- Drag the tree to move it; position is remembered.
- ▶ / Ⅱ starts, resumes, or pauses.
- ✓ finishes the session and opens naming.
- × hides the pet without stopping an active session.
- Tap the tree itself to open the app.
- Settings also provides an opacity slider (30–100%), animation switch, and Show/Hide controls.

If the OS does not allow an overlay, notification controls remain available. Sensitive system screens, banking apps, lock screens, some manufacturers, and managed devices may hide overlays. No Accessibility service is requested. If launching the naming screen from the overlay is blocked by Android, the finished session remains available from the notification.

## Journal

A month calendar shows daily active totals (`2h`, `1h 15m`, `45m`). Select a date to inspect its sessions; Day/Week/Month buttons show totals for the selected period. Today/Yesterday and browsed-month totals are displayed. Rename via ✎; delete via × with confirmation and Undo for the most recent deletion. Weeks start Monday; totals use each session’s local start date, excluding pauses.

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

- Debug APK assembled successfully.
- JVM tests cover: pause exclusion, resume, repeated Start protection, simulated long elapsed intervals, formatting, reset.
- Android lint: **0 errors**; warnings are reviewed in the generated report.
- Installed and tested on an **Android 15 / Pixel 8 Pro emulator**.
- On-device instrumentation passed: launch, Start, foreground notification with chronometer, accurate Pause, category-name persistence, saved session in calendar, floating overlay after permission grant, and notification fallback when overlay permission was denied.
- A native-view journal image was inspected for layout. Notification controls and overlay behavior have not been tested on physical phones or manufacturer-modified Android versions. Physical deep-sleep/reboot recovery was not exercised.

The instrumentation runner is included under `app/src/androidTest/` and refuses to run outside the separate `.qa` package. Use a disposable emulator and replace `emulator-5556` with that emulator’s serial. The primary installed app and account are not used:

```sh
./gradlew -PmvpQa :app:assembleQa :app:assembleQaAndroidTest
adb -s emulator-5556 install -r -g app/build/outputs/apk/qa/app-qa.apk
adb -s emulator-5556 install -r app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk
# Clears only the isolated QA app. Never use the primary package here.
adb -s emulator-5556 shell pm clear com.cybercat.stopwatch.qa
adb -s emulator-5556 shell pm grant com.cybercat.stopwatch.qa android.permission.POST_NOTIFICATIONS
adb -s emulator-5556 shell am instrument -w com.cybercat.stopwatch.qa.test/com.cybercat.stopwatch.SmokeInstrumentation
adb -s emulator-5556 shell am force-stop com.cybercat.stopwatch.qa
adb -s emulator-5556 shell am instrument -w -e mode recovery com.cybercat.stopwatch.qa.test/com.cybercat.stopwatch.SmokeInstrumentation
```

The end-to-end runner verifies notification Pause/Resume/Finish, category and manual save, Close/reopen preserving the name, rename/delete/undo, calendar selection after activity recreation, export, granted overlays and denied-permission notification fallback. The second invocation checks the paused checkpoint and saved history after process termination. `-PforestQa` selects the separate native screenshot/milestone runner. No test account or cloud credentials are needed.

## Android references

- [Foreground-service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Draw-over-other-apps permission](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_OVERLAY_PERMISSION)
- [Monotonic clock including deep sleep](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtime())

## Shared account (v1.1)

Settings → Account / Sync supports username + four-digit PIN. Credentials persist using Android Keystore encryption; the PIN is not stored. INTERNET permission is used for optional sync. Existing sessions upload only on opt-in. See [backend guide](../backend/README.md).

## Focus forest

A young plant grows at 30 minutes, with one completed tree for each full focus hour. Saved sessions add together per day, including synced sessions from other devices. Eight saved hours in a day earns a Garden badge. Calendar days show time and tree rewards; week/month summaries count garden days. Rewards recalculate after edits/deletions. Pausing freezes water and sway; disable animation for a still companion.

## Android MVP (v1.3)

Android is Focanopy’s flagship application. The home screen includes saved daily time, tree counts, the live companion and progress to the next tree. Persistent bottom navigation opens Timer, Journal and Settings. Notification permissions and optional floating controls are visible in Settings; neither overlay permission nor an account is required for core focus timing.

Settings → **Export session history** opens Android’s document picker to save the visible history as JSON. This export contains task names, times, durations and device metadata; credentials and PINs are never exported. The save dialog retains the task name in private app preferences until saved or discarded. Package ID and Keystore aliases retain the original namespace for upgrade compatibility.
