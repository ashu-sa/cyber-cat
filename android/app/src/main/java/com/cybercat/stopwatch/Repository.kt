package com.cybercat.stopwatch

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

@android.annotation.SuppressLint("StaticFieldLeak") // Only the process-lifetime application context is retained.
object Repository {
    lateinit var context: Context
    val engine = TimerEngine { SystemClock.elapsedRealtime() }
    var started = 0L; var ended = 0L
    private var growthKey:String?=null
    private var growthBase=0L
    var sessions = mutableListOf<JSONObject>()
        set(value) { field=value; growthKey=null }
    var error: String? = null
    private var initialized = false
    private var sessionId = UUID.randomUUID().toString()
    val prefs get() = context.getSharedPreferences("cybercat", Context.MODE_PRIVATE)
    private val file get() = AtomicFile(File(context.filesDir, "sessions.json"))
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    fun init(c: Context) {
        if (initialized) return
        context = c.applicationContext; initialized = true
        try {
            if (file.baseFile.exists() || File(context.filesDir, "sessions.json.bak").exists()) {
                val array = JSONArray(file.openRead().bufferedReader().use { it.readText() })
                sessions = (0 until array.length()).map { array.getJSONObject(it) }.toMutableList()
            }
            engine.phase = prefs.getString("phase", "idle")!!
            engine.accumulated = prefs.getLong("elapsed", 0)
            started = prefs.getLong("started", 0); ended = prefs.getLong("ended", 0)
            sessionId = prefs.getString("sessionId", null) ?: UUID.randomUUID().toString()
            if (engine.phase == "running") {
                val sameBoot = boot() >= 0 && prefs.getInt("boot", -2) == boot()
                val passed = if (sameBoot) SystemClock.elapsedRealtime() - prefs.getLong("checkpointMono", SystemClock.elapsedRealtime())
                    else System.currentTimeMillis() - prefs.getLong("checkpointWall", System.currentTimeMillis())
                engine.accumulated += passed.coerceAtLeast(0); engine.anchor = SystemClock.elapsedRealtime()
            }
        } catch (e: Exception) { error = "Could not read saved data: ${e.localizedMessage}" }
        Cloud.init(context); if(Cloud.owner!=null) sessions=Cloud.visible()
    }
    fun checkpoint() {
        check(prefs.edit().putString("phase", engine.phase).putLong("elapsed", engine.elapsed())
            .putLong("started", started).putLong("ended", ended).putString("sessionId", sessionId)
            .putInt("boot", boot()).putLong("checkpointMono", SystemClock.elapsedRealtime())
            .putLong("checkpointWall", System.currentTimeMillis()).commit()) { "Unable to preserve timer state" }
    }
    fun growthElapsed():Long {
        val time=if(engine.phase!="idle")started else System.currentTimeMillis()
        fun day(ms:Long)=java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val date=day(time); val active=if(engine.phase!="idle")sessionId else ""
        val key="$date:$active"
        if(growthKey!=key) { growthBase=sessions.filter { day(it.getLong("start"))==date && it.getString("id")!=active }.sumOf { it.getLong("duration") }; growthKey=key }
        return growthBase+engine.elapsed()
    }
    fun start() { if(Cloud.busy.get() && Cloud.owner==null) return; if (engine.phase == "idle") { started = System.currentTimeMillis(); ended = 0; sessionId = UUID.randomUUID().toString() }; engine.start(); checkpoint() }
    fun resumeFinished() { if(engine.phase=="finished") { ended=0;engine.resumeFinished();checkpoint() } }
    fun pause() { engine.pause(); checkpoint() }
    fun finish() { if (engine.phase == "running" || engine.phase == "paused") ended = System.currentTimeMillis(); engine.finish(); checkpoint() }
    fun replace(records: List<JSONObject>) {
        check(error == null) { error ?: "Storage unavailable" }
        if(Cloud.owner!=null) { Cloud.replace(records); sessions=Cloud.visible(); Cloud.run({ Cloud.sync() }); return }
        val stream = file.startWrite()
        try { stream.write(JSONArray(records).toString(2).toByteArray()); file.finishWrite(stream); sessions = records.toMutableList() }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }
    fun save(name: String) {
        if (engine.phase != "finished") return
        val deviceId = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("deviceId", it).commit()) { "Unable to save device identity" }
        }
        val item = JSONObject().put("id", sessionId).put("name", name.trim().ifEmpty { "Focus session" })
            .put("start", started).put("end", ended).put("duration", engine.accumulated)
            .put("platform", "android").put("device_id", deviceId).put("device_name", "Phone")
        replace(listOf(item) + sessions.filter { it.getString("id") != sessionId })
        engine.reset(); prefs.edit().remove("draftName").apply(); checkpoint()
    }
    fun loadGuest() {
        sessions=if(file.baseFile.exists()) { val a=JSONArray(file.openRead().bufferedReader().use { it.readText() }); (0 until a.length()).map { a.getJSONObject(it) }.toMutableList() } else mutableListOf()
    }
    fun discard() { engine.reset(); prefs.edit().remove("draftName").apply(); checkpoint() }
    fun deviceLabel(record: JSONObject): String = record.optString("device_name").ifBlank {
        when(record.optString("platform")) { "macos" -> "Mac"; "android" -> "Phone"; "windows" -> "Windows"; "linux" -> "Linux"; else -> "This device" }
    }
    fun rename(id: String, name: String) {
        require(name.isNotBlank()) { "Enter a task name" }
        replace(sessions.map { JSONObject(it.toString()).apply { if (getString("id") == id) put("name", name.trim()) } })
    }
    fun delete(id: String): JSONObject { val record = sessions.first { it.getString("id") == id }; replace(sessions.filter { it.getString("id") != id }); return record }
    fun restore(record: JSONObject) { if (sessions.none { it.getString("id") == record.getString("id") }) replace((sessions + record).sortedByDescending { it.getLong("start") }) }
}
