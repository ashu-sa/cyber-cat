"""Native vector tree. Motion is a pure function of focus time, so pause freezes it."""
import math
from PySide6.QtCore import Qt, QRectF
from PySide6.QtGui import QColor, QPen, QPainterPath, QLinearGradient, QFont, QGuiApplication
from .forest import trees, growth

def smooth(x):
    x=max(0,min(1,x)); return x*x*(3-2*x)

def paint_tree(p, seconds, phase, animated=True, flower=False, death_progress=1.0, motion_time=None):
    actual=seconds
    death=max(0,min(1,death_progress)) if phase=="dead" and animated else (1 if phase=="dead" else 0)
    def blend(a,b,f):
        a,b=QColor(a),QColor(b);return QColor(int(a.red()+(b.red()-a.red())*f),int(a.green()+(b.green()-a.green())*f),int(a.blue()+(b.blue()-a.blue())*f))
    seconds=seconds*2.4 if flower else seconds
    if flower and seconds>0 and seconds%3600<0.0001: seconds-=0.0001
    n=trees(seconds); progress=growth(seconds)
    dry=phase in ("paused","dead")
    t=(actual if motion_time is None else motion_time) if animated and not dry else 0
    p.setPen(Qt.PenStyle.NoPen)
    p.setBrush(QColor(91,165,123,24)); p.drawEllipse(QRectF(25,45,60,12))
    def tree(g,alpha,center=55,scale=1,dormant=False):
        if alpha<=0: return
        p.save(); p.setOpacity(alpha); p.translate(center-55*scale,51-51*scale); p.scale(scale,scale)
        p.translate(55,51); p.rotate(math.sin(t*1.1)*1.2 if animated and not dry else 0); p.translate(-55,-51)
        d=smooth(death) if dormant and phase=='dead' else 0
        wilt=(d if phase=='dead' else 1) if dormant else 0
        h=5+23*g;top=51-h
        dx,dy=14*d,min(16*d,max(0,h-2))
        canopy=g*(1-.72*d)
        stem=QPainterPath(); stem.moveTo(55,51); stem.cubicTo(53,43,57+dx,top+7+dy,55+dx,top+dy)
        p.setBrush(Qt.BrushStyle.NoBrush); p.setPen(QPen(QColor('#c3a171'),2+2*g,Qt.PenStyle.SolidLine,Qt.PenCapStyle.RoundCap)); p.drawPath(stem)
        p.translate(dx,dy)
        for side in (-1,1):
            leaf=QPainterPath(); y=top+1
            leaf.moveTo(55,y+3); leaf.cubicTo(55+side*3,y-(2+4*g),55+side*(3+7*g),y-(3+4*g),55+side*(3+8*g),y-(1+3*g))
            leaf.cubicTo(55+side*(3+6*g),y+4,55+side*3,y+8,55,y+3)
            p.save()
            if dormant: p.translate(55,y+3); p.rotate(side*(65*d if phase=="dead" else 18)); p.translate(-55,-(y+3))
            p.setPen(Qt.PenStyle.NoPen); p.setBrush(blend('#8bd3a3' if side<0 else '#469779','#a87d4d',wilt)); p.drawPath(leaf); p.restore()
        if canopy>0:
            p.save(); p.translate(55,top+2); p.scale(canopy,canopy); p.translate(-55,-(top+2))
            p.setOpacity(alpha*(1-.9*d))
            gradient=QLinearGradient(40,top-20,65,top+12)
            alive=['#ffc9d5','#f07aa6','#a6457d'] if flower else ['#b2e2ad','#65b58b','#2c795d']
            for stop,a,b in zip([0,.5,1],alive,['#b8945c','#856138','#57452e']):gradient.setColorAt(stop,blend(a,b,wilt))
            p.setPen(Qt.PenStyle.NoPen); p.setBrush(gradient); p.drawEllipse(QRectF(30,top-14,30,29)); p.drawEllipse(QRectF(52,top-14,29,29)); p.drawEllipse(QRectF(39,top-21,32,31))
            if flower and (not dormant or (phase=="dead" and death<.6)):
                for x,y in [(44,top-5),(63,top-4),(54,top-14)]:
                    p.setBrush(QColor('#ffe3eb'))
                    for a in range(5):
                        angle=a*math.pi*2/5; p.drawEllipse(QRectF(x+math.cos(angle)*3-2.8,y+math.sin(angle)*3-2.8,5.6,5.6))
                    p.setBrush(QColor('#ffe081'));p.drawEllipse(QRectF(x-1.7,y-1.7,3.4,3.4))
            p.setBrush(QColor(203,242,180,95)); p.drawEllipse(QRectF(43,top-16,14,10))
            p.setBrush(QColor(22,80,57,45)); p.drawEllipse(QRectF(47,top+1,22,9)); p.restore()
        p.restore()
    if n:
        centers=[40] if n==1 else [26,44] if n==2 else [19,35,51]
        for i,x in enumerate(centers): tree(1,.95,x,.62 if n==1 else .52 if i==1 else .44)
    active=66 if n==1 else 70 if n==2 else 77 if n else 55
    tree(progress,1,active,1,dry)
    p.setPen(Qt.PenStyle.NoPen); p.setBrush(QColor('#493c32')); p.drawEllipse(QRectF(10 if n else active-25,50,94 if n else 51,7))
    p.setBrush(QColor('#8d7758' if dry else '#6a9c70')); p.drawEllipse(QRectF(11 if n else active-24,49,92 if n else 49,4))
    if phase=='dead':
        g=progress;top=51-(5+23*g)-9*g
        for i in range(15):
            delay=i*.027
            if death>delay:
                u=max(0,min(1,(death-delay)/(1-delay)))
                x=active+math.cos(i*2.4)*12*g+math.sin(i*1.3)*12*u;y=top+(50-top)*u*u
                p.save();p.translate(x,y);p.rotate((i%3-1)*12 if u>=1 else i*47+220*u)
                p.setPen(Qt.PenStyle.NoPen);p.setBrush(blend('#ffcfdb','#91663b',death));p.drawEllipse(QRectF(-2,-1,4,2));p.restore()
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
