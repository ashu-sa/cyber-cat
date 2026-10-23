#!/usr/bin/env bash
# Build on Ubuntu 22.04 x86-64 for glibc 2.35+ desktop distributions.
set -euo pipefail
cd "$(dirname "$0")"
[[ "$(uname -s)" == Linux && "$(uname -m)" == x86_64 ]] || { echo 'Build on x86-64 Linux.' >&2; exit 1; }
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
for lib in libxcb-cursor.so.0 libxcb-image.so.0 libxcb-keysyms.so.1 libxcb-render-util.so.0 libxcb-icccm.so.4 libxcb-xinerama.so.0 libxcb-xkb.so.1 libxkbcommon-x11.so.0; do
    source_lib=$(ldconfig -p | awk -v lib="$lib" '$1==lib && /x86-64/ && !found {print $NF;found=1}')
    [[ -n "$source_lib" ]] || { echo "Missing build dependency: $lib" >&2; exit 1; }
    cp -L "$source_lib" "$appdir/usr/lib/focanopy/_internal/$lib"
done
# Keep the tar fallback on the same bundled-library baseline.
cp -a "$appdir/usr/lib/focanopy/." dist/Focanopy/
tar -czf dist/Focanopy-Linux.tar.gz -C dist Focanopy
desktop-file-validate "$appdir/app.focanopy.Focanopy.desktop"
cache="$PWD/.appimage-tools"
mkdir -p "$cache"
curl --fail --location --retry 3 'https://github.com/AppImage/appimagetool/releases/download/1.9.1/appimagetool-x86_64.AppImage' -o "$cache/appimagetool"
curl --fail --location --retry 3 'https://github.com/AppImage/type2-runtime/releases/download/20251108/runtime-x86_64' -o "$cache/runtime-x86_64"
printf '%s  %s\n' ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0 "$cache/appimagetool" 2fca8b443c92510f1483a883f60061ad09b46b978b2631c807cd873a47ec260d "$cache/runtime-x86_64" | sha256sum --check
chmod +x "$cache/appimagetool"
ARCH=x86_64 "$cache/appimagetool" --appimage-extract-and-run --runtime-file "$cache/runtime-x86_64" "$appdir" "$PWD/dist/Focanopy-Linux-x86_64.AppImage"
chmod +x dist/Focanopy-Linux-x86_64.AppImage
(cd dist && sha256sum Focanopy-Linux-x86_64.AppImage Focanopy-Linux.tar.gz > SHA256SUMS-Linux.txt)
printf 'Built dist/Focanopy-Linux-x86_64.AppImage (Fedora / Ubuntu / compatible glibc 2.35+ desktops)\n'
