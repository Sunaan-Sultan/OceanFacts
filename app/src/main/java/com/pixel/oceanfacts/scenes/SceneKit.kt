package com.pixel.oceanfacts.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pixel.oceanfacts.core.Easing
import com.pixel.oceanfacts.core.Reveal
import com.pixel.oceanfacts.core.SceneId
import com.pixel.oceanfacts.core.clamp
import com.pixel.oceanfacts.core.swell
import com.pixel.oceanfacts.ui.OceanFont
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

// Every scene is authored in the SCENE_W x SCENE_H virtual space (see ui/Visuals.kt) and drawn
// against these shared building blocks. Nothing here holds state: a scene is a pure function of
// its clock, so the same code serves the player, a looping card and a frozen thumbnail.

// ───────────────────────── colour & type ─────────────────────────

internal fun c(v: Long) = Color(v or 0xFF000000)

internal fun fmt(n: Float) = String.format(Locale.US, "%,d", n.roundToInt())

internal fun metres(m: Int) = String.format(Locale.US, "%,d m", m)

internal fun eyebrow(color: Color) = TextStyle(
    fontFamily = OceanFont, fontWeight = FontWeight.Bold, fontSize = 26.sp,
    letterSpacing = 0.42.em, color = color,
)

internal fun head(sizeSp: Float, color: Color = Color.White) = TextStyle(
    fontFamily = OceanFont, fontWeight = FontWeight.Light, fontSize = sizeSp.sp,
    letterSpacing = (-0.02).em, lineHeight = (sizeSp * 1.06f).sp, color = color,
)

internal fun label(sizeSp: Float, color: Color, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = OceanFont, fontWeight = weight, fontSize = sizeSp.sp,
    letterSpacing = 0.16.em, color = color,
)

/** Builds a two-weight headline: [plain] then [emph] in semibold. */
internal fun headline(plain: String, emph: String, tail: String = ""): AnnotatedString = buildAnnotatedString {
    append(plain)
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(emph) }
    if (tail.isNotEmpty()) append(tail)
}

/** Builds a body line: [plain] then [bold] in bold. */
internal fun body(plain: String, bold: String, tail: String = ""): AnnotatedString = buildAnnotatedString {
    append(plain)
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
    if (tail.isNotEmpty()) append(tail)
}

// ───────────────────────── layout primitives ─────────────────────────

/** Places [content] at virtual coordinate (x, y) with an optional alpha. */
@Composable
internal fun BoxScope.At(
    x: Float,
    y: Float,
    alpha: Float = 1f,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier.offset(x.dp, y.dp).alpha(alpha.coerceIn(0f, 1f))) { content() }
}

/** Cross-fades a whole scene in and out, so a 15-second loop has no visible seam. */
@Composable
internal fun BoxScope.SceneFade(t: Float, duration: Float, content: @Composable BoxScope.() -> Unit) {
    val fi = 0.7f
    val fo = 0.7f
    val end = duration - fo
    val o = when {
        t < fi -> Easing.easeOutCubic(clamp(t / fi, 0f, 1f))
        t > end -> 1f - Easing.easeInCubic(clamp((t - end) / fo, 0f, 1f))
        else -> 1f
    }
    Box(Modifier.fillMaxSize().alpha(o.coerceIn(0f, 1f))) { content() }
}

// ───────────────────────── drawing primitives ─────────────────────────

/**
 * A radial-gradient disc positioned by absolute scene coordinates — the sun through the
 * surface, a lure, a vent glow.
 *
 * Drawn on a scene-sized canvas so the circle can far exceed the canvas; only the overlapping
 * part shows, which is exactly what makes a rising limb work.
 */
