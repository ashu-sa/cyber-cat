import calendar
import math
import os
import sys
import time
from pathlib import Path
from datetime import date, datetime, timedelta
from functools import partial
from PySide6.QtCore import Qt, QDate, QRectF, QTimer, QPoint, QPointF, QSettings
from PySide6.QtGui import QColor, QFont, QFontDatabase, QIcon, QPainter, QPainterPath, QPalette, QPen, QPixmap, QPolygonF, QShortcut, QKeySequence
from PySide6.QtWidgets import (QApplication, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QDialog, QComboBox, QDialogButtonBox, QSlider, QCheckBox, QScrollArea, QCalendarWidget,
    QSystemTrayIcon, QMenu, QMessageBox, QInputDialog, QFileDialog, QFrame)
from .model import Store, clock_text, duration_text
from .shortcuts import Shortcuts, gnome_available, install_gnome, remove_gnome

STYLE = '''
QWidget { background:#141820; color:#e6edf5; font-size:13px; }
QDialog, QFrame#card { background:#1e2430; border-radius:12px; }
QPushButton { background:#273140; border:1px solid #354454; border-radius:7px; padding:8px 12px; }
QPushButton:hover { background:#344758; border-color:#30c3e8; }
QPushButton:disabled { color:#63707d; }
QPushButton#primary { color:#30c3e8; }
QComboBox { background:#151b24; border:1px solid #48586c; border-radius:6px; padding:9px; min-width:270px; }
QLabel#subtitle { color:#96a4b5; font-size:11px; }
QLabel#title { font-size:25px; font-weight:700; }
QLabel#total { color:#30c3e8; font-size:25px; font-weight:600; }
QScrollArea { border:0; } QCalendarWidget QAbstractItemView { selection-background-color:#215b6b; }
'''


def apply_theme(app):
    app.setStyle('Fusion')
    assets = Path(__file__).resolve().parent / 'assets'
    for filename in ('NotoSans.ttf', 'NotoSansMono.ttf'):
        if QFontDatabase.addApplicationFont(str(assets / filename)) < 0:
            raise RuntimeError('Missing bundled font: ' + filename)
    app.setFont(QFont('Noto Sans', 10))
    palette = QPalette()
    for role, color in [('Window','#141820'),('WindowText','#e6edf5'),('Base','#1e2430'),('AlternateBase','#232b36'),('Text','#e6edf5'),('Button','#273140'),('ButtonText','#e6edf5'),('Highlight','#215b6b'),('HighlightedText','#ffffff'),('ToolTipBase','#273140'),('ToolTipText','#e6edf5')]:
        palette.setColor(getattr(QPalette.ColorRole, role), QColor(color))
    app.setPalette(palette); app.setStyleSheet(STYLE)


def button(text, callback, tooltip=''):
    b = QPushButton(text)
    b.clicked.connect(callback)
    b.setToolTip(tooltip)
    return b


def label(text, role=None):
    w = QLabel(text)
    if role:
        w.setObjectName(role)
    return w


