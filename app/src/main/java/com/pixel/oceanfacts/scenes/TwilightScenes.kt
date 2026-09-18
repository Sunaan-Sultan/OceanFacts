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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.BodyKind
import com.pixel.oceanfacts.core.Easing
import com.pixel.oceanfacts.core.animate
import com.pixel.oceanfacts.core.interpolate
import com.pixel.oceanfacts.core.reveal
import com.pixel.oceanfacts.core.swell
import com.pixel.oceanfacts.ui.SeaBody
import com.pixel.oceanfacts.ui.Sphere
import kotlin.math.sin

// ───────────────────── 1 · arriving in the twilight zone ─────────────────────

@Composable
fun BoxScope.TwiEnter(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.4f)

    Eyebrow("200 TO 1,000 METRES", c(0x9aa4ee), 190f, e1)
    Head(headline("A blue so faint\nyour eyes ", "invent it."), 92f, 244f, e2)

    val yTop = 640f
    val yBot = 1500f
    val botM = 1200

    fun yOf(m: Int) = yTop + (yBot - yTop) * m / botM.toFloat()

    val bandA = reveal(t, 1.6f, 0.9f)
    GradientRect(
        x = 96f, y = yOf(200), w = 500f, h = yOf(1000) - yOf(200),
        stops = arrayOf(
            0f to c(0x6f7ae0).copy(alpha = 0.26f),
            1f to c(0x2a2f66).copy(alpha = 0.10f),
        ),
        corner = 14f,
        alpha = bandA.opacity,
    )
    At(120f, yOf(200) + 18f, bandA.opacity) {
        Text("THE TWILIGHT ZONE", style = label(24f, c(0xa9b2ff)))
    }

    DepthRuler(
        x = 700f, yTop = yTop, yBot = yBot,
        topM = 0, botM = botM,
        marks = listOf(0, 200, 1000),
        accent = c(0x8f9bf0),
        alpha = reveal(t, 1.2f).opacity,
        progress = animate(0f, 1f, 1.2f, 5.0f, Easing.easeInOutSine)(t),
    )

    // A submersible falling through the band, which is how anyone has ever seen this.
    val fall = interpolate(listOf(2.0f, 11.0f), listOf(yTop - 120f, yBot - 40f), Easing.easeInOutSine)(t)
    val sway = swell(t, 0.6f) * 22f
    At(250f + sway, fall, reveal(t, 2.0f).opacity) {
        SeaBody(
            lengthUnits = 190f,
            kind = BodyKind.SUB,
            colors = listOf(c(0xf0d9a0), c(0xc9a052), c(0x6d4a1c)),
            swimT = t,
        )
    }

    val note = reveal(t, 6.2f)
    At(90f, 1560f + note.ty, note.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "It holds more fish than the whole of the rest of the ocean put together, and almost none of them have names.",
            style = head(34f, c(0xc0c8f0)),
        )
    }

    BottomLine(1750f, e3, body("One percent of the surface light reaches ", "200 m", ". None of it reaches 1,000."))
}

// ───────────────────── 2 · marine snow ─────────────────────

