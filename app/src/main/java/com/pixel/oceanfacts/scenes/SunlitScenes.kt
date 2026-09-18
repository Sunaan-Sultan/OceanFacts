package com.pixel.oceanfacts.scenes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.BodyKind
import com.pixel.oceanfacts.core.CREATURES
import com.pixel.oceanfacts.core.Easing
import com.pixel.oceanfacts.core.animate
import com.pixel.oceanfacts.core.interpolate
import com.pixel.oceanfacts.core.reveal
import com.pixel.oceanfacts.core.swell
import com.pixel.oceanfacts.ui.SeaBody
import com.pixel.oceanfacts.ui.bodyHeight
import kotlin.math.cos
import kotlin.math.sin

// ───────────────────── 1 · ocean giants, to scale ─────────────────────

/**
 * The line-up, stacked rather than laid end to end.
 *
 * Side by side, a 1.7 m swimmer beside a 30 m whale is a smudge next to a bar; on its own row
 * at the same scale it is a person you can still recognise, which is the only thing that makes
 * the last row land.
 */
@Composable
fun BoxScope.SunSizes(t: Float, duration: Float) = SceneFade(t, duration) {
    val roster = CREATURES
    if (roster.isEmpty()) return@SceneFade

    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.4f)

    Eyebrow("AT TRUE RELATIVE SIZE", c(0x93c6d2), 200f, e1)
    Head(headline("Everything here\nis ", "larger", " than you."), 92f, 252f, e2)

    val longest = roster.maxOf { it.lengthM }
    val scale = 860f / longest
    var y = 552f
    roster.forEachIndexed { i, cr ->
        val len = cr.lengthM * scale
        val h = bodyHeight(cr.kind, len)
        val a = reveal(t, 1.6f + i * 0.55f, 0.75f, Easing.easeOutCubic)
        val slide = (1f - a.opacity) * 90f

        At(100f - slide, y - 30f, a.opacity) {
            Text(
                "${cr.name.uppercase()}   ${trim(cr.lengthM)} M",
                style = label(21f, c(0x9dc2cf), FontWeight.SemiBold),
            )
        }
        At(100f - slide, y, a.opacity) {
            SeaBody(
                lengthUnits = len,
                kind = cr.kind,
                colors = cr.c,
                // Each body swims on its own phase, so the column never pulses in unison.
                swimT = t * 0.8f + i * 1.7f,
            )
        }
        y += h + 48f
    }

    BottomLine(
        1640f, e3,
        body("A blue whale is ", "30 metres", " long. Nothing that has ever lived on Earth is bigger."),
    )
}

private fun trim(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)

// ───────────────────── 2 · the blue whale ─────────────────────

@Composable
fun BoxScope.SunWhale(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 8.6f)

    // The whale crosses once, slowly, and is off frame before the loop seam.
    val cross = animate(-1000f, 1180f, 1.0f, 13.5f, Easing.linear)(t)
    val rise = swell(t, 0.5f) * 26f

    Eyebrow("THE LARGEST ANIMAL EVER", c(0x8fd3e4), 190f, e1)
    Head(headline("Bigger than any\ndinosaur. ", "Still here."), 92f, 244f, e2)

    At(cross, 780f + rise, 1f) {
        SeaBody(
            lengthUnits = 940f,
            kind = BodyKind.WHALE,
            colors = listOf(c(0x8fb6c9), c(0x3f6f8c), c(0x18354a)),
            swimT = t,
        )
    }

    // Three numbers, each arriving as the whale is passing the place it describes.
    val s1 = reveal(t, 3.4f)
    val s2 = reveal(t, 4.2f)
    val s3 = reveal(t, 5.0f)
    At(90f, 1180f + s1.ty, s1.opacity) {
        Row(horizontalArrangement = Arrangement.spacedBy(64.dp)) {
            StatCol("30", " m", "Nose to fluke", 92f, Color.White, c(0x8ba9b8))
        }
    }
    At(400f, 1180f + s2.ty, s2.opacity) {
        StatCol("150", " t", "As much as 25 elephants", 92f, Color.White, c(0x8ba9b8))
    }
    At(90f, 1370f + s3.ty, s3.opacity) {
        StatCol("188", " dB", "Its call, louder than a jet", 92f, Color.White, c(0x8ba9b8))
    }

    BottomLine(
        1620f, e3,
        body("Its heart is the size of a ", "small car", ", and beats about eight times a minute."),
    )
}

// ───────────────────── 3 · where the light ends ─────────────────────

