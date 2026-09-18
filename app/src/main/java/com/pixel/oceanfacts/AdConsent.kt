package com.pixel.oceanfacts

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Google's User Messaging Platform consent flow, which AdMob requires before serving to users
 * under GDPR. Without it an EEA or UK user is served with no legal basis at all.
 *
 * [gather] always calls back, including on failure. Consent that cannot be established simply
 * means non-personalised ads — a form that fails to load must never leave the user staring at a
 * blank screen, so the app carries on either way. The Mobile Ads SDK reads the stored consent
 * itself, so there is nothing to hand it beyond the ordering: initialise it only after this
 * resolves.
 */
object AdConsent {

    private const val TAG = "OceanConsent"

    private var consentInformation: ConsentInformation? = null

    /**
     * Whether the user is entitled to reopen the consent form — true only in regions where a
     * privacy choice applies. Drives the visibility of the You tab's row, so it is Compose state
     * rather than a plain read.
     */
    var isPrivacyOptionsRequired by mutableStateOf(false)
        private set

    /** Runs [onResolved] once consent is settled, whether it was granted, refused or failed. */
    fun gather(activity: Activity, onResolved: () -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity).also { consentInformation = it }

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            // Debug builds can force a geography to see the form; a release build must not.
            .apply {
                if (BuildConfig.DEBUG) {
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .build(),
                    )
                }
            }
            .build()

        // Guards against a form that never calls back leaving ads permanently unloaded.
        var settled = false
        fun settle() {
            if (settled) return
            settled = true
            isPrivacyOptionsRequired = info.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            onResolved()
        }

        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                    settle()
                }
            },
            { error ->
                Log.w(TAG, "Consent info update failed: ${error.message}")
                settle()
            },
        )
    }

    /** Reopens the form from the You tab, for a user who wants to change their mind. */
    fun showPrivacyOptions(activity: Activity, onDone: (String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            onDone(error?.message)
        }
    }
}
