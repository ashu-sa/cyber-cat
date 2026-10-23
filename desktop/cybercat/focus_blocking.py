"""Opt-in app minimization. Never kills, freezes, or modifies another app's data."""
import os
import sys
import time
import shutil
import subprocess
from pathlib import Path
from .model import atomic_json

UUID='focanopy-focus@ashu-sa'

def active(store):
    return bool(store.pomodoro_enabled and store.phase=='running' and store.pomodoro.stage=='focus' and store.settings.get('focus_blocking',False))

def install_gnome_focus():
    result=subprocess.run(['gnome-shell','--version'],capture_output=True,text=True,check=True,timeout=5)
    import re
    version=re.search(r'(\d+)\.',result.stdout)
    if not version or int(version[1])<45: raise RuntimeError('App blocking requires GNOME 45 or later. Free Stopwatch and Pomodoro still work.')
    target=Path(os.environ.get('XDG_DATA_HOME',str(Path.home()/'.local/share')))/'gnome-shell/extensions'/UUID
    shutil.copytree(Path(__file__).parent/'assets/gnome-focus',target,dirs_exist_ok=True)
    return 'Installed. Enable Focanopy Flower Focus in the GNOME Extensions app. If it is not listed, log out and back in first.'

def desktop_apps():
    import configparser
    roots=[Path.home()/'.local/share/applications',Path('/usr/local/share/applications'),Path('/usr/share/applications')]
    apps={}
    for root in roots:
        for path in root.glob('*.desktop'):
            try:
                config=configparser.ConfigParser(interpolation=None,strict=False);config.read(path,encoding='utf-8');entry=config['Desktop Entry']
                if entry.get('Type')!='Application' or entry.get('NoDisplay','false')=='true' or entry.get('Hidden','false')=='true':continue
                if path.name in ('org.gnome.Settings.desktop','org.gnome.Extensions.desktop','com.mattjakeman.ExtensionManager.desktop'):continue
                if 'focanopy' in entry.get('Name','').lower():continue
                apps.setdefault(path.name,entry.get('Name',path.stem))
            except Exception:continue
    return sorted(apps.items(),key=lambda pair:pair[1].lower())

class FocusBlocker:
    def __init__(self,store,notice):
        self.store=store;self.notice=notice;self.last=0;self.last_write=0
        self.policy=store.directory/'focus-policy.json'
    def tick(self):
        s=self.store; enabled=active(s); chosen=s.settings.get('blocked_apps',[])
        if sys.platform.startswith('linux'):
            now=s.wall()
            policy=dict(active=enabled,apps=chosen,expires=now+min(10,s.pomodoro.remaining(s.elapsed)) if enabled else 0)
            if enabled and now-self.last_write<3:return
            if not enabled and self.last_write==0:return
            atomic_json(self.policy,policy);self.last_write=now if enabled else 0
        elif sys.platform=='win32' and enabled and chosen:
            try:
                import ctypes
                from ctypes import wintypes
                u=ctypes.WinDLL('user32',use_last_error=True);k=ctypes.WinDLL('kernel32',use_last_error=True)
                u.GetForegroundWindow.restype=wintypes.HWND
                u.GetWindowThreadProcessId.argtypes=[wintypes.HWND,ctypes.POINTER(wintypes.DWORD)]
                k.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];k.OpenProcess.restype=wintypes.HANDLE
                k.QueryFullProcessImageNameW.argtypes=[wintypes.HANDLE,wintypes.DWORD,wintypes.LPWSTR,ctypes.POINTER(wintypes.DWORD)]
                k.CloseHandle.argtypes=[wintypes.HANDLE];u.ShowWindowAsync.argtypes=[wintypes.HWND,ctypes.c_int]
                window=u.GetForegroundWindow();pid=wintypes.DWORD();u.GetWindowThreadProcessId(window,ctypes.byref(pid))
                if pid.value==os.getpid():return
                handle=k.OpenProcess(0x1000,False,pid.value)
                if not handle:return
                try:
                    size=wintypes.DWORD(32768);buffer=ctypes.create_unicode_buffer(size.value)
                    if not k.QueryFullProcessImageNameW(handle,0,buffer,ctypes.byref(size)):return
                    name=os.path.normcase(os.path.abspath(buffer.value))
                finally:k.CloseHandle(handle)
                if name in [os.path.normcase(os.path.abspath(p)) for p in chosen]:
                    u.ShowWindowAsync(window,6)
                    if time.monotonic()-self.last>10:self.last=time.monotonic();self.notice('Flower focus','App minimized. Pause Pomodoro or turn blocking off to allow it.')
            except OSError:pass
    def close(self):
        if sys.platform.startswith('linux') and self.policy.exists():atomic_json(self.policy,dict(active=False,apps=[],expires=0))
