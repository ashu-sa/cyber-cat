#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
APP="../../Focanopy Growth Simulator.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
swiftc -parse-as-library Sources/MochiStopwatch/Model.swift Sources/MochiStopwatch/Cloud.swift Sources/MochiStopwatch/AccountView.swift Sources/MochiStopwatch/Views.swift Tools/GrowthSimulator.swift -o "$APP/Contents/MacOS/GrowthSimulator"
cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd"><plist version="1.0"><dict><key>CFBundleExecutable</key><string>GrowthSimulator</string><key>CFBundleIdentifier</key><string>app.focanopy.growth-simulator</string><key>CFBundleName</key><string>Focanopy Growth Simulator</string><key>CFBundlePackageType</key><string>APPL</string><key>CFBundleIconFile</key><string>AppIcon</string><key>LSMinimumSystemVersion</key><string>14.0</string></dict></plist>
PLIST
if [[ -f "Focanopy.app/Contents/Resources/AppIcon.icns" ]]; then cp "Focanopy.app/Contents/Resources/AppIcon.icns" "$APP/Contents/Resources/AppIcon.icns"; fi
codesign --force --sign - "$APP"
open "$APP"
