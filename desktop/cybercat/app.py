import calendar
import math
import os
import sys
import time
from pathlib import Path
from datetime import date, datetime, timedelta
from functools import partial
from PySide6.QtCore import Qt, QDate, QRectF, QTimer, QPoint, QPointF, QSize, QSettings, QEvent
from PySide6.QtGui import QColor, QFont, QFontDatabase, QIcon, QPainter, QPainterPath, QPalette, QPen, QPixmap, QPolygonF, QShortcut, QKeySequence
from PySide6.QtWidgets import (QApplication, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QDialog, QComboBox, QDialogButtonBox, QSlider, QCheckBox, QScrollArea, QCalendarWidget,
    QListWidget, QListWidgetItem, QAbstractItemView, QSystemTrayIcon, QMenu, QMessageBox, QInputDialog, QFileDialog, QFrame, QProgressBar)
from .model import Store, clock_text, duration_text, device_label
from .forest import tree_text, cell_text, summary as forest_summary, garden
from .tree import paint_tree
from .shortcuts import Shortcuts, gnome_available, install_gnome, remove_gnome

STYLE = '''
QWidget { background:#111b17; color:#e6edf5; font-size:13px; }
QDialog, QFrame#card { background:#1e2c24; border-radius:12px; }
QPushButton { background:#273c2d; border:1px solid #3a4e3e; border-radius:7px; padding:8px 12px; }
QPushButton:hover { background:#354e3b; border-color:#addaad; }
QPushButton:disabled { color:#63707d; }
QPushButton#primary { color:#addaad; }
QComboBox { background:#132219; border:1px solid #506652; border-radius:6px; padding:9px; min-width:270px; }
QLabel#subtitle { color:#a1b5a5; font-size:11px; }
QLabel#title { font-size:25px; font-weight:700; }
QLabel#total { color:#addaad; font-size:25px; font-weight:600; }
QScrollArea { border:0; } QCalendarWidget QAbstractItemView { selection-background-color:#395b3d; }
'''


def apply_theme(app):
    app.setStyle('Fusion')
    assets = Path(__file__).resolve().parent / 'assets'
    for filename in ('NotoSans.ttf', 'NotoSansMono.ttf'):
        if QFontDatabase.addApplicationFont(str(assets / filename)) < 0:
            raise RuntimeError('Missing bundled font: ' + filename)
    app.setFont(QFont('Noto Sans', 10))
    palette = QPalette()
    for role, color in [('Window','#111b17'),('WindowText','#e6edf5'),('Base','#1e2c24'),('AlternateBase','#25392b'),('Text','#e6edf5'),('Button','#273c2d'),('ButtonText','#e6edf5'),('Highlight','#395b3d'),('HighlightedText','#ffffff'),('ToolTipBase','#273c2d'),('ToolTipText','#e6edf5')]:
        palette.setColor(getattr(QPalette.ColorRole, role), QColor(color))
    app.setPalette(palette); app.setStyleSheet(STYLE)


def action_icon(symbol):
    pix = QPixmap(24,24); pix.fill(Qt.GlobalColor.transparent)
    painter = QPainter(pix); painter.setRenderHint(QPainter.RenderHint.Antialiasing)
    painter.setPen(QPen(QColor('#addaad'),2.5,Qt.PenStyle.SolidLine,Qt.PenCapStyle.RoundCap,Qt.PenJoinStyle.RoundJoin))
    if symbol == '▶':
        painter.setBrush(QColor('#addaad')); painter.setPen(Qt.PenStyle.NoPen)
        painter.drawPolygon(QPolygonF([QPointF(7,4),QPointF(20,12),QPointF(7,20)]))
    elif symbol == 'Ⅱ':
        painter.setBrush(QColor('#addaad')); painter.setPen(Qt.PenStyle.NoPen)
        painter.drawRoundedRect(QRectF(6,4,4,16),1,1);painter.drawRoundedRect(QRectF(15,4,4,16),1,1)
    elif symbol == '✓':
        painter.drawLine(4,12,10,18);painter.drawLine(10,18,21,5)
    elif symbol == '↺':
        painter.drawArc(QRectF(5,5,14,14),40*16,290*16);painter.drawLine(4,4,4,11);painter.drawLine(4,11,10,10)
    elif symbol == '↓':
        painter.drawLine(12,3,12,16);painter.drawLine(7,11,12,16);painter.drawLine(12,16,17,11);painter.drawLine(5,21,19,21)
    else:
        painter.drawLine(5,18,18,5);painter.drawLine(4,20,8,19);painter.drawLine(16,4,20,8)
    painter.end();return QIcon(pix)


def button(text, callback, tooltip=''):
    b = QPushButton(text)
    if text in ('▶','Ⅱ','✓','↓','✎','↺'):
        b.setText('');b.setIcon(action_icon(text));b.setIconSize(QSize(14,14));b.setAccessibleName(tooltip or text)
    b.clicked.connect(callback)
    b.setToolTip(tooltip)
    return b


def label(text, role=None):
    w = QLabel(text)
    if role:
        w.setObjectName(role)
    return w


