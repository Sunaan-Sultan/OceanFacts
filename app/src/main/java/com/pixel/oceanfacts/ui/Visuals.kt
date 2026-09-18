package com.pixel.oceanfacts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.BodyKind
import com.pixel.oceanfacts.core.swell
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Everything in a scene is authored in this fixed virtual space, then scaled to fit. */
const val SCENE_W = 1080f
const val SCENE_H = 1920f

val OceanFont = FontFamily.SansSerif

// ───────────────────────── the water ─────────────────────────

/**
 * The colour of the sea at a given depth, as three vertical stops.
 *
 * This is the one idea the whole app is built on. A fact carries the depth it happens at, the
 * water behind its scene is coloured from that number, and so the app gets darker as the reader
 * descends without a single screen having to be told what mood to be in. The bands are spaced
 * roughly logarithmically because that is how light actually leaves: most of it is gone in the
 * first eighty metres, and everything past about 1,500 m is the same black.
 */
private class WaterBand(val m: Float, val top: Long, val mid: Long, val bot: Long)

private val BANDS = listOf(
    WaterBand(0f, 0x2FB9C6, 0x0E7294, 0x053B58),
    WaterBand(80f, 0x1E9EB4, 0x0A5C7E, 0x042C46),
    WaterBand(200f, 0x11738F, 0x063F60, 0x02192B),
    WaterBand(700f, 0x0A4463, 0x03233C, 0x010E1A),
    WaterBand(1500f, 0x05273F, 0x021326, 0x01080F),
    WaterBand(4000f, 0x02131F, 0x010912, 0x000408),
    WaterBand(11000f, 0x010A12, 0x00050A, 0x000205),
)

private fun opaque(v: Long) = Color(v or 0xFF000000)

/** The three gradient stops of the water column at [depthM], surface-most first. */
fun waterTone(depthM: Int): Triple<Color, Color, Color> {
    val d = depthM.toFloat().coerceIn(0f, BANDS.last().m)
    val hi = BANDS.indexOfFirst { it.m >= d }.coerceAtLeast(1)
    val a = BANDS[hi - 1]
    val b = BANDS[hi]
    val span = (b.m - a.m).coerceAtLeast(1f)
    val t = ((d - a.m) / span).coerceIn(0f, 1f)
    return Triple(
        lerp(opaque(a.top), opaque(b.top), t),
        lerp(opaque(a.mid), opaque(b.mid), t),
        lerp(opaque(a.bot), opaque(b.bot), t),
    )
}

/**
 * How much of the surface is still in play at [depthM]: 1 at the waterline, nothing by 220 m.
 * Drives the light shafts, and nothing else — the gradient darkens on its own.
 */
fun surfaceLight(depthM: Int): Float = (1f - depthM / 220f).coerceIn(0f, 1f)

/** How much of what falls past the camera is visible. Marine snow thickens as the light goes. */
private fun snowWeight(depthM: Int): Float = (0.28f + depthM / 1400f).coerceIn(0.28f, 1f)

/** Living light only matters once the sun has stopped competing with it. */
private fun biolumWeight(depthM: Int): Float = ((depthM - 450f) / 900f).coerceIn(0f, 1f)

/** The still water column behind every scene. */
@Composable
fun WaterColumn(depthM: Int) {
    val (top, mid, bot) = waterTone(depthM)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to top,
                0.46f to mid,
                1f to bot,
                startY = 0f,
                endY = size.height,
            ),
        )
    }
}

private class Shaft(val x: Float, val w: Float, val len: Float, val a: Float, val sp: Float, val ph: Float)

private val SHAFTS: List<Shaft> = run {
    var s = 4111L
    val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
    List(9) {
        Shaft(
            x = 0.04f + rnd() * 0.94f,
            w = 0.020f + rnd() * 0.055f,
            len = 0.42f + rnd() * 0.48f,
            a = 0.05f + rnd() * 0.10f,
            sp = 0.22f + rnd() * 0.30f,
            ph = rnd() * 6.28f,
        )
    }
}