@Composable
internal fun RadialDisc(
    cxUnits: Float,
    cyUnits: Float,
    rUnits: Float,
    stops: Array<Pair<Float, Color>>,
    centerFracX: Float = 0.5f,
    centerFracY: Float = 0.5f,
    radiusFactor: Float = 0.5f,
) {
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        val cx = cxUnits * k
        val cy = cyUnits * k
        val r = rUnits * k
        // A zero or negative radius has nothing to draw and would make radialGradient throw.
        if (r <= 0f) return@Canvas
        val gcx = cx + r * (2f * centerFracX - 1f)
        val gcy = cy + r * (2f * centerFracY - 1f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = stops,
                center = Offset(gcx, gcy),
                radius = (2f * r) * radiusFactor,
            ),
            radius = r,
            center = Offset(cx, cy),
        )
    }
}

/** A soft round glow — the cheapest way to say "this thing is the light source". */
@Composable
internal fun Glow(cx: Float, cy: Float, r: Float, color: Color, alpha: Float = 1f) {
    if (alpha <= 0.01f || r <= 0f) return
    RadialDisc(
        cxUnits = cx, cyUnits = cy, rUnits = r,
        stops = arrayOf(
            0f to color.copy(alpha = color.alpha * alpha),
            0.45f to color.copy(alpha = color.alpha * alpha * 0.30f),
            1f to color.copy(alpha = 0f),
        ),
    )
}

/** A stroked ring or ellipse in scene units; optional dash, fade and clip-to-below-[clipTop]. */
@Composable
internal fun RingArc(
    cx: Float, cy: Float, w: Float, h: Float, deg: Float, color: Color, bw: Float,
    dash: Boolean = false, clipTop: Float? = null, alpha: Float = 1f,
) {
    Canvas(if (alpha < 1f) Modifier.fillMaxSize().alpha(alpha) else Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        val stroke = if (dash) {
            Stroke(bw * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * k, 12f * k)))
        } else {
            Stroke(bw * k)
        }
        val oval = {
            rotate(deg, pivot = Offset(cx * k, cy * k)) {
                drawOval(
                    color = color,
                    topLeft = Offset((cx - w / 2f) * k, (cy - h / 2f) * k),
                    size = Size(w * k, h * k),
                    style = stroke,
                )
            }
        }
        if (clipTop != null) clipRect(top = clipTop * k) { oval() } else oval()
    }
}

/** A straight line in scene units. */
@Composable
internal fun Line(
    x1: Float, y1: Float, x2: Float, y2: Float,
    color: Color, width: Float = 2f, dash: Boolean = false, alpha: Float = 1f,
) {
    if (alpha <= 0.01f) return
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        drawLine(
            color = color.copy(alpha = color.alpha * alpha),
            start = Offset(x1 * k, y1 * k),
            end = Offset(x2 * k, y2 * k),
            strokeWidth = width * k,
            cap = StrokeCap.Round,
            pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(12f * k, 14f * k)) else null,
        )
    }
}

/** A filled rectangle in scene units, optionally rounded. */
@Composable
internal fun Rect(
    x: Float, y: Float, w: Float, h: Float,
    color: Color, alpha: Float = 1f, corner: Float = 0f,
) {
    if (alpha <= 0.01f || w <= 0f || h <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        val c = color.copy(alpha = color.alpha * alpha.coerceIn(0f, 1f))
        if (corner > 0f) {
            drawRoundRect(
                color = c,
                topLeft = Offset(x * k, y * k),
                size = Size(w * k, h * k),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * k, corner * k),
            )
        } else {
            drawRect(color = c, topLeft = Offset(x * k, y * k), size = Size(w * k, h * k))
        }
    }
}

/** A gradient-filled rectangle in scene units. Stops run top-to-bottom, or left-to-right. */
@Composable
internal fun GradientRect(
    x: Float, y: Float, w: Float, h: Float,
    stops: Array<Pair<Float, Color>>,
    vertical: Boolean = true,
    alpha: Float = 1f,
    corner: Float = 0f,
) {
    if (alpha <= 0.01f || w <= 0f || h <= 0f) return
    Canvas(Modifier.fillMaxSize().alpha(alpha.coerceIn(0f, 1f))) {
        val k = size.width / 1080f
        val brush = if (vertical) {
            Brush.verticalGradient(colorStops = stops, startY = y * k, endY = (y + h) * k)
        } else {
            Brush.horizontalGradient(colorStops = stops, startX = x * k, endX = (x + w) * k)
        }
        if (corner > 0f) {
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x * k, y * k),
                size = Size(w * k, h * k),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner * k, corner * k),
            )
        } else {
            drawRect(brush = brush, topLeft = Offset(x * k, y * k), size = Size(w * k, h * k))
        }
    }
}

