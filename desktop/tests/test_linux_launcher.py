import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

class LinuxLauncherTests(unittest.TestCase):
    def test_display_selection_and_arguments_from_path_with_spaces(self):
        if os.name=='nt':self.skipTest('POSIX launcher')
        source=Path(__file__).resolve().parents[1]/'packaging/linux/AppRun'
        with tempfile.TemporaryDirectory(prefix='focanopy app ') as directory:
            root=Path(directory);shutil.copy(source,root/'AppRun')
            binary=root/'usr/lib/focanopy/Focanopy';binary.parent.mkdir(parents=True)
            binary.write_text('#!/bin/sh\nprintf "%s\\n" "${QT_QPA_PLATFORM:-unset}" "$@"\n');binary.chmod(0o755)
            for variables,expected in [({'DISPLAY':':9','WAYLAND_DISPLAY':'wayland-0'},'xcb'),({'WAYLAND_DISPLAY':'wayland-0'},'wayland'),({'DISPLAY':':9','QT_QPA_PLATFORM':'offscreen'},'offscreen')]:
                env={k:v for k,v in os.environ.items() if k not in ('DISPLAY','WAYLAND_DISPLAY','QT_QPA_PLATFORM')};env.update(variables)
                result=subprocess.check_output([str(root/'AppRun'),'--command','R'],env=env,text=True)
                self.assertEqual(result.splitlines(),[expected,'--command','R'])
