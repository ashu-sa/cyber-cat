package com.cybercat.stopwatch

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.sin

class CatView(c: Context): View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tail = Path()
    private val mouth = Path().apply { moveTo(49f,34f); lineTo(54f,36f); lineTo(59f,34f) }
    private val ears = listOf(Path().apply { moveTo(25f,20f); lineTo(26f,1f); lineTo(43f,13f); close() }, Path().apply { moveTo(64f,12f); lineTo(82f,1f); lineTo(83f,24f); close() })
    private var motionEnabled = true
    var animate = true
    var phase = "idle"
    private val tick = object: Runnable { override fun run() { if (isShown && animate && motionEnabled) { invalidate(); postDelayed(this, if ((context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive) 83 else 1000) } } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(tick); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); if (visibility == VISIBLE && animate) { removeCallbacks(tick); post(tick) } else removeCallbacks(tick) }
    fun refresh() { phase = Repository.engine.phase; animate = Repository.prefs.getBoolean("animate", true); motionEnabled = android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0; removeCallbacks(tick); if (animate && motionEnabled && isShown) post(tick); invalidate() }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(c: Canvas) {
        super.onDraw(c); c.save(); val scale = minOf(width / 110f, height / 58f); c.translate((width - 110 * scale) / 2, 0f); c.scale(scale, scale)
        val seconds = android.os.SystemClock.elapsedRealtime() / 1000.0
        val moving = animate && motionEnabled
        val wag = if (moving) (sin(seconds * if (phase == "running") 2.8 else 1.4) * 3).toFloat() else 0f
        if (moving) c.translate(0f, sin(seconds * 1.8).toFloat() * .8f)
        val ink = Color.rgb(17,25,34); val accent = if (phase == "paused") Color.rgb(172,138,255) else Color.rgb(48,195,232)
        paint.color = ink; paint.style = Paint.Style.STROKE; paint.strokeWidth = 6f; paint.strokeCap = Paint.Cap.ROUND
        tail.reset(); tail.moveTo(81f,48f); tail.cubicTo(102f,56f+wag,107f,32f+wag,94f,36f+wag); c.drawPath(tail,paint)
        paint.style = Paint.Style.FILL
        for (ear in ears) c.drawPath(ear,paint)
        c.drawRoundRect(29f,29f,81f,53f,10f,10f,paint); c.drawRoundRect(24f,12f,84f,43f,11f,11f,paint)
        paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; c.drawRoundRect(24f,12f,84f,43f,11f,11f,paint)
        paint.style = Paint.Style.FILL
        val eyeHeight = if (phase == "paused" || (moving && seconds % 5.5 < .18)) 2f else 5f
        for (x in listOf(37f,63f)) c.drawRoundRect(x,25f,x+9,25+eyeHeight,2f,2f,paint)
        paint.style = Paint.Style.STROKE; c.drawPath(mouth,paint)
        paint.color = ink; paint.style = Paint.Style.FILL; c.drawRoundRect(34f,47f,46f,54f,3f,3f,paint); c.drawRoundRect(66f,47f,78f,54f,3f,3f,paint)
        c.restore()
    }
}
