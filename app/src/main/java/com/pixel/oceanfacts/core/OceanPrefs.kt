package com.pixel.oceanfacts.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.TimeZone

/**
 * The small amount of state that has to outlive the process: the saved collection, which facts
 * have been seen, the daily streak, and the daily quiz record.
 *
 * [init] runs once from the Application, before any screen reads these. Every accessor falls
 * back to a sane default when that has not happened (Compose previews, unit tests), so nothing
 * here throws on an uninitialised object.
 *
 * The streak arithmetic itself lives in [Streak], which knows nothing about Android; this object
 * only reads the numbers off disk, hands them to that transform, and writes the result back.
 */
object OceanPrefs {

    private const val FILE = "ocean_prefs"
    private const val KEY_SAVED = "saved_ids"
    private const val KEY_VIEWED = "viewed_ids"
    private const val KEY_STREAK = "streak_days"
    private const val KEY_LAST_OPEN = "last_open_day"
    private const val KEY_FIRST_OPEN = "first_open_day"
    private const val KEY_LAST_VERSION = "last_seen_version"
    private const val KEY_AD_FREE = "ad_free"
    private const val KEY_NOTIFY_ENABLED = "notify_enabled"
    private const val KEY_NOTIFY_HOUR = "notify_hour"
    private const val KEY_QUIZ_BEST = "quiz_best"
    private const val KEY_QUIZ_ROUNDS = "quiz_rounds"
    private const val KEY_QUIZ_PROGRESS = "quiz_progress"
    private const val KEY_STREAK_LONGEST = "streak_longest"
    private const val KEY_ACTIVE_MASK = "active_mask"
    private const val KEY_DAILY_DAY = "daily_quiz_day"
    private const val KEY_DAILY_SCORE = "daily_quiz_score"
    private const val KEY_DAILY_COUNT = "daily_quiz_count"
    private const val KEY_BROKEN_STREAK = "broken_streak"
    private const val KEY_BROKEN_DAY = "broken_day"
    private const val KEY_LAST_REPAIR = "last_repair_day"
    private const val KEY_REPAIR_FAIL_DAY = "repair_fail_day"
    private const val KEY_REPAIR_FAILS = "repair_fails"

    private const val MILLIS_PER_DAY = 86_400_000L

    /** Early evening — the day has settled, and it is still early enough not to be a nuisance. */
    const val DEFAULT_NOTIFY_HOUR = 19

    /** The one fact a brand-new install starts with, seeded only on a true first run. */
    private val FIRST_RUN_SAVED = setOf("blue-whale")

    private var prefs: SharedPreferences? = null

    /** Whether [init] found no existing data, i.e. this is the app's very first launch. */
    private var freshInstall = false

    /**
     * Bumped by every mutating call, so a screen can subscribe to "something changed here".
     *
     * These values would be safe to read plainly if nothing could alter them while a screen was
     * alive. The daily quiz changes that: finishing a round extends the streak, and the home
     * card behind it would otherwise sit there urging the user to keep a streak they just kept.
     */
    var revision: Int by mutableIntStateOf(0)
        private set

