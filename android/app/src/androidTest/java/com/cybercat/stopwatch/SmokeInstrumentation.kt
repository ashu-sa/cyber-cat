package com.cybercat.stopwatch

import android.app.*
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import org.json.JSONArray

/** End-to-end MVP checks run only in the separate .qa app. Never touches the installed personal app. */
class SmokeInstrumentation:Instrumentation() {
    private var recovery=false
    override fun onCreate(arguments:Bundle?) { recovery=arguments?.getString("mode")=="recovery";super.onCreate(arguments);start() }
    private fun find(view:View,name:String):View? {
        if(view.contentDescription==name || (view is TextView && view.text.toString()==name)) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i),name)?.let { return it }
        return null
    }
    private fun node(root:AccessibilityNodeInfo?,name:String):AccessibilityNodeInfo? {
        if(root==null) return null
        if(root.text?.toString()?.equals(name,true)==true || root.contentDescription?.toString()?.equals(name,true)==true) return root
        for(i in 0 until root.childCount) node(root.getChild(i),name)?.let { return it }
        return null
    }
    private fun tap(name:String) { waitForIdleSync(); var item=node(uiAutomation.rootInActiveWindow,name); var attempts=0; while(item==null && attempts++<30) { SystemClock.sleep(100); item=node(uiAutomation.rootInActiveWindow,name) }; val target=item ?: error("Missing accessible control: $name; root="+dump(uiAutomation.rootInActiveWindow));if(!target.isVisibleToUser) { target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id); SystemClock.sleep(200) }; check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(150);waitForIdleSync() }
    private fun dump(n:AccessibilityNodeInfo?):String { if(n==null) return "none";return "[${n.packageName} ${n.text} ${n.contentDescription}]"+(0 until n.childCount).joinToString("") { dump(n.getChild(it)) } }
    private fun type(name:String,value:String) { val item=node(uiAutomation.rootInActiveWindow,name) ?: error("Missing input: $name");check(item.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value) }));waitForIdleSync() }
    private fun notice(action:String?=null):Notification {
        val manager=targetContext.getSystemService(NotificationManager::class.java)
        repeat(60) { manager.activeNotifications.firstOrNull { it.id==7 && (action==null || it.notification.actions.any { item -> item.title.toString()==action }) }?.let { entry -> return entry.notification }; SystemClock.sleep(100) }
        error("Missing foreground notification action=$action, phase=${Repository.engine.phase}")
    }
    private fun awaitPhase(phase:String) { repeat(80) { if(Repository.engine.phase==phase) return; SystemClock.sleep(100) }; error("Expected $phase, found ${Repository.engine.phase}") }
    override fun onStart() {
        val result=Bundle()
        try {
            check(targetContext.packageName.endsWith(".qa")) { "Use -PmvpQa and a disposable emulator" }
            Repository.init(targetContext)
            var activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            if(recovery) {
                check(Repository.engine.phase=="paused");check(Repository.engine.elapsed()>=1000)
                check(Repository.sessions.single().getString("name")=="MVP renamed")
                val elapsed=Repository.engine.elapsed();SystemClock.sleep(300);check(Repository.engine.elapsed()==elapsed)
                runOnMainSync { Repository.discard(); targetContext.stopService(Intent(targetContext,TimerService::class.java));activity.finish() }
                result.putString("stream","PASS: process restart restored paused timer, duration and saved history\n");finish(Activity.RESULT_OK,result);return
            }
            val navigation=find(activity.window.decorView,"Timer") ?: error("Missing navigation")
            runOnMainSync { check(Repository.engine.phase=="idle"); find(activity.window.decorView,"Start or resume")!!.performClick() }
            SystemClock.sleep(1300)
            val manager=targetContext.getSystemService(NotificationManager::class.java)
            check(Repository.engine.phase=="running")
            var notice=notice()
            check(notice.extras.getBoolean("android.showChronometer"));check(notice.extras.getString("android.title")!!.startsWith("Focanopy"))
            (notice.actions.firstOrNull { it.title.toString()=="Pause" } ?: error("Missing Pause action: "+notice.actions.map { it.title })).actionIntent.send();awaitPhase("paused")
            runOnMainSync {check(find(activity.window.decorView,"Timer")===navigation) {"Timer transition replaced the navigation shell"}}
            check(Repository.engine.phase=="paused");val paused=Repository.engine.elapsed();SystemClock.sleep(200);check(Repository.engine.elapsed()==paused)
            notice=notice("Resume")
            (notice.actions.firstOrNull { it.title.toString()=="Resume" } ?: error("Missing Resume action: "+notice.actions.map { it.title })).actionIntent.send();awaitPhase("running")
            notice=notice("Finish")
            (notice.actions.firstOrNull { it.title.toString()=="Finish" } ?: error("Missing Finish action: "+notice.actions.map { it.title })).actionIntent.send();awaitPhase("finished");SystemClock.sleep(400);waitForIdleSync()
            tap("Session category");tap("Work");check(Repository.prefs.getString("draftName","")=="Work")
            type("Session task name","Android MVP");tap("Close")
            runOnMainSync { check(find(activity.window.decorView,"Reset timer")!=null);find(activity.window.decorView,"Resume timer")!!.performClick() }
            awaitPhase("running")
            runOnMainSync { find(activity.window.decorView,"Finish session")!!.performClick() };waitForIdleSync();tap("Close")
            runOnMainSync { find(activity.window.decorView,"Save session")!!.performClick() };waitForIdleSync()
            check(node(uiAutomation.rootInActiveWindow,"Session task name")!!.text.toString()=="Android MVP")
            tap("Save");check(Repository.sessions.single().getString("name")=="Android MVP")
            tap("Rename session");type("Rename task","MVP renamed");tap("Save");check(Repository.sessions.single().getString("name")=="MVP renamed")
            tap("Delete session");tap("Delete");check(Repository.sessions.isEmpty());tap("Undo last deletion");check(Repository.sessions.size==1)
            tap("Month")
            val monitor=addMonitor(MainActivity::class.java.name,null,false)
            runOnMainSync { activity.recreate() }
            activity=waitForMonitorWithTimeout(monitor,5000) as? MainActivity ?: error("Activity did not recreate")
            removeMonitor(monitor);waitForIdleSync()
            runOnMainSync { check(find(activity.window.decorView,"Month")!!.isSelected) { "Calendar scope lost during recreation" } }
            val export=java.io.File(targetContext.filesDir,"export-test.json")
            runOnMainSync { MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }.invoke(activity,42,Activity.RESULT_OK,Intent().setData(android.net.Uri.fromFile(export))) }
            check(JSONArray(export.readText()).getJSONObject(0).getString("name")=="MVP renamed")
            runOnMainSync { find(activity.window.decorView,"Timer")!!.performClick();find(activity.window.decorView,"Start or resume")!!.performClick();find(activity.window.decorView,"Finish session")!!.performClick() }
            waitForIdleSync();tap("Close");tap("Reset timer");tap("Reset")
            check(Repository.engine.phase=="idle" && Repository.sessions.single().getString("name")=="MVP renamed")
            fun shell(command:String)=android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
            shell("appops set ${targetContext.packageName} SYSTEM_ALERT_WINDOW allow")
            runOnMainSync { find(activity.window.decorView,"Settings")!!.performClick();find(activity.window.decorView,"Show floating tree")!!.performClick() }
            SystemClock.sleep(500);check(shell("dumpsys window windows").contains("ty=APPLICATION_OVERLAY"))
            runOnMainSync { find(activity.window.decorView,"Hide floating tree")!!.performClick() }
            SystemClock.sleep(200);shell("appops set ${targetContext.packageName} SYSTEM_ALERT_WINDOW deny")
            runOnMainSync { Repository.start();Repository.prefs.edit().putBoolean("showPet",true).commit();targetContext.startForegroundService(Intent(targetContext,TimerService::class.java).setAction("SHOW")) }
            SystemClock.sleep(1100)
            check(Repository.engine.phase=="running");check(manager.activeNotifications.any { it.id==7 })
            runOnMainSync { Repository.pause();Repository.prefs.edit().putBoolean("showPet",false).commit();Repository.checkpoint();activity.finish() }
            result.putString("stream","PASS: branded native Android MVP; notification start/pause/resume/finish; category/custom save and preserved naming draft; rename/delete/undo; calendar recreation; JSON export; granted overlay; denied-permission fallback. Paused checkpoint ready for process-restart test.\n")
            finish(Activity.RESULT_OK,result)
        } catch(e:Throwable) { result.putString("stream","FAIL: ${e.stackTraceToString()}\n");finish(Activity.RESULT_CANCELED,result) }
    }
}
