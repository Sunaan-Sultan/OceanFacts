package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.oceanfacts.core.ALL_FACTS

@Composable
fun SavedScreen(saved: Set<String>, viewed: Set<String>, onOpen: (String) -> Unit) {
    val items = ALL_FACTS.filter { saved.contains(it.id) }
    Column(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier.statusBarsPadding().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 16.dp),
        ) {
            Text("YOUR COLLECTION", style = ts(12f, FontWeight.Bold, Dim, 0.24f))
            Text(
                "Saved",
                style = ts(32f, FontWeight.Bold, Color.White, -0.02f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) { Ico("saved", size = 28.dp, color = Dim) }
                Box(Modifier.height(18.dp))
                Text("Nothing saved yet", style = ts(17f, FontWeight.SemiBold, Color(0xFFCADCE4)))
                Text(
                    "Tap the bookmark on any fact to keep it here.",
                    style = ts(14f, color = Dim, lineHeight = 21f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 102.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items.size) { i ->
                    val item = items[i]
                    FactListCard(fact = item, onClick = { onOpen(item.id) }, isSeen = item.id in viewed)
                }
            }
        }
    }
}
