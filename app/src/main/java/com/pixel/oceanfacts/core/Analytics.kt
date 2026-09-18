package com.pixel.oceanfacts.core

import android.util.Log
import com.pixel.oceanfacts.BuildConfig

/**
 * The app's event taxonomy, and the one place any of it is named.
 *
 * Ocean Facts ships ads and a paid ad-free upgrade, so it needs to be able to answer which
 * facts are actually watched, where a session ends, and whether a change to the ad cap helped
 * or hurt. Every call site is wired here so those questions stay answerable from one file.
 *
 * **Nothing leaves the device until a provider is attached.** That is deliberately the last
 * step, because it is the only part that needs an account: drop `app/google-services.json` in
 * and [com.pixel.oceanfacts.OceanApplication] attaches Firebase to [sink] on the next launch.
 * Until then a debug build logs to logcat, so the call sites can be verified without one.
 */
object Analytics {

    private const val TAG = "OceanAnalytics"

    fun interface Sink {
        fun log(event: String, params: Map<String, String>)
    }

    /** Null until a provider is attached; events are dropped rather than queued. */
    var sink: Sink? = null

    /** Which slot an ad was asked for, so revenue can be read per placement. */
    object Placement {
        const val STREAK_REPAIR = "streak_repair"
        const val QUIZ_HINT = "quiz_hint"
        const val QUIZ_ROUND_END = "quiz_round_end"
        const val NAVIGATION = "navigation"
        const val FACT_SCROLL = "fact_scroll"
    }

    /** How a perk was granted — paid for with an ad, owned outright, or given up on. */
    object Grant {
        const val AD = "ad"
        const val AD_FREE = "ad_free"
        const val FALLBACK = "fallback"
    }

    /** Where a fact was opened from, so drop-off can be attributed to a surface. */
    object Source {
        const val TODAY = "today"
        const val ZONE = "zone"
        const val DEEP_DIVE = "deep_dive"
        const val SAVED = "saved"
        const val SEARCH = "search"
        const val QUIZ = "quiz"
        const val DEEP_LINK = "deep_link"
    }

    fun factView(fact: Fact, source: String) =
        log("fact_view", "fact_id" to fact.id, "zone" to fact.zone, "source" to source)

    fun zoneOpen(zoneId: String) = log("zone_open", "zone" to zoneId)

    fun factSave(fact: Fact, saved: Boolean) =
        log("fact_save", "fact_id" to fact.id, "saved" to saved.toString())

    fun factShare(fact: Fact) = log("share_fact", "fact_id" to fact.id, "zone" to fact.zone)

    fun search(query: String, resultCount: Int) =
        log("search", "length" to query.length.toString(), "results" to resultCount.toString())

    fun reminderToggled(enabled: Boolean, hour: Int) =
        log("reminder_toggled", "enabled" to enabled.toString(), "hour" to hour.toString())

    fun quizStarted(daily: Boolean) = log("quiz_started", "daily" to daily.toString())

    fun quizFinished(score: Int, total: Int) =
        log("quiz_finished", "score" to score.toString(), "total" to total.toString())

    fun dailyQuizFinished(score: Int, total: Int, streak: Int, hintUsed: Boolean) =
        log(
            "daily_quiz_finished",
            "score" to score.toString(),
            "total" to total.toString(),
            "streak" to streak.toString(),
            "hint_used" to hintUsed.toString(),
        )

    // The streak pair is what answers the question the whole daily loop was built to answer:
    // whether a run actually brings people back, and how long they last before one lapses.
    fun streakExtended(days: Int) = log("streak_extended", "days" to days.toString())

    fun streakBroken(days: Int, gapDays: Int) =
        log("streak_broken", "days" to days.toString(), "gap_days" to gapDays.toString())

    fun streakRepairOffered(days: Int) = log("streak_repair_offered", "days" to days.toString())

    fun streakRepaired(days: Int, method: String) =
        log("streak_repaired", "days" to days.toString(), "method" to method)

    fun hintUsed(method: String) = log("hint_used", "method" to method)

    fun interstitialShown(placement: String) = log("interstitial_shown", "placement" to placement)

    fun interstitialFailed(placement: String, reason: String) =
        log("interstitial_failed", "placement" to placement, "reason" to reason)

    // Requested → shown → earned, per placement, is the rewarded funnel; AdMob reports the
    // money against the same two units, so the two halves can be read together.
    fun rewardedRequested(placement: String) = log("rewarded_requested", "placement" to placement)

    fun rewardedShown(placement: String) = log("rewarded_shown", "placement" to placement)

    fun rewardedEarned(placement: String) = log("rewarded_earned", "placement" to placement)

    fun rewardedAbandoned(placement: String) = log("rewarded_abandoned", "placement" to placement)

    fun rewardedFailed(placement: String, reason: String) =
        log("rewarded_failed", "placement" to placement, "reason" to reason)

    fun notificationPosted(kind: String) = log("notification_posted", "kind" to kind)

    fun notificationOpened(kind: String) = log("notification_opened", "kind" to kind)

    /**
     * Named `iap_purchase`, not `purchase`: Firebase reserves `purchase` for a recommended
     * event with a currency/value schema, and logging a bare outcome against it corrupts the
     * revenue reports it feeds.
     */
    fun purchase(outcome: String) = log("iap_purchase", "outcome" to outcome)

    private fun log(event: String, vararg params: Pair<String, String>) {
        val map = params.toMap()
        sink?.log(event, map)
        if (BuildConfig.DEBUG) Log.d(TAG, "$event $map")
    }
}