class Cat(QWidget):
    def __init__(self, controller):
        super().__init__()
        self.c = controller
        self.setFixedSize(110, 45)
        self.setToolTip('Drag cat to move · click or right-click for controls')
        self.setAccessibleName('Cyber cat. Drag to move or click for controls.')
        self.drag_start = None
        self.animation = QTimer(self)
        self.animation.timeout.connect(self.update)

    def sync(self):
        if self.c.pet.isVisible() and self.c.store.settings.get('animation', True):
            self.animation.start(83)
        else:
            self.animation.stop()
        self.update()

    def paintEvent(self, event):
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)
        p.scale(.84, .78)
        p.translate(8, 1)
        running = self.c.store.phase == 'running'
        animate = self.animation.isActive()
        t = time.monotonic() if animate else 1
        wag = math.sin(t * (2.8 if running else 1.4)) * 3 if animate else 0
        if animate:
            p.translate(0, math.sin(t * 1.8) * .8)
        ink = QColor('#111922')
        accent = QColor('#ac8aff' if self.c.store.phase == 'paused' else '#30c3e8')
        tail = QPainterPath()
        tail.moveTo(81, 48)
        tail.cubicTo(102, 56 + wag, 107, 32 + wag, 94, 36 + wag)
        p.setPen(QPen(ink, 6, Qt.PenStyle.SolidLine, Qt.PenCapStyle.RoundCap))
        p.drawPath(tail)
        p.setPen(Qt.PenStyle.NoPen)
        p.setBrush(ink)
        for coords in ((25, 20, 26, 1, 43, 13), (64, 12, 82, 1, 83, 24)):
            path = QPainterPath()
            path.moveTo(coords[0], coords[1]); path.lineTo(coords[2], coords[3]); path.lineTo(coords[4], coords[5]); path.closeSubpath()
            p.drawPath(path)
        p.drawRoundedRect(QRectF(29, 29, 52, 24), 10, 10)
        p.setPen(QPen(accent, .8)); p.drawRoundedRect(QRectF(24, 12, 60, 31), 11, 11)
        p.setPen(Qt.PenStyle.NoPen); p.setBrush(accent)
        blink = self.c.store.phase == 'paused' or (animate and t % 5.5 < .18)
        for x in (37, 63):
            p.drawRoundedRect(QRectF(x, 25, 9, 2 if blink else 5), 2, 2)
        p.setPen(QPen(accent, 1))
        p.drawLine(49, 34, 54, 36); p.drawLine(54, 36, 59, 34)
        p.setBrush(ink); p.setPen(Qt.PenStyle.NoPen)
        p.drawRoundedRect(QRectF(34, 47, 12, 7), 3, 3); p.drawRoundedRect(QRectF(66, 47, 12, 7), 3, 3)

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
        self.setWindowTitle('Cyber Cat')
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0); layout.setSpacing(2)
        self.cat = Cat(controller)
        layout.addWidget(self.cat, alignment=Qt.AlignmentFlag.AlignCenter)
        self.timer = label('00:00:00')
        timer_font = QFont('Noto Sans Mono', 13)
        timer_font.setPointSize(13); timer_font.setWeight(QFont.Weight.DemiBold)
        self.timer.setFont(timer_font)
        self.timer.setStyleSheet('color:#30c3e8; background:#10151d; border-radius:10px; padding:2px 8px;')
        layout.addWidget(self.timer, alignment=Qt.AlignmentFlag.AlignCenter)
        row = QHBoxLayout(); row.setSpacing(5)
        self.play = button('▶', controller.primary)
        self.finish = button('✓', controller.finish, 'Finish session · Ctrl+F')
        for b in (self.play, self.finish):
            b.setFixedSize(26, 26)
            b.setStyleSheet('QPushButton { color:#30c3e8; background:#101820; border:1px solid #287185; border-radius:4px; padding:0; font-size:10px; } QPushButton:disabled { color:#56616b; border-color:#36414a; }')
        row.addWidget(self.play); row.addWidget(self.finish)
        layout.addLayout(row)
        row.setAlignment(Qt.AlignmentFlag.AlignCenter)
        for key in 'RSPF':
            shortcut = QShortcut(QKeySequence('Ctrl+' + key), self)
            shortcut.activated.connect(partial(controller.command, key))

    def sync(self):
        phase = self.c.store.phase
        self.timer.setText(clock_text(self.c.store.elapsed))
        self.play.setText('Ⅱ' if phase == 'running' else '↓' if phase == 'finished' else '▶')
        self.play.setToolTip({'running':'Pause · Ctrl+P', 'paused':'Resume · Ctrl+S', 'finished':'Name and save session', 'idle':'Start · Ctrl+S'}[phase])
        self.play.setAccessibleName(self.play.toolTip())
        self.finish.setEnabled(phase in ('running', 'paused'))
        self.finish.setAccessibleName('Finish session')
        self.setWindowOpacity(max(.3, min(1, self.c.store.settings.get('opacity', 1))))
        self.cat.sync()

    def closeEvent(self, event):
        event.ignore(); self.c.hide_pet()