@Composable
fun BoxScope.TwiSnow(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.0f)

    Eyebrow("MARINE SNOW", c(0xbcd4e4), 190f, e1)
    Head(headline("It has been snowing\nhere ", "for ever."), 92f, 244f, e2)

    // Big, near-camera flakes on top of the ambient ones the stage already draws.
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        var s = 99131L
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        repeat(46) {
            val fx = rnd()
            val fy0 = rnd()
            val r = (5f + rnd() * 16f) * k
            val fall = 0.020f + rnd() * 0.026f
            val ph = rnd() * 6.28f
            val y = ((fy0 + t * fall) % 1f) * size.height
            val x = fx * size.width + sin(t * 0.6f + ph) * 24f * k
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFE8F6FF).copy(alpha = 0.55f),
                    0.55f to Color(0xFFBBD8EA).copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    center = Offset(x, y),
                    radius = r * 2.2f,
                ),
                radius = r * 2.2f,
                center = Offset(x, y),
            )
        }
    }

    val s1 = reveal(t, 2.2f)
    At(90f, 940f + s1.ty, s1.opacity) {
        LabeledStat(
            top = "TO FALL ONE KILOMETRE",
            topColor = c(0xbcd4e4),
            value = "WEEKS",
            unit = null,
            sub = "some flakes take months to reach the floor",
            valueSize = 116f,
        )
    }

    val s2 = reveal(t, 4.4f)
    At(90f, 1200f + s2.ty, s2.opacity) {
        LabeledStat(
            top = "CARRIED DOWN EACH YEAR",
            topColor = c(0x7fe0b4),
            value = "BILLIONS",
            unit = " of tonnes",
            sub = "of carbon, taken out of the air and buried",
            valueSize = 96f,
        )
    }

    BottomLine(
        1600f, e3,
        body("It is not snow. It is the ", "remains of everything", " that lived above, falling for ever."),
    )
}

// ───────────────────── 3 · living light ─────────────────────

@Composable
fun BoxScope.TwiBiolum(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("BIOLUMINESCENCE", c(0x6effd2), 190f, e1)
    Head(headline("Down here,\nglowing is ", "normal."), 92f, 244f, e2)

    // Lights coming on one at a time, so the count in the read-out has something to count.
    val lit = animate(0f, 1f, 1.6f, 8.4f, Easing.easeInOutSine)(t)
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        var s = 7717L
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        val n = 64
        repeat(n) { i ->
            val gx = rnd()
            val gy = 0.30f + rnd() * 0.56f
            val r = (3f + rnd() * 7f) * k
            val ph = rnd() * 6.28f
            val order = i / n.toFloat()
            if (order > lit) return@repeat
            val age = ((lit - order) * 6f).coerceIn(0f, 1f)
            val pulse = 0.45f + 0.55f * ((sin(t * 2.1f + ph) + 1f) / 2f)
            val a = age * pulse
            val ctr = Offset(gx * size.width, gy * size.height)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFF6EFFD2).copy(alpha = 0.8f * a),
                    1f to Color.Transparent,
                    center = ctr,
                    radius = r * 6f,
                ),
                radius = r * 6f,
                center = ctr,
            )
            drawCircle(Color(0xFFE8FFF7).copy(alpha = a), radius = r, center = ctr)
        }
    }

    val counter = interpolate(listOf(2.0f, 8.0f), listOf(0f, 76f), Easing.easeOutCubic)(t)
    val cA = reveal(t, 1.9f)
    At(90f, 1080f + cA.ty, cA.opacity) {
        LabeledStat(
            top = "OF ANIMALS IN THE OPEN OCEAN",
            topColor = c(0x6effd2),
            value = counter.toInt().toString(),
            unit = " %",
            sub = "make their own light",
            valueSize = 150f,
        )
    }

    BottomLine(
        1560f, e3,
        body("On land it is a curiosity. In the sea it is ", "the majority", " — the commonest way to be alive."),
    )
}

// ───────────────────── 4 · counter-illumination ─────────────────────

/**
 * Two panels of the same fish against the same faint surface glow. On the left its belly is
 * dark and it is a silhouette; on the right the photophores come on and match the water, and
 * it disappears. The right-hand fish really is drawn — it just stops being visible.
 */
