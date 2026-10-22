package com.cybercat.stopwatch

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

/** Isolated .qa package: native rendering and reward assertions without cloud credentials. */
class ForestInstrumentation:Instrumentation() {
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments); start() }
    private fun find(view:View,name:String):View? {
        if(view.contentDescription==name || (view is TextView && view.text.toString()==name)) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i),name)?.let { return it }
        return null
    }
    override fun onStart() {
        val result=Bundle()
        try {
            check(targetContext.packageName.endsWith(".qa"))
            Repository.init(targetContext)
            runOnMainSync {
                Repository.discard()
                val today=LocalDate.now()
                val demo=listOf(Triple(0,1.0,"Work"),Triple(1,8.0,"Upskill"),Triple(3,1.5,"Interview Prep"),Triple(5,3.0,"Job Apply")).map { (days,hours,name) ->
                    val start=today.minusDays(days.toLong()).atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val duration=(hours*3600000).toLong()
                    JSONObject().put("id",UUID.randomUUID().toString()).put("name",name).put("start",start).put("end",start+duration).put("duration",duration).put("device_name","Phone").put("platform","android")
                }
                Repository.replace(demo)
                check(Repository.growthElapsed()==Forest.HOUR)
                for(minutes in listOf(0,30,60,480)) {
                    val tree=TreeView(targetContext).apply { previewMillis=minutes*60000L; layout(0,0,660,348) }
                    val image=Bitmap.createBitmap(660,348,Bitmap.Config.ARGB_8888)
                    tree.draw(Canvas(image))
                    targetContext.openFileOutput("android-tree-$minutes.png",0).use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
                }
            }
            runOnMainSync { Repository.start(); Repository.engine.accumulated=1800000; Repository.checkpoint() }
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            android.os.SystemClock.sleep(1500);waitForIdleSync()
            fun screenshot(name:String) { android.os.SystemClock.sleep(900); waitForIdleSync(); uiAutomation.takeScreenshot()!!.let { image -> targetContext.openFileOutput(name,0).use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle() } }
            screenshot("android-timer.png")
            runOnMainSync { find(activity.window.decorView,"Finish session")!!.performClick() }
            waitForIdleSync();screenshot("android-save.png")
            uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Close").first { it.text?.toString()?.equals("Close",true)==true }.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            waitForIdleSync();screenshot("android-finished.png")
            runOnMainSync { Repository.discard(); targetContext.startService(Intent(targetContext,TimerService::class.java).setAction("SYNC")); find(activity.window.decorView,"Journal")!!.performClick() }
            waitForIdleSync();screenshot("android-journal.png")
            runOnMainSync { find(activity.window.decorView,"Settings")!!.performClick() }
            waitForIdleSync();screenshot("android-settings.png")
            runOnMainSync { find(activity.window.decorView,"Account / Sync")!!.performClick() }
            waitForIdleSync();screenshot("android-account.png")
            runOnMainSync { activity.finish() }
            result.putString("stream","PASS: isolated native tree milestones, combined history growth, calendar and timer rendering\n");finish(0,result)
        } catch(e:Throwable) { result.putString("stream","FAIL: ${e.stackTraceToString()}\n");finish(1,result) }
    }
}
