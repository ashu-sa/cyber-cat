# Focanopy — Windows and Linux desktop version

A real desktop app made with Python + **Qt Widgets (PySide6 Essentials)**. No Electron, browser or web view; account sync is optional. This is a portable implementation of the SwiftUI macOS companion; the existing SwiftUI app is unchanged.

## Fedora GNOME / portable Linux

Use the [ARM64 AppImage](https://github.com/ashu-sa/cyber-cat/releases/latest/download/Focanopy-Linux-aarch64.AppImage) or [Intel/AMD AppImage](https://github.com/ashu-sa/cyber-cat/releases/latest/download/Focanopy-Linux-x86_64.AppImage) for Fedora and other modern Linux desktops. Both bundle Python and Qt and include a no-FUSE fallback. ARM64 requires glibc 2.39+; Intel/AMD requires glibc 2.35+. See the [Fedora GNOME installation guide](LINUX.md) for permissions, shortcuts, XWayland/native Wayland behaviour, and package requirements.

## What is implemented

- Transparent 150 × 100 logical-pixel floating tree with gentle sway, slow water droplets, and elapsed-time growth.
- Compact square icon buttons, accurate stopwatch, pause/resume, and recovery after closing/restarting.
- Local completed history with editable names, delete confirmation and Undo.
- Month calendar with daily totals (`2h`, `1h 15m`), selection and Day/Week/Month detail totals.
- Editable category dropdown: Job Apply, Upskill, Interview Prep, Work, or any typed name.
- Close naming popup without discarding; successful save automatically opens history at that session’s date.
- Position memory with screen-bound clamping; opacity and animation settings.
- Hide/Close only hides the pet. Quit has Save/Discard/Cancel protection.
- Single instance and local IPC: launching again shows the existing pet, without a duplicate timer.

## Run on Windows

Install **64-bit Python 3.12** from python.org, extract this folder, then double-click **run-windows.cmd**. The first launch installs the pinned Qt runtime in a local `.venv`; later launches reuse it. Internet is needed for that initial dependency install. Python 3.10+ may also work; Python 3.12 is the build target.

The app appears as a pet and system tray icon. Right-click the tree for options. Ctrl+R toggles it globally. **Ctrl+S starts/resumes, Ctrl+P pauses, and Ctrl+F finishes only while the pet is visible.** These combinations temporarily replace Save/Print/Find in other apps while visible. Hide the pet to release S/P/F. Ctrl+R remains registered until Quit. Native Windows hotkey registration requires no keyboard-monitoring permission; conflicts are reported through the tray.

## Run on Ubuntu / GNOME

Target Ubuntu 24.04+ x86-64 with Python 3.12. Install Python venv support and the Qt runtime dependencies:

```sh
sudo apt install python3-venv libegl1 libopengl0 libxcb-cursor0 libxkbcommon-x11-0 libxcb-icccm4 libxcb-image0 libxcb-keysyms1 libxcb-render-util0 libxcb-xinerama0
./run-linux.sh
```

On a clean minimal Linux installation additional display-driver/XCB libraries may be required by Qt. A working graphical desktop session is required. Qt uses native Wayland when available and XCB under X11.

### GNOME global Ctrl shortcuts

Right-click the pet and choose **Enable GNOME Ctrl shortcuts** once. This configures only Focanopy’s own GNOME custom keyboard shortcut entries; existing entries are retained. It works through GNOME’s shortcut system on **both Wayland and X11**, without trying to intercept keyboard events through Wayland.

- Ctrl+R: show/hide; also launches the app when stopped.
- Ctrl+S/P/F: start/resume, pause, finish; their bindings are enabled only while the pet is visible and not in a stopwatch modal dialog.
- Remove GNOME shortcuts: removes only this app’s custom entries.

Keep this folder (and its `.venv`) at its installed location after enabling shortcuts. If moved, enable them again to update the command paths. Bindings update through GNOME settings; session timing does not depend on those updates. A forced kill can leave GNOME bindings active until the app is restarted or the entries are removed in Settings → Keyboard → Custom Shortcuts. Normal Quit releases S/P/F; R remains a launcher.

On non-GNOME Linux desktops, the UI and local shortcuts work, but global shortcuts are not supplied by this build. Configure desktop-specific shortcuts to run `python -m cybercat --command R` (or S/P/F) if desired. Unlike the integrated GNOME setup, a manually assigned binding is not automatically released while hidden; commands safely no-op for S/P/F when hidden.

### Ubuntu desktop limitations

Wayland compositors decide whether apps may position windows or stay above other apps/across workspaces. The pet requests always-on-top and remembers its position, but GNOME may restrict placement. GNOME does not always expose legacy tray icons; the pet’s menu contains all commands including Quit, so no tray extension is required. Ctrl+R can restore the pet after it is hidden once GNOME shortcut setup is enabled. The Windows/Linux tray shows elapsed time in its tooltip and open menu; those platforms do not have macOS-style text menu bar extras.

## Build distributable app folders

On Windows, run **build-windows.cmd** → `dist/Focanopy-Windows.zip`.

On Ubuntu, run **./build-linux.sh** → `dist/Focanopy-Linux.tar.gz`.

These scripts create a PyInstaller application folder bundling Python and Qt. End users of the built folder do not need Python. Keep the whole extracted folder together. Builds are **unsigned** portable app folders, not MSI/DEB installers. Build each package on its target OS; a macOS build is not a Windows or Linux executable.

The repository workflow `.github/workflows/build.yml` builds packages on Windows and Ubuntu runners. Run “Build and package all platforms” manually to create build artifacts. Verified artifacts are published separately to Releases. Download prebuilt packages from the repository’s Releases page.

## Data and macOS migration

- Windows: `%LOCALAPPDATA%\CyberCat`
- Linux: `${XDG_DATA_HOME:-~/.local/share}/cybercat`
- macOS test runtime: `~/Library/Application Support/CyberCatDesktop`

The files are `sessions.json`, `active.json`, and `settings.json`. JSON writes use replacement in the same directory. Files contain task names/times and preferences only. There is no sync or analytics.

To migrate existing Swift app sessions, copy its `~/Library/Application Support/MochiStopwatch/sessions.json` to the target computer, open History → **Import macOS history…**, and select the file. Import converts Apple’s date epoch and skips duplicate session IDs. The Swift data is not edited. Live unsaved timers are not imported.

Live elapsed time uses Linux `CLOCK_BOOTTIME` or Windows `GetTickCount64`, including sleep, and excludes paused intervals. Recovery after process exit uses wall-clock time from the last checkpoint, so manually changing the system clock while closed can affect recovered intervals. Totals belong to each session’s local start date; weeks start Monday in this version.

## Verification and current build status

```sh
python -m unittest discover -s tests -v
```

Model and offscreen native-widget tests pass on this Mac: timing, pause/resume, recovery, atomic-write failure, history editing, import/deduplication, visibility-scoped commands, calendar/options construction, Close preserving a finished session, and Save opening history. Windows hotkey scope is also tested with mocked OS registration.

Windows and Linux packages are built on their target GitHub runners and available on the repository’s Releases page. Native-widget tests and packaged smoke tests run headlessly. Interactive Windows/Ubuntu-GNOME sessions, real global shortcut integration, and physical sleep/wake have not been tested.

Qt API references: [QCalendarWidget](https://doc.qt.io/qtforpython-6/PySide6/QtWidgets/QCalendarWidget.html), [QSystemTrayIcon](https://doc.qt.io/qtforpython-6/PySide6/QtWidgets/QSystemTrayIcon.html), [Windows RegisterHotKey](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-registerhotkey).

## Shared account (v1.1)

Open Account / Sync from the pet controls, tray or journal. Username + four-digit PIN; saved securely with Windows Credential Manager or Ubuntu Secret Service. A functioning OS vault is required to sign in. Local-only mode still works without one. See [sync details](../backend/README.md).

## Focus forest

The trunk, leaves and crown grow continuously across the full hour. Each completed tree stays visible beside the next sprout. Saved sessions add together per day, including synced sessions from other devices. Eight saved hours in a day earns a Garden badge. Calendar days show time and tree rewards; week/month summaries count garden days. Rewards recalculate after edits/deletions. Pausing stops water and turns the current plant dry and dormant; resuming restores green foliage and watering. Disable animation for a still companion.

Floating controls support dragging from the tree, timer, button surfaces and empty space. Movement beyond the drag threshold suppresses the click action; a regular click/tap still controls the stopwatch. Pausing never removes earned trees or saved focus time.

Closing the save popup preserves the unsaved session. Use **Resume Timer** to continue it without counting the waiting/naming gap, or **Reset Timer** to clear only that unsaved timer. Save remains available.
