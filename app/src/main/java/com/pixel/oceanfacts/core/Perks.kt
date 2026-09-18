package com.pixel.oceanfacts.core

/**
 * What a broken streak is worth restoring, and when.
 *
 * Kept as a pure function away from the ad code so the rules can be read and tested on their
 * own — they are the part that decides whether the streak means anything, and they matter more
 * than the mechanics of playing a video.
 */
object Perks {

    /**
     * Below this a run is cheap to rebuild — three days of ordinary use — so spending an ad on
     * it is a bad trade for the user, and offering it trains ad-watching on throwaway streaks.
     */
    const val MIN_REPAIRABLE_STREAK = 3

    /**
     * Roughly two offers a month. A streak that can be bought back every few days is not a
     * record of a habit, and the habit is the thing actually worth money here.
     */
    const val REPAIR_COOLDOWN_DAYS = 14

    /**
     * How many times loading a video may fail in a day before the repair is granted anyway.
     *
     * One rewarded impression is worth cents; a user with a month-long run is worth many future
     * sessions. Losing them because our ad network had no fill is a straightforwardly bad trade,
     * so the failure is absorbed rather than passed on.
     */
    const val FAILURES_BEFORE_GRANT = 2

    /** A live offer to restore [lostStreak] days. */
    data class RepairOffer(val lostStreak: Int)

    /**
     * The offer to show today, or null when there is nothing to offer.
     *
     * Each of the four rules earns its place:
     *
     * - **Exactly one missed day.** Restoring a week away would be a lie, and one day is the
     *   only case where the loss genuinely feels unfair.
     * - **A run worth keeping** ([MIN_REPAIRABLE_STREAK]).
     * - **The offer dies at local midnight.** It appears on the day the break is noticed and is
     *   gone tomorrow, which is what stops "repair" becoming a permanent undo button.
     * - **A cooldown** ([REPAIR_COOLDOWN_DAYS]).
     */
    fun repairOffer(
        today: Long,
        brokenStreak: Int,
        brokenDay: Long,
        lastRepairDay: Long,
        gapDays: Int,
    ): RepairOffer? = when {
        brokenStreak < MIN_REPAIRABLE_STREAK -> null
        gapDays != 1 -> null
        brokenDay != today -> null
        lastRepairDay != Streak.NEVER && today - lastRepairDay < REPAIR_COOLDOWN_DAYS -> null
        else -> RepairOffer(brokenStreak)
    }
}
