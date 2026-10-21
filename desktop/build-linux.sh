#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -d .venv ]; then python3 -m venv .venv; fi
.venv/bin/python -m pip install -r requirements-build.txt
.venv/bin/python -m PyInstaller --noconfirm --clean --windowed --onedir --name Focanopy --collect-submodules cybercat --collect-submodules keyring.backends --copy-metadata keyring --add-data "cybercat/assets:cybercat/assets" --copy-metadata PySide6_Essentials --copy-metadata shiboken6 run.py
mkdir -p dist/Focanopy/licenses
.venv/bin/python -m pip show PySide6_Essentials > dist/Focanopy/licenses/PySide6.txt
cp README.md LICENSE THIRD_PARTY.md dist/Focanopy/
tar -czf dist/Focanopy-Linux.tar.gz -C dist Focanopy
printf 'Built dist/Focanopy-Linux.tar.gz\n'
