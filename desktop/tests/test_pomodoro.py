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
        self.assertEqual(s.elapsed,3000);s.finish();self.assertEqual(s.phase,'paused');self.assertEqual(s.sessions[0]['duration'],3000);self.assertEqual(len(s.sessions),1)
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

    def test_cancel_kills_partial_tree_and_restart_grows_a_new_one(self):
        s=self.s;s.select_mode(True);s.start();self.advance(600);s.finish();self.assertEqual(s.phase,'running');self.assertEqual(s.sessions,[])
        s.cancel_pomodoro();self.assertEqual(s.pomodoro.stage,'dead');self.assertEqual(s.phase,'paused');self.assertEqual(s.display_time,1500);self.assertEqual(s.sessions,[])
        self.advance(500);recovered=Store(self.tmp.name,lambda:self.now,lambda:self.wall);self.assertEqual(recovered.pomodoro.stage,'dead');self.assertEqual(recovered.elapsed,600)
        recovered.start();self.assertEqual(recovered.elapsed,0);self.assertEqual(recovered.pomodoro.target,1500);self.assertEqual(recovered.pomodoro.stage,'focus')
    def test_completed_round_saved_once_and_survives_next_tree_death(self):
        s=self.s;s.select_mode(True);s.start();self.advance(1500);s.advance_pomodoro();self.assertEqual(s.sessions[0]['duration'],1500)
        identifier=s.sessions[0]['id'];s.advance_pomodoro();self.assertEqual(len(s.sessions),1)
        s.start();self.advance(400);s.cancel_pomodoro();self.assertEqual(s.pomodoro.stage,'dead');self.assertEqual(s.sessions[0]['duration'],1500);self.assertEqual(s.sessions[0]['id'],identifier)
        recovered=Store(self.tmp.name,lambda:self.now,lambda:self.wall);self.assertEqual(len(recovered.sessions),1);self.assertEqual(recovered.sessions[0]['duration'],1500)

    def test_death_animation_is_bounded_and_preview_is_isolated(self):
        from cybercat.lifecycle import TreePreviewFrame
        s=self.s;s.select_mode(True);s.start();self.advance(600);s.cancel_pomodoro()
        self.assertEqual(s.death_progress,0);self.advance(1.7);self.assertAlmostEqual(s.death_progress,.5);self.advance(10);self.assertEqual(s.death_progress,1)
        before=(s.elapsed,s.pomodoro.stage,list(s.sessions))
        for t in (0,10,12,20,21.7,27):TreePreviewFrame.at(t)
        self.assertEqual(before,(s.elapsed,s.pomodoro.stage,s.sessions))
        self.assertEqual(TreePreviewFrame.at(10).elapsed,1500);self.assertFalse(TreePreviewFrame.at(10).dead)
        self.assertTrue(TreePreviewFrame.at(20).dead);self.assertAlmostEqual(TreePreviewFrame.at(21.7).death,.5);self.assertEqual(TreePreviewFrame.at(27).death,1)
        recovered=Store(self.tmp.name,lambda:self.now,lambda:self.wall);self.assertEqual(recovered.death_progress,1)
