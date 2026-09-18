package com.pixel.oceanfacts.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.AdManager
import com.pixel.oceanfacts.core.Analytics
import com.pixel.oceanfacts.core.DailyQuiz
import com.pixel.oceanfacts.core.DailyQuizOutcome
import com.pixel.oceanfacts.core.Fact
import com.pixel.oceanfacts.core.OceanPrefs
import com.pixel.oceanfacts.core.Quiz
import com.pixel.oceanfacts.core.QuizQuestion
import com.pixel.oceanfacts.core.Streak
import com.pixel.oceanfacts.core.ZONE_META
import com.pixel.oceanfacts.core.factById
import org.json.JSONArray
import org.json.JSONObject

/** What the screen is showing: the opening hub, a round in progress, or the score. */
enum class Stage { INTRO, PLAYING, RESULT }

/**
 * A round, held outside the screen that draws it.
 *
 * Backing out of a question used to throw the whole round away. The state lives here and is
 * mirrored to disk on every answer, so leaving the quiz for a fact, for the home page, or by
 * closing the app entirely, all come back to the same question with the same answers behind it.
 */
@Stable
class QuizSession {
    val stageState = mutableStateOf(Stage.INTRO)
    val questionsState = mutableStateOf(emptyList<QuizQuestion>())
    val indexState = mutableIntStateOf(0)
    val pickedState = mutableStateOf<Int?>(null)
    val resultsState = mutableStateOf(emptyList<Boolean>())
    val missedState = mutableStateOf(emptyList<String>())
    val beatBestState = mutableStateOf(false)
    val dailyState = mutableStateOf(true)
    val playDayState = mutableLongStateOf(0L)
    val outcomeState = mutableStateOf<DailyQuizOutcome?>(null)
    val hintUsedState = mutableStateOf(false)
    val hiddenState = mutableStateOf(emptySet<Int>())
    val openState = mutableStateOf(false)

    fun clearIfFinished() {
        if (stageState.value == Stage.RESULT) clear()
    }

    /** An unfinished daily round left over from a previous day is not "today's quiz". */
    fun discardStaleDaily() {
        if (stageState.value == Stage.PLAYING &&
            dailyState.value &&
            playDayState.longValue != OceanPrefs.dayIndex
        ) {
            clear()
        }
    }

    fun clear() {
        stageState.value = Stage.INTRO
        questionsState.value = emptyList()
        indexState.intValue = 0
        pickedState.value = null
        resultsState.value = emptyList()
        missedState.value = emptyList()
        beatBestState.value = false
        dailyState.value = true
        playDayState.longValue = 0L
        outcomeState.value = null
        hintUsedState.value = false
        hiddenState.value = emptySet()
        OceanPrefs.quizProgress = null
    }

    fun noteOpen(open: Boolean) {
        openState.value = open
        persist()
    }

    fun persist() {
        val questions = questionsState.value
        if (stageState.value != Stage.PLAYING || questions.isEmpty()) {
            OceanPrefs.quizProgress = null
            return
        }
        val out = JSONObject()
            .put("v", SNAPSHOT_VERSION)
            .put("daily", dailyState.value)
            .put("day", playDayState.longValue)
            .put("index", indexState.intValue)
            .put("results", JSONArray(resultsState.value))
            .put("missed", JSONArray(missedState.value))
            .put("hintUsed", hintUsedState.value)
            .put("hidden", JSONArray(hiddenState.value.toList()))
            .put("open", openState.value)
            .put("questions", JSONArray(questions.map(::encodeQuestion)))
        pickedState.value?.let { out.put("picked", it) }
        OceanPrefs.quizProgress = out.toString()
    }

    fun restore() {
        val raw = OceanPrefs.quizProgress ?: return
        val loaded = runCatching { load(raw) }.getOrDefault(false)
        if (!loaded) {
            clear()
            return
        }
        discardStaleDaily()
        if (stageState.value != Stage.PLAYING) openState.value = false
    }