@Composable
fun BoxScope.SunLight(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.6f)

    Eyebrow("HOW FAR SUNLIGHT GETS", c(0x9ed8e6), 190f, e1)
    Head(headline("The sea runs out\nof ", "light", " first."), 92f, 244f, e2)

    val yTop = 640f
    val yBot = 1500f
    val topM = 0
    val botM = 1000

    // The light column: bright at the waterline, gone well before the ruler ends.
    val grow = animate(0f, 1f, 1.1f, 5.2f, Easing.easeInOutSine)(t)
    GradientRect(
        x = 150f, y = yTop, w = 300f, h = (yBot - yTop) * grow,
        stops = arrayOf(
            0f to c(0xd8fbff).copy(alpha = 0.85f),
            0.10f to c(0x74d8ee).copy(alpha = 0.55f),
            0.22f to c(0x2f8fb4).copy(alpha = 0.28f),
            0.45f to c(0x123f5c).copy(alpha = 0.10f),
            1f to Color.Transparent,
        ),
        corner = 12f,
    )

    DepthRuler(
        x = 620f, yTop = yTop, yBot = yBot,
        topM = topM, botM = botM,
        marks = listOf(0, 200, 500, 1000),
        accent = c(0x5fd0e8),
        alpha = reveal(t, 1.4f).opacity,
        progress = grow,
    )

    // A read-out riding the descent, counting the light away.
    val diveM = interpolate(listOf(1.2f, 9.6f), listOf(0f, 1000f), Easing.easeInOutSine)(t)
    val pct = interpolate(
        listOf(0f, 10f, 50f, 100f, 200f, 400f, 1000f),
        listOf(100f, 45f, 22f, 9f, 1f, 0.1f, 0f),
        Easing.linear,
    )(diveM)
    val markerY = yTop + (yBot - yTop) * (diveM / botM)
    val markerA = reveal(t, 1.6f).opacity

    Line(140f, markerY, 600f, markerY, c(0xffdf9a).copy(alpha = 0.75f), width = 3f, dash = true, alpha = markerA)
    At(150f, markerY - 96f, markerA) {
        LabeledStat(
            top = "SURFACE LIGHT LEFT",
            topColor = c(0xffdf9a),
            value = pctText(pct),
            unit = " %",
            sub = null,
            valueSize = 76f,
        )
    }

    val note = reveal(t, 6.4f)
    At(700f, 1120f + note.ty, note.opacity, modifier = Modifier.width(320.dp)) {
        Text(
            "Below 200 m no plant can live on sunlight. Below 1,000 m there is none left to live on.",
            style = head(30f, c(0xb8d6e2)),
        )
    }

    BottomLine(1630f, e3, body("The sea is ", "3,682 m", " deep on average. Light gets a quarter of the way."))
}

private fun pctText(p: Float): String = when {
    p >= 10f -> p.toInt().toString()
    p >= 1f -> String.format(java.util.Locale.US, "%.0f", p)
    p >= 0.1f -> "0.1"
    else -> "0"
}

// ───────────────────── 4 · why the sea is blue ─────────────────────

/**
 * Colour is not lost all at once: the sea eats the long wavelengths first. Each bar is drawn to
 * the depth at which that colour is effectively gone, so the picture *is* the explanation.
 */
@Composable
fun BoxScope.SunBlue(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.2f)

    Eyebrow("WHERE THE COLOURS GO", c(0x86ccdd), 190f, e1)
    Head(headline("Blue is simply\nwhat is ", "left over."), 92f, 244f, e2)

    data class Band(val name: String, val gone: Int, val color: Long)

    val bands = listOf(
        Band("RED", 5, 0xff5a4a),
        Band("ORANGE", 20, 0xff9e4c),
        Band("YELLOW", 40, 0xffd85e),
        Band("GREEN", 80, 0x5fd889),
        Band("BLUE", 275, 0x4aa8ff),
    )

    val yTop = 660f
    val perMetre = 3.1f      // scene units per metre of depth
    val barW = 132f
    val gap = 34f
    val x0 = 122f

    bands.forEachIndexed { i, b ->
        val a = reveal(t, 1.3f + i * 0.55f, 0.7f, Easing.easeOutCubic)
        val grow = animate(0f, 1f, 1.3f + i * 0.55f, 2.5f + i * 0.55f, Easing.easeOutCubic)(t)
        val x = x0 + i * (barW + gap)
        val h = b.gone * perMetre * grow

        GradientRect(
            x = x, y = yTop, w = barW, h = h,
            stops = arrayOf(
                0f to c(b.color),
                0.75f to c(b.color).copy(alpha = 0.55f),
                1f to c(b.color).copy(alpha = 0f),
            ),
            corner = 10f,
            alpha = a.opacity,
        )
        At(x, yTop - 46f, a.opacity, modifier = Modifier.width(barW.dp)) {
            Text(b.name, style = label(19f, c(b.color)))
        }
        At(x, yTop + h + 14f, a.opacity * (if (b.gone > 200) 0f else 1f), modifier = Modifier.width(barW.dp)) {
            Text(metres(b.gone), style = label(21f, Color.White.copy(alpha = 0.8f), FontWeight.Medium))
        }
    }

    // The blue bar keeps going off the bottom of the frame, which is the point.
    val blueA = reveal(t, 3.6f).opacity
    At(x0 + 4 * (barW + gap), yTop + 275f * perMetre + 6f, blueA, modifier = Modifier.width(barW.dp)) {
        Text("and on\nand on", style = head(26f, c(0x8fc6f5)))
    }

    BottomLine(
        1620f, e3,
        body("Water absorbs red light within ", "five metres", ". Blood looks green at thirty."),
    )
}

