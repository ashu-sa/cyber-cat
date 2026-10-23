# Focanopy on Fedora GNOME and other Linux desktops

The portable **x86-64 AppImage** bundles Focanopy, Python, Qt, fonts, and XCB helper libraries. No Python installation or virtual environment is needed on the target machine.

[Download the Linux AppImage](https://github.com/ashu-sa/cyber-cat/releases/latest/download/Focanopy-Linux-x86_64.AppImage) · [Releases and checksums](https://github.com/ashu-sa/cyber-cat/releases/latest)

## Requirements

- An **Intel or AMD x86-64** desktop. `uname -m` should print `x86_64`. This is not an ARM64 or 32-bit build.
- A modern glibc-based Linux distribution with **glibc 2.35 or newer**, such as Fedora 43/44 or Ubuntu 22.04/24.04.
- A graphical desktop with its normal graphics, font, and D-Bus libraries. Alpine/musl is not supported.

The package is built on Ubuntu 22.04 to avoid requiring Ubuntu 24.04's newer glibc. CI launches the same packaged AppImage in Fedora 43 and 44 containers, with X11 and native Wayland display backends. Those runtime checks do not replace testing every GNOME desktop configuration or graphics driver.

## Run on Fedora

Download the AppImage, then run these commands from the folder containing it:

```bash
chmod +x Focanopy-Linux-x86_64.AppImage
./Focanopy-Linux-x86_64.AppImage
```

You can also enable **Executable as Program** in the file's Properties and open it from Files. Move the file to a permanent location, such as `~/Applications`, before enabling GNOME shortcuts.

### No FUSE? Run without it

If the AppImage cannot mount, use its built-in extraction mode. This does not need FUSE, root access, or a package installation:

```bash
./Focanopy-Linux-x86_64.AppImage --appimage-extract-and-run
```

For a persistent extracted copy:

```bash
./Focanopy-Linux-x86_64.AppImage --appimage-extract
./squashfs-root/AppRun
```

Keep the whole `squashfs-root` folder. AppRun starts the bundled application; it is not an installer.

## GNOME controls

- The tree is a normal native Qt floating window. Drag it to move it; open its controls to view the journal or settings.
- The launcher prefers **XWayland** when `DISPLAY` is available, which supports manual positioning better. If only a native Wayland display is available, it uses Qt's Wayland backend. GNOME/compositor policy still controls placement, visibility across workspaces, and stacking.
- GNOME may not display a system-tray icon by default. The floating tree and its context menu remain the entry point.
- In Focanopy's floating controls, choose **Enable GNOME Ctrl shortcuts** once. Ctrl+R shows/hides the tree. Ctrl+S starts/resumes, Ctrl+P pauses, and Ctrl+F finishes an ordinary timer session while the tree is visible. Pomodoro uses its cancellation controls.
- The shortcuts point to the permanent AppImage path, not a temporary mount. If you move or rename the AppImage, enable the shortcuts again.
- App blocking is optional and requires GNOME 45+ plus the user-enabled Focanopy extension. See [Pomodoro and app blocking](../POMODORO.md). It is not silently enabled by installing the app.

To explicitly choose a backend for troubleshooting:

```bash
QT_QPA_PLATFORM=xcb ./Focanopy-Linux-x86_64.AppImage
# Or, in a Wayland session:
QT_QPA_PLATFORM=wayland ./Focanopy-Linux-x86_64.AppImage
```

## History and accounts

The AppImage uses the same data and account identifiers as the older Linux archive. Existing history stays in `${XDG_DATA_HOME:-~/.local/share}/cybercat`; replacing the application does not reset it. Credential storage uses the existing OS keyring integration. A guest account and local timing do not need cloud sign-in.

## Tar archive fallback

The [Linux tar archive](https://github.com/ashu-sa/cyber-cat/releases/latest/download/Focanopy-Linux.tar.gz) is also built on the portable baseline. Extract the entire archive and launch its `Focanopy/Focanopy` executable. Keep the adjacent `_internal` folder. The AppImage is the recommended download because it also provides the display-backend launcher.

## Build and verify

On x86-64 Ubuntu 22.04, install the native dependencies listed in [the portable build workflow](../.github/workflows/linux-portable.yml), then run:

```bash
cd desktop
./build-appimage.sh
```

The script verifies pinned AppImage tooling and runtime checksums, packages the native app, and produces:

```text
dist/Focanopy-Linux-x86_64.AppImage
dist/Focanopy-Linux.tar.gz
dist/SHA256SUMS-Linux.txt
```

[Portable build and Fedora runtime checks](https://github.com/ashu-sa/cyber-cat/actions/workflows/linux-portable.yml) run independently of the Android/macOS/Windows packages. The main build workflow also includes portable Linux packaging and Fedora runtime checks.

Packaging references: [AppImage execution and extraction](https://docs.appimage.org/user-guide/run-appimages.html), [FUSE fallback](https://docs.appimage.org/user-guide/troubleshooting/fuse.html), and [Qt Linux requirements](https://doc.qt.io/qt-6/linux.html).
