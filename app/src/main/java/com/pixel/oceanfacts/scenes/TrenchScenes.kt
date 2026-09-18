package com.pixel.oceanfacts.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.cos
import kotlin.math.sin

private const val TRENCH_TOP_Y = 560f
private const val TRENCH_BOT_Y = 1460f
private const val TRENCH_MAX_M = 11000f

private fun depthY(m: Float) = TRENCH_TOP_Y + (TRENCH_BOT_Y - TRENCH_TOP_Y) * (m / TRENCH_MAX_M)

// ───────────────────── 1 · Challenger Deep ─────────────────────

/**
 * Everest, upright, standing on the floor of the Challenger Deep. Its summit is drawn where it
 * would actually be — two kilometres underwater — because that is the only way the number
 * 10,935 means anything.
 */
@Composable
fun BoxScope.TrChallenger(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 11.0f)

    Eyebrow("CHALLENGER DEEP", c(0x9fd0e8), 190f, e1)
    Head(headline("The deepest place\non ", "the planet."), 88f, 244f, e2)

    val floorM = 6000f
    val deepM = 10935f
    val everestM = 8849f
    val summitM = deepM - everestM

    val cut = animate(0f, 1f, 1.1f, 4.4f, Easing.easeInOutCubic)(t)

    // The seabed, and the notch cut out of it.
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        val floor = depthY(floorM) * k
        val apexY = depthY(deepM) * k
        val apex = 540f * k
        val left = 230f * k
        val right = 850f * k
        val path = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, floor)
            lineTo(left, floor)
            lineTo(apex - 26f * k, floor + (apexY - floor) * cut)
            lineTo(apex + 26f * k, floor + (apexY - floor) * cut)
            lineTo(right, floor)
            lineTo(size.width, floor)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                0f to Color(0xFF1B3446),
                1f to Color(0xFF060F17),
                startY = floor,
                endY = size.height,
            ),
        )
    }

    // Everest, standing on the bottom of the notch.
    val rise = animate(0f, 1f, 4.8f, 7.6f, Easing.easeOutCubic)(t)
    val mtA = reveal(t, 4.8f).opacity
    Canvas(Modifier.fillMaxSize()) {
        if (rise <= 0.01f) return@Canvas
        val k = size.width / 1080f
        val baseY = depthY(deepM) * k
        val fullTop = depthY(summitM) * k
        val topY = baseY + (fullTop - baseY) * rise
        val path = Path().apply {
            moveTo(540f * k, topY)
            lineTo(720f * k, baseY)
            lineTo(360f * k, baseY)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                0f to Color(0xFFE8F4FF).copy(alpha = 0.92f * mtA),
                0.25f to Color(0xFF9FB8CC).copy(alpha = 0.85f * mtA),
                1f to Color(0xFF3E5567).copy(alpha = 0.8f * mtA),
                startY = topY,
                endY = baseY,
            ),
        )
    }

    val sumA = reveal(t, 7.8f)
    Line(560f, depthY(summitM), 940f, depthY(summitM), c(0xffc46a).copy(alpha = 0.7f), 3f, dash = true, alpha = sumA.opacity)
    At(600f, depthY(summitM) - 96f, sumA.opacity, modifier = Modifier.width(420.dp)) {
        Text("Everest summit,\nstill 2,086 m under water", style = head(28f, c(0xffc46a)))
    }

    DepthRuler(
        x = 990f, yTop = TRENCH_TOP_Y, yBot = TRENCH_BOT_Y,
        topM = 0, botM = TRENCH_MAX_M.toInt(),
        marks = listOf(0, 4000, 8000),
        accent = c(0x7fb4d0),
        alpha = reveal(t, 1.6f).opacity,
        labelRight = false,
    )

    val statA = reveal(t, 2.2f)
    At(90f, 1560f + statA.ty, statA.opacity) {
        LabeledStat(
            top = "MARIANA TRENCH, WESTERN PACIFIC",
            topColor = c(0x9fd0e8),
            value = "10,935",
            unit = " m",
            sub = "measured to within six metres",
            valueSize = 128f,
        )
    }

    BottomLine(1800f, e3, body("Drop Everest in and you would still need ", "two kilometres", " of rope."))
}

// ───────────────────── 2 · the pressure ─────────────────────

