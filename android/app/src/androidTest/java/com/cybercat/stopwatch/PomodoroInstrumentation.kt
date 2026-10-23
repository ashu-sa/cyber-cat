package com.cybercat.stopwatch

import android.app.Instrumentation
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class PomodoroInstrumentation:Instrumentation() {
    override fun onCreate(arguments:Bundle?) {super.onCreate(arguments);start()}
    private fun find(view:View,name:String):View? {
        if(view.contentDescription==name || (view is TextView && view.text.toString()==name))return view
        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i),name)?.let{return it}
        return null
    }
    override fun onStart() {
        val result=Bundle()
        try {
            check(targetContext.packageName.endsWith(".qa"));Repository.init(targetContext)
            runOnMainSync {Repository.discard();Repository.selectMode(false,5);Repository.prefs.edit().putBoolean("showPet",false).putBoolean("focusBlocking",false).apply()}
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            runOnMainSync {activity.setShowWhenLocked(true);activity.setTurnScreenOn(true)}
            fun screenshot(name:String) {SystemClock.sleep(1200);waitForIdleSync();uiAutomation.takeScreenshot()!!.let {b->targetContext.openFileOutput(name,0).use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}}
            check(!Repository.pomodoroEnabled)
            runOnMainSync {
                check(find(activity.window.decorView,"Free Stopwatch")==null)
                check(find(activity.window.decorView,"Pomodoro effect · 25-minute flower")!=null)
                find(activity.window.decorView,"App blocking · Off")!!.performClick()
            }
            screenshot("android-app-blocking.png")
            val root=uiAutomation.rootInActiveWindow
            check(root.findAccessibilityNodeInfosByText("Block all").isNotEmpty())
            check(root.findAccessibilityNodeInfosByText("Block selected apps").isNotEmpty())
            val close=root.findAccessibilityNodeInfosByText("Close").first()
            check(close.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            waitForIdleSync()
            runOnMainSync { (find(activity.window.decorView,"Pomodoro effect · 25-minute flower") as android.widget.Switch).isChecked=true }
            waitForIdleSync();check(Repository.pomodoroEnabled)
            runOnMainSync {find(activity.window.decorView,"Start or resume")!!.performClick()}
            waitForIdleSync();check(Repository.engine.phase=="running")
            runOnMainSync {Repository.engine.accumulated=750000;Repository.engine.anchor=SystemClock.elapsedRealtime();Repository.checkpoint()}
            screenshot("android-pomodoro-focus.png")
            runOnMainSync {Repository.engine.accumulated=1501000;Repository.engine.anchor=SystemClock.elapsedRealtime();Repository.advancePomodoro()}
            screenshot("android-pomodoro-break.png")
            check(Repository.engine.phase=="paused" && Repository.pomodoro.stage=="break")
            check(Repository.engine.elapsed()==1500000L)
            val notifications=targetContext.getSystemService(NotificationManager::class.java).activeNotifications
            check(notifications.any {it.id==8}) {"Focus-end reminder was not posted"}
            runOnMainSync {Repository.pomodoro.restore("break",1500000,5,System.currentTimeMillis()-1000);Repository.advancePomodoro()}
            screenshot("android-pomodoro-ready.png")
            check(Repository.pomodoro.stage=="ready" && Repository.engine.phase=="paused" && Repository.engine.elapsed()==1500000L)
            runOnMainSync {find(activity.window.decorView,"Start next 25-minute focus")!!.performClick()}
            check(Repository.engine.phase=="running" && Repository.pomodoro.target==3000000L)
            runOnMainSync {Repository.pause();Repository.finish();Repository.save("Pomodoro QA")}
            check(Repository.sessions.first().getLong("duration") in 1500000..1503000)
            runOnMainSync {Repository.selectMode(false);Repository.start();Repository.engine.accumulated=3600000;Repository.engine.anchor=SystemClock.elapsedRealtime();Repository.advancePomodoro()}
            check(Repository.engine.phase=="running" && Repository.engine.elapsed()>=3600000)
            runOnMainSync {Repository.discard();targetContext.stopService(Intent(targetContext,TimerService::class.java));activity.finish()}
            result.putString("stream","PASS: native Pomodoro focus/bloom/break/ready/manual-next, reminders, focus-only save and unchanged Free Stopwatch\n");finish(0,result)
        }catch(e:Throwable){result.putString("stream","FAIL: ${e.stackTraceToString()}\n");finish(1,result)}
    }
}
