package com.pixel.oceanfacts.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pixel.oceanfacts.AppActions

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceWebScreen(url: String, accent: Color, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var currentUrl by remember(url) { mutableStateOf(url) }
    val loadedUrl = remember { mutableStateOf<String?>(null) }

    val host = remember(currentUrl) {
        currentUrl.toUri().host?.removePrefix("www.") ?: currentUrl
    }

    BackHandler {
        val view = webView
        if (canGoBack && view != null) view.goBack() else onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF07161F))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Ico("close", size = 18.dp, color = Color.White, sw = 2f) }
            Text(
                host,
                style = ts(13f, FontWeight.SemiBold, color = Mute),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
            )
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { AppActions.openExternal(context, currentUrl) },
                contentAlignment = Alignment.Center,
            ) { Ico("external", size = 16.dp, color = Color.White, sw = 2f) }
        }

        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (progress in 1..99) {
                Box(Modifier.fillMaxWidth(progress / 100f).height(2.dp).background(accent))
            }
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(AndroidColor.WHITE)
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mediaPlaybackRequiresUserGesture = true
                            allowFileAccess = false
                            allowContentAccess = false
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url ?: return false
                                val scheme = target.scheme?.lowercase()
                                if (scheme == "http" || scheme == "https") return false
                                AppActions.openExternal(context, target.toString())
                                return true
                            }

                            override fun doUpdateVisitedHistory(
                                view: WebView?,
                                newUrl: String?,
                                isReload: Boolean,
                            ) {
                                canGoBack = view?.canGoBack() == true
                                if (newUrl != null) currentUrl = newUrl
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                newUrl: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                failed = false
                                if (newUrl != null) currentUrl = newUrl
                            }

                            override fun onPageFinished(view: WebView?, newUrl: String?) {
                                progress = 100
                                canGoBack = view?.canGoBack() == true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) failed = true
                            }
                        }
                        loadedUrl.value = url
                        loadUrl(url)
                        webView = this
                    }
                },
                update = { view ->
                    if (loadedUrl.value != url) {
                        loadedUrl.value = url
                        failed = false
                        view.loadUrl(url)
                    }
                },
            )

            if (failed) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF07161F)).padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Could not load this page",
                        style = ts(19f, FontWeight.Bold, Color.White),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Check your connection and try again, or open it in your browser.",
                        style = ts(14f, color = Mute, lineHeight = 21f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(22.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(accent)
                                .clickable {
                                    failed = false
                                    progress = 0
                                    webView?.loadUrl(currentUrl)
                                }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                        ) { Text("Retry", style = ts(14.5f, FontWeight.SemiBold, Color.Black)) }
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable { AppActions.openExternal(context, currentUrl) }
                                .padding(horizontal = 20.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Open in browser", style = ts(14.5f, FontWeight.SemiBold, Color.White))
                            Spacer(Modifier.width(7.dp))
                            Ico("external", size = 14.dp, color = Color.White, sw = 2f)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.let {
                it.stopLoading()
                it.webChromeClient = null
                it.loadUrl("about:blank")
                it.destroy()
            }
            webView = null
        }
    }
}
