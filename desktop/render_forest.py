"""Export the production Qt vector renderer at the four focus milestones."""
import os
os.environ.setdefault('QT_QPA_PLATFORM','offscreen')
from pathlib import Path
from PySide6.QtWidgets import QApplication
from PySide6.QtGui import QImage,QPainter,QColor,QFont
from PySide6.QtCore import QRectF
from cybercat.app import apply_theme
from cybercat.tree import paint_tree
app=QApplication([]);apply_theme(app)
image=QImage(1120,410,QImage.Format.Format_ARGB32);image.fill(QColor('#111b17'))
p=QPainter(image);p.setRenderHint(QPainter.RenderHint.Antialiasing)
p.setPen(QColor('#c5e5cd'));p.setFont(QFont(app.font().family(),24,QFont.Weight.DemiBold));p.drawText(32,45,'A little focus. A living grove.')
for i,(title,seconds,description) in enumerate([('Seed · 0m',0,'Ready to grow'),('Young plant · 30m',1801,'Half an hour, taking root'),('Tree · 60m',3601,'One complete tree'),('Garden · 8h',28801,'Eight trees. A garden grown.')]):
 x=28+i*274;p.setPen(QColor('#335044'));p.setBrush(QColor('#1d2925'));p.drawRoundedRect(QRectF(x,75,260,270),18,18)
 p.setPen(QColor('#d9eddd'));p.setFont(QFont(app.font().family(),13,QFont.Weight.DemiBold));p.drawText(x+18,105,title)
 p.save();p.translate(x+15,125);p.scale(2.1,2.1);paint_tree(p,seconds,'running' if seconds else 'idle');p.restore()
 p.setPen(QColor('#a6c6b1'));p.setFont(QFont(app.font().family(),10));p.drawText(x+18,319,description)
p.setPen(QColor('#a6b7ae'));p.setFont(QFont(app.font().family(),11));p.drawText(32,383,'One tree per focused hour · Short sessions add up · Watering pauses with your timer')
p.end();out=Path(__file__).resolve().parents[1]/'docs/screenshots/forest-milestones.png';image.save(str(out));print(out)
