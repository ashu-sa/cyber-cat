package com.cybercat.stopwatch

/** Pure layout: preserves the completed tree while the next seed emerges in front. */
object Grove {
    data class Tree(val x:Float,val scale:Float,val alpha:Float)
    data class Frame(val trees:List<Tree>,val active:Float,val seedAlpha:Float)
    fun frame(count:Long,remainder:Float,animated:Boolean=true,flower:Boolean=false):Frame {
        val v=if(animated)(remainder/(if(flower)5.76f else 2.4f)).coerceIn(0f,1f) else 1f
        val ease=v*v*(3-2*v)
        val targets=listOf(Tree(45f,.86f,1f),Tree(30f,.70f,.86f),Tree(20f,.56f,.72f),Tree(12f,.46f,0f))
        val trees=(0 until minOf(count,4).toInt()).reversed().map {rank->
            val a=if(rank==0)Tree(if(count==1L)55f else 66f,1f,1f) else targets[rank-1];val b=targets[rank]
            Tree(a.x+(b.x-a.x)*ease,a.scale+(b.scale-a.scale)*ease,a.alpha+(b.alpha-a.alpha)*ease)
        }
        return Frame(trees,if(count>0)66f else 55f,if(count>0)ease else 1f)
    }
}
