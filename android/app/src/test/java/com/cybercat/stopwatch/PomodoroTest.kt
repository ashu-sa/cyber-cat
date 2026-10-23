package com.cybercat.stopwatch
import org.junit.Assert.*
import org.junit.Test

class PomodoroTest {
    @Test fun blockingIsOptInDuringStopwatchOrFocusOnly() {
        assertTrue(focusBlockingActive(true,"running","focus",true))
        assertTrue(focusBlockingActive(false,"running","focus",true))
        assertFalse(focusBlockingActive(true,"running","focus",false))
        assertFalse(focusBlockingActive(true,"paused","focus",true))
        assertFalse(focusBlockingActive(true,"paused","break",true))
        assertFalse(focusBlockingActive(true,"paused","ready",true))
    }
    @Test fun allAppsRespectBrowsersExclusionsAndProtectedApps() {
        fun blocked(name:String,all:Boolean=true)=focusAppBlocked(name,all,setOf("studio","browser"),setOf("music"),setOf("browser"),setOf("home","keyboard"),setOf("studio","browser","music","home","keyboard"))
        assertTrue(blocked("studio"));assertFalse(blocked("browser"));assertFalse(blocked("music"));assertFalse(blocked("home"));assertFalse(blocked("keyboard"));assertFalse(blocked("system-agent"))
        assertTrue(blocked("browser",false));assertFalse(blocked("music",false))
        assertFalse(focusBlockingActive(true,"running","break",true))
        assertFalse(focusBlockingActive(false,"paused","focus",true))
    }
    @Test fun breaksAreExcludedAndNextRoundIsManual() {
        var now=0L;var wall=100000L;val plan=Pomodoro({now},{wall});val timer=TimerEngine {now}
        timer.start();now+=1500000;wall+=1500000
        val first=plan.advance(timer.elapsed(),true);assertEquals(1500000L,first.first);timer.accumulated=first.first!!;timer.phase="paused"
        now+=300000;wall+=300000;assertNotNull(plan.advance(timer.elapsed(),false).second);assertEquals("ready",plan.stage)
        now+=600000;wall+=600000;assertEquals(1500000L,timer.elapsed());assertEquals("paused",timer.phase)
        plan.start(timer.elapsed());timer.start();assertEquals(3000000L,plan.target);assertEquals("focus",plan.stage)
        now+=120000;assertEquals(1620000L,timer.elapsed())
    }
    @Test fun delayedTicksCapFocusAndSkipExpiredBreak() {
        var now=0L;var wall=10000L;val plan=Pomodoro({now},{wall});plan.breakMinutes=10
        now=7200000;wall+=7200000;val result=plan.advance(now,true)
        assertEquals(1500000L,result.first);assertEquals("ready",plan.stage);assertTrue(result.second!!.contains("needs water"))
    }
    @Test fun restartRestoresRemainingBreakAndEarlyStartIsExplicit() {
        var now=0L;var wall=100000L;val plan=Pomodoro({now},{wall})
        now+=1502000;wall+=1502000;plan.advance(now,true);assertEquals(298000L,plan.remaining(1500000))
        now+=100000;wall+=100000;val recovered=Pomodoro({now},{wall});recovered.restore(plan.stage,plan.target,plan.breakMinutes,plan.deadline)
        assertEquals(198000L,recovered.remaining(1500000));recovered.start(1500000);assertEquals(3000000L,recovered.target)
    }
}
