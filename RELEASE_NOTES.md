# Focanopy 1.7.0 · Stronger focus, simpler controls

- Stopwatch opens by default. Pomodoro is one optional effect toggle, without a separate Free Stopwatch choice.
- App blocking now works during the ordinary running stopwatch as well as Pomodoro focus.
- Select apps to block, or block all apps except browsers, protected system essentials and your allowed exclusions.
- macOS checks bundle identifiers and application paths (including Android Studio Java helpers), checks foreground apps more often while blocking is active, and adds a full-screen blocking shield with Pause/Disable controls. Optional Accessibility permission enables window minimization.
- Android app-blocking controls are always available on the Timer tab. Enable the Focanopy Accessibility service yourself; blocking returns blocked launchable apps to Home without reading screen content or typing.
- Windows and GNOME receive the same strategy/exclusion options. GNOME policies expire after a crash; system recovery controls remain protected.
- Pauses, Pomodoro breaks and finished sessions release blocking. Existing timers, accounts and saved history retain their storage formats.

App blocking is a reversible focus aid, not an OS security lockdown. Windows protected apps may refuse minimization. GNOME requires its optional extension. Android requires Accessibility permission. See POMODORO.md for setup and limits.
