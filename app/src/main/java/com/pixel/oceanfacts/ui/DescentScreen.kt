package com.pixel.oceanfacts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.DescentStop
import com.pixel.oceanfacts.core.OceanData
import com.pixel.oceanfacts.core.StopKind
import com.pixel.oceanfacts.core.swell
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** The deepest surveyed point on Earth, and therefore the bottom of this screen. */
private const val MAX_DEPTH = 10935f

/**
 * Depth is spread by a power law rather than linearly.
 *
 * On a linear axis the first two hundred metres — which contain the surface, a scuba limit, the
 * end of sunlight and a free-diving record — occupy under two per cent of the track and pile on
 * top of each other, while the four kilometres of empty water above the trench floor get most of
 * the screen. This gives the shallows room without lying about the order of anything.
 */
private const val CURVE = 0.55f

private fun posOf(depthM: Int): Float = (depthM / MAX_DEPTH).coerceIn(0f, 1f).pow(CURVE)

private fun depthAt(pos: Float): Float = MAX_DEPTH * pos.coerceIn(0f, 1f).pow(1f / CURVE)

/** How far, in dp, the camera travels between the surface and the bottom. */
private const val TRACK_DP = 9000f

/** Seconds for the whole descent, and how long it waits on the bottom before starting again. */
private const val DESCENT_SECONDS = 38f
private const val END_HOLD_SECONDS = 3.2f

/**
 * The surface-to-Challenger-Deep fly-through.
 *
 * The water behind it is coloured from the camera's own depth, so the screen genuinely gets
 * darker as it falls — by the time the Titanic goes past there is nothing left but the marker
 * lights. Tap to hold, drag to go back up.
 */
@Composable
fun DescentScreen(externalPaused: Boolean = false) {
    val stops = OceanData.descent
    if (stops.isEmpty()) return

    var clock by remember { mutableFloatStateOf(0f) }
    var pos by remember { mutableFloatStateOf(0f) }
    var paused by remember { mutableStateOf(false) }
    var endHold by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(externalPaused) {
        if (externalPaused) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_FRAME_STEP)
            last = now
            clock += dt
            if (!paused) {
                if (pos >= 1f) {
                    endHold += dt
                    if (endHold >= END_HOLD_SECONDS) {
                        pos = 0f
                        endHold = 0f
                    }
                } else {
                    pos = (pos + dt / DESCENT_SECONDS).coerceAtMost(1f)
                    endHold = 0f
                }
            }
        }
    }

    val depth = depthAt(pos)
    val depthM = depth.toInt()
    val (topC, midC, botC) = waterTone(depthM)
    val light = surfaceLight(depthM)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(botC)
            .pointerInput(Unit) { detectTapGestures { paused = !paused } }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { paused = true; endHold = 0f },
                    onVerticalDrag = { change, drag ->
                        change.consume()
                        // Dragging up pulls the sea past you downward, i.e. you descend.
                        pos = (pos - drag / (TRACK_DP * density)).coerceIn(0f, 1f)
                    },
                )
            },
    ) {
        val screenH = maxHeight
        val centreY = screenH / 2f

        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    0f to topC,
                    0.46f to midC,
                    1f to botC,
                    startY = 0f,
                    endY = size.height,
                ),
            )
        }

        Caustics(clock, light)

        // Particles streaming upward past the camera, at a rate set by how fast it is falling.
        Canvas(Modifier.fillMaxSize()) {
            var s = 5711L
            val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
            val weight = (0.3f + depthM / 1600f).coerceIn(0.3f, 1f)
            repeat(130) {
                val px = rnd()
                val py0 = rnd()
                val r = (1.2f + rnd() * 3.4f) * density
                val ph = rnd() * 6.28f
                // Tied to pos, not to the clock, so scrubbing by hand moves them too.
                val y = ((py0 - pos * 9f + 10f) % 1f) * size.height
                val x = px * size.width + sin(clock * 0.7f + ph) * 6f * density
                drawCircle(
                    Color(0xFFDDF2FF).copy(alpha = (0.12f + rnd() * 0.42f) * weight),
                    radius = r,
                    center = Offset(x, y),
                )
            }
        }

        // The spine the whole descent hangs off.
        val spineFrac = 0.40f
        Canvas(Modifier.fillMaxSize()) {
            drawLine(
                Color.White.copy(alpha = 0.16f),
                Offset(size.width * spineFrac, 0f),
                Offset(size.width * spineFrac, size.height),
                strokeWidth = 2f * density,
            )
        }

        stops.forEachIndexed { i, stop ->
            val dy = (posOf(stop.depthM) - pos) * TRACK_DP
            // Two screens of slack either side, so nothing pops in at the edge of the frame.
            if (abs(dy) > screenH.value + 400f) return@forEachIndexed
            DescentMarker(
                stop = stop,
                onLeft = i % 2 == 0,
                spineFrac = spineFrac,
                yOffsetDp = dy,
                centreY = centreY.value,
                clock = clock,
            )
        }

        // The camera line, and the number that is the whole point of the screen.
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height / 2f
            drawLine(
                Color(0xFF3FE0D8).copy(alpha = 0.55f),
                Offset(0f, y),
                Offset(size.width, y),
                strokeWidth = 1.5f * density,
                cap = StrokeCap.Round,
            )
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                "%,d".format(depthM),
                style = ts(46f, FontWeight.Bold, Color.White, -0.03f),
            )
            Text("METRES DOWN", style = ts(10.5f, FontWeight.Bold, QuizAccent, 0.2f))
        }

        // Where you are in the whole eleven kilometres, as a bar down the right-hand edge.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .width(3.dp)
                .height(screenH * 0.42f)
                .clip(RoundedCornerShape(100))
                .background(Color.White.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier
                    .offset(y = screenH * 0.42f * pos - 9.dp)
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(100))
                    .background(QuizAccent),
            )
        }

        // In a pill rather than bare text: markers slide right past this spot, and two pieces of
        // pale type on top of each other is unreadable for the second or two it takes to pass.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(100))
                .background(Ink.copy(alpha = 0.66f))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                if (paused) "❙❙  Tap to resume  ·  Drag to explore" else "Tap to hold  ·  Drag to explore",
                style = ts(12f, FontWeight.Medium, Color.White.copy(alpha = 0.62f), 0.06f),
                textAlign = TextAlign.Center,
            )
        }

        if (pos >= 1f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .clip(RoundedCornerShape(100))
                    .background(Ink.copy(alpha = 0.7f))
                    .border(1.dp, QuizAccent.copy(alpha = 0.3f), RoundedCornerShape(100))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text("The bottom of the world", style = ts(13f, FontWeight.SemiBold, QuizAccent))
            }
        }
    }
}

