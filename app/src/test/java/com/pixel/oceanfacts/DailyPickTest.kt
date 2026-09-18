package com.pixel.oceanfacts

import com.pixel.oceanfacts.core.ALL_FACTS
import com.pixel.oceanfacts.core.DailyFact
import com.pixel.oceanfacts.core.DailyQuiz
import com.pixel.oceanfacts.core.OceanData
import com.pixel.oceanfacts.core.Quiz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Today's fact and today's round are pure functions of the date. The notification worker and the
 * UI work them out in different processes and have to agree, so the properties that guarantee
 * that are worth pinning down.
 */
class DailyPickTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            val assets = File("src/main/assets")
            OceanData.load { path -> File(assets, path).readText() }
        }
    }

    @Test
    fun theSameDayAlwaysGivesTheSameFact() {
        repeat(50) { i ->
            val day = 19_000L + i
            assertEquals(DailyFact.factForDay(day)?.id, DailyFact.factForDay(day)?.id)
        }
    }

    @Test
    fun oneCycleCoversEveryFactExactlyOnce() {
        // The catalog is walked as a shuffled cycle, so nothing repeats until everything else
        // has had a turn.
        val size = ALL_FACTS.size
        val cycle = (0 until size).map { DailyFact.factForDay(it.toLong())?.id }
        assertEquals("a fact repeated inside one cycle", size, cycle.toSet().size)
        assertEquals(ALL_FACTS.map { it.id }.toSet(), cycle.filterNotNull().toSet())
    }

    @Test
    fun daysBeforeTheEpochStillLandOnAFact() {
        assertNotNull(DailyFact.factForDay(-1L))
        assertNotNull(DailyFact.factForDay(-12_345L))
    }

    @Test
    fun theSameDayAlwaysGivesTheSameRound() {
        repeat(30) { i ->
            val day = 19_000L + i
            val a = DailyQuiz.roundForDay(day)
            val b = DailyQuiz.roundForDay(day)
            assertEquals(Quiz.ROUND_SIZE, a.size)
            assertEquals(a.map { it.prompt }, b.map { it.prompt })
            assertEquals(a.map { it.options }, b.map { it.options })
        }
    }

    @Test
    fun consecutiveDaysGetDifferentRounds() {
        val a = DailyQuiz.roundForDay(19_000L).map { it.prompt }
        val b = DailyQuiz.roundForDay(19_001L).map { it.prompt }
        assertEquals("two days in a row drew the identical round", false, a == b)
    }
}