class FocusCalendar(QCalendarWidget):
    def __init__(self, store):
        super().__init__()
        self.store = store
        self.setVerticalHeaderFormat(QCalendarWidget.VerticalHeaderFormat.NoVerticalHeader)
        self.setGridVisible(False)
        self.setMinimumSize(440, 370)

    def paintCell(self, painter, rect, day):
        painter.save()
        amount = sum(r['duration'] for r in self.store.records_on(day.toPython()))
        selected = day == self.selectedDate()
        today = day == QDate.currentDate()
        r = QRectF(rect).adjusted(3, 3, -3, -3)
        painter.fillRect(rect, QColor('#141820'))
        bg = QColor('#1e2834')
        if amount:
            intensity = min(amount / 14400, 1)
            bg = QColor(25, int(55 + intensity * 55), int(70 + intensity * 65))
        painter.setBrush(bg)
        painter.setPen(QPen(QColor('#30c3e8') if selected else QColor('#29313e'), 2 if selected else 1))
        painter.drawRoundedRect(r, 7, 7)
        painter.setPen(QColor('#e5edf5') if day.month() == self.monthShown() else QColor('#657080'))
        painter.setFont(QFont(QApplication.font().family(), 10, QFont.Weight.Bold if today else QFont.Weight.Normal))
        painter.drawText(QPointF(r.left()+8, r.top()+18), str(day.day()))
        painter.setPen(QColor('#30c3e8') if amount else QColor('#56616c'))
        painter.setFont(QFont(QApplication.font().family(), 9))
        painter.drawText(QPointF(r.left()+8, r.bottom()-8), duration_text(amount) if amount else '—')
        painter.restore()