    /** Idempotent — safe to call from every entry point. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        freshInstall = !p.contains(KEY_FIRST_OPEN)
        // Seed the first-run values only when the keys are absent, so a user who clears their
        // collection does not get the blue whale handed back on the next launch.
        p.edit().apply {
            if (!p.contains(KEY_SAVED)) putStringSet(KEY_SAVED, FIRST_RUN_SAVED)
            if (!p.contains(KEY_FIRST_OPEN)) putLong(KEY_FIRST_OPEN, today())
            // An install from before the history existed knows its run but not the days behind
            // it. A run of n ending on lastDay was active on the n days up to it, so the week
            // dots and the record are right on the first launch after an update rather than
            // starting blank.
            if (!p.contains(KEY_ACTIVE_MASK) && p.contains(KEY_LAST_OPEN)) {
                val seed = Streak.seed(
                    current = p.getInt(KEY_STREAK, 0),
                    lastDay = p.getLong(KEY_LAST_OPEN, Streak.NEVER),
                )
                putInt(KEY_ACTIVE_MASK, seed.mask)
                putInt(KEY_STREAK_LONGEST, maxOf(p.getInt(KEY_STREAK_LONGEST, 0), seed.longest))
            }
        }.apply()
    }

    // getStringSet hands back an instance owned by SharedPreferences that must not be mutated,
    // so both accessors copy across the boundary.
    var saved: Set<String>
        get() = prefs?.getStringSet(KEY_SAVED, null)?.toSet() ?: FIRST_RUN_SAVED
        set(value) { prefs?.edit()?.putStringSet(KEY_SAVED, value.toSet())?.apply() }

    var viewed: Set<String>
        get() = prefs?.getStringSet(KEY_VIEWED, null)?.toSet() ?: emptySet()
        set(value) { prefs?.edit()?.putStringSet(KEY_VIEWED, value.toSet())?.apply() }

    /**
     * Cached copy of the Play "remove ads" entitlement, so the ad code can answer synchronously
     * on a cold start. Play owns the real answer and overwrites this on every successful
     * ownership query.
     */
    var adFree: Boolean
        get() = prefs?.getBoolean(KEY_AD_FREE, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_AD_FREE, value)?.apply() }

    /** Whether the daily reminder is scheduled. Off until the user asks for it. */
    var notifyEnabled: Boolean
        get() = prefs?.getBoolean(KEY_NOTIFY_ENABLED, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_NOTIFY_ENABLED, value)?.apply() }

    /** Local hour of day the reminder fires, 0-23. */
    var notifyHour: Int
        get() = prefs?.getInt(KEY_NOTIFY_HOUR, DEFAULT_NOTIFY_HOUR) ?: DEFAULT_NOTIFY_HOUR
        set(value) { prefs?.edit()?.putInt(KEY_NOTIFY_HOUR, value.coerceIn(0, 23))?.apply() }

    // ───────────────────────── quiz scores ─────────────────────────

    /** Best score from a single round, out of [Quiz.ROUND_SIZE]. */
    val quizBest: Int get() = prefs?.getInt(KEY_QUIZ_BEST, 0) ?: 0

    /** How many rounds have been finished, daily and practice together. */
    val quizRounds: Int get() = prefs?.getInt(KEY_QUIZ_ROUNDS, 0) ?: 0

    var quizProgress: String?
        get() = prefs?.getString(KEY_QUIZ_PROGRESS, null)
        set(value) {
            val p = prefs ?: return
            val e = p.edit()
            if (value == null) e.remove(KEY_QUIZ_PROGRESS) else e.putString(KEY_QUIZ_PROGRESS, value)
            e.apply()
        }

    /** Records a finished round and reports whether it beat the previous best. */
    fun recordQuizRound(score: Int): Boolean {
        val p = prefs ?: return false
        val best = p.getInt(KEY_QUIZ_BEST, 0)
        val improved = score > best
        p.edit()
            .putInt(KEY_QUIZ_ROUNDS, p.getInt(KEY_QUIZ_ROUNDS, 0) + 1)
            .apply { if (improved) putInt(KEY_QUIZ_BEST, score) }
            .apply()
        revision++
        return improved
    }

    // ───────────────────────── the daily quiz ─────────────────────────

    /** Day index of the most recently completed daily round, or [Streak.NEVER]. */
    val dailyQuizDay: Long get() = prefs?.getLong(KEY_DAILY_DAY, Streak.NEVER) ?: Streak.NEVER

    /** Score on that day's round. */
    val dailyQuizScore: Int get() = prefs?.getInt(KEY_DAILY_SCORE, 0) ?: 0

    /** How many distinct days the daily round has been finished — the cleanest retention number. */
    val dailyQuizCount: Int get() = prefs?.getInt(KEY_DAILY_COUNT, 0) ?: 0

    /**
     * Whether [day]'s round is already done.
     *
     * Deliberately `>=` rather than `==`: a recorded day in the future can only come from a
     * clock that moved backwards, and "already done" is the answer that cannot be farmed.
     */
    fun isDailyQuizDone(day: Long = today()): Boolean = dailyQuizDay >= day

    /**
     * Files a finished daily round.
     *
     * [playDay] is the day the round was *drawn for*, not the day it finished — a round started
     * at 23:59 belongs to the questions it asked, so that is the day whose completion it marks.
     * The streak, by contrast, records the day the user actually showed up, so it is credited
     * against today. The two are deliberately allowed to differ.
     */
    fun recordDailyQuiz(playDay: Long, score: Int): DailyQuizOutcome {
        val today = today()
        val before = streakState
        val p = prefs ?: return DailyQuizOutcome(
            firstToday = false, beatBest = false,
            streakBefore = before.current, streak = before.current,
            longest = before.longest, week = before.week(today),
        )
        val firstToday = !isDailyQuizDone(playDay)
        if (firstToday) {
            p.edit()
                .putLong(KEY_DAILY_DAY, maxOf(playDay, dailyQuizDay))
                .putInt(KEY_DAILY_SCORE, score)
                .putInt(KEY_DAILY_COUNT, dailyQuizCount + 1)
                .apply()
        }
        // A replayed round still counts toward the best score and the round tally; only the day
        // itself is protected from being claimed twice.
        val beatBest = recordQuizRound(score)
        val after = markActive(today)
        return DailyQuizOutcome(
            firstToday = firstToday,
            beatBest = beatBest,
            streakBefore = before.current,
            streak = after.current.coerceAtLeast(1),
            longest = after.longest,
            week = after.week(today),
        )
    }

    // ───────────────────────── the streak ─────────────────────────

    /** Days since the epoch in the device's own time zone. Drives the streak and the daily picks. */
    val dayIndex: Long get() = today()

    /**
     * The full streak, history included.
     *
     * [Streak.longest] is floored at the current run on the way out, so a pref file that is
     * partially migrated or simply odd can never render a record lower than the run beside it.
     */
    val streakState: Streak
        get() {
            val p = prefs ?: return Streak.EMPTY
            val lastDay = p.getLong(KEY_LAST_OPEN, Streak.NEVER)
            if (lastDay == Streak.NEVER) return Streak.EMPTY
            val current = p.getInt(KEY_STREAK, 1).coerceAtLeast(1)
            return Streak(
                current = current,
                lastDay = lastDay,
                longest = maxOf(p.getInt(KEY_STREAK_LONGEST, 0), current),
                mask = p.getInt(KEY_ACTIVE_MASK, 1),
            )
        }

    /** Consecutive days the app has been used, today included. Floors at 1. */
    val streak: Int get() = streakState.current.coerceAtLeast(1)

    /** Day index of the most recent active day, or [Streak.NEVER]. Read by the reminder worker. */
    val lastActiveDay: Long get() = prefs?.getLong(KEY_LAST_OPEN, Streak.NEVER) ?: Streak.NEVER

    /** Whole days since the first launch — 0 on the first day. */
    val daysSinceFirstOpen: Int
        get() {
            val p = prefs ?: return 0
            val first = p.getLong(KEY_FIRST_OPEN, today())
            return (today() - first).coerceAtLeast(0L).toInt()
        }

    /**
     * Credits today toward the streak. Idempotent within a day, so calling it on every fact
     * opened is safe.
     *
     * A day is earned by watching a fact **or** by finishing the daily quiz; both funnel through
     * here, so there is only ever one streak and one place a break can be noticed. It is called
     * when something is actually read rather than on launch, so the streak measures the habit
     * the app is trying to build instead of counting bare launches.
     */
    fun recordActivity() {
        markActive(today())
    }

    /**
     * Applies a day to the streak, capturing the loss first if this day ends a run.
     *
     * The capture is the whole reason this is not a one-liner. [Streak.record] resets a broken
     * run to 1, and it is called from the very deep link that brought the user back — so without
     * writing the old length down here, by the time any screen could offer to restore a 12-day
     * streak the 12 no longer exists anywhere.
     */
    private fun markActive(day: Long): Streak {
        val p = prefs ?: return Streak.EMPTY
        val before = streakState
        if (before.breaksOn(day)) {
            p.edit()
                .putInt(KEY_BROKEN_STREAK, before.current)
                .putLong(KEY_BROKEN_DAY, day)
                .apply()
            Analytics.streakBroken(before.current, before.gapOn(day))
        }
        val after = before.record(day)
        if (after == before) return before
        write(p, after)
        if (after.current > before.current) Analytics.streakExtended(after.current)
        return after
    }

    private fun write(p: SharedPreferences, s: Streak) {
        p.edit()
            .putInt(KEY_STREAK, s.current)
            .putLong(KEY_LAST_OPEN, s.lastDay)
            .putInt(KEY_STREAK_LONGEST, s.longest)
            .putInt(KEY_ACTIVE_MASK, s.mask)
            .apply()
        revision++
    }

    // ───────────────────────── streak repair ─────────────────────────

    /** The run that the most recent break cost, or 0. */
    val brokenStreak: Int get() = prefs?.getInt(KEY_BROKEN_STREAK, 0) ?: 0

    /** The day that break was noticed, or [Streak.NEVER]. The offer lives only on that day. */
    val brokenDay: Long get() = prefs?.getLong(KEY_BROKEN_DAY, Streak.NEVER) ?: Streak.NEVER

    val lastRepairDay: Long get() = prefs?.getLong(KEY_LAST_REPAIR, Streak.NEVER) ?: Streak.NEVER

    /** Today's live repair offer, or null. */
    fun repairOffer(): Perks.RepairOffer? {
        val today = today()
        return Perks.repairOffer(
            today = today,
            brokenStreak = brokenStreak,
            brokenDay = brokenDay,
            lastRepairDay = lastRepairDay,
            // The break wrote brokenDay on the day it happened, so the gap is what that day lost.
            gapDays = if (brokenDay == today) 1 else 0,
        )
    }

    /**
     * Restores the run the last break cost, and returns its new length.
     *
     * Uses `commit()` rather than `apply()` on purpose: this runs from an ad callback, and the
     * user may swipe the app away the instant the video closes. A repair that was paid for with
     * an ad and then lost to a background write is the worst outcome this feature has.
     */
    fun repairStreak(): Int {
        val p = prefs ?: return 0
        val today = today()
        val lost = brokenStreak
        val repaired = streakState.repaired(lost, today)
        p.edit()
            .putInt(KEY_STREAK, repaired.current)
            .putLong(KEY_LAST_OPEN, repaired.lastDay)
            .putInt(KEY_STREAK_LONGEST, repaired.longest)
            .putInt(KEY_ACTIVE_MASK, repaired.mask)
            .putLong(KEY_LAST_REPAIR, today)
            .remove(KEY_BROKEN_STREAK)
            .remove(KEY_BROKEN_DAY)
            .remove(KEY_REPAIR_FAILS)
            .remove(KEY_REPAIR_FAIL_DAY)
            .commit()
        revision++
        return repaired.current
    }

    /**
     * Counts a failed attempt to load the repair video and reports whether to give up and grant
     * it anyway. The counter is keyed to the day, so it resets on its own.
     */
    fun noteRepairFailure(): Boolean {
        val p = prefs ?: return false
        val today = today()
        val fails = if (p.getLong(KEY_REPAIR_FAIL_DAY, Streak.NEVER) == today) {
            p.getInt(KEY_REPAIR_FAILS, 0) + 1
        } else {
            1
        }
        p.edit().putLong(KEY_REPAIR_FAIL_DAY, today).putInt(KEY_REPAIR_FAILS, fails).apply()
        return fails >= Perks.FAILURES_BEFORE_GRANT
    }

    /**
     * True exactly once after the app is updated: an existing install whose recorded version is
     * behind [current]. Records [current] either way, so a second call in the same install
     * returns false and the notice cannot reappear.
     *
     * A genuinely fresh install is excluded: nothing is "new" to a new user.
     */
    fun consumeWhatsNew(current: Long): Boolean {
        val p = prefs ?: return false
        val recorded = p.getLong(KEY_LAST_VERSION, Long.MIN_VALUE)
        if (recorded != current) p.edit().putLong(KEY_LAST_VERSION, current).apply()
        return !freshInstall && recorded < current
    }

    /**
     * Days since the epoch in the device's own time zone, so the streak rolls over at local
     * midnight rather than UTC. java.time is not available at minSdk 24 without core library
     * desugaring, hence the offset arithmetic.
     */
    private fun today(): Long {
        val now = System.currentTimeMillis()
        return Math.floorDiv(now + TimeZone.getDefault().getOffset(now), MILLIS_PER_DAY)
    }
}
