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
    private val cyan = Color.rgb(173,218,173)
    private val bg = Color.rgb(17,27,23)
    private val surface = Color.rgb(30,44,36)
    private lateinit var root: LinearLayout
    private var timer: TextView? = null
    private var growthHint: TextView? = null
    private var growthBar: ProgressBar? = null
    private var scroller: ScrollView? = null
    private var renderedTab = ""
    private var groveStatus: TextView? = null
    private var cat: TreeView? = null
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
        cat?.invalidate()
        updateGrowth()
        groveStatus?.text=Forest.text(Repository.growthElapsed())+(if(Forest.garden(Repository.growthElapsed())) " · Garden grown" else " · One tree per focused hour")
        if(phase!=Repository.engine.phase) render()
        syncTicks++; if(syncTicks%30==0 && Cloud.owner!=null) Cloud.run({ Cloud.sync() })
        handler.postDelayed(this,1000)
    } }
    override fun onCreate(state:Bundle?) { super.onCreate(state); Repository.init(this); if(state!=null) { tab=state.getString("tab","Timer"); selected=LocalDate.parse(state.getString("selected",LocalDate.now().toString())); month=YearMonth.parse(state.getString("month",YearMonth.now().toString())); scope=state.getString("scope","Day"); requestOverlay=state.getBoolean("requestOverlay") }; window.statusBarColor=bg; window.navigationBarColor=bg; window.decorView.systemUiVisibility=window.decorView.systemUiVisibility and (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv(); render(); handle(intent); if(Repository.engine.phase!="idle" || Repository.prefs.getBoolean("showPet",false)) safe { service("SYNC") } }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    private fun handle(intent:Intent?) {
        if(intent?.action=="FINISH") safe { Repository.finish(); service("SYNC"); saveDialog() }
        else if(intent?.action=="SAVE" || Repository.engine.phase=="finished") saveDialog()
        intent?.action=null
    }
    override fun onResume() {
        super.onResume(); Cloud.changed={ if(!isFinishing && !isDestroyed) render() }; if(Cloud.owner!=null) Cloud.run({ Cloud.sync() }); handler.removeCallbacks(tick); handler.post(tick)
        if(tab=="Settings") render()
        if(requestOverlay && Settings.canDrawOverlays(this)) { Repository.prefs.edit().putBoolean("showPet",true).apply(); service("SHOW"); requestOverlay=false }
    }
    override fun onSaveInstanceState(state:Bundle) {
        state.putString("tab",tab); state.putString("selected",selected.toString()); state.putString("month",month.toString()); state.putString("scope",scope); state.putBoolean("requestOverlay",requestOverlay)
        super.onSaveInstanceState(state)
    }
    override fun onPause() { Cloud.changed=null; handler.removeCallbacks(tick); try { Repository.checkpoint() } catch(e:Exception) { Repository.error=e.localizedMessage }; super.onPause() }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun safe(action:()->Unit) { try { action() } catch(e:Exception) { AlertDialog.Builder(this).setTitle("Couldn’t complete action").setMessage(e.localizedMessage).setPositiveButton("OK",null).show() } }
    private fun service(action:String) { startForegroundService(Intent(this,TimerService::class.java).setAction(action)) }
    private fun accountDialog() {
        val dialog=Dialog(this)
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(22),dp(20),dp(22),dp(20)); setBackgroundColor(bg) }
        dialog.setContentView(ScrollView(this).apply { addView(content); isFillViewport=false }); dialog.window?.setLayout(-1,-2)
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
        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(50,173,218,173)),GradientDrawable().apply { setColor(surface); cornerRadius=dp(14).toFloat(); setStroke(dp(1),Color.rgb(58,78,62)) },null)
        setOnClickListener { safe(action) }; minHeight=dp(48) }
    private fun line(parent:LinearLayout=root, build:(LinearLayout)->Unit) { val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }; parent.addView(row); build(row) }
    private fun render() {
        val previousScroll=if(renderedTab==tab) scroller?.scrollY ?: 0 else 0
        renderedTab=tab
        phase=Repository.engine.phase; timer=null; cat=null; groveStatus=null; growthHint=null; growthBar=null
        val outer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(20),dp(12),dp(20),dp(10)) }
        outer.setOnApplyWindowInsetsListener { view,insets -> if(Build.VERSION.SDK_INT>=30) { val bars=insets.getInsets(WindowInsets.Type.systemBars()); view.setPadding(dp(20)+bars.left,dp(12)+bars.top,dp(20)+bars.right,dp(10)+bars.bottom) }; insets }
        line(outer) { row ->
            val title=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
            title.addView(text("Focanopy",27f).apply { typeface=Typeface.DEFAULT_BOLD; setPadding(0,0,0,0) })
            title.addView(text("MAKE ROOM TO GROW",10f,cyan).apply { letterSpacing=.16f })
            row.addView(title,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(ImageButton(this).apply { setImageResource(R.drawable.ic_account); contentDescription="Account / Sync"; background=card(); setPadding(dp(12),dp(12),dp(12),dp(12)); setOnClickListener { safe { accountDialog() } } },LinearLayout.LayoutParams(dp(48),dp(48)))
        }
        val scroll=ScrollView(this).apply { isFillViewport=true; clipToPadding=false }; scroller=scroll
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(0,dp(16),0,dp(18)) }
        scroll.addView(root); outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(0,dp(8),0,0) }
        for((name,symbol) in listOf("Timer" to "◷","Journal" to "▦","Settings" to "⚙")) {
            nav.addView(button("$symbol  $name",name) { tab=name; render() }.apply {
                isSelected=tab==name; setTextColor(if(isSelected) bg else cyan)
                background=card(if(isSelected) cyan else surface)
            },LinearLayout.LayoutParams(0,dp(50),1f).apply { setMargins(dp(3),0,dp(3),0) })
        }
        outer.addView(nav); setContentView(outer)
        when(tab) { "Timer" -> timerPage(); "Journal" -> journal(); else -> settings() }
        Repository.error?.let { root.addView(text(it,13f,Color.rgb(255,170,130))) }
        scroll.post { scroll.scrollTo(0,previousScroll) }
    }
    private fun updateGrowth() {
        val elapsed=Repository.growthElapsed()
        growthHint?.text=Forest.nextTreeText(elapsed)
        growthBar?.progress=((elapsed%Forest.HOUR)*1000/Forest.HOUR).toInt()
    }
    private fun timerPage() {
        val today=total(recordsFor(LocalDate.now()))
        line { row ->
            for((title,value) in listOf("SAVED TODAY" to focusText(today),"TREES TODAY" to Forest.trees(today).toString())) {
                val metric=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(10),dp(16),dp(10)); background=card() }
                metric.addView(text(title,10f,Color.rgb(161,181,165)).apply { letterSpacing=.08f })
                metric.addView(text(value,23f,cyan).apply { typeface=Typeface.DEFAULT_BOLD })
                row.addView(metric,LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),0,dp(3),dp(8)) })
            }
        }
        cat=TreeView(this); root.addView(cat,LinearLayout.LayoutParams(-1,dp(150))); cat?.refresh()
        timer=text(clockText(Repository.engine.elapsed()),42f,cyan).apply { gravity=Gravity.CENTER; typeface=Typeface.create("monospace",Typeface.NORMAL) }; root.addView(timer)
        root.addView(text(when(phase) { "running" -> "A little water. A little growth."; "paused" -> "Watering paused. Your tree is resting."; "finished" -> "Lovely work. Save your focus."; else -> "Your next tree starts here." },14f,Color.rgb(182,199,185)).apply { gravity=Gravity.CENTER })
        line { row ->
            row.gravity=Gravity.CENTER
            row.addView(button(if(phase=="running") "Ⅱ" else if(phase=="finished") "↓" else "▶",if(phase=="running") "Pause" else if(phase=="finished") "Save session" else "Start or resume") {
                if(phase=="finished") saveDialog() else { notifications(); if(Repository.engine.phase=="running") Repository.pause() else Repository.start(); service("SYNC"); render() }
            }.apply { setTextColor(bg); background=card(cyan); textSize=20f },LinearLayout.LayoutParams(dp(56),dp(56)).apply { setMargins(dp(4),dp(10),dp(4),dp(10)) })
            row.addView(button("✓","Finish session") { if(Repository.engine.phase!="idle") { Repository.finish(); service("SYNC"); render(); saveDialog() } }.apply { isEnabled=phase!="idle"; visibility=if(phase=="finished")View.GONE else View.VISIBLE; alpha=if(isEnabled)1f else .35f; textSize=20f },LinearLayout.LayoutParams(dp(56),dp(56)).apply { setMargins(dp(4),dp(10),dp(4),dp(10)) })
        }
        if(phase=="finished") {
            root.addView(text("Not ready to save? Continue this session or start fresh.",13f,Color.LTGRAY))
            line { row ->
                row.addView(button("▶","Resume timer") { Repository.resumeFinished();service("SYNC");render() },LinearLayout.LayoutParams(0,dp(48),1f))
                row.addView(button("↺","Reset timer") { AlertDialog.Builder(this).setTitle("Reset this unsaved timer?").setMessage("Saved history stays in your journal.").setNegativeButton("Keep timer",null).setPositiveButton("Reset") { _,_-> safe { Repository.discard();service("SYNC");render() } }.show() },LinearLayout.LayoutParams(0,dp(48),1f))
            }
        }
        groveStatus=text(Forest.text(Repository.growthElapsed())+(if(Forest.garden(Repository.growthElapsed())) " · Garden grown" else " · One tree per focused hour"),14f,cyan).apply { gravity=Gravity.CENTER }; root.addView(groveStatus)
        growthBar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply { max=1000; progressTintList=android.content.res.ColorStateList.valueOf(cyan); progressBackgroundTintList=android.content.res.ColorStateList.valueOf(surface); contentDescription="Progress to next tree" }
        root.addView(growthBar,LinearLayout.LayoutParams(-1,dp(8)).apply { setMargins(dp(35),dp(8),dp(35),0) })
        growthHint=text("",12f,Color.rgb(161,181,165)).apply { gravity=Gravity.CENTER }; root.addView(growthHint); updateGrowth()
        root.addView(text("30m · Young plant     1h · Tree     8h · Garden",11f,Color.rgb(161,181,165)).apply { gravity=Gravity.CENTER })
        if(phase=="idle") {
            root.addView(text("Grow here, or take your tree with you.",13f,Color.LTGRAY).apply { gravity=Gravity.CENTER })
            root.addView(button("Floating tree options") { tab="Settings"; render() })
        } else {
            root.addView(text("Your timer continues in the notification shade.",12f,Color.LTGRAY).apply { gravity=Gravity.CENTER })
            root.addView(button("Cancel session…") { AlertDialog.Builder(this).setTitle("Discard this session?").setMessage("This session won’t be saved.").setNegativeButton("Keep session",null).setPositiveButton("Discard") { _,_-> safe { Repository.discard(); service("SYNC"); render() } }.show() })
        }
    }
    private fun saveDialog() {
        if(naming || Repository.engine.phase!="finished") return
        naming=true
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(22),dp(12),dp(22),dp(4)) }
        content.addView(text("${focusText(Repository.engine.elapsed())} of focus",16f,cyan))
        val spinner=Spinner(this).apply { contentDescription="Session category" }
        val choices=listOf("Choose a category","Job Apply","Upskill","Interview Prep","Work","Custom task…")
        spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,choices); content.addView(spinner,LinearLayout.LayoutParams(-1,dp(54)))
        val name=EditText(this).apply { hint="Or type a custom task name"; contentDescription="Session task name"; setSingleLine(); setTextColor(Color.WHITE); setText(Repository.prefs.getString("draftName","")) }; content.addView(name)
        name.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { Repository.prefs.edit().putString("draftName",s.toString()).apply() }
            override fun afterTextChanged(s:android.text.Editable?) {}
        })
        spinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { if(position in 1..4) name.setText(choices[position]); if(position==5) { name.setText(""); name.requestFocus() } }
            override fun onNothingSelected(parent:AdapterView<*>?) {}
        }
        content.addView(text("Close to save later, resume, or reset from the timer screen.",12f,Color.LTGRAY))
        val dialog=AlertDialog.Builder(this).setIcon(R.mipmap.ic_launcher).setTitle("What did you work on?").setView(content).setNegativeButton("Close",null).setPositiveButton("Save",null).create()
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
        for(index in 0 until Forest.calendarCells(month.atDay(1).dayOfWeek.value-1,month.lengthOfMonth())) {
            val day=start.plusDays(index.toLong()); val sum=total(recordsFor(day))
            val cell=TextView(this).apply {
                text="${day.dayOfMonth}\n${if(sum>0) focusText(sum) else "—"}\n${Forest.cell(sum)}"; textSize=10f; gravity=Gravity.CENTER; includeFontPadding=false
                setTextColor(if(YearMonth.from(day)!=month) Color.GRAY else if(Forest.garden(sum)) Color.rgb(238,212,151) else Color.WHITE)
                background=card(if(day==selected) Color.rgb(57,91,61) else if(sum>0) Color.rgb(35,60,43) else surface)
                contentDescription=day.toString()+", "+focusText(sum)+" tracked, "+Forest.text(sum)+(if(Forest.garden(sum))", garden" else "")
                setOnClickListener { selected=day; month=YearMonth.from(day); scope="Day"; render() }
            }
            grid.addView(cell,GridLayout.LayoutParams().apply { width=0; height=dp(68); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(dp(2),dp(2),dp(2),dp(2)) })
        }; root.addView(grid)
        line { row -> for(name in listOf("Day","Week","Month")) row.addView(button(name) { scope=name; render() }.apply { isSelected=scope==name; background=card(if(scope==name) cyan else surface); setTextColor(if(scope==name) bg else cyan) },LinearLayout.LayoutParams(0,dp(44),1f).apply { setMargins(dp(2),dp(12),dp(2),dp(8)) }) }
        val weekStart=selected.minusDays((selected.dayOfWeek.value-1).toLong())
        val records=Repository.sessions.filter { val d=dayOf(it.getLong("start")); when(scope) { "Week"-> !d.isBefore(weekStart)&&d.isBefore(weekStart.plusDays(7)); "Month"->YearMonth.from(d)==YearMonth.from(selected); else->d==selected } }
        root.addView(text("${selected.format(DateTimeFormatter.ofPattern("EEE, MMM d"))} · $scope",19f))
        root.addView(text("${focusText(total(records))} · ${records.size} session${if(records.size==1) "" else "s"}",24f,cyan))
        root.addView(text(if(scope=="Day" && Forest.garden(total(records))) "Garden grown · "+Forest.text(total(records)) else Forest.summary(total(records),records.groupBy { dayOf(it.getLong("start")) }.values.map { total(it) }),18f,Color.rgb(169,218,177)))
        root.addView(text(records.groupBy { Repository.deviceLabel(it) }.toSortedMap().map { (name,items) -> "$name ${focusText(total(items))}" }.joinToString(" · "),13f,Color.LTGRAY))
        deleted?.let { item -> root.addView(button("Undo last deletion") { Repository.restore(item); deleted=null; render() }) }
        if(records.isEmpty()) root.addView(text("No sessions for this period yet.",14f,Color.LTGRAY))
        for(record in records.sortedByDescending { it.getLong("start") }) {
            val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(14),dp(10),dp(14),dp(10)); background=card() }
            box.addView(text(record.getString("name"),17f))
            box.addView(text(Repository.deviceLabel(record)+" · "+Forest.text(record.getLong("duration")),12f,Color.LTGRAY))
            box.addView(text("${Instant.ofEpochMilli(record.getLong("start")).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d · HH:mm"))}   ${focusText(record.getLong("duration"))}",13f,cyan))
            line(box) { row ->
                row.addView(button("✎","Rename session") { val input=EditText(this).apply { contentDescription="Rename task"; setText(record.getString("name")); setSingleLine() }; AlertDialog.Builder(this).setTitle("Rename session").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_-> safe { Repository.rename(record.getString("id"),input.text.toString()); render() } }.show() })
                row.addView(button("×","Delete session") { AlertDialog.Builder(this).setTitle("Delete session?").setMessage(record.getString("name")).setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_-> safe { deleted=Repository.delete(record.getString("id")); render() } }.show() })
            }
            root.addView(box,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        }
        root.addView(text("Totals exclude pauses. Sessions belong to their local start date.",11f,Color.LTGRAY))
    }
    private fun exportHistory() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="application/json"; putExtra(Intent.EXTRA_TITLE,"Focanopy-sessions-${LocalDate.now()}.json") },42)
    }
    @Deprecated("Native Activity document result callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==42 && resultCode==RESULT_OK) data?.data?.let { uri -> safe {
            val stream=contentResolver.openOutputStream(uri) ?: error("Could not open selected document")
            stream.bufferedWriter().use { it.write(org.json.JSONArray(Repository.sessions).toString(2)) }
            Toast.makeText(this,"Session history exported",Toast.LENGTH_SHORT).show()
        } }
    }
    private fun settings() {
        root.addView(button("Account / Sync") { accountDialog() })
        root.addView(text(if(Cloud.owner!=null) "${Cloud.username} · ${Cloud.status}" else "Local only · sign in to sync devices",13f,Color.LTGRAY))
        root.addView(text("Your floating grove",24f))
        root.addView(text("Notification controls work without floating-window permission. To use the tree above other apps, allow “Display over other apps”.",14f,Color.LTGRAY))
        root.addView(button(if(Settings.canDrawOverlays(this)) "Show floating tree" else "Enable floating tree…") {
            notifications()
            if(Settings.canDrawOverlays(this)) { Repository.prefs.edit().putBoolean("showPet",true).apply(); service("SHOW"); Toast.makeText(this,"Floating tree enabled",Toast.LENGTH_SHORT).show() }
            else { requestOverlay=true; startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
        })
        root.addView(button("Hide floating tree") { Repository.prefs.edit().putBoolean("showPet",false).apply(); service("HIDE") })
        val opacity=text("Tree opacity ${(Repository.prefs.getFloat("opacity",1f)*100).toInt()}%",16f); root.addView(opacity)
        root.addView(SeekBar(this).apply {
            max=70; progress=(Repository.prefs.getFloat("opacity",1f)*100).toInt()-30
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean) { val value=(progress+30)/100f; Repository.prefs.edit().putFloat("opacity",value).apply(); opacity.text="Tree opacity ${progress+30}%" }
                override fun onStartTrackingTouch(bar:SeekBar?) {}; override fun onStopTrackingTouch(bar:SeekBar?) {}
            })
        })
        root.addView(Switch(this).apply { text="Animate tree and water"; isChecked=Repository.prefs.getBoolean("animate",true); setOnCheckedChangeListener { _,value->Repository.prefs.edit().putBoolean("animate",value).apply() } })
        root.addView(text(if(Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) "Notifications enabled · background controls ready" else "Notifications disabled · enable for background controls",13f,Color.LTGRAY))
        root.addView(button("Notification settings") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName)) })
        root.addView(button("Export session history") { exportHistory() })
        root.addView(text("Focanopy · 1.4.0",12f,cyan))
        root.addView(text("Swipe the app away without losing the timer. Android’s Force stop, reboot, and some battery managers can stop the service; reopen Focanopy to restore the saved session. No internet or account is needed.",13f,Color.LTGRAY))
    }
}
