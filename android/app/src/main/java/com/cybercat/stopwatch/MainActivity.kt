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
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.browser.customtabs.CustomTabsIntent
import java.time.*
import java.time.format.DateTimeFormatter

class MainActivity: androidx.activity.ComponentActivity() {
    private val cyan = Color.rgb(173,218,173)
    private val bg = Color.rgb(17,27,23)
    private val surface = Color.rgb(30,44,36)
    private var refreshFocusPermissions:(()->Unit)?=null
    private var shellReady=false
    private var tab = "Timer"
    private var syncTicks=0
    private val composeRevision = mutableIntStateOf(0)
    private var composeProfileOpen by androidx.compose.runtime.mutableStateOf(false)
    private var composeSaveOpen by androidx.compose.runtime.mutableStateOf(false)
    private var phase = ""
    private var pomoStage=""
    private var requestOverlay = false
    private val handler=Handler(Looper.getMainLooper())
    private val tick=object:Runnable { override fun run() {
        Repository.advancePomodoro()
        composeRevision.intValue++
        if(tab=="Timer" && (phase!=Repository.engine.phase || pomoStage!=Repository.pomodoro.stage)) render()
        syncTicks++; if(syncTicks%30==0 && Cloud.owner!=null) Cloud.run({ Cloud.sync() })
        handler.postDelayed(this,1000)
    } }
    override fun onCreate(state:Bundle?) { super.onCreate(state); Repository.init(this); if(!BuildConfig.APP_BLOCKING_AVAILABLE)Repository.prefs.edit().putBoolean("focusBlocking",false).apply(); if(state!=null) { tab=state.getString("tab","Timer"); composeSaveOpen=state.getBoolean("saveOpen"); requestOverlay=state.getBoolean("requestOverlay") }; enableEdgeToEdge(statusBarStyle=SystemBarStyle.light(Color.rgb(245,246,239),Color.rgb(245,246,239)),navigationBarStyle=SystemBarStyle.light(Color.rgb(245,246,239),Color.rgb(245,246,239))); render(); handle(intent); if(Repository.engine.phase!="idle" || Repository.prefs.getBoolean("showPet",false)) safe { service("SYNC") } }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    private fun handle(intent:Intent?) {
        val callback=intent?.dataString
        if(intent?.action==Intent.ACTION_VIEW && callback!=null && GoogleOAuth.isCallback(callback)) {
            intent.data=null; intent.action=null
            Cloud.run({ Cloud.completeGoogle(callback) }) { error ->
                if(!isFinishing && !isDestroyed) {
                    render()
                    if(error==null) { Toast.makeText(this,"Signed in with Google",Toast.LENGTH_SHORT).show(); Cloud.run({ Cloud.sync() }) }
                    else { accountDialog() }
                }
            }
            return
        }
        if(intent?.action=="CANCEL_POMODORO") cancelPomodoroDialog()
        else if(intent?.action=="FINISH" && !Repository.pomodoroEnabled) safe { Repository.finish(); service("SYNC"); saveDialog() }
        else if(intent?.action=="SAVE" || Repository.engine.phase=="finished") saveDialog()
        if(intent?.action in setOf("CANCEL_POMODORO","FINISH","SAVE")) intent?.action=null
    }
    override fun onResume() {
        super.onResume(); Cloud.changed={ if(!isFinishing && !isDestroyed) render() }; if(Cloud.owner!=null) Cloud.run({ Cloud.sync() }); handler.removeCallbacks(tick); handler.post(tick)
        if(tab=="Settings" || tab=="Timer") render()
        refreshFocusPermissions?.invoke()
        if(requestOverlay && Settings.canDrawOverlays(this)) { Repository.prefs.edit().putBoolean("showPet",true).apply(); service("SHOW"); requestOverlay=false;render() }
    }
    override fun onSaveInstanceState(state:Bundle) {
        state.putString("tab",tab); state.putBoolean("saveOpen",composeSaveOpen); state.putBoolean("requestOverlay",requestOverlay)
        super.onSaveInstanceState(state)
    }
    override fun onPause() { Cloud.changed=null; handler.removeCallbacks(tick); try { Repository.checkpoint() } catch(e:Exception) { Repository.error=e.localizedMessage }; super.onPause() }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    private fun safe(action:()->Unit) { try { action() } catch(e:Exception) { AlertDialog.Builder(this).setTitle("Couldn’t complete action").setMessage(e.localizedMessage).setPositiveButton("OK",null).show() } }
    private fun service(action:String) { startForegroundService(Intent(this,TimerService::class.java).setAction(action)) }
    private fun accountDialog() {
        composeProfileOpen = true; render(); return
    }
    private fun focusApps() {
        if(!BuildConfig.APP_BLOCKING_AVAILABLE)return
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
            val component=ComponentName(packageName,"$packageName.FocusAccessibilityService").flattenToString()
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
    private fun card(color:Int=surface)=GradientDrawable().apply { setColor(color); cornerRadius=dp(16).toFloat() }
    private fun button(value:String,description:String=value,action:()->Unit)=Button(this).apply {
        text=value;contentDescription=description;textSize=14f;isAllCaps=false;setTextColor(cyan);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);minWidth=0;minimumWidth=0;minHeight=dp(48);minimumHeight=dp(48);setPadding(dp(14),dp(8),dp(14),dp(8));stateListAnimator=null
        val symbol=mapOf("▶" to "play","Ⅱ" to "pause","✓" to "check","×" to "close","↓" to "save","✎" to "edit","‹" to "left","›" to "right","↺" to "reset")[value]
        if(symbol!=null) {text="";setPadding(dp(13),dp(13),dp(13),dp(13));setCompoundDrawablesRelative(UiIcon(symbol,cyan).apply {setBounds(0,0,dp(22),dp(22))},null,null,null)}
        background=android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(40,173,218,173)),card(),null)
        setOnClickListener {safe(action)}
    }
    private fun render() {
        phase=Repository.engine.phase; pomoStage=Repository.pomodoro.stage
        if(!shellReady) {
            shellReady=true
            setContent {
                FocanopyScreen(composeRevision.intValue, tab, { tab=it; render() }, composeProfileOpen, {composeProfileOpen=it}, composeSaveOpen, {composeSaveOpen=it}, ::composeAction)
            }
        }
        composeRevision.intValue++
    }
    private fun composeAction(action:String) { safe {
        when(action) {
            "primary" -> { notifications(); if(Repository.engine.phase=="running") Repository.pause() else if(Repository.engine.phase=="finished") Repository.resumeFinished() else Repository.start(); service("SYNC") }
            "finish" -> { Repository.finish(); service("SYNC") }
            "saved" -> service("SYNC")
            "cancel" -> cancelPomodoroDialog()
            "reset" -> { Repository.discard(); service("SYNC") }
            "google" -> beginGoogleSignIn()
            "sync" -> if(Cloud.owner!=null) Cloud.run({Cloud.sync()}) else beginGoogleSignIn()
            "signout" -> if(Repository.engine.phase=="idle" && !Cloud.busy.get()) {Cloud.signOut();Repository.loadGuest()}
            "cancelGoogle" -> Cloud.cancelGoogle()
            "keepLocal" -> Cloud.run({Cloud.resolve(true);Cloud.sync()})
            "keepCloud" -> Cloud.run({Cloud.resolve(false);Cloud.sync()})
            "apps" -> focusApps()
            "preview" -> treePreviewDialog()
            "export" -> exportHistory()
            "notifications" -> startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
            "overlay" -> {
                val showing=Repository.prefs.getBoolean("showPet",false) && Settings.canDrawOverlays(this)
                if(showing){Repository.prefs.edit().putBoolean("showPet",false).apply();service("HIDE")}
                else {notifications();if(Settings.canDrawOverlays(this)){Repository.prefs.edit().putBoolean("showPet",true).apply();service("SHOW")}else{requestOverlay=true;startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))}}
            }
        }
        render()
    } }
    private fun beginGoogleSignIn() {
        if(Repository.engine.phase!="idle") {Toast.makeText(this,"Finish your timer before connecting an account",Toast.LENGTH_LONG).show();return}
        if(Cloud.owner!=null || Cloud.busy.get() || Cloud.googlePending)return
        var url=""
        Cloud.run({url=Cloud.beginGoogle(false)}) { error ->
            if(error==null && !isFinishing && !isDestroyed) {
                try { CustomTabsIntent.Builder().setShowTitle(true).setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .setDefaultColorSchemeParams(androidx.browser.customtabs.CustomTabColorSchemeParams.Builder().setToolbarColor(bg).build())
                    .build().launchUrl(this,Uri.parse(url)) }
                catch(_:ActivityNotFoundException) {Cloud.cancelGoogle();Toast.makeText(this,"Install a browser to continue with Google",Toast.LENGTH_LONG).show()}
            }
            render()
        }
        render()
    }
    private fun focusCard():LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(18),dp(20),dp(18))
        background=GradientDrawable().apply { setColor(surface);cornerRadius=dp(24).toFloat();setStroke(dp(1),Color.rgb(55,76,61)) }
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
    private fun saveDialog() { composeSaveOpen=true; render() }
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
}
