package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.oceanfacts.core.Fact
import com.pixel.oceanfacts.core.SceneId
import com.pixel.oceanfacts.scenes.RenderScene

/**
 * A frame step longer than this means the app was away, not that the animation is slow, so the
 * clock is held rather than jumped — otherwise coming back from the background teleports every
 * scene to a random moment.
 */
internal const val MAX_FRAME_STEP = 0.05f

@Composable
internal fun rememberAppResumed(): State<Boolean> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val resumed = remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumed.value = true
                Lifecycle.Event.ON_PAUSE -> resumed.value = false
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/**
 * Plays a single scene on its own looping clock, in water coloured for [depthM].
 *
 * When [active] flips on it restarts from 0; when it goes inactive it freezes on the [hero]
 * frame — the moment of the scene chosen to be worth looking at — so a card shows a meaningful
 * still rather than an empty frame 0.
 */
@Composable
fun MiniStage(
    scene: SceneId,
    dur: Float,
    hero: Float,
    depthM: Int,
    active: Boolean,
    paused: Boolean,
    modifier: Modifier = Modifier,
    cover: Boolean = true,
) {
    var time by remember { mutableFloatStateOf(hero) }
    LaunchedEffect(active) { time = if (active) 0f else hero }
    LaunchedEffect(active, paused, dur) {
        if (!active || paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_STEP)
            last = now
            time = (time + dt).let { if (it >= dur) it % dur else it }
        }
    }
    SceneCanvas(modifier, depthM = depthM, cover = cover) {
        WaterColumn(depthM)
        Caustics(time, surfaceLight(depthM))
        MarineSnow(time, depthM)
        DepthVignette(depthM)
        RenderScene(scene, time, dur)
        Bioluminescence(time, depthM)
    }
}

/** Convenience overload for the common case of drawing a fact. */
@Composable
fun MiniStage(
    fact: Fact,
    active: Boolean,
    paused: Boolean,
    modifier: Modifier = Modifier,
    cover: Boolean = true,
) = MiniStage(fact.scene, fact.dur, fact.hero, fact.depthM, active, paused, modifier, cover)

/** Accent-coloured category chip with a leading dot. */
@Composable
fun CategoryPill(label: String, accent: Color, filledBg: Color? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(100))
            .background(filledBg ?: Color.Transparent)
            .border(1.dp, accent, RoundedCornerShape(100))
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(6.dp))
        Text(
            label.uppercase(),
            style = TextStyle(
                fontFamily = OceanFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                letterSpacing = 0.12.em, color = accent,
            ),
        )
    }
}

/** How deep a fact happens, written the way a dive computer would put it. */
internal fun depthLabel(depthM: Int): String = when {
    depthM <= 0 -> "Surface"
    depthM >= 1000 -> "${"%,d".format(depthM)} m"
    else -> "$depthM m"
}

/**
 * A list row: a frozen thumbnail, the category, the title, the subtitle, and the depth.
 *
 * The depth sits on the thumbnail rather than in the text column because it belongs to the
 * picture — the water in that thumbnail is already the colour that number describes.
 */
@Composable
fun FactListCard(
    fact: Fact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSeen: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, fact.accent.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(12.dp))) {
            MiniStage(fact, active = false, paused = true, modifier = Modifier.fillMaxSize())
            if (isSeen) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Ink.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Ico("check", size = 11.dp, color = fact.accent, sw = 2.6f)
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Ink.copy(alpha = 0.62f))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(depthLabel(fact.depthM), style = ts(9f, FontWeight.SemiBold, Color.White, 0.06f))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(fact.cat.uppercase(), style = ts(10.5f, FontWeight.SemiBold, fact.accent, 0.1f))
            Text(
                fact.title,
                style = ts(16.5f, FontWeight.Bold, Color.White),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (fact.sub.isNotEmpty()) {
                Text(
                    fact.sub,
                    style = ts(12.5f, color = Mute),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Ico("chevR", size = 18.dp, color = fact.accent)
        Spacer(Modifier.width(4.dp))
    }
}

/** Translucent bottom navigation with five destinations. */
@Composable
fun BottomNav(tab: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        "zones" to "Zones",
        "deepdive" to "Deep Dive",
        "descent" to "Descent",
        "saved" to "Saved",
        "you" to "You",
    )
    Row(
        modifier
            .fillMaxWidth()
            // Paint the background first so it bleeds down through the navigation-bar inset —
            // the system nav pane then matches the bar's colour.
            .background(Ink.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (id, label) ->
            val on = tab == id
            val color = if (on) Color.White else Color(0xFF7E8F9A)
            val icon = if (id == "you") "profile" else id
            Column(
                Modifier.weight(1f).clickable { onSelect(id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Ico(icon, size = 24.dp, color = color, filled = on && (id == "saved" || id == "deepdive"))
                Spacer(Modifier.height(3.dp))
                Text(
                    label,
                    style = TextStyle(
                        fontFamily = OceanFont,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 10.5.sp, color = color,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
