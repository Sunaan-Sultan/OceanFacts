package com.pixel.oceanfacts.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.BodyKind
import com.pixel.oceanfacts.core.Easing
import com.pixel.oceanfacts.core.animate
import com.pixel.oceanfacts.core.clamp
import com.pixel.oceanfacts.core.interpolate
import com.pixel.oceanfacts.core.reveal
import com.pixel.oceanfacts.core.swell
import com.pixel.oceanfacts.ui.SeaBody
import com.pixel.oceanfacts.ui.Sphere
import kotlin.math.cos
import kotlin.math.sin

// ───────────────────── 1 · Titanic ─────────────────────

/**
 * A searchlight sweeping across black until it finds the bow. The wreck is drawn the whole
 * time; only the light decides how much of it exists.
 */
@Composable
fun BoxScope.DdTitanic(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.8f)

    val floorY = 1400f
    SeaFloor(floorY, c(0x120f12), roughness = 0.12f, seed = 9001L, alpha = reveal(t, 0.9f).opacity)

    val sweep = interpolate(listOf(1.2f, 7.0f), listOf(-200f, 620f), Easing.easeInOutSine)(t)
    val lightA = reveal(t, 1.0f).opacity

    Canvas(Modifier.fillMaxSize()) {
        if (lightA <= 0.01f) return@Canvas
        val k = size.width / 1080f
        // The bow, side on, sunk to the rail in sediment.
        val hull = Path().apply {
            moveTo(120f * k, floorY * k)
            lineTo(150f * k, 1180f * k)
            lineTo(560f * k, 1150f * k)
            lineTo(880f * k, 1214f * k)
            lineTo(900f * k, floorY * k)
            close()
        }
        drawPath(hull, Color(0xFF3A2C22).copy(alpha = 0.95f * lightA))
        // Superstructure and the two masts still standing.
        drawRect(
            Color(0xFF2E241C).copy(alpha = 0.95f * lightA),
            topLeft = Offset(380f * k, 1088f * k),
            size = Size(300f * k, 66f * k),
        )
        drawLine(
            Color(0xFF2E241C).copy(alpha = 0.95f * lightA),
            Offset(300f * k, 1156f * k), Offset(300f * k, 960f * k),
            strokeWidth = 12f * k, cap = StrokeCap.Round,
        )
        drawLine(
            Color(0xFF2E241C).copy(alpha = 0.9f * lightA),
            Offset(640f * k, 1140f * k), Offset(640f * k, 1000f * k),
            strokeWidth = 10f * k, cap = StrokeCap.Round,
        )
        // Rusticles: the iron running off it in rivulets.
        var s = 3733L
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        repeat(30) {
            val x = (160f + rnd() * 700f) * k
            val y0 = (1160f + rnd() * 40f) * k
            val len = (20f + rnd() * 70f) * k
            drawLine(
                Color(0xFF8A5A32).copy(alpha = 0.55f * lightA),
                Offset(x, y0), Offset(x + (rnd() - 0.5f) * 10f * k, y0 + len),
                strokeWidth = 5f * k, cap = StrokeCap.Round,
            )
        }

        // The cone of light, and the dark it leaves behind.
        val cone = Path().apply {
            moveTo(sweep * k, 560f * k)
            lineTo((sweep - 300f) * k, size.height)
            lineTo((sweep + 300f) * k, size.height)
            close()
        }
        drawPath(
            cone,
            Brush.verticalGradient(
                0f to Color(0xFFCFEFFF).copy(alpha = 0.22f * lightA),
                1f to Color.Transparent,
                startY = 560f * k,
                endY = size.height,
            ),
        )
    }
    // Everything outside the beam stays unlit.
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        drawRect(
            Brush.horizontalGradient(
                0f to Color(0xFF000306).copy(alpha = 0.92f),
                ((sweep - 340f) / 1080f).coerceIn(0f, 1f) to Color.Transparent,
                ((sweep + 340f) / 1080f).coerceIn(0.001f, 1f) to Color.Transparent,
                1f to Color(0xFF000306).copy(alpha = 0.92f),
                startX = 0f,
                endX = size.width,
            ),
            topLeft = Offset(0f, 900f * k),
            size = Size(size.width, size.height - 900f * k),
        )
    }

    Eyebrow("TITANIC", c(0xd8b884), 190f, e1)
    Head(headline("Three and a half\nkilometres ", "down."), 88f, 244f, e2)

    val sA = reveal(t, 4.4f)
    At(90f, 640f + sA.ty, sA.opacity) {
        LabeledStat(
            top = "RESTING DEPTH",
            topColor = c(0xd8b884),
            value = "3,800",
            unit = " m",
            sub = "found in 1985, 73 years after she sank",
            valueSize = 120f,
        )
    }

    val note = reveal(t, 7.2f)
    At(90f, 880f + note.ty, note.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "Iron-eating bacteria are taking her apart from the inside. The rust hangs off the hull in icicles nobody had a name for before.",
            style = head(34f, c(0xcbb49a)),
        )
    }

    BottomLine(1600f, e3, body("At this depth the wreck is under ", "380 atmospheres", ", in permanent dark, at 1 °C."))
}

