package com.cybercat.stopwatch

class TimerEngine(private val clock: () -> Long) {
    var phase = "idle"
    var accumulated = 0L
    var anchor = 0L
    fun elapsed(): Long = accumulated + if (phase == "running") (clock() - anchor).coerceAtLeast(0) else 0
    fun start() {
        if (phase != "idle" && phase != "paused") return
        if (phase == "idle") accumulated = 0
        anchor = clock(); phase = "running"
    }
    fun resumeFinished() { if(phase=="finished") { anchor=clock(); phase="running" } }
    fun pause() { if (phase == "running") { accumulated = elapsed(); phase = "paused" } }
    fun finish() { if (phase == "running" || phase == "paused") { accumulated = elapsed(); phase = "finished" } }
    fun reset() { phase = "idle"; accumulated = 0; anchor = 0 }
}
fun clockText(milliseconds: Long): String {
    val s = milliseconds.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}
fun focusText(milliseconds: Long): String {
    val s = milliseconds.coerceAtLeast(0) / 1000; val h = s / 3600; val m = s / 60 % 60
    return when { h > 0 -> "${h}h" + if (m > 0) " ${m}m" else ""; m > 0 -> "${m}m"; s > 0 -> "${s}s"; else -> "0m" }
}
