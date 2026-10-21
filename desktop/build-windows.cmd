@echo off
cd /d "%~dp0"
if not exist .venv\Scripts\python.exe py -3 -m venv .venv
if errorlevel 1 exit /b 1
.venv\Scripts\python.exe -m pip install -r requirements-build.txt
if errorlevel 1 exit /b 1
.venv\Scripts\python.exe -m PyInstaller --noconfirm --clean --windowed --onedir --name CyberCat --collect-submodules cybercat --collect-submodules keyring.backends --copy-metadata keyring --add-data "cybercat/assets:cybercat/assets" --copy-metadata PySide6_Essentials --copy-metadata shiboken6 run.py
if errorlevel 1 exit /b 1
copy README.md dist\CyberCat\README.md >nul
copy LICENSE dist\CyberCat\LICENSE >nul
copy THIRD_PARTY.md dist\CyberCat\THIRD_PARTY.md >nul
powershell -NoProfile -Command "Compress-Archive -Path 'dist\CyberCat' -DestinationPath 'dist\CyberCat-Windows.zip' -Force"