// ───────────────────── 2 · the water cycle ─────────────────────

@Composable
fun BoxScope.DdWaterCycle(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("THE SAME WATER, ALWAYS", c(0x8fd8ff), 190f, e1)
    Head(headline("Every drop you drink\nhas ", "been everywhere."), 78f, 244f, e2)

    val cx = 540f
    val cy = 1030f
    val rx = 350f
    val ry = 250f

    val loopA = reveal(t, 1.2f)
    RingArc(cx, cy, rx * 2f, ry * 2f, 0f, c(0x8fd8ff).copy(alpha = 0.28f), 5f, dash = true, alpha = loopA.opacity)

    // A single drop going round and round, which is the entire mechanism.
    val turn = (t * 0.62f) % 1f
    val a = turn * 6.2832f - 1.5708f
    val dx = cx + cos(a) * rx
    val dy = cy + sin(a) * ry
    At(dx - 20f, dy - 20f, loopA.opacity) {
        Sphere(40f, listOf(c(0xe4f8ff), c(0x7fc8ec), c(0x2f6f96)), glow = c(0x558fd8ff))
    }

    // The stations it passes through, each lighting as the drop reaches it.
    val stations = listOf(
        Triple("OCEAN", 0.50f, 0x4aa8ffL),
        Triple("VAPOUR", 0.72f, 0xbfe8ffL),
        Triple("CLOUD", 0.00f, 0xffffffL),
        Triple("RAIN", 0.22f, 0x8fd8ffL),
        Triple("RIVER", 0.36f, 0x7fe0b4L),
    )
    stations.forEach { (name, at, tint) ->
        val sa = cos(at * 6.2832f - 1.5708f)
        val sb = sin(at * 6.2832f - 1.5708f)
        val sx = cx + sa * rx
        val sy = cy + sb * ry
        val near = 1f - (((turn - at + 1f) % 1f).let { minOf(it, 1f - it) } * 5f).coerceIn(0f, 1f)
        val base = reveal(t, 1.8f).opacity
        At(sx - 100f, sy - 22f, base, modifier = Modifier.width(200.dp)) {
            Text(
                name,
                style = label(23f, c(tint).copy(alpha = 0.45f + 0.55f * near)),
            )
        }
    }

    val s1 = reveal(t, 4.2f)
    At(90f, 1420f + s1.ty, s1.opacity) {
        LabeledStat(
            top = "OF ALL THE WATER ON EARTH",
            topColor = c(0x4aa8ff),
            value = "97",
            unit = " %",
            sub = "is in the ocean, and always comes back to it",
            valueSize = 116f,
        )
    }

    BottomLine(
        1660f, e3,
        body("The planet has had the same water for ", "four billion years", ". None of it is new, and none is ever lost."),
    )
}

// ───────────────────── 3 · the tides ─────────────────────