class GrowingTree(QWidget):
    def __init__(self, controller):
        super().__init__()
        self.c = controller
        self.setFixedSize(110, 45)
        self.setToolTip('Drag tree to move · click or right-click for controls')
        self.setAccessibleName('Growing tree. Drag to move or click for controls.')
        self.drag_start = None
        self.animation = QTimer(self)
        self.animation.timeout.connect(self.update)

    def sync(self):
        if self.c.pet.isVisible() and self.c.store.phase == 'running' and self.c.store.settings.get('animation', True):
            self.animation.start(50)
        else:
            self.animation.stop()
        self.update()

    def paintEvent(self, event):
        p=QPainter(self); p.setRenderHint(QPainter.RenderHint.Antialiasing)
        p.scale(self.width()/110, self.height()/58)
        paint_tree(p,self.c.store.elapsed if self.c.store.pomodoro_enabled else self.c.store.growth_elapsed,
            "dead" if self.c.store.pomodoro_enabled and self.c.store.pomodoro.stage=="dead" else "resting" if self.c.store.pomodoro_enabled and self.c.store.pomodoro.stage!="focus" else self.c.store.phase,self.c.store.settings.get("animation",True),self.c.store.pomodoro_enabled)

    def mousePressEvent(self, event):
        if event.button() == Qt.MouseButton.RightButton:
            self.c.options()
            return
        if event.button() == Qt.MouseButton.LeftButton:
            self.drag_start = event.globalPosition().toPoint()
            self.origin = self.c.pet.pos()
            self.dragged = False

    def mouseMoveEvent(self, event):
        if self.drag_start is not None and event.buttons() & Qt.MouseButton.LeftButton:
            delta = event.globalPosition().toPoint() - self.drag_start
            if delta.manhattanLength() > 3:
                self.dragged = True
                self.c.pet.move(self.origin + delta)

    def mouseReleaseEvent(self, event):
        if self.drag_start is not None:
            if self.dragged:
                self.c.remember_position()
            else:
                self.c.options()
            self.drag_start = None


class Pet(QWidget):
    def __init__(self, controller):
        super().__init__(None, Qt.WindowType.Tool | Qt.WindowType.FramelessWindowHint | Qt.WindowType.WindowStaysOnTopHint)
        self.c = controller
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.setStyleSheet('background:transparent;')
        self.setFixedSize(150, 100)
        self.setWindowTitle('Focanopy')
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0); layout.setSpacing(2)
        self.cat = GrowingTree(controller)
        layout.addWidget(self.cat, alignment=Qt.AlignmentFlag.AlignCenter)
        self.timer = label('00:00:00')
        timer_font = QFont('Noto Sans Mono', 13)
        timer_font.setPointSize(13); timer_font.setWeight(QFont.Weight.DemiBold)
        self.timer.setFont(timer_font)
        self.timer.setStyleSheet('color:#addaad; background:#111b17; border-radius:10px; padding:2px 8px;')
        layout.addWidget(self.timer, alignment=Qt.AlignmentFlag.AlignCenter)
        row = QHBoxLayout(); row.setSpacing(5)
        self.play = button('▶', controller.primary)
        self.finish = button('✓', controller.finish, 'Finish session · Ctrl+F')
        for b in (self.play, self.finish):
            b.setFixedSize(26, 26); b.setIconSize(QSize(10,10))
            b.setStyleSheet('QPushButton { color:#addaad; background:#111b17; border:1px solid #567e58; border-radius:4px; padding:0; font-size:10px; } QPushButton:disabled { color:#56616b; border-color:#36414a; }')
        self.resume_finished = button('▶', controller.start, 'Resume timer')
        self.reset_finished = button('↺', controller.cancel, 'Reset timer')
        for b in (self.resume_finished,self.reset_finished): b.setFixedSize(26,26); b.setIconSize(QSize(10,10)); b.setStyleSheet(self.play.styleSheet())
        row.addWidget(self.play); row.addWidget(self.finish); row.addWidget(self.resume_finished); row.addWidget(self.reset_finished)
        layout.addLayout(row)
        row.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.drag_start = None
        self.dragged = False
        for target in (self, self.timer, self.play, self.finish, self.resume_finished, self.reset_finished):
            target.installEventFilter(self)
            target.setMouseTracking(True)
        for key in 'RSPF':
            shortcut = QShortcut(QKeySequence('Ctrl+' + key), self)
            shortcut.activated.connect(partial(controller.command, key))

    def eventFilter(self, target, event):
        kind=event.type()
        if kind==QEvent.Type.MouseButtonPress and event.button()==Qt.MouseButton.LeftButton:
            self.drag_start=event.globalPosition().toPoint(); self.drag_origin=self.pos(); self.dragged=False
        elif kind==QEvent.Type.MouseMove and self.drag_start is not None and event.buttons() & Qt.MouseButton.LeftButton:
            delta=event.globalPosition().toPoint()-self.drag_start
            if delta.manhattanLength()>QApplication.startDragDistance(): self.dragged=True
            if self.dragged:
                if isinstance(target,QPushButton): target.setDown(False)
                self.move(self.drag_origin+delta); return True
        elif kind==QEvent.Type.MouseButtonRelease and self.drag_start is not None:
            moved=self.dragged; self.drag_start=None
            if moved:
                if isinstance(target,QPushButton): target.setDown(False)
                self.c.remember_position(); return True
        return super().eventFilter(target,event)

    def sync(self):
        phase = self.c.store.phase
        self.timer.setText(clock_text(self.c.store.display_time))
        self.play.setText('')
        self.play.setIcon(action_icon('Ⅱ' if phase == 'running' else '↓' if phase == 'finished' else '▶'))
        self.play.setToolTip({'running':'Pause · Ctrl+P', 'paused':'Resume · Ctrl+S', 'finished':'Name and save session', 'idle':'Start · Ctrl+S'}[phase])
        if self.c.store.pomodoro_enabled and self.c.store.pomodoro.stage!='focus' and phase!='finished':self.play.setToolTip('Skip break & start next focus' if self.c.store.pomodoro.stage=='break' else 'Start next 25-minute focus')
        if self.c.store.pomodoro_enabled and self.c.store.pomodoro.stage=='dead':self.play.setToolTip('Dead tree · Start a new 25-minute round')
        self.play.setAccessibleName(self.play.toolTip())
        self.finish.setEnabled(phase in ('running', 'paused'))
        self.finish.setVisible(phase!='finished' and not self.c.store.pomodoro_enabled);self.resume_finished.setVisible(phase=='finished');self.reset_finished.setVisible(phase=='finished')
        self.finish.setAccessibleName('Finish session')
        self.setWindowOpacity(max(.3, min(1, self.c.store.settings.get('opacity', 1))))
        self.cat.setToolTip(('Flower focus · '+self.c.store.pomodoro.stage+' · '+clock_text(self.c.store.display_time)) if self.c.store.pomodoro_enabled else tree_text(self.c.store.growth_elapsed) + (' · Garden grown' if garden(self.c.store.growth_elapsed) else '') + ' · Drag to move; click for controls')
        self.cat.sync()

    def closeEvent(self, event):
        event.ignore(); self.c.hide_pet()


