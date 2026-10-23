# Flower Pomodoro

Choose **Free Stopwatch** for the original open-ended timer, or opt into **Pomodoro** for a 25-minute focus round. Existing sessions keep their original mode; finish/save or reset before switching.

- **Android:** Timer → Pomodoro. Choose a 5- or 10-minute break before starting.
- **macOS:** click the floating tree → Free Stopwatch / Pomodoro. The same option is in the menu bar.
- **Windows / Ubuntu:** click the tree → Free Stopwatch / Pomodoro.

The pink flowering tree grows continuously during 25 minutes of focus. At 25 minutes it blooms fully, watering stops, and a reminder asks you to stretch and move a little. The chosen break counts down separately. When it ends, another reminder says your next flower needs water. **The next focus round never starts automatically.** Press Start next focus when ready; an explicit Skip break action can start it earlier.

Manual pauses freeze growth and stop watering. Breaks preserve the healthy completed flower. During the next round a completed flower remains beside the next growing one. Finish and name a session whenever you want; the saved duration contains only focused time across its rounds. Existing journal/calendar hourly tree and garden totals continue to use actual focused time, without treating breaks as work. Flower counts shown on the timer describe the current Pomodoro session.

Focus time is capped at the 25-minute boundary even if the app wakes late or restores after process exit. A late wake cannot silently run additional rounds. Drafts preserve the mode, target, break choice and break deadline. Runtime intervals use monotonic clocks; restoring a break after process exit uses the saved wall-clock deadline, so changing the system clock while the app is closed can affect the restored break countdown.

## Optional app blocking

Blocking is **off by default**, and only applies when all of these are true: Pomodoro is selected, a focus round is running, blocking is enabled, and the app is in your selected list. Free Stopwatch, manual pauses, breaks, waiting for the next round and finished sessions allow apps. Settings and system recovery controls are protected. Apps are never terminated or suspended, and their documents remain open.

- **Android:** Pomodoro → App blocking. Select launchable apps and save. Enable **Focanopy · Pomodoro app blocking** in Android Accessibility settings if you want enforcement. It receives app-window change events and returns to Home when a selected app opens. It requests no screen-content retrieval and does not read typed text. You can pause through the notification, turn blocking off in Focanopy, or disable the service in Android settings. Some Android versions require the owner to allow restricted settings for a sideloaded APK before enabling an Accessibility service; follow Android's own prompts yourself. Without the permission, the timer and reminders still work and the UI says Permission needed.
- **macOS:** enable blocking and choose application bundles in the native picker. Selected apps are hidden when activated during focus. The reminder panel offers Pause focus & allow apps and Turn app blocking off. Finder and system settings are excluded.
- **Windows:** select the `.exe` files for distracting apps. Their foreground windows are minimized during focus. Elevated or protected applications may refuse minimization. Explorer, Task Manager and essential system processes are excluded from the picker.
- **Ubuntu/GNOME 45+:** choose apps, then use Install optional GNOME focus extension. Enable **Focanopy Flower Focus** yourself in GNOME Extensions; after a first install, a logout/login may be needed before GNOME discovers it. The bundled extension minimizes chosen apps through GNOME Shell, including Wayland. It reads only a local, short-lived focus policy. The policy expires within ten seconds if Focanopy exits unexpectedly and never extends past the focus boundary. Use GNOME Extensions to disable/remove it whenever you want. Other Linux desktops can use the timer and reminders without this GNOME-specific blocking integration.

App selections, permissions and blocking preferences are device-local. Turning blocking off or pausing allows apps to be reopened; the app does not automatically reopen hidden/minimized windows.

## Notifications and verification

Allow notifications when prompted if you want background break/return reminders. Android uses its ongoing countdown notification plus a separate Flower focus reminders channel. macOS requests notification permission only when you choose Pomodoro; Windows/Linux use native tray notifications where the desktop supports them.

Automated tests cover capped focus, delayed wakes, break recovery, pause at the boundary, manual next-round behavior, optional blocking scope, expiring GNOME policies and unchanged Free Stopwatch timing. The isolated Android device test exercises focus → bloom → break reminder → ready → manual next focus → focus-only save. Platform builds and native UI/packaged smoke checks run in CI. Android Accessibility enforcement and Windows/GNOME window handling still need real-device/desktop permission validation; the Mac development host cannot stand in for those desktops.
