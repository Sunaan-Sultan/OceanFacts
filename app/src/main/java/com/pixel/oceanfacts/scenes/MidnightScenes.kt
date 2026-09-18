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
import com.pixel.oceanfacts.ui.Sphere
import kotlin.math.cos
import kotlin.math.sin

// ───────────────────── 1 · eternal night ─────────────────────

/**
 * The whole water column as one bar, with the lit part drawn to scale on it. The sliver at the
 * top is the entire world anyone has ever photographed in daylight.
 */
@Composable
fun BoxScope.MidDark(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("1,000 TO 4,000 METRES", c(0x59d8f0), 190f, e1)
    Head(headline("Most of the living world\nhas ", "never seen the sun."), 76f, 244f, e2)

    val barX = 120f
    val barY = 640f
    val barW = 210f
    val barH = 860f
    val grow = animate(0f, 1f, 1.2f, 4.6f, Easing.easeInOutCubic)(t)

    GradientRect(
        x = barX, y = barY, w = barW, h = barH * grow,
        stops = arrayOf(
            0f to c(0x2fb9c6),
            0.02f to c(0x0e7294),
            0.10f to c(0x042a44),
            0.35f to c(0x01121e),
            1f to c(0x000508),
        ),
        corner = 14f,
    )
    // The lit layer, at true scale: 200 m of 11,000 is under two percent of the bar.
    Rect(barX, barY, barW, barH * (200f / 11000f), c(0xd8fbff), alpha = 0.9f * grow, corner = 6f)

    val litA = reveal(t, 3.0f)
    Line(barX + barW, barY + barH * (200f / 11000f), barX + barW + 60f, barY + 90f, c(0xd8fbff).copy(alpha = 0.5f), 3f, alpha = litA.opacity)
    At(barX + barW + 70f, barY + 60f, litA.opacity, modifier = Modifier.width(560.dp)) {
        Text("everything you have ever\nseen lit by the sun", style = head(30f, c(0xd8fbff)))
    }

    val counter = interpolate(listOf(3.4f, 7.4f), listOf(0f, 90f), Easing.easeOutCubic)(t)
    val cA = reveal(t, 3.4f)
    At(barX + barW + 70f, 900f + cA.ty, cA.opacity) {
        LabeledStat(
            top = "OF EARTH LIVING SPACE",
            topColor = c(0x59d8f0),
            value = "${counter.toInt()}",
            unit = " %+",
            sub = "is deep water, in permanent darkness",
            valueSize = 128f,
        )
    }

    BottomLine(
        1620f, e3,
        body("Below a thousand metres there is no day, no season and no ", "sunrise", " — only what glows."),
    )
}

// ───────────────────── 2 · four degrees, everywhere ─────────────────────

@Composable
fun BoxScope.MidCold(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("DEEP OCEAN TEMPERATURE", c(0x8fd4ff), 190f, e1)
    Head(headline("Tropical or polar,\ndown here it is ", "4 °C."), 78f, 244f, e2)

    // A temperature-against-depth profile: warm skin, a sharp thermocline, then flat for ever.
    val xL = 150f
    val xR = 760f
    val yT = 700f
    val yB = 1440f
    val profile = listOf(
        0 to 26f, 100 to 24f, 200 to 18f, 350 to 12f,
        600 to 8f, 1000 to 5f, 2000 to 4f, 4000 to 3f,
    )
    fun xOf(tempC: Float) = xL + (xR - xL) * (tempC / 28f)
    fun yOf(m: Int) = yT + (yB - yT) * (m / 4000f)

    val axisA = reveal(t, 1.1f)
    Line(xL, yT, xL, yB, Color.White.copy(alpha = 0.18f), 3f, alpha = axisA.opacity)
    Line(xL, yB, xR, yB, Color.White.copy(alpha = 0.18f), 3f, alpha = axisA.opacity)
    listOf(0, 10, 20).forEach { temp ->
        At(xOf(temp.toFloat()) - 40f, yB + 16f, axisA.opacity, modifier = Modifier.width(80.dp)) {
            Text("$temp°", style = label(22f, c(0x7f98a8), FontWeight.Medium))
        }
    }
    listOf(0, 1000, 2000, 4000).forEach { m ->
        At(xL - 220f, yOf(m) - 18f, axisA.opacity, modifier = Modifier.width(200.dp)) {
            Text(metres(m), style = label(21f, c(0x7f98a8), FontWeight.Medium))
        }
    }

    val draw = animate(0f, 1f, 1.5f, 7.0f, Easing.easeInOutSine)(t)
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        val path = Path()
        val cut = profile.size * draw
        profile.forEachIndexed { i, (m, temp) ->
            if (i > cut) return@forEachIndexed
            val p = Offset(xOf(temp) * k, yOf(m) * k)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        drawPath(
            path,
            brush = Brush.verticalGradient(
                0f to Color(0xFFFFC46A),
                0.35f to Color(0xFF6FD8E8),
                1f to Color(0xFF7FA8FF),
                startY = yT * k,
                endY = yB * k,
            ),
            style = Stroke(width = 8f * k, cap = StrokeCap.Round),
        )
    }

    val warm = reveal(t, 3.0f)
    At(xOf(24f) + 20f, yOf(60) - 20f, warm.opacity, modifier = Modifier.width(280.dp)) {
        Text("the warm skin\nyou swim in", style = head(28f, c(0xffc46a)))
    }
    val cold = reveal(t, 6.4f)
    At(xOf(4f) + 40f, yOf(2400) - 40f, cold.opacity, modifier = Modifier.width(420.dp)) {
        Text("and then nothing\nchanges again", style = head(30f, c(0x8fd4ff)))
    }

    BottomLine(
        1560f, e3,
        body("Ninety per cent of the ocean sits between ", "0 and 4 °C", ", from the equator to the poles."),
    )
}

