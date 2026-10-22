package com.cybercat.stopwatch
import org.junit.Assert.*
import org.junit.Test
class TimerEngineTest {
    @Test fun pauseResumeExcludesBreaks() {
        var now=1000L; val engine=TimerEngine { now }
        engine.start(); now+=2000; engine.pause(); now+=9000
        assertEquals(2000L,engine.elapsed()); engine.start(); now+=3000; engine.finish()
        assertEquals(5000L,engine.elapsed()); assertEquals("finished",engine.phase)
    }
    @Test fun repeatedStartDoesNotResetAndSleepCounts() {
        var now=0L; val engine=TimerEngine { now }; engine.start(); now=7200000; engine.start()
        assertEquals(7200000L,engine.elapsed()); engine.finish(); engine.start(); assertEquals("finished",engine.phase)
    }
    @Test fun resumeFinishedExcludesNamingGap() {
        var now=0L;val engine=TimerEngine { now };engine.start();now=1200000;engine.finish()
        now+=3600000;engine.resumeFinished();now+=500;engine.resumeFinished();now+=500;engine.finish()
        assertEquals(1201000L,engine.elapsed());assertEquals("finished",engine.phase)
        engine.reset();assertEquals(0L,engine.elapsed())
    }
    @Test fun formattingAndReset() {
        assertEquals("100:00:01",clockText(360001000)); assertEquals("2h",focusText(7200000)); assertEquals("1h 15m",focusText(4500000))
        val engine=TimerEngine { 1000 }; engine.start(); engine.reset(); assertEquals("idle",engine.phase); assertEquals(0L,engine.elapsed())
    }
}