@Composable
fun BoxScope.DdTides(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("WHY THE SEA BREATHES", c(0xdfe6ff), 190f, e1)
    Head(headline("The Moon lifts\nthe ", "whole ocean."), 88f, 244f, e2)

    val ex = 430f
    val ey = 1020f
    val er = 210f
    val show = animate(0f, 1f, 1.1f, 3.4f, Easing.easeOutCubic)(t)

    // The water, drawn as an oval stretched toward the Moon. Two bulges, which is why there are
    // two high tides a day rather than one.
    Canvas(Modifier.fillMaxSize()) {
        if (show <= 0.01f) return@Canvas
        val k = size.width / 1080f
        val stretch = 1f + 0.30f * show
        rotate(degrees = 0f, pivot = Offset(ex * k, ey * k)) {
            drawOval(
                brush = Brush.radialGradient(
                    0f to Color(0xFF2F8FBE).copy(alpha = 0.55f),
                    1f to Color(0xFF4AA8FF).copy(alpha = 0.35f),
                    center = Offset(ex * k, ey * k),
                    radius = er * stretch * k,
                ),
                topLeft = Offset((ex - er * stretch) * k, (ey - er) * k),
                size = Size(er * 2f * stretch * k, er * 2f * k),
            )
        }
    }
    At(ex - er, ey - er, show) {
        Sphere(er * 2f, listOf(c(0x7fd8a0), c(0x2f7a5c), c(0x123528)))
    }

    // A point on the surface, turning under the bulge: the tide is the coast moving, not the sea.
    val spin = t * 0.9f
    val px = ex + cos(spin) * er
    val py = ey + sin(spin) * er
    At(px - 12f, py - 12f, show) {
        Sphere(24f, listOf(c(0xffe0a0), c(0xffa84c), c(0xb45a1c)))
    }

    val moonA = reveal(t, 2.4f)
    At(890f, 940f, moonA.opacity) {
        Sphere(120f, listOf(c(0xf6f8ff), c(0xc0c8d8), c(0x767f92)), glow = c(0x33c8d4ff))
    }
    Line(ex + er + 20f, ey, 880f, 1000f, c(0xdfe6ff).copy(alpha = 0.3f), 3f, dash = true, alpha = moonA.opacity)
    At(820f, 1090f, moonA.opacity, modifier = Modifier.width(240.dp)) {
        Text("THE MOON", style = label(22f, c(0xdfe6ff)))
    }

    val s = reveal(t, 5.4f)
    At(90f, 1380f + s.ty, s.opacity) {
        LabeledStat(
            top = "BAY OF FUNDY, TIDE RANGE",
            topColor = c(0x8fd8ff),
            value = "16",
            unit = " m",
            sub = "a five-storey building, twice a day",
            valueSize = 120f,
        )
    }

    BottomLine(
        1640f, e3,
        body("There are two bulges, not one — the far side is water being ", "left behind", " as the Earth is pulled away from it."),
    )
}

// ───────────────────── 4 · the Amazon ─────────────────────

