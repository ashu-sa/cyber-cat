# Focanopy Stopwatch

A tiny native SwiftUI macOS 14+ stopwatch companion. The existing Mochi app has been redesigned as a charcoal robotic cat with electric blue, teal, and purple details. The release is named `Focanopy.app`; its original identifier is retained so existing local history is preserved.

## Run and control

Open `Focanopy.app` in the parent folder. Only a transparent **150 × 100 point** pet appears; macOS scales points to physical pixels on Retina displays. No history window or dashboard opens at launch.

Use the visible Play button to start; it becomes Pause while running and Resume when paused. Finish opens task naming and is disabled until a session starts. An unsaved finished session shows a Save Session button. Click or right-click the cat for Cancel Session, Hide Pet, Close Pet, and History. Drag the cat to move it. Its last position is stored and restored within an available display’s visible bounds.

The menu bar displays `⏱ HH:MM:SS` for an active session and a small idle icon otherwise. All timer controls and explicit Quit App are available there. Starting from the menu bar shows the pet. Hide and Close both hide only the pet; the timer and menu bar remain active. Show Pet reuses the same panel. A native Pet menu also provides visibility controls and a Command-Shift-P Show Pet shortcut when the app is active; this is not a global shortcut.

Finish opens a native naming dialog. Save writes the name, start/end dates, and active elapsed duration to local history. Name Later preserves the finished session, accessible from Name & Save Session. Cancel Session requires confirmation. Quit with any unsaved session offers Save & Quit, Discard & Quit, or Cancel; dismissing naming cancels quitting and preserves the finished session. History opens only when requested, in a compact native window.

## Timing and local data

A `ContinuousClock` measures elapsed time, including system sleep, rather than incrementing a counter. Paused time is excluded. The pet and menu bar use the same formatted value, refreshed once per second; state transitions refresh it immediately. Wake notifications refresh the display. Animation runs briefly on state transitions, never continuously.

History remains at `~/Library/Application Support/MochiStopwatch/sessions.json`. Active sessions are checkpointed atomically on transitions, before sleep, and every 15 seconds while running. Recovery after an interrupted process uses wall-clock time since the checkpoint; changing the system clock while the app is closed can affect that recovered interval. Pet coordinates are saved in the app’s local preferences under `local.mochi.stopwatch`. Optional account sessions sync to Supabase; credentials stay in Keychain. No analytics or web views are used.

The borderless transparent NSPanel uses floating level, all-Spaces, and full-screen auxiliary behavior. macOS controls ordering relative to system surfaces and other full-screen apps.

## Build and tests

Run `./build.sh` with Apple Command Line Tools installed. It compiles a release binary, packages the app in the parent folder, and applies a local ad-hoc signature. The supplied bundle targets Apple Silicon. Distribution to other Macs requires your own Developer ID signing/notarization.

Run `./test.sh` for standalone checks of pause/resume, elapsed time, finished and running recovery, discard, history reload, shared timer formatting, and long durations. XCTest is not required.

The release build and model checks passed. Native UI checks exercised the small pet, Start, Pause, Finish, naming and saving, the quit confirmation and Cancel option, cancellation confirmation, and hiding/restoring a running pet with elapsed time preserved. Full physical sleep/wake and display-disconnection scenarios have not been exercised on this Mac. The code handles them through ContinuousClock, wake refresh, and screen-bound clamping. Verification sessions are visible in the local history.

The visible Play → Pause → Resume → Finish → Save controls were verified in the running app. The idle slogan has been removed.

## Spacious pet options

Click or right-click the cat to open a 280-point-wide native popover. Options have 40-point rows, larger click targets, grouped sections, and an explicit dismiss button. The popover is sized to the available screen height, uses a standard SwiftUI scroll view when necessary, and dismisses on outside click. The floating pet remains 150 × 100 points. The updated release build, visible options, and dismiss control were verified in the running app.

## Global show/hide shortcut

Press **Command-R** to toggle the pet while the app is running, including when another app is in front. This shows/hides the existing panel and never pauses, resets, or discards a session. The shortcut uses native macOS hot-key registration without Accessibility or Input Monitoring permission. The Pet and menu bar menus also expose Show / Hide Pet. If registration is unavailable because another utility owns the shortcut, an error is shown in the menu bar menu and regular Show/Hide actions remain usable.

Command-R is reserved system-wide while this app runs, so other apps’ usual Command-R actions (such as Reload) are replaced. Quitting unregisters the shortcut. The updated release build passed.

The floating pet buttons now show icons only (play, pause, resume, finish, and save). Hover tooltips and VoiceOver labels retain each action’s name. The updated icon-only controls were visually checked in the running release app.

## History management and totals

History now has Day, Week, and Month tabs, previous/next period controls, a Today button, and a date picker. The selected period shows total active duration and session count. Sessions are grouped by day with a daily subtotal; weekly/monthly views show the daily breakdown. Calendar weeks follow macOS locale settings. A session’s entire active duration belongs to its start date, including sessions crossing midnight; paused time is excluded. Only completed saved sessions count.

