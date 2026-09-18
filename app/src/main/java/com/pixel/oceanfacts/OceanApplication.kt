package com.pixel.oceanfacts

import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.pixel.oceanfacts.core.Analytics
import com.pixel.oceanfacts.core.OceanData
import com.pixel.oceanfacts.core.OceanPrefs
import com.pixel.oceanfacts.core.Streak

/**
 * Process-wide setup, so the things every entry point needs are ready wherever the process was
 * started from.
 *
 * It exists chiefly for analytics. The reminder runs in a cold process with no Activity, so a
 * sink attached in `MainActivity.onCreate` would be null exactly when the notification is being
 * posted — silently losing the one event that says whether notifications are being delivered at
 * all. Attaching it here covers both paths.
 */
class OceanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        OceanPrefs.init(this)
        OceanData.init(this)
        attachAnalytics()
    }

    /**
     * Wires Firebase to [Analytics], if it is configured.
     *
     * `initializeApp` returns null when no `google-services.json` has been supplied, which is
     * the normal state of this repo. In that case the sink stays null and events are dropped —
     * the app builds, runs and ships without a Firebase account, and starts reporting the moment
     * one is added.
     */
    private fun attachAnalytics() {
        if (Analytics.sink != null) return
        val app = runCatching { FirebaseApp.initializeApp(this) }.getOrNull()
        if (app == null) {
            Log.i(TAG, "No Firebase config — analytics events stay on the device.")
            return
        }
        val firebase = FirebaseAnalytics.getInstance(this)
        Analytics.sink = Analytics.Sink { event, params ->
            firebase.logEvent(event, Bundle().apply { params.forEach { (k, v) -> putString(k, v) } })
        }
        // Cohorts worth splitting every other number by. Set once per process, from values that
        // are already on disk, so none of this costs a read at an awkward moment.
        firebase.setUserProperty("ad_free", OceanPrefs.adFree.toString())
        firebase.setUserProperty("notify_on", OceanPrefs.notifyEnabled.toString())
        firebase.setUserProperty("streak_bucket", streakBucket(OceanPrefs.streakState))
    }

    /** Buckets rather than the raw number: a user property with hundreds of values is useless. */
    private fun streakBucket(streak: Streak): String = when (streak.current) {
        0 -> "0"
        in 1..2 -> "1-2"
        in 3..6 -> "3-6"
        in 7..29 -> "7-29"
        else -> "30+"
    }

    private companion object {
        const val TAG = "OceanFacts"
    }
}
