import unittest
from unittest.mock import patch
from cybercat.shortcuts import install_gnome, remove_gnome, BASE

class GnomeTests(unittest.TestCase):
    def test_installer_preserves_unrelated_bindings(self):
        calls=[]
        def fake(*args):
            calls.append(args)
            if args[:3]==('get','org.gnome.settings-daemon.plugins.media-keys','custom-keybindings'):
                return "['/unrelated/']"
            if args[0]=='get': return "'<Control><Alt>x'"
            return ''
        with patch('cybercat.shortcuts.gsettings',side_effect=fake): install_gnome()
        last=calls[-1]
        self.assertIn('/unrelated/',last[-1]); self.assertIn(BASE+'r/',last[-1])
        self.assertEqual(sum(c[0]=='set' and c[2]=='command' for c in calls),4)
    def test_conflict_does_not_overwrite_other_shortcut(self):
        def fake(*args):
            if args[2]=='custom-keybindings':return "['/other/']"
            return "'<Control>s'"
        with patch('cybercat.shortcuts.gsettings',side_effect=fake) as mock:
            with self.assertRaises(RuntimeError):install_gnome()
            self.assertTrue(all(c.args[0]=='get' for c in mock.call_args_list))

if __name__=='__main__':unittest.main()

class PortableGnomeTests(unittest.TestCase):
    def test_appimage_shortcuts_use_persistent_image_not_temporary_mount(self):
        import os
        import sys
        calls=[]
        def fake(*args):
            calls.append(args)
            return '[]' if args[:3]==('get','org.gnome.settings-daemon.plugins.media-keys','custom-keybindings') else ''
        with patch.object(sys,'frozen',True,create=True), patch.dict(os.environ,{'APPIMAGE':'/home/test/Apps/Focus Grove.AppImage'}), patch('cybercat.shortcuts.gsettings',side_effect=fake):install_gnome()
        commands=[c[3] for c in calls if c[0]=='set' and c[2]=='command']
        self.assertEqual(len(commands),4)
        self.assertTrue(all(c.startswith("'/home/test/Apps/Focus Grove.AppImage' --command ") for c in commands))
    def test_host_commands_restore_original_loader_environment(self):
        import os
        import sys
        from cybercat.host import host_environment
        with patch.object(sys,'frozen',True,create=True),patch('cybercat.host.sys.platform','linux'),patch.dict(os.environ,{'LD_LIBRARY_PATH':'/tmp/.mount_focus/_internal','LD_LIBRARY_PATH_ORIG':'/custom/host','QT_PLUGIN_PATH':'/tmp/.mount_focus/plugins'}):
            env=host_environment();self.assertEqual(env['LD_LIBRARY_PATH'],'/custom/host');self.assertNotIn('QT_PLUGIN_PATH',env)
            self.assertIn('.mount_focus',os.environ['LD_LIBRARY_PATH'])
