"""Render the production native tree at minute-by-minute growth checkpoints."""
import os
os.environ.setdefault('QT_QPA_PLATFORM','offscreen')
from pathlib import Path
from PySide6.QtWidgets import QApplication
from PySide6.QtGui import QImage,QPainter,QColor,QFont
from PySide6.QtCore import QRectF
from cybercat.app import apply_theme
from cybercat.tree import paint_tree
app=QApplication([]);apply_theme(app)
image=QImage(1110,300,QImage.Format.Format_ARGB32);image.fill(QColor('#111b17'))
p=QPainter(image);p.setRenderHint(QPainter.RenderHint.Antialiasing)
p.setPen(QColor('#c5e5cd'));p.setFont(QFont(app.font().family(),22,QFont.Weight.DemiBold));p.drawText(22,40,'A little focus, a flower in bloom.')
for i,minute in enumerate([0,5,10,15,20,25]):
 x=20+i*180;p.setPen(QColor('#335044'));p.setBrush(QColor('#1d2925'));p.drawRoundedRect(QRectF(x,62,170,184),14,14)
 p.setPen(QColor('#d9eddd'));p.setFont(QFont(app.font().family(),13,QFont.Weight.DemiBold));p.drawText(x+14,90,f'{minute} min')
 p.save();p.translate(x+8,105);p.scale(1.4,1.4);paint_tree(p,minute*60,'idle',False,True);p.restore()
 p.setPen(QColor('#a6c6b1'));p.setFont(QFont(app.font().family(),10));p.drawText(x+10,226,'Fully bloomed' if minute==25 else 'Growing your flower')
p.setPen(QColor('#a6b7ae'));p.setFont(QFont(app.font().family(),11));p.drawText(22,278,'25 minutes focus · 5 or 10 minutes to stretch · Watering stops for breaks · You start the next round')
p.end();out=Path(__file__).resolve().parents[1]/'docs/screenshots/pomodoro-flower-milestones.png';image.save(str(out));print(out)
