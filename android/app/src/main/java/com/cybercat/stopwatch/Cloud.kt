package com.cybercat.stopwatch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Cloud {
    const val URL_ROOT="https://fwlengqpmrtmpbrhtxis.supabase.co"
    const val PUBLIC_KEY="sb_publishable_gsnb17q8TXRiHPu7W1vr4g_K7J8Np3H"
    private lateinit var context:Context
    private var login:JSONObject?=null
    private var records=mutableListOf<JSONObject>()
    private val lock=Any()
    val busy=AtomicBoolean(false)
    private val pool=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    var status="Local only"; private set
    var changed:(()->Unit)?=null
    val owner get()=login?.getJSONObject("user")?.getString("id")
    val username get():String {
        val user=login?.optJSONObject("user"); val meta=user?.optJSONObject("user_metadata")
        return listOf(meta?.optString("username"),meta?.optString("full_name"),meta?.optString("name"),user?.optString("email")).firstOrNull { !it.isNullOrBlank() } ?: "Google account"
    }
    private val googleFile get()=AtomicFile(File(context.noBackupFilesDir,"google-pending"))
    private fun pendingGoogle():JSONObject? {
        if(!googleFile.baseFile.exists() && !File(googleFile.baseFile.path+".bak").exists()) return null
        val value=readSealed(googleFile)
        val age=System.currentTimeMillis()-value.getLong("created")
        if(age !in 0..180000) { googleFile.delete(); return null }
        return value
    }
    val googlePending get()=try { pendingGoogle()!=null } catch(_:Exception) { googleFile.delete(); false }
    fun cancelGoogle() { googleFile.delete() }
    fun beginGoogle(include:Boolean):String {
        check(owner==null && Repository.engine.phase=="idle") { "Finish your current timer before signing in." }
        val connection=URL(URL_ROOT+"/auth/v1/settings").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=15000; connection.readTimeout=15000; connection.setRequestProperty("apikey",PUBLIC_KEY)
            check(connection.responseCode in 200..299) { "Unable to check Google sign-in. Please try again." }
            val settings=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            check(settings.optJSONObject("external")?.optBoolean("google")==true) { "Google sign-in needs one-time setup by the app owner. Username and PIN still work." }
        } finally { connection.disconnect() }
        val flow=GoogleOAuth.create()
        saveSealed(googleFile,JSONObject().put("verifier",flow.verifier).put("state",flow.state).put("created",System.currentTimeMillis()).put("include",include))
        return flow.authorize(URL_ROOT)
    }
    fun completeGoogle(callback:String) {
        val pending=pendingGoogle() ?: error("Google sign-in expired. Please try again.")
        val flow=GoogleOAuth(pending.getString("verifier"),pending.getString("state"))
        // Validate before consuming: unrelated links cannot cancel a legitimate pending sign-in.
        val code=try { flow.code(callback) } catch(e:Exception) { if(flow.matchesState(callback)) googleFile.delete(); throw e }
        check(owner==null && Repository.engine.phase=="idle") { "Finish your current timer before signing in." }
        googleFile.delete()
        val existing=if(pending.optBoolean("include")) Repository.sessions.toList() else emptyList()
        acceptLogin(post("/auth/v1/token?grant_type=pkce",JSONObject().put("auth_code",code).put("code_verifier",flow.verifier)),existing)
    }
    private val tokenFile get()=AtomicFile(File(context.noBackupFilesDir,"cloud-credentials"))
    private fun file(id:String)=AtomicFile(File(context.filesDir,"cloud-$id.json"))
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey("CyberCat.Sync",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("CyberCat.Sync",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun write(file:AtomicFile,bytes:ByteArray) { val out=file.startWrite(); try { out.write(bytes); file.finishWrite(out) } catch(e:Exception) { file.failWrite(out); throw e } }
    private fun saveLogin(value:JSONObject) = saveSealed(tokenFile,value)
    private fun saveSealed(file:AtomicFile,value:JSONObject) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        val data=JSONObject().put("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).put("data",Base64.encodeToString(cipher.doFinal(value.toString().toByteArray()),Base64.NO_WRAP))
        write(file,data.toString().toByteArray())
    }
    private fun readSealed(file:AtomicFile):JSONObject {
        val sealed=JSONObject(file.openRead().bufferedReader().use { it.readText() })
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(sealed.getString("iv"),Base64.NO_WRAP)))
        return JSONObject(String(cipher.doFinal(Base64.decode(sealed.getString("data"),Base64.NO_WRAP))))
    }
    fun init(c:Context) {
        context=c.applicationContext
        try {
            if(tokenFile.baseFile.exists() || File(tokenFile.baseFile.path+".bak").exists()) {
                login=readSealed(tokenFile)
                records=read(owner!!); status="Saved offline · waiting to sync"
            }
        } catch(e:Exception) { login=null; status="Couldn’t open secure account storage. Local history is safe." }
    }
    private fun read(id:String):MutableList<JSONObject> {
        val f=file(id); if(!f.baseFile.exists() && !File(f.baseFile.path+".bak").exists()) return mutableListOf()
        val array=JSONArray(f.openRead().bufferedReader().use { it.readText() }); return (0 until array.length()).map { array.getJSONObject(it) }.toMutableList()
    }
    private fun save(updated:List<JSONObject>) { write(file(owner!!),JSONArray(updated).toString().toByteArray()); records=updated.toMutableList() }
    private fun copy(r:JSONObject)=JSONObject(r.toString())
    private fun wire(r:JSONObject)=JSONObject().put("id",r.getString("id").lowercase()).put("name",r.getString("name"))
        .put("start_ms",r.getLong("start")).put("end_ms",r.getLong("end")).put("duration_ms",r.getLong("duration"))
        .put("platform",r.optString("platform").ifEmpty { "unknown" }).put("device_id",r.optString("device_id").ifEmpty { UUID.randomUUID().toString() })
        .put("device_name",r.optString("device_name").ifEmpty { "This device" }).put("revision",0).put("deleted",false)
    fun visible():MutableList<JSONObject> = synchronized(lock) {
        records.filter { !it.optBoolean("deleted") }.map { r -> JSONObject().put("id",r.getString("id")).put("name",r.getString("name"))
            .put("start",r.getLong("start_ms")).put("end",r.getLong("end_ms")).put("duration",r.getLong("duration_ms"))
            .put("platform",r.getString("platform")).put("device_id",r.getString("device_id")).put("device_name",r.getString("device_name")) }.sortedByDescending { it.getLong("start") }.toMutableList()
    }
    fun replace(visible:List<JSONObject>)=synchronized(lock) {
        val wanted=visible.associateBy { it.getString("id").lowercase() }; val old=records.associateBy { it.getString("id") }
        val updated=(wanted.keys+old.keys).map { id ->
            val before=old[id]; val after=wanted[id]
            val item=before?.let(::copy) ?: wire(after!!)
            val name=after?.getString("name") ?: item.getString("name"); val deleted=after==null
            if(before==null || name!=item.getString("name") || deleted!=item.optBoolean("deleted")) item.put("_dirty",true).put("_change",UUID.randomUUID().toString())
            item.put("name",name).put("deleted",deleted)
        }; save(updated)
    }
    private fun post(path:String,body:JSONObject,token:String?=null):JSONObject {
        val c=URL(URL_ROOT+path).openConnection() as HttpURLConnection
        try {
            c.requestMethod="POST"; c.connectTimeout=20000; c.readTimeout=20000; c.doOutput=true
            c.setRequestProperty("Content-Type","application/json"); c.setRequestProperty("apikey",PUBLIC_KEY)
            if(token!=null) c.setRequestProperty("Authorization","Bearer $token")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val ok=c.responseCode in 200..299
            val result=JSONObject((if(ok)c.inputStream else c.errorStream).bufferedReader().use { it.readText() })
            check(ok) { result.optString("error_description").ifEmpty { result.optString("msg").ifEmpty { result.optString("error","Unable to sync. Please try again.") } } }; return result
        } finally { c.disconnect() }
    }
    fun run(action:()->Unit,done:(String?)->Unit={}) {
        if(!busy.compareAndSet(false,true)) return
        pool.execute {
            var failure:String?=null
            try { action() } catch(e:Exception) { failure=e.localizedMessage ?: "Unable to connect"; status="Saved offline · $failure" }
            main.post { busy.set(false); if(owner!=null) Repository.sessions=visible(); changed?.invoke(); done(failure) }
        }
    }
    fun authenticate(name:String,pin:String,signup:Boolean,existing:List<JSONObject>) {
        val result=post("/functions/v1/cybercat-account",JSONObject().put("action",if(signup)"signup" else "login").put("username",name).put("pin",pin))
        acceptLogin(result,existing)
    }
    private fun acceptLogin(result:JSONObject,existing:List<JSONObject>) {
        val loaded=read(result.getJSONObject("user").getString("id")); saveLogin(result)
        synchronized(lock) { login=result; records=loaded; if(existing.isNotEmpty()) { val current=visible(); replace(current+existing.filter { r -> current.none { it.getString("id")==r.getString("id") } }) } }
        status="Signed in · waiting to sync"
    }
    fun signOut() { check(!busy.get()); tokenFile.delete(); check(!tokenFile.baseFile.exists() && !File(tokenFile.baseFile.path+".bak").exists()) { "Unable to remove saved sign-in" }; synchronized(lock) { login=null; records=mutableListOf() }; status="Local only" }
    fun hasConflicts()=synchronized(lock) { records.any { it.has("_conflict") } }
    fun resolve(keep:Boolean)=synchronized(lock) {
        save(records.map { copy(it).apply { val remote=optJSONObject("_conflict"); if(remote!=null) {
            if(keep) { put("revision",remote.getLong("revision")); put("_change",UUID.randomUUID().toString()); remove("_conflict") }
            else { return@map remote }
        } } })
    }
    fun sync() {
        val session=login ?: return; val id=owner!!
        val sent=synchronized(lock) { records.filter { it.optBoolean("_dirty") && !it.has("_conflict") }.take(100).map(::copy) }
        val refreshed=post("/functions/v1/cybercat-account",JSONObject().put("action","refresh").put("refresh_token",session.getString("refresh_token")))
        saveLogin(refreshed); login=refreshed
        val payload=sent.map { r -> copy(r).apply { keys().asSequence().filter { it.startsWith("_") || it=="user_id" || it=="updated_at" }.toList().forEach { remove(it) } } }
        val reply=post("/rest/v1/rpc/sync_focus_sessions",JSONObject().put("changes",JSONArray(payload)),refreshed.getString("access_token"))
        synchronized(lock) {
            if(owner!=id) return
            val array=reply.getJSONArray("records"); val remote=(0 until array.length()).associate { val r=array.getJSONObject(it); r.getString("id") to r }.toMutableMap()
            val conflicts=reply.getJSONArray("conflicts"); val conflictIds=(0 until conflicts.length()).map { conflicts.getString(it) }.toSet()
            for(r in records.filter { it.optBoolean("_dirty") }) {
                val rid=r.getString("id"); val item=copy(r)
                if(rid in conflictIds) { item.put("_conflict",remote[rid]); remote[rid]=item }
                else if(r.has("_conflict")) remote[rid]=item
                else if(sent.none { it.getString("id")==rid && it.optString("_change")==r.optString("_change") }) {
                    remote[rid]?.let { item.put("revision",it.getLong("revision")) }; remote[rid]=item
                }
            }
            save(remote.values.toList()); status=if(hasConflicts()) "Changes need review" else if(records.any { it.optBoolean("_dirty") }) "Saved offline · syncing remaining changes" else "All devices up to date"
        }
    }
}
