package com.pixel.oceanfacts

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.pixel.oceanfacts.billing.BillingManager
import com.pixel.oceanfacts.core.Analytics

/** How a rewarded ad ended. Four values rather than a boolean, because the answers differ. */
enum class RewardResult {
    /** The video was watched far enough to earn the reward. Grant the perk. */
    EARNED,

    /** Shown, then dismissed early. The user's own choice — say nothing, leave the offer live. */
    ABANDONED,

    /** It broke after we committed to showing it. Our fault. */
    FAILED,

    /** Nothing to show: no fill, no consent, offline, or the SDK is not up yet. Our fault. */
    UNAVAILABLE,
}

object AdManager {

    // Shared frequency cap so every trigger point competes for the same slot and ads never
    // stack. These are the knobs that trade revenue against how the app feels.
    private const val ACTIONS_PER_AD = 4         // show on every Nth eligible action
    private const val MIN_INTERVAL_MS = 100_000L // ...but never more often than this
    private const val LAUNCH_GRACE_MS = 90_000L  // ...and never this soon after a launch

    private const val QUIZ_MIN_INTERVAL_MS = 120_000L

    private const val INTERSTITIAL_BACKOFF_MS = 30_000L

    private var actionsSinceAd = 0
    private var lastShownAt = 0L
    private var sessionStartedAt = System.currentTimeMillis()

    // Debug builds must never touch a live unit: a developer clicking real ads is what gets an
    // AdMob account suspended.
    private val useTestAds: Boolean get() = BuildConfig.DEBUG

    // Every REAL_* id below is still Google's public test unit. Replace each with the real one
    // from AdMob before shipping — and the app id in AndroidManifest.xml with it.
    private const val REAL_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

    /** A separate unit for the quiz, so its revenue reads on its own. Unset until created. */
    private const val REAL_INTERSTITIAL_QUIZ_ID = "ca-app-pub-3940256099942544/0000000000"

    private const val REAL_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"

    // A unit per placement, so AdMob reports impressions, revenue and eCPM for streak repair
    // and for the quiz hint separately without any work on our side.
    private const val REAL_REWARDED_REPAIR_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val REAL_REWARDED_HINT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"

    /** What an unfilled real id looks like, so a release build never requests a dead unit. */
    private const val UNSET_UNIT = "/0000000000"

    /** Rewarded ads expire; anything older than this is reloaded rather than shown. */
    private const val REWARDED_TTL_MS = 50 * 60_000L

    /** How long to sit on a failed load before trying again, so a dead network is not hammered. */
    private const val REWARDED_BACKOFF_MS = 30_000L

    val bannerId: String
        get() = if (useTestAds) TEST_BANNER_ID else REAL_BANNER_ID

    val adsEnabled: Boolean get() = !BillingManager.isAdFree

    var isAdShowing by mutableStateOf(false)
        private set

    /**
     * Whether the Mobile Ads SDK has finished starting.
     *
     * It is deliberately not initialised until UMP consent resolves, so anything that requests
     * an ad before this is true is quietly dropped. Interstitials survive that because they are
     * retried on the next navigation; a rewarded ad is asked for once, by hand, so it has to
     * know.
     */
    var sdkReady = false
        private set

    private class InterstitialSlot(private val unitId: String) {
        private var ad: InterstitialAd? = null
        private var loadInFlight = false
        private var failedAt = 0L

        val ready: Boolean get() = ad != null

        fun load(context: Context) {
            if (!adsEnabled || !sdkReady || loadInFlight || ad != null) return
            if (System.currentTimeMillis() - failedAt < INTERSTITIAL_BACKOFF_MS) return
            loadInFlight = true
            InterstitialAd.load(
                context.applicationContext,
                unitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(loaded: InterstitialAd) {
                        loadInFlight = false
                        ad = loaded
                        failedAt = 0L
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadInFlight = false
                        ad = null
                        failedAt = System.currentTimeMillis()
                    }
                },
            )
        }

        fun show(activity: android.app.Activity, placement: String, onDismissed: () -> Unit) {
            val showing = ad
            if (showing == null) {
                onDismissed()
                return
            }
            ad = null
            isAdShowing = true
            showing.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    Analytics.interstitialShown(placement)
                }