@Composable
fun BoxScope.TrPressure(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.8f)

    Eyebrow("AT THE BOTTOM", c(0xffa46a), 190f, e1)
    Head(headline("Eight tonnes on\nevery ", "square inch."), 86f, 244f, e2)

    val descend = interpolate(listOf(1.4f, 8.4f), listOf(0f, 10935f), Easing.easeInOutSine)(t)
    val bar = descend / 10.06f     // one bar per roughly ten metres of seawater
    val shrink = 1f - 0.62f * (descend / 10935f)

    // The cup. Every submersible crew sends one down; it comes back a thimble.
    val cupA = reveal(t, 1.4f).opacity
    Canvas(Modifier.fillMaxSize()) {
        if (cupA <= 0.01f) return@Canvas
        val k = size.width / 1080f
        val cx = 760f * k
        val cy = 1030f * k
        val h = 300f * k * shrink
        val wTop = 200f * k * shrink
        val wBot = 130f * k * shrink
        val path = Path().apply {
            moveTo(cx - wTop / 2f, cy - h / 2f)
            lineTo(cx + wTop / 2f, cy - h / 2f)
            lineTo(cx + wBot / 2f, cy + h / 2f)
            lineTo(cx - wBot / 2f, cy + h / 2f)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                0f to Color(0xFFF2F6F8).copy(alpha = 0.92f * cupA),
                1f to Color(0xFF93A7B2).copy(alpha = 0.9f * cupA),
                startY = cy - h / 2f,
                endY = cy + h / 2f,
            ),
        )
        drawLine(
            Color(0xFFD6E2E8).copy(alpha = cupA),
            Offset(cx - wTop / 2f, cy - h / 2f),
            Offset(cx + wTop / 2f, cy - h / 2f),
            strokeWidth = 7f * k,
            cap = StrokeCap.Round,
        )
        // Arrows pressing in from every side, growing with the depth.
        val push = (descend / 10935f)
        repeat(12) { i ->
            val a = i / 12f * 6.2832f
            val r0 = 210f * k
            val r1 = r0 - 60f * k * push
            drawLine(
                Color(0xFFFF9E5C).copy(alpha = 0.18f + 0.55f * push),
                Offset(cx + cos(a) * r0, cy + sin(a) * r0 * 0.9f),
                Offset(cx + cos(a) * r1, cy + sin(a) * r1 * 0.9f),
                strokeWidth = 6f * k,
                cap = StrokeCap.Round,
            )
        }
    }

    val gA = reveal(t, 1.6f)
    At(90f, 700f + gA.ty, gA.opacity) {
        LabeledStat(
            top = "DEPTH",
            topColor = c(0x9fd0e8),
            value = fmt(descend),
            unit = " m",
            sub = null,
            valueSize = 116f,
        )
    }
    At(90f, 900f + gA.ty, gA.opacity) {
        LabeledStat(
            top = "PRESSURE",
            topColor = c(0xffa46a),
            value = fmt(bar),
            unit = " bar",
            sub = "a thousand times what you feel now",
            valueSize = 116f,
        )
    }

    val note = reveal(t, 8.6f)
    At(90f, 1300f + note.ty, note.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "A polystyrene cup strapped to a submersible comes back the size of a thimble, with every detail still on it.",
            style = head(34f, c(0xd6bcae)),
        )
    }

    BottomLine(
        1620f, e3,
        body("It is the weight of ", "fifty jumbo jets", " stacked on a person, and there are animals living in it."),
    )
}

// ───────────────────── 3 · the deepest fish ─────────────────────

@Composable
fun BoxScope.TrSnailfish(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 11.0f)

    Eyebrow("THE SNAILFISH", c(0xffd0b8), 190f, e1)
    Head(headline("There is a depth\nno fish ", "can pass."), 88f, 244f, e2)

    val limitM = 8400f
    val recordM = 8336f

    DepthRuler(
        x = 300f, yTop = TRENCH_TOP_Y, yBot = TRENCH_BOT_Y,
        topM = 0, botM = TRENCH_MAX_M.toInt(),
        marks = listOf(0, 4000, 8000, 10935),
        accent = c(0x7fb4d0),
        alpha = reveal(t, 1.1f).opacity,
        progress = animate(0f, 1f, 1.1f, 4.4f, Easing.easeInOutSine)(t),
    )

    // The wall: not a floor, but the point where the chemistry that keeps cells working gives out.
    val wallA = reveal(t, 5.6f)
    Rect(300f, depthY(limitM), 700f, 5f, c(0xff6b5a), alpha = wallA.opacity * 0.9f)
    GradientRect(
        x = 300f, y = depthY(limitM), w = 700f, h = TRENCH_BOT_Y - depthY(limitM),
        stops = arrayOf(0f to c(0xff6b5a).copy(alpha = 0.16f), 1f to Color.Transparent),
        alpha = wallA.opacity,
    )
    At(320f, depthY(limitM) + 18f, wallA.opacity, modifier = Modifier.width(640.dp)) {
        Text("BELOW HERE, NO FISH AT ALL", style = label(24f, c(0xff8f7a)))
    }

    // The record holder, hanging just above it.
    val fishY = interpolate(listOf(1.6f, 5.2f), listOf(TRENCH_TOP_Y + 40f, depthY(recordM) - 40f), Easing.easeInOutSine)(t)
    At(420f + swell(t, 0.5f) * 26f, fishY, reveal(t, 1.6f).opacity) {
        SeaBody(
            lengthUnits = 260f,
            kind = BodyKind.FISH,
            colors = listOf(c(0xffe4d4), c(0xe0b49c), c(0x8a5a48)),
            swimT = t * 1.6f,
        )
    }

    val sA = reveal(t, 7.0f)
    At(90f, 1560f + sA.ty, sA.opacity) {
        LabeledStat(
            top = "DEEPEST FISH EVER FILMED",
            topColor = c(0xffd0b8),
            value = "8,336",
            unit = " m",
            sub = "a snailfish, Izu-Ogasawara Trench, 2022",
            valueSize = 116f,
        )
    }

    BottomLine(
        1780f, e3,
        body("The chemical that protects their cells stops working at about ", "8,400 m", ". Below that, the trench is fishless."),
    )
}