    private fun load(raw: String): Boolean {
        val o = JSONObject(raw)
        if (o.optInt("v") != SNAPSHOT_VERSION) return false
        val array = o.optJSONArray("questions") ?: return false
        val questions = (0 until array.length()).map { decodeQuestion(array.getJSONObject(it)) }
        if (questions.isEmpty() || questions.any { it.answerIndex !in it.options.indices }) return false

        val results = o.optJSONArray("results").bools().take(questions.size)
        stageState.value = Stage.PLAYING
        questionsState.value = questions
        indexState.intValue = o.optInt("index").coerceIn(0, questions.lastIndex)
        pickedState.value = if (o.has("picked")) o.getInt("picked") else null
        resultsState.value = results
        missedState.value = o.optJSONArray("missed").strings()
        beatBestState.value = false
        dailyState.value = o.optBoolean("daily", true)
        playDayState.longValue = o.optLong("day")
        outcomeState.value = null
        hintUsedState.value = o.optBoolean("hintUsed")
        hiddenState.value = o.optJSONArray("hidden").ints().toSet()
        openState.value = o.optBoolean("open")
        return true
    }

    private companion object {
        const val SNAPSHOT_VERSION = 1

        fun encodeQuestion(q: QuizQuestion) = JSONObject()
            .put("fact", q.factId)
            .put("prompt", q.prompt)
            .put("options", JSONArray(q.options))
            .put("answer", q.answerIndex)

        fun decodeQuestion(o: JSONObject) = QuizQuestion(
            factId = o.getString("fact"),
            prompt = o.getString("prompt"),
            options = o.getJSONArray("options").strings(),
            answerIndex = o.getInt("answer"),
        )

        fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).map { getString(it) }

        fun JSONArray?.ints(): List<Int> =
            if (this == null) emptyList() else (0 until length()).map { getInt(it) }

        fun JSONArray?.bools(): List<Boolean> =
            if (this == null) emptyList() else (0 until length()).map { getBoolean(it) }
    }
}

/**
 * Ten authored questions drawn from the facts the app already ships — see [Quiz] for the bank.
 *
 * Two ways in. **Today's quiz** is the same ten questions for everyone, worked out from the date
 * alone ([DailyQuiz]), and finishing it keeps the streak alive; that is the reason to come back
 * tomorrow, and it is what the screen leads with. **Practice** draws a fresh random round, as
 * many times as you like, and costs nothing — the daily is the occasion, not a limit on playing.
 *
 * A wrong answer is the interesting moment, so it is never just marked wrong: the right answer
 * is shown immediately along with the fact it came from, and the round ends with every fact that
 * was missed, tappable, so the quiz sends people back into the content rather than out of it.
 *
 * The screen takes its colour from the fact being asked about, so the backdrop shifts from
 * question to question — the same accent that fact's own scene and card use.
 */