class FocusCalendar(QCalendarWidget):
    def __init__(self, store):
        super().__init__()
        self.store = store
        self.setVerticalHeaderFormat(QCalendarWidget.VerticalHeaderFormat.NoVerticalHeader)
        self.setGridVisible(False)
        self.setMinimumSize(440, 430)

    def paintCell(self, painter, rect, day):
        painter.save()
        amount = sum(r['duration'] for r in self.store.records_on(day.toPython()))
        selected = day == self.selectedDate()
        today = day == QDate.currentDate()
        r = QRectF(rect).adjusted(3, 3, -3, -3)
        painter.fillRect(rect, QColor('#111b17'))
        bg = QColor('#1e2c24')
        if amount:
            intensity = min(amount / 14400, 1)
            bg = QColor(int(30+intensity*20), int(55+intensity*35), int(35+intensity*20))
        painter.setBrush(bg)
        painter.setPen(QPen(QColor('#addaad') if selected else QColor('#2b4030'), 2 if selected else 1))
        painter.drawRoundedRect(r, 7, 7)
        painter.setPen(QColor('#e5edf5') if day.month() == self.monthShown() else QColor('#657080'))
        painter.setFont(QFont(QApplication.font().family(), 10, QFont.Weight.Bold if today else QFont.Weight.Normal))
        painter.drawText(QPointF(r.left()+8, r.top()+18), str(day.day()))
        painter.setPen(QColor('#addaad') if amount else QColor('#56616c'))
        painter.setFont(QFont(QApplication.font().family(), 9))
        painter.drawText(QPointF(r.left()+8, r.bottom()-8), duration_text(amount) if amount else '—')
        if amount:
            painter.setPen(QColor('#b5deac') if garden(amount) else QColor('#9fbda8')); painter.setFont(QFont(QApplication.font().family(),8))
            painter.drawText(QPointF(r.left()+8,r.top()+33),cell_text(amount))
        painter.restore()