// ───────────────────── 5 · half the air you breathe ─────────────────────

@Composable
fun BoxScope.SunPlankton(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 9.8f)

    Eyebrow("PHYTOPLANKTON", c(0x7fe0b4), 190f, e1)
    Head(headline("Every second breath\ncame from ", "the sea."), 88f, 244f, e2)

    // A drifting bloom. Deterministic, and a pure function of t like everything else.
    Canvas(Modifier.fillMaxSize()) {
        val k = size.width / 1080f
        var s = 5150L
        val rnd = { s = (s * 9301 + 49297) % 233280; s / 233280f }
        repeat(170) {
            val bx = rnd()
            val by = 0.34f + rnd() * 0.52f
            val r = (2.2f + rnd() * 5.2f) * k
            val ph = rnd() * 6.28f
            val drift = sin(t * 0.5f + ph) * 16f * k
            val bob = cos(t * 0.7f + ph * 1.4f) * 11f * k
            val a = 0.22f + 0.5f * ((sin(t * 0.9f + ph) + 1f) / 2f)
            drawCircle(
                color = Color(0xFF6FE8B4).copy(alpha = a * 0.75f),
                radius = r,
                center = Offset(bx * size.width + drift, by * size.height + bob),
            )
        }
    }

    val counter = interpolate(listOf(2.0f, 5.6f), listOf(0f, 50f), Easing.easeOutExpo)(t)
    val cA = reveal(t, 1.9f)
    At(90f, 900f + cA.ty, cA.opacity) {
        LabeledStat(
            top = "OF EARTH ATMOSPHERIC OXYGEN",
            topColor = c(0x7fe0b4),
            value = counter.toInt().toString(),
            unit = " %",
            sub = "made by drifting plants too small to see",
            valueSize = 150f,
        )
    }

    val bubA = reveal(t, 3.0f).opacity
    At(0f, 0f, bubA) {
        com.pixel.oceanfacts.ui.Bubbles(time = t, count = 22, seed = 4242L, modifier = Modifier.fillMaxSize())
    }

    BottomLine(
        1620f, e3,
        body("A single drop of seawater holds ", "thousands", " of them. They out-produce every forest on land."),
    )
}

// ───────────────────── 6 · waves move, water does not ─────────────────────

/**
 * The wave travels; the water goes in a circle and ends up where it started. Three floats trace
 * their own orbits against the passing crest, which is the whole fact in one picture.
 */
@Composable
fun BoxScope.SunWave(t: Float, duration: Float) = SceneFade(t, duration) {
    val e1 = reveal(t, 0.3f)
    val e2 = reveal(t, 0.8f)
    val e3 = reveal(t, 10.2f)

    Eyebrow("WHAT A WAVE ACTUALLY DOES", c(0x9fdcea), 190f, e1)
    Head(headline("The wave moves.\nThe water ", "stays."), 92f, 244f, e2)

    val baseY = 900f
    val amp = 84f
    val k = 2.6f
    val speed = 1.25f
    val show = animate(0f, 1f, 1.0f, 2.6f, Easing.easeOutCubic)(t)

    Canvas(Modifier.fillMaxSize()) {
        val px = size.width / 1080f
        val path = Path()
        var x = 0f
        while (x <= 1080f) {
            val phase = (x / 1080f) * k * 6.2832f - t * speed * 6.2832f * 0.25f
            val y = baseY + sin(phase) * amp * show
            if (x == 0f) path.moveTo(x * px, y * px) else path.lineTo(x * px, y * px)
            x += 8f
        }
        drawPath(
            path,
            color = Color(0xFFBFF0FF).copy(alpha = 0.9f * show),
            style = Stroke(width = 5f * px, cap = StrokeCap.Round),
        )
    }

    // The orbits: one per float, drawn as a faint ring with the float riding it.
    listOf(220f, 540f, 860f).forEachIndexed { i, cx ->
        val a = reveal(t, 2.4f + i * 0.4f)
        val phase = (cx / 1080f) * k * 6.2832f - t * speed * 6.2832f * 0.25f
        RingArc(
            cx = cx, cy = baseY, w = amp * 2f, h = amp * 2f, deg = 0f,
            color = Color.White.copy(alpha = 0.22f), bw = 3f, dash = true, alpha = a.opacity,
        )
        val fx = cx + cos(phase) * amp
        val fy = baseY + sin(phase) * amp
        At(fx - 17f, fy - 17f, a.opacity) {
            com.pixel.oceanfacts.ui.Sphere(
                sizeUnits = 34f,
                colors = listOf(c(0xfff0c4), c(0xffc46a), c(0xb5702a)),
            )
        }
    }

    val note = reveal(t, 6.0f)
    At(90f, 1200f + note.ty, note.opacity, modifier = Modifier.width(900.dp)) {
        Text(
            "Each float rides a circle and comes back. What travels across the ocean is the energy, not the sea.",
            style = head(34f, c(0xbdd9e4)),
        )
    }

    BottomLine(
        1620f, e3,
        body("A Pacific swell can cross ", "10,000 km", " while the water it passes through barely moves."),
    )
}
