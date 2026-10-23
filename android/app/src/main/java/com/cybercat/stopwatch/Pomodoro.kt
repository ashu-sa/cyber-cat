package com.cybercat.stopwatch

/** Focus totals use monotonic time. A restored deadline anchors back onto monotonic time. */
class Pomodoro(private val clock:()->Long,private val wall:()->Long) {
    companion object { const val FOCUS=25*60*1000L }
    var stage="focus"
    var target=FOCUS
    var breakMinutes=5
    var deadline=0L
    private var end=0L
    fun restore(stage:String,target:Long,minutes:Int,deadline:Long) {
        this.stage=stage;this.target=target;breakMinutes=if(minutes==10)10 else 5;this.deadline=deadline
        end=clock()+(deadline-wall()).coerceAtLeast(0)
    }
    fun start(elapsed:Long) { if(stage=="break" || stage=="ready") { stage="focus";target=elapsed+FOCUS;deadline=0;end=0 } }
    fun remaining(elapsed:Long):Long = when(stage) { "break" -> (end-clock()).coerceAtLeast(0); "ready" -> 0; else -> (target-elapsed).coerceAtLeast(0) }
    fun advance(elapsed:Long,running:Boolean):Pair<Long?,String?> {
        var cap:Long?=null;var notice:String?=null
        if(stage=="focus" && running && elapsed>=target) {
            cap=target;val remaining=breakMinutes*60000L-(elapsed-target)
            deadline=wall()+remaining;end=clock()+remaining;stage="break"
            notice="Your flower bloomed! Stop watering, stretch and move a little."
        }
        if(stage=="break" && remaining(elapsed)==0L) { stage="ready";notice="Come back when you’re ready — your next flower needs water." }
        return cap to notice
    }
}

fun focusBlockingActive(pomodoro:Boolean,phase:String,stage:String,enabled:Boolean):Boolean = pomodoro && phase=="running" && stage=="focus" && enabled