class History(QWidget):
    def __init__(self, controller):
        super().__init__()
        self.c = controller; self.deleted = None
        self.setWindowTitle('Focanopy · Focus journal')
        self.resize(940, 710)
        root = QVBoxLayout(self); root.setContentsMargins(25, 25, 25, 25); root.setSpacing(18)
        head = QHBoxLayout(); head.addWidget(label('Focus journal', 'title')); head.addStretch()
        head.addWidget(button('Account / Sync', controller.accounts.show)); head.addWidget(button('Today', self.today)); head.addWidget(button('Import macOS history…', self.import_history))
        root.addLayout(head)
        self.summary = label('', 'total'); root.addWidget(self.summary)
        body = QHBoxLayout()
        self.calendar = FocusCalendar(controller.store)
        self.calendar.selectionChanged.connect(self.refresh)
        self.calendar.currentPageChanged.connect(lambda *_: self.refresh())
        body.addWidget(self.calendar, 3)
        detail = QVBoxLayout()
        self.scope = QComboBox(); self.scope.addItems(['Day', 'Week', 'Month']); self.scope.currentTextChanged.connect(self.refresh)
        detail.addWidget(self.scope)
        self.title = label('', 'title'); detail.addWidget(self.title)
        self.total = label('', 'total'); detail.addWidget(self.total)
        self.forest = label('', 'total'); self.forest.setWordWrap(True); self.forest.setStyleSheet('color:#b5deac; background:#22372c; border:1px solid #41664b; border-radius:9px; padding:10px; font-size:15px;'); detail.addWidget(self.forest)
        self.devices = label('', 'subtitle'); self.devices.setWordWrap(True); detail.addWidget(self.devices)
        self.undo = button('Undo last deletion', self.restore); self.undo.hide(); detail.addWidget(self.undo)
        scroll = QScrollArea(); scroll.setWidgetResizable(True)
        self.list = QWidget(); self.rows = QVBoxLayout(self.list); self.rows.setAlignment(Qt.AlignmentFlag.AlignTop)
        scroll.setWidget(self.list); detail.addWidget(scroll, 1)
        body.addLayout(detail, 2); root.addLayout(body, 1)
        root.addWidget(label('Daily totals exclude pauses. Sessions count toward their start date.', 'subtitle'))
        self.refresh()

    def today(self):
        self.calendar.setSelectedDate(QDate.currentDate()); self.scope.setCurrentText('Day'); self.refresh()

    def refresh(self):
        selected = self.calendar.selectedDate().toPython()
        scope = self.scope.currentText()
        def included(r):
            d = datetime.fromtimestamp(r['start']).date()
            if scope == 'Day': return d == selected
            if scope == 'Week': return selected - timedelta(days=selected.weekday()) <= d < selected - timedelta(days=selected.weekday()) + timedelta(days=7)
            return (d.year, d.month) == (selected.year, selected.month)
        today = date.today()
        totals = [sum(r['duration'] for r in self.c.store.records_on(d)) for d in (today, today - timedelta(days=1))]
        self.summary.setText('Today  ' + duration_text(totals[0]) + '     Yesterday  ' + duration_text(totals[1]))
        records = sorted(filter(included, self.c.store.sessions), key=lambda r:r['start'], reverse=True)
        self.title.setText(selected.strftime('%b %d') if scope == 'Day' else scope + ' of ' + selected.strftime('%b %d'))
        self.total.setText(duration_text(sum(r['duration'] for r in records)) + f' · {len(records)} sessions')
        daily={}
        for r in records:
            d=datetime.fromtimestamp(r['start']).date(); daily[d]=daily.get(d,0)+r['duration']
        amount=sum(r['duration'] for r in records)
        self.forest.setText('Garden grown · '+tree_text(amount) if scope=='Day' and garden(amount) else forest_summary(amount,daily.values()))
        devices = {}
        for r in records: devices[device_label(r)] = devices.get(device_label(r), 0) + r['duration']
        self.devices.setText('  ·  '.join(name + ' ' + duration_text(value) for name, value in sorted(devices.items())))
        while self.rows.count():
            item = self.rows.takeAt(0)
            if item.widget(): item.widget().hide(); item.widget().deleteLater()
        if not records:
            self.rows.addWidget(label('No sessions in this period.'))
        for record in records:
            card = QFrame(); card.setObjectName('card'); layout = QVBoxLayout(card)
            name = label(record['name']); name.setWordWrap(True); layout.addWidget(name)
            layout.addWidget(label(tree_text(record['duration']), 'subtitle'))
            row = QHBoxLayout()
            row.addWidget(label(device_label(record) + ' · ' + datetime.fromtimestamp(record['start']).strftime('%b %d · %H:%M') + '  ' + duration_text(record['duration']), 'subtitle'))
            row.addStretch()
            row.addWidget(button('✎', partial(self.rename, record), 'Rename session'))
            row.addWidget(button('×', partial(self.delete, record), 'Delete session'))
            layout.addLayout(row); self.rows.addWidget(card)
        self.calendar.updateCells()

    def rename(self, record):
        name, ok = QInputDialog.getText(self, 'Rename session', 'Task name', text=record['name'])
        if ok:
            self.c.safe(lambda: self.c.store.rename(record['id'], name)); self.refresh()

    def delete(self, record):
        if QMessageBox.question(self, 'Delete session?', 'Delete “' + record['name'] + '”? You can undo the latest deletion.', QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel, QMessageBox.StandardButton.Cancel) == QMessageBox.StandardButton.Yes:
            try:
                self.deleted = self.c.store.delete(record['id']); self.undo.show(); self.refresh()
            except Exception as error: self.c.error(str(error))

    def restore(self):
        if self.deleted:
            if self.c.safe(lambda:self.c.store.restore(self.deleted)):
                self.deleted = None; self.undo.hide(); self.refresh()

    def import_history(self):
        filename, _ = QFileDialog.getOpenFileName(self, 'Import existing Swift macOS history', '', 'JSON files (*.json)')
        if filename:
            self.c.safe(lambda:self.c.store.import_swift(filename)); self.refresh()


