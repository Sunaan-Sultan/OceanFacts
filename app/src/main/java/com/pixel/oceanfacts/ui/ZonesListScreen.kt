package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.Analytics
import com.pixel.oceanfacts.core.DailyFact
import com.pixel.oceanfacts.core.FEATURED_ZONES
import com.pixel.oceanfacts.core.Fact
import com.pixel.oceanfacts.core.FeaturedZone
import com.pixel.oceanfacts.core.OceanPrefs
import com.pixel.oceanfacts.core.Quiz
import com.pixel.oceanfacts.core.REALMS
import com.pixel.oceanfacts.core.Streak
import com.pixel.oceanfacts.core.factsForZone

/**
 * The home screen: today's fact, the daily quiz and streak, then the four zones in the order
 * you would meet them going down.
 */
@Composable
fun ZonesList(
    viewed: Set<String>,
    onOpenZone: (String) -> Unit,
    onOpenFact: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenQuiz: () -> Unit,
) {
    // Recomputed per composition rather than remembered: the pick rolls over at local midnight,
    // and a session can outlive that.
    val today = DailyFact.factForToday()

    // The revision is what keeps the quiz card honest. Finishing a round changes the streak
    // underneath it, and a card still urging you to keep a streak you just kept is worse than
    // no card at all.
    val revision = OceanPrefs.revision
    val dayIndex = OceanPrefs.dayIndex
    val streak = remember(revision, dayIndex) { OceanPrefs.streakState }
    val doneToday = remember(revision, dayIndex) { OceanPrefs.isDailyQuizDone(dayIndex) }
    val offer = remember(revision, dayIndex) { OceanPrefs.repairOffer() }
    var repairOpen by remember { mutableStateOf(false) }
    var repaired by remember { mutableStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("OCEAN FACTS", style = ts(12f, FontWeight.Bold, Mute, 0.34f))
                    Text(
                        "The zones",
                        style = ts(34f, FontWeight.Bold, Color.White, -0.02f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(onClick = onOpenSearch),
                    contentAlignment = Alignment.Center,
                ) { Ico("search", size = 20.dp, color = Color.White, sw = 2f) }
            }
            Text(
                "Eleven kilometres of water, one layer at a time.",
                style = ts(15f, FontWeight.Light, Mute, lineHeight = 22f),
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        today?.let { TodayCard(fact = it, onClick = { onOpenFact(it.id) }) }

        // A card rather than a dialog on launch: an offer that opens itself one tap from a video
        // reads as an ad nobody asked for, whatever it is offering.
        if (repaired > 0) {
            StreakRestoredCard(streak = repaired)
        } else {
            offer?.let { StreakRepairCard(lost = it.lostStreak, onClick = { repairOpen = true }) }
        }

        QuizCard(streak = streak, today = dayIndex, doneToday = doneToday, onClick = onOpenQuiz)

        FEATURED_ZONES.forEach { zone ->
            val inZone = factsForZone(zone.zoneId)
            ZoneCard(
                zone = zone,
                factCount = inZone.size,
                seenCount = inZone.count { it.id in viewed },
                onClick = { onOpenZone(zone.zoneId) },
            )
        }

        Row(
            Modifier.padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MORE REALMS", style = ts(12f, FontWeight.Bold, Mute, 0.24f))
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        }
        REALMS.forEach { realm ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 15.dp).alpha(0.8f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sphere(sizeUnits = 44f, colors = realm.color, glow = realm.color[1].copy(alpha = 0.4f))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(realm.name, style = ts(16f, FontWeight.SemiBold, Color.White))
                    Text(
                        "${realm.depth} · ${realm.desc}",
                        style = ts(12.5f, color = Mute),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(100))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico("lock", size = 12.dp, color = Mute, sw = 2f)
                    Spacer(Modifier.width(5.dp))
                    Text("SOON", style = ts(11f, FontWeight.SemiBold, Mute, 0.08f))
                }
            }
        }
        Text(
            "More of the sea is charted and added over time.",
            style = ts(12.5f, color = Dim, lineHeight = 19f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 22.dp),
        )
    }

    LaunchedEffect(offer?.lostStreak) {
        offer?.let { Analytics.streakRepairOffered(it.lostStreak) }
    }

    if (repairOpen && offer != null) {
        RewardPrompt(
            title = "Restore your ${offer.lostStreak}-day streak",
            body = "It picks up where it left off, as though yesterday had counted.",
            cta = "Restore my streak",
            placement = Analytics.Placement.STREAK_REPAIR,
            accent = StreakAccent,
            onGranted = { method ->
                repaired = OceanPrefs.repairStreak()
                repairOpen = false
                Analytics.streakRepaired(repaired, method)
            },
            onDismiss = { repairOpen = false },
            // Our own fill failure must not cost someone a month-long run. After a couple of
            // honest attempts the streak is simply given back; the grant is logged as a fallback
            // so the size of the leak stays visible.
            onFailed = { OceanPrefs.noteRepairFailure() },
        )
    }
}

