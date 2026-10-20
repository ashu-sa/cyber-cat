"""Render the actual Qt widgets on the target OS using isolated sample history."""
import argparse
import os
import tempfile
from datetime import datetime,timedelta
from pathlib import Path
os.environ.setdefault('QT_QPA_PLATFORM','offscreen')
from PySide6.QtWidgets import QApplication
from cybercat.app import Controller,apply_theme
from cybercat.model import Store

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--platform',required=True);parser.add_argument('--output',required=True)
    args=parser.parse_args();out=Path(args.output);out.mkdir(parents=True,exist_ok=True)
    app=QApplication([]);apply_theme(app)
    with tempfile.TemporaryDirectory() as folder:
        model=Store(folder);records=[]
        for days,hours,name in [(0,1,'Work'),(1,2,'Upskill'),(3,1.5,'Interview Prep'),(5,3,'Job Apply')]:
            start=(datetime.now().replace(hour=10,minute=0,second=0)-timedelta(days=days)).timestamp()
            records.append(dict(id=str(days),name=name,start=start,end=start+hours*3600,duration=hours*3600))
        model.replace_sessions(records)
        controller=Controller(app,model);controller.show_history();app.processEvents()
        assert controller.history.grab().save(str(out/f'{args.platform}-journal.png'))
        assert controller.pet.grab().save(str(out/f'{args.platform}-pet.png'))
        controller.tick.stop();controller.persist.stop();controller.shortcuts.close();controller.tray.hide();controller.pet.cat.animation.stop()

if __name__=='__main__':main()
