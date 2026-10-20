# Cyber Cat

A tiny animated cyber-cat that keeps track of your focus time. Native SwiftUI on macOS, Kotlin on Android, and Qt Widgets on Windows and Linux—no Electron or web views.

[Download the latest release](https://github.com/ashu-sa/cyber-cat/releases/latest) · [Builds](https://github.com/ashu-sa/cyber-cat/actions/workflows/build.yml)

## Download

| Platform | Package | Requirements |
|---|---|---|
| Android | [CyberCat-Android-debug.apk](https://github.com/ashu-sa/cyber-cat/releases/latest/download/CyberCat-Android-debug.apk) | Android 8+; allow notifications. Floating permission is optional. |
| macOS | [CyberCat-macOS-universal.zip](https://github.com/ashu-sa/cyber-cat/releases/latest/download/CyberCat-macOS-universal.zip) | macOS 14+, Intel or Apple Silicon |
| Windows | [CyberCat-Windows.zip](https://github.com/ashu-sa/cyber-cat/releases/latest/download/CyberCat-Windows.zip) | 64-bit Windows; extract the entire folder and run `CyberCat.exe` |
| Ubuntu / Linux | [CyberCat-Linux.tar.gz](https://github.com/ashu-sa/cyber-cat/releases/latest/download/CyberCat-Linux.tar.gz) | Ubuntu 24.04+ x86-64 / compatible Qt runtime libraries |

Windows and Linux packages bundle their runtime. The APK is debug-signed; desktop builds are unsigned or locally signed, without store signing/notarization.

## What it does

- An animated, draggable cyber-cat with compact icon controls, opacity adjustment, and Hide/Show.
- A stopwatch based on elapsed time rather than counting UI ticks; Pause excludes breaks.
- Save with **Job Apply, Upskill, Interview Prep, Work**, or a custom task name.
- A month calendar showing daily totals such as **2h yesterday / 1h today**, with Day/Week/Month details.
- Rename and delete sessions, Undo the latest deletion, and open history automatically after saving.
- Local history and preferences. No account, cloud sync, analytics, or network permission in the Android app.

## Screenshots

The images show the actual platform views rendered with isolated example sessions. macOS images are captured from native AppKit-hosted SwiftUI views; Windows/Linux images are captured from Qt widgets on their respective CI runners. Android is captured from the native app on an Android 15 emulator. Sample time totals are demonstration data, not personal activity.

### Android

<img src="docs/screenshots/android-journal.png" alt="Android calendar journal with session totals" width="280" />

### macOS

<img src="docs/screenshots/macos-pet.png" alt="Tiny macOS floating cyber-cat stopwatch" width="200" />

![macOS focus journal](docs/screenshots/macos-journal.png)

### Windows

<img src="docs/screenshots/windows-pet.png" alt="Windows floating cyber-cat stopwatch" width="180" />

![Windows calendar journal](docs/screenshots/windows-journal.png)

### Ubuntu / Linux

<img src="docs/screenshots/linux-pet.png" alt="Linux floating cyber-cat stopwatch" width="180" />

![Linux calendar journal](docs/screenshots/linux-journal.png)

## Controls

| Action | Windows / Ubuntu-GNOME | macOS |
|---|---|---|
| Show / hide pet | Ctrl+R | ⌘R |
| Start / resume | Ctrl+S | ⌘S |
| Pause | Ctrl+P | ⌘P |
| Finish and name | Ctrl+F | ⌘F |

S/P/F shortcuts are active only while the pet is visible. Hide the pet to release Save/Print/Find back to other apps. Android uses touch and notification controls.

On Ubuntu/GNOME, open pet controls → **Enable GNOME Ctrl shortcuts** once. This works with GNOME Wayland and X11 custom keyboard shortcuts. GNOME/Wayland may restrict exact overlay placement and always-on-top behavior. A tray extension is optional; all commands are available from the pet itself. Other Linux desktops have local shortcuts, with manual global binding support described in [desktop setup](desktop/README.md).

## Source and build instructions

| App | Source | Build |
|---|---|---|
| Android | [`android/`](android/) | `cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` |
| macOS | [`macos/`](macos/) | `cd macos && ./build.sh` (`CYBERCAT_UNIVERSAL=1` for both architectures) |
| Windows | [`desktop/`](desktop/) | `desktop\build-windows.cmd` on Windows |
| Ubuntu/Linux | [`desktop/`](desktop/) | `cd desktop && ./build-linux.sh` on Ubuntu |

[Android guide](android/README.md) · [Desktop guide](desktop/README.md) · [macOS guide](macos/README.md)

The workflow builds each package on its target OS, runs automated checks, and uploads the artifacts. Manual workflow runs create build artifacts; verified packages are published to GitHub Releases with SHA-256 checksums. The Android release uses the same local debug-signing identity as the original APK, rather than replacing it with a fresh CI debug key.

## Data and testing

History stays on your device. The desktop version can import the original macOS JSON history without modifying the source file. See the platform guides for storage locations, recovery behavior, and permissions.

Android notification/overlay behavior has been tested on an Android 15 emulator; physical OEM phones may behave differently. Windows/Linux CI verifies native widgets and packaged application construction, not a fully interactive user desktop. Linux global shortcut registration is covered by scoped/mocked checks; physical sleep/wake behavior is not claimed as tested.

## License

Application source: [MIT](LICENSE). Qt/PySide and bundled desktop dependencies have their own licenses; see [third-party notices](desktop/THIRD_PARTY.md).
