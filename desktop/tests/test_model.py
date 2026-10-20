import json
import tempfile
import unittest
from pathlib import Path
from cybercat.model import Store, atomic_json, clock_text, duration_text

class ModelTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.path = Path(self.tmp.name)
        self.now, self.wall = 100.0, 100000.0
        self.store = Store(self.path, lambda:self.now, lambda:self.wall)
    def tearDown(self): self.tmp.cleanup()
    def test_pause_resume_and_save(self):
        s=self.store; s.start(); self.now+=30; self.wall+=30; s.pause()
        self.now+=50; self.wall+=50; self.assertEqual(s.elapsed,30)
        s.start(); self.now+=20; self.wall+=20; s.finish()
        self.assertEqual(s.elapsed,50)
        record=s.save(' Work '); self.assertEqual(record['name'],'Work')
        self.assertEqual(Store(self.path).sessions[0]['duration'],50)
        self.assertEqual(s.phase,'idle'); self.assertFalse((self.path/'active.json').exists())
    def test_running_recovery_and_sleep(self):
        self.store.start(); self.now+=7200; self.wall+=7200
        self.assertEqual(self.store.elapsed,7200)
        recovered=Store(self.path, lambda:self.now, lambda:self.wall)
        self.assertEqual(recovered.elapsed,7200)
    def test_rename_delete_restore_and_settings(self):
        s=self.store; s.start(); self.now+=4; s.finish(); record=s.save('Task')
        s.rename(record['id'],'New name'); self.assertEqual(Store(self.path).sessions[0]['name'],'New name')
        with self.assertRaises(ValueError): s.rename(record['id'],' ')
        deleted=s.delete(record['id']); self.assertEqual(Store(self.path).sessions,[])
        s.restore(deleted); s.restore(deleted); self.assertEqual(len(Store(self.path).sessions),1)
        s.setting('opacity',.5); self.assertEqual(Store(self.path).settings['opacity'],.5)
    def test_failed_history_write_keeps_memory(self):
        from unittest.mock import patch
        before=list(self.store.sessions)
        with patch('cybercat.model.atomic_json',side_effect=OSError('disk full')):
            with self.assertRaises(OSError): self.store.replace_sessions([{'id':'bad'}])
        self.assertEqual(self.store.sessions,before)
    def test_import_swift_and_deduplicate(self):
        file=self.path/'swift.json'
        atomic_json(file,[dict(id='test',name='Swift',start=0,end=60,duration=60)])
        self.store.import_swift(file); self.store.import_swift(file)
        self.assertEqual(len(self.store.sessions),1); self.assertEqual(self.store.sessions[0]['start'],978307200)
    def test_format(self):
        self.assertEqual(clock_text(360001),'100:00:01')
        self.assertEqual(duration_text(7200),'2h')
        self.assertEqual(duration_text(4500),'1h 15m')

if __name__=='__main__': unittest.main()

class SaveRetryTests(unittest.TestCase):
    def test_recovery_after_cleanup_failure_does_not_duplicate(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            store=Store(tmp); store.start(); store.finish()
            with patch.object(Path,'unlink',side_effect=PermissionError('cleanup failed')):
                with self.assertRaises(PermissionError): store.save('Task')
            store.checkpoint()
            recovered=Store(tmp); recovered.save('Task')
            self.assertEqual(len(recovered.sessions),1)
