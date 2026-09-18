package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.DEEP_DIVE_FACTS

/** Standalone facts that belong to the whole ocean rather than to one layer of it. */
@Composable
fun DeepDiveScreen(viewed: Set<String>, onOpen: (String) -> Unit) {
    val facts = DEEP_DIVE_FACTS
    Column(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier.statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 8.dp),
        ) {
            Text("DEEP DIVE", style = ts(12f, FontWeight.Bold, Mute, 0.28f))
            Text(
                "Beyond the zones",
                style = ts(32f, FontWeight.Bold, Color.White, -0.02f),
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "Wrecks, rivers, tides and currents — the sea taken as one thing.",
                style = ts(15f, FontWeight.Light, Mute, lineHeight = 22f),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 102.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(facts.size) { i ->
                val fact = facts[i]
                FactListCard(fact = fact, onClick = { onOpen(fact.id) }, isSeen = fact.id in viewed)
            }
        }
    }
}