// ───────────────────── 4 · life without the sun ─────────────────────

@Composable
fun BoxScope.TrVents(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.8f)

    Eyebrow("HYDROTHERMAL VENTS", c(0xff8a4c), 190f, e1)
    Head(headline("A whole world\nthat ignores ", "the sun."), 86f, 244f, e2)

    val floorY = 1420f
    SeaFloor(floorY, c(0x140f14), roughness = 0.4f, seed = 515L, alpha = reveal(t, 1.0f).opacity)

    val chimneyA = reveal(t, 1.4f).opacity
    Canvas(Modifier.fillMaxSize()) {
        if (chimneyA <= 0.01f) return@Canvas
        val k = size.width / 1080f
        // The chimney: mineral, built by the vent out of what it precipitates.
        val path = Path().apply {
            moveTo(430f * k, floorY * k)
            lineTo(478f * k, 1010f * k)
            lineTo(560f * k, 990f * k)
            lineTo(600f * k, floorY * k)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                0f to Color(0xFF5A3A2E).copy(alpha = chimneyA),
                1f to Color(0xFF1C1114).copy(alpha = chimneyA),
                startY = 980f * k,
                endY = floorY * k,
            ),
        )

        // The smoke: superheated water full of metal sulphides, which is what makes it black.
        val plume = clamp((t - 2.2f) / 2.0f, 0f, 1f)
        if (plume > 0.01f) {
            var s = 4357L
            val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
            repeat(70) {
                val ph = rnd() * 6.28f
                val speed = 0.10f + rnd() * 0.14f
                val u = ((rnd() + t * speed) % 1f)
                val y = 995f - u * 780f
                val spread = 18f + u * 210f
                val x = 519f + sin(t * 0.8f + ph) * spread + (rnd() - 0.5f) * spread
                val r = (7f + u * 34f) * k
                drawCircle(
                    Color(0xFF1A1418).copy(alpha = (0.55f * (1f - u) * plume).coerceIn(0f, 1f)),
                    radius = r,
                    center = Offset(x * k, y * k),
                )
            }
        }
    }

    // The heat at the mouth.
    Glow(519f, 1000f, 190f, c(0x88ff7a3c), reveal(t, 2.0f).opacity)

    // Tube worms: white stalks, red plumes, and no interest whatever in sunlight.
    val wormA = reveal(t, 4.0f).opacity
    Canvas(Modifier.fillMaxSize()) {
        if (wormA <= 0.01f) return@Canvas
        val k = size.width / 1080f
        var s = 2027L
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        repeat(26) {
            val x = (250f + rnd() * 600f)
            val h = 90f + rnd() * 130f
            val ph = rnd() * 6.28f
            val lean = sin(t * 0.9f + ph) * 16f
            val top = floorY - h
            drawLine(
                Color(0xFFE8DCD0).copy(alpha = 0.85f * wormA),
                Offset(x * k, floorY * k),
                Offset((x + lean) * k, top * k),
                strokeWidth = 9f * k,
                cap = StrokeCap.Round,
            )
            drawCircle(
                Color(0xFFE04A3C).copy(alpha = 0.92f * wormA),
                radius = 11f * k,
                center = Offset((x + lean) * k, top * k),
            )
        }
    }

    val s1 = reveal(t, 5.0f)
    At(90f, 640f + s1.ty, s1.opacity) {
        LabeledStat(
            top = "VENT FLUID",
            topColor = c(0xff8a4c),
            value = "400",
            unit = " °C",
            sub = "too pressurised to boil",
            valueSize = 120f,
        )
    }

    val note = reveal(t, 6.8f)
    At(90f, 880f + note.ty, note.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "Nothing here eats sunlight. The bottom of the food chain is bacteria living on hydrogen sulphide — the smell of rotten eggs, turned into a whole ecosystem.",
            style = head(34f, c(0xe0c0a8)),
        )
    }

    BottomLine(
        1600f, e3,
        body("Until 1977 nobody thought life could work ", "without the sun", ". Then we looked."),
    )
}

