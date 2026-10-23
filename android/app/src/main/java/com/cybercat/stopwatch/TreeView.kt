package com.cybercat.stopwatch

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin

/** Native vector growth and watering; focus time drives every frame, including pause. */
class TreeView(c:Context):View(c) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var motionEnabled=true
    var animate=true
    var phase="idle"
    var previewMillis:Long?=null
    private fun moving()=animate && motionEnabled && phase=="running"
    private val tick=object:Runnable { override fun run() { if(isShown && moving()) { invalidate(); if((context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive) postDelayed(this,50) } } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(tick); if(moving()) post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView:View,visibility:Int) { super.onVisibilityChanged(changedView,visibility); removeCallbacks(tick); if(visibility==VISIBLE && moving()) post(tick) }
    override fun onWindowVisibilityChanged(visibility:Int) { super.onWindowVisibilityChanged(visibility); removeCallbacks(tick); if(visibility==VISIBLE && isShown && moving()) post(tick) }
    fun refresh() {
        phase=if(Repository.pomodoroEnabled && Repository.pomodoro.stage!="focus") "resting" else Repository.engine.phase; animate=Repository.prefs.getBoolean("animate",true)
        motionEnabled=android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0
        contentDescription=Forest.text(Repository.growthElapsed())+if(Forest.garden(Repository.growthElapsed()))", garden grown" else if(phase=="paused") ", dry tree, watering paused" else ", growing tree"
        removeCallbacks(tick); if(isShown && moving()) post(tick); invalidate()
    }
    override fun performClick():Boolean { super.performClick(); return true }
    private fun smooth(value:Float):Float { val v=value.coerceIn(0f,1f); return v*v*(3-2*v) }
    override fun onDraw(c:Canvas) {
        super.onDraw(c); c.save()
        val scale=minOf(width/110f,height/58f); c.translate((width-110*scale)/2,(height-58*scale)/2); c.scale(scale,scale)
        val flower=Repository.pomodoroEnabled && previewMillis==null
        val actual=(previewMillis ?: if(flower)Repository.engine.elapsed() else Repository.growthElapsed()).coerceAtLeast(0)
        val scaled=if(flower)actual*12/5 else actual
        val ms=if(flower && scaled>0 && scaled%Forest.HOUR==0L)scaled-1 else scaled
        val seconds=actual/1000.0; val n=Forest.trees(ms); val remainder=(ms%Forest.HOUR)/1000f
        val dry=phase=="paused"; val motion=animate && motionEnabled && !dry; val t=if(motion)seconds else 0.0
        fun ellipse(x:Float,y:Float,w:Float,h:Float,color:Int) { paint.shader=null; paint.style=Paint.Style.FILL; paint.color=color; c.drawOval(x,y,x+w,y+h,paint) }
        ellipse(25f,45f,60f,12f,Color.argb(24,91,165,123))
        fun tree(g:Float,alpha:Float,center:Float=55f,scale:Float=1f,dormant:Boolean=false) {
            if(alpha<=0) return
            val layer=c.saveLayerAlpha(0f,0f,110f,58f,(alpha*255).toInt());c.translate(center-55*scale,51-51*scale);c.scale(scale,scale)
            c.rotate(if(motion)(sin(t*1.1)*1.2).toFloat() else 0f,55f,51f)
            val top=51-(5+23*g); val canopy=g
            val stem=Path().apply { moveTo(55f,51f); cubicTo(53f,43f,57f,top+7,55f,top) }
            paint.shader=null; paint.color=Color.rgb(195,161,113); paint.style=Paint.Style.STROKE; paint.strokeWidth=2+2*g; paint.strokeCap=Paint.Cap.ROUND; c.drawPath(stem,paint)
            for(side in listOf(-1,1)) {
                val y=top+1; val leaf=Path().apply { moveTo(55f,y+3); cubicTo(55+side*3f,y-(2+4*g),55+side*(3+7*g),y-(3+4*g),55+side*(3+8*g),y-(1+3*g)); cubicTo(55+side*(3+6*g),y+4,55+side*3f,y+8,55f,y+3) }
                c.save(); if(dormant)c.rotate(side*18f,55f,y+3)
                paint.style=Paint.Style.FILL; paint.color=if(dormant)Color.rgb(168,146,100) else if(side<0)Color.rgb(139,211,163) else Color.rgb(70,151,121); c.drawPath(leaf,paint); c.restore()
            }
            if(canopy>0) {
                c.save(); c.scale(canopy,canopy,55f,top+2)
                paint.shader=LinearGradient(40f,top-20,65f,top+12,if(dormant)intArrayOf(Color.rgb(213,191,140),Color.rgb(178,156,110),Color.rgb(123,115,90)) else if(flower)intArrayOf(Color.rgb(255,201,213),Color.rgb(240,122,166),Color.rgb(166,69,125)) else intArrayOf(Color.rgb(178,226,173),Color.rgb(101,181,139),Color.rgb(44,121,93)),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
                c.drawOval(30f,top-14,60f,top+15,paint); c.drawOval(52f,top-14,81f,top+15,paint); c.drawOval(39f,top-21,71f,top+10,paint)
                if(flower) for((x,y) in listOf(44f to top-5,63f to top-4,54f to top-14)) {
                    for(a in 0..4) { val angle=a*Math.PI*2/5;ellipse(x+kotlin.math.cos(angle).toFloat()*3-2.8f,y+kotlin.math.sin(angle).toFloat()*3-2.8f,5.6f,5.6f,Color.rgb(255,227,235)) }
                    ellipse(x-1.7f,y-1.7f,3.4f,3.4f,Color.rgb(255,224,129))
                }
                ellipse(43f,top-16,14f,10f,Color.argb(95,203,242,180)); c.restore()
            }; c.restoreToCount(layer)
        }
        if(n>0) {
            val centers=if(n==1L)listOf(25f) else if(n==2L)listOf(15f,33f) else listOf(12f,27f,42f)
            centers.forEachIndexed { i,x -> tree(1f,.95f,x,if(n==1L).62f else if(i==1).52f else .44f) }
        }
        val active=if(n>0)74f else 55f
        tree(remainder/3600,1f,active,1f,dry)
        ellipse(active-25,50f,51f,7f,Color.rgb(73,60,50)); ellipse(active-24,49f,49f,4f,if(dry)Color.rgb(141,119,88) else Color.rgb(106,156,112))
        if(motion && phase=="running") {
            c.saveLayerAlpha(0f,0f,110f,58f,255)
            val u=(t%3.6/3.6).toFloat(); val fall=(u/.78f).coerceAtMost(1f); val y=3+44*fall*fall
            if(u<.78) {
                val drop=Path().apply { moveTo(84f,y-5); cubicTo(82f,y-2,78f,y+3,81f,y+5); cubicTo(87f,y+9,90f,y+2,84f,y-5) }
                paint.shader=null; paint.style=Paint.Style.FILL; paint.color=Color.rgb(165,217,204); c.drawPath(drop,paint)
                ellipse(82f,y+1,1.5f,2.5f,Color.rgb(212,245,255))
            } else {
                val r=(u-.78f)/.22f; paint.style=Paint.Style.STROKE; paint.color=Color.argb(((1-r)*160).toInt(),165,217,204); paint.strokeWidth=.8f
                c.drawOval(84-10*r,51-2*r,84+10*r,51+2*r,paint)
            }; c.restore()
        }
        if(n>0) { paint.shader=null; paint.style=Paint.Style.FILL; paint.color=Color.rgb(168,217,180); paint.textSize=8f; paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); c.drawText("${n}×",12f,57f,paint) }
        c.restore()
    }
}