class Controller:
    def __init__(self, app, store):
        self.app, self.store = app, store
        self.modal = False; self.history = None; self.popup = None
        self.pet = Pet(self)
        self.shortcuts = Shortcuts(app, self.command, self.error, store.settings.get('gnome_shortcuts', False) and gnome_available())
        self.tray = QSystemTrayIcon(self.icon(), app)
        self.menu = QMenu()
        self.tray.setContextMenu(self.menu)
        self.menu.aboutToShow.connect(self.tray_menu)
        self.tray.activated.connect(lambda reason: self.show_pet() if reason == QSystemTrayIcon.ActivationReason.Trigger else None)
        self.tray.show()
        self.restore_position(); self.show_pet()
        self.tick = QTimer(); self.tick.timeout.connect(self.refresh); self.tick.start(1000)
        from .account import Accounts
        self.accounts = Accounts(self)
        from .focus_blocking import FocusBlocker
        self.blocker=FocusBlocker(store,self.focus_notice)
        app.aboutToQuit.connect(self.blocker.close)
        self.persist = QTimer(); self.persist.timeout.connect(lambda:self.safe(self.store.checkpoint)); self.persist.start(15000)
        if not QSystemTrayIcon.isSystemTrayAvailable():
            self.pet.setToolTip('System tray unavailable. Click the tree for all controls, including Quit.')

    def icon(self):
        pix = QPixmap(32, 32); pix.fill(Qt.GlobalColor.transparent)
        p = QPainter(pix); p.setRenderHint(QPainter.RenderHint.Antialiasing)
        p.scale(32/110,32/58); paint_tree(p,3600,'idle',False); p.end()
        return QIcon(pix)

    def safe(self, operation):
        try: operation(); return True
        except Exception as error: self.error(str(error)); return False

    def error(self, message):
        # Non-modal notification avoids reentrant dialogs during shortcut registration.
        if hasattr(self, 'tray'):
            self.tray.showMessage('Focanopy', message, QSystemTrayIcon.MessageIcon.Warning)
        print('Focanopy:', message, file=sys.stderr)

    def scope(self):
        self.shortcuts.scope(self.pet.isVisible(), self.modal,finish_allowed=not self.store.pomodoro_enabled)

    def focus_notice(self,title,message):
        self.tray.showMessage(title,message)
        if sys.platform.startswith('linux') and not QSystemTrayIcon.isSystemTrayAvailable():
            import shutil,subprocess
            if shutil.which('notify-send'):
                subprocess.Popen(['notify-send','--app-name=Focanopy',title,message],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)

    def refresh(self):
        notice=self.store.advance_pomodoro()
        if notice:self.focus_notice('Focanopy · Flower focus',notice);self.show_pet()
        if hasattr(self,'blocker'):self.blocker.tick()
        self.pet.sync();self.scope()
        self.tray.setToolTip('Focanopy · ' + clock_text(self.store.display_time) + ' · ' + (self.store.pomodoro.stage if self.store.pomodoro_enabled else self.store.phase))
        # Windows/Linux trays have icon + tooltip, not macOS-style variable-width text labels.

    def restore_position(self):
        pos = self.store.settings.get('position')
        if pos: self.pet.move(*pos)
        else:
            rect = self.app.primaryScreen().availableGeometry()
            self.pet.move(rect.right() - 180, rect.bottom() - 140)
        self.clamp()

    def clamp(self):
        frame = self.pet.frameGeometry()
        screens = self.app.screens()
        screen = max(screens, key=lambda s: (s.availableGeometry().intersected(frame).width() * s.availableGeometry().intersected(frame).height()))
        r = screen.availableGeometry()
        self.pet.move(max(r.left(), min(self.pet.x(), r.right()-self.pet.width()+1)), max(r.top(), min(self.pet.y(), r.bottom()-self.pet.height()+1)))

    def remember_position(self):
        self.clamp(); self.safe(lambda:self.store.setting('position', [self.pet.x(), self.pet.y()]))

    def show_pet(self):
        self.clamp(); self.pet.show(); self.pet.raise_(); self.pet.sync(); self.scope()

    def hide_pet(self):
        self.remember_position(); self.pet.hide(); self.pet.cat.sync(); self.scope()
        if self.popup: self.popup.close()

    def command(self, key):
        if key == 'R':
            self.hide_pet() if self.pet.isVisible() else self.show_pet()
            return
        if not self.pet.isVisible() or self.modal: return
        if key == 'S': self.safe(self.store.start)
        elif key == 'P': self.safe(self.store.pause)
        elif key == 'F' and self.store.phase != 'idle': self.finish()
        self.refresh()

    def primary(self):
        if self.store.phase == 'finished': self.finish()
        elif self.store.phase == 'running': self.safe(self.store.pause)
        else: self.safe(self.store.start)
        self.refresh()

    def start(self):
        self.safe((self.store.resume_finished if self.store.phase=="finished" else self.store.start)); self.show_pet(); self.refresh()

    def finish(self, reveal=True):
        if self.modal or self.store.phase == 'idle' or (self.store.pomodoro_enabled and self.store.phase!='finished'): return False
        if not self.safe(self.store.finish): return False
        self.refresh(); self.modal = True; self.scope()
        dialog = QDialog(self.pet); dialog.setWindowTitle('Save session'); dialog.setMinimumWidth(350)
        layout = QVBoxLayout(dialog); layout.setContentsMargins(22,22,22,22); layout.setSpacing(16)
        layout.addWidget(label('What did you work on?', 'title'))
        layout.addWidget(label(duration_text(self.store.elapsed) + ' of focus', 'subtitle'))
        field = QComboBox(); field.setEditable(True)
        field.addItems(['Job Apply', 'Upskill', 'Interview Prep', 'Work']); field.setCurrentIndex(-1)
        field.lineEdit().setPlaceholderText('Choose a category or type your own')
        layout.addWidget(field); layout.addWidget(label('Close to save later, resume, or reset from timer controls.', 'subtitle'))
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Save | QDialogButtonBox.StandardButton.Close)
        buttons.accepted.connect(dialog.accept); buttons.rejected.connect(dialog.reject); layout.addWidget(buttons)
        accepted = dialog.exec() == QDialog.DialogCode.Accepted
        self.modal = False; self.scope()
        if accepted:
            try:
                record = self.store.save(field.currentText()); self.refresh()
                if reveal and record: self.show_history(datetime.fromtimestamp(record['start']).date())
                return self.store.phase == 'idle'
            except Exception as error: self.error(str(error))
        return False

    def cancel(self):
        if self.store.phase == 'idle': return
        self.modal = True; self.scope()
        answer = QMessageBox.question(self.pet, 'Discard session?', 'An unfinished tree will die. Completed 25-minute rounds stay saved.' if self.store.pomodoro_enabled else 'Discard this unsaved session?', QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel, QMessageBox.StandardButton.Cancel)
        self.modal = False; self.scope()
        if answer == QMessageBox.StandardButton.Yes: self.safe(self.store.cancel_pomodoro if self.store.pomodoro_enabled else self.store.discard); self.refresh()

    def show_history(self, selected=None):
        if not self.history: self.history = History(self)
        if selected:
            self.history.calendar.setSelectedDate(QDate(selected.year, selected.month, selected.day)); self.history.scope.setCurrentText('Day')
        self.history.refresh(); self.history.show(); self.history.raise_(); self.history.activateWindow()

    def options(self):
        if self.popup and self.popup.isVisible(): self.popup.close(); return
        self.popup = QDialog(self.pet, Qt.WindowType.Popup)
        container = QWidget()
        layout = QVBoxLayout(container); layout.setContentsMargins(16,16,16,16); layout.setSpacing(8)
        header = QHBoxLayout(); header.addWidget(label('Pet controls', 'title')); header.addWidget(button('×', self.popup.close)); layout.addLayout(header)
        for text, action in [('Start / Resume · Ctrl+S', self.start), ('Pause · Ctrl+P', lambda:self.safe(self.store.pause)), ('Finish · Ctrl+F', self.finish), ('Cancel session…', self.cancel), ('Hide / Close pet · Ctrl+R', self.hide_pet), ('Pomodoro & App Blocking', self.focus_settings), ('Session history', self.show_history), ('Account / Sync', self.accounts.show)]:
            if self.store.pomodoro_enabled and action==self.finish:continue
            def invoke(fn=action): self.popup.close(); QTimer.singleShot(0, fn)
            layout.addWidget(button(text, invoke))
        opacity_text = label('Pet opacity'); layout.addWidget(opacity_text)
        slider = QSlider(Qt.Orientation.Horizontal); slider.setRange(30,100); slider.setValue(int(self.store.settings.get('opacity',1)*100))
        def opacity(value):
            opacity_text.setText(f'Pet opacity · {value}%'); self.pet.setWindowOpacity(value/100)
        slider.valueChanged.connect(opacity); slider.sliderReleased.connect(lambda:self.safe(lambda:self.store.setting('opacity',slider.value()/100)))
        layout.addWidget(slider)
        animate = QCheckBox('Animate tree and water'); animate.setChecked(self.store.settings.get('animation',True))
        animate.toggled.connect(lambda value: (self.safe(lambda:self.store.setting('animation',value)), self.pet.cat.sync())); layout.addWidget(animate)
        if gnome_available():
            layout.addWidget(button('Enable GNOME Ctrl shortcuts', self.enable_gnome))
            if self.store.settings.get('gnome_shortcuts'): layout.addWidget(button('Remove GNOME shortcuts', self.disable_gnome))
        layout.addWidget(button('Quit App…', self.quit))
        scroll = QScrollArea(); scroll.setWidgetResizable(True); scroll.setWidget(container)
        outer = QVBoxLayout(self.popup); outer.setContentsMargins(0,0,0,0); outer.addWidget(scroll)
        screen = self.pet.screen().availableGeometry()
        self.popup.setFixedWidth(350); self.popup.resize(350, min(650, screen.height()-40))
        pos = self.pet.pos() + QPoint(-80, -self.popup.height())
        self.popup.move(max(screen.left(), min(pos.x(), screen.right()-350)), max(screen.top(), min(pos.y(), screen.bottom()-self.popup.height())))
        self.popup.show()

    def focus_settings(self,preview=False):
        dialog=QDialog(self.pet);dialog.setWindowTitle('Focanopy · Focus garden');dialog.resize(470,690)
        dialog.setStyleSheet('QScrollBar:vertical{background:#17231b;width:8px;margin:0;} QScrollBar::handle:vertical{background:#3a4e3e;border-radius:4px;min-height:30px;} QScrollBar::add-line:vertical,QScrollBar::sub-line:vertical{height:0;}')
        shell=QVBoxLayout(dialog);shell.setContentsMargins(4,4,4,4)
        scroll=QScrollArea();scroll.setWidgetResizable(True);container=QWidget();scroll.setWidget(container);shell.addWidget(scroll)
        layout=QVBoxLayout(container);layout.setContentsMargins(20,20,20,20);layout.setSpacing(16)
        layout.addWidget(label('Your focus garden','title'));layout.addWidget(label('A little time. A little growth.','subtitle'))
        mode=QCheckBox('Pomodoro effect · One flower, 25 minutes');mode.setChecked(self.store.pomodoro_enabled)
        mode.setStyleSheet('padding:14px; background:#1e2c24; border-radius:12px;');layout.addWidget(mode)
        hero=QFrame();hero.setObjectName('focusHero');hero.setStyleSheet('QFrame#focusHero {background:#1e2c24;border:1px solid #374c3d;border-radius:22px;} QFrame#focusHero QLabel {background:transparent;}')
        box=QVBoxLayout(hero);box.setContentsMargins(22,20,22,20);box.setSpacing(12)
        badge=label('FLOWER FOCUS');badge.setAlignment(Qt.AlignmentFlag.AlignCenter);badge.setStyleSheet('color:#addaad;font-size:10px;font-weight:600;');box.addWidget(badge)
        heading=label('Grow one little flower');heading.setAlignment(Qt.AlignmentFlag.AlignCenter);heading.setStyleSheet('font-size:21px;color:#eef0dc;');box.addWidget(heading)
        tree=GrowingTree(self);tree.setFixedSize(245,129);tree.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents);tree.setStyleSheet('background:transparent;');box.addWidget(tree,0,Qt.AlignmentFlag.AlignHCenter)
        status=label('');status.setAlignment(Qt.AlignmentFlag.AlignCenter);status.setFont(QFont('Noto Sans Mono',35));status.setStyleSheet("color:#eef0dc;font-family:'Noto Sans Mono';font-size:46px;");box.addWidget(status)
        hint=label('');hint.setWordWrap(True);hint.setAlignment(Qt.AlignmentFlag.AlignCenter);hint.setStyleSheet('color:#a1b5a5;font-size:11px;');box.addWidget(hint)
        progress=QProgressBar();progress.setRange(0,1000);progress.setTextVisible(False);progress.setFixedHeight(5);progress.setStyleSheet('QProgressBar{background:#2e4133;border:0;border-radius:2px;} QProgressBar::chunk{background:#addaad;border-radius:2px;}');box.addWidget(progress)
        controls=QHBoxLayout();controls.addStretch()
        play=button('▶',lambda:(self.primary(),update()),'Start or pause focus');play.setFixedSize(48,44);play.setStyleSheet('background:#395b3d;border:1px solid #567e58;border-radius:12px;');controls.addWidget(play)
        cancel_round=button('×',lambda:(self.cancel(),update()),'Cancel round · tree will die');cancel_round.setFixedSize(44,44);cancel_round.setStyleSheet('color:#cf9e89;background:#17231b;border-radius:12px;');controls.addWidget(cancel_round)
        next_round=button('Start next round',lambda:(self.safe(self.store.start),self.refresh(),update()));controls.addWidget(next_round);controls.addStretch();box.addLayout(controls)
        minutes=QComboBox();minutes.addItems(['5-minute break','10-minute break']);minutes.setCurrentIndex(int(self.store.pomodoro.break_minutes==10));box.addWidget(minutes)
        foot=label('Completed rounds save automatically.\nThe next round is always your choice.','subtitle');foot.setAlignment(Qt.AlignmentFlag.AlignCenter);box.addWidget(foot)
        end_cycle=button('End Pomodoro',lambda:(self.safe(self.store.cancel_pomodoro),self.refresh(),update()));box.addWidget(end_cycle)
        layout.addWidget(hero)
        def apply_mode():
            self.safe(lambda:self.store.select_mode(mode.isChecked(),10 if minutes.currentIndex() else 5));self.refresh();update()
        mode.toggled.connect(apply_mode);minutes.currentIndexChanged.connect(apply_mode)
        layout.addWidget(label('A little room for what matters','subtitle'))
        blocking=QCheckBox('Block apps while the timer runs');blocking.setChecked(self.store.settings.get('focus_blocking',False));layout.addWidget(blocking)
        blocking.toggled.connect(lambda value:self.safe(lambda:self.store.setting('focus_blocking',value)))
        note=label('Apps stay open and keep their data. Pauses and Pomodoro breaks allow apps. You can turn blocking off at any time.');note.setWordWrap(True);layout.addWidget(note)
        strategy=QComboBox();strategy.addItems(['Block selected apps','Block all except browsers & exclusions']);strategy.setCurrentIndex(int(self.store.settings.get('focus_block_all',False)));layout.addWidget(strategy)
        strategy.currentIndexChanged.connect(lambda index:self.store.setting('focus_block_all',bool(index)))
        chosen=list(self.store.settings.get('excluded_apps' if strategy.currentIndex() else 'blocked_apps',[]));selected=label('\n'.join(chosen) or 'No apps selected');selected.setWordWrap(True);selected.setMaximumHeight(80);layout.addWidget(selected)
        def choose():
            if sys.platform=='win32':
                files,_=QFileDialog.getOpenFileNames(dialog,'Select distracting applications','','Applications (*.exe)')
                protected={'explorer.exe','taskmgr.exe','winlogon.exe','logonui.exe','focanopy.exe'}
                items=[f for f in files if Path(f).name.lower() not in protected]
            else:
                from .focus_blocking import desktop_apps
                chooser=QDialog(dialog);chooser.setWindowTitle('Choose apps to minimize');chooser.resize(420,500);box=QVBoxLayout(chooser);listing=QListWidget();listing.setSelectionMode(QAbstractItemView.SelectionMode.MultiSelection)
                for identifier,name in desktop_apps():
                    item=QListWidgetItem(name);item.setData(Qt.ItemDataRole.UserRole,identifier);listing.addItem(item);item.setSelected(identifier in chosen)
                box.addWidget(listing);buttons=QDialogButtonBox(QDialogButtonBox.StandardButton.Ok|QDialogButtonBox.StandardButton.Cancel);buttons.accepted.connect(chooser.accept);buttons.rejected.connect(chooser.reject);box.addWidget(buttons)
                if chooser.exec()!=QDialog.DialogCode.Accepted:return
                items=[item.data(Qt.ItemDataRole.UserRole) for item in listing.selectedItems()]
            chosen[:]=items;self.store.setting('excluded_apps' if strategy.currentIndex() else 'blocked_apps',items);selected.setText('\n'.join(items) or 'No apps selected')
        def change_selection():
            chosen[:]=self.store.settings.get('excluded_apps' if strategy.currentIndex() else 'blocked_apps',[]);selected.setText('\n'.join(chosen) or 'No apps selected')
        strategy.currentIndexChanged.connect(change_selection)
        layout.addWidget(button('Choose blocked apps / allowed exclusions…',choose))
        if sys.platform.startswith('linux'):
            help_text=label('Ubuntu/GNOME 45+ needs the optional Focanopy Flower Focus extension. Install it here, then enable it yourself in GNOME Extensions.');help_text.setWordWrap(True);layout.addWidget(help_text)
            def install():
                from .focus_blocking import install_gnome_focus
                try:QMessageBox.information(dialog,'Enable GNOME focus blocking',install_gnome_focus())
                except Exception as error:QMessageBox.warning(dialog,'GNOME setup',str(error))
            layout.addWidget(button('Install optional GNOME focus extension',install))
        def update():
            s=self.store;stage=s.pomodoro.stage
            hero.setVisible(s.pomodoro_enabled)
            badge.setText({'break':'REST & RESET','ready':'READY TO GROW','dead':'ROUND CANCELLED'}.get(stage,'WATERING PAUSED' if s.phase=='paused' else 'FLOWER FOCUS'))
            heading.setText({'break':'A little room to breathe','ready':'Your next flower is waiting','dead':'A fresh start awaits'}.get(stage,'Grow one little flower'))
            seconds=max(0,int(s.display_time));status.setText(f'{seconds//60:02}:{seconds%60:02}')
            hint.setText({'break':'Flower saved. Stretch, breathe, move a little.','ready':'Start when you’re ready. No rush.','dead':'This tree died. Begin again when you’re ready.'}.get(stage,'Your flower is resting.' if s.phase=='paused' else 'A little water. A little growth.'))
            progress.setValue(0 if stage=='dead' else 1000 if stage!='focus' else int(max(0,min(1,1-s.display_time/1500))*1000))
            next_round.setVisible(stage in ('break','ready'));end_cycle.setVisible(stage in ('break','ready'))
            play.setVisible(stage not in ('break','ready'));play.setIcon(action_icon('Ⅱ' if s.phase=='running' else '▶'))
            cancel_round.setVisible(s.phase!='idle' and stage=='focus')
            mode.setEnabled(s.phase=='idle' or stage=='dead');minutes.setVisible(s.phase=='idle')
            tree.update()
            if dialog.isVisible() and not dialog.isMinimized() and s.phase=='running' and s.settings.get('animation',True):tree.animation.start(50)
            else:tree.animation.stop()
        timer=QTimer(dialog);timer.timeout.connect(update);timer.start(500);update()
        shell.addWidget(button('Close',dialog.accept));dialog.finished.connect(lambda: (timer.stop(),tree.animation.stop()))
        if preview:return dialog
        dialog.exec();timer.stop();tree.animation.stop();dialog.deleteLater()

    def enable_gnome(self):
        if self.safe(install_gnome):
            self.store.setting('gnome_shortcuts',True); self.shortcuts.gnome_enabled = True
            self.shortcuts.visible = None; self.scope()

    def disable_gnome(self):
        self.shortcuts.timer.stop()
        if self.safe(remove_gnome):
            self.shortcuts.gnome_enabled = False; self.store.setting('gnome_shortcuts',False)

    def tray_menu(self):
        self.menu.clear()
        self.menu.addAction('Focanopy · ' + clock_text(self.store.elapsed)).setEnabled(False)
        for text, action in [('Start / Resume',self.start),('Pause',lambda:self.safe(self.store.pause)),('Finish Session',self.finish),('Show Pet',self.show_pet),('Hide Pet',self.hide_pet),('Pomodoro & App Blocking',self.focus_settings),('Session History',self.show_history),('Account / Sync',self.accounts.show),('Quit App…',self.quit)]:
            if self.store.pomodoro_enabled and action==self.finish:continue
            self.menu.addAction(text, action)

    def quit(self):
        if self.modal: return
        if self.store.pomodoro_enabled and self.store.phase!='idle':
            answer=QMessageBox.question(self.pet,'Quit Pomodoro?','Keep timer across relaunch? Choose No to cancel the round; its unfinished tree dies. Completed rounds stay saved.',QMessageBox.StandardButton.Yes|QMessageBox.StandardButton.No|QMessageBox.StandardButton.Cancel,QMessageBox.StandardButton.Cancel)
            if answer==QMessageBox.StandardButton.Cancel:return
            if answer==QMessageBox.StandardButton.No:self.store.cancel_pomodoro()
            self.store.checkpoint();self.remember_position();self.shortcuts.close();self.tray.hide();self.app.quit();return
        if self.store.phase != 'idle':
            self.modal = True; self.scope()
            box = QMessageBox(self.pet); box.setWindowTitle('Quit Focanopy?'); box.setText('Save or discard your active session before quitting.')
            save = box.addButton('Save & Quit', QMessageBox.ButtonRole.AcceptRole)
            discard = box.addButton('Discard & Quit', QMessageBox.ButtonRole.DestructiveRole)
            cancel = box.addButton('Cancel', QMessageBox.ButtonRole.RejectRole); box.setDefaultButton(cancel)
            box.exec(); self.modal = False; self.scope()
            if box.clickedButton() == save:
                if not self.finish(reveal=False): return
            elif box.clickedButton() == discard:
                if not self.safe(self.store.discard): return
            else: return
        self.remember_position(); self.shortcuts.close(); self.tray.hide(); self.app.quit()