// ───────────────────── 3 · the lure ─────────────────────

@Composable
fun BoxScope.MidAngler(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.8f)

    // The lure is on screen from the very first frame; the animal behind it is not.
    val bob = sin(t * 0.9f) * 34f + swell(t, 0.4f) * 16f
    val lureX = 700f
    val lureY = 980f + bob
    val pulse = 0.55f + 0.45f * ((sin(t * 1.7f) + 1f) / 2f)

    Glow(lureX, lureY, 300f, c(0x99b6ffe8), pulse)
    At(lureX - 16f, lureY - 16f, 1f) {
        Sphere(32f, listOf(c(0xffffff), c(0xc8fff0), c(0x4fd8c0)))
    }

    // The fish resolves out of the dark it was hiding in.
    val show = animate(0f, 1f, 4.6f, 8.6f, Easing.easeInOutSine)(t)
    At(150f, 900f + bob * 0.6f, show * 0.92f) {
        SeaBody(
            lengthUnits = 520f,
            kind = BodyKind.FISH,
            colors = listOf(c(0x2a3b42), c(0x141f26), c(0x060c10)),
            swimT = t * 0.5f,
        )
    }
    // The stalk, drawn from the head up and over to the lure.
    Line(430f, 900f + bob * 0.6f + 60f, lureX, lureY, c(0x4fd8c0).copy(alpha = 0.55f * show), 4f)

    Eyebrow("THE ANGLERFISH", c(0x6fffe0), 190f, e1)
    Head(headline("A light, in a place\nwith ", "no light."), 92f, 244f, e2)

    val note = reveal(t, 6.0f)
    At(90f, 1340f + note.ty, note.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "The glow is not hers. It comes from bacteria she keeps alive inside the lure, and cannot make without them.",
            style = head(34f, c(0xa8cfd8)),
        )
    }

    BottomLine(
        1620f, e3,
        body("Everything down here is drawn to light, because light means ", "food", ". That is the trap."),
    )
}

// ───────────────────── 4 · the male that fuses ─────────────────────

@Composable
fun BoxScope.MidFusion(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.8f)

    Eyebrow("SEXUAL PARASITISM", c(0xffa0c0), 190f, e1)
    Head(headline("He finds her once,\nand ", "never lets go."), 86f, 244f, e2)

    val femaleX = 240f
    val femaleY = 860f
    val bob = swell(t, 0.4f) * 18f

    At(femaleX, femaleY + bob, reveal(t, 1.2f).opacity) {
        SeaBody(
            lengthUnits = 460f,
            kind = BodyKind.FISH,
            colors = listOf(c(0x37505c), c(0x1b2a33), c(0x080f14)),
            swimT = t * 0.4f,
        )
    }
    At(femaleX + 40f, femaleY - 60f + bob, reveal(t, 1.2f).opacity) {
        Text("FEMALE · up to 1 m", style = label(22f, c(0x9fbcc8)))
    }

    // He crosses, attaches, and then simply stops being a separate animal.
    val approach = animate(1040f, 610f, 2.0f, 6.4f, Easing.easeInOutCubic)(t)
    val merge = animate(1f, 0.42f, 6.6f, 9.6f, Easing.easeInOutCubic)(t)
    val fade = animate(1f, 0.55f, 7.4f, 10.4f, Easing.easeInOutSine)(t)

    At(approach, femaleY + 120f + bob, reveal(t, 2.0f).opacity * fade) {
        SeaBody(
            lengthUnits = 92f * merge,
            kind = BodyKind.FISH,
            colors = listOf(c(0x5f7a86), c(0x33474f), c(0x121b20)),
            swimT = t * 1.6f,
        )
    }
    val mA = reveal(t, 2.4f)
    At(approach + 40f, femaleY + 190f + bob, mA.opacity * fade, modifier = Modifier.width(400.dp)) {
        Text("MALE · a few centimetres", style = label(22f, c(0xffa0c0)))
    }

    val s = reveal(t, 7.6f)
    At(90f, 1260f + s.ty, s.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "He bites on, their skin grows together, their bloodstreams join, and he dissolves into her until only his gonads are left.",
            style = head(34f, c(0xd4bcc8)),
        )
    }

    BottomLine(
        1600f, e3,
        body("A female can carry ", "several", " of them at once. Nobody down here gets a second chance to meet."),
    )
}