@Composable
fun QuizScreen(
    session: QuizSession,
    onOpenFact: (String) -> Unit,
    onClose: () -> Unit,
) {
    var stage by session.stageState
    var questions by session.questionsState
    var index by session.indexState
    var picked by session.pickedState
    var results by session.resultsState
    var missed by session.missedState
    var beatBest by session.beatBestState
    var daily by session.dailyState
    var playDay by session.playDayState
    var outcome by session.outcomeState
    var hintUsed by session.hintUsedState
    var hidden by session.hiddenState
    var hintOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? android.app.Activity

    LaunchedEffect(stage, questions, index, picked, results, hidden, hintUsed) { session.persist() }

    LaunchedEffect(stage) {
        if (stage == Stage.PLAYING) AdManager.preloadQuizInterstitial(context)
    }

    fun leave(exit: () -> Unit) {
        session.clearIfFinished()
        exit()
    }

    fun atRoundBoundary(next: () -> Unit) {
        if (activity != null) AdManager.maybeShowQuizInterstitial(activity, next) else next()
    }

    // Subscribing to the revision is what keeps the hub honest: finishing a round changes the
    // streak underneath it, and these are plain reads off disk.
    val revision = OceanPrefs.revision
    val today = OceanPrefs.dayIndex
    val streak = remember(revision, today) { OceanPrefs.streakState }
    val doneToday = remember(revision, today) { OceanPrefs.isDailyQuizDone(today) }

    val score = results.count { it }
    val question = questions.getOrNull(index)
    val fact = factById(question?.factId)

    fun start(isDaily: Boolean) {
        daily = isDaily
        playDay = OceanPrefs.dayIndex
        questions = if (isDaily) DailyQuiz.roundForDay(playDay) else Quiz.round()
        index = 0
        picked = null
        results = emptyList()
        missed = emptyList()
        beatBest = false
        outcome = null
        hintUsed = false
        hidden = emptySet()
        stage = Stage.PLAYING
        Analytics.quizStarted(daily = isDaily)
    }

    fun advance() {
        if (index + 1 < questions.size) {
            index += 1
            picked = null
            hidden = emptySet()
        } else {
            Analytics.quizFinished(score, questions.size)
            if (daily) {
                val result = OceanPrefs.recordDailyQuiz(playDay, score)
                outcome = result
                beatBest = result.beatBest
                Analytics.dailyQuizFinished(score, questions.size, result.streak, hintUsed)
            } else {
                beatBest = OceanPrefs.recordQuizRound(score)
            }
            stage = Stage.RESULT
        }
    }

    val accent = when (stage) {
        Stage.PLAYING -> fact?.accent ?: QuizAccent
        else -> QuizAccent
    }
    val glow by animateColorAsState(accent, tween(500), label = "quizGlow")

    Column(Modifier.fillMaxSize().background(Ink).currentGlow(glow)) {
        TopBar(
            stage = stage,
            daily = daily,
            index = index,
            total = questions.size,
            score = score,
            accent = accent,
            onClose = { leave(onClose) },
        )

        when (stage) {
            Stage.INTRO -> Intro(
                streak = streak,
                today = today,
                doneToday = doneToday,
                onPlayDaily = { start(isDaily = true) },
                onPractice = { start(isDaily = false) },
            )

            // The bank cannot realistically empty, but an empty round must not strand.
            Stage.PLAYING -> if (question == null) {
                Intro(streak, today, doneToday, { start(true) }, { start(false) })
            } else {
                Pips(total = questions.size, results = results, current = index, accent = accent)
                Question(
                    question = question,
                    fact = fact,
                    accent = accent,
                    picked = picked,
                    isLast = index + 1 == questions.size,
                    hidden = hidden,
                    // The hint is offered on the daily only. Practice rounds are unlimited and
                    // unscored, so a hint there is worth nothing to the user while turning the
                    // placement into a loop anyone could farm.
                    canHint = daily && !hintUsed && picked == null,
                    onHint = { hintOpen = true },
                    onPick = { choice ->
                        if (picked == null) {
                            picked = choice
                            val correct = choice == question.answerIndex
                            results = results + correct
                            if (!correct) missed = missed + question.factId
                        }
                    },
                    onNext = { advance() },
                    onOpenFact = { id -> leave { onOpenFact(id) } },
                )
            }

            Stage.RESULT -> Result(
                score = score,
                total = questions.size,
                daily = daily,
                outcome = outcome,
                beatBest = beatBest,
                missed = missed,
                onOpenFact = { id -> leave { onOpenFact(id) } },
                onAgain = { atRoundBoundary { start(isDaily = false) } },
                onClose = { atRoundBoundary { leave(onClose) } },
            )
        }
    }

    if (hintOpen && question != null) {
        RewardPrompt(
            title = "Rule out two answers",
            body = "Two of the wrong options will be struck out, once this round.",
            cta = "Use 50/50",
            placement = Analytics.Placement.QUIZ_HINT,
            accent = accent,
            onGranted = { method ->
                hidden = Quiz.fiftyFiftyHidden(question)
                hintUsed = true
                hintOpen = false
                Analytics.hintUsed(method)
            },
            onDismiss = { hintOpen = false },
        )
    }
}

/** A soft wash of the current accent behind everything, bled in from the top corners. */
private fun Modifier.currentGlow(color: Color) = drawBehind {
    drawRect(
        Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.22f), Color.Transparent),
            center = Offset(size.width * 0.22f, 0f),
            radius = size.width * 1.05f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(color.copy(alpha = 0.10f), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.18f),
            radius = size.width * 0.85f,
        ),
    )
}

@Composable
private fun TopBar(
    stage: Stage,
    daily: Boolean,
    index: Int,
    total: Int,
    score: Int,
    accent: Color,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .border(1.dp, Hairline, CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Ico("back", size = 19.dp, color = Color.White, sw = 2.1f) }

        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    stage == Stage.PLAYING && daily -> "TODAY'S QUIZ"
                    stage == Stage.PLAYING -> "PRACTICE ROUND"
                    else -> "DEEP WATER QUIZ"
                },
                style = ts(10.5f, FontWeight.Bold, Dim, 0.22f),
            )
            if (stage == Stage.PLAYING && total > 0) {
                Text(
                    "Question ${index + 1} of $total",
                    style = ts(16f, FontWeight.SemiBold, Color.White),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (stage == Stage.PLAYING) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.30f), CircleShape)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico("check", size = 13.dp, color = accent, sw = 2.6f)
                Spacer(Modifier.width(6.dp))
                Text("$score", style = ts(14f, FontWeight.Bold, accent))
            }
        }
    }
}

