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
image=QImage(1290,300,QImage.Format.Format_ARGB32);image.fill(QColor('#111b17'))
p=QPainter(image);p.setRenderHint(QPainter.RenderHint.Antialiasing)
p.setPen(QColor('#c5e5cd'));p.setFont(QFont(app.font().family(),22,QFont.Weight.DemiBold));p.drawText(22,40,'Every minute grows something.')
for i,minute in enumerate([0,10,20,30,45,60,80]):
 x=20+i*180;p.setPen(QColor('#335044'));p.setBrush(QColor('#1d2925'));p.drawRoundedRect(QRectF(x,62,170,184),14,14)
 p.setPen(QColor('#d9eddd'));p.setFont(QFont(app.font().family(),13,QFont.Weight.DemiBold));p.drawText(x+14,90,f'{minute} min')
 p.save();p.translate(x+8,105);p.scale(1.4,1.4);paint_tree(p,minute*60,'idle',False);p.restore()
 p.setPen(QColor('#a6c6b1'));p.setFont(QFont(app.font().family(),10));p.drawText(x+10,226,'Tree + new sprout' if minute>=60 else 'Continuous growth')
p.setPen(QColor('#a6b7ae'));p.setFont(QFont(app.font().family(),11));p.drawText(22,278,'One completed tree per hour · Earned trees stay visible as the next one grows · Pause never removes progress')
p.end();out=Path(__file__).resolve().parents[1]/'docs/screenshots/forest-milestones.png';image.save(str(out));print(out)
