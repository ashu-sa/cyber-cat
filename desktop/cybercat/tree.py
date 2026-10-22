"""Native vector tree. Motion is a pure function of focus time, so pause freezes it."""
import math
from PySide6.QtCore import Qt, QRectF
from PySide6.QtGui import QColor, QPen, QPainterPath, QLinearGradient, QFont, QGuiApplication
from .forest import trees, growth

def smooth(x):
    x=max(0,min(1,x)); return x*x*(3-2*x)

def paint_tree(p, seconds, phase, animated=True):
    n=trees(seconds); progress=growth(seconds)
    dry=phase=="paused"
    t=seconds if animated and not dry else 0
    p.setPen(Qt.PenStyle.NoPen)
    p.setBrush(QColor(91,165,123,24)); p.drawEllipse(QRectF(25,45,60,12))
    def tree(g,alpha,center=55,scale=1,dormant=False):
        if alpha<=0: return
        p.save(); p.setOpacity(alpha); p.translate(center-55*scale,51-51*scale); p.scale(scale,scale)
        p.translate(55,51); p.rotate(math.sin(t*1.1)*1.2 if animated and not dry else 0); p.translate(-55,-51)
        h=5+23*g; top=51-h; canopy=g
        stem=QPainterPath(); stem.moveTo(55,51); stem.cubicTo(53,43,57,top+7,55,top)
        p.setBrush(Qt.BrushStyle.NoBrush); p.setPen(QPen(QColor('#c3a171'),2+2*g,Qt.PenStyle.SolidLine,Qt.PenCapStyle.RoundCap)); p.drawPath(stem)
        for side in (-1,1):
            leaf=QPainterPath(); y=top+1
            leaf.moveTo(55,y+3); leaf.cubicTo(55+side*3,y-(2+4*g),55+side*(3+7*g),y-(3+4*g),55+side*(3+8*g),y-(1+3*g))
            leaf.cubicTo(55+side*(3+6*g),y+4,55+side*3,y+8,55,y+3)
            p.save()
            if dormant: p.translate(55,y+3); p.rotate(side*18); p.translate(-55,-(y+3))
            p.setPen(Qt.PenStyle.NoPen); p.setBrush(QColor('#a89264' if dormant else '#8bd3a3' if side<0 else '#469779')); p.drawPath(leaf); p.restore()
        if canopy>0:
            p.save(); p.translate(55,top+2); p.scale(canopy,canopy); p.translate(-55,-(top+2))
            gradient=QLinearGradient(40,top-20,65,top+12); gradient.setColorAt(0,QColor('#d5bf8c' if dormant else '#b2e2ad')); gradient.setColorAt(.5,QColor('#b29c6e' if dormant else '#65b58b')); gradient.setColorAt(1,QColor('#7b735a' if dormant else '#2c795d'))
            p.setPen(Qt.PenStyle.NoPen); p.setBrush(gradient); p.drawEllipse(QRectF(30,top-14,30,29)); p.drawEllipse(QRectF(52,top-14,29,29)); p.drawEllipse(QRectF(39,top-21,32,31))
            p.setBrush(QColor(203,242,180,95)); p.drawEllipse(QRectF(43,top-16,14,10))
            p.setBrush(QColor(22,80,57,45)); p.drawEllipse(QRectF(47,top+1,22,9)); p.restore()
        p.restore()
    if n:
        centers=[25] if n==1 else [15,33] if n==2 else [12,27,42]
        for i,x in enumerate(centers): tree(1,.95,x,.62 if n==1 else .52 if i==1 else .44)
    active=74 if n else 55
    tree(progress,1,active,1,dry)
    p.setPen(Qt.PenStyle.NoPen); p.setBrush(QColor('#493c32')); p.drawEllipse(QRectF(active-25,50,51,7))
    p.setBrush(QColor('#8d7758' if dry else '#6a9c70')); p.drawEllipse(QRectF(active-24,49,49,4))
    if animated and phase=='running':
        u=(t%3.6)/3.6; fall=min(1,u/.78); y=3+44*fall*fall
        p.save(); p.setOpacity(1)
        if u<.78:
            drop=QPainterPath(); drop.moveTo(84,y-5); drop.cubicTo(82,y-2,78,y+3,81,y+5); drop.cubicTo(87,y+9,90,y+2,84,y-5)
            p.setBrush(QColor('#a5d9cc')); p.drawPath(drop); p.setBrush(QColor('#d4f5ff')); p.drawEllipse(QRectF(82,y+1,1.5,2.5))
        else:
            r=(u-.78)/.22; p.setBrush(Qt.BrushStyle.NoBrush); p.setPen(QPen(QColor(165,217,204,int((1-r)*160)),.8)); p.drawEllipse(QRectF(84-10*r,51-2*r,20*r,4*r))
        p.restore()
    if n:
        p.setPen(QColor('#a8d9b4')); p.setFont(QFont(QGuiApplication.font().family(),8,QFont.Weight.DemiBold)); p.drawText(QRectF(8,48,32,10),Qt.AlignmentFlag.AlignCenter,str(n)+'×')
