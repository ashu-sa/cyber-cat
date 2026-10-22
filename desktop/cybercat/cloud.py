"""Account-scoped offline cache, secure credentials and revisioned reconciliation."""
import ssl
import certifi
import copy
import json
import threading
import uuid
import urllib.request
import urllib.error
from pathlib import Path

URL = 'https://fwlengqpmrtmpbrhtxis.supabase.co'
PUBLIC_KEY = 'sb_publishable_gsnb17q8TXRiHPu7W1vr4g_K7J8Np3H'  # public project key populated before packaging


def post(path, body, token=None):
    request = urllib.request.Request(URL + path, json.dumps(body).encode(), {
        'Content-Type': 'application/json', 'apikey': PUBLIC_KEY,
        **({'Authorization': 'Bearer ' + token} if token else {})})
    try:
        with urllib.request.urlopen(request, timeout=20, context=ssl.create_default_context(cafile=certifi.where())) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        try: message = (lambda d: d.get('error_description') or d.get('msg') or d.get('error'))(json.load(error)) or 'Cloud request failed. Try again.'
        except Exception: message = 'Cloud request failed. Try again.'
        raise RuntimeError(message) from None


def credentials(value=...):
    import keyring
    # Refuse plaintext/null fallbacks. The OS backend must provide a real vault.
    backend = keyring.get_keyring()
    if backend.priority < 1 or 'plaintext' in type(backend).__name__.lower():
        raise RuntimeError('Secure sign-in storage is unavailable. Enable your OS credential vault.')
    if value is ...:
        raw = keyring.get_password('CyberCat.Sync', 'session')
        return json.loads(raw) if raw else None
    if value is None:
        try: keyring.delete_password('CyberCat.Sync', 'session')
        except keyring.errors.PasswordDeleteError: pass
    else:
        keyring.set_password('CyberCat.Sync', 'session', json.dumps(value))


def wire(record, device_id):
    return dict(id=record['id'].lower(), name=record['name'],
        start_ms=round(record['start']*1000), end_ms=round(record['end']*1000),
        duration_ms=round(record['duration']*1000), platform=record.get('platform') or 'unknown',
        device_id=record.get('device_id') or device_id,
        device_name=record.get('device_name') or 'This device', revision=0, deleted=False)


def local(record):
    return dict(id=record['id'], name=record['name'], start=record['start_ms']/1000,
        end=record['end_ms']/1000, duration=record['duration_ms']/1000,
        platform=record['platform'], device_id=record['device_id'], device_name=record['device_name'])