/**
 * One pip per question, so progress and performance read in a single glance: answered pips carry
 * their own result, the current one is a wider bar in the accent, the rest stay faint.
 */
@Composable
private fun Pips(total: Int, results: List<Boolean>, current: Int, accent: Color) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { i ->
            val answered = results.getOrNull(i)
            val target = when {
                answered == true -> Right
                answered == false -> Wrong
                i == current -> accent
                else -> Color.White.copy(alpha = 0.12f)
            }
            val color by animateColorAsState(target, tween(280), label = "pip$i")
            val weight by animateFloatAsState(if (i == current) 2.2f else 1f, tween(280), label = "pipW$i")
            Box(Modifier.weight(weight).height(4.dp).clip(CircleShape).background(color))
        }
    }
}

// ───────────────────────── intro ─────────────────────────

@Composable
private fun Intro(
    streak: Streak,
    today: Long,
    doneToday: Boolean,
    onPlayDaily: () -> Unit,
    onPractice: () -> Unit,
) {
    val best = OceanPrefs.quizBest
    val dailies = OceanPrefs.dailyQuizCount
    val todayScore = OceanPrefs.dailyQuizScore

    // A slow breath on the badge, so the opening screen is not completely static.
    val pulse = rememberInfiniteTransition(label = "quizPulse")
    val breath by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "breath",
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(22.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(132.dp)
                    .graphicsLayer { scaleX = breath; scaleY = breath }
                    .background(
                        Brush.radialGradient(listOf(QuizAccent.copy(alpha = 0.26f), Color.Transparent)),
                        CircleShape,
                    ),
            )
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(QuizAccent.copy(alpha = 0.12f))
                    .border(1.dp, QuizAccent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Ico("deepdive", size = 34.dp, color = QuizAccent, filled = true) }
        }

        Text(
            "Deep Water Quiz",
            style = ts(34f, FontWeight.Bold, Color.White, -0.025f),
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            if (doneToday) {
                "Today's round is done. Come back tomorrow for ten new ones — or keep practising."
            } else {
                "Ten questions, the same for everyone today. Finish them to keep your streak."
            },
            style = ts(15f, FontWeight.Light, Mute, lineHeight = 23f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp, start = 6.dp, end = 6.dp),
        )

        if (streak.current > 0) {
            Spacer(Modifier.height(20.dp))
            StreakBanner(streak = streak, today = today)
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile("${streak.longest}", "LONGEST RUN", Modifier.weight(1f))
            StatTile("$dailies", if (dailies == 1) "DAILY DONE" else "DAILIES DONE", Modifier.weight(1f))
            StatTile("$best", "BEST SCORE", Modifier.weight(1f))
        }

        Spacer(Modifier.height(22.dp))
        // Whichever of the two is the point right now leads; the other stays one tap away.
        if (doneToday) {
            PrimaryButton("Practice round", QuizAccent, onClick = onPractice)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Replay today · $todayScore of ${Quiz.ROUND_SIZE}", onClick = onPlayDaily)
        } else {
            PrimaryButton("Play today's quiz", QuizAccent, onClick = onPlayDaily)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Practice round", onClick = onPractice)
        }
        Spacer(Modifier.height(34.dp))
    }
}

/** The run, its record, and the week behind it — the thing the daily round is played for. */
@Composable
private fun StreakBanner(streak: Streak, today: Long, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                Brush.horizontalGradient(
                    listOf(StreakAccent.copy(alpha = 0.18f), StreakAccent.copy(alpha = 0.05f)),
                ),
            )
            .border(1.dp, StreakAccent.copy(alpha = 0.28f), CardShape)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico("bolt", size = 24.dp, color = StreakAccent, filled = true)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text("${streak.current}-day streak", style = ts(18f, FontWeight.Bold, Color.White))
            Text(
                if (streak.current >= streak.longest) "Your longest run yet" else "Longest: ${streak.longest} days",
                style = ts(12.5f, color = Color(0xFFE0B48F)),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        WeekDots(streak.week(today))
    }
}

