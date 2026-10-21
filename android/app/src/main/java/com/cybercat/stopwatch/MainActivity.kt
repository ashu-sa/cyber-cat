package com.cybercat.stopwatch

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity: Activity() {
    private val cyan = Color.rgb(48,195,232)
    private val bg = Color.rgb(20,24,32)
    private val surface = Color.rgb(30,36,48)
    private lateinit var root: LinearLayout
    private var timer: TextView? = null
    private var cat: CatView? = null
    private var tab = "Timer"
    private var selected = LocalDate.now()
    private var month = YearMonth.now()
    private var scope = "Day"
    private var naming = false
    private var syncTicks=0
    private var phase = ""
    private var requestOverlay = false
    private var deleted: JSONObject? = null
    private val handler=Handler(Looper.getMainLooper())
    private val tick=object:Runnable { override fun run() {
        timer?.text=clockText(Repository.engine.elapsed())
        if(phase!=Repository.engine.phase) render()
        syncTicks++; if(syncTicks%30==0 && Cloud.owner!=null) Cloud.run({ Cloud.sync() })
        handler.postDelayed(this,1000)
    } }
    override fun onCreate(state:Bundle?) { super.onCreate(state); Repository.init(this); window.statusBarColor=bg; window.navigationBarColor=bg; render(); handle(intent); if(Repository.engine.phase!="idle" || Repository.prefs.getBoolean("showPet",false)) safe { service("SYNC") } }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    private fun handle(intent:Intent?) {
        if(intent?.action=="FINISH") safe { Repository.finish(); service("SYNC"); saveDialog() }
        else if(intent?.action=="SAVE" || Repository.engine.phase=="finished") saveDialog()
        intent?.action=null
    }
    override fun onResume() {
        super.onResume(); Cloud.changed={ if(!isFinishing && !isDestroyed) render() }; if(Cloud.owner!=null) Cloud.run({ Cloud.sync() }); handler.removeCallbacks(tick); handler.post(tick)
        if(requestOverlay && Settings.canDrawOverlays(this)) { Repository.prefs.edit().putBoolean("showPet",true).apply(); service("SHOW"); requestOverlay=false }
    }
    override fun onPause() { Cloud.changed=null; handler.removeCallbacks(tick); super.onPause() }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun safe(action:()->Unit) { try { action() } catch(e:Exception) { AlertDialog.Builder(this).setTitle("Couldn’t complete action").setMessage(e.localizedMessage).setPositiveButton("OK",null).show() } }
    private fun service(action:String) { startForegroundService(Intent(this,TimerService::class.java).setAction(action)) }
    private fun accountDialog() {
        val dialog=Dialog(this)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(22),dp(20),dp(22),dp(20)); setBackgroundColor(bg) }
        dialog.setContentView(content); dialog.window?.setLayout(-1,-2)
        content.addView(text("Your focus, everywhere",22f,cyan))
        val status=text(Cloud.status,13f,Color.LTGRAY); content.addView(status)
        fun run(action:()->Unit,done:(String?)->Unit={}) {
            if(Cloud.busy.get()) { status.text="Sync in progress…"; return }
            status.text="Connecting…"
            Cloud.run(action) { error ->
                if(!isFinishing && !isDestroyed) { status.text=Cloud.status; done(error) }
            }
        }
        if(Cloud.owner!=null) {
            content.addView(text("Signed in as ${Cloud.username}"))
            content.addView(button("Sync now") { run({ Cloud.sync() }) })
            if(Cloud.hasConflicts()) for(keep in listOf(true,false)) content.addView(button(if(keep)"Keep my changes" else "Use cloud changes") { run({ Cloud.resolve(keep); Cloud.sync() }) })
            content.addView(button("Sign out") {
                if(Repository.engine.phase!="idle" || Cloud.busy.get()) status.text="Finish your timer and wait for sync before switching accounts."
                else { Cloud.signOut(); Repository.loadGuest(); render(); dialog.dismiss() }
            })
        } else {
            val username=EditText(this).apply { hint="Username"; setSingleLine(); inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; setTextColor(Color.WHITE) }; content.addView(username)
            val pin=EditText(this).apply { hint="4-digit PIN"; inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD; filters=arrayOf(android.text.InputFilter.LengthFilter(4)); setTextColor(Color.WHITE) }; content.addView(pin)
            val upload=CheckBox(this).apply { text="Add this device’s existing sessions to my account"; setTextColor(Color.WHITE) }; content.addView(upload)
            content.addView(text("Use the same username and PIN on every device. Keep them safe; this account has no email recovery.",12f,Color.LTGRAY))
            for(signup in listOf(false,true)) content.addView(button(if(signup)"Create account" else "Sign in") {
                val name=username.text.toString().trim().lowercase(java.util.Locale.ROOT); val code=pin.text.toString()
                if(Repository.engine.phase!="idle") { status.text="Finish your current timer before signing in." }
                else if(!Regex("^[a-z0-9_]{3,24}$").matches(name) || !Regex("^[0-9]{4}$").matches(code)) status.text="Use 3–24 letters, numbers or underscores and a 4-digit PIN."
                else {
                    val existing=if(upload.isChecked) Repository.sessions.toList() else emptyList()
                    run({ Cloud.authenticate(name,code,signup,existing) }) { error ->
                        pin.setText("")
                        if(error==null) { dialog.dismiss(); render(); Cloud.run({ Cloud.sync() }) }
                    }
                }
            })
        }
        content.addView(button("Close") { dialog.dismiss() }); dialog.show(); dialog.window?.setLayout(-1,-2)
    }
    private fun notifications() { if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),5) }
    private fun text(value:String,size:Float=15f,color:Int=Color.WHITE)=TextView(this).apply { text=value; textSize=size; setTextColor(color); setPadding(0,dp(5),0,dp(5)) }
    private fun card(color:Int=surface)=GradientDrawable().apply { setColor(color); cornerRadius=dp(16).toFloat() }
    private fun button(value:String,description:String=value,action:()->Unit)=Button(this).apply { text=value; contentDescription=description; textSize=13f; isAllCaps=false; setTextColor(cyan)
        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(60,48,195,232)),GradientDrawable().apply { setColor(surface); cornerRadius=dp(8).toFloat(); setStroke(dp(1),Color.rgb(47,67,83)) },null)
        setOnClickListener { safe(action) }; minHeight=dp(48) }
    private fun line(parent:LinearLayout=root, build:(LinearLayout)->Unit) { val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }; parent.addView(row); build(row) }
    private fun render() {
        phase=Repository.engine.phase; timer=null; cat=null
        val outer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(20),dp(12),dp(20),dp(10)) }
        // Keep content below system bars on edge-to-edge Android versions.
        outer.setOnApplyWindowInsetsListener { view,insets -> if(Build.VERSION.SDK_INT>=30) { val bars=insets.getInsets(WindowInsets.Type.systemBars()); view.setPadding(dp(20)+bars.left,dp(12)+bars.top,dp(20)+bars.right,dp(10)+bars.bottom) }; insets }
        outer.addView(text("Cyber Cat",28f).apply { typeface=Typeface.DEFAULT_BOLD })
        line(outer) { row -> for(name in listOf("Timer","Journal","Settings")) row.addView(button(name) { tab=name; render() },LinearLayout.LayoutParams(0,dp(48),1f)) }
        val scroll=ScrollView(this); root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,dp(14),0,dp(18)) }; scroll.addView(root); outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(outer)
        when(tab) { "Timer" -> timerPage(); "Journal" -> journal(); else -> settings() }
        Repository.error?.let { root.addView(text(it,13f,Color.rgb(255,170,130))) }
    }
    private fun timerPage() {
        cat=CatView(this); root.addView(cat,LinearLayout.LayoutParams(-1,dp(150))); cat?.refresh()
        timer=text(clockText(Repository.engine.elapsed()),42f,cyan).apply { gravity=Gravity.CENTER; typeface=Typeface.MONOSPACE }; root.addView(timer)
        root.addView(text(when(phase) { "running" -> "Locked in ⚡"; "paused" -> "Paused — take a breather"; "finished" -> "Session cleared"; else -> "A little focus goes a long way" },14f,Color.LTGRAY).apply { gravity=Gravity.CENTER })
        line { row ->
            row.gravity=Gravity.CENTER
            row.addView(button(if(phase=="running") "Ⅱ" else if(phase=="finished") "↓" else "▶",if(phase=="running") "Pause" else if(phase=="finished") "Save session" else "Start or resume") {
                if(phase=="finished") saveDialog() else { notifications(); if(Repository.engine.phase=="running") Repository.pause() else Repository.start(); service("SYNC"); render() }
            },LinearLayout.LayoutParams(dp(64),dp(56)))
            row.addView(button("✓","Finish session") { if(Repository.engine.phase!="idle") { Repository.finish(); service("SYNC"); saveDialog(); render() } }.apply { isEnabled=phase!="idle" },LinearLayout.LayoutParams(dp(64),dp(56)))
        }
        root.addView(text("The stopwatch keeps running in the notification shade. Floating pet is optional.",13f,Color.LTGRAY))
        root.addView(button("Floating pet options") { tab="Settings"; render() })
        if(phase!="idle") root.addView(button("Cancel session…") { AlertDialog.Builder(this).setTitle("Discard this session?").setMessage("This session won’t be saved.").setNegativeButton("Keep session",null).setPositiveButton("Discard") { _,_-> safe { Repository.discard(); service("SYNC"); render() } }.show() })
    }
    private fun saveDialog() {
        if(naming || Repository.engine.phase!="finished") return
        naming=true
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(22),dp(12),dp(22),dp(4)) }
        content.addView(text("${focusText(Repository.engine.elapsed())} of focus",16f,cyan))
        val spinner=Spinner(this)
        val choices=listOf("Choose a category","Job Apply","Upskill","Interview Prep","Work","Custom task…")
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,choices); content.addView(spinner,LinearLayout.LayoutParams(-1,dp(54)))
        val name=EditText(this).apply { hint="Or type a custom task name"; setSingleLine(); setTextColor(Color.WHITE) }; content.addView(name)
        spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { if(position in 1..4) name.setText(choices[position]); if(position==5) { name.setText(""); name.requestFocus() } }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        content.addView(text("Close keeps this session ready to save later.",12f,Color.LTGRAY))
        val dialog=AlertDialog.Builder(this).setTitle("What did you work on?").setView(content).setNegativeButton("Close",null).setPositiveButton("Save",null).create()
        dialog.setOnDismissListener { naming=false }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            safe { val day=dayOf(Repository.started); Repository.save(name.text.toString()); service("SYNC"); dialog.dismiss(); tab="Journal"; selected=day; month=YearMonth.from(day); scope="Day"; render() }
        } }; dialog.show()
    }
    private fun dayOf(timestamp:Long)=Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    private fun recordsFor(day:LocalDate)=Repository.sessions.filter { dayOf(it.getLong("start"))==day }
    private fun total(records:List<JSONObject>)=records.sumOf { it.getLong("duration") }
    private fun journal() {
        root.addView(text("Focus journal",24f).apply { typeface=Typeface.DEFAULT_BOLD })
        val today=LocalDate.now()
        root.addView(text("Today ${focusText(total(recordsFor(today)))}   ·   Yesterday ${focusText(total(recordsFor(today.minusDays(1))))}",17f,cyan))
        line { row -> row.addView(button("‹","Previous month") { month=month.minusMonths(1); render() }); row.addView(text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),19f),LinearLayout.LayoutParams(0,-2,1f)); row.addView(button("›","Next month") { month=month.plusMonths(1); render() }) }
        val monthRecords=Repository.sessions.filter { YearMonth.from(dayOf(it.getLong("start")))==month }
        root.addView(text("${focusText(total(monthRecords))} tracked this month",13f,Color.LTGRAY))
        val grid=GridLayout(this).apply { columnCount=7 }
        for(day in listOf("M","T","W","T","F","S","S")) grid.addView(text(day,12f,Color.LTGRAY).apply { gravity=Gravity.CENTER },GridLayout.LayoutParams().apply { width=0; height=dp(30); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) })
        val start=month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value-1).toLong())
        for(index in 0..41) {
            val day=start.plusDays(index.toLong()); val sum=total(recordsFor(day))
            val cell=TextView(this).apply {
                text="${day.dayOfMonth}\n${if(sum>0) focusText(sum) else "—"}"; textSize=10f; gravity=Gravity.CENTER
                setTextColor(if(YearMonth.from(day)==month) Color.WHITE else Color.GRAY)
                background=card(if(day==selected) Color.rgb(29,93,112) else if(sum>0) Color.rgb(28,60,75) else surface)
                contentDescription=day.toString()+", "+focusText(sum)+" tracked"
                setOnClickListener { selected=day; scope="Day"; render() }
            }
            grid.addView(cell,GridLayout.LayoutParams().apply { width=0; height=dp(54); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(dp(2),dp(2),dp(2),dp(2)) })
        }; root.addView(grid)
        line { row -> for(name in listOf("Day","Week","Month")) row.addView(button(name) { scope=name; render() },LinearLayout.LayoutParams(0,dp(48),1f)) }
        val weekStart=selected.minusDays((selected.dayOfWeek.value-1).toLong())
        val records=Repository.sessions.filter { val d=dayOf(it.getLong("start")); when(scope) { "Week"-> !d.isBefore(weekStart)&&d.isBefore(weekStart.plusDays(7)); "Month"->YearMonth.from(d)==YearMonth.from(selected); else->d==selected } }
        root.addView(text("${selected.format(DateTimeFormatter.ofPattern("EEE, MMM d"))} · $scope",19f))
        root.addView(text("${focusText(total(records))} · ${records.size} sessions",24f,cyan))
        root.addView(text(records.groupBy { Repository.deviceLabel(it) }.toSortedMap().map { (name,items) -> "$name ${focusText(total(items))}" }.joinToString(" · "),13f,Color.LTGRAY))
        deleted?.let { item -> root.addView(button("Undo last deletion") { Repository.restore(item); deleted=null; render() }) }
        if(records.isEmpty()) root.addView(text("No sessions for this period yet.",14f,Color.LTGRAY))
        for(record in records.sortedByDescending { it.getLong("start") }) {
            val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(10),dp(14),dp(10)); background=card() }
            box.addView(text(record.getString("name"),17f))
            box.addView(text(Repository.deviceLabel(record),12f,Color.LTGRAY))
            box.addView(text("${Instant.ofEpochMilli(record.getLong("start")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · HH:mm"))}   ${focusText(record.getLong("duration"))}",13f,cyan))
            line(box) { row ->
                row.addView(button("✎","Rename session") { val input=EditText(this).apply { setText(record.getString("name")); setSingleLine() }; AlertDialog.Builder(this).setTitle("Rename session").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_-> safe { Repository.rename(record.getString("id"),input.text.toString()); render() } }.show() })
                row.addView(button("×","Delete session") { AlertDialog.Builder(this).setTitle("Delete session?").setMessage(record.getString("name")).setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_-> safe { deleted=Repository.delete(record.getString("id")); render() } }.show() })
            }
            root.addView(box,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        }
        root.addView(text("Totals exclude pauses. Sessions belong to their local start date.",11f,Color.LTGRAY))
    }
    private fun settings() {
        root.addView(button("Account / Sync") { accountDialog() })
        root.addView(text(if(Cloud.owner!=null) "${Cloud.username} · ${Cloud.status}" else "Local only · sign in to sync devices",13f,Color.LTGRAY))
        root.addView(text("Your desktop companion",24f))
        root.addView(text("Notification controls work without floating-window permission. To use the cat above other apps, allow “Display over other apps”.",14f,Color.LTGRAY))
        root.addView(button(if(Settings.canDrawOverlays(this)) "Show floating cat" else "Enable floating cat…") {
            notifications()
            if(Settings.canDrawOverlays(this)) { Repository.prefs.edit().putBoolean("showPet",true).apply(); service("SHOW"); Toast.makeText(this,"Floating cat enabled",Toast.LENGTH_SHORT).show() }
            else { requestOverlay=true; startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
        })
        root.addView(button("Hide floating cat") { Repository.prefs.edit().putBoolean("showPet",false).apply(); service("HIDE") })
        val opacity=text("Pet opacity ${(Repository.prefs.getFloat("opacity",1f)*100).toInt()}%",16f); root.addView(opacity)
        root.addView(SeekBar(this).apply {
            max=70; progress=(Repository.prefs.getFloat("opacity",1f)*100).toInt()-30
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean) { val value=(progress+30)/100f; Repository.prefs.edit().putFloat("opacity",value).apply(); opacity.text="Pet opacity ${progress+30}%" }
                override fun onStartTrackingTouch(bar:SeekBar?) {}; override fun onStopTrackingTouch(bar:SeekBar?) {}
            })
        })
        root.addView(Switch(this).apply { text="Animate cat"; isChecked=Repository.prefs.getBoolean("animate",true); setOnCheckedChangeListener { _,value->Repository.prefs.edit().putBoolean("animate",value).apply() } })
        root.addView(button("Notification settings") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName)) })
        root.addView(text("Swipe the app away without losing the timer. Android’s Force stop, reboot, and some battery managers can stop the service; reopen Cyber Cat to restore the saved session. No internet or account is needed.",13f,Color.LTGRAY))
    }
}