/**
 * Sunlight coming through a moving surface: a bright band at the waterline and a set of shafts
 * that lean and widen as they go down. [strength] is [surfaceLight], so this simply stops
 * drawing once a scene is deep enough for it to be a lie.
 */
@Composable
fun Caustics(time: Float, strength: Float) {
    if (strength <= 0.01f) return
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFFCFFAFF).copy(alpha = 0.20f * strength),
                0.35f to Color(0xFF8FE4F2).copy(alpha = 0.07f * strength),
                1f to Color.Transparent,
            ),
            size = Size(w, h * 0.30f),
        )

        SHAFTS.forEach { s ->
            val sway = swell(time, s.sp, s.ph)
            val x = s.x * w
            val topW = s.w * w
            val botW = topW * 2.6f
            val len = h * s.len
            val drift = sway * w * 0.055f
            val a = s.a * strength
            val path = Path().apply {
                moveTo(x - topW / 2f, 0f)
                lineTo(x + topW / 2f, 0f)
                lineTo(x + botW / 2f + drift, len)
                lineTo(x - botW / 2f + drift, len)
                close()
            }
            drawPath(
                path,
                Brush.verticalGradient(
                    0f to Color(0xFFD8FBFF).copy(alpha = a),
                    0.45f to Color(0xFF86DDF0).copy(alpha = a * 0.40f),
                    1f to Color.Transparent,
                    startY = 0f,
                    endY = len,
                ),
            )
        }
    }
}

private class Flake(
    val x: Float, val y: Float, val r: Float,
    val fall: Float, val sway: Float, val ph: Float, val a: Float,
)

private val FLAKES: List<Flake> = run {
    var s = 20259L
    val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
    List(160) {
        Flake(
            x = rnd(), y = rnd(),
            r = 0.9f + rnd() * 2.4f,
            // Slow: the whole point of marine snow is that it takes weeks to reach the bottom.
            fall = 0.012f + rnd() * 0.030f,
            sway = 0.25f + rnd() * 0.9f,
            ph = rnd() * 6.28f,
            a = 0.18f + rnd() * 0.45f,
        )
    }
}

/**
 * The falling detritus that is visible in every underwater shot ever taken, and the thing that
 * makes a still gradient read as water rather than as a coloured wall.
 *
 * Deterministic, and a pure function of time, so a frozen thumbnail and a running scene draw
 * the same flakes in the same places.
 */
@Composable
fun MarineSnow(time: Float, depthM: Int) {
    val weight = snowWeight(depthM)
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        FLAKES.forEach { f ->
            // Wrapping in [0,1) rather than respawning keeps this stateless.
            val y = ((f.y + time * f.fall) % 1f) * h
            val x = (f.x * w) + swell(time, f.sway, f.ph) * w * 0.012f
            drawCircle(
                color = Color(0xFFDDF2FF).copy(alpha = (f.a * weight).coerceIn(0f, 1f)),
                radius = f.r * (w / SCENE_W),
                center = Offset(x, y),
            )
        }
    }
}

private class Mote(val x: Float, val y: Float, val r: Float, val sp: Float, val ph: Float)

private val MOTES: List<Mote> = run {
    var s = 60631L
    val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
    List(22) { Mote(rnd(), rnd(), 2.2f + rnd() * 4.5f, 0.35f + rnd() * 1.1f, rnd() * 6.28f) }
}