/** The receipt for a repair, so watching a video visibly bought something. */
@Composable
private fun StreakRestoredCard(streak: Int) {
    Row(
        Modifier
            .padding(start = 22.dp, end = 22.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Right.copy(alpha = 0.10f))
            .border(1.dp, Right.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico("check", size = 20.dp, color = Right, sw = 2.4f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Streak restored", style = ts(16f, FontWeight.Bold, Color.White))
            Text(
                "You are back to $streak days. Keep it going tomorrow.",
                style = ts(12.5f, color = Mute),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The way into the quiz, and the only place on the home screen the streak is visible. A row
 * rather than another big card, so it sits between the daily fact and the zone library without
 * competing with either.
 */
@Composable
private fun QuizCard(streak: Streak, today: Long, doneToday: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 22.dp, end = 22.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(QuizAccent.copy(alpha = 0.16f), StreakAccent.copy(alpha = 0.05f)),
                ),
            )
            .border(1.dp, QuizAccent.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico("deepdive", size = 26.dp, color = QuizAccent, filled = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Daily Quiz", style = ts(18f, FontWeight.Bold, Color.White))
            Text(
                when {
                    doneToday -> "Done today · ${OceanPrefs.dailyQuizScore} of ${Quiz.ROUND_SIZE}"
                    streak.current > 1 -> "Ten questions · keep your ${streak.current}-day streak"
                    else -> "Ten questions · the same for everyone today"
                },
                style = ts(13f, color = Color(0xFF9FD0CC)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (streak.current > 0) {
                WeekDots(streak.week(today), dot = 6.dp, gap = 5.dp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        if (doneToday) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(Right.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Ico("check", size = 14.dp, color = Right, sw = 2.6f) }
        } else {
            Ico("chevR", size = 18.dp, color = QuizAccent)
        }
    }
}

/**
 * Shown only on the day a run of three or more is lost to a single missed day — see
 * [com.pixel.oceanfacts.core.Perks] for why those are the limits.
 */
@Composable
private fun StreakRepairCard(lost: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 22.dp, end = 22.dp, top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StreakAccent.copy(alpha = 0.10f))
            .border(1.dp, StreakAccent.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico("bolt", size = 24.dp, color = StreakAccent, filled = true)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Your $lost-day streak ended", style = ts(16f, FontWeight.Bold, Color.White))
            Text(
                "You missed yesterday — you can still get it back today.",
                style = ts(12.5f, color = Color(0xFFE0B48F)),
                maxLines = 2,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Ico("chevR", size = 18.dp, color = StreakAccent)
    }
}

@Composable
private fun TodayCard(fact: Fact, onClick: () -> Unit) {
    Box(Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(178.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, fact.accent.copy(alpha = 0.32f), RoundedCornerShape(22.dp))
                .clickable(onClick = onClick),
        ) {
            MiniStage(fact, active = false, paused = true, modifier = Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.3f to Color.Transparent, 1f to Ink.copy(alpha = 0.9f)),
                ),
            )
            Box(Modifier.align(Alignment.TopStart).padding(14.dp)) {
                CategoryPill("Today", fact.accent, filledBg = fact.accent.copy(alpha = 0.16f))
            }
            Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) {
                Text(depthLabel(fact.depthM), style = ts(12f, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f), 0.06f))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 16.dp, bottom = 15.dp)) {
                Text(
                    fact.title,
                    style = ts(26f, FontWeight.Bold, Color.White, -0.02f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    fact.sub,
                    style = ts(13.5f, FontWeight.Medium, Color(0xFFCFE0E8)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ZoneCard(zone: FeaturedZone, factCount: Int, seenCount: Int, onClick: () -> Unit) {
    val subtitle = when {
        seenCount == 0 -> "${zone.tagline} · $factCount facts"
        seenCount >= factCount -> "${zone.tagline} · all $factCount explored"
        else -> "${zone.tagline} · $seenCount of $factCount explored"
    }
    val progress = if (factCount == 0) 0f else seenCount.toFloat() / factCount
    Box(Modifier.padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                .clickable(onClick = onClick),
        ) {
            MiniStage(
                zone.scene, zone.dur, zone.hero, zone.depthM,
                active = false, paused = true, modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.38f to Color.Transparent, 1f to Ink.copy(alpha = 0.9f)),
                ),
            )
            Box(Modifier.align(Alignment.TopStart).padding(14.dp)) {
                CategoryPill(zone.pill, zone.pillColor, filledBg = zone.pillColor.copy(alpha = 0.16f))
            }
            if (seenCount > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.14f)),
                ) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(zone.pillColor))
                }
            }
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(zone.title, style = ts(32f, FontWeight.Bold, Color.White, -0.02f))
                    Text(
                        subtitle,
                        style = ts(13.5f, FontWeight.Medium, Color(0xFFCFE0E8)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(100))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Explore", style = ts(13f, FontWeight.SemiBold, Color.White))
                    Spacer(Modifier.width(5.dp))
                    Ico("chevR", size = 15.dp, color = Color.White, sw = 2.2f)
                }
            }
        }
    }
}