// ───────────────────── 5 · a whale fall ─────────────────────

@Composable
fun BoxScope.MidWhaleFall(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 11.0f)

    Eyebrow("A WHALE FALL", c(0xd8c08f), 190f, e1)
    Head(headline("One death\nfeeds ", "a century."), 92f, 244f, e2)

    val floorY = 1420f
    SeaFloor(floorY, c(0x0d2230), roughness = 0.18f, seed = 771L, alpha = reveal(t, 1.0f).opacity)

    // Down, land, and then the long afterwards.
    val sink = interpolate(listOf(1.2f, 6.0f), listOf(520f, floorY - 130f), Easing.easeInOutSine)(t)
    val settle = clamp((t - 6.0f) / 1.2f, 0f, 1f)
    val tilt = settle * 10f

    At(300f + swell(t, 0.3f) * (1f - settle) * 26f, sink, reveal(t, 1.0f).opacity) {
        SeaBody(
            lengthUnits = 500f,
            kind = BodyKind.WHALE,
            colors = listOf(c(0x93a6b0), c(0x4d626e), c(0x1d2b34)),
            swimT = (1f - settle) * t,
        )
    }

    // Sediment kicked up on impact, then the bloom.
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        if (settle > 0.02f) {
            val puff = (1f - settle).coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFF6E5A42).copy(alpha = 0.40f * puff),
                    1f to Color.Transparent,
                    center = Offset(540f * k, (floorY - 40f) * k),
                    radius = 380f * k * settle,
                ),
                radius = 380f * k * settle,
                center = Offset(540f * k, (floorY - 40f) * k),
            )
        }
        val life = clamp((t - 7.4f) / 4.2f, 0f, 1f)
        if (life <= 0.01f) return@Canvas
        var s = 8191L
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        repeat(150) { i ->
            if (i / 150f > life) return@repeat
            val a = rnd() * 6.2832f
            val d = rnd()
            val x = 540f + cos(a) * d * 330f
            val y = floorY - 60f + sin(a) * d * 120f
            val r = (2f + rnd() * 5f) * k
            val hue = rnd()
            val col = if (hue > 0.7f) Color(0xFFFF9E5C) else Color(0xFF7FE0C8)
            drawCircle(
                col.copy(alpha = (0.35f + rnd() * 0.5f) * life),
                radius = r,
                center = Offset(x * k, y * k),
            )
        }
    }

    // A clock on the whole thing, so the timescale is on screen rather than only described.
    val years = interpolate(listOf(7.4f, 11.6f), listOf(0f, 50f), Easing.easeOutCubic)(t)
    val yA = reveal(t, 7.4f)
    At(90f, 1000f + yA.ty, yA.opacity) {
        LabeledStat(
            top = "TIME SINCE IT LANDED",
            topColor = c(0xd8c08f),
            value = "${years.toInt()}",
            unit = " years",
            sub = "and the bones are still being eaten",
            valueSize = 120f,
        )
    }

    BottomLine(
        1620f, e3,
        body("Sharks first, then worms, then bacteria living on the ", "bone oil", " — whole species exist nowhere else."),
    )
}

// ───────────────────── 6 · the deepest octopus ─────────────────────

@Composable
fun BoxScope.MidDumbo(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.8f)

    Eyebrow("THE DUMBO OCTOPUS", c(0xffb07f), 190f, e1)
    Head(headline("The deepest octopus\never ", "filmed."), 88f, 244f, e2)

    // It does not swim so much as flap, so the fins do the work and the body barely moves.
    val drift = interpolate(listOf(1.2f, 11.5f), listOf(700f, 1080f), Easing.easeInOutSine)(t)
    val sway = swell(t, 0.5f) * 40f
    At(330f + sway, drift, reveal(t, 1.0f).opacity) {
        SeaBody(
            lengthUnits = 420f,
            kind = BodyKind.OCTOPUS,
            colors = listOf(c(0xffd0b0), c(0xd4785a), c(0x6b2c22)),
            swimT = t * 2.4f,
            glow = c(0x22ff9e5c),
        )
    }

    val depth = interpolate(listOf(1.4f, 9.0f), listOf(0f, 6957f), Easing.easeInOutCubic)(t)
    val dA = reveal(t, 1.4f)
    At(90f, 640f + dA.ty, dA.opacity) {
        LabeledStat(
            top = "DEPTH",
            topColor = c(0xffb07f),
            value = fmt(depth),
            unit = " m",
            sub = "filmed in the Java Trench, 2020",
            valueSize = 128f,
        )
    }

    val note = reveal(t, 7.4f)
    At(90f, 1400f + note.ty, note.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "Most octopuses live in the shallows. This one flaps its ear-like fins nearly seven kilometres down, under 700 atmospheres.",
            style = head(34f, c(0xd6bcae)),
        )
    }

    BottomLine(
        1660f, e3,
        body("It has no ink sac. Down here there is ", "nothing to hide from", " in a cloud of black."),
    )
}
