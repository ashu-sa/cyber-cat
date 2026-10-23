"""Keep packaged Qt libraries out of host GNOME tools' dynamic loader path."""
import os
import sys

def host_environment():
    env=os.environ.copy()
    if sys.platform.startswith('linux') and getattr(sys,'frozen',False):
        original=env.get('LD_LIBRARY_PATH_ORIG')
        if original is None:env.pop('LD_LIBRARY_PATH',None)
        else:env['LD_LIBRARY_PATH']=original
        for key in ('QT_PLUGIN_PATH','QT_QPA_PLATFORM_PLUGIN_PATH','QML2_IMPORT_PATH'):
            env.pop(key,None)
    return env
