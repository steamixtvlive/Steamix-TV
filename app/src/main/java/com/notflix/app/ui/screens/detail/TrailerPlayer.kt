package com.notflix.app.ui.screens.detail

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.notflix.app.NotflixApplication
import com.notflix.app.R
import com.notflix.app.data.YouTubeTrailerResolver
import kotlinx.coroutines.delay

private const val TAG = "NotflixTrailer"

/**
 * Fragman oynaticisi.
 *
 * Sorun: YouTube'in WebView icindeki oynaticisi, cihaz VP9/AV1 decode
 * edemiyorsa (emulator / BlueStacks) SADECE SES acar, kadreler siyah kalir.
 *
 * Cozum:
 * 1) Once gizli bir WebView ile YouTube'un kendi oturumu kullanilarak
 *    dogrusan H.264 (itag 18/22 progressive) adresi bulunur.
 * 2) Bu adres ExoPlayer ile oynatilir -> gercek goruntu gelir.
 * 3) Cozulemezse YouTube embed WebView'e dusulur (en azindan ses+gorsel).
 * 4) O da olmazsa poster gorunur; siyah ekran olusmaz.
 *
 * Fragman bitince bastan otomatik devam eder.
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
    val app = context.applicationContext as NotflixApplication

    var stream by remember(videoKey) { mutableStateOf<YouTubeTrailerResolver.Stream?>(null) }
    var resolved by remember(videoKey) { mutableStateOf(false) }
    var usedWebResolver by remember(videoKey) { mutableStateOf(false) }

    // 1) Once uygulama ici cozumleme (hizli, IP'ye bagli degil).
    LaunchedEffect(videoKey) {
        val direct = runCatching { app.trailers.resolve(videoKey) }.getOrNull()
        if (direct != null) {
            Log.d(TAG, "resolve key=$videoKey -> ${direct.javaClass.simpleName}")
            stream = direct
            resolved = true
            return@LaunchedEffect
        }
        usedWebResolver = true
        resolved = false
    }

    // 2) Cozulemezse gizli WebView icinden YouTube oturumuyla dene.
    if (usedWebResolver && stream == null) {
        WebStreamResolver(
            videoKey = videoKey,
            onStream = { found ->
                stream = found
                resolved = true
            },
            onGaveUp = {
                resolved = true
            }
        )
    }

    val current = stream
    when {
        current != null -> TrailerExoPlayer(
            stream = current,
            playing = playing,
            soundOn = soundOn,
            onFailed = onFailed,
            modifier = modifier
        )
        resolved -> TrailerEmbedWebView(
            videoKey = videoKey,
            playing = playing,
            soundOn = soundOn,
            onFailed = onFailed,
            modifier = modifier
        )
    }
}

@Composable
private fun TrailerExoPlayer(
    stream: YouTubeTrailerResolver.Stream,
    playing: Boolean,
    soundOn: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val httpFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
    }
    val dataSourceFactory = remember(httpFactory) { DefaultDataSource.Factory(context, httpFactory) }

    val player = remember(stream) {
        val source = when (stream) {
            is YouTubeTrailerResolver.Stream.Progressive ->
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(stream.url))
            is YouTubeTrailerResolver.Stream.Adaptive -> MergingMediaSource(
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(stream.videoUrl)),
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(stream.audioUrl))
            )
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(source)
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.d(TAG, "exo error: ${error.errorCodeName}")
                onFailed()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(playing) {
        if (playing) player.play() else player.pause()
    }
    LaunchedEffect(soundOn) {
        player.volume = if (soundOn) 1f else 0f
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (android.view.LayoutInflater.from(ctx)
                .inflate(R.layout.notflix_trailer_player, null) as PlayerView).apply {
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                this.player = player
            }
        },
        update = { view -> view.player = player }
    )
}

/**
 * YouTube'un kendi oturumunu kullanan gizli WebView: watch sayfasi yuklenir,
 * sayfa icinden player API cagrilir ve dogrusan H.264 adresi alinir.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebStreamResolver(
    videoKey: String,
    onStream: (YouTubeTrailerResolver.Stream) -> Unit,
    onGaveUp: () -> Unit
) {
    val onStreamState = rememberUpdatedState(onStream)
    val onGaveUpState = rememberUpdatedState(onGaveUp)
    var finished by remember(videoKey) { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = DESKTOP_UA
                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(
                    ResolverBridge(
                        onStream = { payload ->
                            val parsed = parseStreamPayload(payload)
                            if (parsed != null) {
                                finished = true
                                onStreamState.value(parsed)
                            }
                        },
                        onError = {
                            finished = true
                            onGaveUpState.value()
                        }
                    ),
                    "NotflixResolver"
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        view.evaluateJavascript(resolverScript(videoKey), null)
                    }
                }
                loadUrl("https://www.youtube.com/watch?v=$videoKey")
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface("NotflixResolver")
            view.stopLoading()
            view.destroy()
        }
    )

    LaunchedEffect(videoKey) {
        var waited = 0
        while (waited < 20_000 && !finished) {
            delay(500)
            waited += 500
        }
        if (!finished) onGaveUpState.value()
    }
}

private fun parseStreamPayload(payload: String): YouTubeTrailerResolver.Stream? {
    return runCatching {
        val json = org.json.JSONObject(payload)
        when (json.optString("type")) {
            "p" -> YouTubeTrailerResolver.Stream.Progressive(json.getString("url"))
            "a" -> YouTubeTrailerResolver.Stream.Adaptive(
                json.getString("v"),
                json.getString("a")
            )
            else -> null
        }
    }.getOrNull()
}

private class ResolverBridge(
    private val onStream: (String) -> Unit,
    private val onError: () -> Unit
) {
    @JavascriptInterface
    fun trailerStream(payload: String) {
        onStream(payload)
    }

    @JavascriptInterface
    fun trailerResolveError() {
        onError()
    }
}

/**
 * YouTube embed WebView: dogrudan embed adresi iframe icinde, iframe_api yok.
 */
