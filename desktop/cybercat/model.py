"""Storage and clock logic, independent of the desktop toolkit."""
import ctypes
import json
import os
import sys
import tempfile
import time
import uuid
from datetime import datetime
from pathlib import Path
from .pomodoro import Pomodoro


def continuous_time():
    if sys.platform == 'win32':
        fn = ctypes.windll.kernel32.GetTickCount64
        fn.restype = ctypes.c_ulonglong
        return fn() / 1000
    if hasattr(time, 'CLOCK_BOOTTIME'):
        return time.clock_gettime(time.CLOCK_BOOTTIME)
    return time.monotonic()


def data_directory():
    override = os.environ.get('CYBERCAT_DATA_DIRECTORY')
    if override:
        return Path(override)
    if sys.platform == 'win32':
        return Path(os.environ.get('LOCALAPPDATA', str(Path.home()))) / 'CyberCat'
    if sys.platform == 'darwin':
        return Path.home() / 'Library/Application Support/CyberCatDesktop'
    return Path(os.environ.get('XDG_DATA_HOME', str(Path.home() / '.local/share'))) / 'cybercat'


def atomic_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(dir=path.parent, suffix='.tmp')
    try:
        with os.fdopen(fd, 'w', encoding='utf-8') as stream:
            json.dump(data, stream, indent=2, ensure_ascii=False)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def clock_text(value):
    seconds = max(0, int(value))
    return f'{seconds // 3600:02}:{seconds // 60 % 60:02}:{seconds % 60:02}'


def duration_text(value):
    seconds = max(0, int(value))
    hours, minutes = seconds // 3600, seconds // 60 % 60
    if hours:
        return f'{hours}h' + (f' {minutes}m' if minutes else '')
    return f'{minutes}m' if minutes else (f'{seconds}s' if seconds else '0m')


def device_label(record):
    return record.get('device_name') or {'macos': 'Mac', 'android': 'Phone',
        'windows': 'Windows', 'linux': 'Linux'}.get(record.get('platform'), 'This device')