class Cloud:
    def __init__(self, directory, vault=credentials, transport=post):
        self.directory, self.vault, self.transport = Path(directory), vault, transport
        self.lock = threading.RLock()
        self.switching = False
        self.session = None
        self.status = 'Local only'
        self.owner = None
        try:
            self.session = self.vault()
            if self.session: self.owner = self.session['user']['id']
        except Exception: self.status = 'Credential vault unavailable — local only'
        self.records = self.read() if self.owner else []

    @property
    def username(self):
        user = (self.session or {}).get('user', {})
        metadata = user.get('user_metadata') or {}
        return metadata.get('username') or metadata.get('full_name') or metadata.get('name') or user.get('email') or 'Google account'

    def read(self):
        path = self.directory / ('cloud-' + self.owner + '.json')
        return json.loads(path.read_text()) if path.exists() else []

    def write(self, records):
        from .model import atomic_json
        atomic_json(self.directory / ('cloud-' + self.owner + '.json'), records)
        self.records = records

    def visible(self):
        return sorted([local(r) for r in self.records if not r.get('deleted')], key=lambda r:r['start'], reverse=True)

    def replace(self, visible, device_id):
        with self.lock:
            old = {r['id']:r for r in self.records}
            wanted = {r['id'].lower():r for r in visible}
            updated = []
            for identifier in old.keys() | wanted.keys():
                previous = old.get(identifier)
                if identifier in wanted:
                    item = wire(wanted[identifier], device_id)
                    if previous:
                        # Timing/device origins stay immutable for existing remote records.
                        item = dict(previous, name=item['name'], deleted=False)
                    changed = not previous or previous['name'] != item['name'] or previous.get('deleted', False)
                else:
                    item = dict(previous, deleted=True)
                    changed = not previous.get('deleted', False)
                if changed: item.update(_dirty=True, _change=str(uuid.uuid4()))
                updated.append(item)
            self.write(updated)

    def authenticate(self, username, pin, signup=False):
        self.switching=True
        try:
            result = self.transport('/functions/v1/cybercat-account', dict(action='signup' if signup else 'login', username=username, pin=pin))
            self.accept_login(result)
        finally: self.switching=False

    def accept_login(self, result):
        # Verify the vault and cache before switching the active account.
        self.vault(result)
        with self.lock:
            previous = (self.session, self.owner, self.records)
            try:
                self.session, self.owner = result, result['user']['id']
                self.records = self.read()
            except Exception:
                self.session, self.owner, self.records = previous
                self.vault(previous[0]); raise
        self.status = 'Signed in · waiting to sync'

    def authenticate_google(self, flow, open_browser):
        try:
            from .oauth import require_google
            require_google()
            if flow.cancelled.is_set(): raise RuntimeError("Google sign-in cancelled.")
            open_browser()
            result = self.transport('/auth/v1/token?grant_type=pkce', flow.wait())
            if flow.cancelled.is_set(): raise RuntimeError('Google sign-in cancelled. Your local history is safe.')
            self.accept_login(result)
        finally:
            flow.server.server_close(); flow.verifier = ""
            self.switching=False

    def signout(self):
        with self.lock:
            self.vault(None)
            self.session, self.owner, self.records = None, None, []
            self.status = 'Local only'

    def resolve(self, keep_local):
        with self.lock:
            records = copy.deepcopy(self.records)
            for index, record in enumerate(records):
                remote = record.pop('_conflict', None)
                if remote is not None:
                    if keep_local:
                        record['revision'] = remote['revision']
                        record['_change'] = str(uuid.uuid4())
                    else: records[index] = remote
            self.write(records)

    def sync(self):
        with self.lock:
            if not self.session: return
            owner = self.owner
            snapshot = copy.deepcopy([r for r in self.records if r.get('_dirty') and '_conflict' not in r][:100])
            session = self.session
        # Refresh on each reconciliation; preserve rotated refresh tokens before uploading.
        refreshed = self.transport('/functions/v1/cybercat-account', {'action':'refresh', 'refresh_token':session['refresh_token']})
        with self.lock:
            if self.owner != owner: return
            self.vault(refreshed); self.session = refreshed
        changes = [{k:v for k,v in r.items() if not k.startswith('_') and k not in ('user_id','updated_at')} for r in snapshot]
        result = self.transport('/rest/v1/rpc/sync_focus_sessions', {'changes':changes}, refreshed['access_token'])
        with self.lock:
            if self.owner != owner: return
            sent = {r['id']:r for r in snapshot}
            remote = {r['id']:r for r in result['records']}
            conflicts = set(result['conflicts'])
            for record in self.records:
                identifier = record['id']
                if record.get('_dirty'):
                    if identifier in conflicts:
                        record = dict(record, _conflict=remote[identifier])
                        remote[identifier] = record
                    elif identifier not in sent or record.get('_change') != sent[identifier].get('_change'):
                        record = dict(record)
                        if identifier in remote: record['revision'] = remote[identifier]['revision']
                        remote[identifier] = record
            self.write(list(remote.values()))
            self.status = 'Changes need review' if any('_conflict' in r for r in self.records) else ('Saved offline · syncing remaining changes' if any(r.get('_dirty') for r in self.records) else 'All devices up to date')
