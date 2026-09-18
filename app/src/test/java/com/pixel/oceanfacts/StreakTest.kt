package com.pixel.oceanfacts

import com.pixel.oceanfacts.core.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakTest {

    private val day = 20_000L

    @Test
    fun aFirstDayStartsARunOfOne() {
        val s = Streak.EMPTY.record(day)
        assertEquals(1, s.current)
        assertEquals(1, s.longest)
        assertEquals(day, s.lastDay)
        assertTrue(s.isActiveOn(day))
    }

    @Test
    fun theSameDayTwiceChangesNothing() {
        val once = Streak.EMPTY.record(day)
        assertEquals(once, once.record(day))
    }

    @Test
    fun aClockMovedBackwardsChangesNothing() {
        // The streak is only worth something if it cannot be farmed by winding the clock back.
        val s = Streak.EMPTY.record(day).record(day + 1)
        assertEquals(s, s.record(day - 5))
    }

    @Test
    fun consecutiveDaysExtendTheRun() {
        var s = Streak.EMPTY
        (0 until 5).forEach { s = s.record(day + it) }
        assertEquals(5, s.current)
        assertEquals(5, s.longest)
        assertEquals(listOf(true, true, true, true, true), s.week(day + 4).takeLast(5))
    }

    @Test
    fun aGapResetsTheRunButNotTheRecord() {
        var s = Streak.EMPTY
        (0 until 6).forEach { s = s.record(day + it) }
        val after = s.record(day + 10)
        assertEquals(1, after.current)
        assertEquals(6, after.longest)
    }

    @Test
    fun breaksOnOnlyFiresForARealGap() {
        var s = Streak.EMPTY
        (0 until 3).forEach { s = s.record(day + it) }
        assertFalse(s.breaksOn(day + 3))
        assertTrue(s.breaksOn(day + 4))
        assertEquals(0, s.gapOn(day + 3))
        assertEquals(1, s.gapOn(day + 4))
    }

    @Test
    fun aRepairRestoresTheRunAndLightsTheMissedDay() {
        var s = Streak.EMPTY
        (0 until 8).forEach { s = s.record(day + it) }
        val lost = s.current
        val broken = s.record(day + 9)      // missed day + 8
        assertEquals(1, broken.current)

        val repaired = broken.repaired(lost, day + 9)
        assertEquals(9, repaired.current)
        assertEquals(9, repaired.longest)
        assertTrue("the missed day should be filled in", repaired.isActiveOn(day + 8))
        assertTrue(repaired.isActiveOn(day + 9))
    }

    @Test
    fun aRepairCannotInventADay() {
        var s = Streak.EMPTY
        (0 until 4).forEach { s = s.record(day + it) }
        // Today is not recorded, so there is nothing to repair onto.
        assertEquals(s, s.repaired(4, day + 9))
    }

    @Test
    fun theHistoryExpiresRatherThanWrappingAround() {
        // `shl` uses only the low five bits of its count, so a gap of exactly 32 days would
        // otherwise leave the old mask in place and invent a week of activity.
        val s = Streak(current = 5, lastDay = day, longest = 5, mask = 0b11111)
        val after = s.record(day + Streak.HISTORY_DAYS)
        assertEquals(1, after.current)
        assertEquals(1, after.mask)
        assertTrue(after.week(day + Streak.HISTORY_DAYS).dropLast(1).none { it })
    }

    @Test
    fun aFutureDayIsNeverActive() {
        val s = Streak.EMPTY.record(day)
        assertFalse(s.isActiveOn(day + 1))
    }

    @Test
    fun seedRebuildsTheWeekForAnUpgradingInstall() {
        val s = Streak.seed(current = 3, lastDay = day)
        assertEquals(3, s.current)
        assertEquals(3, s.longest)
        assertEquals(listOf(false, false, false, false, true, true, true), s.week(day))
    }

    @Test
    fun theWeekReadsOldestFirst() {
        var s = Streak.EMPTY
        s = s.record(day).record(day + 1)
        val week = s.week(day + 2)
        assertEquals(Streak.WEEK, week.size)
        assertFalse("today has nothing recorded yet", week.last())
        assertTrue("yesterday was active", week[week.size - 2])
    }
}
