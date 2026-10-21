package com.cybercat.stopwatch
object Forest {
    const val HOUR=3600000L
    const val GARDEN=8*HOUR
    fun trees(ms:Long)=ms.coerceAtLeast(0)/HOUR
    fun garden(ms:Long)=ms>=GARDEN
    fun text(ms:Long):String { val n=trees(ms); return "$n tree${if(n==1L)"" else "s"}" }
    fun cell(ms:Long)=if(garden(ms))"Garden" else if(trees(ms)>0)text(ms) else if(ms>0)"Growing" else "—"
    fun summary(ms:Long,daily:Collection<Long>):String {
        val n=daily.count { garden(it) }; return text(ms)+(if(n>0)" · $n garden day${if(n==1)"" else "s"}" else "")
    }
}
