"""Native vector tree. Motion is a pure function of focus time, so pause freezes it."""
import math
from PySide6.QtCore import Qt, QRectF
from PySide6.QtGui import QColor, QPen, QPainterPath, QLinearGradient, QFont, QGuiApplication
from .forest import trees, growth

def smooth(x):
    x=max(0,min(1,x)); return x*x*(3-2*x)

def paint_tree(p, seconds, phase, animated=True):
    n=trees(seconds); progress=growth(seconds)
    t=seconds if animated else 0
    p.setPen(Qt.PenStyle.NoPen)
    p.setBrush(QColor(91,165,123,24)); p.drawEllipse(QRectF(25,45,60,12))
    # Distant trees quietly acknowledge completed hours without cluttering the timer.
    for i in range(min(n,3)):
        x=(15,94,103)[i]; p.setBrush(QColor(91,165,123,65))
        p.drawRoundedRect(QRectF(x-1,43,2,9),1,1); p.drawEllipse(QRectF(x-5,35,10,12))
    def tree(g,alpha):
        if alpha<=0: return
        p.save(); p.setOpacity(alpha)
        p.translate(55,51); p.rotate(math.sin(t*1.1)*1.2 if animated else 0); p.translate(-55,-51)
        h=7+22*g; top=51-h; canopy=smooth((g-.5)*2)
        stem=QPainterPath(); stem.moveTo(55,51); stem.cubicTo(53,43,57,top+7,55,top)
        p.setBrush(Qt.BrushStyle.NoBrush); p.setPen(QPen(QColor('#c3a171'),2+2*g,Qt.PenStyle.SolidLine,Qt.PenCapStyle.RoundCap)); p.drawPath(stem)
        for side in (-1,1):
            leaf=QPainterPath(); y=top+4
            leaf.moveTo(55,y+3); leaf.cubicTo(55+side*3,y-7,55+side*(9+7*g),y-8,55+side*(12+8*g),y-5)
            leaf.cubicTo(55+side*(10+6*g),y+4,55+side*3,y+8,55,y+3)
            p.setPen(Qt.PenStyle.NoPen); p.setBrush(QColor('#8bd3a3' if side<0 else '#469779')); p.drawPath(leaf)
        if canopy>0:
            p.save(); p.translate(55,top+2); p.scale(canopy,canopy); p.translate(-55,-(top+2))
            gradient=QLinearGradient(40,top-20,65,top+12); gradient.setColorAt(0,QColor('#b2e2ad')); gradient.setColorAt(.5,QColor('#65b58b')); gradient.setColorAt(1,QColor('#2c795d'))
            p.setBrush(gradient); p.drawEllipse(QRectF(30,top-14,30,29)); p.drawEllipse(QRectF(52,top-14,29,29)); p.drawEllipse(QRectF(39,top-21,32,31))
            p.setBrush(QColor(203,242,180,95)); p.drawEllipse(QRectF(43,top-16,14,10))
            p.setBrush(QColor(22,80,57,45)); p.drawEllipse(QRectF(47,top+1,22,9)); p.restore()
        p.restore()
    blend=smooth((seconds%3600)/8) if n else 1
    if n: tree(1,1-blend)
    tree(progress,blend)
    p.setPen(Qt.PenStyle.NoPen); p.setBrush(QColor('#493c32')); p.drawEllipse(QRectF(30,50,51,7))
    p.setBrush(QColor('#6a9c70')); p.drawEllipse(QRectF(31,49,49,4))
    if animated and phase in ('running','paused'):
        u=(t%3.6)/3.6; fall=min(1,u/.78); y=3+44*fall*fall
        p.save(); p.setOpacity(1 if phase=='running' else .4)
        if u<.78:
            drop=QPainterPath(); drop.moveTo(84,y-5); drop.cubicTo(82,y-2,78,y+3,81,y+5); drop.cubicTo(87,y+9,90,y+2,84,y-5)
            p.setBrush(QColor('#81d6ed')); p.drawPath(drop); p.setBrush(QColor('#d4f5ff')); p.drawEllipse(QRectF(82,y+1,1.5,2.5))
        else:
            r=(u-.78)/.22; p.setBrush(Qt.BrushStyle.NoBrush); p.setPen(QPen(QColor(129,214,237,int((1-r)*160)),.8)); p.drawEllipse(QRectF(84-10*r,51-2*r,20*r,4*r))
        p.restore()
    if n:
        p.setPen(QColor('#a8d9b4')); p.setFont(QFont(QGuiApplication.font().family(),8,QFont.Weight.DemiBold)); p.drawText(QRectF(0,38,26,14),Qt.AlignmentFlag.AlignCenter,str(n)+'×')
