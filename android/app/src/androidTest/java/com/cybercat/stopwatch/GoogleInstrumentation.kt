package com.cybercat.stopwatch

import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.graphics.Bitmap

/** Uses only the isolated QA package, no Google account and no production history. */
class GoogleInstrumentation:Instrumentation() {
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
            Repository.init(targetContext); check(Cloud.owner==null); Repository.discard(); Cloud.cancelGoogle()
            val before=Repository.sessions.toString()
            // An unsolicited browser callback must never create an account or change local records.
            try { Cloud.completeGoogle("focanopy://auth/callback?state=forged&code=forged"); error("Accepted unsolicited callback") }
            catch(e:IllegalStateException) { check(e.message!!.contains("expired")) }
            check(Cloud.owner==null && Repository.sessions.toString()==before)
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            runOnMainSync { find(activity.window.decorView,"Settings")!!.performClick() }
            waitForIdleSync()
            runOnMainSync { find(activity.window.decorView,"Account / Sync")!!.performClick() }
            waitForIdleSync()
            val nodes=uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText("Continue with Google")
            check(nodes.isNotEmpty()) { "Google sign-in control missing" }
            uiAutomation.takeScreenshot()!!.let { image -> targetContext.openFileOutput("android-google-account.png",0).use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle() }
            check(Repository.engine.phase=="idle" && Cloud.owner==null && Repository.sessions.toString()==before)
            runOnMainSync { activity.finish() }
            result.putString("stream","PASS: Google account UI, unsolicited callback rejection, local history isolation\n");finish(0,result)
        } catch(e:Throwable) { result.putString("stream","FAIL: ${e.stackTraceToString()}\n");finish(1,result) }
    }
}