class History(QWidget):
    def __init__(self, controller):
        super().__init__()
        self.c = controller; self.deleted = None
        self.setWindowTitle('Cyber Cat · Focus journal')
        self.resize(940, 710)
        root = QVBoxLayout(self); root.setContentsMargins(25, 25, 25, 25); root.setSpacing(18)
        head = QHBoxLayout(); head.addWidget(label('Focus journal', 'title')); head.addStretch()
        head.addWidget(button('Today', self.today)); head.addWidget(button('Import macOS history…', self.import_history))
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
        while self.rows.count():
            item = self.rows.takeAt(0)
            if item.widget(): item.widget().hide(); item.widget().deleteLater()
        if not records:
            self.rows.addWidget(label('No sessions in this period.'))
        for record in records:
            card = QFrame(); card.setObjectName('card'); layout = QVBoxLayout(card)
            name = label(record['name']); name.setWordWrap(True); layout.addWidget(name)
            row = QHBoxLayout()
            row.addWidget(label(datetime.fromtimestamp(record['start']).strftime('%b %d · %H:%M') + '  ' + duration_text(record['duration']), 'subtitle'))
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
        self.persist = QTimer(); self.persist.timeout.connect(lambda:self.safe(self.store.checkpoint)); self.persist.start(15000)
        if not QSystemTrayIcon.isSystemTrayAvailable():
            self.pet.setToolTip('System tray unavailable. Click the cat for all controls, including Quit.')

    def icon(self):
        pix = QPixmap(32, 32); pix.fill(Qt.GlobalColor.transparent)
        p = QPainter(pix); p.setRenderHint(QPainter.RenderHint.Antialiasing)
        p.setBrush(QColor('#152330')); p.setPen(QPen(QColor('#30c3e8'), 2)); p.drawRoundedRect(2, 6, 28, 24, 8, 8)
        p.setPen(QPen(QColor('#30c3e8'), 3)); p.drawPoint(11, 16); p.drawPoint(22, 16); p.end()
        return QIcon(pix)

    def safe(self, operation):
        try: operation(); return True
        except Exception as error: self.error(str(error)); return False

    def error(self, message):
        # Non-modal notification avoids reentrant dialogs during shortcut registration.
        if hasattr(self, 'tray'):
            self.tray.showMessage('Cyber Cat', message, QSystemTrayIcon.MessageIcon.Warning)
        print('Cyber Cat:', message, file=sys.stderr)

    def scope(self):
        self.shortcuts.scope(self.pet.isVisible(), self.modal)

    def refresh(self):
        self.pet.sync()
        self.tray.setToolTip('Cyber Cat · ' + clock_text(self.store.elapsed) + ' · ' + self.store.phase)
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
        self.safe(self.store.start); self.show_pet(); self.refresh()

    def finish(self, reveal=True):
        if self.modal or self.store.phase == 'idle': return False
        if not self.safe(self.store.finish): return False
        self.refresh(); self.modal = True; self.scope()
        dialog = QDialog(self.pet); dialog.setWindowTitle('Save session'); dialog.setMinimumWidth(350)
        layout = QVBoxLayout(dialog); layout.setContentsMargins(22,22,22,22); layout.setSpacing(16)
        layout.addWidget(label('What did you work on?', 'title'))
        layout.addWidget(label(duration_text(self.store.elapsed) + ' of focus', 'subtitle'))
        field = QComboBox(); field.setEditable(True)
        field.addItems(['Job Apply', 'Upskill', 'Interview Prep', 'Work']); field.setCurrentIndex(-1)
        field.lineEdit().setPlaceholderText('Choose a category or type your own')
        layout.addWidget(field); layout.addWidget(label('Close keeps the session ready to save later.', 'subtitle'))
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
        answer = QMessageBox.question(self.pet, 'Discard session?', 'Discard this unsaved session?', QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.Cancel, QMessageBox.StandardButton.Cancel)
        self.modal = False; self.scope()
        if answer == QMessageBox.StandardButton.Yes: self.safe(self.store.discard); self.refresh()

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
        for text, action in [('Start / Resume · Ctrl+S', self.start), ('Pause · Ctrl+P', lambda:self.safe(self.store.pause)), ('Finish · Ctrl+F', self.finish), ('Cancel session…', self.cancel), ('Hide / Close pet · Ctrl+R', self.hide_pet), ('Session history', self.show_history)]:
            def invoke(fn=action): self.popup.close(); QTimer.singleShot(0, fn)
            layout.addWidget(button(text, invoke))
        opacity_text = label('Pet opacity'); layout.addWidget(opacity_text)
        slider = QSlider(Qt.Orientation.Horizontal); slider.setRange(30,100); slider.setValue(int(self.store.settings.get('opacity',1)*100))
        def opacity(value):
            opacity_text.setText(f'Pet opacity · {value}%'); self.pet.setWindowOpacity(value/100)
        slider.valueChanged.connect(opacity); slider.sliderReleased.connect(lambda:self.safe(lambda:self.store.setting('opacity',slider.value()/100)))
        layout.addWidget(slider)
        animate = QCheckBox('Animate cat'); animate.setChecked(self.store.settings.get('animation',True))
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
        self.menu.addAction('Cyber Cat · ' + clock_text(self.store.elapsed)).setEnabled(False)
        for text, action in [('Start / Resume',self.start),('Pause',lambda:self.safe(self.store.pause)),('Finish Session',self.finish),('Show Pet',self.show_pet),('Hide Pet',self.hide_pet),('Session History',self.show_history),('Quit App…',self.quit)]:
            self.menu.addAction(text, action)

    def quit(self):
        if self.modal: return
        if self.store.phase != 'idle':
            self.modal = True; self.scope()
            box = QMessageBox(self.pet); box.setWindowTitle('Quit Cyber Cat?'); box.setText('Save or discard your active session before quitting.')
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
