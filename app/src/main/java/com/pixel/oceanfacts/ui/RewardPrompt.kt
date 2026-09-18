package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.pixel.oceanfacts.AdManager
import com.pixel.oceanfacts.RewardResult
import com.pixel.oceanfacts.billing.BillingManager
import com.pixel.oceanfacts.core.Analytics
import kotlinx.coroutines.delay

/** How long to wait for fill before admitting there is no video. */
private const val LOAD_TIMEOUT_MS = 5_000L

private enum class Gate { IDLE, LOADING, UNAVAILABLE }

/**
 * The one place the app asks for an ad in exchange for something, shared by every perk so the
 * ways it can go wrong are handled once.
 *
 * Three rules it exists to enforce:
 *
 * 1. **Someone who paid to remove ads never sees one.** The product they bought is called
 *    "remove ads"; charging them a video for a perk would break that, and locking them out
 *    instead would make the paid tier worse than the free one at something. They are granted it
 *    outright, under exactly the same eligibility rules.
 * 2. **The button is never dead.** Nothing is preloaded at launch, so the first tap usually
 *    arrives before any ad has been fetched. Rather than sitting inert, it warms one and waits
 *    up to [LOAD_TIMEOUT_MS] with a spinner.
 * 3. **A failure says so.** No fill, no network or no consent all land on the same honest
 *    message with a way to try again, never a silent no-op. [onFailed] lets the caller decide
 *    whether to give up and grant the perk anyway.
 */
@Composable
fun RewardPrompt(
    title: String,
    body: String,
    cta: String,
    placement: String,
    accent: Color,
    onGranted: (method: String) -> Unit,
    onDismiss: () -> Unit,
    /** Returns true when the caller would rather grant the perk than lose the user to a no-fill. */
    onFailed: () -> Boolean = { false },
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var gate by remember { mutableStateOf(Gate.IDLE) }
    val adFree = BillingManager.isAdFree

    // Warm one as the prompt appears, so a tap a couple of seconds later is instant.
    LaunchedEffect(placement, adFree) {
        if (!adFree) AdManager.loadRewarded(context, placement)
    }

    fun finish(result: RewardResult) {
        when (result) {
            RewardResult.EARNED -> onGranted(Analytics.Grant.AD)
            // Dismissed early is the user's own decision. Say nothing and leave the offer live.
            RewardResult.ABANDONED -> gate = Gate.IDLE
            RewardResult.FAILED, RewardResult.UNAVAILABLE ->
                if (onFailed()) onGranted(Analytics.Grant.FALLBACK) else gate = Gate.UNAVAILABLE
        }
    }

    fun play() {
        Analytics.rewardedRequested(placement)
        if (adFree) {
            onGranted(Analytics.Grant.AD_FREE)
            return
        }
        if (activity == null) {
            if (onFailed()) onGranted(Analytics.Grant.FALLBACK) else gate = Gate.UNAVAILABLE
            return
        }
        if (AdManager.rewardedReady) {
            AdManager.showRewarded(activity, placement, ::finish)
        } else {
            gate = Gate.LOADING
        }
    }

    // The warm-up. Held here rather than inside play() so the spinner is cancelled correctly if
    // the prompt is dismissed while it is still running.
    LaunchedEffect(gate) {
        if (gate != Gate.LOADING) return@LaunchedEffect
        var ready = AdManager.rewardedReady
        if (!ready) {
            AdManager.loadRewarded(context, placement)
            val until = System.currentTimeMillis() + LOAD_TIMEOUT_MS
            while (!AdManager.rewardedReady && System.currentTimeMillis() < until) delay(150)
            ready = AdManager.rewardedReady
        }
        if (gate != Gate.LOADING) return@LaunchedEffect
        if (ready && activity != null) {
            gate = Gate.IDLE
            AdManager.showRewarded(activity, placement, ::finish)
        } else {
            Analytics.rewardedFailed(placement, "timeout")
            if (onFailed()) onGranted(Analytics.Grant.FALLBACK) else gate = Gate.UNAVAILABLE
        }
    }

    Dialog(onDismissRequest = { if (gate != Gate.LOADING) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(Color(0xFF0A1C26))
                .border(1.dp, accent.copy(alpha = 0.22f), CardShape)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), Color.Transparent))),
                contentAlignment = Alignment.Center,
            ) { Ico("bolt", size = 26.dp, color = accent, filled = true) }

            Text(
                title,
                style = ts(20f, FontWeight.Bold, Color.White, -0.015f, lineHeight = 26f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                when {
                    gate == Gate.UNAVAILABLE -> "No video is available right now. You can try again in a moment."
                    adFree -> body
                    else -> "$body A short video plays first."
                },
                style = ts(14.5f, FontWeight.Light, Mute, lineHeight = 21f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(20.dp))
            when (gate) {
                Gate.LOADING -> Row(
                    Modifier.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Finding a video…", style = ts(14.5f, FontWeight.Medium, Color.White))
                }

                Gate.UNAVAILABLE -> PrimaryButton("Try again", accent) { gate = Gate.LOADING }

                // An ad-free owner is told what they get, not what plays.
                Gate.IDLE -> PrimaryButton(if (adFree) cta else "▶  $cta", accent) { play() }
            }

            if (gate != Gate.LOADING) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(ButtonShape)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No thanks", style = ts(14.5f, FontWeight.Medium, Dim))
                }
            }
        }
    }
}
