package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.Fact

/**
 * Full-screen looping fact player.
 *
 * Deliberately almost bare: a progress hairline, the subtitle, the depth, and a way into the
 * detail sheet. There is no top bar — the scene is the screen, and the system back gesture
 * (handled by OceanFactsApp) is the way out.
 */
@Composable
fun FactScreen(
    fact: Fact,
    paused: Boolean,
    isActive: Boolean,
    onTogglePause: () -> Unit,
    onBack: () -> Unit,
    onLearn: () -> Unit,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
) {
    // Looping clock (0 → dur) driving the top progress bar; restarts per fact, only ticks while
    // this page is the one being looked at.
    var clock by remember(fact.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(fact.id, isActive, paused, fact.dur) {
        if (!isActive || paused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_STEP)
            clock = (clock + dt).let { if (it >= fact.dur) it % fact.dur else it }
            last = now
        }
    }
    val progress = (clock / fact.dur).coerceIn(0f, 1f)

    Box(
        Modifier.fillMaxSize().background(Ink).clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
        ) { onTogglePause() },
    ) {
        MiniStage(fact, active = isActive, paused = paused, modifier = Modifier.fillMaxSize(), cover = false)

        // Thin progress line across the top, filling in the fact's accent colour.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.14f)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(fact.accent))
        }

        // The depth, top right: the one number that says where in the sea you are standing.
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 14.dp, end = 16.dp)
                .clip(RoundedCornerShape(100))
                .background(Ink.copy(alpha = 0.5f))
                .border(1.dp, fact.accent.copy(alpha = 0.3f), RoundedCornerShape(100))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(fact.accent))
            Spacer(Modifier.width(7.dp))
            Text(depthLabel(fact.depthM), style = ts(12f, FontWeight.SemiBold, Color.White, 0.06f))
        }

        if (paused) {
            Box(
                Modifier.align(Alignment.Center).size(74.dp).clip(CircleShape).background(Ink.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) { Ico("play", size = 30.dp, color = Color.White) }
        }

        // Scrim, so the caption stays readable over a bright sunlit-zone scene.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(320.dp)
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Ink.copy(alpha = 0.86f))),
        )

        // Caption and Learn more. Taps here do not toggle play.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 36.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {},
        ) {
            Text(fact.sub, style = ts(16f, FontWeight.Light, Color(0xFFCFE0E8)))
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(100))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(100))
                    .clickable(onClick = onLearn)
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Learn more", style = ts(14.5f, FontWeight.SemiBold, Color.White))
                Spacer(Modifier.width(8.dp))
                Ico("chevUp", size = 16.dp, color = Color.White, sw = 2.2f)
            }
        }
    }
}
