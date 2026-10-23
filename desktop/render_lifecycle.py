"""Export deterministic frames from Focanopy's actual native Qt tree renderer."""
import os,sys,math
from pathlib import Path
os.environ.setdefault('QT_QPA_PLATFORM','offscreen')
from PySide6.QtWidgets import QApplication
from PySide6.QtCore import Qt,QRectF
from PySide6.QtGui import QPainter,QImage,QColor,QFont,QPen
from cybercat.app import apply_theme
from cybercat.tree import paint_tree
from cybercat.lifecycle import TreePreviewFrame
app=QApplication([]);apply_theme(app)
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
def frame(t):
    f=TreePreviewFrame.at(t);image=QImage(720,480,QImage.Format.Format_ARGB32);image.fill(QColor('#111b17'))
    p=QPainter(image);p.setRenderHint(QPainter.RenderHint.Antialiasing);p.setBrush(QColor('#1e2c24'));p.setPen(QPen(QColor('#374c3d'),1));p.drawRoundedRect(QRectF(20,20,680,440),24,24)
    def text(s,y,size,color):
        p.setPen(QColor(color));p.setFont(QFont('Noto Sans',size));p.drawText(QRectF(35,y,650,30),Qt.AlignmentFlag.AlignCenter,s)
    text('The life of a little tree',32,22,'#eef0dc');text('FAST-FORWARD PREVIEW · YOUR REAL TIMER STAYS UNTOUCHED',67,10,'#a1b5a5')
    p.save();p.translate(85,95);p.scale(5,5)
    phase='dead' if f.dead else 'running' if t<10 or 12<=t<20 else 'resting'
    paint_tree(p,f.elapsed,phase,True,True,f.death,t);p.restore()
    text(f.title,376,14,'#addaad')
    text('Water stops. Petals fall. The stem droops and dries.' if f.dead else '25 minutes of focus shown in 10 seconds.',407,11,'#a1b5a5')
    p.setPen(Qt.PenStyle.NoPen);p.setBrush(QColor('#2e4133'));p.drawRoundedRect(QRectF(50,445,620,4),2,2);p.setBrush(QColor('#addaad'));p.drawRoundedRect(QRectF(50,445,620*t/27,4),2,2);p.end();return image
for i in range(406):frame(min(27,i/15)).save(str(out/f'{i:04}.png'))
strip=QImage(1200,260,QImage.Format.Format_ARGB32);strip.fill(QColor('#111b17'));p=QPainter(strip);p.setRenderHint(QPainter.RenderHint.Antialiasing)
for i,t in enumerate([0,10,19.9,20.8,22,27]):
    f=TreePreviewFrame.at(t);p.save();p.translate(i*200+7,48);p.scale(1.7,1.7);paint_tree(p,f.elapsed,'dead' if f.dead else 'resting',True,True,f.death,t);p.restore();p.setPen(QColor('#addaad'));p.setFont(QFont('Noto Sans',11));p.drawText(QRectF(i*200,180,200,35),Qt.AlignmentFlag.AlignCenter,['Seed','Bloom saved','Next tree','Petals fall','Withering','Bare & dry'][i])
p.end();strip.save(str(out.parent/'tree-lifecycle-strip.png'))
print('Exported 406 native frames and lifecycle strip')
