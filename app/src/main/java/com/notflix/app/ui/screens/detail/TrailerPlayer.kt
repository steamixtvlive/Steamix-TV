package com.notflix.app.ui.screens.detail

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

private const val TAG = "NotflixTrailer"

@Composable
fun TrailerPlayer(
    videoKey: String,
    playing: Boolean,
    soundOn: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundOnState = rememberUpdatedState(soundOn)
    val playingState = rememberUpdatedState(playing)
    val onFailedState = rememberUpdatedState(onFailed)

    var webView by remember(videoKey) { mutableStateOf<WebView?>(null) }
    var isVisible by remember(videoKey) { mutableStateOf(false) }
    var ready by remember(videoKey) { mutableStateOf(false) }

    val desired = remember(videoKey) { arrayOf(false, true) }
    desired[0] = !soundOn
    desired[1] = playing

    AndroidView(
        modifier = modifier,
        factory = {
            createTrailerWebView(
                context = context,
                videoId = videoKey,
                desiredState = { desired[0] to desired[1] },
                onPlaying = {
                    ready = true
                    isVisible = true
                    webView?.let { active ->
                        sendControl(active, !soundOnState.value, playingState.value)
                    }
                },
                onFailed = {
                    isVisible = false
                    onFailedState.value()
                },
                onRestart = {
                    // Bitis/onori katmani yerine poster gorunsun (kisa sure).
                    isVisible = false
                }
            ).also { created ->
                webView = created
            }
        },
        update = { view ->
            view.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
            view.keepScreenOn = playing && isVisible
            if (playing) {
                runCatching { view.onResume() }
                runCatching { view.resumeTimers() }
            }
        },
        onRelease = { view ->
            if (webView === view) webView = null
            runCatching {
                view.removeJavascriptInterface("Android")
                view.stopLoading()
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy()
            }
        }
    )

    DisposableEffect(videoKey) {
        onDispose { webView = null }
    }

    LaunchedEffect(soundOn, playing, ready) {
        val view = webView ?: return@LaunchedEffect
        sendControl(view, !soundOnState.value, playingState.value)
    }
}

private class TrailerJsBridge(
    private val onLog: (String) -> Unit,
    private val onPlaying: () -> Unit,
    private val onFailed: () -> Unit,
    private val onRestart: () -> Unit
) {
    @JavascriptInterface
    fun log(message: String) = onLog(message)

    @JavascriptInterface
    fun notifyPlaying() = onPlaying()

    @JavascriptInterface
    fun notifyPlayerError() = onFailed()

    @JavascriptInterface
    fun notifyRestart() = onRestart()
}

private fun asset(context: Context, path: String): String =
    runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrDefault("")

private fun sendControl(view: WebView, muted: Boolean, play: Boolean) {
    runCatching {
        val context = view.context
        val script = asset(context, "trailer/control.js")
        if (script.isNotBlank()) {
            view.evaluateJavascript(script, null)
        }
        view.evaluateJavascript(
            "try{if(window.__svControl){window.__svControl($muted,$play);}}catch(e){}",
            null
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createTrailerWebView(
    context: Context,
    videoId: String,
    desiredState: () -> Pair<Boolean, Boolean>,
    onPlaying: () -> Unit,
    onFailed: () -> Unit,
    onRestart: () -> Unit
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    isClickable = false
    isFocusable = false
    setOnTouchListener { _, _ -> true }
    setBackgroundColor(android.graphics.Color.BLACK)
    visibility = View.INVISIBLE
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.userAgentString =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.let { Log.d(TAG, "console ${it.message()}") }
            return true
        }
    }
    addJavascriptInterface(
        TrailerJsBridge(
            onLog = { Log.d(TAG, it) },
            onPlaying = { post { onPlaying() } },
            onFailed = { post { onFailed() } },
            onRestart = { post { onRestart() } }
        ),
        "Android"
    )
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url
            if (url.host == "appassets.androidplatform.net" && url.path.orEmpty().startsWith("/trailer/")) {
                val name = url.path.orEmpty().removePrefix("/trailer/")
                if (name.isNotBlank() && !name.contains("..")) {
                    val mime = when {
                        name.endsWith(".html") -> "text/html"
                        name.endsWith(".js") -> "application/javascript"
                        name.endsWith(".css") -> "text/css"
                        name.endsWith(".json") -> "application/json"
                        else -> "text/plain"
                    }
                    val body = if (name == "player.html") {
                        asset(context, "trailer/player.html").replace("__VIDEO_ID__", videoId)
                    } else {
                        asset(context, "trailer/$name")
                    }
                    if (body.isNotBlank()) {
                        return WebResourceResponse(
                            mime,
                            "utf-8",
                            200,
                            "OK",
                            mapOf("Cache-Control" to "no-store"),
                            ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
                        )
                    }
                }
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
            Log.d(TAG, "page-started: $url")
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            Log.d(TAG, "page-finished: $url")
            super.onPageFinished(view, url)
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: android.webkit.WebResourceError
        ) {
            if (request.isForMainFrame) {
                Log.d(TAG, "webview error code=${error.errorCode}")
                onFailed()
            }
            super.onReceivedError(view, request, error)
        }

        override fun onRenderProcessGone(
            view: WebView,
            detail: android.webkit.RenderProcessGoneDetail?
        ): Boolean {
            Log.d(TAG, "renderer-gone didCrash=${detail?.didCrash()}")
            onFailed()
            return true
        }
    }
    loadUrl("https://appassets.androidplatform.net/trailer/player.html")
}