@Composable
private fun TrailerEmbedWebView(
    videoKey: String,
    playing: Boolean,
    soundOn: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val soundOnState = rememberUpdatedState(soundOn)
    val holder = remember(videoKey) { arrayOfNulls<WebView>(1) }
    var reported by remember(videoKey) { mutableStateOf(false) }

    androidx.compose.runtime.key(videoKey) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    isClickable = false
                    isLongClickable = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadsImagesAutomatically = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    settings.setSupportMultipleWindows(false)
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView,
                            request: android.webkit.WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            if (request.isForMainFrame) {
                                Log.d(TAG, "webview main frame error key=$videoKey")
                                onFailed()
                            }
                        }
                    }
                    addJavascriptInterface(
                        TrailerBridge(
                            onPlaying = {
                                post {
                                    reported = true
                                    applySound(holder[0], soundOnState.value)
                                }
                            },
                            onFailed = { code ->
                                post {
                                    Log.d(TAG, "webview failed key=$videoKey code=$code")
                                    onFailed()
                                }
                            }
                        ),
                        "NotflixBridge"
                    )
                    loadDataWithBaseURL(
                        EMBED_ORIGIN,
                        embedHtml(videoKey),
                        "text/html",
                        "UTF-8",
                        null
                    )
                    holder[0] = this
                }
            },
            onRelease = { view ->
                view.removeJavascriptInterface("NotflixBridge")
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
                holder[0] = null
            }
        )
    }

    LaunchedEffect(videoKey) {
        var waited = 0
        while (waited < 14_000) {
            delay(500)
            waited += 500
            if (reported) break
        }
        if (!reported) {
            Log.d(TAG, "webview timeout key=$videoKey")
            onFailed()
        }
    }

    LaunchedEffect(playing) {
        val func = if (playing) "playVideo" else "pauseVideo"
        holder[0]?.evaluateJavascript("try{command('$func');}catch(e){}", null)
    }
    LaunchedEffect(soundOn) {
        applySound(holder[0], soundOn)
    }
}

private fun applySound(webView: WebView?, soundOn: Boolean) {
    val func = if (soundOn) "unMute" else "mute"
    webView?.evaluateJavascript("try{command('$func');}catch(e){}", null)
}

