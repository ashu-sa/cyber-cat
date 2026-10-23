# Focanopy v1.6.0 — Flower focus, room to rest

- Adds optional **Pomodoro** beside the unchanged Free Stopwatch: 25-minute focus and a selectable 5- or 10-minute break.
- A separate pink flowering tree grows continuously and blooms at 25 minutes. Watering stops for breaks. Stretch/move and return reminders guide the cycle.
- Every next focus round waits for you. Early break skipping is explicit; break and pause time never enter saved focus totals. Delayed wakes and recovery cannot silently run extra focus rounds.
- Adds opt-in, device-local app blocking during running Pomodoro focus: Android returns to Home for selected apps after owner-enabled Accessibility permission; macOS hides selected apps; Windows minimizes chosen app windows; Ubuntu/GNOME 45+ uses a bundled optional user-enabled extension. Apps are left running with their data intact. Pauses, breaks and Free Stopwatch allow apps.
- Adds flower preview to the native fast-forward simulator and documents permission/setup requirements in POMODORO.md.

Android device tests verified focus, bloom, break/return reminders, manual next round and focus-only saving. State tests cover recovery and blocking scope; real Android Accessibility and Windows/GNOME enforcement still require owner permission/device validation. Elevated Windows apps may resist minimization. Ubuntu blocking needs the optional GNOME extension; the timer itself does not.

Google sign-in remains pending the previously documented owner OAuth setup. Existing accounts, history, PIN login and signing identities are preserved. Android is debug-signed for testing; desktop packages remain unsigned/ad-hoc signed.
