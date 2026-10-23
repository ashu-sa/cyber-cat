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
    fun remaining(elapsed:Long):Long = when(stage) { "dead" -> FOCUS; "break" -> (end-clock()).coerceAtLeast(0); "ready" -> 0; else -> (target-elapsed).coerceAtLeast(0) }
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

fun focusBlockingActive(pomodoro:Boolean,phase:String,stage:String,enabled:Boolean):Boolean = phase=="running" && (!pomodoro || stage=="focus") && enabled

/** System essentials and user exclusions always win. Browsers are allowed only in all-app mode. */
fun focusAppBlocked(name:String,all:Boolean,selected:Set<String>,excluded:Set<String>,browsers:Set<String>,protected:Set<String>,launchers:Set<String>):Boolean =
    name !in protected && name !in excluded && (if(all) name in launchers && name !in browsers else name in selected)

data class TreePreviewFrame(val elapsed:Long,val dead:Boolean,val death:Float,val title:String) {
    companion object {
        fun at(time:Float):TreePreviewFrame {
            val t=time.coerceIn(0f,27f)
            return when {
                t<10 -> TreePreviewFrame((t/10*Pomodoro.FOCUS).toLong(),false,0f,"01 · A seed becomes a flower")
                t<12 -> TreePreviewFrame(Pomodoro.FOCUS,false,0f,"02 · 25 minutes complete — flower saved")
                t<20 -> TreePreviewFrame(Pomodoro.FOCUS+((t-12)/8*975000).toLong(),false,0f,"03 · A second tree begins")
                else -> TreePreviewFrame(2475000,true,((t-20)/3.4f).coerceIn(0f,1f),if(t<23.4)"04 · Cancelled — the unfinished tree withers" else "05 · Completed flower safe · Cancelled tree lost")
            }
        }
    }
}
