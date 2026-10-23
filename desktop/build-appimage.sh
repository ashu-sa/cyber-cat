#!/usr/bin/env bash
# Native builds: Ubuntu 22.04 x86-64; Ubuntu 24.04 ARM64.
set -euo pipefail
cd "$(dirname "$0")"
[[ "$(uname -s)" == Linux ]] || { echo 'Build on native Linux.' >&2; exit 1; }
image_arch=$(uname -m)
case "$image_arch" in
    x86_64)
        tool_sha=ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0
        runtime_sha=2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d
        archive=Focanopy-Linux.tar.gz ;;
    aarch64)
        tool_sha=f0837e7448a0c1e4e650a93bb3e85802546e60654ef287576f46c71c126a9158
        runtime_sha=00cbdfcf917cc6c0ff6d3347d59e0ca1f7f45a6df1a428a0d6d8a78664d87444
        archive=Focanopy-Linux-aarch64.tar.gz ;;
    *) echo "Unsupported architecture: $image_arch" >&2; exit 1 ;;
esac
./build-linux.sh
appdir="$PWD/dist/Focanopy.AppDir"
rm -rf "$appdir"
mkdir -p "$appdir/usr/lib/focanopy" "$appdir/usr/share/applications" "$appdir/usr/share/icons/hicolor/256x256/apps"
cp -a dist/Focanopy/. "$appdir/usr/lib/focanopy/"
cp packaging/linux/AppRun "$appdir/AppRun"
cp packaging/linux/app.focanopy.Focanopy.desktop "$appdir/"
cp packaging/linux/app.focanopy.Focanopy.desktop "$appdir/usr/share/applications/"
cp ../docs/screenshots/app-icon.png "$appdir/app.focanopy.Focanopy.png"
cp ../docs/screenshots/app-icon.png "$appdir/usr/share/icons/hicolor/256x256/apps/app.focanopy.Focanopy.png"
# Bundle XCB helper libraries often absent on a fresh Fedora workstation.
for lib in libxcb-cursor.so.0 libxcb-image.so.0 libxcb-keysyms.so.1 libxcb-render-util.so.0 libxcb-icccm.so.4 libxcb-xinerama.so.0 libxcb-xkb.so.1 libxkbcommon-x11.so.0 libxkbcommon.so.0 libxcb-shape.so.0 libxcb-randr.so.0 libxcb-xfixes.so.0 libxcb-sync.so.1 libxcb-render.so.0 libxcb-shm.so.0 libxcb.so.1 libX11-xcb.so.1 libxcb-util.so.1; do
    source_lib=$(ldconfig -p | awk -v lib="$lib" '$1==lib && !found {print $NF;found=1}')
    [[ -n "$source_lib" ]] || { echo "Missing build dependency: $lib" >&2; exit 1; }
    cp -L "$source_lib" "$appdir/usr/lib/focanopy/_internal/$lib"
done
# Keep the tar fallback on the same bundled-library baseline.
cp -a "$appdir/usr/lib/focanopy/." dist/Focanopy/
tar -czf "dist/$archive" -C dist Focanopy
desktop-file-validate "$appdir/app.focanopy.Focanopy.desktop"
cache="$PWD/.appimage-tools"
mkdir -p "$cache"
curl --fail --location --retry 3 "https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-$image_arch.AppImage" -o "$cache/appimagetool-$image_arch"
curl --fail --location --retry 3 "https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-$image_arch" -o "$cache/runtime-$image_arch"
printf '%s  %s\n' "$tool_sha" "$cache/appimagetool-$image_arch" "$runtime_sha" "$cache/runtime-$image_arch" | sha256sum --check
chmod +x "$cache/appimagetool-$image_arch"
ARCH="$image_arch" "$cache/appimagetool-$image_arch" --appimage-extract-and-run --runtime-file "$cache/runtime-$image_arch" "$appdir" "$PWD/dist/Focanopy-Linux-$image_arch.AppImage"
chmod +x "dist/Focanopy-Linux-$image_arch.AppImage"
(cd dist && sha256sum "Focanopy-Linux-$image_arch.AppImage" "$archive" > "SHA256SUMS-Linux-$image_arch.txt")
printf 'Built dist/Focanopy-Linux-%s.AppImage\n' "$image_arch"
