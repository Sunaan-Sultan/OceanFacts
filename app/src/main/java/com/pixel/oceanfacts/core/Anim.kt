package com.pixel.oceanfacts.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The timeline engine every scene is drawn against.
 *
 * Everything here is a pure function of `time` (seconds), so a scene can be driven by any
 * clock at all: the full-screen player, a looping card, or a single frozen frame captured
 * for a thumbnail. Nothing animates itself, which is what lets the same scene code serve a
 * 15-second loop and a still image with no branching.
 */

typealias Ease = (Float) -> Float

object Easing {
    val linear: Ease = { it }
    val easeInQuad: Ease = { it * it }
    val easeOutQuad: Ease = { it * (2 - it) }
    val easeInOutQuad: Ease = { t -> if (t < 0.5f) 2 * t * t else -1 + (4 - 2 * t) * t }
    val easeInCubic: Ease = { it * it * it }
    val easeOutCubic: Ease = { t -> val u = t - 1f; u * u * u + 1f }
    val easeInOutCubic: Ease =
        { t -> if (t < 0.5f) 4 * t * t * t else (t - 1) * (2 * t - 2) * (2 * t - 2) + 1 }
    val easeInOutQuart: Ease =
        { t -> if (t < 0.5f) 8 * t * t * t * t else { val u = t - 1f; 1f - 8 * u * u * u * u } }
    val easeOutExpo: Ease = { t -> if (t >= 1f) 1f else 1f - 2f.pow(-10 * t) }
    val easeInExpo: Ease = { t -> if (t <= 0f) 0f else 2f.pow(10 * (t - 1)) }
    val easeInOutSine: Ease = { t -> (-(cos(PI.toFloat() * t) - 1) / 2) }
    val easeOutSine: Ease = { t -> sin((t * PI.toFloat()) / 2) }
    val easeOutBack: Ease = { t ->
        val c1 = 1.70158f; val c3 = c1 + 1f
        1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
    }
}

fun clamp(v: Float, min: Float, max: Float): Float = maxOf(min, minOf(max, v))

/** Maps a value across [input] breakpoints onto [output] values, easing each span. */
fun interpolate(
    input: List<Float>,
    output: List<Float>,
    ease: Ease = Easing.linear,
): (Float) -> Float = { t ->
    when {
        t <= input.first() -> output.first()
        t >= input.last() -> output.last()
        else -> {
            var result = output.last()
            for (i in 0 until input.size - 1) {
                if (t >= input[i] && t <= input[i + 1]) {
                    val span = input[i + 1] - input[i]
                    val local = if (span == 0f) 0f else (t - input[i]) / span
                    result = output[i] + (output[i + 1] - output[i]) * ease(local)
                    break
                }
            }
            result
        }
    }
}

/** A single eased tween from [from] to [to] over the window [start]..[end]. */
fun animate(
    from: Float = 0f,
    to: Float = 1f,
    start: Float = 0f,
    end: Float = 1f,
    ease: Ease = Easing.easeInOutCubic,
): (Float) -> Float = { t ->
    when {
        t <= start -> from
        t >= end -> to
        else -> from + (to - from) * ease((t - start) / (end - start))
    }
}

/** Fade-up reveal: an alpha and a vertical offset (in scene units) that settles back to 0. */
data class Reveal(val opacity: Float, val ty: Float)

fun reveal(t: Float, start: Float, dur: Float = 0.6f, ease: Ease = Easing.easeOutCubic): Reveal {
    val p = clamp((t - start) / dur, 0f, 1f)
    val e = ease(p)
    return Reveal(opacity = e, ty = (1f - e) * 22f)
}

/**
 * A slow, never-repeating swell built from three detuned sines.
 *
 * Almost everything in this app is underwater and so almost everything sways. Using one
 * shared function for that keeps the whole app moving to the same sea rather than to a
 * dozen unrelated sine waves, and the incommensurable periods stop the motion visibly
 * looping inside a 15-second scene.
 */
fun swell(t: Float, speed: Float = 1f, phase: Float = 0f): Float =
    0.62f * sin(t * 0.83f * speed + phase) +
        0.26f * sin(t * 1.47f * speed + phase * 1.7f) +
        0.12f * sin(t * 2.31f * speed + phase * 0.6f)
