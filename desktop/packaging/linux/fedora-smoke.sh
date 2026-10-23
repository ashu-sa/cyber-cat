#!/usr/bin/env bash
# Disposable Fedora container: real Qt display backends; never uses personal data.
set -euo pipefail
dnf install -y glibc-langpack-en libglvnd-egl libglvnd-opengl mesa-dri-drivers libxkbcommon-x11 xcb-util-cursor libX11 libXrender libXext libSM libICE fontconfig dbus-x11 xorg-x11-server-Xvfb xorg-x11-xauth weston
mkdir -p /tmp/focanopy-check
cp "/artifacts/${APPIMAGE_NAME:-Focanopy-Linux-x86_64.AppImage}" /tmp/focanopy-check/Focanopy.AppImage
chmod +x /tmp/focanopy-check/Focanopy.AppImage
export LANG=en_US.UTF-8
cd /tmp/focanopy-check
sha256sum Focanopy.AppImage
# Exercise the documented no-FUSE path and XWayland-compatible Qt platform.
CYBERCAT_DATA_DIRECTORY=/tmp/focanopy-x11 xvfb-run -a ./Focanopy.AppImage --appimage-extract-and-run --smoke-test
# A native Wayland compositor catches absent/mispackaged Qt Wayland plugins.
export XDG_RUNTIME_DIR=/tmp/focanopy-runtime
mkdir -p "$XDG_RUNTIME_DIR"; chmod 700 "$XDG_RUNTIME_DIR"
weston --backend=headless --socket=wayland-focanopy --idle-time=0 --no-config --log=/tmp/weston.log &
compositor_pid=$!
trap 'kill "$compositor_pid" 2>/dev/null || true' EXIT
for attempt in {1..100}; do [[ -S "$XDG_RUNTIME_DIR/wayland-focanopy" ]] && break; sleep 0.1; done
[[ -S "$XDG_RUNTIME_DIR/wayland-focanopy" ]] || { cat /tmp/weston.log; exit 1; }
env -u DISPLAY WAYLAND_DISPLAY=wayland-focanopy QT_QPA_PLATFORM=wayland CYBERCAT_DATA_DIRECTORY=/tmp/focanopy-wayland ./Focanopy.AppImage --appimage-extract-and-run --smoke-test
printf 'PASS: Fedora %s frozen AppImage on X11 and native Wayland; no FUSE or host Python required\n' "$(rpm -E %fedora)"
