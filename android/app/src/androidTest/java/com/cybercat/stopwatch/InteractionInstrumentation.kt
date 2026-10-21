package com.cybercat.stopwatch

import android.app.*
import android.content.Intent
import android.os.*
import android.graphics.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo

/** Real touch injection into the isolated QA overlay; no personal app data is used. */
class InteractionInstrumentation:Instrumentation() {
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);start() }
    private fun node(root:AccessibilityNodeInfo?,text:String):AccessibilityNodeInfo? {
        if(root==null)return null
        if(root.text?.toString()==text || root.contentDescription?.toString()==text)return root
        for(i in 0 until root.childCount)node(root.getChild(i),text)?.let { return it }
        return null
    }
    private fun overlay():AccessibilityNodeInfo {
        repeat(40) {
            for(window in uiAutomation.windows) {
                val bounds=Rect();window.getBoundsInScreen(bounds)
                if(bounds.width() in 1 until targetContext.resources.displayMetrics.widthPixels/2 && window.root?.packageName==targetContext.packageName)return window.root
            }
            SystemClock.sleep(100)
        };error("Floating overlay not accessible")
    }
    private fun gesture(item:AccessibilityNodeInfo,dx:Int,dy:Int) {
        val bounds=Rect();item.getBoundsInScreen(bounds);val x=bounds.exactCenterX();val y=bounds.exactCenterY();val start=SystemClock.uptimeMillis()
        fun event(action:Int,x:Float,y:Float) { val event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x,y,0);event.source=InputDevice.SOURCE_TOUCHSCREEN;check(uiAutomation.injectInputEvent(event,true));event.recycle() }
        event(MotionEvent.ACTION_DOWN,x,y)
        for(i in 1..10) { SystemClock.sleep(16);event(MotionEvent.ACTION_MOVE,x+dx*i/10f,y+dy*i/10f) }
        event(MotionEvent.ACTION_UP,x+dx,y+dy);SystemClock.sleep(250)
    }
    override fun onStart() {
        val result=Bundle()
        try {
            check(targetContext.packageName.endsWith(".qa"));Repository.init(targetContext)
            val info=uiAutomation.serviceInfo;info.flags=info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;uiAutomation.serviceInfo=info
            android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("appops set ${targetContext.packageName} SYSTEM_ALERT_WINDOW allow")).use { it.readBytes() }
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            runOnMainSync {
                Repository.discard();Repository.prefs.edit().putBoolean("showPet",true).putInt("petX",30).putInt("petY",180).commit()
                targetContext.startForegroundService(Intent(targetContext,TimerService::class.java).setAction("SHOW"))
            }
            SystemClock.sleep(700)
            for(label in listOf("00:00:00","Start or resume","Finish session")) {
                val old=Repository.prefs.getInt("petX",0)
                gesture(node(overlay(),label) ?: error("Missing $label"),70,40)
                check(Repository.prefs.getInt("petX",0)>old+20) { "Did not move from $label" }
                check(Repository.engine.phase=="idle") { "Dragging $label activated a control" }
            }
            gesture(node(overlay(),"Start or resume")!!,0,0);SystemClock.sleep(400);check(Repository.engine.phase=="running")
            gesture(node(overlay(),"Pause")!!,0,0);SystemClock.sleep(300);check(Repository.engine.phase=="paused")
            runOnMainSync {
                fun image(phase:String):Bitmap { val tree=TreeView(targetContext).apply { this.phase=phase;previewMillis=2701000;layout(0,0,330,174) };return Bitmap.createBitmap(330,174,Bitmap.Config.ARGB_8888).also { tree.draw(Canvas(it)) } }
                val dry=image("paused");val live=image("running")
                check(!dry.sameAs(live))
                check((246 until 270).all { x -> (10 until 90).all { y -> Color.alpha(dry.getPixel(x,y))==0 } }) { "Water remains visible while paused" }
                check(live.sameAs(image("running"))) { "Resume did not restore healthy appearance" }
                for((name,bitmap) in listOf("android-tree-paused.png" to dry,"android-tree-running.png" to live)) targetContext.openFileOutput(name,0).use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                Repository.discard();Repository.prefs.edit().putBoolean("showPet",false).commit();targetContext.stopService(Intent(targetContext,TimerService::class.java));activity.finish()
            }
            result.putString("stream","PASS: timer/play/finish overlay drag persists position without activating controls; normal taps start/pause; paused dry artwork contains no water and resume restores healthy tree.\n");finish(Activity.RESULT_OK,result)
        } catch(e:Throwable) { result.putString("stream","FAIL: ${e.stackTraceToString()}\n");finish(Activity.RESULT_CANCELED,result) }
    }
}
