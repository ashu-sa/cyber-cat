package com.cybercat.stopwatch

import android.graphics.*
import android.graphics.drawable.Drawable

/** Resolution-independent, consistent 24dp icons. Labels remain in accessibility descriptions. */
class UiIcon(private val name:String,private val tint:Int):Drawable() {
    override fun draw(canvas:Canvas) {
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=tint;style=Paint.Style.STROKE;strokeWidth=1.8f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
        fun path(vararg points:Float) {val line=Path();line.moveTo(points[0],points[1]);for(i in 2 until points.size step 2)line.lineTo(points[i],points[i+1]);canvas.drawPath(line,p)}
        when(name) {
            "play"->{p.style=Paint.Style.FILL;path(8f,5f,19f,12f,8f,19f,8f,5f)}
            "pause"->{path(8f,6f,8f,18f);path(16f,6f,16f,18f)}
            "check"->path(5f,12f,10f,17f,19f,7f)
            "close"->{path(6f,6f,18f,18f);path(18f,6f,6f,18f)}
            "save"->{path(12f,4f,12f,15f);path(7f,11f,12f,16f,17f,11f);path(5f,18f,5f,21f,19f,21f,19f,18f)}
            "edit"->{path(5f,16f,16f,5f,20f,9f,9f,20f,4f,21f,5f,16f);path(14f,7f,18f,11f)}
            "left"->path(15f,5f,8f,12f,15f,19f)
            "right"->path(9f,5f,16f,12f,9f,19f)
            "calendar"->{canvas.drawRoundRect(4f,5f,20f,21f,3f,3f,p);path(4f,10f,20f,10f);path(8f,3f,8f,7f);path(16f,3f,16f,7f);canvas.drawCircle(9f,15f,1f,p);canvas.drawCircle(15f,15f,1f,p)}
            "settings"->{canvas.drawCircle(12f,12f,4f,p);canvas.drawCircle(12f,12f,8f,p);for(i in 0..7){val a=i*Math.PI/4;path((12+8*kotlin.math.cos(a)).toFloat(),(12+8*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat())}}
            "reset"->{canvas.drawArc(5f,5f,20f,20f,220f,300f,false,p);path(4f,4f,4f,10f,10f,10f)}
            else->{canvas.drawCircle(12f,13f,8f,p);path(12f,8f,12f,13f,16f,15f);path(9f,2f,15f,2f)}
        }
        canvas.restore()
    }
    override fun setAlpha(alpha:Int) {} // Each control owns its disabled alpha.
    override fun setColorFilter(colorFilter:ColorFilter?) {}
    @Deprecated("Drawable API") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
