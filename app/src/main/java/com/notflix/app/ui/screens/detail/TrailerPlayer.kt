package com.notflix.app.ui.screens.detail

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
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
import com.notflix.app.data.YouTubeTrailerResolver

private const val TAG = "NotflixTrailer"

/**
 * Fragman oynaticisi.
 *
 * 1) Oncelikle dogrudan H.264 MP4 akisi cozulur ve ExoPlayer ile oynatilir.
 *    (WebView + YouTube iframe, cihaz VP9/AV1 decode edemiyorsa sesi acar
 *    ama kadreleri siyah birakir.)
 * 2) Akis cozulemezse YouTube embed WebView'e dusulur; bu yol da basarisiz
 *    olursa poster gorunur, siyah ekran olusmaz.
 *
 * Her iki yolda da fragman bitince bastan otomatik devam eder.
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

    LaunchedEffect(videoKey) {
        val found = runCatching { app.trailers.resolve(videoKey) }.getOrNull()
        Log.d(TAG, "resolve key=$videoKey -> ${found?.javaClass?.simpleName}")
        stream = found
        resolved = true
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
        resolved -> TrailerWebView(
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
            // Fragman bitince bastan otomatik devam eder.
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
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                setBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
            }
        },
        update = { view -> view.player = player }
    )
}

@Composable
private fun TrailerWebView(
    videoKey: String,
    playing: Boolean,
    soundOn: Boolean,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val soundOnState = rememberUpdatedState(soundOn)
    val holder = remember(videoKey) { arrayOfNulls<WebView>(1) }

    androidx.compose.runtime.key(videoKey) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    // Saydam arka plan: video gelmezse poster gorunur, siyah kutu olusmaz.
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setLayerType(android.view.View.LAYER_TYPE_NONE, null)
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
                                    holder[0]?.evaluateJavascript(
                                        if (soundOnState.value) {
                                            "try{if(player){player.unMute();player.setVolume(100);}}catch(e){}"
                                        } else {
                                            "try{if(player){player.mute();}}catch(e){}"
                                        },
                                        null
                                    )
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
                        "https://filmmodu.news/",
                        trailerHtml(videoKey),
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

    LaunchedEffect(playing) {
        val js = if (playing) {
            "try{if(window.player&&player.playVideo){player.playVideo();}}catch(e){}"
        } else {
            "try{if(window.player&&player.pauseVideo){player.pauseVideo();}}catch(e){}"
        }
        holder[0]?.evaluateJavascript(js, null)
    }
    LaunchedEffect(soundOn) {
        val js = if (soundOn) {
            "try{if(window.player){player.unMute();player.setVolume(100);}}catch(e){}"
        } else {
            "try{if(window.player){player.mute();}}catch(e){}"
        }
        holder[0]?.evaluateJavascript(js, null)
    }
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

@SuppressLint("SetJavaScriptEnabled")
private fun trailerHtml(videoKey: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="referrer" content="strict-origin-when-cross-origin">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden;}
#wrap{position:absolute;top:0;left:0;width:100%;height:100%;overflow:hidden;background:#000;}
#player{position:absolute;top:0;left:0;width:100%;height:100%;}
#player iframe{width:100%;height:100%;border:0;}
</style>
</head>
<body>
<div id="wrap"><div id="player"></div></div>
<script>
var player=null;
var started=false;
var retried=false;
var apiLoaded=false;
function bridgePlaying(){
  if(started) return;
  started=true;
  try{NotflixBridge.trailerPlaying();}catch(err){}
}
function fail(code){
  try{NotflixBridge.trailerFailed(String(code||'unknown'));}catch(err){}
}
function kick(){
  if(!player) return;
  try{player.mute();player.playVideo();}catch(err){}
}
function onYouTubeIframeAPIReady(){
  apiLoaded=true;
  player=new YT.Player('player',{
    videoId:'$videoKey',
    host:'https://www.youtube.com',
    playerVars:{
      autoplay:1,controls:0,modestbranding:1,rel:0,playsinline:1,fs:0,
      disablekb:1,iv_load_policy:3,showinfo:0,cc_load_policy:0,
      origin:'https://filmmodu.news',enablejsapi:1,mute:1,loop:1,
      widget_referrer:'https://filmmodu.news/'
    },
    events:{
      onReady:function(e){
        kick();
        setTimeout(kick,500);
        setTimeout(function(){
          var state=-1;
          try{state=e.target.getPlayerState();}catch(err){}
          if(started||state===1||state===3) return;
          if(!retried){
            retried=true;
            try{e.target.loadVideoById('$videoKey');}catch(err){}
            kick();
            setTimeout(function(){
              if(!started) fail('timeout');
            },5000);
          }else{
            fail('timeout');
          }
        },6000);
      },
      onStateChange:function(e){
        if(!e) return;
        if(e.data===0){
          try{e.target.seekTo(0);e.target.playVideo();}catch(err){}
          return;
        }
        if(e.data===1||e.data===3){
          try{e.target.playVideo();}catch(err){}
          if(e.data===1){
            setTimeout(function(){
              var s=-1;
              try{s=player.getPlayerState();}catch(err){}
              if(s===1||s===3) bridgePlaying();
            },450);
          }
        }
      },
      onAutoplayBlocked:function(e){
        kick();
      },
      onError:function(e){
        var code=e&&e.data;
        if((code===2||code===5)&&!retried){
          retried=true;
          setTimeout(function(){
            try{player.loadVideoById('$videoKey');}catch(err){}
            kick();
          },900);
          return;
        }
        fail(code);
      }
    }
  });
}
var tag=document.createElement('script');
tag.src='https://www.youtube.com/iframe_api';
tag.onerror=function(){fail('api-script');};
document.head.appendChild(tag);
setTimeout(function(){if(!apiLoaded) fail('api-timeout');},15000);
</script>
</body>
</html>
""".trimIndent()
}