/** Living light: sparse blue-green pulses that only appear once the sun has gone. */
@Composable
fun Bioluminescence(time: Float, depthM: Int) {
    val weight = biolumWeight(depthM)
    if (weight <= 0.01f) return
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val k = w / SCENE_W
        MOTES.forEach { m ->
            // Mostly dark with an occasional flare, rather than a steady twinkle: a pulse that
            // is off more than it is on is what reads as an animal instead of a star.
            val pulse = ((sin(time * m.sp + m.ph) + 1f) / 2f)
            val a = (pulse * pulse * pulse * weight).coerceIn(0f, 1f)
            if (a < 0.02f) return@forEach
            val c = Offset(m.x * w, m.y * size.height)
            val r = m.r * k
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFF8CFFE8).copy(alpha = a * 0.75f),
                    1f to Color.Transparent,
                    center = c,
                    radius = r * 5f,
                ),
                radius = r * 5f,
                center = c,
            )
            drawCircle(Color(0xFFE6FFF9).copy(alpha = a), radius = r, center = c)
        }
    }
}

/** Pressure at the edges: the frame closes in as the scene gets deeper. */
@Composable
fun DepthVignette(depthM: Int) {
    val strength = (0.55f + depthM / 3000f).coerceIn(0.55f, 0.92f)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0.50f to Color.Transparent, 1f to Color(0xFF00060C).copy(alpha = strength)),
                center = Offset(size.width / 2f, size.height * 0.44f),
                radius = size.height * 0.64f,
            ),
        )
    }
}

// ───────────────────────── the stage ─────────────────────────

/**
 * Hosts scene content in the [SCENE_W] x [SCENE_H] virtual space and scales it to fill (cover)
 * or fit (contain) the available area, centred and clipped.
 *
 * The scaling works by overriding the ambient density rather than by scaling a node: one
 * virtual unit (authored as 1.dp) becomes `pxPerUnit` pixels, which keeps each scene's render
 * buffers bounded to the viewport instead of inflating a full 1080x1920.dp node and shrinking
 * it — that exhausts memory on a phone with more than a couple of scenes on screen.
 */
@Composable
fun SceneCanvas(
    modifier: Modifier = Modifier,
    depthM: Int = 0,
    cover: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val realDensity = LocalDensity.current
    val (_, _, deep) = waterTone(depthM)
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .background(deep),
        contentAlignment = Alignment.Center,
    ) {
        val wPx = maxWidth.value * realDensity.density
        val hPx = maxHeight.value * realDensity.density
        val pxPerUnit = if (cover) max(wPx / SCENE_W, hPx / SCENE_H) else min(wPx / SCENE_W, hPx / SCENE_H)
        val sceneDensity = Density(density = pxPerUnit, fontScale = realDensity.fontScale)
        CompositionLocalProvider(LocalDensity provides sceneDensity) {
            Box(Modifier.requiredSize(SCENE_W.dp, SCENE_H.dp)) { content() }
        }
    }
}

// ───────────────────────── bodies ─────────────────────────

/** A lit sphere: bubbles, eyes, the Moon, the Earth. */
@Composable
fun Sphere(
    sizeUnits: Float,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    glow: Color? = null,
    ring: Color? = null,
) {
    Canvas(modifier.size(sizeUnits.dp)) {
        val d = size.width
        val c = Offset(d / 2f, d / 2f)

        glow?.let {
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(0f to it, 0.6f to it.copy(alpha = it.alpha * 0.4f), 1f to it.copy(alpha = 0f)),
                    center = c,
                    radius = d * 0.95f,
                ),
                radius = d * 0.95f,
                center = c,
            )
        }

        ring?.let {
            val rw = d * 2.3f
            val rh = d * 0.62f
            val sw = max(2f, d * 0.045f)
            rotate(degrees = -17f, pivot = c) {
                drawOval(
                    color = it,
                    topLeft = Offset(c.x - rw / 2f, c.y - rh / 2f),
                    size = Size(rw, rh),
                    style = Stroke(width = sw),
                )
            }
        }

        // Body, lit from above — underwater the only light there has ever been comes from up.
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to colors[0], 0.52f to colors[1], 1f to colors[2]),
                center = Offset(d * 0.36f, d * 0.26f),
                radius = d * 0.92f,
            ),
            radius = d / 2f,
            center = c,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f)),
                center = Offset(d * 0.36f, d * 0.26f),
                radius = d * 0.95f,
            ),
            radius = d / 2f,
            center = c,
        )
    }
}

