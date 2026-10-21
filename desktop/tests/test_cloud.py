import copy
import tempfile
import unittest
import uuid
from cybercat.cloud import Cloud, wire

class Vault:
    def __init__(self): self.data=None
    def __call__(self,value=...):
        if value is ...: return copy.deepcopy(self.data)
        self.data=copy.deepcopy(value)

class Server:
    def __init__(self): self.rows={}; self.offline=False; self.during=None
    def __call__(self,path,body,token=None):
        if self.offline: raise OSError('Offline')
        if path.endswith('cybercat-account'):
            owner=body.get('username') or body['refresh_token']
            return dict(user=dict(id=owner,user_metadata=dict(username=owner)),access_token=owner,refresh_token=owner)
        rows=self.rows.setdefault(token,{})
        conflicts=[]
        for r in body['changes']:
            old=rows.get(r['id'])
            if old and old['name']==r['name'] and old['deleted']==r['deleted']: continue
            if old and old['revision']!=r['revision']: conflicts.append(r['id']); continue
            rows[r['id']]=dict(r,revision=(old['revision']+1 if old else 1))
        if self.during:
            fn,self.during=self.during,None; fn()
        return dict(records=copy.deepcopy(list(rows.values())),conflicts=conflicts)

class CloudTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(); self.server=Server(); self.vault=Vault()
        self.cloud=Cloud(self.tmp.name,self.vault,self.server); self.cloud.authenticate('alice','1234')
        self.record=dict(id=str(uuid.uuid4()),name='Work',start=1700000000.125,end=1700002400.125,duration=2400,platform='macos',device_id=str(uuid.uuid4()),device_name='Mac')
    def tearDown(self): self.tmp.cleanup()
    def test_epoch_and_precision(self):
        r=wire(self.record,str(uuid.uuid4())); self.assertEqual(r['start_ms'],1700000000125); self.assertEqual(r['duration_ms'],2400000)
    def test_offline_recovery_and_replay(self):
        self.cloud.replace([self.record],str(uuid.uuid4())); self.server.offline=True
        with self.assertRaises(OSError): self.cloud.sync()
        recovered=Cloud(self.tmp.name,self.vault,self.server); self.assertEqual(len(recovered.visible()),1)
        self.server.offline=False; recovered.sync(); recovered.sync()
        self.assertEqual(len(self.server.rows['alice']),1); self.assertFalse(recovered.records[0].get('_dirty'))
    def test_account_isolation(self):
        self.cloud.replace([self.record],str(uuid.uuid4())); self.cloud.sync(); self.cloud.signout()
        self.cloud.authenticate('bob','2345'); self.assertEqual(self.cloud.visible(),[])
        self.cloud.authenticate('alice','1234'); self.assertEqual(self.cloud.visible()[0]['name'],'Work')
    def test_edit_during_upload_is_preserved(self):
        self.cloud.replace([self.record],str(uuid.uuid4()))
        self.server.during=lambda:self.cloud.replace([dict(self.record,name='Newer local edit')],str(uuid.uuid4()))
        self.cloud.sync(); self.assertEqual(self.cloud.visible()[0]['name'],'Newer local edit'); self.assertTrue(self.cloud.records[0]['_dirty'])
        self.cloud.sync(); self.assertEqual(self.server.rows['alice'][self.record['id']]['name'],'Newer local edit')
    def test_tombstone_and_undo(self):
        self.cloud.replace([self.record],str(uuid.uuid4())); self.cloud.sync()
        self.cloud.replace([],str(uuid.uuid4())); self.cloud.sync(); self.assertEqual(self.cloud.visible(),[])
        self.assertTrue(self.server.rows['alice'][self.record['id']]['deleted'])
        self.cloud.replace([self.record],str(uuid.uuid4())); self.cloud.sync(); self.assertEqual(len(self.cloud.visible()),1)
    def test_conflict_review(self):
        self.cloud.replace([self.record],str(uuid.uuid4())); self.cloud.sync()
        other=Cloud(self.tmp.name,Vault(),self.server); other.authenticate('alice','1234'); other.sync()
        other.replace([dict(self.record,name='Other device')],str(uuid.uuid4())); other.sync()
        self.cloud.replace([dict(self.record,name='My edit')],str(uuid.uuid4())); self.cloud.sync()
        self.assertIn('_conflict',self.cloud.records[0]); self.assertEqual(self.cloud.visible()[0]['name'],'My edit')
        self.cloud.resolve(True); self.cloud.sync(); self.assertEqual(self.server.rows['alice'][self.record['id']]['name'],'My edit')
    def test_vault_failure_does_not_switch_account(self):
        def broken(*args): raise OSError('Locked vault')
        self.cloud.vault=broken
        with self.assertRaises(OSError): self.cloud.authenticate('bob','1234')
        self.assertEqual(self.cloud.owner,'alice'); self.assertFalse(self.cloud.switching)
