"""Native account sheet; networking never runs on the Qt UI thread."""
import time
from concurrent.futures import ThreadPoolExecutor
from PySide6.QtCore import QObject, Signal, QTimer
from PySide6.QtWidgets import QDialog, QVBoxLayout, QLabel, QLineEdit, QPushButton, QCheckBox, QMessageBox

class Accounts(QObject):
    complete = Signal(object)
    def __init__(self, controller):
        super().__init__()
        self.c = controller; self.busy = False; self.last_attempt=0; self.pool = ThreadPoolExecutor(max_workers=1)
        self.complete.connect(lambda fn:fn())
        self.timer = QTimer(self); self.timer.timeout.connect(self.poll); self.timer.start(5000)
        if self.c.store.cloud.owner: QTimer.singleShot(1000,self.sync)

    def run(self, operation, done):
        if self.busy: return
        self.busy = True
        def work():
            try: value, error = operation(), None
            except Exception as e: value, error = None, str(e)
            def finish():
                self.busy = False; done(value,error)
            self.complete.emit(finish)
        self.pool.submit(work)

    def refresh(self):
        cloud = self.c.store.cloud
        if cloud.owner: self.c.store.sessions = cloud.visible()
        if self.c.history: self.c.history.refresh()

    def poll(self):
        pending=any(r.get("_dirty") and "_conflict" not in r for r in self.c.store.cloud.records)
        if time.monotonic()-self.last_attempt >= (5 if pending else 30): self.sync()

    def sync(self):
        if self.busy or not self.c.store.cloud.owner: return
        self.last_attempt=time.monotonic()
        def done(_, error):
            if error: self.c.store.cloud.status = 'Saved offline · ' + error
            self.refresh()
        self.run(self.c.store.cloud.sync, done)

    def show(self):
        c=self.c; cloud=c.store.cloud
        dialog=QDialog(c.pet); dialog.setWindowTitle('Focanopy · Account'); dialog.setMinimumWidth(390)
        layout=QVBoxLayout(dialog); layout.setSpacing(12)
        heading=QLabel('Your focus, everywhere'); layout.addWidget(heading)
        status=QLabel(cloud.status); status.setWordWrap(True); layout.addWidget(status)
        if cloud.owner:
            layout.addWidget(QLabel('Signed in as ' + cloud.username))
            sync=QPushButton('Sync now'); layout.addWidget(sync)
            def refresh_done(_,error):
                if error: cloud.status='Saved offline · '+error
                self.refresh(); status.setText(cloud.status); sync.setEnabled(True)
            def sync_now():
                if self.busy: status.setText('Sync in progress…'); return
                sync.setEnabled(False); self.run(cloud.sync,refresh_done)
            sync.clicked.connect(sync_now)
            if any('_conflict' in r for r in cloud.records):
                for text,keep in [('Keep my changes',True),('Use cloud changes',False)]:
                    b=QPushButton(text); layout.addWidget(b)
                    b.clicked.connect(lambda _,k=keep:(cloud.resolve(k),self.refresh(),sync_now()))
            out=QPushButton('Sign out'); layout.addWidget(out)
            def signout():
                if self.busy or c.store.phase!='idle': status.setText('Finish the current timer and wait for sync before switching accounts.'); return
                try:
                    cloud.signout(); c.store.sessions=c.store.read('sessions.json',[]); self.refresh(); dialog.accept()
                except Exception as e: status.setText(str(e))
            out.clicked.connect(signout)
        else:
            username=QLineEdit(); username.setPlaceholderText('Username'); username.setAccessibleName('Username'); layout.addWidget(username)
            pin=QLineEdit(); pin.setEchoMode(QLineEdit.EchoMode.Password); pin.setMaxLength(4); pin.setPlaceholderText('4-digit PIN'); pin.setAccessibleName('4-digit PIN'); layout.addWidget(pin)
            upload=QCheckBox("Add this device’s existing sessions to my account"); layout.addWidget(upload)
            note=QLabel('Use the same username and PIN on every device. Keep them safe; this account has no email recovery.'); note.setWordWrap(True); layout.addWidget(note)
            buttons=[]
            def submit(signup):
                if self.busy or c.store.phase!='idle': status.setText('Finish your current timer before signing in.'); return
                name= username.text().strip().lower(); code=pin.text()
                import re
                if not re.fullmatch('[a-z0-9_]{3,24}',name) or not re.fullmatch('[0-9]{4}',code): status.setText('Use 3–24 letters, numbers or underscores and a 4-digit PIN.'); return
                existing=list(c.store.sessions); include=upload.isChecked()
                for b in buttons: b.setEnabled(False)
                status.setText('Connecting…')
                def done(_,error):
                    for b in buttons: b.setEnabled(True)
                    pin.clear()
                    if error: status.setText(error); return
                    c.store.sessions=cloud.visible()
                    if include: c.safe(lambda:c.store.replace_sessions(c.store.sessions+[r for r in existing if r['id'].lower() not in {x['id'] for x in c.store.sessions}]))
                    self.refresh(); dialog.accept(); self.sync()
                self.run(lambda:cloud.authenticate(name,code,signup),done)
            for text,signup in [('Sign in',False),('Create account',True)]:
                b=QPushButton(text); b.clicked.connect(lambda _,s=signup:submit(s)); layout.addWidget(b); buttons.append(b)
        close=QPushButton('Close'); close.clicked.connect(dialog.reject); layout.addWidget(close)
        # Do not destroy callback widgets while a request is in flight.
        dialog.finished.connect(lambda _:None)
        c.modal=True; c.scope()
        try: dialog.exec()
        finally: c.modal=False; c.scope()