/** How tall a body of the given length is, so callers can lay a line-up out without guessing. */
fun bodyHeight(kind: BodyKind, lengthUnits: Float): Float = lengthUnits * when (kind) {
    BodyKind.WHALE -> 0.30f
    BodyKind.SHARK -> 0.32f
    BodyKind.FISH -> 0.44f
    BodyKind.SQUID -> 0.40f
    BodyKind.OCTOPUS -> 0.66f
    BodyKind.SUB -> 0.58f
    // A person is the one body in the roster measured head to toe, so it is drawn upright and
    // its "length" is its height.
    BodyKind.HUMAN -> 1f
}

fun bodyWidth(kind: BodyKind, lengthUnits: Float): Float =
    if (kind == BodyKind.HUMAN) lengthUnits * 0.34f else lengthUnits

/**
 * A sea animal in silhouette, drawn head-left inside a box [bodyWidth] x [bodyHeight].
 *
 * Everything is authored as fractions of that box and scaled on the way out, so one path serves
 * a 2-unit lanternfish and a 700-unit blue whale. [swimT] drives a gentle body flex — pass the
 * scene clock and a body moves; pass a constant and it holds still for a thumbnail.
 */
@Composable
fun SeaBody(
    lengthUnits: Float,
    kind: BodyKind,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    swimT: Float = 0f,
    glow: Color? = null,
    flip: Boolean = false,
) {
    val w = bodyWidth(kind, lengthUnits)
    val h = bodyHeight(kind, lengthUnits)
    Canvas(modifier.size(w.dp, h.dp)) {
        val sw = size.width
        val sh = size.height
        val flex = sin(swimT * 2.1f) * sh * 0.05f

        glow?.let {
            drawOval(
                brush = Brush.radialGradient(
                    0f to it,
                    1f to it.copy(alpha = 0f),
                    center = Offset(sw / 2f, sh / 2f),
                    radius = max(sw, sh) * 0.8f,
                ),
                topLeft = Offset(-sw * 0.25f, -sh * 0.5f),
                size = Size(sw * 1.5f, sh * 2f),
            )
        }

        val body = Brush.verticalGradient(
            0f to colors[0],
            0.45f to colors[1],
            1f to colors[2],
            startY = 0f,
            endY = sh,
        )

        fun paint(path: Path) = drawPath(path, body)

        if (flip) {
            // Mirroring about the centre is the whole of "turn the animal around".
            scale(-1f, 1f, pivot = Offset(sw / 2f, sh / 2f)) {
                drawBody(kind, sw, sh, flex, ::paint, colors)
            }
        } else {
            drawBody(kind, sw, sh, flex, ::paint, colors)
        }
    }
}

