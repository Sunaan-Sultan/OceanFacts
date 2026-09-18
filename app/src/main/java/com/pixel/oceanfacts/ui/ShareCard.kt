package com.pixel.oceanfacts.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pixel.oceanfacts.core.DeepLink
import com.pixel.oceanfacts.core.Fact
import java.io.File

/**
 * Portrait, so the card fills a phone screen when it lands in a chat or a story. Sized in dp and
 * captured at the device's own density, so the exported PNG is roughly 1050 x 1470 on a typical
 * phone — big enough to stay sharp when a messaging app re-encodes it.
 */
private val CARD_WIDTH = 400.dp
private val CARD_HEIGHT = 560.dp

/**
 * Renders [fact] as a shareable image and hands it to the system share sheet.
 *
 * The scenes are the whole point of the app, so a shared card draws the real scene, frozen on
 * its hero frame, through the same [MiniStage] the app uses — including the water colour for
 * that fact's depth. What gets shared always looks like what the fact actually looks like.
 *
 * Composed invisibly (alpha 0) rather than offscreen: the capture needs a real layout pass, and
 * `drawWithContent` still records the layer at zero alpha. The host renders this only while a
 * share is pending, so it costs nothing the rest of the time.
 */
@Composable
fun ShareCardCapture(fact: Fact, onDone: () -> Unit) {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()

    Box(
        Modifier
            .size(CARD_WIDTH, CARD_HEIGHT)
            .alpha(0f)
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            },
    ) {
        ShareCard(fact)
    }

    LaunchedEffect(fact.id) {
        // Two frames for the scene to lay out and draw before the layer holds anything.
        withFrameNanos { }
        withFrameNanos { }
        val bitmap = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull()
        if (bitmap != null) shareBitmap(context, fact, bitmap)
        onDone()
    }
}

@Composable
private fun ShareCard(fact: Fact) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        // The scene takes whatever the text does not, rather than a fixed share of the card: a
        // two-line title must never be able to push the wordmark off the bottom.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            MiniStage(fact, active = false, paused = true, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Ink)),
            )
            Row(
                Modifier.align(Alignment.TopEnd).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(fact.accent))
                Spacer(Modifier.width(7.dp))
                Text(depthLabel(fact.depthM), style = ts(11.5f, FontWeight.SemiBold, Color.White, 0.06f))
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 4.dp)) {
            Text(fact.cat.uppercase(), style = ts(11f, FontWeight.SemiBold, fact.accent, 0.14f))
            Text(
                fact.title,
                style = ts(27f, FontWeight.Bold, Color.White, -0.02f, lineHeight = 30f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                fact.sub,
                style = ts(14f, FontWeight.Light, Color(0xFFB4C8D2), lineHeight = 20f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            fact.stats.firstOrNull()?.let { (label, value) ->
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = ts(12.5f, color = Mute), modifier = Modifier.weight(1f))
                    Text(value, style = ts(15f, FontWeight.SemiBold, Color.White))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(fact.accent))
            Spacer(Modifier.width(8.dp))
            Text("OCEAN FACTS", style = ts(11f, FontWeight.Bold, Mute, 0.28f))
        }
    }
}

/**
 * Writes the card to the cache and opens the share sheet. The caption carries the fact's own
 * deep link, so a tap from the other side lands on this fact rather than the app's home tab.
 */
private fun shareBitmap(context: Context, fact: Fact, bitmap: Bitmap) {
    val uri = runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        // One file per fact, overwritten — sharing the same fact twice must not accumulate.
        val file = File(dir, "fact-${fact.id}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return

    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(
            Intent.EXTRA_TEXT,
            "${fact.title} — ${fact.sub}\n\nOcean Facts · ${DeepLink.toFact(fact.id)}",
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "Share this fact").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}
