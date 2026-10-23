# Focanopy Android 1.11.0 · A calmer place to focus

This release updates the Android app. Desktop packages remain available in v1.10.0.

- A redesigned focus screen with a clear tree-and-timer card, quieter daily metrics and consistent icon-only session controls.
- Stable three-destination navigation: timer transitions update the content without replacing the app shell. Each destination keeps its scroll position while navigating.
- Grouped settings for account, floating grove, appearance, focus permissions and history management.
- Matching dark dialogs, inset scrollable panels, vector icons, visible touch feedback and accessible control descriptions.
- Compact layouts keep timer controls above the tree on short screens, including at larger text sizes.
- Adaptive timer text for narrow screens, larger calendar labels and consistent 48dp control targets.
- Fixed the floating-tree setting remaining stale after permission approval. Back from Journal or Settings returns to Timer.
- Existing offline history, sync, notification timer, app blocking and tree growth remain supported.

Validation: Android unit tests and lint; isolated-device save/rename/delete/undo, notification controls, calendar recreation, JSON export, overlay permission fallback, stable navigation shell and Pomodoro lifecycle checks.
