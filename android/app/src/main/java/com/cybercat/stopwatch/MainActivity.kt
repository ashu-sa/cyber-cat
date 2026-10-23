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
    private var focusTime:TextView?=null
    private var refreshFocusPermissions:(()->Unit)?=null
    private var growthHint: TextView? = null
    private var growthBar: ProgressBar? = null
    private var scroller: ScrollView? = null
    private var renderedTab = ""
    private var shellReady=false
    private lateinit var pageTitle:TextView
    private lateinit var pageSubtitle:TextView
    private val navItems=mutableMapOf<String,LinearLayout>()
    private val scrollPositions=mutableMapOf<String,Int>()
    private var groveStatus: TextView? = null
    private var cat: TreeView? = null
    private var tab = "Timer"
    private var selected = LocalDate.now()
    private var month = YearMonth.now()
    private var scope = "Day"
    private var naming = false
    private var accountSheet:Dialog?=null
    private var syncTicks=0
    private var phase = ""
    private var pomoStage=""
    private var requestOverlay = false
    private var deleted: JSONObject? = null
    private val handler=Handler(Looper.getMainLooper())
    private val tick=object:Runnable { override fun run() {
        Repository.advancePomodoro()
        timer?.text=timerText()
        focusTime?.text="Focus time  ·  "+clockText(Repository.engine.elapsed())
        cat?.invalidate()
        updateGrowth()
        groveStatus?.text=if(Repository.pomodoroEnabled) "🌸 ${Repository.engine.elapsed()/Pomodoro.FOCUS} flowers completed · Focus time excludes breaks" else Forest.text(Repository.growthElapsed())+(if(Forest.garden(Repository.growthElapsed())) " · Garden grown" else " · One tree per focused hour")
        if(tab=="Timer" && (phase!=Repository.engine.phase || pomoStage!=Repository.pomodoro.stage)) render()
        syncTicks++; if(syncTicks%30==0 && Cloud.owner!=null) Cloud.run({ Cloud.sync() })
        handler.postDelayed(this,1000)
    } }
    override fun onCreate(state:Bundle?) { super.onCreate(state); Repository.init(this); if(state!=null) { tab=state.getString("tab","Timer"); selected=LocalDate.parse(state.getString("selected",LocalDate.now().toString())); month=YearMonth.parse(state.getString("month",YearMonth.now().toString())); scope=state.getString("scope","Day"); requestOverlay=state.getBoolean("requestOverlay") }; window.statusBarColor=bg; window.navigationBarColor=bg; window.decorView.systemUiVisibility=window.decorView.systemUiVisibility and (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv(); render(); handle(intent); if(Repository.engine.phase!="idle" || Repository.prefs.getBoolean("showPet",false)) safe { service("SYNC") } }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    private fun handle(intent:Intent?) {
        val callback=intent?.dataString
        if(intent?.action==Intent.ACTION_VIEW && callback!=null && GoogleOAuth.isCallback(callback)) {
            intent.data=null; intent.action=null
            Cloud.run({ Cloud.completeGoogle(callback) }) { error ->
                if(!isFinishing && !isDestroyed) {
                    accountSheet?.dismiss(); render()
                    if(error==null) { Toast.makeText(this,"Signed in with Google",Toast.LENGTH_SHORT).show(); Cloud.run({ Cloud.sync() }) }
                    else { accountDialog() }
                }
            }
            return
        }
        if(intent?.action=="CANCEL_POMODORO") cancelPomodoroDialog()
        else if(intent?.action=="FINISH" && !Repository.pomodoroEnabled) safe { Repository.finish(); service("SYNC"); saveDialog() }
        else if(intent?.action=="SAVE" || Repository.engine.phase=="finished") saveDialog()
        intent?.action=null
    }
    override fun onResume() {
        super.onResume(); Cloud.changed={ if(!isFinishing && !isDestroyed) render() }; if(Cloud.owner!=null) Cloud.run({ Cloud.sync() }); handler.removeCallbacks(tick); handler.post(tick)
        if(tab=="Settings" || tab=="Timer") render()
        refreshFocusPermissions?.invoke()
        if(requestOverlay && Settings.canDrawOverlays(this)) { Repository.prefs.edit().putBoolean("showPet",true).apply(); service("SHOW"); requestOverlay=false;render() }
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
        val dialog=Dialog(this); accountSheet?.dismiss(); accountSheet=dialog
        val content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(22),dp(20),dp(22),dp(20)); background=card(bg) }
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
            content.addView(button("Continue with Google") {
                if(Repository.engine.phase!="idle") status.text="Finish your current timer before signing in."
                else if(Cloud.googlePending) status.text="Finish Google sign-in in your browser, or cancel below."
                else {
                    var url=""
                    val include=content.findViewWithTag<CheckBox>("include-existing")?.isChecked==true
                    run({ url=Cloud.beginGoogle(include) }) { error ->
                        if(error==null) {
                            try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))); dialog.dismiss() }
                            catch(_:ActivityNotFoundException) { Cloud.cancelGoogle(); status.text="No browser is available. Install a browser and try again." }
                        }
                    }
                }
            })
            if(Cloud.googlePending) {
                content.addView(text("Google sign-in is waiting in your browser.",12f,Color.LTGRAY))
                content.addView(button("Cancel Google sign-in") { Cloud.cancelGoogle(); dialog.dismiss(); accountDialog() })
            }
            content.addView(text("or use your existing username and PIN",12f,Color.LTGRAY))
            val username=EditText(this).apply { hint="Username"; setSingleLine(); inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS; setTextColor(Color.WHITE) }; content.addView(username)
            val pin=EditText(this).apply { hint="4-digit PIN"; inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD; filters=arrayOf(android.text.InputFilter.LengthFilter(4)); setTextColor(Color.WHITE) }; content.addView(pin)
            val upload=CheckBox(this).apply { tag="include-existing"; text="Add this device’s existing sessions to my account"; setTextColor(Color.WHITE) }; content.addView(upload)
            content.addView(text("Use the same Google account on every device. Username/PIN accounts are separate and have no email recovery.",12f,Color.LTGRAY))
            for(signup in listOf(false,true)) content.addView(button(if(signup)"Create account" else "Sign in") {
                val name=username.text.toString().trim().lowercase(java.util.Locale.ROOT); val code=pin.text.toString()
                if(Repository.engine.phase!="idle" || Cloud.googlePending) { status.text="Finish your current timer or cancel Google sign-in before signing in." }
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
        content.addView(button("Close") { dialog.dismiss() }); dialog.show(); dialog.window?.setBackgroundDrawable(card(bg)); dialog.window?.setLayout(resources.displayMetrics.widthPixels-dp(32),-2);dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    private fun focusApps() {
        val prefs=Repository.prefs
        val packages=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
            .filter {it.activityInfo.packageName!=packageName && it.activityInfo.packageName !in setOf("com.android.settings","com.google.android.dialer","com.android.dialer","com.android.phone")}
            .distinctBy {it.activityInfo.packageName}.sortedBy {it.loadLabel(packageManager).toString().lowercase()}
        val browsers=packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW,Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),PackageManager.GET_RESOLVED_FILTER).filter {it.filter?.countDataAuthorities()==0}.map {it.activityInfo.packageName}.toSet()
        val home=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),0).map {it.activityInfo.packageName}.toSet()
        val keyboards=(getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).enabledInputMethodList.map {it.packageName}.toSet()
        val blocked=(prefs.getStringSet("blockedApps",emptySet()) ?: emptySet()).toMutableSet()
        val excluded=(prefs.getStringSet("excludedApps",emptySet()) ?: emptySet()).toMutableSet()
        var all=prefs.getBoolean("focusBlockAll",false)
        val dialog=Dialog(this)
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(22),dp(20),dp(14));background=card(bg)}
        panel.addView(text("Apps & permissions",24f,cyan).apply {typeface=Typeface.DEFAULT_BOLD})
        panel.addView(text("Switch on the apps you want to block while you focus.",12f,Color.rgb(161,181,165)))
        fun switch(title:String,on:Boolean)=Switch(this).apply {text=title;isChecked=on;textSize=14f;setTextColor(cyan);minHeight=dp(52);setPadding(dp(8),0,dp(8),0);contentDescription=title}
        val master=switch("App blocking",prefs.getBoolean("focusBlocking",false));panel.addView(master)
        master.setOnCheckedChangeListener {_,on->prefs.edit().putBoolean("focusBlocking",on).apply()}
        val allSwitch=switch("Block all except browsers",all);panel.addView(allSwitch)
        val access=text("",12f,Color.rgb(161,181,165));panel.addView(access)
        val permission=button("Enable access") {startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))};panel.addView(permission)
        refreshFocusPermissions={
            val component=ComponentName(this,FocusAccessibilityService::class.java).flattenToString()
            val granted=(Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").split(':').any {it.equals(component,true)}
            access.text=if(granted)"Accessibility access is enabled · Pauses and breaks allow apps" else "Accessibility access is needed to block apps. No screen contents or typing are read."
            permission.text=if(granted)"Manage access" else "Enable access"
        };refreshFocusPermissions?.invoke()
        val search=EditText(this).apply {hint="Find an app";contentDescription="Find an app";setSingleLine();textSize=14f;setTextColor(Color.WHITE);setHintTextColor(Color.rgb(135,157,140))};panel.addView(search)
        val list=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        val scroll=ScrollView(this).apply {addView(list);isFillViewport=false};panel.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun drawApps() {
            list.removeAllViews()
            for(info in packages) {
                val id=info.activityInfo.packageName;val name=info.loadLabel(packageManager).toString()
                if(id in home || id in keyboards)continue
                val browserAllowed=all && id in browsers
                val row=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(9),dp(8),dp(9));background=card();tag=name.lowercase()}
                row.addView(ImageView(this).apply {setImageDrawable(info.loadIcon(packageManager));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(32),dp(32)).apply {setMargins(0,0,dp(12),0)})
                val labels=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};labels.addView(text(name,14f));labels.addView(text(if(browserAllowed)"Browser · always allowed" else "On blocks this app",10f,Color.rgb(161,181,165)))
                row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
                val toggle=Switch(this).apply {isChecked=!browserAllowed && id !in excluded && (all || id in blocked);isEnabled=!browserAllowed;contentDescription="$name blocking";minWidth=dp(48);minHeight=dp(48)}
                toggle.setOnCheckedChangeListener {_,on->
                    if(all) {if(on)excluded.remove(id) else excluded.add(id)} else {if(on){blocked.add(id);excluded.remove(id)}else blocked.remove(id)}
                    prefs.edit().putStringSet("blockedApps",blocked.toSet()).putStringSet("excludedApps",excluded.toSet()).apply()
                }
                row.addView(toggle);row.setOnClickListener {if(toggle.isEnabled)toggle.isChecked=!toggle.isChecked}
                list.addView(row,LinearLayout.LayoutParams(-1,-2).apply {setMargins(0,0,0,dp(6))})
                row.visibility=if(name.contains(search.text.toString(),true))View.VISIBLE else View.GONE
            }
        }
        allSwitch.setOnCheckedChangeListener {_,on->all=on;prefs.edit().putBoolean("focusBlockAll",on).apply();drawApps()}
        search.addTextChangedListener(object:android.text.TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){for(i in 0 until list.childCount){val row=list.getChildAt(i);row.visibility=if(row.tag.toString().contains(s.toString(),true))View.VISIBLE else View.GONE}}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        drawApps();panel.addView(text("On = blocked · Off = allowed. Browsers stay available in all-app mode.",10f,Color.rgb(161,181,165)))
        panel.addView(button("Close") {dialog.dismiss()})
        panel.isFocusableInTouchMode=true;panel.requestFocus();dialog.setContentView(panel);dialog.setOnDismissListener {refreshFocusPermissions=null;render()};dialog.show();dialog.window?.setBackgroundDrawable(card(bg));dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.window?.setLayout(resources.displayMetrics.widthPixels-dp(24),(resources.displayMetrics.heightPixels*.88).toInt())
    }
    private fun notifications() { if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),5) }
    private fun text(value:String,size:Float=15f,color:Int=Color.rgb(238,240,220))=TextView(this).apply { text=value; textSize=size; setTextColor(color); includeFontPadding=false; setLineSpacing(dp(3).toFloat(),1f); setPadding(0,dp(5),0,dp(5)) }
    private fun section(title:String,hint:String?=null) {
        root.addView(text(title,18f).apply {typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)},LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(20);bottomMargin=dp(5)})
        if(hint!=null) root.addView(text(hint,13f,Color.rgb(161,181,165)))
    }
    private fun settingsRow(title:String,subtitle:String,action:()->Unit) {
        val row=focusCard().apply {gravity=Gravity.CENTER_VERTICAL;orientation=LinearLayout.HORIZONTAL;foreground=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(35,173,218,173)),null,card());isClickable=true;isFocusable=true;contentDescription=title;setOnClickListener {safe(action)}}
        val labels=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};labels.addView(text(title,15f));labels.addView(text(subtitle,12f,Color.rgb(161,181,165)));row.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(ImageView(this).apply {setImageDrawable(UiIcon("right",cyan));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(20),dp(20)))
        root.addView(row,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(8)})
    }
    private fun card(color:Int=surface)=GradientDrawable().apply { setColor(color); cornerRadius=dp(16).toFloat() }
    private fun button(value:String,description:String=value,action:()->Unit)=Button(this).apply {
        text=value;contentDescription=description;textSize=14f;isAllCaps=false;setTextColor(cyan);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);minWidth=0;minimumWidth=0;minHeight=dp(48);minimumHeight=dp(48);setPadding(dp(14),dp(8),dp(14),dp(8));stateListAnimator=null
        val symbol=mapOf("▶" to "play","Ⅱ" to "pause","✓" to "check","×" to "close","↓" to "save","✎" to "edit","‹" to "left","›" to "right","↺" to "reset")[value]
        if(symbol!=null) {text="";setPadding(dp(13),dp(13),dp(13),dp(13));setCompoundDrawablesRelative(UiIcon(symbol,cyan).apply {setBounds(0,0,dp(22),dp(22))},null,null,null)}
        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(40,173,218,173)),card(),null)
        setOnClickListener {safe(action)}
    }
    private fun line(parent:LinearLayout=root, build:(LinearLayout)->Unit) { val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }; parent.addView(row); build(row) }
    private fun buildShell() {
        val outer=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(bg)}
        outer.setOnApplyWindowInsetsListener {view,insets->if(Build.VERSION.SDK_INT>=30){val bars=insets.getInsets(WindowInsets.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom)};insets}
        val header=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL;setPadding(dp(24),dp(16),dp(24),dp(12))}
        val titles=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};pageSubtitle=text("FOCANOPY",10f,cyan).apply {letterSpacing=.18f};pageTitle=text("Make room to grow",25f).apply {typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)};titles.addView(pageSubtitle);titles.addView(pageTitle);header.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(ImageButton(this).apply {setImageResource(R.drawable.ic_account);contentDescription="Account / Sync";background=card();setPadding(dp(13),dp(13),dp(13),dp(13));setOnClickListener {safe {accountDialog()}}},LinearLayout.LayoutParams(dp(48),dp(48)))
        outer.addView(header)
        val scroll=ScrollView(this).apply {isFillViewport=true;clipToPadding=false;isVerticalScrollBarEnabled=false;setPadding(dp(20),0,dp(20),dp(20))};scroller=scroll
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};scroll.addView(root);outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val nav=LinearLayout(this).apply {setPadding(dp(12),dp(8),dp(12),dp(6));setBackgroundColor(surface)}
        for((name,icon) in listOf("Timer" to "timer","Journal" to "calendar","Settings" to "settings")) {
            val item=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;contentDescription=name;isFocusable=true;setPadding(dp(8),dp(4),dp(8),dp(4));setOnClickListener {if(tab!=name){tab=name;render()}}}
            item.addView(ImageView(this).apply {setImageDrawable(UiIcon(icon,cyan));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(24),dp(24)))
            item.addView(text(name,11f,cyan).apply {gravity=Gravity.CENTER;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO});navItems[name]=item;nav.addView(item,LinearLayout.LayoutParams(0,dp(64),1f))
        }
        outer.addView(nav);setContentView(outer);shellReady=true
    }
    private fun render() {
        if(!shellReady)buildShell()
        if(renderedTab.isNotEmpty())scrollPositions[renderedTab]=scroller?.scrollY ?: 0
        val targetScroll=scrollPositions[tab] ?: 0;renderedTab=tab
        phase=Repository.engine.phase;pomoStage=Repository.pomodoro.stage;timer=null;focusTime=null;cat=null;groveStatus=null;growthHint=null;growthBar=null
        root.removeAllViews();pageTitle.text=when(tab){"Journal"->"Your focus, in bloom";"Settings"->"Make it yours";else->"Make room to grow"}
        for((name,item) in navItems){item.isSelected=name==tab;item.background=if(name==tab)card(Color.rgb(47,70,53)) else null}
        when(tab){"Timer"->timerPage();"Journal"->journal();else->settings()}
        Repository.error?.let {root.addView(text(it,13f,Color.rgb(255,180,155)))}
        scroller?.post {scroller?.scrollTo(0,targetScroll)}
    }
    private fun updateGrowth() {
        if(Repository.pomodoroEnabled) {
            val remaining=Repository.pomodoro.remaining(Repository.engine.elapsed())
            growthHint?.text=if(Repository.pomodoro.stage=="dead") "Cancelled round · Start a fresh tree" else if(Repository.pomodoro.stage=="focus") "${focusText(remaining)} until your flower blooms" else if(Repository.pomodoro.stage=="break") "Watering stopped · Enjoy your break" else "Ready when you are · Start the next flower"
            growthBar?.progress=if(Repository.pomodoro.stage=="dead")0 else if(Repository.pomodoro.stage!="focus")1000 else ((Pomodoro.FOCUS-remaining).coerceIn(0,Pomodoro.FOCUS)*1000/Pomodoro.FOCUS).toInt()
        } else {
            val elapsed=Repository.growthElapsed();growthHint?.text=Forest.nextTreeText(elapsed);growthBar?.progress=((elapsed%Forest.HOUR)*1000/Forest.HOUR).toInt()
        }
    }
    private fun timerText():String {
        val ms=Repository.displayTime()
        return if(Repository.pomodoroEnabled) String.format(java.util.Locale.ROOT,"%02d:%02d",(ms/60000).coerceAtLeast(0),(ms/1000%60).coerceAtLeast(0)) else clockText(ms)
    }
    private fun focusCard():LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(18))
        background=GradientDrawable().apply { setColor(surface);cornerRadius=dp(24).toFloat();setStroke(dp(1),Color.rgb(55,76,61)) }
    }
    private fun adaptFocusCard(hero:LinearLayout) {
        if(resources.configuration.screenHeightDp>=700)return
        cat?.let {tree->hero.removeView(tree);hero.addView(tree,LinearLayout.LayoutParams(-1,dp(96)))}
        val controls=(0 until hero.childCount).map {hero.getChildAt(it)}.filterIsInstance<LinearLayout>().firstOrNull {row->(0 until row.childCount).any {row.getChildAt(it) is Button}}
        if(controls!=null){hero.removeView(controls);val clock=focusTime ?: timer;hero.addView(controls,(hero.indexOfChild(clock)+1).coerceAtMost(hero.childCount))}
    }
    private fun pomodoroPage() {
        val muted=Color.rgb(161,181,165);val cream=Color.rgb(238,240,220);val coral=Color.rgb(207,158,137)
        val dead=pomoStage=="dead";val rest=pomoStage=="break";val ready=pomoStage=="ready"
        val hero=focusCard();root.addView(hero,LinearLayout.LayoutParams(-1,-2).apply {setMargins(0,dp(12),0,dp(14))})
        val badge=when {dead->"ROUND CANCELLED";rest->"REST & RESET";ready->"READY TO GROW";phase=="paused"->"WATERING PAUSED";else->"FLOWER FOCUS"}
        hero.addView(text(badge,10f,if(dead)coral else cyan).apply {letterSpacing=.16f;gravity=Gravity.CENTER})
        hero.addView(text(when {dead->"A fresh start awaits";rest->"A little room to breathe";ready->"Your next flower is waiting";else->"Grow one little flower"},22f,cream).apply {typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);gravity=Gravity.CENTER})
        cat=TreeView(this);hero.addView(cat,LinearLayout.LayoutParams(-1,dp(142)));cat?.refresh()
        timer=text(timerText(),52f,cream).apply {gravity=Gravity.CENTER;setSingleLine();setAutoSizeTextTypeUniformWithConfiguration(28,52,1,android.util.TypedValue.COMPLEX_UNIT_SP);typeface=Typeface.create("monospace",Typeface.NORMAL);setPadding(0,0,0,0);tag="Pomodoro countdown"};hero.addView(timer)
        focusTime=text("Focus time  ·  "+clockText(Repository.engine.elapsed()),13f,cyan).apply {gravity=Gravity.CENTER;typeface=Typeface.create("monospace",Typeface.NORMAL);tag="Focus time"};hero.addView(focusTime)
        hero.addView(text(when {dead->"This tree died. Begin again when you’re ready.";rest->"Flower saved. Stretch, breathe, move a little.";ready->"Start when you’re ready. No rush.";phase=="paused"->"Your flower is resting. Resume to keep growing.";phase=="running"->"A little water. A little growth.";else->"25 minutes, one thing at a time."},12f,muted).apply {gravity=Gravity.CENTER})
        growthBar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {max=1000;progressTintList=android.content.res.ColorStateList.valueOf(if(dead)coral else cyan);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(Color.rgb(46,65,51));contentDescription="Round progress"}
        hero.addView(growthBar,LinearLayout.LayoutParams(-1,dp(5)).apply {setMargins(dp(12),dp(16),dp(12),dp(6))})
        growthHint=text("",11f,muted).apply {gravity=Gravity.CENTER};hero.addView(growthHint)
        line(hero) {row ->
            row.gravity=Gravity.CENTER;row.setPadding(0,dp(12),0,0)
            if(rest || ready) {
                row.addView(button("Start next round",if(rest)"Skip break & start next focus" else "Start next 25-minute focus") { notifications();Repository.start();service("SYNC");render() }.apply {setTextColor(cyan);background=card(Color.rgb(63,101,69))},LinearLayout.LayoutParams(-2,dp(48)))
            } else {
                row.addView(button(if(phase=="running")"Ⅱ" else "▶",if(phase=="running")"Pause" else "Start or resume") {notifications();if(Repository.engine.phase=="running")Repository.pause() else Repository.start();service("SYNC");render()}.apply {textSize=21f;setTextColor(cyan);background=card(Color.rgb(63,101,69))},LinearLayout.LayoutParams(dp(54),dp(50)))
                if(phase!="idle" && !dead)row.addView(button("×","Cancel round · tree will die") {cancelPomodoroDialog()}.apply {textSize=24f;setTextColor(coral)},LinearLayout.LayoutParams(dp(50),dp(50)).apply {setMargins(dp(10),0,0,0)})
            }
        }
        adaptFocusCard(hero)
        if(phase=="idle") {
            val rhythm=focusCard();root.addView(rhythm,LinearLayout.LayoutParams(-1,-2).apply {setMargins(0,0,0,dp(12))})
            rhythm.addView(text("MAKE SPACE FOR A BREAK",10f,muted).apply {letterSpacing=.12f})
            line(rhythm) {row ->for(minutes in listOf(5,10)) row.addView(button("$minutes min") {Repository.selectMode(true,minutes);render()}.apply {background=card(if(Repository.pomodoro.breakMinutes==minutes)Color.rgb(49,78,56) else surface)},LinearLayout.LayoutParams(0,dp(48),1f).apply {setMargins(dp(3),dp(5),dp(3),0)})}
        }
        line {row ->
            for((label,value) in listOf("FOCUS TODAY" to focusText(total(recordsFor(LocalDate.now()))),"BLOOMS THIS SESSION" to (Repository.engine.elapsed()/Pomodoro.FOCUS).toString())) {
                val metric=focusCard();metric.setPadding(dp(15),dp(12),dp(15),dp(12));metric.addView(text(label,9f,muted).apply {letterSpacing=.08f});metric.addView(text(value,23f,cream))
                row.addView(metric,LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(3),0,dp(3),dp(12))})
            }
        }
        val on=Repository.prefs.getBoolean("focusBlocking",false)
        val component=ComponentName(this,FocusAccessibilityService::class.java).flattenToString()
        val allowed=(Settings.Secure.getString(contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").split(':').any {it.equals(component,true)}
        root.addView(button("App blocking · "+(if(!on)"Off" else if(!allowed)"Permission needed" else "On")) {focusApps()}.apply {gravity=Gravity.START or Gravity.CENTER_VERTICAL;setPadding(dp(18),0,dp(18),0)})
        root.addView(text("25-minute rounds save automatically · Next round is always your choice",11f,muted).apply {gravity=Gravity.CENTER;setPadding(dp(8),dp(12),dp(8),dp(4))})
        if(rest || ready)root.addView(button("End Pomodoro","End Pomodoro · completed rounds saved") {cancelPomodoroDialog()}.apply {setTextColor(muted)})
        root.addView(button("Fast-forward tree preview") {treePreviewDialog()})
        updateGrowth()
    }
    private fun timerPage() {
        root.addView(android.widget.Switch(this).apply {
            text="Pomodoro effect";contentDescription="Pomodoro effect · 25-minute flower";textSize=14f;setPadding(dp(14),dp(3),dp(10),dp(3));background=card();minHeight=dp(50);isChecked=Repository.pomodoroEnabled;isEnabled=phase=="idle" || pomoStage=="dead"
            setTextColor(cyan);setOnCheckedChangeListener { _,on -> Repository.selectMode(on,Repository.pomodoro.breakMinutes);render() }
        })
        if(Repository.pomodoroEnabled) {pomodoroPage();return}
        val hero=focusCard();root.addView(hero,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(14);bottomMargin=dp(16)})
        hero.addView(text(when(phase){"running"->"GROWING NOW";"paused"->"TAKE A BREATH";"finished"->"SESSION COMPLETE";else->"YOUR DAILY GROVE"},10f,cyan).apply {letterSpacing=.16f;gravity=Gravity.CENTER})
        cat=TreeView(this);hero.addView(cat,LinearLayout.LayoutParams(-1,dp(172)));cat?.refresh()
        timer=text(clockText(Repository.displayTime()),46f).apply {gravity=Gravity.CENTER;setSingleLine();setAutoSizeTextTypeUniformWithConfiguration(24,46,1,android.util.TypedValue.COMPLEX_UNIT_SP);typeface=Typeface.create("sans-serif-light",Typeface.NORMAL);fontFeatureSettings="tnum";setPadding(0,dp(8),0,dp(8))};hero.addView(timer)
        hero.addView(text(when(phase){"running"->"A little water. A little growth.";"paused"->"Your tree is resting. Resume when you’re ready.";"finished"->"Lovely work. Give this session a name.";else->"One small start. Something beautiful grows."},13f,Color.rgb(161,181,165)).apply {gravity=Gravity.CENTER})
        line(hero){row->row.gravity=Gravity.CENTER;row.setPadding(0,dp(14),0,dp(14))
            row.addView(button(if(phase=="running")"Ⅱ" else if(phase=="finished")"↓" else "▶",if(phase=="running")"Pause" else if(phase=="finished")"Save session" else "Start or resume") {
                if(phase=="finished")saveDialog() else {notifications();if(Repository.engine.phase=="running")Repository.pause() else Repository.start();service("SYNC");render()}
            }.apply {background=card(Color.rgb(63,101,69))},LinearLayout.LayoutParams(dp(56),dp(56)))
            if(phase!="finished")row.addView(button("✓","Finish session") {Repository.finish();service("SYNC");render();saveDialog()}.apply {isEnabled=phase!="idle";alpha=if(isEnabled)1f else .3f},LinearLayout.LayoutParams(dp(56),dp(56)).apply {leftMargin=dp(12)})
        }
        adaptFocusCard(hero)
        growthBar=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {max=1000;progressTintList=android.content.res.ColorStateList.valueOf(cyan);progressBackgroundTintList=android.content.res.ColorStateList.valueOf(bg);contentDescription="Progress to next tree"};hero.addView(growthBar,LinearLayout.LayoutParams(-1,dp(4)))
        growthHint=text("",12f,Color.rgb(161,181,165)).apply {gravity=Gravity.CENTER};hero.addView(growthHint);updateGrowth()
        if(phase=="finished")line {row->row.gravity=Gravity.CENTER;row.setPadding(0,0,0,dp(12));row.addView(button("▶","Resume timer") {Repository.resumeFinished();service("SYNC");render()},LinearLayout.LayoutParams(dp(56),dp(48)).apply {rightMargin=dp(8)});row.addView(button("↺","Reset timer") {AlertDialog.Builder(this).setTitle("Reset this unsaved timer?").setMessage("Saved history stays in your journal.").setNegativeButton("Keep timer",null).setPositiveButton("Reset") {_,_->safe {Repository.discard();service("SYNC");render()}}.show()},LinearLayout.LayoutParams(dp(56),dp(48)))}
        line {row->for((label,value) in listOf("FOCUS TODAY" to focusText(total(recordsFor(LocalDate.now()))),"TREES GROWN" to Forest.trees(total(recordsFor(LocalDate.now()))).toString())) {
            val metric=focusCard();metric.addView(text(label,10f,Color.rgb(161,181,165)).apply {letterSpacing=.08f});metric.addView(text(value,25f,cyan));row.addView(metric,LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(3),0,dp(3),dp(12))})
        }}
        section("Create a little space")
        settingsRow("App blocking · "+if(Repository.prefs.getBoolean("focusBlocking",false))"On" else "Off","Choose what stays out while you focus") {focusApps()}
        settingsRow("Floating tree options","Keep your growing companion nearby") {tab="Settings";render()}
        if(phase=="running" || phase=="paused")root.addView(button("Cancel session…") {AlertDialog.Builder(this).setTitle("Discard this session?").setMessage("This session won’t be saved.").setNegativeButton("Keep session",null).setPositiveButton("Discard") {_,_->safe {Repository.discard();service("SYNC");render()}}.show()})
    }
    private fun treePreviewDialog() {
        val dialog=Dialog(this)
        val content=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(22),dp(22),dp(22),dp(16));background=card(bg)}
        content.addView(text("The life of a little tree",23f,cyan))
        content.addView(text("Fast-forward preview · Your timer and history stay untouched",12f,Color.rgb(161,181,165)))
        val tree=TreeView(this);content.addView(tree,LinearLayout.LayoutParams(-1,dp(200)))
        val stage=text("",17f,cyan).apply {gravity=Gravity.CENTER};content.addView(stage)
        val hint=text("",12f,Color.rgb(161,181,165)).apply {gravity=Gravity.CENTER};content.addView(hint)
        val slider=SeekBar(this).apply {max=27000;contentDescription="Tree lifecycle preview"};content.addView(slider)
        val controls=LinearLayout(this).apply {gravity=Gravity.CENTER};content.addView(controls)
        var position=0f;var playing=true;var last=SystemClock.elapsedRealtime()
        val play=button("Ⅱ","Pause preview") {};val replay=button("↺","Replay preview") {};controls.addView(play,LinearLayout.LayoutParams(dp(54),dp(48)));controls.addView(replay,LinearLayout.LayoutParams(dp(54),dp(48)))
        fun draw() {
            val frame=TreePreviewFrame.at(position)
            tree.previewMillis=frame.elapsed;tree.previewFlower=true;tree.previewDeath=frame.death;tree.previewMotionTime=position.toDouble()
            tree.previewPhase=if(frame.dead)"dead" else if(playing && (position<10 || position>=12 && position<20))"running" else "resting";tree.refresh()
            stage.text=frame.title;hint.text=if(frame.dead)"Water stops. Petals fall. The stem droops and dries." else "25 minutes of focus shown in 10 seconds."
            slider.progress=(position*1000).toInt();play.text="";play.setCompoundDrawablesRelative(UiIcon(if(playing)"pause" else "play",cyan).apply {setBounds(0,0,dp(22),dp(22))},null,null,null);play.contentDescription=if(playing)"Pause preview" else "Play preview"
        }
        val previewHandler=Handler(Looper.getMainLooper())
        lateinit var tick:Runnable
        tick=Runnable {if(dialog.isShowing && playing){val now=SystemClock.elapsedRealtime();position=(position+(now-last)/1000f).coerceAtMost(27f);last=now;if(position>=27)playing=false;draw();if(playing)previewHandler.postDelayed(tick,50)}}
        play.setOnClickListener {if(position>=27)position=0f;playing=!playing;last=SystemClock.elapsedRealtime();previewHandler.removeCallbacks(tick);draw();if(playing)previewHandler.post(tick)}
        replay.setOnClickListener {position=0f;playing=true;last=SystemClock.elapsedRealtime();previewHandler.removeCallbacks(tick);draw();previewHandler.post(tick)}
        slider.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar:SeekBar?,value:Int,user:Boolean){if(user){position=value/1000f;last=SystemClock.elapsedRealtime();draw()}}
            override fun onStartTrackingTouch(bar:SeekBar?){}
            override fun onStopTrackingTouch(bar:SeekBar?){}
        })
        content.addView(button("Close") {dialog.dismiss()});dialog.setContentView(ScrollView(this).apply {addView(content);isFillViewport=true});dialog.setOnDismissListener {previewHandler.removeCallbacksAndMessages(null)}
        dialog.show();dialog.window?.setBackgroundDrawable(card(bg));dialog.window?.setLayout(resources.displayMetrics.widthPixels-dp(24),(resources.displayMetrics.heightPixels*.88).toInt());draw();previewHandler.post(tick)
    }
    private fun cancelPomodoroDialog() {
        AlertDialog.Builder(this).setTitle("Cancel Pomodoro?")
            .setMessage("An unfinished tree will die and this partial round will not count. Completed 25-minute rounds stay saved.")
            .setNegativeButton("Keep growing",null).setPositiveButton("Cancel round") { _,_->safe { Repository.cancelPomodoro();service("SYNC");render() } }.show()
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
        dialog.setOnDismissListener { naming=false; if(!isFinishing && !isDestroyed && Repository.engine.phase=="finished") render() }
        dialog.setOnShowListener { dialog.window?.decorView?.apply {background=card(surface);clipToOutline=true};dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            safe { val day=dayOf(Repository.started); Repository.save(name.text.toString()); service("SYNC"); dialog.dismiss(); tab="Journal"; selected=day; month=YearMonth.from(day); scope="Day"; render() }
        } }; dialog.show()
    }
    private fun dayOf(timestamp:Long)=Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    private fun recordsFor(day:LocalDate)=Repository.sessions.filter { dayOf(it.getLong("start"))==day }
    private fun total(records:List<JSONObject>)=records.sumOf { it.getLong("duration") }
    private fun journal() {
        section("A little progress, every day")
        val today=LocalDate.now()
        root.addView(text("Today ${focusText(total(recordsFor(today)))}   ·   Yesterday ${focusText(total(recordsFor(today.minusDays(1))))}",14f,cyan))
        line { row -> row.addView(button("‹","Previous month") { month=month.minusMonths(1); render() }); row.addView(text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),18f).apply {gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,-2,1f)); row.addView(button("›","Next month") { month=month.plusMonths(1); render() }) }
        val monthRecords=Repository.sessions.filter { YearMonth.from(dayOf(it.getLong("start")))==month }
        root.addView(text("${focusText(total(monthRecords))} tracked this month",13f,Color.LTGRAY))
        val grid=GridLayout(this).apply { columnCount=7 }
        for(day in listOf("Mo","Tu","We","Th","Fr","Sa","Su")) grid.addView(text(day,12f,Color.LTGRAY).apply { gravity=Gravity.CENTER },GridLayout.LayoutParams().apply { width=0; height=dp(30); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f) })
        val start=month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value-1).toLong())
        for(index in 0 until Forest.calendarCells(month.atDay(1).dayOfWeek.value-1,month.lengthOfMonth())) {
            val day=start.plusDays(index.toLong()); val sum=total(recordsFor(day))
            val cell=TextView(this).apply {
                text=if(sum>0) "${day.dayOfMonth}\n${focusText(sum)}\n${Forest.cell(sum)}" else "${day.dayOfMonth}\n\n"; textSize=11f; gravity=Gravity.CENTER; includeFontPadding=false
                setTextColor(if(YearMonth.from(day)!=month) Color.GRAY else if(Forest.garden(sum)) Color.rgb(238,212,151) else Color.WHITE)
                background=GradientDrawable().apply {cornerRadius=dp(10).toFloat();setColor(if(day==selected) Color.rgb(57,91,61) else if(sum>0) Color.rgb(35,60,43) else surface);if(day==selected)setStroke(dp(1),cyan)}
                contentDescription=day.toString()+", "+focusText(sum)+" tracked, "+Forest.text(sum)+(if(Forest.garden(sum))", garden" else "")
                setOnClickListener { selected=day; month=YearMonth.from(day); scope="Day"; render() }
            }
            grid.addView(cell,GridLayout.LayoutParams().apply { width=0; height=dp(maxOf(64,(56*resources.configuration.fontScale).toInt())); columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); setMargins(dp(2),dp(2),dp(2),dp(2)) })
        }; root.addView(grid)
        line { row -> for(name in listOf("Day","Week","Month")) row.addView(button(name) { scope=name; render() }.apply { isSelected=scope==name; background=card(if(scope==name) cyan else surface); setTextColor(if(scope==name) bg else cyan) },LinearLayout.LayoutParams(0,dp(48),1f).apply { setMargins(dp(2),dp(12),dp(2),dp(8)) }) }
        val weekStart=selected.minusDays((selected.dayOfWeek.value-1).toLong())
        val records=Repository.sessions.filter { val d=dayOf(it.getLong("start")); when(scope) { "Week"-> !d.isBefore(weekStart)&&d.isBefore(weekStart.plusDays(7)); "Month"->YearMonth.from(d)==YearMonth.from(selected); else->d==selected } }
        root.addView(text("${selected.format(DateTimeFormatter.ofPattern("EEE, MMM d"))} · $scope",16f))
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
                row.addView(button("✎","Rename session") { val input=EditText(this).apply { contentDescription="Rename task"; setText(record.getString("name")); setSingleLine() }; AlertDialog.Builder(this).setTitle("Rename session").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_-> safe { Repository.rename(record.getString("id"),input.text.toString()); render() } }.show() },LinearLayout.LayoutParams(dp(48),dp(48)))
                row.addView(button("×","Delete session") { AlertDialog.Builder(this).setTitle("Delete session?").setMessage(record.getString("name")).setNegativeButton("Cancel",null).setPositiveButton("Delete") { _,_-> safe { deleted=Repository.delete(record.getString("id")); render() } }.show() },LinearLayout.LayoutParams(dp(48),dp(48)))
            }
            root.addView(box,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
        }
        root.addView(text("Totals exclude pauses. Sessions belong to their local start date.",11f,Color.LTGRAY))
    }
    @Deprecated("Native Activity back callback")
    override fun onBackPressed() {if(tab!="Timer"){tab="Timer";render()}else super.onBackPressed()}
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
        section("Your account")
        settingsRow("Account / Sync",if(Cloud.owner!=null) "${Cloud.username} · ${Cloud.status}" else "Saved on this device · Sign in to sync") {accountDialog()}
        section("Your floating grove","Keep a small tree above other apps, or use notification controls.")
        val showing=Repository.prefs.getBoolean("showPet",false) && Settings.canDrawOverlays(this)
        settingsRow(if(showing)"Hide floating tree" else if(Settings.canDrawOverlays(this))"Show floating tree" else "Enable floating tree…",if(showing)"Visible above other apps" else "Optional · Requires display-over-apps access") {
            if(showing){Repository.prefs.edit().putBoolean("showPet",false).apply();service("HIDE");render()}
            else {notifications();if(Settings.canDrawOverlays(this)){Repository.prefs.edit().putBoolean("showPet",true).apply();service("SHOW");render()}else{requestOverlay=true;startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}}
        }
        val appearance=focusCard();root.addView(appearance)
        val opacity=text("Tree opacity ${(Repository.prefs.getFloat("opacity",1f)*100).toInt()}%",14f);appearance.addView(opacity)
        appearance.addView(SeekBar(this).apply {max=70;progress=(Repository.prefs.getFloat("opacity",1f)*100).toInt()-30;contentDescription="Floating tree opacity";minimumHeight=dp(48)
            setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar:SeekBar?,progress:Int,fromUser:Boolean){if(fromUser){Repository.prefs.edit().putFloat("opacity",(progress+30)/100f).apply();opacity.text="Tree opacity ${progress+30}%"}}
                override fun onStartTrackingTouch(bar:SeekBar?){};override fun onStopTrackingTouch(bar:SeekBar?){}
            })
        })
        appearance.addView(Switch(this).apply {text="Animate tree and water";textSize=14f;setTextColor(cyan);minHeight=dp(56);isChecked=Repository.prefs.getBoolean("animate",true);setOnCheckedChangeListener {_,value->Repository.prefs.edit().putBoolean("animate",value).apply()}})
        section("Focus & permissions")
        settingsRow("Apps & permissions","Choose blocked apps and manage access") {focusApps()}
        settingsRow("Notification settings",if(Build.VERSION.SDK_INT<33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)"Enabled · Background controls ready" else "Not enabled · Tap to manage") {startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))}
        section("Explore & manage")
        settingsRow("Fast-forward tree preview","Watch a little tree’s complete lifecycle") {treePreviewDialog()}
        settingsRow("Export session history","Save your journal as a JSON file") {exportHistory()}
        root.addView(text("Focanopy · 1.12.0",12f,cyan).apply {gravity=Gravity.CENTER;setPadding(0,dp(24),0,dp(8))})
        root.addView(text("Made for small, steady progress. Your sessions stay on this device unless you choose to sync.",12f,Color.rgb(161,181,165)).apply {gravity=Gravity.CENTER})
    }
}
