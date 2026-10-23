# Focanopy on Fedora GNOME and other Linux desktops

The portable **AppImage** bundles Focanopy, Python, Qt, fonts, and XCB helper libraries. No Python installation or virtual environment is needed on the target machine.

[Download ARM64 / aarch64](https://github.com/ashu-sa/cyber-cat/releases/latest/download/Focanopy-Linux-aarch64.AppImage) · [Download Intel/AMD x86-64](https://github.com/ashu-sa/cyber-cat/releases/latest/download/Focanopy-Linux-x86_64.AppImage) · [Releases and checksums](https://github.com/ashu-sa/cyber-cat/releases/latest)

## Requirements

Check `uname -m` and choose the matching package:

| CPU | AppImage | Minimum runtime | Examples |
|---|---|---|---|
| ARM64 (`aarch64`) | `Focanopy-Linux-aarch64.AppImage` | glibc 2.39+ | Fedora 43/44 ARM64, Ubuntu 24.04 ARM64 |
| Intel/AMD (`x86_64`) | `Focanopy-Linux-x86_64.AppImage` | glibc 2.35+ | Fedora 43/44, Ubuntu 22.04/24.04 |

Both require a graphical desktop with its normal graphics, font, and D-Bus libraries. These are separate native binaries; one CPU architecture cannot run the other build without emulation. Alpine/musl and 32-bit Linux are not supported.

The x86-64 package is built on Ubuntu 22.04; the ARM64 package is built natively on Ubuntu 24.04 ARM. CI launches each architecture's packaged AppImage in Fedora 43 and 44 containers, with X11 and native Wayland display backends. Those runtime checks do not replace testing every GNOME desktop configuration or graphics driver.

## Run on Fedora

The commands below are for **ARM64 Fedora**. For Intel/AMD, replace `aarch64` with `x86_64`. Download the matching AppImage, then run these commands from the folder containing it:

```bash
chmod +x Focanopy-Linux-aarch64.AppImage
./Focanopy-Linux-aarch64.AppImage
```

You can also enable **Executable as Program** in the file's Properties and open it from Files. Move the file to a permanent location, such as `~/Applications`, before enabling GNOME shortcuts.

### No FUSE? Run without it

If the AppImage cannot mount, use its built-in extraction mode. This does not need FUSE, root access, or a package installation:

```bash
./Focanopy-Linux-aarch64.AppImage --appimage-extract-and-run
```

For a persistent extracted copy:

```bash
./Focanopy-Linux-aarch64.AppImage --appimage-extract
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
QT_QPA_PLATFORM=xcb ./Focanopy-Linux-aarch64.AppImage
# Or, in a Wayland session:
QT_QPA_PLATFORM=wayland ./Focanopy-Linux-aarch64.AppImage
```

## History and accounts

The AppImage uses the same data and account identifiers as the older Linux archive. Existing history stays in `${XDG_DATA_HOME:-~/.local/share}/cybercat`; replacing the application does not reset it. Credential storage uses the existing OS keyring integration. A guest account and local timing do not need cloud sign-in.

## Tar archive fallback

The [ARM64 tar archive](https://github.com/ashu-sa/cyber-cat/releases/latest/download/Focanopy-Linux-aarch64.tar.gz) and [Intel/AMD tar archive](https://github.com/ashu-sa/cyber-cat/releases/latest/download/Focanopy-Linux.tar.gz) are also built on their respective portable baselines. Extract the entire archive and launch its `Focanopy/Focanopy` executable. Keep the adjacent `_internal` folder. The AppImage is the recommended download because it also provides the display-backend launcher.

## Build and verify

On native Ubuntu 22.04 x86-64 or Ubuntu 24.04 ARM64, install the native dependencies listed in [the portable build workflow](../.github/workflows/linux-portable.yml), then run:

```bash
cd desktop
./build-appimage.sh
```

The script verifies pinned AppImage tooling and runtime checksums, packages the native app, and produces these ARM64 files (the Intel/AMD filenames use `x86_64`):

```text
dist/Focanopy-Linux-aarch64.AppImage
dist/Focanopy-Linux-aarch64.tar.gz
dist/SHA256SUMS-Linux-aarch64.txt
```

[Portable build and Fedora runtime checks](https://github.com/ashu-sa/cyber-cat/actions/workflows/linux-portable.yml) run independently of the Android/macOS/Windows packages. The main build workflow also includes portable Linux packaging and Fedora runtime checks.

Packaging references: [AppImage execution and extraction](https://docs.appimage.org/user-guide/run-appimages.html), [FUSE fallback](https://docs.appimage.org/user-guide/troubleshooting/fuse.html), and [Qt Linux requirements](https://doc.qt.io/qt-6/linux.html).