// ───────────────────── 5 · more people have walked on the Moon ─────────────────────

@Composable
fun BoxScope.TrVisits(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.8f)

    Eyebrow("WHO HAS BEEN THERE", c(0xbfe0f0), 190f, e1)
    Head(headline("More people have\nstood ", "on the Moon."), 84f, 244f, e2)

    VisitorColumn(t, 96f, 12, 0xdfe6ff, 1.4f, "WALKED ON THE MOON", "12 people")
    VisitorColumn(t, 600f, 27, 0x7fd4e8, 3.4f, "REACHED CHALLENGER DEEP", "27 people")

    val note = reveal(t, 8.0f)
    At(90f, 1420f + note.ty, note.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "The Moon is 384,400 km away. The bottom of the Challenger Deep is eleven kilometres from a boat.",
            style = head(34f, c(0xc0d6e0)),
        )
    }

    BottomLine(
        1640f, e3,
        body("Space is far. The deep sea is ", "hard", " — and nobody has ever been paid to go."),
    )
}

/** One tally of people, laid out four to a row and arriving one at a time. */
@Composable
private fun BoxScope.VisitorColumn(
    t: Float,
    x: Float,
    count: Int,
    tint: Long,
    startAt: Float,
    caption: String,
    sub: String,
) {
    val cA = reveal(t, startAt)
    At(x, 760f + cA.ty, cA.opacity, modifier = Modifier.width(400.dp)) {
        Text(caption, style = label(24f, c(tint)))
    }
    At(x, 794f + cA.ty, cA.opacity, modifier = Modifier.width(400.dp)) {
        Text(sub, style = head(46f, Color.White))
    }
    repeat(count) { i ->
        val a = reveal(t, startAt + i * 0.10f, 0.5f, Easing.easeOutCubic)
        At(x + (i % 4) * 92f, 900f + (i / 4) * 132f, a.opacity) {
            SeaBody(
                lengthUnits = 108f,
                kind = BodyKind.HUMAN,
                colors = listOf(c(tint), c(tint), c(tint)),
            )
        }
    }
}

// ───────────────────── 6 · the unmapped floor ─────────────────────

@Composable
fun BoxScope.TrMapped(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.8f)

    Eyebrow("WHAT WE HAVE CHARTED", c(0x7fd4e8), 190f, e1)
    Head(headline("We have better maps\nof ", "Mars."), 88f, 244f, e2)

    // A grid, filling in to the fraction of the seafloor that has actually been surveyed.
    val cols = 12
    val rows = 9
    val cell = 66f
    val gridX = 120f
    val gridY = 740f
    val fill = animate(0f, 0.26f, 1.6f, 7.0f, Easing.easeInOutCubic)(t)

    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        var s = 1237L
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        val order = (0 until cols * rows).map { it to rnd() }.sortedBy { it.second }
        val litCount = (order.size * fill).toInt()
        order.forEachIndexed { rank, (idx, _) ->
            val cx = gridX + (idx % cols) * cell
            val cy = gridY + (idx / cols) * cell
            val lit = rank < litCount
            drawRect(
                color = if (lit) Color(0xFF7FD4E8).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.06f),
                topLeft = Offset(cx * k, cy * k),
                size = androidx.compose.ui.geometry.Size((cell - 7f) * k, (cell - 7f) * k),
            )
        }
    }

    val pct = fill * 100f
    val pA = reveal(t, 2.0f)
    At(120f, 1380f + pA.ty, pA.opacity) {
        LabeledStat(
            top = "OF THE SEAFLOOR MAPPED IN DETAIL",
            topColor = c(0x7fd4e8),
            value = "${pct.toInt()}",
            unit = " %",
            sub = "the rest is guessed from satellite gravity",
            valueSize = 128f,
        )
    }

    val marsA = reveal(t, 7.6f)
    At(760f, 500f, marsA.opacity) {
        com.pixel.oceanfacts.ui.Sphere(
            sizeUnits = 190f,
            colors = listOf(c(0xffbb88), c(0xd2703f), c(0x6b2c18)),
            glow = c(0x33ff8a4c),
        )
    }
    At(700f, 700f, marsA.opacity, modifier = Modifier.width(320.dp)) {
        Text("Mars: all of it,\nin higher resolution", style = head(28f, c(0xffbb88)))
    }

    BottomLine(
        1620f, e3,
        body("Most of our own planet is a ", "blur", " — and almost all of it is under water."),
    )
}