@Composable
fun BoxScope.TwiCounter(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("COUNTER-ILLUMINATION", c(0x9fd8ff), 190f, e1)
    Head(headline("The best place to hide\nis ", "inside the light."), 84f, 244f, e2)

    val panelY = 760f
    val panelH = 440f
    val glow = arrayOf(
        0f to c(0x7fc4e8).copy(alpha = 0.34f),
        1f to Color.Transparent,
    )

    val pa = reveal(t, 1.4f)
    GradientRect(96f, panelY, 400f, panelH, glow, corner = 18f, alpha = pa.opacity)
    GradientRect(584f, panelY, 400f, panelH, glow, corner = 18f, alpha = pa.opacity)

    // The lights ramp on halfway through, and the silhouette dissolves with them.
    val on = animate(0f, 1f, 4.6f, 7.4f, Easing.easeInOutSine)(t)
    val drift = swell(t, 0.45f) * 14f

    At(150f, panelY + 190f + drift, pa.opacity) {
        SeaBody(
            lengthUnits = 300f,
            kind = BodyKind.FISH,
            colors = listOf(c(0x0a1a24), c(0x06121a), c(0x030a10)),
            swimT = t,
        )
    }
    At(638f, panelY + 190f + drift, pa.opacity * (1f - on * 0.88f)) {
        SeaBody(
            lengthUnits = 300f,
            kind = BodyKind.FISH,
            colors = listOf(c(0x0a1a24), c(0x06121a), c(0x030a10)),
            swimT = t,
        )
    }
    // The photophore row itself, brightening along the belly.
    Canvas(Modifier.fillMaxSize()) {
        if (on <= 0.01f) return@Canvas
        val k = size.width / 1080f
        repeat(9) { i ->
            val x = (664f + i * 30f) * k
            val y = (panelY + 258f + drift) * k
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFCFF0FF).copy(alpha = 0.85f * on),
                    1f to Color.Transparent,
                    center = Offset(x, y),
                    radius = 26f * k,
                ),
                radius = 26f * k,
                center = Offset(x, y),
            )
        }
    }

    val l1 = reveal(t, 2.4f)
    val l2 = reveal(t, 5.0f)
    At(96f, panelY + panelH + 22f, l1.opacity, modifier = Modifier.width(400.dp)) {
        Text("LIGHTS OFF\nA shadow, and a meal.", style = head(30f, c(0xbdd9e8)))
    }
    At(584f, panelY + panelH + 22f, l2.opacity, modifier = Modifier.width(400.dp)) {
        Text("LIGHTS ON\nNothing there at all.", style = head(30f, c(0x9fd8ff)))
    }

    BottomLine(
        1560f, e3,
        body("Lanternfish match the ", "exact brightness", " of the water above them, and erase their own outline."),
    )
}

// ───────────────────── 5 · the biggest migration on Earth ─────────────────────

@Composable
fun BoxScope.TwiMigration(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.8f)

    Eyebrow("EVERY SINGLE NIGHT", c(0xa9b2ff), 190f, e1)
    Head(headline("The largest migration\non Earth, ", "daily."), 84f, 244f, e2)

    val yTop = 700f
    val yBot = 1440f
    // One full day over the middle of the scene: up at dusk, down at dawn.
    val dayPhase = ((t - 1.4f) / 9.0f).coerceIn(0f, 1f)
    val night = (sin((dayPhase * 2f - 0.5f) * 3.14159f) + 1f) / 2f
    val bandY = yBot + (yTop - yBot) * night

    val ruler = reveal(t, 1.2f)
    DepthRuler(
        x = 760f, yTop = yTop, yBot = yBot,
        topM = 0, botM = 600,
        marks = listOf(0, 200, 400, 600),
        accent = c(0x8f9bf0),
        alpha = ruler.opacity,
    )

    // The scattering layer: a dense band of animals that a ship sonar reads as a false bottom.
    val bandA = reveal(t, 1.6f).opacity
    Canvas(Modifier.fillMaxSize()) {
        if (bandA <= 0.01f) return@Canvas
        val k = size.width / 1080f
        var s = 31337L
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        repeat(220) {
            val bx = 90f + rnd() * 620f
            val spread = (rnd() - 0.5f) * 150f
            val ph = rnd() * 6.28f
            val y = bandY + spread + sin(t * 1.3f + ph) * 10f
            val r = (2.2f + rnd() * 3.4f) * k
            drawCircle(
                Color(0xFFBFD4FF).copy(alpha = (0.30f + rnd() * 0.5f) * bandA),
                radius = r,
                center = Offset(bx * k, y * k),
            )
        }
    }

    // Sun and moon on the same track, so the reason for the movement is on screen with it.
    val skyA = reveal(t, 1.0f).opacity
    val sunX = 140f + dayPhase * 800f
    At(sunX - 40f, 560f, skyA * (1f - night)) {
        Sphere(80f, listOf(c(0xfff3c8), c(0xffd464), c(0xe89a2c)), glow = c(0x66ffcf7a))
    }
    At((1080f - sunX) - 30f, 560f, skyA * night) {
        Sphere(60f, listOf(c(0xf2f6ff), c(0xc3cde0), c(0x7f8ba0)), glow = c(0x449fb4ff))
    }

    val label1 = reveal(t, 3.0f)
    At(90f, 1520f + label1.ty, label1.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            if (night > 0.5f) "NIGHT — they rise to feed in the dark" else "DAY — they sink out of sight",
            style = label(26f, if (night > 0.5f) c(0xa9b2ff) else c(0x8fc0d4)),
        )
    }

    BottomLine(
        1600f, e3,
        body("Billions of tonnes of animals climb ", "hundreds of metres", " at dusk, and go back down at dawn."),
    )
}

