package com.pixel.oceanfacts.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shown while Play is being asked whether a forced update is due. Deliberately just the app's
 * own background: the answer normally arrives in a few hundred milliseconds, and a spinner that
 * flashes for that long reads as jank. Its job is only to make sure the app is never briefly
 * interactive before the gate can come down.
 */
@Composable
fun UpdateCheckSplash() = Box(Modifier.fillMaxSize().background(Ink))

/**
 * The hard gate. Replaces the whole app when a required update is outstanding, so there is no
 * way past it other than taking the update — and, unlike closing the app, it leaves the user one
 * obvious action.
 */
@Composable
fun UpdateRequiredScreen(onUpdate: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Sphere(
                sizeUnits = 92f,
                colors = listOf(Color(0xFF9FF0E8), Color(0xFF2F9FB4), Color(0xFF0B2E44)),
                glow = Color(0xFF3FE0D8).copy(alpha = 0.34f),
            )
            Spacer(Modifier.height(30.dp))
            Text("UPDATE REQUIRED", style = ts(12f, FontWeight.Bold, QuizAccent, 0.22f))
            Text(
                "Time to resurface",
                style = ts(30f, FontWeight.Bold, Color.White, -0.02f),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "A newer version of Ocean Facts is needed before you can keep exploring.",
                style = ts(14.5f, color = Mute, lineHeight = 22f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(28.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(100))
                    .background(Color.White)
                    .clickable(onClick = onUpdate)
                    .padding(horizontal = 30.dp, vertical = 14.dp),
            ) {
                Text("Update now", style = ts(15f, FontWeight.SemiBold, Color.Black))
            }
            Spacer(Modifier.height(14.dp))
            Text("Google Play will handle the rest.", style = ts(12.5f, color = Dim))
        }
        // The refresh glyph, tucked at the bottom so the screen does not read as an error.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 46.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center,
        ) {
            Ico("refresh", size = 20.dp, color = Dim)
        }
    }
}