Use the pencil button to rename a session. Empty names are rejected. Use the trash button to delete after confirmation. The deletion banner provides Undo for the latest deletion while available; deleting another session replaces the previous Undo opportunity. Changes are written atomically to local history and survive relaunch. Existing session timestamps/durations remain intact when renaming. Storage failures leave the saved list unchanged and display an error.

Tests verify rename reload, rejection of blank names, delete/restore persistence, duplicate restore protection, day/week/month boundaries, and weekly totals. Native UI checks verified the period tabs, weekly/monthly totals, rename/save, and delete confirmation.

## Calendar journal redesign

History now opens as a dark charcoal Focus journal with a full month calendar. Each date displays its tracked duration (`2h`, `1h 15m`, `45m`), with subtle cyan intensity indicating more focus time. Today has a small marker and the selected day has an outline. Click a date to inspect its sessions and rename/delete them. Month navigation respects your calendar’s first weekday. The Day/Week/Month selector changes the detail panel’s scope.

Summary cards show Today, Yesterday, This Week, and This Month; clicking a card opens its sessions. The calendar heading reports the total for the browsed month. Totals still use session start dates and exclude pauses. The history window scrolls on shorter screens; only the tiny pet appears on app launch.

The calendar was visually checked with isolated example data showing 2h yesterday, 1h today, and a 10h 30m month. Example data was not added to your actual history. Automated checks cover all twelve 2026 month grids, locale week starts, DST date continuity, and readable duration formatting. To use an isolated data folder for development, set `MOCHI_TEST_DATA_DIRECTORY` when launching the executable; normal launches use the original local history folder.

## Cat animation and opacity

The cat now blinks, gently sways, and wags its tail; the tail moves a little faster during a running session. Drawing is capped at 12 frames per second and pauses when the pet is hidden. macOS Reduce Motion disables the continuous animation. Right-click the cat to adjust **Pet opacity** from 30% to 100%, or switch **Animate cat** off. Both preferences persist between launches. Opacity affects only the floating pet, keeping the controls popover and history readable. Start/Pause/Resume/Finish icons are now 9 points while their clickable button areas remain unchanged.

The release build passed. Native UI checks verified the smaller icons, opacity adjustment, and animation toggle. Default opacity was restored to 100% and animation left enabled after checking.

Pet action buttons now use compact 26 × 26 point squares with subtly rounded corners. The button row wraps its content instead of stretching across the pet. The release build and native visual check passed.

## Visible-pet session shortcuts

While the pet is visible anywhere on screen:

- Command-S starts a session, or resumes a paused session.
- Command-P pauses a running session.
- Command-F finishes a session and opens task naming.

These keys are registered globally only while the floating pet is visible. Hide/Close unregister them immediately; Show registers them again. Their usual Save/Print/Find behavior in other apps is available while the pet is hidden. Command-R continues to toggle visibility globally. Session shortcuts are suspended while native naming, cancellation, and quit confirmation dialogs are open. Repeated Start never resets an active session; Pause is a no-op unless running. A shortcut conflict is reported in the menu bar menu, with buttons remaining available.

The updated release build passed, shortcut actions were inspected in the running app, and the existing paused session was restored without changing elapsed time.

## Session naming dropdown

The native save dialog now has an editable dropdown with Job Apply, Upskill, Interview Prep, and Work. Select a preset or type any custom task name in the same field. Close (or Escape) dismisses the dialog without discarding the finished session; click the pet’s Save icon or use Name & Save Session to return. Closing this dialog during Save & Quit cancels quitting and preserves the session.

The release build passed. Isolated native UI verification checked all four preset options, category selection, custom typing, successful saving, and Close preserving an unsaved finished session. Test records were kept separate from normal local history.

After a successful normal session save, calendar history now opens automatically with that session’s start date selected. This also works if history was previously browsing another month. Closing the naming popup or encountering a save error does not open history. Save & Quit still exits without opening history. Verified in the running app using isolated test data.

## Shared account (v1.1)

Open Account / Sync in the menu bar or journal. Username + four-digit PIN; sessions stay signed in through Keychain. The PIN is never saved. See [sync details](../backend/README.md).

## Focus forest

The trunk, leaves and crown grow continuously across the full hour. Each completed tree stays visible beside the next sprout. Saved sessions add together per day, including synced sessions from other devices. Eight saved hours in a day earns a Garden badge. Calendar days show time and tree rewards; week/month summaries count garden days. Rewards recalculate after edits/deletions. Pausing stops water and turns the current plant dry and dormant; resuming restores green foliage and watering. Disable animation for a still companion.

Floating controls support dragging from the tree, timer, button surfaces and empty space. Movement beyond the drag threshold suppresses the click action; a regular click/tap still controls the stopwatch. Pausing never removes earned trees or saved focus time.

Closing the save popup preserves the unsaved session. Use **Resume Timer** to continue it without counting the waiting/naming gap, or **Reset Timer** to clear only that unsaved timer. Save remains available.