@Composable
private fun StatTile(value: String, caption: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(CardShape)
            .background(CardFill)
            .border(1.dp, Hairline, CardShape)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = ts(24f, FontWeight.Bold, Color.White))
        Text(
            caption,
            style = ts(9.5f, FontWeight.SemiBold, Dim, 0.13f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
        )
    }
}

// ───────────────────────── a question ─────────────────────────

@Composable
private fun Question(
    question: QuizQuestion,
    fact: Fact?,
    accent: Color,
    picked: Int?,
    isLast: Boolean,
    hidden: Set<Int>,
    canHint: Boolean,
    onHint: () -> Unit,
    onPick: (Int) -> Unit,
    onNext: () -> Unit,
    onOpenFact: (String) -> Unit,
) {
    // Each question slides up as it arrives, so moving on reads as a new card rather than as
    // text being swapped underneath the same options.
    val enter = remember(question.prompt) { Animatable(0f) }
    LaunchedEffect(question.prompt) { enter.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 12.dp)
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 26.dp.toPx()
                },
        ) {
            fact?.let { QuestionEyebrow(it, accent) }
            Text(
                question.prompt,
                style = ts(26f, FontWeight.Bold, Color.White, -0.02f, lineHeight = 33f),
                modifier = Modifier.padding(top = 12.dp, bottom = if (canHint) 14.dp else 22.dp),
            )
            if (canHint) {
                HintPill(accent = accent, armed = enter.value >= 1f, onClick = onHint)
                // A wide gap under the pill on purpose: a button that plays an ad must not sit
                // within a thumb slip of the answer the user actually meant to tap.
                Spacer(Modifier.height(26.dp))
            }
            question.options.forEachIndexed { i, option ->
                Option(
                    letter = ('A' + i).toString(),
                    text = option,
                    accent = accent,
                    state = when {
                        picked == null && i in hidden -> OptionState.STRUCK
                        picked == null -> OptionState.IDLE
                        i == question.answerIndex -> OptionState.RIGHT
                        i == picked -> OptionState.WRONG
                        else -> OptionState.MUTED
                    },
                    onClick = { onPick(i) },
                )
                Spacer(Modifier.height(9.dp))
            }
            if (picked != null && fact != null) {
                Spacer(Modifier.height(8.dp))
                Verdict(
                    correct = picked == question.answerIndex,
                    answer = question.answer,
                    fact = fact,
                    onOpenFact = { onOpenFact(fact.id) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // Pinned to the bottom so the next question is always one tap away in the same place,
        // however long the options run.
        if (picked != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Ink)))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                PrimaryButton(if (isLast) "See your score" else "Next question", accent, onClick = onNext)
            }
        } else {
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}

/** Where the question came from: its zone and the category it sits in. */
@Composable
private fun QuestionEyebrow(fact: Fact, accent: Color) {
    val zone = ZONE_META[fact.zone]?.label
    val text = if (zone == null) fact.cat else "$zone · ${fact.cat}"
    Row(
        Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.26f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(7.dp))
        Text(text.uppercase(), style = ts(10.5f, FontWeight.Bold, accent, 0.12f))
    }
}

/**
 * [STRUCK] is a still-playable option ruled out by the 50/50. It stays in place, greyed and
 * dead, rather than being removed: taking a row out reflows the list and renumbers the A/B/C/D
 * badges under the reader's thumb mid-question.
 */
private enum class OptionState { IDLE, RIGHT, WRONG, MUTED, STRUCK }

/**
 * The 50/50 offer.
 *
 * [armed] is false for the few hundred milliseconds a new question takes to animate in, so a
 * fast tap aimed at the previous screen's button cannot land on an ad prompt by accident.
 */
@Composable
private fun HintPill(accent: Color, armed: Boolean, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (armed) 1f else 0.4f, tween(200), label = "hintArm")
    Row(
        Modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, accent.copy(alpha = 0.28f), CircleShape)
            .clickable(enabled = armed, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico("bolt", size = 13.dp, color = accent, filled = true)
        Spacer(Modifier.width(7.dp))
        Text("50/50", style = ts(12.5f, FontWeight.Bold, accent, 0.06f))
        Spacer(Modifier.width(7.dp))
        Text("rule out two", style = ts(12.5f, FontWeight.Medium, Mute))
    }
}