// ───────────────────── 6 · the giant squid eye ─────────────────────

@Composable
fun BoxScope.TwiSquid(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.4f)

    Eyebrow("THE GIANT SQUID", c(0xffb0a0), 190f, e1)
    Head(headline("The largest eye\nin ", "the animal kingdom."), 82f, 244f, e2)

    // The squid drifts in from the right, tail first, the way it actually swims.
    val glide = animate(1150f, 340f, 1.2f, 7.0f, Easing.easeOutCubic)(t)
    At(glide, 700f + swell(t, 0.5f) * 20f, reveal(t, 1.2f).opacity) {
        SeaBody(
            lengthUnits = 620f,
            kind = BodyKind.SQUID,
            colors = listOf(c(0xf0a898), c(0xc4614e), c(0x6b2a20)),
            swimT = t,
        )
    }

    // The eye, opening, at true size against a human one.
    val open = animate(0f, 1f, 4.2f, 6.4f, Easing.easeOutCubic)(t)
    val eyeR = 150f * open
    val eyeA = reveal(t, 4.2f).opacity
    Glow(300f, 1180f, eyeR * 1.9f, c(0x66ffd9a0), eyeA)
    At(300f - eyeR, 1180f - eyeR, eyeA) {
        Sphere(eyeR * 2f, listOf(c(0xfff1d8), c(0xe0b070), c(0x6a3a1c)))
    }
    At(300f - eyeR * 0.42f, 1180f - eyeR * 0.42f, eyeA) {
        Sphere(eyeR * 0.84f, listOf(c(0x1a1410), c(0x080604), c(0x000000)))
    }
    At(300f + eyeR * 0.18f, 1180f - eyeR * 0.52f, eyeA * 0.85f) {
        Sphere(eyeR * 0.20f, listOf(c(0xffffff), c(0xdfeaf2), c(0xa8bcc8)))
    }

    val cmpA = reveal(t, 6.8f)
    At(620f, 1150f + cmpA.ty, cmpA.opacity) {
        Sphere(14f, listOf(c(0xf4f8fb), c(0xc0cdd6), c(0x6e7d88)))
    }
    At(660f, 1136f + cmpA.ty, cmpA.opacity, modifier = Modifier.width(360.dp)) {
        Text("a human eye,\nat the same scale", style = head(28f, c(0xb6cbd6)))
    }

    val statA = reveal(t, 5.6f)
    At(90f, 1420f + statA.ty, statA.opacity) {
        LabeledStat(
            top = "ACROSS",
            topColor = c(0xffb0a0),
            value = "27",
            unit = " cm",
            sub = "about the size of a dinner plate",
            valueSize = 130f,
        )
    }

    BottomLine(
        1700f, e3,
        body("It is built to catch the faintest ", "flicker", " — most likely a sperm whale, coming for it."),
    )
}