/**
 * The seabed: a ragged silhouette across the bottom of the scene.
 *
 * [roughness] at 0 is an abyssal plain, which really is nearly flat; at 1 it is a trench wall.
 */
@Composable
internal fun SeaFloor(
    y: Float,
    color: Color,
    roughness: Float = 0.35f,
    seed: Long = 3301L,
    alpha: Float = 1f,
) {
    Canvas(Modifier.fillMaxSize().alpha(alpha.coerceIn(0f, 1f))) {
        val k = size.width / 1080f
        var s = seed
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        val steps = 22
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, y * k)
            for (i in 1..steps) {
                val x = size.width * i / steps
                val h = (y - (rnd() - 0.45f) * 150f * roughness) * k
                lineTo(x, h)
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                0f to color,
                1f to color.copy(alpha = color.alpha * 0.35f),
                startY = (y - 120f) * k,
                endY = size.height,
            ),
        )
    }
}

// ───────────────────────── text kit ─────────────────────────

@Composable
internal fun BoxScope.Eyebrow(text: String, color: Color, y: Float, r: Reveal) {
    At(90f, y + r.ty, r.opacity) { Text(text.uppercase(), style = eyebrow(color)) }
}

@Composable
internal fun BoxScope.Head(text: AnnotatedString, sizeSp: Float, y: Float, r: Reveal) {
    At(88f, y + r.ty, r.opacity, modifier = Modifier.width(920.dp)) { Text(text, style = head(sizeSp)) }
}

@Composable
internal fun BoxScope.BottomLine(y: Float, r: Reveal, text: AnnotatedString, color: Color = Color.White) {
    At(90f, y + r.ty, r.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            text,
            style = TextStyle(
                fontFamily = OceanFont, fontWeight = FontWeight.Light, fontSize = 34.sp,
                color = color, lineHeight = 44.sp,
            ),
        )
    }
}

/** A big-number + caption stat column (the number may carry a smaller trailing unit). */
@Composable
internal fun StatCol(value: String, unit: String?, caption: String, valueSize: Float, numColor: Color, labelColor: Color) {
    Column {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = valueSize.sp, color = numColor)) { append(value) }
                if (unit != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Light, fontSize = (valueSize * 0.44f).sp, color = numColor)) { append(unit) }
                }
            },
            style = TextStyle(fontFamily = OceanFont, lineHeight = valueSize.sp),
        )
        Text(
            caption,
            style = TextStyle(fontFamily = OceanFont, fontWeight = FontWeight.Light, fontSize = 22.sp, color = labelColor),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A small eyebrow label sitting above a big number. */
@Composable
internal fun LabeledStat(top: String, topColor: Color, value: String, unit: String?, sub: String?, valueSize: Float) {
    Column {
        Text(top, style = label(17f, topColor))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = valueSize.sp, color = Color.White)) { append(value) }
                if (unit != null) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Light, fontSize = (valueSize * 0.42f).sp, color = Color.White)) { append(unit) }
                }
            },
            style = TextStyle(fontFamily = OceanFont, lineHeight = valueSize.sp),
        )
        if (sub != null) {
            Text(sub, style = TextStyle(fontFamily = OceanFont, fontWeight = FontWeight.Light, fontSize = 20.sp, color = c(0x7d95a3)))
        }
    }
}

/**
 * A vertical depth scale: the spine of half the scenes in this app.
 *
 * [marks] are depths in metres, placed linearly between [topM] and [botM] down the span
 * [yTop]..[yBot]. [progress] draws the line in from the top, so a descent can be shown arriving
 * rather than simply being there.
 */
