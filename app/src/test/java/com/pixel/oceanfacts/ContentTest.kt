package com.pixel.oceanfacts

import com.pixel.oceanfacts.core.ALL_FACTS
import com.pixel.oceanfacts.core.CREATURES
import com.pixel.oceanfacts.core.FEATURED_ZONES
import com.pixel.oceanfacts.core.OceanData
import com.pixel.oceanfacts.core.SceneId
import com.pixel.oceanfacts.core.SourceManager
import com.pixel.oceanfacts.core.ZONE_META
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Holds the catalog to the invariants the app quietly relies on.
 *
 * All of the content lives in JSON, which means a typo in it is a runtime crash rather than a
 * compile error. These run on the JVM against the real files in `src/main/assets`, so a broken
 * scene name or a duplicate id fails the build instead of the app.
 */
class ContentTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            val assets = File("src/main/assets")
            OceanData.load { path -> File(assets, path).readText() }
        }
    }

    @Test
    fun everyFactHasAUniqueId() {
        val ids = ALL_FACTS.map { it.id }
        assertEquals("duplicate fact ids: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}", ids.size, ids.toSet().size)
    }

    @Test
    fun everySceneIsUsedExactlyOnce() {
        val used = ALL_FACTS.map { it.scene }
        assertEquals("a scene is used by more than one fact", used.size, used.toSet().size)
        val unused = SceneId.entries.toSet() - used.toSet()
        assertTrue("scenes with no fact behind them: $unused", unused.isEmpty())
    }

    @Test
    fun everyFactHasCopyAndStats() {
        ALL_FACTS.forEach { fact ->
            assertTrue("${fact.id} has no title", fact.title.isNotBlank())
            assertTrue("${fact.id} has no subtitle", fact.sub.isNotBlank())
            assertTrue("${fact.id} has no blurb", fact.blurb.isNotBlank())
            assertTrue("${fact.id} has no category", fact.cat.isNotBlank())
            assertTrue("${fact.id} has fewer than two stats", fact.stats.size >= 2)
            fact.stats.forEach { (k, v) ->
                assertTrue("${fact.id} has a blank stat label", k.isNotBlank())
                assertTrue("${fact.id} has a blank stat value", v.isNotBlank())
            }
        }
    }

    @Test
    fun everyDepthIsInsideTheOcean() {
        ALL_FACTS.forEach { fact ->
            assertTrue(
                "${fact.id} sits at ${fact.depthM} m, which is not in the sea",
                fact.depthM in 0..11000,
            )
        }
    }

    @Test
    fun everyHeroFrameIsInsideItsLoop() {
        ALL_FACTS.forEach { fact ->
            assertTrue("${fact.id} has a hero frame outside its loop", fact.hero in 0f..fact.dur)
        }
    }

    @Test
    fun everyFactHasASource() {
        ALL_FACTS.forEach { fact ->
            assertNotNull("${fact.id} has no source", fact.source)
            assertTrue(
                "${fact.id} has a source that is not https",
                fact.source!!.url.startsWith("https://"),
            )
        }
    }

    @Test
    fun noSourceKeyPointsAtNothing() {
        // A key left behind after a fact is removed is dead weight that reads as coverage.
        val live = ALL_FACTS.mapTo(HashSet()) { SourceManager.keyFor(it.zone, indexWithinZone(it.id, it.zone)) }
        val orphans = SourceManager.keys - live
        assertTrue("source keys with no fact behind them: $orphans", orphans.isEmpty())
    }

    @Test
    fun everyFeaturedZoneHasMetaAndFacts() {
        FEATURED_ZONES.forEach { zone ->
            assertNotNull("${zone.zoneId} has no header copy", ZONE_META[zone.zoneId])
            assertTrue(
                "${zone.zoneId} has no facts",
                ALL_FACTS.any { it.zone == zone.zoneId },
            )
        }
    }

    @Test
    fun theZonesRunFromShallowToDeep() {
        // The home screen is a descent; a card out of order would quietly break that reading.
        val depths = FEATURED_ZONES.map { it.depthM }
        assertEquals("the zone cards are not in depth order", depths.sorted(), depths)
    }

    @Test
    fun theCreatureRosterIsOrderedAndComplete() {
        assertTrue("the creature roster is empty", CREATURES.isNotEmpty())
        val lengths = CREATURES.map { it.lengthM }
        assertEquals("the roster is not ordered small to large", lengths.sorted(), lengths)
        CREATURES.forEach { c ->
            assertEquals("${c.name} needs a three-stop palette", 3, c.c.size)
            assertTrue("${c.name} has no length", c.lengthM > 0f)
        }
    }

    @Test
    fun theDescentIsOrderedAndInRange() {
        val stops = OceanData.descent
        assertTrue("the descent has no stops", stops.size >= 8)
        val depths = stops.map { it.depthM }
        assertEquals("the descent is not in depth order", depths.sorted(), depths)
        assertEquals("the descent should start at the surface", 0, depths.first())
        assertTrue("the descent should end at the bottom", depths.last() >= 10000)
    }

    private fun indexWithinZone(id: String, zone: String): Int =
        ALL_FACTS.filter { it.zone == zone }.indexOfFirst { it.id == id } + 1
}
