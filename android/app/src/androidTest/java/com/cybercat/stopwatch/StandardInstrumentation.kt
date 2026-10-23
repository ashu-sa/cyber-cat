package com.cybercat.stopwatch
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.*
import android.widget.TextView

/** Validate the actual permission-reduced APK, never a personal installation. */
class StandardInstrumentation:Instrumentation() {
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    private fun find(view:View,label:String):View? {
        if(view.contentDescription==label || view is TextView && view.text.toString()==label)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i),label)?.let{return it}
        return null
    }
    override fun onStart(){val result=Bundle();try {
        check(targetContext.packageName.endsWith(".qa"));check(!BuildConfig.APP_BLOCKING_AVAILABLE)
        val info=targetContext.packageManager.getPackageInfo(targetContext.packageName,PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS)
        check(info.services.orEmpty().none {it.permission=="android.permission.BIND_ACCESSIBILITY_SERVICE" || it.name.endsWith("FocusAccessibilityService")})
        check(info.requestedPermissions.orEmpty().none {it in setOf("android.permission.READ_SMS","android.permission.RECEIVE_SMS","android.permission.READ_CONTACTS","android.permission.READ_CALL_LOG","android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")})
        try {Class.forName("com.cybercat.stopwatch.FocusAccessibilityService");error("Accessibility code is present in standard build")}catch(expected:ClassNotFoundException){}
        Repository.init(targetContext);runOnMainSync {Repository.discard();Repository.replace(emptyList());Repository.selectMode(false)}
        val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        runOnMainSync {check(find(activity.window.decorView,"App blocking · Off")==null);find(activity.window.decorView,"Start or resume")!!.performClick()}
        SystemClock.sleep(1100);check(Repository.engine.phase=="running")
        runOnMainSync {find(activity.window.decorView,"Pause")!!.performClick()};val elapsed=Repository.engine.elapsed();SystemClock.sleep(200);check(Repository.engine.elapsed()==elapsed)
        runOnMainSync {find(activity.window.decorView,"Start or resume")!!.performClick();check(Repository.engine.phase=="running");Repository.finish();Repository.save("Standard build focus");Repository.selectMode(true);Repository.start();Repository.engine.accumulated=Pomodoro.FOCUS;Repository.engine.anchor=SystemClock.elapsedRealtime();Repository.advancePomodoro();check(Repository.pomodoro.stage in setOf("break","ready"));check(Repository.sessions.size==2);Repository.discard();activity.finish()}
        result.putString("stream","PASS: standard APK has no Accessibility declaration or implementation; timer start/pause/resume/save and Pomodoro automatic save work.\n");finish(Activity.RESULT_OK,result)
    }catch(error:Throwable){result.putString("stream","FAIL: ${error.stackTraceToString()}\n");finish(Activity.RESULT_CANCELED,result)}}
}
