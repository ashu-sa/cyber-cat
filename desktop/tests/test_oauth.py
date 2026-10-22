import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from urllib.parse import parse_qs, urlsplit
from unittest.mock import patch
from cybercat.oauth import GoogleFlow, challenge
from cybercat.cloud import Cloud
from test_cloud import Vault

class GoogleTests(unittest.TestCase):
    def test_rfc7636_challenge_and_no_secret(self):
        self.assertEqual(challenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'), 'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM')
        flow=GoogleFlow()
        try:
            query=parse_qs(urlsplit(flow.url).query)
            self.assertEqual(query['provider'],['google'])
            self.assertEqual(query['code_challenge_method'],['s256'])
            self.assertNotIn(flow.verifier,flow.url)
            self.assertEqual(urlsplit(query['redirect_to'][0]).hostname,'127.0.0.1')
        finally: flow.server.server_close()

    def test_real_loopback_ignores_foreign_requests_and_exchanges_only_code(self):
        flow=GoogleFlow(); result={}
        def wait(): result.update(flow.wait(timeout=3))
        worker=threading.Thread(target=wait); worker.start()
        try:
            with self.assertRaises(urllib.error.HTTPError): urllib.request.urlopen('http://'+flow.host+'/wrong?code=wrong')
            request=urllib.request.Request('http://'+flow.host+flow.path+'?code=wrong',headers={'Host':'evil.example'})
            with self.assertRaises(urllib.error.HTTPError): urllib.request.urlopen(request)
            request=urllib.request.Request('http://'+flow.host+flow.path+'?code=first&code=second')
            with self.assertRaises(urllib.error.HTTPError): urllib.request.urlopen(request)
            with urllib.request.urlopen('http://'+flow.host+flow.path+'?code=one-time-code') as response:
                self.assertEqual(response.headers['Cache-Control'],'no-store')
                self.assertNotIn(b'one-time-code',response.read())
            worker.join(4)
            self.assertFalse(worker.is_alive())
            self.assertEqual(result['auth_code'],'one-time-code')
            self.assertTrue(result['code_verifier'])
            self.assertEqual(flow.verifier,'')
        finally: flow.cancel(); worker.join(4); flow.server.server_close()

    def test_cancel_and_timeout_leave_no_listener(self):
        for cancel in (True,False):
            flow=GoogleFlow()
            if cancel: flow.cancel()
            with self.assertRaisesRegex(RuntimeError,'cancelled|timed out'): flow.wait(timeout=0)
            self.assertEqual(flow.server.socket.fileno(),-1)

    def test_google_login_uses_same_secure_cache_and_handles_disabled_provider(self):
        with tempfile.TemporaryDirectory() as directory:
            vault=Vault(); calls=[]
            def transport(path,body,token=None):
                calls.append((path,body))
                return dict(access_token='access',refresh_token='refresh',user=dict(id='google-user',email='focus@example.com',user_metadata=dict(full_name='Tree Grower')))
            cloud=Cloud(directory,vault,transport)
            flow=GoogleFlow(); verifier=flow.verifier
            def browser():
                threading.Thread(target=lambda:urllib.request.urlopen('http://'+flow.host+flow.path+'?code=once').close()).start()
            with patch('cybercat.oauth.require_google'):
                cloud.switching=True; cloud.authenticate_google(flow,browser)
            self.assertEqual(cloud.username,'Tree Grower')
            self.assertEqual(calls,[('/auth/v1/token?grant_type=pkce',dict(auth_code='once',code_verifier=verifier))])
            self.assertEqual(Cloud(directory,vault,transport).owner,'google-user')
            cloud.signout(); failed=GoogleFlow()
            with patch('cybercat.oauth.require_google',side_effect=RuntimeError('needs setup')):
                with self.assertRaisesRegex(RuntimeError,'needs setup'): cloud.authenticate_google(failed,lambda:self.fail('Should not launch browser'))
            self.assertFalse(cloud.switching); self.assertIsNone(cloud.owner)
            self.assertEqual(failed.server.socket.fileno(),-1)

    def test_cancel_during_exchange_does_not_adopt_account(self):
        with tempfile.TemporaryDirectory() as directory:
            vault=Vault(); flow=GoogleFlow()
            def transport(path,body,token=None):
                flow.cancel()
                return dict(access_token='unused',refresh_token='unused',user=dict(id='not-adopted'))
            cloud=Cloud(directory,vault,transport); cloud.switching=True
            with patch('cybercat.oauth.require_google'), patch.object(flow,'wait',return_value=dict(auth_code='once',code_verifier=flow.verifier)):
                with self.assertRaisesRegex(RuntimeError,'cancelled'): cloud.authenticate_google(flow,lambda:None)
            self.assertIsNone(cloud.owner); self.assertIsNone(vault.data); self.assertFalse(cloud.switching)
