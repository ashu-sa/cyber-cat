import os
os.environ.setdefault('QT_QPA_PLATFORM','offscreen')
import tempfile
import unittest
from datetime import datetime
from unittest.mock import patch
from PySide6.QtWidgets import QApplication, QDialog
from PySide6.QtCore import Qt,QEvent,QPointF
from PySide6.QtGui import QMouseEvent,QImage,QPainter
from cybercat.tree import paint_tree
from cybercat.app import Controller, STYLE, apply_theme
from cybercat.model import Store

class WidgetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app=QApplication.instance() or QApplication([])
        cls.app.setQuitOnLastWindowClosed(False); apply_theme(cls.app)
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
        self.assertFalse(self.c.pet.play.icon().isNull()); self.assertEqual(self.c.pet.play.text(),'')
    def test_save_shows_history_and_close_preserves(self):
        self.c.store.start(); self.c.store.finish()
        with patch.object(QDialog,'exec',return_value=QDialog.DialogCode.Rejected):
            self.assertFalse(self.c.finish())
        self.assertEqual(self.c.store.phase,'finished'); self.assertIsNone(self.c.history)
        with patch.object(QDialog,'exec',return_value=QDialog.DialogCode.Accepted):
            self.assertTrue(self.c.finish())
        self.assertTrue(self.c.history.isVisible()); self.assertEqual(len(self.c.store.sessions),1)
    def test_drag_timer_and_buttons_does_not_click(self):
        self.c.pet.move(100,100)
        for target in (self.c.pet.timer,self.c.pet.play,self.c.pet.finish):
            origin=self.c.pet.pos()
            def send(kind,point,held):
                event=QMouseEvent(kind,QPointF(5,5),QPointF(point),Qt.MouseButton.LeftButton if kind!=QEvent.Type.MouseMove else Qt.MouseButton.NoButton,held,Qt.KeyboardModifier.NoModifier)
                QApplication.sendEvent(target,event)
            send(QEvent.Type.MouseButtonPress,origin,Qt.MouseButton.LeftButton)
            send(QEvent.Type.MouseMove,origin+type(origin)(40,30),Qt.MouseButton.LeftButton)
            send(QEvent.Type.MouseButtonRelease,origin+type(origin)(40,30),Qt.MouseButton.NoButton)
            self.assertEqual(self.c.pet.pos(),origin+type(origin)(40,30))
            self.assertEqual(self.c.store.phase,'idle')
        self.c.pet.play.click(); self.assertEqual(self.c.store.phase,'running')
    def test_growth_changes_throughout_hour_and_keeps_completed_tree(self):
        def image(seconds):
            img=QImage(330,174,QImage.Format.Format_ARGB32);img.fill(Qt.GlobalColor.transparent)
            p=QPainter(img);p.scale(3,3);paint_tree(p,seconds,'idle',False);p.end();return img
        frames=[image(seconds) for seconds in (0,300,600,900,1200,1500,1800,2100,2400,2700,3000,3300,3599)]
        self.assertEqual(len({bytes(frame.constBits()) for frame in frames}),len(frames))
        completed=image(3600)
        self.assertTrue(any(completed.pixelColor(x,y).green()>completed.pixelColor(x,y).red()+10 for x in range(20,120) for y in range(40,140)))
        # New sprout has its own trunk/leaf pixels to the right of the completed tree.
        self.assertTrue(any(completed.pixelColor(x,y).alpha()>0 for x in range(207,237) for y in range(120,148)))
    def test_paused_tree_dry_without_water(self):
        def render(phase):
            image=QImage(330,174,QImage.Format.Format_ARGB32);image.fill(Qt.GlobalColor.transparent)
            painter=QPainter(image);painter.scale(3,3);paint_tree(painter,2701,phase);painter.end();return image
        running=render('running'); paused=render('paused')
        self.assertNotEqual(running,paused)
        # Water now lands above the active seedling; test the air above its canopy.
        self.assertTrue(any(running.pixelColor(x,y).alpha()>0 for x in range(168,201) for y in range(0,30)))
        self.assertTrue(all(paused.pixelColor(x,y).alpha()==0 for x in range(168,201) for y in range(0,30)))
        self.assertEqual(render('running'),running)
    def test_calendar_and_popup_construct(self):
        self.c.show_history(); self.c.options(); self.app.processEvents()
        self.assertTrue(self.c.history.isVisible()); self.assertTrue(self.c.popup.isVisible())

class AppToggleTests(unittest.TestCase):
    def test_app_switches_store_exclusions_and_selected_apps(self):
        import tempfile
        from unittest.mock import patch
        from cybercat.model import Store
        from cybercat.app import Controller,AppSwitch
        app=QApplication.instance() or QApplication([])
        with tempfile.TemporaryDirectory() as folder,patch('cybercat.focus_blocking.app_inventory',return_value=[dict(id='editor.desktop',name='Editor',icon='application-x-executable',browser=False),dict(id='browser.desktop',name='Browser',icon='web-browser',browser=True)]):
            store=Store(folder);controller=Controller(app,store);dialog=controller.focus_settings(preview=True);dialog.show();app.processEvents()
            def toggles():return {t.property('appId'):t for t in dialog.findChildren(AppSwitch,'appBlockToggle') if not t.parent().isHidden()}
            rows=toggles();rows['editor.desktop'].setChecked(True);self.assertIn('editor.desktop',store.settings['blocked_apps'])
            all_switch=dialog.findChild(AppSwitch,'blockAllSwitch');all_switch.setChecked(True);app.processEvents()
            rows=toggles();self.assertFalse(rows['browser.desktop'].isEnabled());self.assertFalse(rows['browser.desktop'].isChecked())
            rows['editor.desktop'].setChecked(False);self.assertIn('editor.desktop',store.settings['excluded_apps'])
            all_switch.setChecked(False);app.processEvents();rows=toggles();rows['editor.desktop'].setChecked(True);self.assertNotIn('editor.desktop',store.settings['excluded_apps'])
            dialog.close();controller.tick.stop();controller.persist.stop();controller.shortcuts.close();controller.tray.hide();controller.pet.cat.animation.stop();controller.pet.close()

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
            shortcuts.scope(True,finish_allowed=False);self.assertEqual(shortcuts.registered,{1,2,3})
            shortcuts.scope(True,modal=True); self.assertEqual(shortcuts.registered,{1})
            shortcuts.scope(True); shortcuts.scope(False)
            self.assertEqual(shortcuts.registered,{1}); shortcuts.close()
