import tempfile
import unittest
from cybercat.model import Store
from cybercat.focus_blocking import active,FocusBlocker,windows_app_blocked
from unittest.mock import patch
import json

class PomodoroTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.now=0;self.wall=10000
        self.s=Store(self.tmp.name,lambda:self.now,lambda:self.wall)
    def tearDown(self):self.tmp.cleanup()
    def advance(self,seconds):self.now+=seconds;self.wall+=seconds
    def test_manual_rounds_exclude_breaks_and_never_autostart(self):
        s=self.s;s.select_mode(True,5);s.start();self.advance(1500)
        self.assertIn('bloomed',s.advance_pomodoro());self.assertEqual(s.phase,'paused');self.assertEqual(s.elapsed,1500);self.assertEqual(s.pomodoro.stage,'break')
        self.advance(300);self.assertIn('needs water',s.advance_pomodoro());self.assertEqual(s.pomodoro.stage,'ready')
        self.advance(1000);s.advance_pomodoro();self.assertEqual(s.phase,'paused');self.assertEqual(s.elapsed,1500)
        s.start();self.assertEqual(s.pomodoro.target,3000);self.advance(120);s.pause();self.advance(200);s.start();self.advance(1380);s.advance_pomodoro()
        self.assertEqual(s.elapsed,3000);s.finish();record=s.save('Two flowers');self.assertEqual(record['duration'],3000)
    def test_long_sleep_caps_focus_and_does_not_run_more_rounds(self):
        s=self.s;s.select_mode(True,10);s.start();self.advance(7200)
        notice=s.advance_pomodoro();self.assertIn('needs water',notice);self.assertEqual(s.elapsed,1500);self.assertEqual(s.pomodoro.stage,'ready');self.assertEqual(s.phase,'paused')
        recovered=Store(self.tmp.name,lambda:self.now,lambda:self.wall);self.assertEqual(recovered.elapsed,1500);self.assertEqual(recovered.pomodoro.stage,'ready')
    def test_recovery_during_break_and_boundary_pause(self):
        s=self.s;s.select_mode(True);s.start();self.advance(1502);s.pause()
        self.assertEqual(s.elapsed,1500);self.assertAlmostEqual(s.display_time,298)
        self.advance(100);recovered=Store(self.tmp.name,lambda:self.now,lambda:self.wall)
        self.assertAlmostEqual(recovered.display_time,198);recovered.start();self.assertEqual(recovered.pomodoro.stage,'focus');self.assertEqual(recovered.pomodoro.target,3000)
    def test_free_mode_and_explicit_mode_switch(self):
        s=self.s;self.assertFalse(s.pomodoro_enabled);s.start();self.advance(3000);self.assertIsNone(s.advance_pomodoro());self.assertEqual(s.elapsed,3000)
        with self.assertRaises(ValueError):s.select_mode(True)
    def test_blocking_only_running_focus_and_lease_releases(self):
        s=self.s;s.setting('focus_blocking',True);s.setting('blocked_apps',['demo.desktop']);s.start();self.assertTrue(active(s));s.discard();s.select_mode(True);s.start();self.assertTrue(active(s))
        with patch('cybercat.focus_blocking.sys.platform','linux'):
            blocker=FocusBlocker(s,lambda *_:None);blocker.tick();policy=json.loads(blocker.policy.read_text());self.assertTrue(policy['active']);self.assertLessEqual(policy['expires']-self.wall,10)
            s.pause();blocker.tick();self.assertFalse(json.loads(blocker.policy.read_text())['active'])
            s.start();self.advance(1500);s.advance_pomodoro();self.assertFalse(active(s));blocker.close()

    def test_all_apps_exclusions_browsers_and_system_essentials(self):
        selected=[r'C:\Apps\Studio.exe',r'C:\Apps\chrome.exe'];excluded=[r'C:\Apps\Music.exe']
        def blocked(path,all_apps=True):return windows_app_blocked(path,selected,excluded,all_apps)
        self.assertTrue(blocked(r'C:\Apps\Studio.exe'))
        self.assertFalse(blocked(r'C:\Apps\chrome.exe'));self.assertTrue(blocked(r'C:\Apps\chrome.exe',False))
        self.assertFalse(blocked(r'C:\Apps\Music.exe'));self.assertFalse(blocked(r'C:\Windows\explorer.exe'))
        self.assertFalse(blocked(r'C:\Apps\Focanopy.exe'));self.assertTrue(blocked(r'C:\Other\Editor.exe'))
    def test_free_timer_all_app_policy_and_exclusions_are_written(self):
        s=self.s;s.setting('focus_blocking',True);s.setting('focus_block_all',True);s.setting('excluded_apps',['music.desktop']);s.start()
        with patch('cybercat.focus_blocking.sys.platform','linux'):
            blocker=FocusBlocker(s,lambda *_:None);blocker.tick();policy=json.loads(blocker.policy.read_text())
            self.assertTrue(policy['active']);self.assertTrue(policy['all']);self.assertEqual(policy['excluded'],['music.desktop'])
            self.assertEqual(policy['expires'],self.wall+10);s.pause();blocker.tick();self.assertFalse(json.loads(blocker.policy.read_text())['active'])
