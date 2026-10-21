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
        phase=Repository.engine.phase; animate=Repository.prefs.getBoolean("animate",true)
        motionEnabled=android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0
        contentDescription=Forest.text(Repository.growthElapsed())+if(Forest.garden(Repository.growthElapsed()))", garden grown" else ", growing tree"
        removeCallbacks(tick); if(isShown && moving()) post(tick); invalidate()
    }
    override fun performClick():Boolean { super.performClick(); return true }
    private fun smooth(value:Float):Float { val v=value.coerceIn(0f,1f); return v*v*(3-2*v) }
    override fun onDraw(c:Canvas) {
        super.onDraw(c); c.save()
        val scale=minOf(width/110f,height/58f); c.translate((width-110*scale)/2,(height-58*scale)/2); c.scale(scale,scale)
        val ms=(previewMillis ?: Repository.growthElapsed()).coerceAtLeast(0); val seconds=ms/1000.0; val n=Forest.trees(ms); val remainder=(ms%Forest.HOUR)/1000f
        val motion=animate && motionEnabled; val t=if(motion)seconds else 0.0
        fun ellipse(x:Float,y:Float,w:Float,h:Float,color:Int) { paint.shader=null; paint.style=Paint.Style.FILL; paint.color=color; c.drawOval(x,y,x+w,y+h,paint) }
        ellipse(25f,45f,60f,12f,Color.argb(24,91,165,123))
        for(i in 0 until minOf(n,3).toInt()) { val x=listOf(15f,94f,103f)[i]; ellipse(x-5,35f,10f,12f,Color.argb(65,91,165,123)); c.drawRoundRect(x-1,43f,x+1,52f,1f,1f,paint) }
        fun tree(g:Float,alpha:Float) {
            if(alpha<=0) return
            val layer=c.saveLayerAlpha(0f,0f,110f,58f,(alpha*255).toInt())
            c.rotate(if(motion)(sin(t*1.1)*1.2).toFloat() else 0f,55f,51f)
            val top=51-(7+22*g); val canopy=smooth((g-.5f)*2)
            val stem=Path().apply { moveTo(55f,51f); cubicTo(53f,43f,57f,top+7,55f,top) }
            paint.shader=null; paint.color=Color.rgb(195,161,113); paint.style=Paint.Style.STROKE; paint.strokeWidth=2+2*g; paint.strokeCap=Paint.Cap.ROUND; c.drawPath(stem,paint)
            for(side in listOf(-1,1)) {
                val y=top+4; val leaf=Path().apply { moveTo(55f,y+3); cubicTo(55+side*3f,y-7,55+side*(9+7*g),y-8,55+side*(12+8*g),y-5); cubicTo(55+side*(10+6*g),y+4,55+side*3f,y+8,55f,y+3) }
                paint.style=Paint.Style.FILL; paint.color=if(side<0)Color.rgb(139,211,163) else Color.rgb(70,151,121); c.drawPath(leaf,paint)
            }
            if(canopy>0) {
                c.save(); c.scale(canopy,canopy,55f,top+2)
                paint.shader=LinearGradient(40f,top-20,65f,top+12,intArrayOf(Color.rgb(178,226,173),Color.rgb(101,181,139),Color.rgb(44,121,93)),floatArrayOf(0f,.5f,1f),Shader.TileMode.CLAMP)
                c.drawOval(30f,top-14,60f,top+15,paint); c.drawOval(52f,top-14,81f,top+15,paint); c.drawOval(39f,top-21,71f,top+10,paint)
                ellipse(43f,top-16,14f,10f,Color.argb(95,203,242,180)); c.restore()
            }; c.restoreToCount(layer)
        }
        val blend=if(n>0)smooth(remainder/8) else 1f
        if(n>0)tree(1f,1-blend); tree(remainder/3600,blend)
        ellipse(30f,50f,51f,7f,Color.rgb(73,60,50)); ellipse(31f,49f,49f,4f,Color.rgb(106,156,112))
        if(motion && (phase=="running" || phase=="paused")) {
            c.saveLayerAlpha(0f,0f,110f,58f,if(phase=="paused")102 else 255)
            val u=(t%3.6/3.6).toFloat(); val fall=(u/.78f).coerceAtMost(1f); val y=3+44*fall*fall
            if(u<.78) {
                val drop=Path().apply { moveTo(84f,y-5); cubicTo(82f,y-2,78f,y+3,81f,y+5); cubicTo(87f,y+9,90f,y+2,84f,y-5) }
                paint.shader=null; paint.style=Paint.Style.FILL; paint.color=Color.rgb(129,214,237); c.drawPath(drop,paint)
                ellipse(82f,y+1,1.5f,2.5f,Color.rgb(212,245,255))
            } else {
                val r=(u-.78f)/.22f; paint.style=Paint.Style.STROKE; paint.color=Color.argb(((1-r)*160).toInt(),129,214,237); paint.strokeWidth=.8f
                c.drawOval(84-10*r,51-2*r,84+10*r,51+2*r,paint)
            }; c.restore()
        }
        if(n>0) { paint.shader=null; paint.style=Paint.Style.FILL; paint.color=Color.rgb(168,217,180); paint.textSize=8f; paint.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL); c.drawText("${n}×",2f,47f,paint) }
        c.restore()
    }
}
