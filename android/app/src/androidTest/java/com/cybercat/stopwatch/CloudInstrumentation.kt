package com.cybercat.stopwatch
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject
import java.util.UUID
/** Only installed in the separate .qa app; never uses a person's stopwatch data. */
class CloudInstrumentation:Instrumentation() {
    private lateinit var arguments:Bundle
    override fun onCreate(arguments:Bundle?) { this.arguments=arguments!!; super.onCreate(arguments); start() }
    private fun find(view:View, name:String):View? {
        if(view is TextView && view.text.toString()==name) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i),name)?.let { return it }
        return null
    }
    override fun onStart() {
        val result=Bundle()
        try {
            Repository.init(targetContext)
            Cloud.authenticate(arguments.getString("username")!!,arguments.getString("pin")!!,false,emptyList())
            Cloud.sync(); check(Cloud.status=="All devices up to date") { Cloud.status }
            check(Cloud.visible().any { it.getString("device_name")=="Mac" && it.getLong("duration")==2400000L }) { "Missing Mac 40m" }
            val id=Cloud.visible().firstOrNull { it.getString("name")=="Phone 30m" }?.getString("id") ?: UUID.randomUUID().toString()
            val phone=JSONObject().put("id",id).put("name","Phone 30m").put("start",1700000000000).put("end",1700001800000).put("duration",1800000)
                .put("platform","android").put("device_id",UUID.randomUUID().toString()).put("device_name","Phone")
            Cloud.replace(Cloud.visible().filter { it.getString("id")!=id }+phone); Cloud.sync(); check(Cloud.status=="All devices up to date") { Cloud.status }
            check(Cloud.visible().sumOf { it.getLong("duration") }==4200000L) { "Expected combined 70m" }
            val owner=Cloud.owner; Cloud.init(targetContext)
            check(Cloud.owner==owner) { "Keystore session was not restored" }
            check(Cloud.visible().any { it.getString("id")==id }) { "Cache not restored" }
            runOnMainSync { Repository.sessions=Cloud.visible() }
            // Render isolated demonstration history after the live account assertions.
            Cloud.signOut()
            val start=LocalDate.now().atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val demo=listOf("Mac" to 2400000L,"Phone" to 1800000L).map { (device,duration) ->
                JSONObject().put("id",UUID.randomUUID().toString()).put("name",if(device=="Mac")"Work" else "Interview Prep")
                    .put("start",start).put("end",start+duration).put("duration",duration).put("device_name",device)
                    .put("device_id",UUID.randomUUID().toString()).put("platform",if(device=="Mac")"macos" else "android")
            }
            runOnMainSync { Repository.replace(demo) }
            val activity=startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            waitForIdleSync()
            runOnMainSync { find(activity.window.decorView,"Journal")!!.performClick() }
            waitForIdleSync()
            uiAutomation.takeScreenshot()!!.let { image -> targetContext.openFileOutput("android-sync-journal.png",0).use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; image.recycle() }
            runOnMainSync { find(activity.window.decorView,"Settings")!!.performClick() }
            waitForIdleSync()
            runOnMainSync { find(activity.window.decorView,"Account / Sync")!!.performClick() }
            waitForIdleSync()
            uiAutomation.takeScreenshot()!!.let { image -> targetContext.openFileOutput("android-account.png",0).use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; image.recycle() }
            result.putString("stream","PASS: Kotlin sign-in, shared Mac 40m + Phone 30m = 70m, Keystore credential/cache restore\n")
            finish(0,result)
        } catch(e:Throwable) { result.putString("stream","FAIL: ${e.message}\n"); finish(1,result) }
    }
}
