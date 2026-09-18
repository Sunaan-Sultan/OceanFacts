package com.pixel.oceanfacts

import com.pixel.oceanfacts.core.ALL_FACTS
import com.pixel.oceanfacts.core.OceanData
import com.pixel.oceanfacts.core.Quiz
import com.pixel.oceanfacts.core.factById
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * The rules the authored quiz bank has to keep.
 *
 * These are the checks a generator used to enforce by construction. Now that the questions are
 * written by hand, something has to hold the line on four distinct options, an answer that the
 * prompt does not give away, and coverage of every fact the app ships.
 */
class QuizTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadCatalog() {
            val assets = File("src/main/assets")
            OceanData.load { path -> File(assets, path).readText() }
        }
    }

    @Test
    fun everyQuestionPointsAtALiveFact() {
        Quiz.bank.forEach { entry ->
            assertNotNull("question for missing fact ${entry.factId}", factById(entry.factId))
        }
    }

    @Test
    fun everyFactHasAtLeastOneQuestion() {
        val covered = Quiz.bank.mapTo(HashSet()) { it.factId }
        val missing = ALL_FACTS.map { it.id }.filterNot { it in covered }
        assertTrue("facts with no question: $missing", missing.isEmpty())
    }

    @Test
    fun everyQuestionHasFourDistinctOptions() {
        Quiz.bank.forEach { entry ->
            val options = entry.wrong + entry.answer
            assertEquals("${entry.prompt} does not have four options", 4, options.size)
            assertEquals(
                "${entry.prompt} repeats an option",
                options.size,
                options.map { it.trim().lowercase() }.toSet().size,
            )
            options.forEach {
                assertTrue("${entry.prompt} has a blank option", it.isNotBlank())
            }
        }
    }

    @Test
    fun noPromptGivesAwayItsOwnAnswer() {
        Quiz.bank.forEach { entry ->
            val prompt = entry.prompt.lowercase()
            val answer = entry.answer.trim().lowercase()
            // Short answers such as "12" legitimately appear in wording like "how many"; only a
            // substantial answer quoted verbatim in the prompt is a giveaway.
            if (answer.length >= 8) {
                assertTrue("${entry.prompt} contains its own answer", !prompt.contains(answer))
            }
        }
    }

    @Test
    fun aRoundIsTenQuestionsAboutTenDifferentFacts() {
        repeat(40) { seed ->
            val round = Quiz.round(Quiz.ROUND_SIZE, Random(seed.toLong()))
            assertEquals("round $seed is not ten questions", Quiz.ROUND_SIZE, round.size)
            assertEquals(
                "round $seed asks about the same fact twice",
                round.size,
                round.map { it.factId }.toSet().size,
            )
            round.forEach { q ->
                assertTrue("an answer index landed outside its options", q.answerIndex in q.options.indices)
            }
        }
    }

    @Test
    fun aRoundIsSpreadAcrossZones() {
        repeat(20) { seed ->
            val zones = Quiz.round(Quiz.ROUND_SIZE, Random(seed.toLong()))
                .mapNotNull { factById(it.factId)?.zone }
                .toSet()
            assertTrue("round $seed came from only ${zones.size} zone(s)", zones.size >= 3)
        }
    }

    @Test
    fun theSameSeedGivesTheSameRound() {
        val a = Quiz.round(Quiz.ROUND_SIZE, Random(4242))
        val b = Quiz.round(Quiz.ROUND_SIZE, Random(4242))
        assertEquals(a.map { it.prompt }, b.map { it.prompt })
        assertEquals(a.map { it.options }, b.map { it.options })
    }

    @Test
    fun theFiftyFiftyNeverStrikesOutTheAnswer() {
        val round = Quiz.round(Quiz.ROUND_SIZE, Random(7))
        round.forEach { q ->
            repeat(20) { seed ->
                val hidden = Quiz.fiftyFiftyHidden(q, Random(seed.toLong()))
                assertEquals("50/50 should remove exactly two options", 2, hidden.size)
                assertTrue("50/50 struck out the answer", q.answerIndex !in hidden)
            }
        }
    }
}