@Composable
private fun Option(
    letter: String,
    text: String,
    accent: Color,
    state: OptionState,
    onClick: () -> Unit,
) {
    val tint = when (state) {
        OptionState.IDLE -> accent
        OptionState.RIGHT -> Right
        OptionState.WRONG -> Wrong
        OptionState.MUTED, OptionState.STRUCK -> Color.White.copy(alpha = 0.22f)
    }
    val border by animateColorAsState(
        when (state) {
            OptionState.IDLE -> Color.White.copy(alpha = 0.10f)
            OptionState.MUTED, OptionState.STRUCK -> Color.White.copy(alpha = 0.05f)
            else -> tint.copy(alpha = 0.75f)
        },
        tween(260),
        label = "optBorder",
    )
    val fill by animateColorAsState(
        when (state) {
            OptionState.RIGHT -> Right.copy(alpha = 0.13f)
            OptionState.WRONG -> Wrong.copy(alpha = 0.13f)
            OptionState.MUTED, OptionState.STRUCK -> Color.White.copy(alpha = 0.02f)
            else -> CardFill
        },
        tween(260),
        label = "optFill",
    )
    val textColor by animateColorAsState(
        if (state == OptionState.MUTED || state == OptionState.STRUCK) Color(0xFF63757F) else Color.White,
        tween(260),
        label = "optLabel",
    )
    // The chosen row lifts very slightly, so the tap has a physical answer to it.
    val scale by animateFloatAsState(
        if (state == OptionState.RIGHT || state == OptionState.WRONG) 1.015f else 1f,
        tween(260, easing = FastOutSlowInEasing),
        label = "optScale",
    )

    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill)
            .border(1.dp, border, shape)
            .clickable(enabled = state == OptionState.IDLE, onClick = onClick)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    tint.copy(
                        alpha = if (state == OptionState.MUTED || state == OptionState.STRUCK) 0.05f else 0.16f,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                OptionState.RIGHT -> Ico("check", size = 15.dp, color = Right, sw = 2.6f)
                OptionState.WRONG -> Ico("close", size = 14.dp, color = Wrong, sw = 2.4f)
                OptionState.STRUCK -> Ico("close", size = 13.dp, color = Color(0xFF63757F), sw = 2.2f)
                else -> Text(
                    letter,
                    style = ts(
                        13f,
                        FontWeight.Bold,
                        if (state == OptionState.MUTED) Color(0xFF63757F) else tint,
                    ),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Text(text, style = ts(15.5f, FontWeight.Medium, textColor, lineHeight = 21f), modifier = Modifier.weight(1f))
    }
}

