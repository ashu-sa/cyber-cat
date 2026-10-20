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
