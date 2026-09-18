package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.ALL_FACTS
import com.pixel.oceanfacts.core.Analytics
import com.pixel.oceanfacts.core.Fact
import com.pixel.oceanfacts.core.ZONE_META
import kotlinx.coroutines.delay

/**
 * Search over the whole catalog.
 *
 * Matching is a plain case-insensitive substring over everything the reader can see, including
 * the blurb and the stat values, so "glow", "1912" and "pressure" all find something. The
 * catalog is a few dozen items held in memory, so filtering on every keystroke costs nothing.
 */
@Composable
fun SearchScreen(viewed: Set<String>, onOpen: (String) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val results = remember(query) { search(query) }

    // Debounced, so a settled query is recorded once rather than once per keystroke.
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(600)
        Analytics.search(query, results.size)
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)).clickable {
                    keyboard?.hide()
                    onClose()
                },
                contentAlignment = Alignment.Center,
            ) { Ico("back", size = 20.dp, color = Color.White, sw = 2.1f) }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search ${ALL_FACTS.size} facts", style = ts(17f, FontWeight.Normal, Dim))
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = ts(17f, FontWeight.Normal, Color.White),
                    cursorBrush = SolidColor(QuizAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f))
                        .clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) { Ico("close", size = 15.dp, color = Color.White, sw = 2f) }
            }
        }

        when {
            query.isBlank() -> Hint("Search by name, by what it is, or by a number — “glow”, “pressure”, “1912”.")
            results.isEmpty() -> Hint("Nothing matches “$query”.")
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 102.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(results.size) { i ->
                    val fact = results[i]
                    FactListCard(
                        fact = fact,
                        onClick = {
                            keyboard?.hide()
                            onOpen(fact.id)
                        },
                        isSeen = fact.id in viewed,
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(
        text,
        style = ts(14.5f, color = Dim, lineHeight = 22f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
    )

/**
 * Ranked so an exact title hit never sits below a fact that merely mentions the word in its
 * blurb; within a tier the catalog's own order is kept, which groups a zone together.
 */
private fun search(query: String): List<Fact> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return ALL_FACTS
        .mapNotNull { fact ->
            val rank = rank(fact, q) ?: return@mapNotNull null
            rank to fact
        }
        .sortedBy { it.first }
        .map { it.second }
}

private fun rank(fact: Fact, q: String): Int? {
    val title = fact.title.lowercase()
    if (title == q) return 0
    if (title.startsWith(q)) return 1
    if (title.contains(q)) return 2
    if (fact.sub.lowercase().contains(q)) return 3
    if (fact.cat.lowercase().contains(q)) return 4
    if (ZONE_META[fact.zone]?.label?.lowercase()?.contains(q) == true) return 5
    if (fact.blurb.lowercase().contains(q)) return 6
    if (fact.stats.any { (k, v) -> k.lowercase().contains(q) || v.lowercase().contains(q) }) return 7
    return null
}