/** Shown once an answer is locked in: the verdict, the answer, and a way back to the fact. */
@Composable
private fun Verdict(correct: Boolean, answer: String, fact: Fact, onOpenFact: () -> Unit) {
    val tone = if (correct) Right else Wrong
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(tone.copy(alpha = 0.07f))
            .border(1.dp, tone.copy(alpha = 0.22f), CardShape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(if (correct) "check" else "close", size = 15.dp, color = tone, sw = 2.5f)
            Spacer(Modifier.width(8.dp))
            Text(if (correct) "Correct" else "Not quite", style = ts(14f, FontWeight.Bold, tone, 0.02f))
        }
        if (!correct) {
            // Set on its own line under a label rather than folded into a sentence: the options
            // are written as phrases, and "The answer is Bacteria living on…" reads badly.
            Text(
                "THE ANSWER",
                style = ts(9.5f, FontWeight.Bold, Dim, 0.16f),
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                answer,
                style = ts(16f, FontWeight.SemiBold, Color.White, lineHeight = 22f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable(onClick = onOpenFact)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(fact.title, style = ts(14f, FontWeight.SemiBold, Color.White))
                if (fact.sub.isNotBlank()) {
                    Text(
                        fact.sub,
                        style = ts(12.5f, color = Mute, lineHeight = 17f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Ico("chevR", size = 16.dp, color = fact.accent)
        }
    }
}

// ───────────────────────── the score ─────────────────────────

@Composable
private fun Result(
    score: Int,
    total: Int,
    daily: Boolean,
    outcome: DailyQuizOutcome?,
    beatBest: Boolean,
    missed: List<String>,
    onOpenFact: (String) -> Unit,
    onAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val facts = remember(missed) { missed.mapNotNull { factById(it) } }
    val pct = if (total == 0) 0 else score * 100 / total
    LazyColumn(
        Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 30.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreRing(score = score, total = total)
                // The streak goes above everything else the score screen has to say. It is the
                // reason the round was played, and the only line that says come back.
                if (outcome != null && outcome.extended) {
                    Spacer(Modifier.height(20.dp))
                    StreakEarned(outcome)
                }
                if (beatBest) {
                    Row(
                        Modifier
                            .padding(top = 16.dp)
                            .clip(CircleShape)
                            .background(QuizAccent.copy(alpha = 0.14f))
                            .border(1.dp, QuizAccent.copy(alpha = 0.32f), CircleShape)
                            .padding(horizontal = 13.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Ico("bolt", size = 13.dp, color = QuizAccent, filled = true)
                        Spacer(Modifier.width(6.dp))
                        Text("NEW PERSONAL BEST", style = ts(10.5f, FontWeight.Bold, QuizAccent, 0.14f))
                    }
                }
                Text(
                    when {
                        outcome != null && !outcome.firstToday -> "Today was already counted — this one was for the practice."
                        score == total -> "Every one. Nothing down there caught you out."
                        pct >= 75 -> "Strong round."
                        pct >= 50 -> "Over halfway there."
                        else -> "Plenty of water left to learn."
                    },
                    style = ts(16f, FontWeight.Light, Color(0xFFCADCE4), lineHeight = 24f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("$pct%", "ACCURACY", Modifier.weight(1f))
                    StatTile("${total - score}", if (total - score == 1) "MISSED" else "MISSES", Modifier.weight(1f))
                    StatTile("${OceanPrefs.quizBest}", "BEST SCORE", Modifier.weight(1f))
                }
                Spacer(Modifier.height(26.dp))
            }
        }
        if (facts.isNotEmpty()) {
            item {
                Text(
                    "WORTH A SECOND LOOK",
                    style = ts(11f, FontWeight.Bold, Dim, 0.2f),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items(facts.size) { i ->
                val fact = facts[i]
                Box(Modifier.padding(bottom = 10.dp)) {
                    FactListCard(fact = fact, onClick = { onOpenFact(fact.id) })
                }
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            PrimaryButton(if (daily) "Practice round" else "Play again", QuizAccent, onClick = onAgain)
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Done", onClick = onClose)
        }
    }
}

/**
 * The payoff: the run this round just extended, and the week it sits in.
 *
 * Deliberately loud compared with the rest of the score screen — a streak only works as a reason
 * to return if the moment it grows is worth seeing.
 */
@Composable
private fun StreakEarned(outcome: DailyQuizOutcome) {
    val grow = remember(outcome.streak) { Animatable(0.7f) }
    LaunchedEffect(outcome.streak) { grow.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = grow.value; scaleY = grow.value }
            .clip(CardShape)
            .background(
                Brush.horizontalGradient(
                    listOf(StreakAccent.copy(alpha = 0.20f), StreakAccent.copy(alpha = 0.06f)),
                ),
            )
            .border(1.dp, StreakAccent.copy(alpha = 0.32f), CardShape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico("bolt", size = 22.dp, color = StreakAccent, filled = true)
            Spacer(Modifier.width(10.dp))
            Text("${outcome.streak}-day streak", style = ts(22f, FontWeight.Bold, Color.White, -0.02f))
        }
        Text(
            if (outcome.newLongest) "Your longest run yet." else "Come back tomorrow to keep it going.",
            style = ts(13.5f, color = Color(0xFFE0B48F)),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp),
        )
        WeekDots(outcome.week, dot = 8.dp, modifier = Modifier.padding(top = 12.dp))
    }
}

/** The score as a sweep around a ring, drawn up from zero when the round ends. */
@Composable
private fun ScoreRing(score: Int, total: Int) {
    val target = if (total == 0) 0f else score.toFloat() / total
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(target) { sweep.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }

    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (sweep.value > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(QuizAccent, Right, QuizAccent)),
                    startAngle = -90f,
                    sweepAngle = 360f * sweep.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", style = ts(62f, FontWeight.Bold, Color.White, -0.04f))
            Text("OUT OF $total", style = ts(11f, FontWeight.SemiBold, Dim, 0.18f))
        }
    }
}