private class TrailerBridge(
    private val onPlaying: () -> Unit,
    private val onFailed: (String) -> Unit
) {
    @JavascriptInterface
    fun trailerPlaying() {
        onPlaying()
    }

    @JavascriptInterface
    fun trailerFailed(code: String) {
        onFailed(code)
    }
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private const val EMBED_ORIGIN = "https://www.youtube.com/"

/** Watch sayfasindan dogrusan H.264 adresi arayan script. */
@SuppressLint("SetJavaScriptEnabled")
private fun resolverScript(videoKey: String): String = """
(function(){
  var vid='$videoKey';
  function send(o){ try{ NotflixResolver.trailerStream(JSON.stringify(o)); }catch(e){} }
  function fail(){ try{ NotflixResolver.trailerResolveError(); }catch(e){} }
  var tries=0;
  function attempt(){
    tries++;
    var ctx=null, key=null;
    try{
      if(window.ytcfg&&ytcfg.get){
        ctx=ytcfg.get('INNERTUBE_CONTEXT');
        key=ytcfg.get('INNERTUBE_API_KEY');
      }
    }catch(e){}
    if(!ctx){ if(tries<40){ setTimeout(attempt,300); } else { fail(); } return; }
    var body={context:ctx,videoId:vid,contentCheckOk:true,racyCheckOk:true};
    var url='/youtubei/v1/player?prettyPrint=false'+(key?('&key='+key):'');
    fetch(url,{
      method:'POST',
      headers:{'Content-Type':'application/json'},
      credentials:'include',
      body:JSON.stringify(body)
    }).then(function(r){return r.json();}).then(function(j){
      var sd=j.streamingData||{};
      var fmt=sd.formats||[];
      var prog=null;
      for(var i=0;i<fmt.length;i++){
        var f=fmt[i];
        if(f.mimeType&&f.mimeType.indexOf('video/mp4')===0&&f.url&&!f.signatureCipher){
          if(f.itag===18||f.itag===22){prog=f;break;}
          if(!prog)prog=f;
        }
      }
      if(prog){ send({type:'p',url:prog.url}); return; }
      var ad=sd.adaptiveFormats||[],v=null,a=null;
      for(var k=0;k<ad.length;k++){
        var g=ad[k];
        if(!g.url||g.signatureCipher) continue;
        if(g.mimeType&&g.mimeType.indexOf('video/mp4')===0&&g.mimeType.indexOf('avc1')>=0&&!v) v=g;
        if(g.mimeType&&g.mimeType.indexOf('audio/mp4')===0&&!a) a=g;
      }
      if(v&&a){ send({type:'a',v:v.url,a:a.url}); return; }
      fail();
    }).catch(function(){ fail(); });
  }
  attempt();
})();
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
private fun embedHtml(videoKey: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="referrer" content="strict-origin-when-cross-origin">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
html,body{margin:0;padding:0;height:100%;background:transparent;overflow:hidden;}
#yt{position:absolute;top:0;left:0;width:100%;height:100%;border:0;}
</style>
</head>
<body>
<iframe id="yt" allow="autoplay; encrypted-media; fullscreen" allowfullscreen
  src="https://www.youtube.com/embed/$videoKey?autoplay=1&mute=1&playsinline=1&controls=0&rel=0&modestbranding=1&iv_load_policy=3&enablejsapi=1&loop=1&playlist=$videoKey"></iframe>
<script>
function command(func,args){
  var f=document.getElementById('yt');
  if(!f||!f.contentWindow) return;
  try{ f.contentWindow.postMessage(JSON.stringify({event:'command',func:func,args:args||[]}),'*'); }catch(e){}
}
window.addEventListener('message',function(e){
  var d=null;
  try{ d=JSON.parse(e.data); }catch(err){ return; }
  if(!d) return;
  if(d.event==='onStateChange'&&d.info){
    var s=d.info.playerState;
    if(s===1||s===3){ try{NotflixBridge.trailerPlaying();}catch(err){} }
    else if(s===-1){ try{NotflixBridge.trailerFailed('embed-error');}catch(err){} }
  }
});
setTimeout(function(){ try{NotflixBridge.trailerFailed('embed-timeout');}catch(err){} },20000);
</script>
</body>
</html>
""".trimIndent()
