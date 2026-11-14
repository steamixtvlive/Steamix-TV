package com.notflix.app.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequester.Companion.Default
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.notflix.app.NotflixApplication
import com.notflix.app.MainActivity
import com.notflix.app.data.LiveChannel
import com.notflix.app.data.MediaType
import com.notflix.app.data.PlayInfo
import com.notflix.app.data.TvProgram
import com.notflix.app.data.WatchEntry
import com.notflix.app.ui.brand.NotflixWordmark
import com.notflix.app.ui.components.FilterChip
import com.notflix.app.ui.components.NotflixButton
import com.notflix.app.ui.components.VolumeHud
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.theme.NotflixColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun PlayerScreen(
    pageUrl: String,
    title: String,
    liveUrl: String?,
    liveChannels: List<LiveChannel> = emptyList(),
    liveIndex: Int = -1,
    nextEpisodeUrl: String? = null,
    nextEpisodeTitle: String? = null,
    onNextEpisode: (String, String) -> Unit = { _, _ -> },
    inPipMode: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val pipAspectFocus = remember { FocusRequester() }
    val app = context.applicationContext as NotflixApplication
    val isLive = liveUrl != null
    val isTv = LocalConfiguration.current.smallestScreenWidthDp >= 600

    // Sonraki bolum: detay ekranindan gelirse parametreden alinir; gelmezse
    // ("Devam Et" satiri, Devam Et sekmesi) dizi bolumu adresinden bulunur.
    // Boylece otomatik "sonraki bolume gecis" heryerde calisir.
    var resolvedNextUrl by remember { mutableStateOf(nextEpisodeUrl) }
    var resolvedNextTitle by remember { mutableStateOf(nextEpisodeTitle) }
    var playbackEnded by remember { mutableStateOf(false) }
    // Dizilerde bittiğinde sonraki bölüme otomatik geçiş (4 sn geri sayım).
    var nextCountdown by remember(pageUrl) { mutableStateOf<Int?>(null) }
    LaunchedEffect(pageUrl, nextEpisodeUrl) {
        if (!nextEpisodeUrl.isNullOrBlank()) {
            resolvedNextUrl = nextEpisodeUrl
            resolvedNextTitle = nextEpisodeTitle
            return@LaunchedEffect
        }
        resolvedNextUrl = null
        resolvedNextTitle = null
        val season = seasonFromUrl(pageUrl)
        val number = episodeFromUrl(pageUrl)
        if (season <= 0 || number <= 0) return@LaunchedEffect
        val seriesUrl = pageUrl.substringBefore("/sezon-").substringBefore("?")
        val next = runCatching { app.content.nextEpisode(seriesUrl, season, number) }.getOrNull()
        if (next != null) {
            resolvedNextUrl = next.url
            resolvedNextTitle = "$title · ${next.season}. Sezon ${next.number}. Bölüm"
            android.util.Log.d("NotflixNextEpisode", "sonraki bolum: ${next.url}")
            // Video bittikten sonra bilgi gelirse geri sayimi hemen baslat.
            if (playbackEnded) nextCountdown = 4
        } else {
            android.util.Log.d("NotflixNextEpisode", "sonraki bolum bulunamadi: $pageUrl")
        }
    }

    val initialChannels = remember(liveUrl) {
        if (!isLive) emptyList()
        else if (liveChannels.isNotEmpty() && liveIndex in liveChannels.indices) liveChannels
        else listOf(LiveChannel(title, liveUrl.orEmpty(), "", ""))
    }
    var channels by remember(liveUrl) { mutableStateOf(initialChannels) }
    var index by remember(liveUrl) {
        mutableIntStateOf(if (isLive && liveIndex in initialChannels.indices) liveIndex else 0)
    }
    val channel = if (isLive) channels.getOrNull(index) else null
    val displayTitle = channel?.name ?: title

    val httpFactory = remember(pageUrl, isLive) {
        DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (!isLive) {
                    setDefaultRequestProperties(
                        mapOf("Referer" to pageUrl, "Origin" to ORIGIN)
                    )
                }
            }
    }
    val dataSourceFactory = remember(httpFactory) {
        DefaultDataSource.Factory(context, httpFactory)
    }
    val trackSelector = remember { DefaultTrackSelector(context) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                // Canli yayinda odak baska yere gecse de ses kesilmemeli.
                setHandleAudioBecomingNoisy(false)
                setWakeMode(C.WAKE_MODE_NETWORK)
                playWhenReady = true
            }
    }

    var resolving by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var resolveTick by remember { mutableIntStateOf(0) }
    var retries by remember { mutableIntStateOf(0) }
    var attempt by remember(index) { mutableIntStateOf(0) }
    var resumeOverride by remember { mutableLongStateOf(0L) }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var controlsVisible by remember { mutableStateOf(true) }
    // Kumandada her tus hareketi bu sayaci artirir (kontrollerin gizlenme
    // sayacini sifirlar).
    var controlsTick by remember { mutableIntStateOf(0) }
    var qualityOpen by remember { mutableStateOf(false) }
    var panelOpen by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(if (isLive) app.settings.playerAspect else "fit") }
    var aspectNotice by remember { mutableStateOf<String?>(null) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    var volume by remember { mutableFloatStateOf(readStreamVolume(audioManager)) }
    var muted by remember { mutableStateOf(volume <= 0.001f) }
    var qualityHeight by remember { mutableStateOf<Int?>(null) }
    var videoHeights by remember { mutableStateOf<List<Int>>(emptyList()) }
    var videoInfo by remember { mutableStateOf<String?>(null) }
    // Canli yayinda kontrol barinda gosterilecek kalite etiketi (orn. "1080p").
    var liveQualityLabel by remember { mutableStateOf<String?>(null) }
    // Oynatma hizi (film/dizi).
    var playbackSpeed by remember { mutableStateOf(1f) }
    var speedNotice by remember { mutableStateOf<String?>(null) }
    // 10 saniye ileri / geri.
    val seekStepSeconds = 10
    var hintText by remember { mutableStateOf<String?>(null) }
    // Kullaniciya gosterilen kisa ipucu (orn. "Kontrolleri acmak icin OK tusuna basiniz").
    fun showHint(text: String) {
        hintText = text
    }
    LaunchedEffect(hintText) {
        if (hintText != null) {
            delay(2_600)
            hintText = null
        }
    }
    var rootBoxFocused by remember { mutableStateOf(false) }
    var posterUrl by remember { mutableStateOf("") }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var startedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var channelNotice by remember { mutableStateOf<String?>(null) }
    var channelNoticeLogo by remember { mutableStateOf("") }
    var channelSwitchPending by remember { mutableStateOf(false) }
    var channelNoticeTick by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current

    val focusRequester = remember { FocusRequester() }
    val controlsFocus = remember { FocusRequester() }
    val seekbarFocus = remember { FocusRequester() }
    val previousControlFocus = remember { FocusRequester() }
    val nextControlFocus = remember { FocusRequester() }
    val rewindControlFocus = remember { FocusRequester() }
    val forwardControlFocus = remember { FocusRequester() }
    val volumeControlFocus = remember { FocusRequester() }
    val speedControlFocus = remember { FocusRequester() }
    val qualityControlFocus = remember { FocusRequester() }
    val aspectControlFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val view = LocalView.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, pageUrl) {
        val host = activity as? MainActivity
        host?.setPlayerActive(true)
        onDispose { host?.setPlayerActive(false) }
    }

    BackHandler {
        when {
            qualityOpen -> qualityOpen = false
            panelOpen -> panelOpen = false
            else -> onBack()
        }
    }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        val previousOrientation = activity?.requestedOrientation
        if (!isTv) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            view.keepScreenOn = false
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
        }
    }

    fun saveProgress() {
        if (isLive || !app.settings.resumeEnabled) return
        val current = player.currentPosition
        val total = player.duration
        if (current <= 5_000) return
        val finished = total != C.TIME_UNSET && total > 0 && current > total - 20_000
        app.settings.savePosition(pageUrl, if (finished) 0L else current)
        app.settings.recordWatch(
            WatchEntry(
                url = pageUrl,
                title = title.substringBefore(" · "),
                poster = posterUrl,
                type = if (pageUrl.contains("/film/")) MediaType.MOVIE else MediaType.SERIES,
                position = if (finished) total else current,
                duration = if (total > 0 && total != C.TIME_UNSET) total else 0L,
                season = seasonFromUrl(pageUrl),
                episode = episodeFromUrl(pageUrl),
                completed = finished,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (isLive) {
                    val remaining = 2 + (channels.getOrNull(index)?.alternates?.size ?: 0)
                    if (attempt < remaining) {
                        attempt++
                        resolving = true
                        errorText = null
                    } else {
                        errorText = liveError()
                    }
                } else {
                    val position = player.currentPosition.coerceAtLeast(0L)
                    resumeOverride = position
                    if (retries < 2) {
                        retries++
                        resolving = true
                        errorText = null
                        resolveTick++
                    } else {
                        errorText = friendlyError(error)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    errorText = null
                    channelSwitchPending = false
                }
                if (playbackState == Player.STATE_ENDED) {
                    controlsVisible = true
                    playbackEnded = true
                    // Sonraki bolum varsa 4 sn sonra otomatik gec.
                    if (!isLive && resolvedNextUrl != null && nextCountdown == null) {
                        nextCountdown = 4
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val heights = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { group ->
                        (0 until group.length).mapNotNull { trackIndex ->
                            group.getTrackFormat(trackIndex).height.takeIf { it > 0 }
                        }
                    }
                    .distinct()
                    .sortedDescending()
                if (heights != videoHeights) videoHeights = heights
                val best = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .flatMap { group ->
                        (0 until group.length).mapNotNull { trackIndex ->
                            group.getTrackFormat(trackIndex).takeIf { it.height > 0 }
                        }
                    }
                    .maxByOrNull { it.height }
                videoInfo = best?.let { format ->
                    val fps = format.frameRate.takeIf { it > 0f }?.let { Math.round(it) }
                    buildString {
                        append("${format.width}×${format.height}")
                        if (fps != null && fps > 0) append(" · $fps FPS")
                    }
                }
                // Kontrol barinda gosterilecek kalite etiketi (orn. "1080p").
                liveQualityLabel = best?.let { heightLabel(it.height) }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            saveProgress()
            player.release()
        }
    }

    LaunchedEffect(player) {
        player.volume = 1f
    }

    LaunchedEffect(player, pageUrl, liveUrl, index, resolveTick, attempt) {
        resolving = true
        errorText = null
        val info: PlayInfo? = if (isLive) {
            val current = channels.getOrNull(index)
            val stream = when {
                attempt >= 2 -> {
                    val alt = current?.alternates?.getOrNull(attempt - 2)
                    when {
                        alt == null -> null
                        alt.contains("/live/play/") -> app.live.resolveStream(alt)
                        else -> alt
                    }
                }
                else -> current?.let { app.live.resolveStream(it.url) }
                    ?: current?.stream?.takeIf { it.startsWith("http") }
                    ?: liveUrl
            }
            stream?.takeIf { it.startsWith("http") }?.let {
                PlayInfo(streamUrl = it, title = displayTitle, poster = "", referer = "")
            }
        } else {
            var resolved: PlayInfo? = null
            for (resolveTry in 0..1) {
                resolved = try {
                    withTimeoutOrNull(20_000) { app.content.resolve(pageUrl) }
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    null
                }
                if (resolved != null && resolved.streamUrl.isNotBlank()) break
                if (resolveTry == 0) delay(700L)
            }
            resolved
        }
        if (info == null || info.streamUrl.isBlank()) {
            errorText = if (isLive) liveError() else
                "Yayın bağlantısı alınamadı. İnternet bağlantınızı kontrol edip tekrar deneyin."
            resolving = false
            return@LaunchedEffect
        }
        if (!isLive && info.poster.isNotBlank()) posterUrl = info.poster
        val startAt = when {
            isLive -> 0L
            resumeOverride > 0L -> resumeOverride
            app.settings.resumeEnabled -> app.settings.savedPosition(pageUrl)
            else -> 0L
        }
        player.stop()
        val mediaItem = MediaItem.fromUri(info.streamUrl)
        if (looksLikeHls(info.streamUrl, isLive)) {
            player.setMediaSource(
                HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(mediaItem)
            )
        } else {
            player.setMediaSource(
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            )
        }
        if (startAt > 0L) player.seekTo(startAt)
        player.prepare()
        player.playWhenReady = true
        val params = trackSelector.buildUponParameters()
        if (isLive) {
            // Cekmeyen kanal yerine konan kaynak en fazla 720p olarak sunulur.
            val isFallback = channels.getOrNull(index)?.fallback == true
            val cap = qualityHeight ?: if (isFallback) 720 else 2160
            params.setMaxVideoSize(cap, cap)
        } else {
            // Film/dizide kalite otomatiktir ama kaynagin sundugu en iyi
            // cozunurluk secilir (720p'ye sabitlenmemeli).
            params.clearVideoSizeConstraints()
            params.setMaxVideoSize(2160, 2160)
        }
        trackSelector.setParameters(params)
        startedAt = System.currentTimeMillis()
        resolving = false
    }

    LaunchedEffect(player) {
        var lastSave = 0L
        while (isActive) {
            val now = System.currentTimeMillis()
            // Gercekten oynayan video cozunurlugu (720p/1080p gibi).
            player.videoFormat?.height?.takeIf { it > 0 }?.let { h ->
                val label = heightLabel(h)
                if (label != liveQualityLabel) liveQualityLabel = label
            }
            if (isLive) {
                clock = now
                isPlaying = player.isPlaying
                volume = readStreamVolume(audioManager)
                muted = volume <= 0.001f
                delay(1_000)
                continue
            }
            clock = now
            if (!dragging) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                val duration = player.duration
                durationMs = if (duration == C.TIME_UNSET || duration < 0) 0L else duration
                isPlaying = player.isPlaying
                volume = readStreamVolume(audioManager)
                muted = volume <= 0.001f
                if (now - lastSave > 5_000) {
                    lastSave = now
                    saveProgress()
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(nextCountdown) {
        val current = nextCountdown ?: return@LaunchedEffect
        if (current <= 0) {
            nextCountdown = null
            val url = resolvedNextUrl
            val label = resolvedNextTitle
            if (!url.isNullOrBlank()) {
                onNextEpisode(url, label ?: title)
            }
            return@LaunchedEffect
        }
        delay(1_000)
        nextCountdown = current - 1
    }

    // Kontroller bir süre kullanılmazsa gizlenir (tam ekran modu).
    // Canlı yayında oynatma durumu beklenmez; kanal değişimi de tam ekranda
    // çalışsın diye canlıda da otomatik gizlenir.
    // controlsTick: kumandada her hareket sayacı sıfırlar; böylece menü
    // gezinirken kaybolmaz, sadece hareketsizlikte kapanır.
    LaunchedEffect(controlsVisible, isPlaying, dragging, qualityOpen, panelOpen, isLive, controlsTick) {
        if (controlsVisible && (isPlaying || isLive) && !dragging && !qualityOpen && !panelOpen) {
            delay(4_000)
            controlsVisible = false
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        delay(350)
        runCatching { focusRequester.requestFocus() }
    }

    // Kontroller gizlendiginde odak ana kutuda kalmali; boylece kumandada
    // her tus (ok/ok tusu) tekrar calisir ve kontroller geri gelir.
    // Panel/kalite acikken odagi onlardan almamaliyiz.
    LaunchedEffect(controlsVisible, panelOpen, qualityOpen) {
        if (!controlsVisible && !panelOpen && !qualityOpen) {
            while (isActive) {
                delay(250)
                runCatching { focusRequester.requestFocus() }
            }
        }
    }

    // Kontroller gorunurken odak kontrol dugmelerine gider; boylece kumanda
    // ile oynat/ses/kalite/en-boy arasinda gezinilebilir.
    LaunchedEffect(controlsVisible, panelOpen, qualityOpen) {
        if (!controlsVisible || panelOpen || qualityOpen) return@LaunchedEffect
        delay(120)
        runCatching { controlsFocus.requestFocus() }
    }

    // Film/dizide oynat dugmesi yalnizca tampon bitince olusuyor; ilk acilista
    // odak kontrollere ulasana kadar birkac kez denenir. Canli TV'yi etkilemez.
    LaunchedEffect(controlsVisible, panelOpen, qualityOpen, buffering, isLive) {
        if (isLive || !controlsVisible || panelOpen || qualityOpen) return@LaunchedEffect
        repeat(12) {
            if (runCatching { controlsFocus.requestFocus() }.isSuccess) {
                return@LaunchedEffect
            }
            delay(150)
        }
    }

    // Panel acildiginda odak kanal listesine gider ve panel kapaliyken
    // kontroller gizli kalir (tam ekran modu korunur).
    LaunchedEffect(panelOpen) {
        if (panelOpen) {
            controlsVisible = false
            delay(90)
            // Odak fare ile bir dugmede kalmis olabilir; liste basina zorla.
            runCatching { panelFocus.requestFocus() }
        }
    }

    LaunchedEffect(qualityOpen) {
        if (qualityOpen) {
            controlsVisible = false
            delay(120)
            // Odak diger bir dugmede kalmis olabilir; modalin ilk ogesine al.
            runCatching { focusManager.clearFocus() }
        }
    }

    LaunchedEffect(index, isLive) {
        if (!isLive) return@LaunchedEffect
        val current = channels.getOrNull(index)
        channelNotice = current?.name ?: title
        channelNoticeLogo = current?.logo.orEmpty()
        channelSwitchPending = true
        channelNoticeTick++
    }

    // Keep the channel name on screen until the new stream is actually ready,
    // then fade it out. This is what TV boxes show while zapping.
    LaunchedEffect(channelNoticeTick) {
        if (!isLive || channelNotice == null) return@LaunchedEffect
        delay(650)
        var waited = 0
        while (waited < 15_000 && channelSwitchPending && errorText == null) {
            delay(200)
            waited += 200
        }
        delay(1_600)
        channelNotice = null
    }

    fun togglePlay() {
        if (!isLive && player.playbackState == Player.STATE_ENDED) {
            // Bitti: once otomatik gecis sayaci varsa onu durdur.
            if (resolvedNextUrl != null && nextCountdown != null) {
                nextCountdown = null
                return
            }
            player.seekTo(0)
            player.play()
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(delta: Long) {
        if (isLive) return
        val target = (player.currentPosition + delta).coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
        positionMs = target
    }


    fun seekWithStep(direction: Int) {
        seekBy(seekStepSeconds * 1_000L * direction)
        hintText = "${if (direction < 0) "−" else "+"}${seekStepSeconds} sn"
    }

    fun zap(delta: Int) {
        if (!isLive || channels.size < 2) return
        index = (index + delta + channels.size) % channels.size
        // Kanal değişiminde kontroller gizli kalsın; kanal adı üstte görünsün.
        controlsVisible = false
        runCatching { focusRequester.requestFocus() }
    }

    fun changeVolume(delta: Float) {
        val manager = audioManager ?: return
        val direction = if (delta > 0f) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        val steps = kotlin.math.abs((delta * 10).roundToInt()).coerceAtLeast(1)
        repeat(steps) { manager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
        volume = readStreamVolume(manager)
        muted = volume <= 0.001f
        VolumeHud.show(volume)
    }

    fun setDeviceVolume(fraction: Float) {
        writeStreamVolume(audioManager, fraction)
        volume = fraction.coerceIn(0f, 1f)
        muted = volume <= 0.001f
        VolumeHud.show(volume)
    }

    fun toggleMute() {
        if (volume <= 0.001f) setDeviceVolume(0.6f) else setDeviceVolume(0f)
    }

    fun openChannelPanel() {
        controlsVisible = false
        panelOpen = true
    }

    // ---------------------------------------------------------------
    // Kumanda / klavye tuşları (odaktan bağımsız)
    // ---------------------------------------------------------------
    // Activity seviyesinde yakalanır: odak kaybolsa, kontroller gizli
    // olsa da ok tuşları, OK tuşu ve kanal tuşları her zaman çalışır.
    DisposableEffect(isLive, channels.size, panelOpen, qualityOpen, controlsVisible) {
        val handler: (Int) -> Boolean = { keyCode ->
            when {
                panelOpen || qualityOpen -> when (keyCode) {
                    android.view.KeyEvent.KEYCODE_BACK,
                    android.view.KeyEvent.KEYCODE_ESCAPE -> {
                        panelOpen = false
                        qualityOpen = false
                        true
                    }
                    // Panel acikken kumandanin ozel kanal tuslari yine de
                    // kanal degistirir (menude gezinmeyi engellemez).
                    android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (isLive && channels.size > 1) {
                            zap(1)
                            true
                        } else {
                            false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (isLive && channels.size > 1) {
                            zap(-1)
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
                else -> when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                        // Kontroller acikken ok tuslari kontrollerde gezinir;
                        // tam ekranda (kontroller gizli) kanal degistirir.
                        if (isLive && channels.size > 1 && !controlsVisible) {
                            zap(1)
                            true
                        } else {
                            false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (isLive && channels.size > 1 && !controlsVisible) {
                            zap(-1)
                            true
                        } else {
                            false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (isLive && channels.size > 1) {
                            zap(1)
                            true
                        } else {
                            false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (isLive && channels.size > 1) {
                            zap(-1)
                            true
                        } else {
                            false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!controlsVisible && isLive && channels.size > 1) {
                            openChannelPanel()
                            true
                        } else {
                            false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER,
                    android.view.KeyEvent.KEYCODE_SPACE -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            true
                        } else {
                            false
                        }
                    }
                    // Medya tuslari: hizli sarma / geri sarma (kumanda ve klavye)
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        togglePlay()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                        controlsVisible = true
                        true
                    }
                    else -> false
                }
            }
        }
        PlayerKeyBus.register(handler)
        onDispose { PlayerKeyBus.unregister(handler) }
    }

    fun applyQuality(height: Int?) {
        qualityHeight = height
        val target = height ?: if (isLive) 2160 else null
        val params = trackSelector.buildUponParameters()
        if (target == null) {
            params.clearVideoSizeConstraints()
        } else {
            params.setMaxVideoSize(target, target)
        }
        trackSelector.setParameters(params)
    }

    fun applyAspect(value: String) {
        aspect = value
        app.settings.playerAspect = value
        playerView?.resizeMode = resizeModeFor(value)
        aspectNotice = when (value) {
            "fill" -> "Doldur"
            "zoom" -> "Kırp"
            else -> "Sığdır"
        }
    }

    /**
     * En-boy oranı düğmesi: modal açmadan sıradaki orana geçer
     * (Sığdır → Doldur → Kırp → Sığdır).
     */
    fun cycleAspect() {
        val next = when (aspect) {
            "fit" -> "fill"
            "fill" -> "zoom"
            else -> "fit"
        }
        applyAspect(next)
    }

    LaunchedEffect(playerView, aspect) {
        playerView?.resizeMode = resizeModeFor(aspect)
    }

    LaunchedEffect(aspectNotice) {
        if (aspectNotice != null) {
            delay(1_400)
            aspectNotice = null
        }
    }

    fun manualRetry() {
        retries = 0
        attempt = 0
        resumeOverride = if (isLive) 0L else player.currentPosition.coerceAtLeast(0L)
        errorText = null
        resolveTick++
    }

    val nowProgram = remember(channel, clock) {
        channel?.programs?.firstOrNull { clock in it.startMs until it.endMs }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            // Kutu kendisi focusable olmalı; aksi halde kontroller gizlenince
            // odak tamamen kayboluyor ve kumanda tuşları çalışmıyordu.
            .focusable()
            .onFocusChanged { rootBoxFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Her kumanda hareketi otomatik gizlenme sayacini sifirlar.
                controlsTick++
                if (errorText != null) return@onPreviewKeyEvent false
                // Panel acikken kumanda panelin icinde gezinir; GERI/ESC kapatir.
                if (panelOpen || qualityOpen) {
                    val native = event.nativeKeyEvent
                    if (native.keyCode == android.view.KeyEvent.KEYCODE_BACK ||
                        native.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE
                    ) {
                        panelOpen = false
                        qualityOpen = false
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (panelOpen || qualityOpen) return@onKeyEvent false
                if (errorText != null) return@onKeyEvent false
                if (!isLive && controlsVisible) {
                    when (event.key) {
                        Key.DirectionRight -> {
                            val moved = focusManager.moveFocus(FocusDirection.Right)
                            if (!moved) speedControlFocus.requestFocus()
                            return@onKeyEvent true
                        }
                        Key.DirectionLeft -> {
                            val moved = focusManager.moveFocus(FocusDirection.Left)
                            if (!moved) controlsFocus.requestFocus()
                            return@onKeyEvent true
                        }
                        Key.DirectionDown -> {
                            val moved = focusManager.moveFocus(FocusDirection.Down)
                            if (!moved) aspectControlFocus.requestFocus()
                            return@onKeyEvent true
                        }
                        Key.DirectionUp -> {
                            val moved = focusManager.moveFocus(FocusDirection.Up)
                            if (!moved) controlsFocus.requestFocus()
                            return@onKeyEvent true
                        }
                        Key.DirectionCenter, Key.Enter -> return@onKeyEvent false
                        else -> Unit
                    }
                }
                if (!isLive) {
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            seekWithStep(1)
                            return@onKeyEvent true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            seekWithStep(-1)
                            return@onKeyEvent true
                        }
                    }
                }
                // Ozel kumanda tuslariyla kanal gecisi (kanal yukarı/asagi,
                // medya next/previous) — kumandasinda ozel tus varsa calisir.
                val native = event.nativeKeyEvent
                if (isLive && channels.size > 1) {
                    val handledChannelKey = when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                            zap(1)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                            zap(-1)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            zap(1)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            zap(-1)
                            true
                        }
                        else -> false
                    }
                    if (handledChannelKey == true) return@onKeyEvent true
                }
                // Film/dizide kontroller acikken odak ana kutudaysa ok tuslari
                // kontrol barindaki dugmelere gecsin (yutulmasin).
                if (!isLive && controlsVisible && rootBoxFocused) {
                    when (event.key) {
                        Key.DirectionRight -> {
                            focusManager.moveFocus(FocusDirection.Right)
                            return@onKeyEvent true
                        }
                        Key.DirectionLeft -> {
                            focusManager.moveFocus(FocusDirection.Left)
                            return@onKeyEvent true
                        }
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            if (rootBoxFocused) {
                                controlsFocus.requestFocus()
                                togglePlay()
                            } else togglePlay()
                            return@onKeyEvent true
                        }
                        else -> Unit
                    }
                }
                when (event.key) {
                    // Tam ekran modu (kontroller gizliyken):
                    //  - Canli TV'de yukarı/asagi kanal degistirir.
                    //  - Film/dizide yön tuslari kontrolleri acar.
                    Key.DirectionUp -> {
                        if (!controlsVisible) {
                            if (isLive && channels.size > 1) {
                                zap(1)
                            } else {
                                controlsVisible = true
                                runCatching { focusRequester.requestFocus() }
                                runCatching { focusManager.moveFocus(FocusDirection.Down) }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionDown -> {
                        if (!controlsVisible) {
                            if (isLive && channels.size > 1) {
                                zap(-1)
                            } else {
                                controlsVisible = true
                                runCatching { focusRequester.requestFocus() }
                                runCatching { focusManager.moveFocus(FocusDirection.Down) }
                            }
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionRight -> {
                        if (controlsVisible && !isLive && rootBoxFocused) {
                            runCatching { controlsFocus.requestFocus() }
                            true
                        } else if (!controlsVisible && isLive && channels.size > 1) {
                            openChannelPanel()
                            true
                        } else false
                    }
                    // Canli TV'de kontroller OK (merkez) tusu ile acilir;
                    // film/dizide OK oynat/durdur yapar.
                    Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                        if (!controlsVisible) {
                            if (isLive) {
                                controlsVisible = true
                                showHint("Kontrolleri açmak için OK tuşuna basınız")
                                runCatching { focusRequester.requestFocus() }
                                runCatching { focusManager.moveFocus(FocusDirection.Down) }
                            } else {
                                togglePlay()
                            }
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> {
                        if (!controlsVisible) {
                            controlsVisible = true
                            runCatching { focusRequester.requestFocus() }
                            runCatching { focusManager.moveFocus(FocusDirection.Down) }
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = resizeModeFor(aspect)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    playerView = this
                }
            },
            update = { view -> view.player = player },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(panelOpen) {
                    detectTapGestures { if (!panelOpen) controlsVisible = !controlsVisible }
                }
        )

        if (inPipMode) {
            Box(
                modifier = Modifier.fillMaxSize().onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionCenter) {
                        runCatching { pipAspectFocus.requestFocus() }.isSuccess
                    } else false
                }
            )
            ControlCircle(
                icon = Icons.Filled.AspectRatio,
                contentDescription = "Ekran oranını değiştir",
                size = 34.dp,
                filled = false,
                onClick = { cycleAspect() },
                modifier = Modifier
                    .focusRequester(pipAspectFocus)
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
            )
        }

        val showError = errorText != null
        val showLoading = resolving && !showError

        if (buffering && !showLoading && !showError && !controlsVisible) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.85f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
            )
        }

        AnimatedVisibility(
            visible = !inPipMode && (controlsVisible || showLoading) && !showError,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.30f))
                )
                PlayerTopBar(
                    title = displayTitle,
                    isLive = isLive,
                    onBack = onBack,
                    onPanel = if (isLive) {
                        { openChannelPanel() }
                    } else null
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = if (isTv) 32.dp else 12.dp,
                            end = if (isTv) 32.dp else 12.dp,
                            bottom = if (isTv) 24.dp else 10.dp
                        )
                        .clip(RoundedCornerShape(if (isTv) 20.dp else 16.dp))
                        .background(Color.Black.copy(alpha = 0.64f))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(if (isTv) 20.dp else 16.dp)
                        )
                        .padding(
                            horizontal = if (isTv) 22.dp else 14.dp,
                            vertical = if (isTv) 14.dp else 10.dp
                        )
                ) {
                    if (isLive) {
                        LiveProgramBar(
                            channel = channel,
                            program = nowProgram,
                            now = clock,
                            elapsedMs = (clock - startedAt).coerceAtLeast(0L)
                        )
                        Spacer(modifier = Modifier.height(if (isTv) 12.dp else 8.dp))
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusGroup(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(if (dragging) dragValue.toLong() else positionMs),
                                color = Color.White,
                                fontSize = if (isTv) 12.sp else 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = if (dragging) dragValue
                                else positionMs.toFloat().coerceIn(
                                    0f,
                                    durationMs.toFloat().coerceAtLeast(1f)
                                ),
                                onValueChange = {
                                    dragging = true
                                    dragValue = it
                                },
                                onValueChangeFinished = {
                                    player.seekTo(dragValue.toLong())
                                    positionMs = dragValue.toLong()
                                    dragging = false
                                },
                                valueRange = 0f..(durationMs.toFloat().coerceAtLeast(1f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = if (isTv) 14.dp else 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = NotflixColors.Accent,
                                    activeTrackColor = NotflixColors.Accent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                                )
                            )
                            Text(
                                text = formatTime(durationMs),
                                color = NotflixColors.TextSecondary,
                                fontSize = if (isTv) 12.sp else 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(if (isTv) 8.dp else 4.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().focusGroup(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (buffering) {
                            Box(
                                modifier = Modifier.size(if (isTv) 44.dp else 38.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(if (isTv) 30.dp else 26.dp)
                                )
                            }
                        } else {
                            ControlCircle(
                                icon = if (isPlaying) Icons.Filled.Pause
                                else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Duraklat" else "Oynat",
                                size = if (isTv) 46.dp else 40.dp,
                                filled = true,
                                modifier = Modifier
                                    .focusRequester(controlsFocus)
                                    .focusProperties {
                                        left = if (isLive) previousControlFocus else Default
                                        right = if (isLive) nextControlFocus else rewindControlFocus
                                        down = if (isLive) Default else rewindControlFocus
                                    },
                                onClick = { togglePlay() }
                            )
                        }
                        Spacer(modifier = Modifier.width(if (isTv) 12.dp else 8.dp))
                        if (isLive) {
                            if (channels.size > 1) {
                                ControlCircle(
                                    icon = Icons.Filled.SkipPrevious,
                                    contentDescription = "Önceki kanal",
                                    size = if (isTv) 40.dp else 34.dp,
                                    filled = false,
                                    modifier = Modifier.focusRequester(previousControlFocus),
                                    onClick = { zap(-1) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ControlCircle(
                                    icon = Icons.Filled.SkipNext,
                                    contentDescription = "Sonraki kanal",
                                    size = if (isTv) 40.dp else 34.dp,
                                    filled = false,
                                    modifier = Modifier.focusRequester(nextControlFocus),
                                    onClick = { zap(1) }
                                )
                            }
                        } else {
                            SeekCircle(
                                label = "−$seekStepSeconds",
                                size = if (isTv) 44.dp else 38.dp,
                                modifier = Modifier
                                    .focusRequester(rewindControlFocus)
                                    .focusProperties { left = controlsFocus; right = forwardControlFocus },
                                onClick = { seekWithStep(-1) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            SeekCircle(
                                label = "+$seekStepSeconds",
                                size = if (isTv) 44.dp else 38.dp,
                                modifier = Modifier
                                    .focusRequester(forwardControlFocus)
                                    .focusProperties {
                                        left = rewindControlFocus
                                        right = volumeControlFocus
                                    },
                                onClick = { seekWithStep(1) }
                            )
                        }
                        Spacer(modifier = Modifier.width(if (isTv) 18.dp else 12.dp))
                        Row(
                            modifier = Modifier.focusGroup().focusProperties {
                                left = if (isLive) nextControlFocus else forwardControlFocus
                                right = if (isLive) aspectControlFocus else speedControlFocus
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ControlCircle(
                                icon = if (muted || volume <= 0.01f) Icons.Filled.VolumeOff
                                else Icons.Filled.VolumeUp,
                                contentDescription = if (muted) "Sesi aç" else "Sesi kapat",
                                size = if (isTv) 38.dp else 34.dp,
                                filled = false,
                                modifier = Modifier
                                    .focusRequester(volumeControlFocus)
                                    .focusProperties {
                                        left = if (isLive) nextControlFocus else forwardControlFocus
                                        right = speedControlFocus
                                    },
                                onClick = { toggleMute() }
                            )
                            Slider(
                                value = if (muted) 0f else volume,
                                onValueChange = { setDeviceVolume(it) },
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .width(if (isTv) 170.dp else 92.dp)
                                    .padding(start = 6.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.26f)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (!isLive) {
                            // Oynatma hizi (1x / 1.25x / 1.5x / 2x / 0.75x)
                            ControlCircle(
                                icon = Icons.Filled.Speed,
                                contentDescription = "Oynatma hızı",
                                size = if (isTv) 38.dp else 34.dp,
                                filled = false,
                                modifier = Modifier
                                    .focusRequester(speedControlFocus)
                                    .focusProperties {
                                        left = volumeControlFocus
                                        right = qualityControlFocus
                                    },
                                onClick = {
                                    val next = when (playbackSpeed) {
                                        1f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2f
                                        2f -> 0.75f
                                        else -> 1f
                                    }
                                    playbackSpeed = next
                                    player.setPlaybackSpeed(next)
                                    speedNotice = "${if (next == next.toInt().toFloat()) next.toInt() else next}x"
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = speedNotice ?: "${if (playbackSpeed == playbackSpeed.toInt().toFloat()) playbackSpeed.toInt() else playbackSpeed}x",
                                color = NotflixColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            // Film/dizide kalite otomatiktir; degistirilemez.
                            Text(
                                text = liveQualityLabel ?: "Otomatik",
                                color = NotflixColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            ControlCircle(
                                icon = Icons.Filled.Settings,
                                contentDescription = "Kalite (otomatik)",
                                size = if (isTv) 38.dp else 34.dp,
                                filled = false,
                                modifier = Modifier
                                    .focusRequester(qualityControlFocus)
                                    .focusProperties {
                                        left = speedControlFocus
                                        right = aspectControlFocus
                                        up = controlsFocus
                                        down = aspectControlFocus
                                    },
                                onClick = { qualityOpen = true }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (isLive) {
                            // Yayin kalitesi kontrol barinin saginda gorunur.
                            liveQualityLabel?.let { label ->
                                Text(
                                    text = label,
                                    color = NotflixColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            ControlCircle(
                                icon = Icons.Filled.LiveTv,
                                contentDescription = "Kanal listesi",
                                size = if (isTv) 38.dp else 34.dp,
                                filled = false,
                                onClick = { openChannelPanel() }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        ControlCircle(
                            icon = Icons.Filled.AspectRatio,
                            contentDescription = "En-boy oranı",
                            size = if (isTv) 38.dp else 34.dp,
                            filled = false,
                            modifier = Modifier
                                .focusRequester(aspectControlFocus)
                                .focusProperties {
                                    left = if (isLive) volumeControlFocus else qualityControlFocus
                                    right = Default
                                    up = if (isLive) controlsFocus else speedControlFocus
                                },
                            onClick = { cycleAspect() }
                        )
                    }
                }
            }
        }

        // Canli yayinda cozunurluk bilgisi kontrol barinin saginda gosterilir.

        // Sonraki bolum geri sayimi (dizilerde otomatik gecis).
        AnimatedVisibility(
            visible = nextCountdown != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.80f))
                    .border(1.dp, NotflixColors.Accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 18.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Sonraki bölüm",
                        color = NotflixColors.AccentSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = resolvedNextTitle ?: "",
                        color = Color.White,
                        fontSize = if (isTv) 15.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${nextCountdown ?: 0} sn sonra geçiyor…",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    NotflixButton(
                        text = "Şimdi izle",
                        onClick = {
                            nextCountdown = null
                            val url = resolvedNextUrl
                            val label = resolvedNextTitle
                            if (!url.isNullOrBlank()) onNextEpisode(url, label ?: title)
                        }
                    )
                }
            }
        }

        // Kullanici ipucu (animasyonlu).
        AnimatedVisibility(
            visible = !inPipMode && hintText != null,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isTv) 120.dp else 96.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .border(1.dp, NotflixColors.Accent.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = hintText.orEmpty(),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (!inPipMode) aspectNotice?.let { label ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isTv) 76.dp else 60.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Ekran: $label",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        AnimatedVisibility(
            visible = !inPipMode && isLive && channelNotice != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                    .padding(horizontal = if (isTv) 34.dp else 24.dp, vertical = if (isTv) 18.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CANLI",
                    color = NotflixColors.Accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = channelNotice.orEmpty(),
                    color = Color.White,
                    fontSize = if (isTv) 30.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = panelOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ChannelPanel(
                channels = channels,
                currentIndex = index,
                focusRequester = panelFocus,
                onSelect = { selected ->
                    index = selected
                    // Kanal secildiginde panel acik kaliyor; kapatmak icin
                    // kumandada GERI/ESC kullanilir.
                },
                onClose = { panelOpen = false }
            )
        }

        if (qualityOpen) {
            QualityPanel(
                heights = videoHeights,
                selected = qualityHeight,
                autoOnly = !isLive,
                onSelect = { applyQuality(it) },
                onClose = { qualityOpen = false }
            )
        }

        if (showError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .width(if (isTv) 460.dp else 300.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NotflixColors.Elevated)
                        .border(1.dp, NotflixColors.Line, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Yayın açılamadı",
                        style = MaterialTheme.typography.headlineSmall,
                        color = NotflixColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorText ?: "Bilinmeyen hata.",
                        color = NotflixColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NotflixButton(text = "Tekrar Dene", onClick = { manualRetry() })
                        NotflixButton(text = "Geri", primary = false, onClick = onBack)
                    }
                }
            }
        }

        if (showLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Yükleme sırasında logo yerine kanal adı gösterilir.
                    if (isLive && displayTitle.isNotBlank()) {
                        Text(
                            text = displayTitle,
                            color = Color.White,
                            fontSize = if (isTv) 30.sp else 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                    CircularProgressIndicator(
                        color = NotflixColors.Accent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = if (isLive) "Kanal bağlanıyor…" else "Yayın hazırlanıyor…",
                        color = NotflixColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    isLive: Boolean,
    onBack: () -> Unit,
    onPanel: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlCircle(
            icon = Icons.Filled.KeyboardArrowLeft,
            contentDescription = "Geri",
            size = 38.dp,
            filled = false,
            onClick = onBack
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (isLive) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NotflixColors.Accent.copy(alpha = 0.16f))
                    .border(
                        1.dp,
                        NotflixColors.Accent.copy(alpha = 0.4f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiveDot()
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CANLI",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        if (onPanel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            ControlCircle(
                icon = Icons.Filled.LiveTv,
                contentDescription = "Kanal listesi",
                size = 34.dp,
                filled = false,
                onClick = onPanel
            )
        }
    }
}

@Composable
private fun ChannelPanel(
    channels: List<LiveChannel>,
    currentIndex: Int,
    focusRequester: FocusRequester,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit
) {
    val tv = LocalConfiguration.current.smallestScreenWidthDp >= 600
    var query by remember { mutableStateOf("") }
    // Panel acildiginda su an oynayan kanalin grubu secili olsun; boyce
    // listede "Tumü" ilk sirada bekleyip ilk Ulusal kanali acilmaz.
    val currentGroup = channels.getOrNull(currentIndex)?.group?.takeIf { it.isNotBlank() } ?: "Tümü"
    var group by remember { mutableStateOf(currentGroup) }
    // Kullanici elle bir grup secerse secim korunur (kanal degisse de).
    var groupPinned by remember { mutableStateOf(false) }
    LaunchedEffect(currentGroup) {
        if (!groupPinned) group = currentGroup
    }
    val focusManager = LocalFocusManager.current
    val groups = remember(channels) {
        val preferred = listOf(
            "Ulusal", "Haber", "Spor", "Sinema & Dizi", "Belgesel",
            "Çocuk", "Müzik", "Yaşam", "Dini", "Avrupa"
        )
        val present = channels.map { it.group }.distinct()
        // "Tümü" sag panelin en sonunda olsun.
        preferred.filter { it in present } +
            present.filter { it !in preferred }.sorted() + listOf("Tümü")
    }
    val needle = remember(query) { panelSearchKey(query.trim()) }
    val filtered = remember(channels, group, needle) {
        channels.withIndex().filter { entry ->
            val inGroup = group == "Tümü" || entry.value.group == group
            inGroup && (needle.length < 2 || panelSearchKey(entry.value.name).contains(needle))
        }
    }
    val selectedPos = filtered.indexOfFirst { it.index == currentIndex }.coerceAtLeast(0)
    // Odak her zaman bir kanalda olsun: oynayan kanal listede yoksa ilk oge.
    val focusPos = if (selectedPos in filtered.indices) selectedPos else 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedPos)
    LaunchedEffect(selectedPos, group, needle) {
        runCatching { listState.scrollToItem(selectedPos) }
    }
    // Panel acildiginda odak dogrudan kanal listesinde olsun (arama alaninda degil),
    // boylece kumanda ile kanallar arasinda gezinebilirsin. Ogeler LazyColumn
    // icinde geci olusabildigi icin birkac kez denenir.
    LaunchedEffect(filtered.size, group, needle) {
        repeat(5) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(if (tv) 380.dp else 296.dp)
                .background(Color(0xF00B0B0B))
                .border(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kanallar",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (filtered.size == channels.size) "${currentIndex + 1}/${channels.size}"
                    else "${filtered.size}/${channels.size}",
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                ControlCircle(
                    icon = Icons.Filled.Close,
                    contentDescription = "Kapat",
                    size = 34.dp,
                    filled = false,
                    onClick = onClose
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            // Asagi tusu direkt kanal listesine gecsin; arama
                            // alaninda kalmak kumandayi kilitler.
                            Key.DirectionDown -> {
                                if (filtered.isNotEmpty()) {
                                    runCatching { focusRequester.requestFocus() }
                                }
                                true
                            }
                            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                            else -> false
                        }
                    },
                placeholder = {
                    Text("Kanal ara", color = NotflixColors.TextMuted, fontSize = 12.sp)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = NotflixColors.TextMuted,
                        modifier = Modifier.size(17.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(9.dp),
                textStyle = MaterialTheme.typography.bodySmall,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NotflixColors.Accent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = NotflixColors.Accent,
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(groups, key = { it }) { item ->
                    FilterChip(
                        label = item,
                        selected = item == group,
                        onClick = { group = item }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(filtered, key = { _, entry -> entry.value.name + entry.value.url }) { pos, entry ->
                    val idx = entry.index
                    val item = entry.value
                    val selected = idx == currentIndex
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    focused -> Color.White.copy(alpha = 0.10f)
                                    selected -> NotflixColors.Accent.copy(alpha = 0.20f)
                                    else -> Color.Transparent
                                }
                            )
                            .border(
                                if (focused || selected) 1.dp else 0.dp,
                                when {
                                    focused -> Color.White.copy(alpha = 0.45f)
                                    selected -> NotflixColors.Accent.copy(alpha = 0.5f)
                                    else -> Color.Transparent
                                },
                                RoundedCornerShape(8.dp)
                            )
                            .then(if (pos == focusPos) Modifier.focusRequester(focusRequester) else Modifier)
                            // Kumandayla (D-pad) secilebilmesi icin focus alabilir olmali.
                            .onFocusChanged { focused = it.isFocused }
                            .clickable { onSelect(idx) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp, 24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.logo.isNotBlank()) {
                                AsyncImage(
                                    model = item.logo,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                )
                            } else {
                                Text(
                                    text = item.name.take(2),
                                    color = NotflixColors.TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = if (selected || focused) Color.White else NotflixColors.TextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.group,
                                color = NotflixColors.TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                        if (selected) {
                            LiveDot()
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    item(key = "no-channel") {
                        Text(
                            text = "Kanal bulunamadı.",
                            color = NotflixColors.TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun heightLabel(height: Int): String = if (height >= 2160) "4K" else "${height}p"

private fun qualityLabel(height: Int?): String =
    if (height == null) "Otomatik" else heightLabel(height)

@Composable
private fun QualityPanel(
    heights: List<Int>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onClose: () -> Unit,
    autoOnly: Boolean = false
) {
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(90)
        runCatching { firstRowFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(240.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NotflixColors.Elevated)
                .border(1.dp, NotflixColors.Line, RoundedCornerShape(14.dp))
                .padding(vertical = 10.dp)
                .clickable(enabled = false) {}
        ) {
            Text(
                text = "Görüntü kalitesi",
                color = NotflixColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            QualityRow(
                modifier = Modifier.focusRequester(firstRowFocus),
                label = "Otomatik",
                selected = selected == null,
                onClick = {
                    onSelect(null)
                    onClose()
                }
            )
            heights.forEach { height ->
                if (autoOnly) return@forEach
                QualityRow(
                    label = heightLabel(height),
                    selected = selected == height,
                    onClick = {
                        onSelect(height)
                        onClose()
                    }
                )
            }
            if (!autoOnly && heights.isNotEmpty() && heights.max() < 1080) {
                QualityRow(
                    label = "1080p",
                    selected = selected == 1080,
                    onClick = {
                        onSelect(1080)
                        onClose()
                    }
                )
            }
            if (autoOnly) {
                Text(
                    text = "Film ve dizilerde kalite otomatik olarak seçilir.",
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            } else if (heights.isEmpty()) {
                Text(
                    text = "Yayın başladığında seçenekler görünür.",
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            } else if (heights.size == 1) {
                Text(
                    text = "Kaynak ${heightLabel(heights.first())}. 1080p seçeneği görüntüyü " +
                        "1080p'ye ölçekler, kaynak netliği değişmez.",
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> Color.White.copy(alpha = 0.10f)
                    selected -> NotflixColors.Accent.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected || focused) Color.White else NotflixColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Text(
                text = "✓",
                color = NotflixColors.AccentSoft,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LiveProgramBar(
    channel: LiveChannel?,
    program: TvProgram?,
    now: Long,
    elapsedMs: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiveDot()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "CANLI",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = program?.title ?: channel?.group?.let { "$it kanalı" } ?: "Canlı yayın",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (program != null) {
                val total = (program.endMs - program.startMs).coerceAtLeast(1L)
                val progress = ((now - program.startMs).toFloat() / total).coerceIn(0f, 1f)
                Spacer(modifier = Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(NotflixColors.Accent)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${clockRange(program.startMs)} – ${clockRange(program.endMs)}",
                    color = NotflixColors.TextMuted,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "İzleniyor ${formatTime(elapsedMs)}",
            color = NotflixColors.TextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LiveDot() {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(NotflixColors.Accent)
    )
}

@Composable
private fun ControlCircle(
    icon: ImageVector,
    contentDescription: String,
    size: Dp,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    // Google TV'de odak halkasi marka renginde ve daha belirgin olsun
    // (kumandada gezerken secili dugme net gorunsun).
    val tv = isTvLayout()
    val focusColor = if (tv) NotflixColors.AccentSoft else Color.White
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (focused) {
                    if (tv) NotflixColors.Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f)
                } else if (filled) Color.White else Color.Black.copy(alpha = 0.45f)
            )
            .border(
                if (focused) (if (tv) 3.dp else 2.dp) else 1.dp,
                if (focused) focusColor else Color.White.copy(alpha = 0.22f),
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (filled) Color.Black else Color.White,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
private fun SeekCircle(
    label: String,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val tv = isTvLayout()
    val focusColor = if (tv) NotflixColors.AccentSoft else Color.White
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (focused) {
                    if (tv) NotflixColors.Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f)
                } else Color.Black.copy(alpha = 0.42f)
            )
            .border(
                if (focused) (if (tv) 3.dp else 2.dp) else 1.dp,
                if (focused) focusColor else Color.White.copy(alpha = 0.2f),
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (size < 42.dp) 10.5.sp else 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun clockRange(ms: Long): String {
    val time = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return String.format(
        "%02d:%02d",
        time.get(java.util.Calendar.HOUR_OF_DAY),
        time.get(java.util.Calendar.MINUTE)
    )
}

private fun friendlyError(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "Ağ bağlantısı kurulamadı. Bağlantınızı kontrol edip tekrar deneyin."
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        "Kaynak sunucu yayını reddetti. Tekrar deneyin; gerekirse başka bir bölüm seçin."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED ->
        "Bu cihaz yayın biçimini çözemedi. Başka bir içerik deneyin."
    else -> "Yayın oynatılamadı. Tekrar deneyin."
}

private fun liveError(): String =
    "Kanal şu an yayında değil. Başka bir kanal seçin veya tekrar deneyin."

private fun looksLikeHls(url: String, isLive: Boolean): Boolean {
    val clean = url.substringBefore('?').lowercase()
    if (clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mkv")) return false
    if (clean.endsWith(".m3u8") || clean.endsWith(".m3u")) return true
    return isLive || url.contains("/api/stream.php", ignoreCase = true) ||
        url.contains("/hls/", ignoreCase = true)
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun panelSearchKey(value: String): String = value.lowercase()
    .replace('ı', 'i').replace('î', 'i').replace('ç', 'c').replace('ğ', 'g')
    .replace('ü', 'u').replace('û', 'u').replace('ş', 's').replace('ö', 'o')
    .replace('â', 'a')

private fun resizeModeFor(aspect: String): Int = when (aspect) {
    "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

private fun readStreamVolume(manager: AudioManager?): Float {
    val am = manager ?: return 1f
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return 0f
    return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

private fun writeStreamVolume(manager: AudioManager?, fraction: Float) {
    val am = manager ?: return
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return
    am.setStreamVolume(
        AudioManager.STREAM_MUSIC,
        (fraction.coerceIn(0f, 1f) * max).roundToInt(),
        0
    )
}

private fun seasonFromUrl(url: String): Int =
    Regex("/sezon-(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

private fun episodeFromUrl(url: String): Int =
    Regex("/bolum-(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

private const val ORIGIN = "https://filmmodu.news"
private const val USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
