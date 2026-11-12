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

/**
 * Detay sayfasindaki fragman oynaticisi.
 *
 * YouTube, dogrudan embed sayfasi yuklenen bir WebView'a oynatici vermez
 * (152-4 / 153 hatalari). Eski Steamix projesindeki calisan cozum uygulanir:
 *
 * - WebView gercek "https://www.youtube.com/" adresine gider ama ana cerceve
 *   isteği [WebViewClient.shouldInterceptRequest] ile yerel HTML olarak
 *   servis edilir. Boylece dokumanin origin'i gercekten youtube.com olur ve
 *   embed, web sitesindeki gibi yuklenir.
 * - Oynatici 6 saniye icinde baslamazsa m.youtube.com/watch adresine yonlendirilir.
 *   Sayfa yuklenince <video> elementi tam ekrana sabitlenir, dongu acilir ve
 *   ses kapatilir; boylece kadreler dogrudan sayfa tarafindan cizilir.
 * - WebView basta GORUNMEZ; video gercekten calismaya baslayinca gorunur olur.
 *   O ana kadar poster gorunur, hicbir siyah kutu olusmaz.
 * - Ses acma/kapatma ve oynat/durdur komutlari her iki yolda da calisir.
 */
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

    // Oynaticiya gonderilecek anlik durum (watch sayfasi degisebilir).
    val desired = remember(videoKey) { arrayOf(false, true) }
    desired[0] = soundOn
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
                },
                onFailed = {
                    isVisible = false
                    onFailedState.value()
                }
            ).also { created ->
                webView = created
            }
        },
        update = { view ->
            view.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
        },
        onRelease = { view ->
            view.removeJavascriptInterface("Android")
            view.stopLoading()
            view.loadUrl("about:blank")
            view.destroy()
            webView = null
        }
    )

    DisposableEffect(videoKey) {
        onDispose {
            webView?.post { webView?.destroy() }
            webView = null
        }
    }

    // Ses / oynatma durumu degistiginde oynaticiya ilet.
    LaunchedEffect(soundOn, playing, ready) {
        val view = webView ?: return@LaunchedEffect
        sendControl(view, !soundOnState.value, playingState.value)
    }
}

private class TrailerJsBridge(
    private val onLog: (String) -> Unit,
    private val onPlaying: () -> Unit,
    private val onFailed: () -> Unit
) {
    @JavascriptInterface
    fun log(message: String) = onLog(message)

    @JavascriptInterface
    fun notifyPlaying() = onPlaying()

    @JavascriptInterface
    fun notifyPlayerError() = onFailed()
}

/** Asset'teki scriptleri okur (node ile dogrulanmis, sozdizimi hatasi yok). */
private fun asset(context: Context, path: String): String =
    runCatching {
        context.assets.open(path).bufferedReader().use { it.readText() }
    }.getOrDefault("")

private fun sendControl(view: WebView, muted: Boolean, play: Boolean) {
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

@SuppressLint("SetJavaScriptEnabled")
private fun createTrailerWebView(
    context: Context,
    videoId: String,
    desiredState: () -> Pair<Boolean, Boolean>,
    onPlaying: () -> Unit,
    onFailed: () -> Unit
): WebView = WebView(context).apply {
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    isClickable = false
    isFocusable = false
    setOnTouchListener { _, _ -> true }
    setBackgroundColor(android.graphics.Color.BLACK)
    // Video calmaya baslayana kadar gizli: arkada poster gorunur, siyah kutu olmaz.
    visibility = View.INVISIBLE
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    // WebView kullanici ajani YouTube icin kirmizı bayrak; normal Chrome UA verilir.
    settings.userAgentString =
        "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
    webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.let { Log.d(TAG, "console ${it.message()}") }
            return true
        }
    }
    addJavascriptInterface(
        TrailerJsBridge(
            onLog = { Log.d(TAG, it) },
            onPlaying = onPlaying,
            onFailed = onFailed
        ),
        "Android"
    )
    webViewClient = object : WebViewClient() {
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse? {
            val url = request.url
            if (request.isForMainFrame && url.host == "www.youtube.com" && url.path == "/") {
                val html = asset(context, "trailer/player.html").replace("__VIDEO_ID__", videoId)
                return WebResourceResponse(
                    "text/html",
                    "utf-8",
                    ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
                )
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(
            view: WebView,
            url: String?,
            favicon: android.graphics.Bitmap?
        ) {
            Log.d(TAG, "page-started: $url")
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView, url: String?) {
            Log.d(TAG, "page-finished: $url")
            super.onPageFinished(view, url)
            // Watch sayfasi yedegi: YouTube arayuzunu temizle, <video> elementini
            // tam ekrana sabitle, dongu kur ve sesi kapat.
            if (url?.contains("watch") == true) {
                val clean = asset(context, "trailer/clean.js")
                if (clean.isNotBlank()) {
                    view.evaluateJavascript(clean, null)
                }
                view.evaluateJavascript("try{if(window.__svClean){window.__svClean();}}catch(e){}", null)
                val (muted, play) = desiredState()
                view.postDelayed({ sendControl(view, muted, play) }, 1200)
                view.postDelayed({ sendControl(view, muted, play) }, 3000)
            }
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
    }
    loadUrl("https://www.youtube.com/")
}
