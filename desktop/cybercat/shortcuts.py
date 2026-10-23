"""Windows native hotkeys and Ubuntu/GNOME custom shortcuts (Wayland compatible)."""
import ast
import ctypes
import getpass
import os
import shlex
import subprocess
import sys
from pathlib import Path
from .host import host_environment
from PySide6.QtCore import QAbstractNativeEventFilter, QTimer

KEYS = {1: 'R', 2: 'S', 3: 'P', 4: 'F'}
BASE = '/org/gnome/settings-daemon/plugins/media-keys/custom-keybindings/cybercat-'
SCHEMA = 'org.gnome.settings-daemon.plugins.media-keys.custom-keybinding:'


def gsettings(*args):
    result = subprocess.run(['gsettings', *args], check=True, text=True, capture_output=True, timeout=5, env=host_environment())
    return result.stdout.strip()


def gnome_available():
    return sys.platform.startswith('linux') and 'GNOME' in os.environ.get('XDG_CURRENT_DESKTOP', '').upper()


def install_gnome():
    paths = gsettings('get', 'org.gnome.settings-daemon.plugins.media-keys', 'custom-keybindings')
    paths = ast.literal_eval(paths.removeprefix('@as '))
    ours = [BASE + key.lower() + '/' for key in KEYS.values()]
    for path in paths:
        if path not in ours:
            binding = gsettings('get', SCHEMA + path, 'binding').strip("'").lower()
            if binding in ('<control>r', '<control>s', '<control>p', '<control>f', '<primary>r', '<primary>s', '<primary>p', '<primary>f'):
                raise RuntimeError('An existing GNOME custom shortcut already uses ' + binding)
    root = str(Path(__file__).resolve().parent.parent)
    if getattr(sys, 'frozen', False):
        command = shlex.quote(os.environ.get('APPIMAGE') or sys.executable)
    else:
        # GSettings commands do not run in a shell; /usr/bin/env supplies module location.
        command = '/usr/bin/env ' + shlex.quote('PYTHONPATH=' + root) + ' ' + shlex.quote(sys.executable) + ' -m cybercat'
    for key, path in zip(KEYS.values(), ours):
        gsettings('set', SCHEMA + path, 'name', 'Focanopy Ctrl+' + key)
        gsettings('set', SCHEMA + path, 'command', command + ' --command ' + key)
        gsettings('set', SCHEMA + path, 'binding', '<Control>r' if key == 'R' else '')
    gsettings('set', 'org.gnome.settings-daemon.plugins.media-keys', 'custom-keybindings', repr(paths + [p for p in ours if p not in paths]))


def remove_gnome():
    paths = ast.literal_eval(gsettings('get', 'org.gnome.settings-daemon.plugins.media-keys', 'custom-keybindings').removeprefix('@as '))
    ours = [BASE + key.lower() + '/' for key in KEYS.values()]
    gsettings('set', 'org.gnome.settings-daemon.plugins.media-keys', 'custom-keybindings', repr([p for p in paths if p not in ours]))
    for path in ours:
        subprocess.run(['gsettings', 'reset-recursively', SCHEMA + path], check=True, timeout=5, env=host_environment())


class Shortcuts(QAbstractNativeEventFilter):
    def __init__(self, app, callback, report, gnome_enabled=False):
        super().__init__()
        self.app, self.callback, self.report = app, callback, report
        self.registered, self.gnome_enabled, self.visible = set(), gnome_enabled, None
        self.finish_allowed=True
        self.timer = QTimer()
        self.timer.setSingleShot(True)
        self.timer.timeout.connect(self.apply_gnome)
        app.installNativeEventFilter(self)
        if sys.platform == 'win32':
            self.user32 = ctypes.WinDLL('user32', use_last_error=True)
            self.user32.RegisterHotKey.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_uint, ctypes.c_uint]
            self.user32.UnregisterHotKey.argtypes = [ctypes.c_void_p, ctypes.c_int]
            self.register(1)

    def register(self, identifier):
        if identifier in self.registered:
            return
        if self.user32.RegisterHotKey(None, identifier, 0x0002 | 0x4000, ord(KEYS[identifier])):
            self.registered.add(identifier)
        else:
            self.report('Ctrl+' + KEYS[identifier] + ' is already in use. Pet buttons still work.')

    def scope(self, visible, modal=False, finish_allowed=True):
        changed=finish_allowed != self.finish_allowed
        self.finish_allowed=finish_allowed
        visible = visible and not modal
        if sys.platform == 'win32':
            for identifier in (2, 3, 4):
                if visible and (identifier!=4 or finish_allowed):
                    self.register(identifier)
                elif identifier in self.registered:
                    self.user32.UnregisterHotKey(None, identifier)
                    self.registered.remove(identifier)
        if self.gnome_enabled and (visible != self.visible or changed):
            self.visible = visible
            self.timer.start(0)

    def apply_gnome(self):
        try:
            for key in ('S', 'P', 'F'):
                gsettings('set', SCHEMA + BASE + key.lower() + '/', 'binding', '<Control>' + key.lower() if self.visible and (key!='F' or self.finish_allowed) else '')
        except Exception as error:
            self.report('GNOME shortcut setup: ' + str(error))

    def nativeEventFilter(self, event_type, message):
        if sys.platform == 'win32':
            from ctypes import wintypes
            msg = wintypes.MSG.from_address(int(message))
            if msg.message == 0x0312 and int(msg.wParam) in self.registered:
                key = KEYS[int(msg.wParam)]
                QTimer.singleShot(0, lambda: self.callback(key))
                return True, 0
        return False, 0

    def close(self):
        if sys.platform == 'win32':
            for identifier in self.registered:
                self.user32.UnregisterHotKey(None, identifier)
        if self.gnome_enabled:
            self.visible = False
            self.apply_gnome()
        self.app.removeNativeEventFilter(self)
