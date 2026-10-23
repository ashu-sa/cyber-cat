"""Native isolated lifecycle demo using the application's real vector renderer."""
import time
from PySide6.QtCore import Qt,QTimer,QRectF
from PySide6.QtGui import QPainter,QColor,QFont
from PySide6.QtWidgets import QDialog,QWidget,QVBoxLayout,QHBoxLayout,QLabel,QPushButton,QSlider
from .lifecycle import TreePreviewFrame
from .tree import paint_tree

class LifecycleCanvas(QWidget):
    def __init__(self):
        super().__init__();self.position=0;self.playing=True;self.setMinimumSize(420,220)
    def paintEvent(self,event):
        frame=TreePreviewFrame.at(self.position);p=QPainter(self);p.setRenderHint(QPainter.RenderHint.Antialiasing)
        scale=min(self.width()/110,self.height()/58);p.translate((self.width()-110*scale)/2,(self.height()-58*scale)/2);p.scale(scale,scale)
        phase='dead' if frame.dead else 'running' if self.playing and (self.position<10 or 12<=self.position<20) else 'resting'
        paint_tree(p,frame.elapsed,phase,True,True,frame.death,self.position);p.end()

class LifecycleDialog(QDialog):
    def __init__(self,parent=None):
        super().__init__(parent);self.setWindowTitle('Focanopy · Tree lifecycle preview');self.resize(550,520)
        self.position=0;self.playing=True;self.last=time.monotonic()
        layout=QVBoxLayout(self);layout.setContentsMargins(24,24,24,24);layout.setSpacing(16)
        title=QLabel('The life of a little tree');title.setStyleSheet('font-size:25px;color:#eef0dc;font-weight:600;');layout.addWidget(title)
        layout.addWidget(QLabel('Fast-forward preview · Your timer and history stay untouched'))
        self.canvas=LifecycleCanvas();layout.addWidget(self.canvas)
        self.stage=QLabel();self.stage.setAlignment(Qt.AlignmentFlag.AlignCenter);self.stage.setStyleSheet('color:#addaad;font-size:14px;');layout.addWidget(self.stage)
        self.hint=QLabel();self.hint.setAlignment(Qt.AlignmentFlag.AlignCenter);layout.addWidget(self.hint)
        self.slider=QSlider(Qt.Orientation.Horizontal);self.slider.setRange(0,27000);self.slider.setAccessibleName('Tree lifecycle preview');self.slider.sliderMoved.connect(self.seek);layout.addWidget(self.slider)
        buttons=QHBoxLayout();self.play=QPushButton('Pause');self.play.clicked.connect(self.toggle);buttons.addWidget(self.play)
        replay=QPushButton('Replay');replay.clicked.connect(self.replay);buttons.addWidget(replay);close=QPushButton('Close');close.clicked.connect(self.accept);buttons.addWidget(close);layout.addLayout(buttons)
        self.timer=QTimer(self);self.timer.timeout.connect(self.tick);self.timer.start(50);self.finished.connect(self.timer.stop);self.draw()
    def draw(self):
        f=TreePreviewFrame.at(self.position);self.canvas.position=self.position;self.canvas.playing=self.playing;self.canvas.update();self.stage.setText(f.title)
        self.hint.setText('Water stops. Petals fall. The stem droops and dries.' if f.dead else '25 minutes of focus shown in 10 seconds.')
        self.slider.setValue(int(self.position*1000));self.play.setText('Pause' if self.playing else 'Play')
    def tick(self):
        now=time.monotonic()
        if self.playing:self.position=min(27,self.position+max(0,now-self.last));self.playing=self.position<27;self.draw()
        self.last=now
    def toggle(self):
        if self.position>=27:self.position=0
        self.playing=not self.playing;self.last=time.monotonic();self.draw()
    def replay(self):self.position=0;self.playing=True;self.last=time.monotonic();self.draw()
    def seek(self,value):self.position=value/1000;self.last=time.monotonic();self.draw()
