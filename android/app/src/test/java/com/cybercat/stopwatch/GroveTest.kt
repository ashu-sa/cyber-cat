package com.cybercat.stopwatch
import org.junit.Assert.*
import org.junit.Test
class GroveTest {
    @Test fun completedTreeKeepsItsOriginalPoseAtBoundary() {
        val start=Grove.frame(1,0f);assertEquals(55f,start.trees.single().x,0f);assertEquals(1f,start.trees.single().scale,0f);assertEquals(0f,start.seedAlpha,0f)
        val half=Grove.frame(1,1.2f);assertEquals(50f,half.trees.single().x,.001f);assertEquals(.5f,half.seedAlpha,.001f)
        assertEquals(45f,Grove.frame(1,2.4f).trees.single().x,.001f)
    }
    @Test fun nextBoundaryPreservesEveryExistingTreeAndBoundsClutter() {
        val start=Grove.frame(2,0f);assertEquals(45f,start.trees[0].x,0f);assertEquals(66f,start.trees[1].x,0f)
        assertEquals(4,Grove.frame(100,5f).trees.size);assertEquals(0f,Grove.frame(100,5f).trees.first().alpha,0f)
        assertEquals(Grove.frame(2,1f),Grove.frame(2,1f))
    }
    @Test fun reducedMotionSettlesImmediatelyAndFlowerUsesSameRealDuration() {
        assertEquals(45f,Grove.frame(1,0f,false).trees[0].x,0f)
        assertEquals(.5f,Grove.frame(1,2.88f,true,true).seedAlpha,.001f)
    }
}
