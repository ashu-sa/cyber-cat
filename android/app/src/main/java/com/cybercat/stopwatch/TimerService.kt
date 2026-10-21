package com.cybercat.stopwatch

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*

class TimerService: Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windows: WindowManager
    private var pet: LinearLayout? = null
    private var cat: TreeView? = null
    private var timeLabel: TextView? = null
    private var play: Button? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastNotice = ""
    private val tick = object: Runnable { override fun run() {
        try { syncOverlay(); if (Repository.engine.phase != lastNotice) notifyState() } catch (_: Exception) { removeOverlay() }
        handler.postDelayed(this, 1000)
    } }
    private val checkpoint = object: Runnable { override fun run() { try { Repository.checkpoint() } catch (_: Exception) {} ; handler.postDelayed(this,15000) } }
    override fun onCreate() {
        super.onCreate(); Repository.init(this); windows = getSystemService(WindowManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("timer", "Running stopwatch", NotificationManager.IMPORTANCE_LOW))
        handler.post(tick); handler.postDelayed(checkpoint,15000)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(7, notification())
        try {
            when (intent?.action) {
                "START" -> Repository.start()
                "PAUSE" -> Repository.pause()
                "FINISH" -> Repository.finish()
                "HIDE" -> Repository.prefs.edit().putBoolean("showPet",false).apply()
                "SHOW" -> Repository.prefs.edit().putBoolean("showPet",true).apply()
            }
            notifyState(); syncOverlay()
            if (Repository.engine.phase == "idle" && pet == null) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        } catch (e: Exception) { Toast.makeText(this,e.localizedMessage,Toast.LENGTH_LONG).show() }
        return START_STICKY
    }
    private fun notification(): Notification {
        val phase = Repository.engine.phase
        val open = PendingIntent.getActivity(this, 10, Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val finish = PendingIntent.getActivity(this,11, Intent(this,MainActivity::class.java).setAction("FINISH").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this,"timer").setSmallIcon(com.cybercat.stopwatch.R.drawable.ic_cat).setContentTitle("Focanopy · " + when(phase) { "running" -> "Locked in"; "paused" -> "Paused"; "finished" -> "Ready to save"; else -> "Floating companion" })
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true).setShowWhen(false).setCategory(if(Build.VERSION.SDK_INT>=31) Notification.CATEGORY_STOPWATCH else Notification.CATEGORY_SERVICE)
        if (phase == "running") builder.setWhen(System.currentTimeMillis()-Repository.engine.elapsed()).setShowWhen(true).setUsesChronometer(true)
        else builder.setContentText(clockText(Repository.engine.elapsed()) + if (phase == "finished") " · Tap to name and save" else " · Tap for controls")
        val action = if (phase == "running") "PAUSE" else "START"
        if (phase != "finished") builder.addAction(Notification.Action.Builder(null, if (phase == "running") "Pause" else if (phase == "paused") "Resume" else "Start", PendingIntent.getService(this,12,Intent(this,TimerService::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build())
        if (phase != "idle") builder.addAction(Notification.Action.Builder(null,if (phase == "finished") "Save" else "Finish",finish).build())
        return builder.build()
    }
    private fun notifyState() { getSystemService(NotificationManager::class.java).notify(7,notification()); lastNotice = Repository.engine.phase }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun syncOverlay() {
        if (!Repository.prefs.getBoolean("showPet",false) || !Settings.canDrawOverlays(this)) { removeOverlay(); return }
        if (pet == null) createOverlay()
        timeLabel?.text = clockText(Repository.engine.elapsed())
        play?.text = when (Repository.engine.phase) { "running" -> "Ⅱ"; "finished" -> "↓"; else -> "▶" }
        play?.contentDescription = when(Repository.engine.phase) { "running" -> "Pause"; "finished" -> "Save session"; else -> "Start or resume" }
        pet?.alpha = Repository.prefs.getFloat("opacity",1f).coerceIn(.3f,1f)
        cat?.refresh()
    }
    private fun createOverlay() {
        val root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(0,0,0,0) }
        cat = TreeView(this); root.addView(cat,LinearLayout.LayoutParams(dp(120),dp(58)))
        timeLabel = TextView(this).apply { setTextColor(Color.rgb(173,218,173)); textSize=13f; typeface=android.graphics.Typeface.MONOSPACE; setBackgroundColor(Color.rgb(17,27,23)); gravity=Gravity.CENTER }
        root.addView(timeLabel,LinearLayout.LayoutParams(dp(120),dp(25)))
        val row = LinearLayout(this).apply { gravity=Gravity.CENTER }
        fun control(symbol: String, description: String, action: () -> Unit): Button = Button(this).apply {
            text=symbol; textSize=12f; contentDescription=description; setTextColor(Color.rgb(173,218,173))
            background=android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(17,27,23)); cornerRadius=dp(5).toFloat(); setStroke(dp(1),Color.rgb(86,126,88)) }; minWidth=0; minimumWidth=0; minHeight=0; minimumHeight=0; setPadding(0,0,0,0); setOnClickListener { action() }
        }
        play = control("▶","Start or resume") {
            if (Repository.engine.phase == "finished") openApp("SAVE") else {
                try { if (Repository.engine.phase == "running") Repository.pause() else Repository.start(); notifyState(); syncOverlay() } catch (e: Exception) { Toast.makeText(this,e.localizedMessage,Toast.LENGTH_LONG).show() }
            }
        }
        row.addView(play,LinearLayout.LayoutParams(dp(40),dp(38)))
        row.addView(control("✓","Finish session") { if (Repository.engine.phase != "idle") openApp("FINISH") },LinearLayout.LayoutParams(dp(40),dp(38)))
        row.addView(control("×","Hide tree; stopwatch continues") { Repository.prefs.edit().putBoolean("showPet",false).apply(); removeOverlay(); if (Repository.engine.phase == "idle") { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() } },LinearLayout.LayoutParams(dp(36),dp(38)))
        root.addView(row)
        val p = WindowManager.LayoutParams(dp(144),dp(126),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT)
        p.gravity=Gravity.TOP or Gravity.START; p.x=Repository.prefs.getInt("petX",20); p.y=Repository.prefs.getInt("petY",150)
        fun clamp() { val bounds = if (Build.VERSION.SDK_INT>=30) windows.currentWindowMetrics.bounds else android.graphics.Rect(0,0,resources.displayMetrics.widthPixels,resources.displayMetrics.heightPixels); p.x=p.x.coerceIn(0,(bounds.width()-p.width).coerceAtLeast(0)); p.y=p.y.coerceIn(0,(bounds.height()-p.height-dp(32)).coerceAtLeast(0)) }
        if (Build.VERSION.SDK_INT >= 30) clamp()
        var x=0f; var y=0f; var px=0; var py=0; var moved=false
        cat!!.setOnClickListener { openApp("OPEN") }
        cat!!.setOnTouchListener { view, e ->
            when(e.action) {
                MotionEvent.ACTION_DOWN -> { x=e.rawX; y=e.rawY; px=p.x; py=p.y; moved=false }
                MotionEvent.ACTION_MOVE -> { if (kotlin.math.abs(e.rawX-x)+kotlin.math.abs(e.rawY-y)>8) moved=true; p.x=px+(e.rawX-x).toInt(); p.y=py+(e.rawY-y).toInt(); if(Build.VERSION.SDK_INT>=30) clamp(); try { windows.updateViewLayout(root,p) } catch (_:Exception) {} }
                MotionEvent.ACTION_UP -> { Repository.prefs.edit().putInt("petX",p.x).putInt("petY",p.y).apply(); if(!moved) view.performClick() }
            }; true
        }
        pet=root; params=p
        try { windows.addView(root,p) } catch(e:Exception) { pet=null; Toast.makeText(this,"Floating tree unavailable; use notification controls.",Toast.LENGTH_LONG).show() }
    }
    private fun openApp(action:String) { if(action=="FINISH") { Repository.finish(); notifyState(); syncOverlay() }; try { startActivity(Intent(this,MainActivity::class.java).setAction(if(action=="FINISH") "SAVE" else action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) } catch (_:Exception) { if(action=="FINISH") Repository.finish(); notifyState() } }
    private fun removeOverlay() { pet?.let { try { windows.removeView(it) } catch (_:Exception) {} }; pet=null; cat=null; timeLabel=null; play=null }
    override fun onDestroy() { try { Repository.checkpoint() } catch (_:Exception) {}; handler.removeCallbacksAndMessages(null); removeOverlay(); super.onDestroy() }
    override fun onBind(intent:Intent?) = null
}
