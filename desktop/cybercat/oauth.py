"""Google browser sign-in: PKCE, short-lived IPv4 loopback and cancellable wait."""
import base64
import hashlib
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlencode, urlsplit, parse_qs
from .cloud import URL, PUBLIC_KEY
import json
import ssl
import certifi
import urllib.request


def challenge(verifier):
    return base64.urlsafe_b64encode(hashlib.sha256(verifier.encode('ascii')).digest()).rstrip(b'=').decode('ascii')


def require_google():
    request = urllib.request.Request(URL + '/auth/v1/settings', headers={'apikey': PUBLIC_KEY})
    with urllib.request.urlopen(request, timeout=15, context=ssl.create_default_context(cafile=certifi.where())) as response:
        settings = json.load(response)
    if not settings.get('external', {}).get('google'):
        raise RuntimeError('Google sign-in needs one-time setup by the app owner. Username and PIN still work.')


class GoogleFlow:
    def __init__(self):
        self.verifier = secrets.token_urlsafe(48)
        self.path = '/focanopy/' + secrets.token_urlsafe(24)
        self.cancelled = threading.Event()
        self.result = None
        flow = self
        class Callback(BaseHTTPRequestHandler):
            def log_message(self, *args): pass  # Codes must never enter request logs.
            def do_GET(self):
                target = urlsplit(self.path)
                values = parse_qs(target.query)
                valid = (target.path == flow.path and self.headers.get('Host') == flow.host
                         and (len(values.get('code', [])) == 1 or 'error' in values))
                self.send_response(200 if valid else 404)
                self.send_header('Content-Type', 'text/html; charset=utf-8')
                self.send_header('Cache-Control', 'no-store')
                self.send_header('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'")
                self.end_headers()
                if valid:
                    flow.result = values
                    body = '<h1>Return to Focanopy 🌱</h1><p>You can close this browser tab. Your app will finish signing you in.</p>'
                else: body = '<h1>This sign-in link is not valid.</h1>'
                try: self.wfile.write(('<!doctype html><meta charset="utf-8"><style>body{background:#111b17;color:#addaad;font:18px system-ui;padding:60px}</style>'+body).encode())
                except OSError: pass
            def setup(self):
                super().setup(); self.connection.settimeout(3)
        self.server = HTTPServer(('127.0.0.1', 0), Callback)
        self.server.timeout = 0.25
        self.host = '127.0.0.1:' + str(self.server.server_port)
        redirect = 'http://' + self.host + self.path
        self.url = URL + '/auth/v1/authorize?' + urlencode(dict(provider='google', redirect_to=redirect,
            code_challenge=challenge(self.verifier), code_challenge_method='s256', scopes='openid email profile', prompt='select_account'))

    def cancel(self): self.cancelled.set()

    def wait(self, timeout=180):
        end = time.monotonic() + timeout
        try:
            while not self.result and not self.cancelled.is_set() and time.monotonic() < end:
                self.server.handle_request()
            if self.cancelled.is_set(): raise RuntimeError('Google sign-in cancelled. Your local history is safe.')
            if not self.result: raise RuntimeError('Google sign-in timed out. Please try again.')
            if 'error' in self.result: raise RuntimeError('Google sign-in was not completed. Please try again.')
            code = self.result['code'][0]
            if not code or len(code) > 2048: raise RuntimeError('Invalid sign-in response. Please try again.')
            return dict(auth_code=code, code_verifier=self.verifier)
        finally:
            self.server.server_close()
            self.verifier = ''
