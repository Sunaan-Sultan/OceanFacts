package com.pixel.oceanfacts

import com.pixel.oceanfacts.core.Streak
import com.pixel.oceanfacts.notify.DailyReminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DailyReminderTest {

    private val today = 20_000L

    private fun nudge(
        streak: Int = 5,
        quizDoneToday: Boolean = false,
        lastActiveDay: Long = today - 1,
    ) = DailyReminder.nudgeFor(streak, quizDoneToday, lastActiveDay, today)

    @Test
    fun aLiveStreakAtStakeGetsTheQuiz() {
        assertEquals(DailyReminder.Nudge.QUIZ, nudge())
    }

    @Test
    fun aRoundAlreadyDoneTodayGetsTheFact() {
        assertEquals(DailyReminder.Nudge.FACT, nudge(quizDoneToday = true))
    }

    @Test
    fun aStreakTooShortToNameGetsTheFact() {
        assertEquals(
            DailyReminder.Nudge.FACT,
            nudge(streak = DailyReminder.MIN_STREAK_FOR_QUIZ_NUDGE - 1),
        )
    }

    @Test
    fun aStaleStreakGetsTheFact() {
        // A number left over from a week ago is not at stake tonight, and a nudge that claims
        // otherwise is worse than no nudge.
        assertEquals(DailyReminder.Nudge.FACT, nudge(lastActiveDay = today - 5))
        assertEquals(DailyReminder.Nudge.FACT, nudge(lastActiveDay = Streak.NEVER))
    }

    @Test
    fun aDayAlreadyEarnedGetsTheFact() {
        assertEquals(DailyReminder.Nudge.FACT, nudge(lastActiveDay = today))
    }

    @Test
    fun theNextRunIsAlwaysInTheFuture() {
        val now = System.currentTimeMillis()
        (0..23).forEach { hour ->
            val delay = DailyReminder.millisUntilNextRun(hour, now)
            assert(delay > 0) { "hour $hour scheduled in the past" }
            assert(delay <= 24 * 60 * 60 * 1000L) { "hour $hour scheduled more than a day out" }
        }
    }

    @Test
    fun theNextRunLandsOnTheHourAsked() {
        val now = System.currentTimeMillis()
        (0..23).forEach { hour ->
            val at = Calendar.getInstance().apply {
                timeInMillis = now + DailyReminder.millisUntilNextRun(hour, now)
            }
            assertEquals(hour, at.get(Calendar.HOUR_OF_DAY))
            assertEquals(0, at.get(Calendar.MINUTE))
        }
    }
}
