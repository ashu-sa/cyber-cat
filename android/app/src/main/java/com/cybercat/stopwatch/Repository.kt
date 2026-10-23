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
    var pomodoroEnabled=false
    var pomodoro=Pomodoro({SystemClock.elapsedRealtime()},{System.currentTimeMillis()})
    var started = 0L; var ended = 0L
    private var growthKey:String?=null
    private var growthBase=0L
    var sessions = mutableListOf<JSONObject>()
        set(value) { field=value; growthKey=null }
    var error: String? = null
    var focusNotice:String?=null
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
            pomodoroEnabled=prefs.getBoolean("pomodoro",false)
            pomodoro.restore(prefs.getString("pomoStage","focus")!!,prefs.getLong("pomoTarget",Pomodoro.FOCUS),prefs.getInt("breakMinutes",5),prefs.getLong("pomoDeadline",0))
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
        advancePomodoro()
    }
    fun checkpoint() {
        check(prefs.edit().putString("phase", engine.phase).putLong("elapsed", engine.elapsed())
            .putLong("started", started).putLong("ended", ended).putString("sessionId", sessionId)
            .putInt("boot", boot()).putLong("checkpointMono", SystemClock.elapsedRealtime())
            .putLong("checkpointWall", System.currentTimeMillis()).putBoolean("pomodoro",pomodoroEnabled)
            .putString("pomoStage",pomodoro.stage).putLong("pomoTarget",pomodoro.target).putInt("breakMinutes",pomodoro.breakMinutes).putLong("pomoDeadline",pomodoro.deadline).commit()) { "Unable to preserve timer state" }
    }
    fun growthElapsed():Long {
        val time=if(engine.phase!="idle")started else System.currentTimeMillis()
        fun day(ms:Long)=java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val date=day(time); val active=if(engine.phase!="idle")sessionId else ""
        val key="$date:$active"
        if(growthKey!=key) { growthBase=sessions.filter { day(it.getLong("start"))==date && it.getString("id")!=active }.sumOf { it.getLong("duration") }; growthKey=key }
        return growthBase+engine.elapsed()
    }
    fun start() { if(pomodoroEnabled && pomodoro.stage=="dead")discard(); if((Cloud.busy.get() && Cloud.owner==null) || Cloud.googlePending) return; if (engine.phase == "idle") { started = System.currentTimeMillis(); ended = 0; sessionId = UUID.randomUUID().toString() }; if(pomodoroEnabled)pomodoro.start(engine.elapsed());engine.start(); checkpoint() }
    fun resumeFinished() { if(engine.phase=="finished") { if(pomodoroEnabled) { if(engine.elapsed()>=pomodoro.target)pomodoro.stage="ready";pomodoro.start(engine.elapsed()) };ended=0;engine.resumeFinished();checkpoint() } }
    fun pause() { advancePomodoro();engine.pause(); checkpoint() }
    fun finish() { if(pomodoroEnabled)return;advancePomodoro(); if (engine.phase == "running" || engine.phase == "paused") ended = System.currentTimeMillis(); engine.finish(); checkpoint() }
    fun selectMode(enabled:Boolean,minutes:Int=5) {
        if(pomodoro.stage=="dead")discard()
        check(engine.phase=="idle") { "Finish or reset the current session before changing modes." }
        pomodoroEnabled=enabled;pomodoro=Pomodoro({SystemClock.elapsedRealtime()},{System.currentTimeMillis()}).apply { breakMinutes=if(minutes==10)10 else 5 };checkpoint()
    }
    fun displayTime():Long = if(pomodoroEnabled && engine.phase!="finished")pomodoro.remaining(engine.elapsed()) else engine.elapsed()
    fun advancePomodoro():String? {
        if(!pomodoroEnabled || engine.phase=="idle" || engine.phase=="finished")return null
        if(engine.phase=="running" && pomodoro.stage=="focus" && engine.elapsed()>=pomodoro.target)creditPomodoro()
        val (cap,notice)=pomodoro.advance(engine.elapsed(),engine.phase=="running")
        if(cap!=null) { engine.accumulated=cap;engine.phase="paused" }
        if(notice!=null) { focusNotice=notice;checkpoint() }
        return notice
    }
    private fun creditPomodoro() {
        if(sessions.any { it.getString("id")==sessionId && it.getLong("duration")>=pomodoro.target })return
        checkpoint()
        val device=prefs.getString("deviceId",null) ?: UUID.randomUUID().toString().also { check(prefs.edit().putString("deviceId",it).commit()) }
        val name=sessions.firstOrNull { it.getString("id")==sessionId }?.getString("name") ?: "Pomodoro focus"
        val item=JSONObject().put("id",sessionId).put("name",name).put("start",started).put("end",System.currentTimeMillis()-(engine.elapsed()-pomodoro.target).coerceAtLeast(0)).put("duration",pomodoro.target).put("platform","android").put("device_id",device).put("device_name","Phone")
        replace(listOf(item)+sessions.filter {it.getString("id")!=sessionId})
    }
    fun cancelPomodoro() {
        if(!pomodoroEnabled || engine.phase=="idle")return
        advancePomodoro()
        if(pomodoro.stage=="focus") { engine.pause();pomodoro.stage="dead";pomodoro.deadline=0;focusNotice="This tree died. Start a new 25-minute round when you’re ready.";checkpoint() }
        else if(pomodoro.stage!="dead")discard()
    }
    private fun resetPomodoro() { focusNotice=null;pomodoro=Pomodoro({SystemClock.elapsedRealtime()},{System.currentTimeMillis()}).apply { breakMinutes=prefs.getInt("breakMinutes",5) } }
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
        engine.reset();resetPomodoro(); prefs.edit().remove("draftName").apply(); checkpoint()
    }
    fun loadGuest() {
        sessions=if(file.baseFile.exists()) { val a=JSONArray(file.openRead().bufferedReader().use { it.readText() }); (0 until a.length()).map { a.getJSONObject(it) }.toMutableList() } else mutableListOf()
    }
    fun discard() { engine.reset();resetPomodoro(); prefs.edit().remove("draftName").apply(); checkpoint() }
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
