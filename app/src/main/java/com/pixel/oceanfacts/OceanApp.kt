package com.pixel.oceanfacts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.ALL_FACTS
import com.pixel.oceanfacts.core.Analytics
import com.pixel.oceanfacts.core.OceanPrefs
import com.pixel.oceanfacts.core.factById
import com.pixel.oceanfacts.core.factsForZone
import com.pixel.oceanfacts.ui.BannerAd
import com.pixel.oceanfacts.ui.BottomNav
import com.pixel.oceanfacts.ui.DeepDiveScreen
import com.pixel.oceanfacts.ui.DescentScreen
import com.pixel.oceanfacts.ui.DetailSheet
import com.pixel.oceanfacts.ui.FactScreen
import com.pixel.oceanfacts.ui.Ink
import com.pixel.oceanfacts.ui.ProfileScreen
import com.pixel.oceanfacts.ui.QuizScreen
import com.pixel.oceanfacts.ui.QuizSession
import com.pixel.oceanfacts.ui.SavedScreen
import com.pixel.oceanfacts.ui.SearchScreen
import com.pixel.oceanfacts.ui.ShareCardCapture
import com.pixel.oceanfacts.ui.SourceWebScreen
import com.pixel.oceanfacts.ui.WhatsNewSheet
import com.pixel.oceanfacts.ui.ZoneExplore
import com.pixel.oceanfacts.ui.ZonesList
import com.pixel.oceanfacts.ui.ts
import kotlinx.coroutines.delay

/**
 * The whole app, in one composable.
 *
 * There is no navigation library: every destination is a piece of state held here, and a single
 * [BackHandler] unwinds them in the order they were stacked. For an app this size that is less
 * code and fewer surprises than a graph — and it keeps the one thing that matters, which fact is
 * playing, in a place every other surface can read.
 */
