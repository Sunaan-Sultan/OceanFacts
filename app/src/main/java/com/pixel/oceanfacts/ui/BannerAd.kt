package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.pixel.oceanfacts.AdManager

/**
 * Anchored adaptive banner, shown only on the Descent tab. Adaptive sizing yields the best fill
 * and eCPM for the device width. [applyNavInset] adds the system navigation-bar padding so the
 * banner clears the gesture area when the app's own bottom bar has scrolled away; otherwise the
 * bar supplies that inset.
 *
 * The size comes from the *large* anchored helper because the per-orientation ones it replaced
 * are deprecated as of Mobile Ads 25. It works out the orientation itself, and is allowed to be
 * taller than the old anchored banner — which is why this lives on the one tab with nothing to
 * read underneath it.
 */
@Composable
fun BannerAd(applyNavInset: Boolean, modifier: Modifier = Modifier) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    val appResumed by rememberAppResumed()
    val adShowing = AdManager.isAdShowing
    var adView by remember { mutableStateOf<AdView?>(null) }

    LaunchedEffect(adView, appResumed, adShowing) {
        val view = adView ?: return@LaunchedEffect
        if (appResumed && !adShowing) view.resume() else view.pause()
    }

    DisposableEffect(Unit) {
        onDispose {
            adView?.destroy()
            adView = null
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .background(Ink.copy(alpha = 0.94f))
            .then(if (applyNavInset) Modifier.navigationBarsPadding() else Modifier),
        factory = { ctx ->
            AdView(ctx).apply {
                // So the container colour shows through anywhere the view itself is not drawing,
                // rather than the AdView painting its own opaque rectangle over the sea. Note
                // that a creative which letterboxes itself inside the slot still shows its own
                // background — Google's fixed-size test banners do exactly that in this slot.
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                adUnitId = AdManager.bannerId
                loadAd(AdRequest.Builder().build())
                adView = this
            }
        },
    )
}
