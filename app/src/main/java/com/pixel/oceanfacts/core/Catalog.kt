package com.pixel.oceanfacts.core

// All catalog content lives in JSON under assets/ and is loaded once by OceanData.init()
// at app start. These accessors forward to it, so the rest of the app never touches the
// parsing and never has to know where the content came from.

/** The sea animals used by the size-comparison scenes. */
val CREATURES: List<Creature> get() = OceanData.creatures

/** Every fact across all zones plus Deep Dive (uniform 15 s loop). */
val ALL_FACTS: List<Fact> get() = OceanData.allFacts

fun factById(id: String?): Fact? = ALL_FACTS.find { it.id == id }

/** Facts belonging to a zone id ("sunlit" / "twilight" / "midnight" / "trench" / "deep"). */
fun factsForZone(zone: String): List<Fact> = OceanData.factsForZone(zone)

/** Distinct categories of a zone, in first-seen order (used by Explore). */
fun categoriesForZone(zone: String): List<String> =
    factsForZone(zone).map { it.cat }.distinct()

/** The standalone facts shown on the Deep Dive tab. */
val DEEP_DIVE_FACTS: List<Fact> get() = factsForZone(OceanData.DEEP_DIVE)

/** The large "Explore" cards on the home screen, in descending order. */
val FEATURED_ZONES: List<FeaturedZone> get() = OceanData.featured

/** Header copy for each explorable zone. */
val ZONE_META: Map<String, ZoneMeta> get() = OceanData.zoneMeta

/** Realms not yet charted, shown under "More realms" on the home screen. */
val REALMS: List<Realm> get() = OceanData.lockedRealms
