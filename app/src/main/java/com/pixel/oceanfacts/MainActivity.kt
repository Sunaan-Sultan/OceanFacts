package com.pixel.oceanfacts

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.pixel.oceanfacts.billing.BillingManager
import com.pixel.oceanfacts.core.DeepLink
import com.pixel.oceanfacts.core.OceanData
import com.pixel.oceanfacts.core.OceanPrefs
import com.pixel.oceanfacts.notify.DailyReminder
import com.pixel.oceanfacts.ui.UpdateCheckSplash
import com.pixel.oceanfacts.ui.UpdateRequiredScreen
import com.pixel.oceanfacts.ui.theme.OceanFactsTheme

class MainActivity : ComponentActivity() {

    /** What the UI is allowed to show. */
    private enum class Gate {
        /** Play has not answered yet — show nothing, so the app is never briefly usable. */
        CHECKING,

        /** No forced update outstanding. */
        ALLOWED,

        /** A required update is outstanding; [UpdateRequiredScreen] replaces the app. */
        BLOCKED,
    }

    private lateinit var appUpdateManager: AppUpdateManager

    private var gate by mutableStateOf(Gate.CHECKING)

    /** True while Play's UI is up, so the two callers below cannot launch it twice. */
    private var updateFlowLaunched = false

    /**
     * A fact id from an `oceanfacts://fact/<id>` intent, waiting to be opened. Held as state
     * rather than acted on directly so it survives the update gate: a deep link that arrives
     * while Play is still being queried is honoured once the gate opens, instead of being
     * dropped along with the splash.
     */
    private var pendingFactId by mutableStateOf<String?>(null)

    /** Same idea as [pendingFactId], for the reminder's "your streak ends tonight" link. */
    private var pendingQuiz by mutableStateOf(false)

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * A hung update check must not brick the app, so the gate opens if Play has not answered in
     * [CHECK_TIMEOUT_MS]. The check runs again on the next launch.
     */
    private val checkTimeout = Runnable {
        if (gate == Gate.CHECKING) {
            Log.w(TAG, "Update check timed out after ${CHECK_TIMEOUT_MS}ms; letting the app run.")
            gate = Gate.ALLOWED
        }
    }

    // Play's full-screen immediate-update UI closed. A non-OK result means the user backed out
    // of (or the flow failed) a *forced* update, so the gate stays down and the screen behind it
    // offers the only way on.
    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result: ActivityResult ->
        updateFlowLaunched = false
        if (result.resultCode != RESULT_OK) {
            Log.w(TAG, "Immediate update flow did not complete (code=${result.resultCode}).")
            gate = Gate.BLOCKED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OceanData.init(this)
        OceanPrefs.init(this)
        pendingFactId = DeepLink.factIdFrom(intent)
        pendingQuiz = DeepLink.isQuizLink(intent)
        // Re-arm the reminder: work does not survive an app update or a "force stop".
        DailyReminder.sync(this)
        BillingManager.init(this)
        AdManager.startSession()

        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForImmediateUpdate()

        enableEdgeToEdge()
        setContent {
            // The app styles itself, but the handful of Material components it does use — the
            // dialogs, the switch, the spinner — take their colours from here.
            OceanFactsTheme {
                when (gate) {
                    Gate.CHECKING -> UpdateCheckSplash()
                    Gate.BLOCKED -> UpdateRequiredScreen(
                        onUpdate = { AppActions.openPlayListing(this) },
                    )
                    Gate.ALLOWED -> OceanFactsApp(
                        pendingFactId = pendingFactId,
                        onPendingFactConsumed = { pendingFactId = null },
                        pendingQuiz = pendingQuiz,
                        onPendingQuizConsumed = { pendingQuiz = false },
                    )
                }
            }
        }

        // Ads come last, and only once the window is real.
        //
        // The Mobile Ads SDK builds a WebView against this Activity as it starts, and doing that
        // before setContent leaves the Activity with no content container at all — the decor is
        // generated without one and the next thing to touch it throws. It only ever showed up on
        // the second launch, because the first has to fetch the consent form over the network
        // and so returns before this line; once consent is cached the callback runs inline.
        //
        // The ordering the other way round still matters: the SDK must not start before consent
        // is settled, or the first requests go out with no legal basis. AdConsent always calls
        // back, so ads are never stranded, and the load happens inside the initialize callback
        // rather than beside it because a request made before the SDK is up is simply dropped.
        if (AdManager.adsEnabled) {
            AdConsent.gather(this) {
                MobileAds.initialize(this) { AdManager.onAdsInitialized(this) }
            }
        }
    }

    // launchMode is singleTop, so a deep link tapped while the app is already running arrives
    // here rather than through a fresh onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLink.factIdFrom(intent)?.let { pendingFactId = it }
        if (DeepLink.isQuizLink(intent)) pendingQuiz = true
    }

    override fun onResume() {
        super.onResume()
        // Catches a purchase, refund or restore that happened outside the app.
        BillingManager.refreshPurchases()
        if (!::appUpdateManager.isInitialized) return
        // If an immediate update was already running (e.g. the app was killed mid-update), Play
        // reports it as in progress — resume the flow so the user cannot slip past it.
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                block(info)
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(checkTimeout)
        super.onDestroy()
    }

    private fun checkForImmediateUpdate() {
        mainHandler.postDelayed(checkTimeout, CHECK_TIMEOUT_MS)
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                mainHandler.removeCallbacks(checkTimeout)
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    block(info)
                } else {
                    gate = Gate.ALLOWED
                }
            }
            .addOnFailureListener { e ->
                mainHandler.removeCallbacks(checkTimeout)
                // No Play Store / offline / sideloaded build — nothing to enforce.
                Log.w(TAG, "App update check failed: ${e.message}")
                gate = Gate.ALLOWED
            }
    }

    /** Closes the gate *before* launching the flow, so a launch that fails still blocks. */
    private fun block(info: AppUpdateInfo) {
        gate = Gate.BLOCKED
        startImmediateUpdate(info)
    }

    private fun startImmediateUpdate(info: AppUpdateInfo) {
        if (updateFlowLaunched) return
        updateFlowLaunched = true
        runCatching {
            appUpdateManager.startUpdateFlowForResult(
                info,
                updateLauncher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        }.onFailure { e ->
            updateFlowLaunched = false
            Log.w(TAG, "Could not start immediate update: ${e.message}")
            // The gate is already down; its button is the retry.
        }
    }

    private companion object {
        const val TAG = "OceanFactsUpdate"
        const val CHECK_TIMEOUT_MS = 2_500L
    }
}
