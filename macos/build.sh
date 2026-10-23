#!/bin/zsh
set -euo pipefail
cd "${0:A:h}"
BUILD_ARGS=(-c release)
if [[ "${CYBERCAT_UNIVERSAL:-0}" == "1" ]]; then BUILD_ARGS+=(--arch arm64 --arch x86_64); fi
swift build "${BUILD_ARGS[@]}"
BIN_DIR=$(swift build "${BUILD_ARGS[@]}" --show-bin-path)
APP="../Focanopy.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN_DIR/MochiStopwatch" "$APP/Contents/MacOS/MochiStopwatch"
ICON_TEMP=$(mktemp -d)
swiftc -parse-as-library Tools/RenderIcon.swift -o "$ICON_TEMP/render-icon"
"$ICON_TEMP/render-icon" "$ICON_TEMP/AppIcon.iconset"
iconutil -c icns "$ICON_TEMP/AppIcon.iconset" -o "$APP/Contents/Resources/AppIcon.icns"
rm -rf "$ICON_TEMP"
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>CFBundleExecutable</key><string>MochiStopwatch</string>
<key>CFBundleIdentifier</key><string>local.mochi.stopwatch</string>
<key>CFBundleName</key><string>Focanopy</string>
<key>CFBundleIconFile</key><string>AppIcon</string>
<key>CFBundlePackageType</key><string>APPL</string>
<key>CFBundleShortVersionString</key><string>1.8.0</string>
<key>LSMinimumSystemVersion</key><string>14.0</string>
<key>LSUIElement</key><true/>
<key>NSHighResolutionCapable</key><true/>
</dict></plist>
PLIST
codesign --force --deep --sign - "$APP"