class Store:
    def __init__(self, directory=None, clock=continuous_time, wall=time.time):
        self.directory = Path(directory) if directory else data_directory()
        self.directory.mkdir(parents=True, exist_ok=True)
        self.clock, self.wall = clock, wall
        self.sessions = self.read('sessions.json', [])
        self.settings = self.read('settings.json', {})
        if 'device_id' not in self.settings:
            self.setting('device_id', str(uuid.uuid4()))
        from .cloud import Cloud
        self.cloud = Cloud(self.directory, **({'vault': lambda value=None: None} if directory is not None or os.environ.get('CYBERCAT_DATA_DIRECTORY') else {}))
        if self.cloud.owner: self.sessions = self.cloud.visible()
        self.phase, self.accumulated, self.anchor = 'idle', 0.0, None
        self.started, self.ended, self.session_id = None, None, None
        self.pomodoro_enabled=bool(self.settings.get('pomodoro',False))
        self.pomodoro=Pomodoro(dict(break_minutes=self.settings.get("break_minutes",5)),clock=self.clock,wall=self.wall)
        draft = self.read('active.json', None)
        if draft:
            self.phase, self.started, self.ended = draft['phase'], draft['start'], draft.get('end')
            self.session_id = draft.get('session_id')
            self.accumulated = draft['elapsed']
            self.pomodoro_enabled=draft.get('pomodoro_enabled',self.pomodoro_enabled)
            self.pomodoro=Pomodoro(draft.get('pomodoro'),clock=self.clock,wall=self.wall)
            if self.phase == 'running':
                self.accumulated += max(0, self.wall() - draft['checkpoint'])
                self.anchor = self.clock()
        self.advance_pomodoro()

    def read(self, name, fallback):
        path = self.directory / name
        return json.loads(path.read_text(encoding='utf-8')) if path.exists() else fallback

    @property
    def growth_elapsed(self):
        day = datetime.fromtimestamp(self.started if self.started is not None and self.phase != 'idle' else self.wall()).date()
        active = self.session_id if self.phase != 'idle' else None
        context = (day, active)
        if getattr(self, '_growth_records', None) is not self.sessions or getattr(self, '_growth_context', None) != context:
            self._growth_records, self._growth_context = self.sessions, context
            self._growth_base = sum(r['duration'] for r in self.records_on(day) if r['id'] != active)
        return self._growth_base + self.elapsed

    @property
    def elapsed(self):
        return self.accumulated + (self.clock() - self.anchor if self.anchor is not None else 0)

    def checkpoint(self):
        if self.phase != 'idle':
            atomic_json(self.directory / 'active.json', dict(phase=self.phase, start=self.started,
                end=self.ended, elapsed=self.elapsed, checkpoint=self.wall(), session_id=self.session_id,
                pomodoro_enabled=self.pomodoro_enabled,pomodoro=self.pomodoro.dump()))

    def start(self):
        if getattr(self.cloud, 'switching', False): return
        if self.pomodoro_enabled and self.pomodoro.stage=='dead':self.discard()
        if self.phase not in ('idle', 'paused'):
            return
        if self.phase == 'idle':
            self.started, self.ended, self.accumulated = self.wall(), None, 0
            self.session_id=str(uuid.uuid4())
        if self.pomodoro_enabled: self.pomodoro.start(self.elapsed)
        self.anchor, self.phase = self.clock(), 'running'
        self.checkpoint()

    def select_mode(self,enabled,break_minutes=5):
        if self.pomodoro.stage=='dead':self.discard()
        if self.phase!='idle': raise ValueError('Finish or reset your current session before changing modes.')
        self.pomodoro_enabled=enabled; self.setting('pomodoro',enabled)
        self.pomodoro=Pomodoro(dict(break_minutes=break_minutes),clock=self.clock,wall=self.wall)
        self.setting('break_minutes',self.pomodoro.break_minutes)

    @property
    def display_time(self):
        return self.pomodoro.remaining(self.elapsed) if self.pomodoro_enabled and self.phase!='finished' else self.elapsed

    def advance_pomodoro(self):
        if not self.pomodoro_enabled or self.phase in ('idle','finished'): return None
        if self.phase=='running' and self.pomodoro.stage=='focus' and self.elapsed>=self.pomodoro.target:self.credit_pomodoro()
        cap,notice=self.pomodoro.advance(self.elapsed,self.phase=='running')
        if cap is not None: self.accumulated=cap; self.anchor=None; self.phase='paused'
        if notice: self.checkpoint()
        return notice

    def credit_pomodoro(self):
        if not self.session_id:self.session_id=str(uuid.uuid4())
        old=next((r for r in self.sessions if r['id']==self.session_id),None)
        if old and old['duration']>=self.pomodoro.target:return
        self.checkpoint()
        record=dict(id=self.session_id,name=old['name'] if old else 'Pomodoro focus',start=self.started,end=self.wall()-max(0,self.elapsed-self.pomodoro.target),duration=self.pomodoro.target,device_id=self.settings['device_id'],platform={'win32':'windows','darwin':'macos'}.get(sys.platform,'linux'),device_name={'win32':'Windows','darwin':'Mac'}.get(sys.platform,'Linux'))
        self.replace_sessions([record]+[r for r in self.sessions if r['id']!=self.session_id])

    def cancel_pomodoro(self):
        if not self.pomodoro_enabled or self.phase=='idle':return
        self.advance_pomodoro()
        if self.pomodoro.stage=='focus':
            self.accumulated=self.elapsed;self.anchor=None;self.phase='paused';self.pomodoro.stage='dead';self.pomodoro.deadline=None;self.checkpoint()
        elif self.pomodoro.stage!='dead':self.discard()

    def resume_finished(self):
        if self.phase != "finished" or getattr(self.cloud,"switching",False): return
        if self.pomodoro_enabled and self.elapsed>=self.pomodoro.target: self.pomodoro.stage="ready"
        if self.pomodoro_enabled: self.pomodoro.start(self.elapsed)
        self.ended=None;self.anchor=self.clock();self.phase="running";self.checkpoint()

    def pause(self):
        self.advance_pomodoro()
        if self.phase == 'running':
            self.accumulated = self.elapsed
            self.anchor, self.phase = None, 'paused'
            self.checkpoint()

    def finish(self):
        if self.pomodoro_enabled:return
        self.advance_pomodoro()
        if self.phase in ('running', 'paused'):
            self.accumulated = self.elapsed
            self.anchor, self.ended, self.phase = None, self.wall(), 'finished'
            self.checkpoint()

    def replace_sessions(self, records):
        if self.cloud.owner:
            self.cloud.replace(records, self.settings['device_id'])
            self.sessions = self.cloud.visible()
        else:
            atomic_json(self.directory / 'sessions.json', records)
            self.sessions = records

    def save(self, name):
        if self.phase != 'finished':
            return None
        record = dict(id=str(uuid.uuid4()), name=name.strip() or 'Focus session', start=self.started,
                      end=self.ended, duration=self.accumulated,
                      device_id=self.settings['device_id'],
                      platform={'win32': 'windows', 'darwin': 'macos'}.get(sys.platform, 'linux'),
                      device_name={'win32': 'Windows', 'darwin': 'Mac'}.get(sys.platform, 'Linux'))
        # Keep a stable record ID if saving succeeded but draft cleanup must be retried.
        self.session_id = self.session_id or record['id']
        record['id'] = self.session_id
        self.checkpoint()
        self.replace_sessions([record] + [r for r in self.sessions if r['id'] != record['id']])
        self.discard()
        return record

    def discard(self):
        (self.directory / 'active.json').unlink(missing_ok=True)
        self.phase, self.accumulated, self.anchor = 'idle', 0, None
        self.started, self.ended, self.session_id = None, None, None
        self.pomodoro=Pomodoro(dict(break_minutes=self.settings.get('break_minutes',5)),clock=self.clock,wall=self.wall)

    def rename(self, identifier, name):
        if not name.strip():
            raise ValueError('Enter a task name.')
        self.replace_sessions([dict(r, name=name.strip()) if r['id'] == identifier else r for r in self.sessions])

    def delete(self, identifier):
        record = next(r for r in self.sessions if r['id'] == identifier)
        self.replace_sessions([r for r in self.sessions if r['id'] != identifier])
        return record

    def restore(self, record):
        if not any(r['id'] == record['id'] for r in self.sessions):
            self.replace_sessions(sorted(self.sessions + [record], key=lambda r: r['start'], reverse=True))

    def setting(self, key, value):
        updated = dict(self.settings, **{key: value})
        atomic_json(self.directory / 'settings.json', updated)
        self.settings = updated

    def import_swift(self, filename):
        records = json.loads(Path(filename).read_text(encoding='utf-8'))
        converted = [dict(id=r['id'], name=r['name'], start=r['start'] + 978307200,
                          end=r['end'] + 978307200, duration=r['duration'], platform=r.get('platform') or 'macos',
                          device_name=r.get('deviceName') or 'Mac', device_id=r.get('deviceId')) for r in records]
        known = {r['id'] for r in self.sessions}
        self.replace_sessions(sorted(self.sessions + [r for r in converted if r['id'] not in known], key=lambda r: r['start'], reverse=True))

    def records_on(self, date):
        return [r for r in self.sessions if datetime.fromtimestamp(r['start']).date() == date]
