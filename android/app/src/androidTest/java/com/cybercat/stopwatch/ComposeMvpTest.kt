package com.cybercat.stopwatch

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.runner.RunWith
import java.time.Instant
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class ComposeMvpTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Before fun clean() {
        compose.runOnIdle {
            check(compose.activity.packageName.endsWith(".qa"))
            Repository.discard();Repository.selectMode(false);Repository.replace(emptyList())
        }
        compose.waitUntil(5000) {compose.onAllNodesWithContentDescription("Start or resume").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun capture(name:String) {
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(compose.activity.filesDir,"compose-$name.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun startPauseResumeSaveAndRename() {
        capture("focus")
        compose.onNodeWithContentDescription("Start or resume").performClick()
        compose.waitUntil(5000) {Repository.engine.phase=="running"}
        compose.runOnIdle {Repository.engine.accumulated=38*60000L;Repository.engine.anchor=android.os.SystemClock.elapsedRealtime()}
        compose.waitUntil(5000) {compose.onAllNodes(hasText("00:38",substring=true)).fetchSemanticsNodes().isNotEmpty()}
        capture("growing")
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.waitUntil(5000) {Repository.engine.phase=="paused"}
        val elapsed=Repository.engine.elapsed()
        Thread.sleep(150)
        Assert.assertEquals(elapsed,Repository.engine.elapsed())
        compose.onNodeWithContentDescription("Start or resume").performClick()
        compose.onNodeWithContentDescription("Finish session").performClick()
        compose.onNodeWithText("Task name").performTextInput("Compose focus")
        compose.onNodeWithText("Save session").performClick()
        compose.onNodeWithText("Your time, in bloom.").assertIsDisplayed()
        compose.onNodeWithText("Compose focus").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Session options").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("Task name").performTextReplacement("Polished journal")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Polished journal").assertIsDisplayed()
    }
    @Test fun pomodoroHasCancellationInsteadOfFinish() {
        compose.onNodeWithText("Flower focus").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithContentDescription("Start or resume").performClick()
        compose.onNodeWithContentDescription("Cancel round · tree will die").assertIsDisplayed()
        compose.onNodeWithContentDescription("Finish session").assertDoesNotExist()
        compose.runOnIdle {Repository.engine.accumulated=Pomodoro.FOCUS;Repository.advancePomodoro()}
        compose.waitUntil(5000) {Repository.sessions.size==1}
        compose.runOnIdle {Repository.discard()}
    }
    @Test fun calendarTotalsAndProfileAreAccessible() {
        compose.runOnIdle {
            val now=System.currentTimeMillis()
            fun record(name:String,start:Long,length:Long,platform:String)=JSONObject().put("id",java.util.UUID.randomUUID().toString()).put("name",name).put("start",start).put("end",start+length).put("duration",length).put("platform",platform)
            Repository.replace(listOf(record("Upskill",now-7200000,7200000,"android"),record("Interview prep",now-86400000,3600000,"macos"),record("Deep work garden",now-3*86400000,9*3600000,"android")))
        }
        compose.onNodeWithText("Journal").performClick()
        compose.onAllNodesWithText("2h 0m").onFirst().assertExists()
        capture("journal")
        compose.onNodeWithText("Upskill").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Floating tree").assertIsDisplayed()
        capture("settings")
        compose.onNodeWithText("Your Google profile").performClick()
        compose.onNodeWithText("Your grove, everywhere").assertIsDisplayed()
        compose.onNodeWithText("Username").assertDoesNotExist()
        capture("profile")
    }

    @Test fun backgroundTimingAndStandardPermissions() {
        compose.runOnIdle {
            val info=compose.activity.packageManager.getPackageInfo(compose.activity.packageName,android.content.pm.PackageManager.GET_SERVICES)
            if(!BuildConfig.APP_BLOCKING_AVAILABLE) {
                Assert.assertTrue(info.services.orEmpty().none {it.permission=="android.permission.BIND_ACCESSIBILITY_SERVICE"})
                try {Class.forName("com.cybercat.stopwatch.FocusAccessibilityService");error("Accessibility implementation in standard APK")}catch(expected:ClassNotFoundException){}
            }
        }
        compose.onNodeWithContentDescription("Start or resume").performClick()
        val before=Repository.engine.elapsed()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        Thread.sleep(650)
        Assert.assertTrue(Repository.engine.elapsed()>=before+600)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.onNodeWithContentDescription("Pause").performClick()
        compose.runOnIdle {Repository.discard()}
    }
}
