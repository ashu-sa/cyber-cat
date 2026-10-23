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
    return bool(store.phase=='running' and (not store.pomodoro_enabled or store.pomodoro.stage=='focus') and store.settings.get('focus_blocking',False))

def windows_app_blocked(path,selected,excluded,all_apps):
    import ntpath
    normalize=lambda value:ntpath.normcase(ntpath.abspath(value))
    name=normalize(path);base=ntpath.basename(name)
    protected={'explorer.exe','taskmgr.exe','winlogon.exe','logonui.exe','focanopy.exe','cybercat.exe','systemsettings.exe','consent.exe','applicationframehost.exe','shellexperiencehost.exe','searchhost.exe','startmenuexperiencehost.exe','textinputhost.exe'}
    browsers={'chrome.exe','msedge.exe','firefox.exe','brave.exe','opera.exe','vivaldi.exe','librewolf.exe','zen.exe'}
    if base in protected or name in map(normalize,excluded):return False
    return base not in browsers if all_apps else name in map(normalize,selected)

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

def app_inventory(extra=()):
    """Names/icons for user-facing apps; no executables are launched."""
    import configparser
    import json
    import ntpath
    found={}
    browser_exes={'chrome.exe','msedge.exe','firefox.exe','brave.exe','opera.exe','vivaldi.exe','librewolf.exe','zen.exe'}
    if sys.platform=='win32':
        script="""[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new(); $shell=New-Object -ComObject WScript.Shell; $roots=@([Environment]::GetFolderPath('StartMenu'),[Environment]::GetFolderPath('CommonStartMenu')); @($roots | ForEach-Object {Get-ChildItem -LiteralPath $_ -Filter *.lnk -Recurse -ErrorAction SilentlyContinue} | ForEach-Object {$s=$shell.CreateShortcut($_.FullName); if($s.TargetPath -like '*.exe'){[pscustomobject]@{id=$s.TargetPath;name=$_.BaseName}}}) | ConvertTo-Json -Compress"""
        try:
            result=subprocess.run(['powershell','-NoProfile','-NonInteractive','-Command',script],capture_output=True,encoding='utf-8-sig',timeout=8,check=True)
            entries=json.loads(result.stdout or '[]');entries=[entries] if isinstance(entries,dict) else entries
            for entry in entries:
                path=entry['id'];base=ntpath.basename(path).lower()
                if base in {'focanopy.exe','cybercat.exe','explorer.exe','taskmgr.exe','systemsettings.exe','uninstall.exe','unins000.exe'}:continue
                found[os.path.normcase(path)]=dict(id=path,name=entry['name'],icon=path,browser=base in browser_exes)
        except (OSError,ValueError,subprocess.SubprocessError):pass
        for path in extra:
            found.setdefault(os.path.normcase(path),dict(id=path,name=ntpath.basename(path),icon=path,browser=ntpath.basename(path).lower() in browser_exes))
    else:
        roots=[Path.home()/'.local/share/applications',Path('/usr/local/share/applications'),Path('/usr/share/applications')]
        for identifier,name in desktop_apps():
            entry={}
            for root in roots:
                path=root/identifier
                if path.exists():
                    config=configparser.ConfigParser(interpolation=None,strict=False)
                    try:config.read(path,encoding='utf-8');entry=config['Desktop Entry'];break
                    except (OSError,KeyError,configparser.Error):continue
            mime=entry.get('MimeType','')
            found[identifier]=dict(id=identifier,name=name,icon=entry.get('Icon','application-x-executable'),browser='x-scheme-handler/http' in mime)
        for identifier in extra:found.setdefault(identifier,dict(id=identifier,name=identifier.removesuffix('.desktop'),icon='application-x-executable',browser=False))
    return sorted(found.values(),key=lambda app:app['name'].lower())

class FocusBlocker:
    def __init__(self,store,notice):
        self.store=store;self.notice=notice;self.last=0;self.last_write=0
        self.policy=store.directory/'focus-policy.json'
    def tick(self):
        s=self.store; enabled=active(s); chosen=s.settings.get('blocked_apps',[])
        if sys.platform.startswith('linux'):
            now=s.wall()
            policy=dict(active=enabled,apps=chosen,all=s.settings.get("focus_block_all",False),excluded=s.settings.get("excluded_apps",[]),expires=now+10 if enabled else 0)
            if enabled and now-self.last_write<3:return
            if not enabled and self.last_write==0:return
            atomic_json(self.policy,policy);self.last_write=now if enabled else 0
        elif sys.platform=='win32' and enabled:
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
                if windows_app_blocked(name,chosen,s.settings.get('excluded_apps',[]),s.settings.get('focus_block_all',False)):
                    u.ShowWindowAsync(window,6)
                    if time.monotonic()-self.last>10:self.last=time.monotonic();self.notice('Flower focus','App minimized. Pause the timer or turn blocking off to allow it.')
            except OSError:pass
    def close(self):
        if sys.platform.startswith('linux') and self.policy.exists():atomic_json(self.policy,dict(active=False,apps=[],expires=0))