@Composable
fun BoxScope.DdAmazon(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("THE AMAZON", c(0x7fe0b4), 190f, e1)
    Head(headline("One river out-pours\nthe ", "next seven."), 86f, 244f, e2)

    // The river, widening left to right and then losing itself in the sea.
    val flow = animate(0f, 1f, 1.2f, 5.4f, Easing.easeInOutSine)(t)
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        val path = Path().apply {
            moveTo(0f, 700f * k)
            cubicTo(260f * k, 690f * k, 420f * k, 760f * k, 700f * k, 800f * k)
            lineTo(700f * k, 980f * k)
            cubicTo(420f * k, 940f * k, 260f * k, 800f * k, 0f, 790f * k)
            close()
        }
        drawPath(path, Color(0xFF6E5A38).copy(alpha = 0.8f * flow))

        // The plume: fresh, silted water still recognisable hundreds of kilometres offshore.
        val spread = flow * flow
        drawOval(
            brush = Brush.radialGradient(
                0f to Color(0xFF9E8248).copy(alpha = 0.55f * spread),
                0.6f to Color(0xFF5E7E6A).copy(alpha = 0.22f * spread),
                1f to Color.Transparent,
                center = Offset(760f * k, 890f * k),
                radius = 420f * k,
            ),
            topLeft = Offset(400f * k, 560f * k),
            size = Size(840f * k, 660f * k),
        )
    }

    val plumeA = reveal(t, 4.4f)
    At(700f, 1160f + plumeA.ty, plumeA.opacity, modifier = Modifier.width(340.dp)) {
        Text("fresh water, still\n400 km out to sea", style = head(28f, c(0x9edbb8)))
    }

    // Amazon against the next seven combined, as two bars.
    val barA = reveal(t, 6.0f)
    val grow = animate(0f, 1f, 6.0f, 8.4f, Easing.easeOutCubic)(t)
    At(90f, 1280f + barA.ty, barA.opacity) {
        Text("AMAZON", style = label(22f, c(0x7fe0b4)))
    }
    GradientRect(
        90f, 1320f, 820f * grow, 46f,
        arrayOf(0f to c(0x7fe0b4), 1f to c(0x2f8f6c)),
        vertical = false, corner = 10f, alpha = barA.opacity,
    )
    At(90f, 1400f + barA.ty, barA.opacity) {
        Text("THE NEXT SEVEN RIVERS, COMBINED", style = label(22f, c(0x7f98a8)))
    }
    GradientRect(
        90f, 1440f, 680f * grow, 46f,
        arrayOf(0f to c(0x53707f), 1f to c(0x2a3a44)),
        vertical = false, corner = 10f, alpha = barA.opacity,
    )

    val sA = reveal(t, 2.4f)
    At(90f, 1030f + sA.ty, sA.opacity) {
        LabeledStat(
            top = "INTO THE ATLANTIC, EVERY SECOND",
            topColor = c(0x7fe0b4),
            value = "209,000",
            unit = " m³",
            sub = "a fifth of all the river water on Earth",
            valueSize = 96f,
        )
    }

    BottomLine(1600f, e3, body("Ships once filled their barrels with ", "fresh water", " while still out of sight of land."))
}

// ───────────────────── 5 · the garbage patch ─────────────────────

@Composable
fun BoxScope.DdGyre(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("THE GREAT PACIFIC GARBAGE PATCH", c(0xffd05c), 190f, e1)
    Head(headline("Not an island.\nA ", "soup."), 92f, 244f, e2)

    val cx = 540f
    val cy = 1010f
    val spin = animate(0f, 1f, 1.0f, 12f, Easing.linear)(t)
    val gather = reveal(t, 1.0f).opacity

    Canvas(Modifier.fillMaxSize()) {
        if (gather <= 0.01f) return@Canvas
        val k = size.width / 1080f
        // The gyre itself: four faint arms of current.
        repeat(4) { arm ->
            val path = Path()
            var u = 0f
            val base = arm * 1.5708f + spin * 2f
            while (u <= 1f) {
                val r = (60f + u * 400f) * k
                val a = base + u * 3.6f
                val x = cx * k + cos(a) * r
                val y = cy * k + sin(a) * r * 0.78f
                if (u == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                u += 0.02f
            }
            drawPath(
                path,
                Color(0xFF4AA8FF).copy(alpha = 0.14f * gather),
                style = Stroke(width = 4f * k, cap = StrokeCap.Round),
            )
        }
        // The plastic: mostly fragments smaller than a fingernail, which is the honest picture.
        var s = 6151L
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        repeat(320) {
            val u = rnd()
            val base = rnd() * 6.2832f
            val r = (60f + u * 400f) * k
            val a = base + u * 3.6f + spin * 2f
            val x = cx * k + cos(a) * r
            val y = cy * k + sin(a) * r * 0.78f
            val sz = (1.6f + rnd() * 4.5f) * k
            val warm = rnd() > 0.7f
            drawRect(
                color = (if (warm) Color(0xFFFFD05C) else Color(0xFFE8F2F6)).copy(alpha = (0.35f + rnd() * 0.5f) * gather),
                topLeft = Offset(x, y),
                size = Size(sz, sz),
            )
        }
    }

    val s1 = reveal(t, 3.4f)
    At(90f, 1420f + s1.ty, s1.opacity) {
        LabeledStat(
            top = "AREA OF THE PATCH",
            topColor = c(0xffd05c),
            value = "1,600,000",
            unit = " km²",
            sub = "about three times the size of France",
            valueSize = 90f,
        )
    }

    val note = reveal(t, 6.4f)
    At(90f, 640f + note.ty, note.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "You could sail through it and see almost nothing. Most of it is fragments smaller than a grain of rice, spread through the water rather than floating on it.",
            style = head(32f, c(0xd8cdb0)),
        )
    }

    BottomLine(1650f, e3, body("Which is exactly why it is ", "so hard to clean up", "."))
}

