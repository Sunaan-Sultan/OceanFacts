package com.pixel.oceanfacts.core

/**
 * A run of consecutive active days, and the short history behind it.
 *
 * All of the streak's day arithmetic lives here, as a value type with no Android imports, so
 * it can be reasoned about and tested without a device. [OceanPrefs] is only the shell that
 * reads these four numbers off disk, applies a transform, and writes them back.
 *
 * ## Why the history is a bitmask
 *
 * The week dots need to answer "was the app used on day X" for the last seven days. Storing a
 * set of day indices would mean a growing string set that has to be swept for old entries.
 * Instead bit *i* of [mask] means "the day `lastDay - i` was active". That is one `putInt`, it
 * expires on its own (anything older than [HISTORY_DAYS] shifts off the top edge and is gone),
 * and — because the anchor is [lastDay], which is already persisted — **reading never writes**.
 * A reader on a later day simply indexes further up the mask and finds zeroes.
 */
data class Streak(
    /** Days in the current run. 0 only when the app has never been used. */
    val current: Int,
    /** Day index of the most recent active day, or [NEVER]. */
    val lastDay: Long,
    /** The best run ever reached. Never falls. */
    val longest: Int,
    /** Bit *i* = the day `lastDay - i` was active. Bit 0 is always set unless [lastDay] is [NEVER]. */
    val mask: Int,
) {

    /**
     * The state after activity on [today].
     *
     * The order of the rules matters:
     *
     * 1. A first ever day starts a run of one.
     * 2. `today <= lastDay` returns this state untouched. That covers two cases at once — the
     *    same day twice (the idempotence every caller relies on, since it runs on every fact
     *    opened) and a clock that has moved backwards, either by a westbound flight or by a
     *    user winding it back to farm days. Failing closed here is what makes the streak worth
     *    something.
     * 3. Yesterday extends the run.
     * 4. Any longer gap starts again at one.
     */
    fun record(today: Long): Streak {
        if (lastDay == NEVER) return Streak(1, today, maxOf(longest, 1), 1)
        if (today <= lastDay) return this
        val next = if (today == lastDay + 1) current + 1 else 1
        return Streak(
            current = next,
            lastDay = today,
            longest = maxOf(longest, next),
            mask = shift(mask, today - lastDay) or 1,
        )
    }

    /** True when activity on [today] would end the current run rather than extend it. */
    fun breaksOn(today: Long): Boolean = lastDay != NEVER && today > lastDay + 1

    /** Days missed before [today], or 0 when the run is unbroken or has never started. */
    fun gapOn(today: Long): Int =
        if (lastDay == NEVER || today <= lastDay) 0 else (today - lastDay - 1).toInt()

    /**
     * Restores a run that a single missed day broke.
     *
     * By the time this is called the break has already been recorded: [current] is 1, [lastDay]
     * is today, and the mask holds today at bit 0, the missed day unlit at bit 1, and the old
     * run from bit 2 up. So repairing is exactly "light bit 1" — which makes the mask contiguous
     * again — and continuing the count at [lost] + 1, today included.
     *
     * A no-op unless today is already recorded, so a repair can never invent a day.
     */
    fun repaired(lost: Int, today: Long): Streak {
        if (lastDay != today || lost <= 0) return this
        val restored = lost + 1
        return copy(
            current = restored,
            longest = maxOf(longest, restored),
            mask = mask or 0b10,
        )
    }

    /** Whether [day] was active, as far back as [HISTORY_DAYS]. Days beyond that read as false. */
    fun isActiveOn(day: Long): Boolean {
        if (lastDay == NEVER) return false
        val i = lastDay - day
        // A negative index is a day in the future, which is never active — guarding it here
        // stops it wrapping round and reading some unrelated bit.
        return i >= 0 && i < HISTORY_DAYS && (mask ushr i.toInt()) and 1 == 1
    }

    /**
     * The last seven days ending at [today], **oldest first and today last**, so the dots can be
     * laid out left to right without the caller reversing anything.
     */
    fun week(today: Long): List<Boolean> = (WEEK - 1 downTo 0).map { isActiveOn(today - it) }

    companion object {

        /** No day has ever been recorded. Distinct from day 0, which is a real date in 1970. */
        const val NEVER = Long.MIN_VALUE

        /** The width of an Int, and therefore how far back the history reaches before it expires. */
        const val HISTORY_DAYS = 32

        const val WEEK = 7

        val EMPTY = Streak(current = 0, lastDay = NEVER, longest = 0, mask = 0)

        /**
         * Moves the history [by] days into the past.
         *
         * The `by >= HISTORY_DAYS` case is not an optimisation — it is required. Kotlin's `shl`
         * uses only the low five bits of the shift count, so `mask shl 32` is `mask` unchanged,
         * and a user returning after exactly 32 days would be shown a full week of activity they
         * never had.
         */
        fun shift(mask: Int, by: Long): Int = when {
            by <= 0L -> mask
            by >= HISTORY_DAYS -> 0
            else -> mask shl by.toInt()
        }

        /**
         * Rebuilds a [Streak] from the two values an older install stored, so upgrading users
         * keep their run and see a correct week of dots on first launch rather than an empty one.
         *
         * A run of *n* ending on [lastDay] was, by definition, active on the *n* days up to and
         * including it — which is the whole of the history we can infer, and all of it we need.
         */
        fun seed(current: Int, lastDay: Long): Streak {
            if (lastDay == NEVER || current <= 0) return EMPTY
            val n = current.coerceAtMost(HISTORY_DAYS)
            val mask = if (n >= HISTORY_DAYS) -1 else (1 shl n) - 1
            return Streak(current = current, lastDay = lastDay, longest = current, mask = mask)
        }
    }
}
