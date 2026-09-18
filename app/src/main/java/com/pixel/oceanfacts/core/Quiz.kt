package com.pixel.oceanfacts.core

import kotlin.random.Random

/**
 * One authored question, exactly as it sits in `assets/quiz.json`.
 *
 * The options are kept apart here — answer and distractors — because the shuffle belongs to the
 * round, not the bank: the same question must not always put its answer in the same place.
 */
data class QuizEntry(
    /** The fact this question came from, so a missed one can send the reader back to it. */
    val factId: String,
    val prompt: String,
    val answer: String,
    val wrong: List<String>,
)

/** One multiple-choice question, with its options already shuffled for this round. */
data class QuizQuestion(
    val factId: String,
    val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
) {
    val answer: String get() = options[answerIndex]
}

/**
 * Rounds of ten, drawn from an authored bank in `assets/quiz.json`.
 *
 * The questions are written by hand, two per fact, rather than generated from the catalog's
 * `stats` rows. Those rows are written to be read *beside* a fact, not answered without one:
 * many are yardsticks rather than facts about their subject ("Everest — 8,849 m" sits under
 * the trench facts to give the reader something to measure against), many labels are column
 * headings rather than questions ("Mapped?", "Seen by?"), and many values are not quantities
 * at all, so a number generator making plausible wrong answers produces nonsense from them.
 *
 * So this file only assembles rounds; `QuizTest` holds the bank to the rules that matter —
 * four distinct options, an answer that is not given away by the prompt, and at least one
 * question for every fact the app ships.
 */
object Quiz {

    const val ROUND_SIZE = 10

    /** Every authored question. Empty until [OceanData.init] has run. */
    val bank: List<QuizEntry> get() = OceanData.quiz

    /**
     * A round of [size] questions, at most one per fact so no title comes up twice, spread
     * across zones so a round is never ten questions about the abyss.
     * [random] is a parameter so tests can pin a round.
     */
    fun round(size: Int = ROUND_SIZE, random: Random = Random.Default): List<QuizQuestion> {
        val queues = bank
            .groupBy { factById(it.factId)?.zone ?: "" }
            .values
            .map { it.shuffled(random).toMutableList() }
            .shuffled(random)
        if (queues.isEmpty()) return emptyList()

        val used = HashSet<String>()
        val out = ArrayList<QuizEntry>(size)
        // Round-robin: one question from each zone in turn, then round again. A zone that runs
        // dry simply stops contributing rather than ending the round short.
        while (out.size < size && queues.any { it.isNotEmpty() }) {
            for (queue in queues) {
                if (out.size == size) break
                while (queue.isNotEmpty()) {
                    val entry = queue.removeAt(queue.lastIndex)
                    if (used.add(entry.factId)) {
                        out += entry
                        break
                    }
                }
            }
        }
        return out.map { it.toQuestion(random) }
    }

    /**
     * Two wrong options to strike out, for the 50/50 hint.
     *
     * Never returns the answer's index, and hands back fewer than two only if a question somehow
     * ships with fewer than three wrong options — the bank is checked for four distinct options
     * in `QuizTest`, so in practice it is always exactly two.
     */
    fun fiftyFiftyHidden(question: QuizQuestion, random: Random = Random.Default): Set<Int> =
        question.options.indices
            .filter { it != question.answerIndex }
            .shuffled(random)
            .take(2)
            .toSet()

    private fun QuizEntry.toQuestion(random: Random): QuizQuestion {
        val options = (wrong + answer).shuffled(random)
        return QuizQuestion(factId, prompt, options, options.indexOf(answer))
    }
}