private fun DrawScope.drawBody(
    kind: BodyKind,
    w: Float,
    h: Float,
    flex: Float,
    paint: (Path) -> Unit,
    colors: List<Color>,
) {
    when (kind) {
        BodyKind.WHALE -> {
            paint(
                Path().apply {
                    moveTo(0f, h * 0.56f)
                    cubicTo(w * 0.06f, h * 0.14f, w * 0.34f, h * 0.02f, w * 0.60f, h * 0.12f)
                    cubicTo(w * 0.73f, h * 0.18f, w * 0.80f, h * 0.31f, w * 0.85f, h * 0.44f + flex)
                    lineTo(w * 1.00f, h * 0.08f + flex)
                    lineTo(w * 0.95f, h * 0.50f + flex)
                    lineTo(w * 1.00f, h * 0.92f + flex)
                    lineTo(w * 0.85f, h * 0.58f + flex)
                    cubicTo(w * 0.78f, h * 0.76f, w * 0.52f, h * 0.98f, w * 0.26f, h * 0.90f)
                    cubicTo(w * 0.11f, h * 0.85f, w * 0.02f, h * 0.74f, 0f, h * 0.56f)
                    close()
                },
            )
            // Pectoral flipper, and the pale throat grooves every rorqual has.
            paint(
                Path().apply {
                    moveTo(w * 0.28f, h * 0.70f)
                    cubicTo(w * 0.26f, h * 1.02f, w * 0.36f, h * 1.14f, w * 0.44f, h * 0.98f)
                    cubicTo(w * 0.42f, h * 0.86f, w * 0.36f, h * 0.76f, w * 0.28f, h * 0.70f)
                    close()
                },
            )
            drawCircle(colors[0].copy(alpha = 0.55f), radius = h * 0.035f, center = Offset(w * 0.08f, h * 0.48f))
        }

        BodyKind.SHARK -> {
            paint(
                Path().apply {
                    moveTo(0f, h * 0.46f)
                    cubicTo(w * 0.10f, h * 0.14f, w * 0.32f, h * 0.04f, w * 0.52f, h * 0.12f)
                    cubicTo(w * 0.66f, h * 0.19f, w * 0.77f, h * 0.32f, w * 0.84f, h * 0.44f + flex)
                    lineTo(w * 1.00f, h * 0.00f + flex)
                    lineTo(w * 0.93f, h * 0.50f + flex)
                    lineTo(w * 1.00f, h * 0.82f + flex)
                    lineTo(w * 0.84f, h * 0.56f + flex)
                    cubicTo(w * 0.74f, h * 0.74f, w * 0.54f, h * 0.94f, w * 0.34f, h * 0.90f)
                    cubicTo(w * 0.16f, h * 0.86f, w * 0.04f, h * 0.68f, 0f, h * 0.46f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(w * 0.40f, h * 0.12f)
                    lineTo(w * 0.54f, h * -0.34f)
                    lineTo(w * 0.60f, h * 0.16f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(w * 0.30f, h * 0.70f)
                    lineTo(w * 0.22f, h * 1.18f)
                    lineTo(w * 0.46f, h * 0.80f)
                    close()
                },
            )
            drawCircle(Color.Black.copy(alpha = 0.55f), radius = h * 0.035f, center = Offset(w * 0.09f, h * 0.42f))
        }

        BodyKind.FISH -> {
            paint(
                Path().apply {
                    moveTo(0f, h * 0.50f)
                    cubicTo(w * 0.10f, h * 0.12f, w * 0.40f, h * 0.06f, w * 0.64f, h * 0.24f)
                    cubicTo(w * 0.74f, h * 0.32f, w * 0.78f, h * 0.42f, w * 0.80f, h * 0.50f + flex)
                    lineTo(w * 1.00f, h * 0.12f + flex)
                    lineTo(w * 1.00f, h * 0.88f + flex)
                    lineTo(w * 0.80f, h * 0.50f + flex)
                    cubicTo(w * 0.78f, h * 0.60f, w * 0.72f, h * 0.72f, w * 0.60f, h * 0.80f)
                    cubicTo(w * 0.36f, h * 0.96f, w * 0.09f, h * 0.86f, 0f, h * 0.50f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(w * 0.34f, h * 0.20f)
                    lineTo(w * 0.48f, h * -0.12f)
                    lineTo(w * 0.56f, h * 0.24f)
                    close()
                },
            )
            drawCircle(Color.Black.copy(alpha = 0.6f), radius = h * 0.055f, center = Offset(w * 0.11f, h * 0.44f))
        }

        BodyKind.SQUID -> {
            // Mantle, pointed at the left; arms stream out to the right and wave with the flex.
            paint(
                Path().apply {
                    moveTo(0f, h * 0.50f)
                    cubicTo(w * 0.06f, h * 0.24f, w * 0.20f, h * 0.14f, w * 0.40f, h * 0.20f)
                    cubicTo(w * 0.52f, h * 0.24f, w * 0.56f, h * 0.38f, w * 0.56f, h * 0.50f)
                    cubicTo(w * 0.56f, h * 0.62f, w * 0.52f, h * 0.76f, w * 0.40f, h * 0.80f)
                    cubicTo(w * 0.20f, h * 0.86f, w * 0.06f, h * 0.76f, 0f, h * 0.50f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(w * 0.04f, h * 0.36f)
                    lineTo(w * -0.16f, h * 0.50f)
                    lineTo(w * 0.04f, h * 0.64f)
                    close()
                },
            )
            repeat(6) { i ->
                val y = h * (0.30f + i * 0.08f)
                val wobble = flex * (if (i % 2 == 0) 1f else -1f) * (0.6f + i * 0.12f)
                paint(
                    Path().apply {
                        moveTo(w * 0.54f, y)
                        cubicTo(w * 0.72f, y + wobble, w * 0.86f, y - wobble, w * 1.00f, y + wobble * 1.6f)
                        lineTo(w * 1.00f, y + wobble * 1.6f + h * 0.035f)
                        cubicTo(w * 0.86f, y - wobble + h * 0.035f, w * 0.72f, y + wobble + h * 0.035f, w * 0.54f, y + h * 0.05f)
                        close()
                    },
                )
            }
            drawCircle(Color(0xFF0B1A22), radius = h * 0.09f, center = Offset(w * 0.48f, h * 0.42f))
            drawCircle(colors[0], radius = h * 0.035f, center = Offset(w * 0.46f, h * 0.39f))
        }

        BodyKind.OCTOPUS -> {
            // Dumbo octopus: a soft bell, two fins standing off it, and a webbed skirt of arms.
            paint(
                Path().apply {
                    moveTo(w * 0.50f, h * 0.06f)
                    cubicTo(w * 0.76f, h * 0.06f, w * 0.86f, h * 0.30f, w * 0.80f, h * 0.48f)
                    cubicTo(w * 0.72f, h * 0.66f, w * 0.28f, h * 0.66f, w * 0.20f, h * 0.48f)
                    cubicTo(w * 0.14f, h * 0.30f, w * 0.24f, h * 0.06f, w * 0.50f, h * 0.06f)
                    close()
                },
            )
            val ear = flex * 1.6f
            paint(
                Path().apply {
                    moveTo(w * 0.24f, h * 0.22f)
                    cubicTo(w * 0.06f, h * 0.10f + ear, w * -0.04f, h * 0.24f + ear, w * 0.10f, h * 0.32f)
                    cubicTo(w * 0.16f, h * 0.34f, w * 0.22f, h * 0.30f, w * 0.24f, h * 0.22f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(w * 0.76f, h * 0.22f)
                    cubicTo(w * 0.94f, h * 0.10f - ear, w * 1.04f, h * 0.24f - ear, w * 0.90f, h * 0.32f)
                    cubicTo(w * 0.84f, h * 0.34f, w * 0.78f, h * 0.30f, w * 0.76f, h * 0.22f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(w * 0.22f, h * 0.54f)
                    cubicTo(w * 0.30f, h * 0.90f + flex, w * 0.70f, h * 0.90f - flex, w * 0.78f, h * 0.54f)
                    cubicTo(w * 0.66f, h * 0.78f, w * 0.34f, h * 0.78f, w * 0.22f, h * 0.54f)
                    close()
                },
            )
            drawCircle(Color.Black.copy(alpha = 0.6f), radius = h * 0.045f, center = Offset(w * 0.40f, h * 0.34f))
            drawCircle(Color.Black.copy(alpha = 0.6f), radius = h * 0.045f, center = Offset(w * 0.60f, h * 0.34f))
        }

        BodyKind.SUB -> {
            paint(
                Path().apply {
                    moveTo(w * 0.10f, h * 0.50f)
                    cubicTo(w * 0.10f, h * 0.20f, w * 0.30f, h * 0.10f, w * 0.52f, h * 0.12f)
                    cubicTo(w * 0.78f, h * 0.14f, w * 0.92f, h * 0.30f, w * 0.92f, h * 0.50f)
                    cubicTo(w * 0.92f, h * 0.72f, w * 0.76f, h * 0.88f, w * 0.50f, h * 0.88f)
                    cubicTo(w * 0.28f, h * 0.88f, w * 0.10f, h * 0.76f, w * 0.10f, h * 0.50f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(w * 0.42f, h * 0.10f)
                    lineTo(w * 0.42f, h * -0.08f)
                    lineTo(w * 0.62f, h * -0.08f)
                    lineTo(w * 0.62f, h * 0.12f)
                    close()
                },
            )
            drawCircle(Color(0xFFBFF0FF).copy(alpha = 0.9f), radius = h * 0.13f, center = Offset(w * 0.24f, h * 0.46f))
            drawCircle(Color(0xFF07202E), radius = h * 0.08f, center = Offset(w * 0.24f, h * 0.46f))
        }

        BodyKind.HUMAN -> {
            // A person, upright, for scale. Deliberately plain: it is a yardstick, not a subject.
            val cx = w * 0.50f
            drawCircle(colors[0], radius = w * 0.24f, center = Offset(cx, h * 0.09f))
            paint(
                Path().apply {
                    moveTo(cx - w * 0.22f, h * 0.20f)
                    lineTo(cx + w * 0.22f, h * 0.20f)
                    lineTo(cx + w * 0.16f, h * 0.56f)
                    lineTo(cx - w * 0.16f, h * 0.56f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(cx - w * 0.22f, h * 0.22f)
                    lineTo(cx - w * 0.46f, h * 0.46f)
                    lineTo(cx - w * 0.34f, h * 0.50f)
                    lineTo(cx - w * 0.14f, h * 0.30f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(cx + w * 0.22f, h * 0.22f)
                    lineTo(cx + w * 0.46f, h * 0.46f)
                    lineTo(cx + w * 0.34f, h * 0.50f)
                    lineTo(cx + w * 0.14f, h * 0.30f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(cx - w * 0.16f, h * 0.54f)
                    lineTo(cx - w * 0.04f, h * 0.54f)
                    lineTo(cx - w * 0.06f, h * 1.00f)
                    lineTo(cx - w * 0.18f, h * 1.00f)
                    close()
                },
            )
            paint(
                Path().apply {
                    moveTo(cx + w * 0.04f, h * 0.54f)
                    lineTo(cx + w * 0.16f, h * 0.54f)
                    lineTo(cx + w * 0.18f, h * 1.00f)
                    lineTo(cx + w * 0.06f, h * 1.00f)
                    close()
                },
            )
        }
    }
}

/** A trail of rising bubbles, used wherever something breathes or vents. */
@Composable
fun Bubbles(
    time: Float,
    count: Int = 16,
    seed: Long = 8123L,
    color: Color = Color(0xFFCFF2FF),
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val bubbles = remember(seed, count) {
        var s = seed
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        List(count) { floatArrayOf(rnd(), rnd(), 2f + rnd() * 7f, 0.06f + rnd() * 0.12f, rnd() * 6.28f) }
    }
    Canvas(modifier) {
        val w = size.width
        val k = w / SCENE_W
        bubbles.forEach { b ->
            val y = 1f - ((b[1] + time * b[3]) % 1f)
            val x = b[0] * w + sin(time * 1.6f + b[4]) * w * 0.018f
            // A bubble grows as it rises and the pressure around it drops.
            val r = b[2] * k * (1.1f - y * 0.35f)
            val a = (0.16f + 0.5f * (1f - abs(y - 0.5f) * 2f)).coerceIn(0f, 1f)
            drawCircle(color.copy(alpha = a * 0.5f), radius = r, center = Offset(x, y * size.height))
            drawCircle(
                color.copy(alpha = a),
                radius = r,
                center = Offset(x, y * size.height),
                style = Stroke(width = max(1f, r * 0.24f)),
            )
        }
    }
}
