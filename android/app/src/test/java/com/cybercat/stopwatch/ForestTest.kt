package com.cybercat.stopwatch
import org.junit.Assert.*
import org.junit.Test
class ForestTest {
 @Test fun calendarFitsEveryMonth() {
  for(year in listOf(2024,2026)) for(month in 1..12) {
   val value=java.time.YearMonth.of(year,month);val offset=value.atDay(1).dayOfWeek.value-1;val cells=Forest.calendarCells(offset,value.lengthOfMonth())
   assertTrue(cells in 28..42);assertEquals(0,cells%7);assertTrue(cells>=offset+value.lengthOfMonth());assertTrue(cells-offset-value.lengthOfMonth()<7)
  }
  assertEquals(28,Forest.calendarCells(0,28));assertEquals(42,Forest.calendarCells(6,31))
 }
 @Test fun nextTreeProgress() {
  assertEquals("60 min to your next tree",Forest.nextTreeText(0)); assertEquals("30 min to your next tree",Forest.nextTreeText(1800000)); assertEquals("1 min to your next tree",Forest.nextTreeText(3599999)); assertEquals("60 min to your next tree",Forest.nextTreeText(Forest.HOUR))
 }
 @Test fun milestonesAndFragments() {
  for((seconds,count) in listOf(0L to 0L,1799L to 0L,1800L to 0L,3599L to 0L,3600L to 1L,5400L to 1L,7200L to 2L,28799L to 7L,28800L to 8L,28801L to 8L)) {
   assertEquals(count,Forest.trees(seconds*1000)); assertEquals(seconds>=28800,Forest.garden(seconds*1000))
  }
  assertEquals("1 tree",Forest.text(1800000+1800000))
  assertEquals("8 trees",Forest.summary(28800000,List(8){3600000L}))
  assertEquals("8 trees · 1 garden day",Forest.summary(28800000,listOf(28800000)))
 }
}
