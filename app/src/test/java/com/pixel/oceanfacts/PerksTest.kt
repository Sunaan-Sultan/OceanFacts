package com.pixel.oceanfacts

import com.pixel.oceanfacts.core.Perks
import com.pixel.oceanfacts.core.Streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PerksTest {

    private val today = 20_000L

    private fun offer(
        brokenStreak: Int = 7,
        brokenDay: Long = today,
        lastRepairDay: Long = Streak.NEVER,
        gapDays: Int = 1,
    ) = Perks.repairOffer(today, brokenStreak, brokenDay, lastRepairDay, gapDays)

    @Test
    fun aRunWorthKeepingIsOffered() {
        val o = offer()
        assertNotNull(o)
        assertEquals(7, o!!.lostStreak)
    }

    @Test
    fun aShortRunIsNotWorthAnAd() {
        assertNull(offer(brokenStreak = Perks.MIN_REPAIRABLE_STREAK - 1))
    }

    @Test
    fun onlyASingleMissedDayCanBeRestored() {
        assertNull("a week away is not an accident", offer(gapDays = 4))
        assertNull(offer(gapDays = 0))
    }

    @Test
    fun theOfferDiesAtMidnight() {
        assertNull(offer(brokenDay = today - 1))
    }

    @Test
    fun theCooldownHolds() {
        assertNull(offer(lastRepairDay = today - (Perks.REPAIR_COOLDOWN_DAYS - 1)))
        assertNotNull(offer(lastRepairDay = today - Perks.REPAIR_COOLDOWN_DAYS))
    }
}
