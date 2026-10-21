package com.cybercat.stopwatch
import android.app.Instrumentation
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import java.util.UUID
/** Only installed in the separate .qa app; never uses a person's stopwatch data. */
class CloudInstrumentation:Instrumentation() {
    private lateinit var arguments:Bundle
    override fun onCreate(arguments:Bundle?) { this.arguments=arguments!!; super.onCreate(arguments); start() }
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
            startActivitySync(Intent(targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            result.putString("stream","PASS: Kotlin sign-in, shared Mac 40m + Phone 30m = 70m, Keystore credential/cache restore\n")
            finish(0,result)
        } catch(e:Throwable) { result.putString("stream","FAIL: ${e.message}\n"); finish(1,result) }
    }
}