                override fun onAdDismissedFullScreenContent() {
                    isAdShowing = false
                    load(activity)
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    isAdShowing = false
                    Analytics.interstitialFailed(placement, "show_${error.code}")
                    load(activity)
                    onDismissed()
                }
            }
            showing.show(activity)
        }

        fun discard() {
            ad = null
            loadInFlight = false
            failedAt = 0L
        }
    }

    private val browseSlot =
        InterstitialSlot(if (useTestAds) TEST_INTERSTITIAL_ID else REAL_INTERSTITIAL_ID)

    private val quizSlot =
        if (useTestAds || REAL_INTERSTITIAL_QUIZ_ID.endsWith(UNSET_UNIT)) browseSlot
        else InterstitialSlot(REAL_INTERSTITIAL_QUIZ_ID)

    /** Called from the consent callback, once MobileAds.initialize has actually finished. */
    fun onAdsInitialized(context: Context) {
        sdkReady = true
        browseSlot.load(context)
    }

    /**
     * Restarts the launch grace period. Called once from MainActivity.onCreate.
     *
     * [lastShownAt] is deliberately left alone. onCreate runs again on every configuration
     * change, and clearing it there would let an interstitial fire seconds after a rewarded ad
     * simply because the user rotated the phone. [LAUNCH_GRACE_MS] already covers a genuine
     * cold start, which is the only thing this needs to protect.
     */
    fun startSession() {
        sessionStartedAt = System.currentTimeMillis()
        actionsSinceAd = 0
        isAdShowing = false
    }

    fun loadInterstitial(context: Context) = browseSlot.load(context)

    fun preloadQuizInterstitial(context: Context) = quizSlot.load(context)

    /**
     * Time-only trigger: shows an interstitial if at least [minIntervalMs] has elapsed since the
     * last ad, ignoring the action count. Used for fact scrolling, where we want an ad roughly
     * every [minIntervalMs] regardless of how many facts were swiped. Shares [lastShownAt] with
     * the other triggers so ads never bunch up. Always invokes [onProceed].
     */
    fun maybeShowInterstitialAfter(
        activity: android.app.Activity,
        minIntervalMs: Long,
        onProceed: () -> Unit,
    ) {
        val now = System.currentTimeMillis()
        if (browseSlot.ready && quotaAllows(now, minIntervalMs)) {
            record(now)
            browseSlot.show(activity, Analytics.Placement.FACT_SCROLL, onProceed)
        } else {
            browseSlot.load(activity)
            onProceed()
        }
    }

    /**
     * Entry point for all in-app triggers. Applies the shared frequency cap and shows an
     * interstitial only when the action count, time interval and launch grace period all allow
     * it. Always invokes [onProceed] so navigation is never blocked.
     *
     * Actions taken while no ad is loaded are not counted: otherwise a fill outage banks up
     * credits and the first ad to load fires immediately.
     */
    fun maybeShowInterstitial(activity: android.app.Activity, onProceed: () -> Unit) {
        if (!browseSlot.ready) {
            browseSlot.load(activity)
            onProceed()
            return
        }
        actionsSinceAd += 1
        val now = System.currentTimeMillis()
        if (actionsSinceAd >= ACTIONS_PER_AD && quotaAllows(now, MIN_INTERVAL_MS)) {
            record(now)
            browseSlot.show(activity, Analytics.Placement.NAVIGATION, onProceed)
        } else {
            onProceed()
        }
    }

    fun maybeShowQuizInterstitial(activity: android.app.Activity, onProceed: () -> Unit) {
        val now = System.currentTimeMillis()
        if (quizSlot.ready && quotaAllows(now, QUIZ_MIN_INTERVAL_MS)) {
            record(now)
            quizSlot.show(activity, Analytics.Placement.QUIZ_ROUND_END, onProceed)
        } else {
            quizSlot.load(activity)
            onProceed()
        }
    }

    private fun quotaAllows(now: Long, minIntervalMs: Long): Boolean =
        adsEnabled &&
            !isAdShowing &&
            now - sessionStartedAt >= LAUNCH_GRACE_MS &&
            now - lastShownAt >= minIntervalMs

    private fun record(now: Long) {
        actionsSinceAd = 0
        lastShownAt = now
    }

    /** Drops any cached ad the moment the user buys the entitlement. */
    fun discard() {
        browseSlot.discard()
        quizSlot.discard()
        rewardedAd = null
        rewardedReady = false
    }

    // ───────────────────────── rewarded ─────────────────────────

    private var rewardedAd: RewardedAd? = null
    private var rewardedLoadedAt = 0L
    private var rewardedLoadInFlight = false
    private var rewardedFailedAt = 0L
    private var rewardedWaiters = mutableListOf<(Boolean) -> Unit>()

    /** Compose state so a "watch" button can light up the moment fill arrives. */
    var rewardedReady by mutableStateOf(false)
        private set

    private fun rewardedId(placement: String): String = when {
        useTestAds -> TEST_REWARDED_ID
        placement == Analytics.Placement.QUIZ_HINT -> REAL_REWARDED_HINT_ID
        else -> REAL_REWARDED_REPAIR_ID
    }

    /**
     * Guards a release build shipped before the AdMob units were created: requesting an invalid
     * unit id logs errors forever and never fills, so it is better not to ask.
     */
    private fun rewardedConfigured(placement: String): Boolean =
        useTestAds || !rewardedId(placement).endsWith(UNSET_UNIT)

    private fun rewardedFresh(now: Long): Boolean =
        rewardedAd != null && now - rewardedLoadedAt < REWARDED_TTL_MS

    /**
     * Warms a rewarded ad, calling [onReady] with whether one is available.
     *
     * Never called at launch: the overwhelming majority of sessions never reach a reward, and a
     * request per session for an ad nobody asks for is wasted fill. The two surfaces that can
     * offer one warm it as they appear instead.
     */
    fun loadRewarded(
        context: Context,
        placement: String = Analytics.Placement.STREAK_REPAIR,
        onReady: ((Boolean) -> Unit)? = null,
    ) {
        val now = System.currentTimeMillis()
        if (!adsEnabled || !sdkReady || !rewardedConfigured(placement)) {
            onReady?.invoke(false)
            return
        }
        if (rewardedFresh(now)) {
            onReady?.invoke(true)
            return
        }
        onReady?.let { rewardedWaiters += it }
        if (rewardedLoadInFlight) return
        if (now - rewardedFailedAt < REWARDED_BACKOFF_MS) {
            drainRewardedWaiters(false)
            return
        }
        rewardedLoadInFlight = true
        RewardedAd.load(
            context,
            rewardedId(placement),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedLoadInFlight = false
                    rewardedAd = ad
                    rewardedLoadedAt = System.currentTimeMillis()
                    rewardedReady = true
                    drainRewardedWaiters(true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedLoadInFlight = false
                    rewardedAd = null
                    rewardedReady = false
                    rewardedFailedAt = System.currentTimeMillis()
                    Analytics.rewardedFailed(placement, "no_fill_${error.code}")
                    drainRewardedWaiters(false)
                }
            },
        )
    }

    private fun drainRewardedWaiters(ready: Boolean) {
        val waiting = rewardedWaiters
        rewardedWaiters = mutableListOf()
        waiting.forEach { it(ready) }
    }

    /**
     * Plays a rewarded ad for [placement] and reports how it ended.
     *
     * [record] runs *before* the ad is shown rather than after. It is the same call the
     * interstitial triggers make, so charging the rewarded against the shared cap up front is
     * what stops an interstitial firing on the very next navigation — the user would otherwise
     * watch a video by choice and immediately be handed one they did not choose. Doing it first
     * also covers the failure path, where the ad may already have drawn something.
     *
     * The reward is banked in the SDK's own callback but only delivered on dismissal, so the UI
     * never changes behind the ad.
     */
    fun showRewarded(
        activity: android.app.Activity,
        placement: String,
        onResult: (RewardResult) -> Unit,
    ) {
        val now = System.currentTimeMillis()
        val ad = rewardedAd
        if (isAdShowing || !sdkReady || ad == null || !rewardedFresh(now)) {
            loadRewarded(activity, placement)
            Analytics.rewardedFailed(placement, if (!sdkReady) "sdk_not_ready" else "not_loaded")
            onResult(RewardResult.UNAVAILABLE)
            return
        }
        var earned = false
        isAdShowing = true
        record(now)
        rewardedAd = null
        rewardedReady = false
        ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Analytics.rewardedShown(placement)
            }

            override fun onAdDismissedFullScreenContent() {
                isAdShowing = false
                if (earned) {
                    onResult(RewardResult.EARNED)
                } else {
                    Analytics.rewardedAbandoned(placement)
                    onResult(RewardResult.ABANDONED)
                }
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                isAdShowing = false
                Analytics.rewardedFailed(placement, "show_${error.code}")
                onResult(RewardResult.FAILED)
            }
        }
        ad.show(activity) {
            earned = true
            Analytics.rewardedEarned(placement)
        }
    }
}
