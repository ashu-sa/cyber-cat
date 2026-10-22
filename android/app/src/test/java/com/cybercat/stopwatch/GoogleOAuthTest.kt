package com.cybercat.stopwatch

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class GoogleOAuthTest {
    @Test fun rfcChallengeAndBrowserRequest() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",GoogleOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        val flow=GoogleOAuth.create(); val other=GoogleOAuth.create()
        assertTrue(flow.verifier.length>=43); assertNotEquals(flow.verifier,other.verifier)
        val url=flow.authorize("https://example.supabase.co")
        assertFalse(url.contains(flow.verifier))
        assertTrue(url.contains("code_challenge_method=s256"))
        val values=URI(url).rawQuery.split('&').associate { val pair=it.split('=',limit=2); pair[0] to URLDecoder.decode(pair[1],"UTF-8") }
        assertEquals("google",values["provider"])
        assertEquals("focanopy://auth/callback?state=${flow.state}",values["redirect_to"])
    }
    @Test fun acceptsOnlyCurrentCallbackOncePrepared() {
        val flow=GoogleOAuth.create()
        assertEquals("once",flow.code("focanopy://auth/callback?state=${flow.state}&code=once"))
        for(url in listOf("other://auth/callback?state=${flow.state}&code=x","focanopy://evil/callback?state=${flow.state}&code=x","focanopy://auth/callback?state=wrong&code=x","focanopy://auth/callback?state=${flow.state}&state=${flow.state}&code=x","focanopy://auth/callback?state=${flow.state}&code=x&code=y","focanopy://auth/callback?state=${flow.state}&error=access_denied")) {
            try { flow.code(url); fail("Accepted invalid callback") } catch(_:IllegalArgumentException) {} catch(_:IllegalStateException) {}
        }
        assertTrue(flow.matchesState("focanopy://auth/callback?state=${flow.state}&error=access_denied"))
        assertFalse(flow.matchesState("focanopy://auth/callback?state=old&code=x"))
    }
}
