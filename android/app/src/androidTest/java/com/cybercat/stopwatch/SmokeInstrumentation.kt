package com.cybercat.stopwatch

import android.app.*
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/** Runs only against the freshly installed test APK; never part of production app. */
class SmokeInstrumentation: Instrumentation() {
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments); start() }
    private fun find(view:View, predicate:(View)->Boolean):View? {
        if(predicate(view)) return view
        if(view is ViewGroup) for(index in 0 until view.childCount) find(view.getChildAt(index),predicate)?.let { return it }
        return null
    }
    override fun onStart() {
        val result=Bundle()
        try {
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            runOnMainSync {
                check(Repository.engine.phase=="idle")
                val play=find(activity.window.decorView) { it.contentDescription=="Start or resume" } ?: error("Missing play control")
                play.performClick()
            }
            SystemClock.sleep(1400)
            runOnMainSync {
                check(Repository.engine.phase=="running")
                check(Repository.engine.elapsed()>=1000)
                val notices=targetContext.getSystemService(NotificationManager::class.java).activeNotifications
                check(notices.any { it.id==7 && it.notification.extras.getBoolean("android.showChronometer") }) { "No live chronometer notification" }
                Repository.pause()
            }
            val paused=Repository.engine.elapsed(); SystemClock.sleep(200)
            runOnMainSync {
                check(Repository.engine.elapsed()==paused)
                Repository.finish(); Repository.save("Upskill")
                check(Repository.sessions.first().getString("name")=="Upskill")
                val journal=find(activity.window.decorView) { it is TextView && it.text.toString()=="Journal" } ?: error("Missing journal tab")
                journal.performClick()
                check(find(activity.window.decorView) { it is TextView && it.text.toString()=="Upskill" }!=null) { "Saved session missing from calendar detail" }
            }
            waitForIdleSync()
            runOnMainSync {
                val view=activity.window.decorView
                val image=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(image))
                java.io.File(targetContext.filesDir,"journal-preview.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            }
            fun shell(command:String):String = android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
            shell("appops set com.cybercat.stopwatch SYSTEM_ALERT_WINDOW allow")
            runOnMainSync {
                val settings=find(activity.window.decorView) { it is TextView && it.text.toString()=="Settings" } ?: error("Missing settings")
                settings.performClick()
                val show=find(activity.window.decorView) { it is TextView && it.text.toString()=="Show floating cat" } ?: error("Missing overlay control")
                show.performClick()
            }
            SystemClock.sleep(1000)
            check(shell("dumpsys window windows").contains("ty=APPLICATION_OVERLAY")) { "Overlay window was not created" }
            runOnMainSync {
                Repository.prefs.edit().putBoolean("showPet",false).commit()
                targetContext.startService(Intent(targetContext,TimerService::class.java).setAction("HIDE"))
            }
            SystemClock.sleep(300)
            shell("appops set com.cybercat.stopwatch SYSTEM_ALERT_WINDOW deny")
            runOnMainSync {
                Repository.start()
                Repository.prefs.edit().putBoolean("showPet",true).commit()
                targetContext.startForegroundService(Intent(targetContext,TimerService::class.java).setAction("SHOW"))
            }
            SystemClock.sleep(700)
            runOnMainSync {
                check(Repository.engine.phase=="running")
                check(targetContext.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id==7 }) { "Notification fallback missing" }
                Repository.discard(); Repository.prefs.edit().putBoolean("showPet",false).commit()
                targetContext.stopService(Intent(targetContext,TimerService::class.java)); activity.finish()
            }
            result.putString("stream","PASS: Android 15 activity, Start, foreground chronometer notification, pause accuracy, save category, calendar history, granted floating overlay, denied-permission notification fallback\n")
            finish(Activity.RESULT_OK,result)
        } catch(e:Throwable) { result.putString("stream","FAIL: ${e.stackTraceToString()}\n"); finish(Activity.RESULT_CANCELED,result) }
    }
}
