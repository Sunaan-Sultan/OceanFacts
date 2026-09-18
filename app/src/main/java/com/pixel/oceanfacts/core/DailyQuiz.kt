package com.pixel.oceanfacts.core

import kotlin.random.Random

/**
 * The one round of ten the app leads with today — the home card, the quiz hub and the reminder
 * notification must all agree about it, so they all come through here.
 *
 * Built exactly the way [DailyFact] builds today's fact, and for the same reason: the choice is
 * a pure function of the day, so it survives relaunches, and the notification worker can work
 * out the same answer in a cold process without ever talking to the UI. Nothing is stored.
 *
 * [Quiz.round] is already deterministic given a seed, so the same build on the same day hands
 * every user the same ten questions in the same order.
 */
object DailyQuiz {

    /**
     * Salts the day index so the daily round does not move in lockstep with anything else that
     * seeds off a day — today's fact above all, which would otherwise correlate with it.
     */
    private const val SEED = 0x0CEA_11D0L

    fun roundForToday(): List<QuizQuestion> = roundForDay(OceanPrefs.dayIndex)

    /** Exposed for tests and for a "tomorrow" preview; [day] is a day index, not millis. */
    fun roundForDay(day: Long): List<QuizQuestion> =
        Quiz.round(Quiz.ROUND_SIZE, Random(SEED + day))
}

/**
 * Everything the end of a daily round needs to know to tell the user what just happened.
 *
 * Assembled by [OceanPrefs.recordDailyQuiz], which has to capture the streak *before* it writes
 * the new one — otherwise the celebration has nothing to compare against.
 */
data class DailyQuizOutcome(
    /** False when the same day's round is replayed; the day is never counted twice. */
    val firstToday: Boolean,
    val beatBest: Boolean,
    val streakBefore: Int,
    val streak: Int,
    val longest: Int,
    val week: List<Boolean>,
) {
    val extended: Boolean get() = streak > streakBefore
    val newLongest: Boolean get() = extended && streak >= longest
}
