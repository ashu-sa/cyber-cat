package com.cybercat.stopwatch

import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE binds the browser callback to this device. No Google client secret ships in apps. */
data class GoogleOAuth(val verifier:String, val state:String) {
    companion object {
        fun create():GoogleOAuth {
            fun random():String { val bytes=ByteArray(32); SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) }
            return GoogleOAuth(random(),random())
        }
        fun challenge(verifier:String):String = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        fun isCallback(value:String):Boolean = try { URI(value).let { it.scheme=="focanopy" && it.host=="auth" && it.path=="/callback" } } catch(_:Exception) { false }
    }
    fun authorize(root:String):String {
        fun encode(value:String)=URLEncoder.encode(value,"UTF-8")
        val redirect="focanopy://auth/callback?state=${encode(state)}"
        val values=linkedMapOf("provider" to "google","redirect_to" to redirect,"code_challenge" to challenge(verifier),"code_challenge_method" to "s256","scopes" to "openid email profile","prompt" to "select_account")
        return root+"/auth/v1/authorize?"+values.entries.joinToString("&") { encode(it.key)+"="+encode(it.value) }
    }
    private fun values(callback:String):Map<String,List<String>> {
        require(isCallback(callback)) { "Invalid Google sign-in callback." }
        return (URI(callback).rawQuery ?: "").split('&').filter { it.isNotEmpty() }.map { it.split('=',limit=2) }.groupBy({ URLDecoder.decode(it[0],"UTF-8") },{ URLDecoder.decode(it.getOrElse(1){""},"UTF-8") })
    }
    fun matchesState(callback:String):Boolean = try { values(callback)["state"]==listOf(state) } catch(_:Exception) { false }
    fun code(callback:String):String {
        val values=values(callback)
        require(values["state"]==listOf(state)) { "This sign-in link is no longer valid. Please try again." }
        check(!values.containsKey("error")) { "Google sign-in was not completed. Please try again." }
        val codes=values["code"]
        require(codes?.size==1 && codes[0].isNotEmpty() && codes[0].length<=2048) { "Invalid Google sign-in response." }
        return codes[0]
    }
}