/**
 * One stop on the way down: a lamp on the spine, a tick out to its side, and its name.
 *
 * Markers alternate sides so the pairs that sit a hundred metres apart — the average ocean floor
 * and the Titanic, the deepest fish and Everest — do not land on top of one another.
 */
@Composable
private fun DescentMarker(
    stop: DescentStop,
    onLeft: Boolean,
    spineFrac: Float,
    yOffsetDp: Float,
    centreY: Float,
    clock: Float,
) {
    val near = 1f - (abs(yOffsetDp) / 900f).coerceIn(0f, 1f)
    val alpha = (0.15f + 0.85f * near).coerceIn(0f, 1f)
    val lamp = when (stop.kind) {
        StopKind.SEAFLOOR, StopKind.LANDMARK -> 22f
        StopKind.WRECK -> 20f
        else -> 16f
    }
    val bob = swell(clock, 0.4f, stop.depthM * 0.001f) * 3f

    Box(Modifier.fillMaxSize()) {
        // The tick, the lamp and the label are laid out in one full-width row so the spine
        // fraction is the only thing that decides where the column sits.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (centreY + yOffsetDp + bob - 34f).dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onLeft) {
                Column(
                    Modifier.weight(spineFrac),
                    horizontalAlignment = Alignment.End,
                ) {
                    MarkerLabel(stop, alpha, alignEnd = true)
                }
                Spacer(Modifier.width(10.dp))
                Lamp(stop, lamp, alpha)
                Spacer(Modifier.weight(1f - spineFrac))
            } else {
                Spacer(Modifier.weight(spineFrac))
                Lamp(stop, lamp, alpha)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f - spineFrac)) {
                    MarkerLabel(stop, alpha, alignEnd = false)
                }
            }
        }
    }
}

@Composable
private fun Lamp(stop: DescentStop, size: Float, alpha: Float) {
    Box(contentAlignment = Alignment.Center) {
        if (stop.glow != Color.Transparent) {
            Box(
                Modifier
                    .size((size * 3.4f).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(stop.glow.copy(alpha = stop.glow.alpha * alpha), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
        }
        Box(Modifier.size(size.dp).clip(CircleShape).background(stop.colors[0].copy(alpha = alpha)))
        Box(
            Modifier
                .size((size * 0.52f).dp)
                .clip(CircleShape)
                .background(stop.colors.last().copy(alpha = alpha * 0.9f)),
        )
    }
}

@Composable
private fun MarkerLabel(stop: DescentStop, alpha: Float, alignEnd: Boolean) {
    Column(
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stop.depthText,
            style = ts(13f, FontWeight.Bold, stop.colors[0].copy(alpha = alpha), 0.08f),
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
        Text(
            stop.name,
            style = ts(17f, FontWeight.SemiBold, Color.White.copy(alpha = alpha)),
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
        Text(
            stop.sub,
            style = ts(12.5f, color = Mute.copy(alpha = alpha), lineHeight = 17f),
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
    }
}
