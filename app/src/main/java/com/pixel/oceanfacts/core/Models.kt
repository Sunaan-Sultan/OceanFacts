package com.pixel.oceanfacts.core

import androidx.compose.ui.graphics.Color

/** Identifies which animated scene a fact renders. */
enum class SceneId {
    // The Sunlight Zone
    SUN_SIZES, SUN_WHALE, SUN_LIGHT, SUN_BLUE, SUN_PLANKTON, SUN_WAVE,
    // The Twilight Zone
    TWI_ENTER, TWI_SNOW, TWI_BIOLUM, TWI_COUNTER, TWI_MIGRATION, TWI_SQUID,
    // The Midnight Zone
    MID_DARK, MID_COLD, MID_ANGLER, MID_FUSION, MID_WHALEFALL, MID_DUMBO,
    // The Trenches
    TR_CHALLENGER, TR_PRESSURE, TR_SNAILFISH, TR_VENTS, TR_VISITS, TR_MAPPED,
    // Deep Dive — standalone facts
    DD_TITANIC, DD_WATERCYCLE, DD_TIDES, DD_AMAZON, DD_GYRE, DD_CONVEYOR,
}

/** How a creature silhouette is drawn. Shape only — the palette comes from the roster. */
enum class BodyKind { WHALE, SHARK, FISH, SQUID, OCTOPUS, HUMAN, SUB }

/** A sea animal at true length, with a 3-stop body palette. */
data class Creature(
    val name: String,
    /** True length in metres, which is what drives every size comparison. */
    val lengthM: Float,
    val kind: BodyKind,
    val c: List<Color>,
)

/** A realm shown (locked) under "More realms" on the home screen. */
data class Realm(
    val name: String,
    val depth: String,
    val desc: String,
    val color: List<Color>,
)

data class FactSource(
    val name: String,
    val url: String,
)

/**
 * A single ocean fact: its scene, its copy, its detail-sheet stats, and the zone it belongs to.
 *
 * [depthM] is load-bearing rather than decorative. The water behind every scene is coloured
 * from it, so a fact about the surface plays in daylight and a fact about the hadal trenches
 * plays in the dark — the app never has to be told twice how deep something is.
 */
data class Fact(
    val id: String,
    val cat: String,
    val accent: Color,
    val scene: SceneId,
    val dur: Float,
    val hero: Float,
    val depthM: Int,
    val title: String,
    val sub: String,
    val blurb: String,
    val stats: List<Pair<String, String>>,
    val zone: String,
    val source: FactSource? = null,
)

/** Header copy for an explorable zone. */
data class ZoneMeta(
    val label: String,
    val eyebrow: String,
    val eyebrowColor: Color,
    val title: String,
    val blurb: String,
)

/** A large card on the home screen that opens an explorable [zoneId]. */
data class FeaturedZone(
    val zoneId: String,
    val scene: SceneId,
    val dur: Float,
    val hero: Float,
    val depthM: Int,
    val pill: String,
    val pillColor: Color,
    val title: String,
    val tagline: String,
)

/** What a marker on the descent is, which decides how it is drawn. */
enum class StopKind { SURFACE, DIVER, ANIMAL, MACHINE, WRECK, SEAFLOOR, LANDMARK }

/** One marker on the surface-to-Challenger-Deep descent, sorted by true depth. */
data class DescentStop(
    val name: String,
    val sub: String,
    val depthText: String,
    /** True depth in metres, which drives the position. */
    val depthM: Int,
    val kind: StopKind,
    val colors: List<Color>,
    val glow: Color = Color.Transparent,
)

/** Opaque colour from a 0xRRGGBB literal. */
internal fun hex(v: Long) = Color(v or 0xFF000000)
