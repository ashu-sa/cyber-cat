#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -d .venv ]; then python3 -m venv .venv; fi
.venv/bin/python -m pip install -r requirements-build.txt
.venv/bin/python -m PyInstaller --noconfirm --clean --windowed --onedir --name CyberCat --collect-submodules cybercat --add-data "cybercat/assets:cybercat/assets" --copy-metadata PySide6_Essentials --copy-metadata shiboken6 run.py
mkdir -p dist/CyberCat/licenses
.venv/bin/python -m pip show PySide6_Essentials > dist/CyberCat/licenses/PySide6.txt
cp README.md LICENSE THIRD_PARTY.md dist/CyberCat/
tar -czf dist/CyberCat-Linux.tar.gz -C dist CyberCat
printf 'Built dist/CyberCat-Linux.tar.gz\n'
