A tiny animated cyber-cat stopwatch for Android, macOS, Windows, and Ubuntu/GNOME.

- Start, pause/resume, and finish sessions; save a preset category or any custom task name.
- Calendar journal with daily totals, Day/Week/Month summaries, renaming, deletion, and Undo.
- Floating cat, adjustable opacity, and animation controls.
- Android foreground chronometer notification and optional floating overlay.
- Desktop show/hide and stopwatch keyboard shortcuts.

## Downloads

- **Android:** install `CyberCat-Android-debug.apk` (Android 8+). This is a debug-signed sideload build.
- **macOS:** extract `CyberCat-macOS-universal.zip` (macOS 14+, Apple Silicon and Intel). The app is locally signed, not Developer ID notarized.
- **Windows:** extract `CyberCat-Windows.zip` and run `CyberCat/CyberCat.exe`. Keep the entire folder together.
- **Ubuntu/Linux:** extract `CyberCat-Linux.tar.gz` and run `CyberCat/CyberCat`. Ubuntu 24.04+ x86-64 is the build target. GNOME global shortcuts require one-click setup in pet controls.

Windows and Linux folders include the Python/Qt runtime; no separate Python installation is needed. These builds are unsigned. Android foreground services and Wayland window positioning follow platform restrictions. Checksums are in `SHA256SUMS.txt`.

Desktop and Android builds were compiled and tested in CI. Android foreground notification, saved history, granted overlay, and denied-overlay fallback were also tested on an Android 15 emulator. Physical Android phones and interactive Windows/GNOME desktop sessions have not been tested.
