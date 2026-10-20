import os
os.environ.setdefault('QT_QPA_PLATFORM','offscreen')
import tempfile
import unittest
from datetime import datetime
from unittest.mock import patch
from PySide6.QtWidgets import QApplication, QDialog
from cybercat.app import Controller, STYLE
from cybercat.model import Store

class WidgetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app=QApplication.instance() or QApplication([])
        cls.app.setQuitOnLastWindowClosed(False); cls.app.setStyleSheet(STYLE)
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.c=Controller(self.app,Store(self.temp.name))
    def tearDown(self):
        self.c.tick.stop(); self.c.persist.stop(); self.c.shortcuts.close(); self.c.tray.hide()
        self.c.pet.hide(); self.c.pet.cat.animation.stop()
        if self.c.popup:self.c.popup.close()
        if self.c.history:self.c.history.close()
        self.temp.cleanup()
    def test_visibility_scope_and_pause_resume(self):
        self.c.command('S'); self.assertEqual(self.c.store.phase,'running')
        self.c.command('P'); self.assertEqual(self.c.store.phase,'paused')
        self.c.hide_pet(); self.c.command('S'); self.assertEqual(self.c.store.phase,'paused')
        self.c.command('R'); self.c.command('S'); self.assertEqual(self.c.store.phase,'running')
        self.assertEqual((self.c.pet.width(),self.c.pet.height()),(150,100))
    def test_save_shows_history_and_close_preserves(self):
        self.c.store.start(); self.c.store.finish()
        with patch.object(QDialog,'exec',return_value=QDialog.DialogCode.Rejected):
            self.assertFalse(self.c.finish())
        self.assertEqual(self.c.store.phase,'finished'); self.assertIsNone(self.c.history)
        with patch.object(QDialog,'exec',return_value=QDialog.DialogCode.Accepted):
            self.assertTrue(self.c.finish())
        self.assertTrue(self.c.history.isVisible()); self.assertEqual(len(self.c.store.sessions),1)
    def test_calendar_and_popup_construct(self):
        self.c.show_history(); self.c.options(); self.app.processEvents()
        self.assertTrue(self.c.history.isVisible()); self.assertTrue(self.c.popup.isVisible())

if __name__=='__main__':unittest.main()

class ShortcutScopeTests(unittest.TestCase):
    def test_windows_releases_session_keys_when_hidden(self):
        from unittest.mock import Mock, patch
        from cybercat.shortcuts import Shortcuts
        app=QApplication.instance() or QApplication([])
        user32=Mock(); user32.RegisterHotKey.return_value=1
        with patch('cybercat.shortcuts.sys.platform','win32'), patch('cybercat.shortcuts.ctypes.WinDLL',return_value=user32,create=True):
            shortcuts=Shortcuts(app,lambda key:None,lambda error:None)
            self.assertEqual(shortcuts.registered,{1})
            shortcuts.scope(True); self.assertEqual(shortcuts.registered,{1,2,3,4})
            shortcuts.scope(True,modal=True); self.assertEqual(shortcuts.registered,{1})
            shortcuts.scope(True); shortcuts.scope(False)
            self.assertEqual(shortcuts.registered,{1}); shortcuts.close()