@Composable
internal fun BoxScope.DepthRuler(
    x: Float,
    yTop: Float,
    yBot: Float,
    topM: Int,
    botM: Int,
    marks: List<Int>,
    accent: Color,
    alpha: Float,
    progress: Float = 1f,
    tickLen: Float = 26f,
    labelRight: Boolean = true,
) {
    if (alpha <= 0.01f) return
    val span = (botM - topM).coerceAtLeast(1)
    fun yOf(m: Int) = yTop + (yBot - yTop) * (m - topM).toFloat() / span

    val drawnTo = yTop + (yBot - yTop) * progress.coerceIn(0f, 1f)
    Line(x, yTop, x, drawnTo, accent.copy(alpha = 0.55f), width = 3f, alpha = alpha)

    marks.forEach { m ->
        val y = yOf(m)
        if (y > drawnTo) return@forEach
        val shown = ((drawnTo - y) / 60f).coerceIn(0f, 1f) * alpha
        Line(x, y, x + (if (labelRight) tickLen else -tickLen), y, accent.copy(alpha = 0.8f), width = 3f, alpha = shown)
        At(
            x = if (labelRight) x + tickLen + 14f else x - tickLen - 220f,
            y = y - 18f,
            alpha = shown,
            modifier = Modifier.width(210.dp),
        ) {
            Text(metres(m), style = label(24f, Color.White.copy(alpha = 0.85f), FontWeight.Medium))
        }
    }
}

// ───────────────────────── dispatcher ─────────────────────────

@Composable
fun BoxScope.RenderScene(scene: SceneId, t: Float, duration: Float) {
    when (scene) {
        SceneId.SUN_SIZES -> SunSizes(t, duration)
        SceneId.SUN_WHALE -> SunWhale(t, duration)
        SceneId.SUN_LIGHT -> SunLight(t, duration)
        SceneId.SUN_BLUE -> SunBlue(t, duration)
        SceneId.SUN_PLANKTON -> SunPlankton(t, duration)
        SceneId.SUN_WAVE -> SunWave(t, duration)

        SceneId.TWI_ENTER -> TwiEnter(t, duration)
        SceneId.TWI_SNOW -> TwiSnow(t, duration)
        SceneId.TWI_BIOLUM -> TwiBiolum(t, duration)
        SceneId.TWI_COUNTER -> TwiCounter(t, duration)
        SceneId.TWI_MIGRATION -> TwiMigration(t, duration)
        SceneId.TWI_SQUID -> TwiSquid(t, duration)

        SceneId.MID_DARK -> MidDark(t, duration)
        SceneId.MID_COLD -> MidCold(t, duration)
        SceneId.MID_ANGLER -> MidAngler(t, duration)
        SceneId.MID_FUSION -> MidFusion(t, duration)
        SceneId.MID_WHALEFALL -> MidWhaleFall(t, duration)
        SceneId.MID_DUMBO -> MidDumbo(t, duration)

        SceneId.TR_CHALLENGER -> TrChallenger(t, duration)
        SceneId.TR_PRESSURE -> TrPressure(t, duration)
        SceneId.TR_SNAILFISH -> TrSnailfish(t, duration)
        SceneId.TR_VENTS -> TrVents(t, duration)
        SceneId.TR_VISITS -> TrVisits(t, duration)
        SceneId.TR_MAPPED -> TrMapped(t, duration)

        SceneId.DD_TITANIC -> DdTitanic(t, duration)
        SceneId.DD_WATERCYCLE -> DdWaterCycle(t, duration)
        SceneId.DD_TIDES -> DdTides(t, duration)
        SceneId.DD_AMAZON -> DdAmazon(t, duration)
        SceneId.DD_GYRE -> DdGyre(t, duration)
        SceneId.DD_CONVEYOR -> DdConveyor(t, duration)
    }
}