// ───────────────────── 6 · the global conveyor ─────────────────────

@Composable
fun BoxScope.DdConveyor(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("THE GLOBAL CONVEYOR", c(0x8fc8ff), 190f, e1)
    Head(headline("One lap of the ocean\ntakes ", "a thousand years."), 74f, 244f, e2)

    val yWarm = 800f
    val yCold = 1220f
    val draw = animate(0f, 1f, 1.2f, 6.0f, Easing.easeInOutSine)(t)

    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        // Warm, shallow, going one way; cold, deep, coming back. The whole system in two ribbons.
        val warm = Path().apply {
            moveTo(80f * k, yWarm * k)
            cubicTo(400f * k, (yWarm - 90f) * k, 700f * k, (yWarm + 80f) * k, 1000f * k, yWarm * k)
        }
        val cold = Path().apply {
            moveTo(1000f * k, yCold * k)
            cubicTo(700f * k, (yCold + 90f) * k, 400f * k, (yCold - 80f) * k, 80f * k, yCold * k)
        }
        drawPath(warm, Color(0xFFFF8A5C).copy(alpha = 0.75f * draw), style = Stroke(width = 16f * k, cap = StrokeCap.Round))
        drawPath(cold, Color(0xFF5C9AFF).copy(alpha = 0.75f * draw), style = Stroke(width = 16f * k, cap = StrokeCap.Round))
        // The two turns: sinking in the north, welling up again far away.
        drawArc(
            color = Color(0xFFBFD8FF).copy(alpha = 0.6f * draw),
            startAngle = -90f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(1000f * k - 0f, yWarm * k),
            size = Size((yCold - yWarm) * k, (yCold - yWarm) * k),
            style = Stroke(width = 16f * k, cap = StrokeCap.Round),
        )
        drawArc(
            color = Color(0xFFBFD8FF).copy(alpha = 0.6f * draw),
            startAngle = 90f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(80f * k - (yCold - yWarm) * k, yWarm * k),
            size = Size((yCold - yWarm) * k, (yCold - yWarm) * k),
            style = Stroke(width = 16f * k, cap = StrokeCap.Round),
        )
    }

    // A single parcel of water riding the loop.
    val u = (t * 0.16f) % 1f
    val px: Float
    val py: Float
    if (u < 0.5f) {
        val q = u / 0.5f
        px = 80f + 920f * q
        py = yWarm - 60f * sin(q * 3.14159f)
    } else {
        val q = (u - 0.5f) / 0.5f
        px = 1000f - 920f * q
        py = yCold + 60f * sin(q * 3.14159f)
    }
    At(px - 18f, py - 18f, draw) {
        Sphere(36f, listOf(c(0xffffff), c(0xbfe0ff), c(0x5c8fc8)), glow = c(0x558fc8ff))
    }

    val l1 = reveal(t, 3.0f)
    At(120f, yWarm - 110f + l1.ty, l1.opacity, modifier = Modifier.width(520.dp)) {
        Text("WARM, SHALLOW, HEADING NORTH", style = label(22f, c(0xff8a5c)))
    }
    val l2 = reveal(t, 4.2f)
    At(120f, yCold + 60f + l2.ty, l2.opacity, modifier = Modifier.width(560.dp)) {
        Text("COLD, SALTY, CRAWLING BACK ALONG THE FLOOR", style = label(22f, c(0x5c9aff)))
    }

    val sA = reveal(t, 6.4f)
    At(90f, 1420f + sA.ty, sA.opacity) {
        LabeledStat(
            top = "FOR ONE CIRCUIT",
            topColor = c(0x8fc8ff),
            value = "1,000",
            unit = " years",
            sub = "water sinking off Greenland today surfaces in the Pacific",
            valueSize = 110f,
        )
    }

    BottomLine(
        1660f, e3,
        body("It moves more heat than any ocean current has any business moving. It is ", "why Europe is warm", "."),
    )
}
