# Third-party components

This source uses Qt for Python / PySide6 Essentials and Shiboken, available under LGPLv3/GPL/commercial terms. The source application's MIT license does not replace Qt's licenses. PyInstaller has its own license and exception for generated bundles.

- Qt for Python licenses: https://doc.qt.io/qtforpython-6/licenses.html
- Qt source code: https://code.qt.io/cgit/pyside/pyside-setup.git/
- Qt base source: https://code.qt.io/cgit/qt/qtbase.git/
- PyInstaller license: https://pyinstaller.org/en/stable/license.html

The build scripts use a directory bundle so Qt shared libraries remain separate. Retain Qt's supplied license resources and this notice when redistributing. Review the licenses of the exact runtime components included in a distributable.

Bundled Noto Sans and Noto Sans Mono fonts are distributed under the SIL Open Font License 1.1. Full notices are included in `cybercat/assets/OFL-NotoSans.txt` and `OFL-NotoSansMono.txt`. Upstream: https://github.com/google/fonts/tree/main/ofl/notosans and https://github.com/google/fonts/tree/main/ofl/notosansmono.