@Composable
fun OceanFactsApp(
    pendingFactId: String? = null,
    onPendingFactConsumed: () -> Unit = {},
    pendingQuiz: Boolean = false,
    onPendingQuizConsumed: () -> Unit = {},
) {
    var tab by remember { mutableStateOf("zones") }
    var openZone by remember { mutableStateOf<String?>(null) }
    var factId by remember { mutableStateOf<String?>(null) }
    var paused by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(OceanPrefs.saved) }
    var sheet by remember { mutableStateOf(false) }
    var sourceUrl by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var viewed by remember { mutableStateOf(OceanPrefs.viewed) }
    var barVisible by remember { mutableStateOf(true) }
    var whatsNew by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val quizSession = remember { QuizSession().apply { restore() } }
    var quizOpen by remember { mutableStateOf(quizSession.openState.value) }
    // Non-null only while a share card is being drawn and captured.
    var sharing by remember { mutableStateOf<String?>(null) }

    // Mirror the collection and the seen list back to disk whenever they change, so both survive
    // the process. The first run of each is a no-op write of what was just read.
    LaunchedEffect(saved) { OceanPrefs.saved = saved }
    LaunchedEffect(viewed) { OceanPrefs.viewed = viewed }

    LaunchedEffect(quizOpen) { quizSession.noteOpen(quizOpen) }

    // Hide the bottom bar when the content scrolls down, reveal it when scrolling up.
    val barScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -2f) barVisible = false
                else if (available.y > 2f) barVisible = true
                return Offset.Zero
            }
        }
    }
    // Always show the bar again when switching tabs.
    LaunchedEffect(tab, openZone) { barVisible = true }

    val context = LocalContext.current
    val adShowing = AdManager.isAdShowing

    // Show the release notes once, on the first launch after an update. consumeWhatsNew records
    // the version as it answers, so re-running this (after a rotation, say) returns false rather
    // than showing the sheet again.
    LaunchedEffect(Unit) {
        whatsNew = OceanPrefs.consumeWhatsNew(AppActions.versionCode(context))
    }

    fun toggleSave(id: String) {
        val nowSaved = !saved.contains(id)
        saved = if (nowSaved) saved + id else saved - id
        factById(id)?.let { Analytics.factSave(it, nowSaved) }
    }

    val activity = context as? android.app.Activity

    // Route a navigation transition through the shared interstitial cap, then run [action].
    // maybeShowInterstitial always calls back (immediately if no ad, no Activity or capped), so
    // navigation is never blocked.
    fun withAd(action: () -> Unit) {
        if (activity != null) AdManager.maybeShowInterstitial(activity, action) else action()
    }

    fun openFact(id: String, source: String) {
        // The streak counts days something was actually read, not bare launches, so it is
        // recorded here rather than in MainActivity. Finishing the daily quiz credits the same
        // day through the same transform. Idempotent within a day.
        OceanPrefs.recordActivity()
        factById(id)?.let { Analytics.factView(it, source) }
        withAd { factId = id; paused = false; sheet = false; sourceUrl = null; viewed = viewed + id }
    }

    /**
     * Opens a fact without consulting the ad cap. Used for deep links, where the user tapped a
     * notification or a shared link rather than navigating inside the app — an interstitial on
     * arrival would be an ad they never asked for.
     */
    fun jumpToFact(id: String) {
        OceanPrefs.recordActivity()
        factById(id)?.let { Analytics.factView(it, Analytics.Source.DEEP_LINK) }
        factId = id
        paused = false
        sheet = false
        sourceUrl = null
        viewed = viewed + id
    }

    // A deep link can arrive before this composes (a cold start behind the update gate) or long
    // after it (singleTop onNewIntent), so it is consumed here rather than passed in once.
    LaunchedEffect(pendingFactId) {
        val target = pendingFactId ?: return@LaunchedEffect
        if (factById(target) != null) {
            tab = "zones"
            openZone = null
            jumpToFact(target)
        }
        onPendingFactConsumed()
    }

    // The reminder points here when a streak is at stake. Not routed through the ad cap, for the
    // same reason jumpToFact is not.
    LaunchedEffect(pendingQuiz) {
        if (!pendingQuiz) return@LaunchedEffect
        tab = "zones"
        openZone = null
        searching = false
        // Load-bearing: the quiz only renders while no fact player is open, so arriving from a
        // notification on top of one would otherwise set the flag with nothing on screen.
        factId = null
        quizSession.discardStaleDaily()
        quizOpen = true
        Analytics.notificationOpened("quiz")
        onPendingQuizConsumed()
    }

    fun exitPlayer() {
        sheet = false
        sourceUrl = null
        factId = null
    }

    LaunchedEffect(toast) {
        if (toast != null) { delay(1700); toast = null }
    }

    // Unwind the in-app navigation stack on system back before letting the OS exit.
    BackHandler(
        enabled = sourceUrl != null || sheet || factId != null || searching ||
            quizOpen || openZone != null || tab != "zones",
    ) {
        when {
            sourceUrl != null -> sourceUrl = null
            sheet -> sheet = false
            factId != null -> exitPlayer()
            searching -> searching = false
            quizOpen -> { quizSession.clearIfFinished(); quizOpen = false }
            openZone != null -> openZone = null
            tab != "zones" -> tab = "zones"
        }
    }

    val curFact = factById(factId) ?: ALL_FACTS.firstOrNull()

    // Page through the facts of the opened fact's own zone (works for zones, Deep Dive and Saved
    // alike — independent of which screen launched it).
    val pagerZone = factById(factId)?.zone
    val pagerFacts = remember(pagerZone) {
        pagerZone?.let { factsForZone(it) } ?: emptyList()
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        if (factId != null && pagerFacts.isNotEmpty()) {
            val initialPage = remember(pagerFacts) {
                pagerFacts.indexOfFirst { it.id == factId }.coerceAtLeast(0)
            }
            val pagerState = key(pagerZone) { rememberPagerState(initialPage = initialPage) { pagerFacts.size } }

            LaunchedEffect(factId, pagerFacts) {
                val target = pagerFacts.indexOfFirst { it.id == factId }
                if (target >= 0 && target != pagerState.currentPage) pagerState.scrollToPage(target)
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.currentPage }.collect { page ->
                    val newFact = pagerFacts[page]
                    if (factId != newFact.id) {
                        factId = newFact.id
                        viewed = viewed + newFact.id
                        // Scrolling facts: surface an ad on the first swipe once 120 s have
                        // elapsed since the last one. Time-based, not per-swipe.
                        activity?.let { AdManager.maybeShowInterstitialAfter(it, 120_000L) {} }
                    }
                }
            }

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { pagerFacts[it].id },
            ) { page ->
                val f = pagerFacts[page]
                FactScreen(
                    fact = f,
                    paused = paused || sheet || sourceUrl != null || whatsNew || adShowing,
                    isActive = factId == f.id,
                    onTogglePause = { paused = !paused },
                    onBack = { exitPlayer() },
                    onLearn = { sheet = true },
                    isSaved = saved.contains(f.id),
                    onToggleSave = {
                        val was = saved.contains(f.id)
                        toggleSave(f.id)
                        toast = if (was) "Removed from Saved" else "Saved to your collection"
                    },
                )
            }
        } else {
            Box(Modifier.fillMaxSize().nestedScroll(barScrollConnection)) {
                when (tab) {
                    "zones" -> {
                        val zone = openZone
                        if (zone != null) {
                            ZoneExplore(
                                zone = zone,
                                viewed = viewed,
                                onOpenFact = { openFact(it, Analytics.Source.ZONE) },
                                onBack = { openZone = null },
                            )
                        } else {
                            ZonesList(
                                viewed = viewed,
                                onOpenZone = { z -> Analytics.zoneOpen(z); withAd { openZone = z } },
                                onOpenFact = { openFact(it, Analytics.Source.TODAY) },
                                onOpenSearch = { searching = true },
                                onOpenQuiz = { quizSession.discardStaleDaily(); quizOpen = true },
                            )
                        }
                    }
                    "deepdive" -> DeepDiveScreen(
                        viewed = viewed,
                        onOpen = { openFact(it, Analytics.Source.DEEP_DIVE) },
                    )
                    "descent" -> DescentScreen(externalPaused = whatsNew || sourceUrl != null || adShowing)
                    "saved" -> SavedScreen(
                        saved = saved,
                        viewed = viewed,
                        onOpen = { openFact(it, Analytics.Source.SAVED) },
                    )
                    "you" -> ProfileScreen(
                        savedCount = saved.size,
                        viewed = viewed.size,
                        onClearSaved = { saved = emptySet(); toast = "Saved collection cleared" },
                    )
                }
            }
        }

        if (quizOpen && factId == null) {
            QuizScreen(
                session = quizSession,
                // Leaves the quiz rather than stacking on top of it: the round is finished by the
                // time these are reachable.
                onOpenFact = { quizOpen = false; openFact(it, Analytics.Source.QUIZ) },
                onClose = { quizOpen = false },
            )
        }

        if (searching && factId == null) {
            SearchScreen(
                viewed = viewed,
                onOpen = { searching = false; openFact(it, Analytics.Source.SEARCH) },
                onClose = { searching = false },
            )
        }

        WhatsNewSheet(
            open = whatsNew,
            onClose = { whatsNew = false },
            onOpenZone = { z ->
                whatsNew = false
                tab = "zones"
                openZone = z
            },
        )

        curFact?.let { fact ->
            DetailSheet(
                fact = fact,
                open = sheet,
                onClose = { sheet = false },
                onJump = { sheet = false; openFact(it, Analytics.Source.ZONE) },
                isSaved = saved.contains(fact.id),
                onToggleSave = { toggleSave(fact.id) },
                onOpenSource = { sourceUrl = it },
                onShare = { Analytics.factShare(fact); sharing = fact.id },
            )
        }

        sharing?.let { id ->
            factById(id)?.let { f ->
                ShareCardCapture(fact = f, onDone = { sharing = null })
            }
        }

        sourceUrl?.let { url ->
            SourceWebScreen(
                url = url,
                accent = curFact?.accent ?: com.pixel.oceanfacts.ui.QuizAccent,
                onClose = { sourceUrl = null },
            )
        }

        toast?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (factId != null) 30.dp else 90.dp)
                    .clip(RoundedCornerShape(100))
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.95f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    msg,
                    style = ts(14f, FontWeight.SemiBold, androidx.compose.ui.graphics.Color.Black),
                )
            }
        }

        // The what's-new sheet is modal: without this the bar draws over it and stays tappable
        // behind the scrim.
        if (factId == null && !whatsNew && !searching && !quizOpen) {
            Column(Modifier.align(Alignment.BottomCenter)) {
                // The banner lives on the Descent tab only: it is the one screen people leave
                // running, and the only one with nothing to read underneath it.
                if (tab == "descent" && AdManager.adsEnabled) BannerAd(applyNavInset = !barVisible)
                AnimatedVisibility(
                    visible = barVisible,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    BottomNav(tab = tab, onSelect = { tab = it })
                }
            }
        }
    }
}
