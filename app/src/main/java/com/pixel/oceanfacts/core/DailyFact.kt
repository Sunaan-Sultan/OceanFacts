package com.pixel.oceanfacts.core

import kotlin.random.Random

/**
 * Picks the one fact the app leads with today — the "Today" card, the reminder notification
 * and any other daily surface must all agree, so they all come through here.
 *
 * The choice is a pure function of the day, which keeps it stable across relaunches and across
 * processes (the notification worker never talks to the UI) without storing anything. The
 * catalog is walked as a shuffled cycle rather than sampled at random, so no fact repeats until
 * every other one has had its turn; each cycle reshuffles on its own seed, so the order differs
 * from one pass to the next.
 */
object DailyFact {

    /** The id of today's fact, or null before [OceanData.init] has loaded the catalog. */
    fun idForToday(): String? = factForToday()?.id

    fun factForToday(): Fact? = factForDay(OceanPrefs.dayIndex)

    /** Exposed for tests and for a "tomorrow" preview; [day] is a day index, not millis. */
    fun factForDay(day: Long): Fact? {
        val facts = ALL_FACTS
        if (facts.isEmpty()) return null
        val size = facts.size
        // floorDiv/floorMod so a day index before the epoch still lands in range.
        val cycle = Math.floorDiv(day, size.toLong())
        val within = Math.floorMod(day, size.toLong()).toInt()
        return facts.shuffled(Random(cycle)).getOrNull(within)
    }
}
