package com.notflix.app.ui.screens.detail

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.notflix.app.NotflixApplication
import com.notflix.app.data.Episode
import com.notflix.app.data.MediaCard
import com.notflix.app.data.MediaDetail
import com.notflix.app.data.MediaType
import com.notflix.app.data.PersonCard
import com.notflix.app.ui.components.ErrorState
import com.notflix.app.ui.components.CircularLoading
import com.notflix.app.ui.components.FilterChip
import com.notflix.app.ui.components.IconAction
import com.notflix.app.ui.components.LoadingState
import com.notflix.app.ui.components.MediaRow
import com.notflix.app.ui.components.NotflixButton
import com.notflix.app.ui.components.focusBringIntoView
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.screens.player.PlayerKeyBus
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DetailZone { Back, Play, Cast, Seasons, Episodes, Related }

@Composable
fun DetailScreen(
    url: String,
    onBack: () -> Unit,
    onPlay: (pageUrl: String, title: String) -> Unit,
    onOpenDetail: (url: String, title: String) -> Unit,
    onPersonClick: (PersonCard) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication

    var loading by remember(url) { mutableStateOf(true) }
    var detail by remember(url) { mutableStateOf<MediaDetail?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retryTick by remember(url) { mutableIntStateOf(0) }

    val backFocus = remember(url) { FocusRequester() }
    val playFocus = remember(url) { FocusRequester() }

    // Compose odagi tamamen kaybolursa (pencere degisimi, gecis animasyonu,
    // WebView) ok tuslari ve klavye sessizce calismaz hale geliyordu. Activity
    // seviyesinde yakalanan bu yedek, ilk tusta odagi oynat dugmesine alir.
    // Baska bir ekranin odağı varsa (view.hasFocus true) hicbir seye karismaz.
    val view = androidx.compose.ui.platform.LocalView.current
    val latestHasDetail by rememberUpdatedState(detail != null)
    DisposableEffect(url) {
        val handler: (Int) -> Boolean = { keyCode ->
            if (latestHasDetail && !view.hasFocus()) {
                when (keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ->
                        runCatching { playFocus.requestFocus() }.isSuccess
                    else -> false
                }
            } else {
                false
            }
        }
        PlayerKeyBus.register(handler)
        onDispose { PlayerKeyBus.unregister(handler) }
    }

    LaunchedEffect(url, retryTick) {
        loading = true
        error = null
        runCatching { app.content.detail(url) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "Detay yüklenemedi." }
        loading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val current = detail
        when {
            loading && current == null -> LoadingState("Detay hazırlanıyor…")
            current == null -> ErrorState(error ?: "İçerik bulunamadı.") { retryTick++ }
            else -> DetailContent(
                detail = current,
                onPlay = onPlay,
                onOpenDetail = onOpenDetail,
                onPersonClick = onPersonClick,
                playFocus = playFocus,
                backFocus = backFocus
            )
        }

        if (!(loading && current == null)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(if (isTvLayout()) 110.dp else 86.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
            ) {
                // Geri dugmesi odak zincirine girmez; kumandada yukarı tusu
                // sayfayı kaydırır, seçim göstergeleri bozulmaz.
                IconAction(
                    icon = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Geri",
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = screenPadding(),
                            top = if (isTvLayout()) 18.dp else 10.dp
                        )
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        runCatching { playFocus.requestFocus() }.isSuccess
                                    }
                                    Key.DirectionUp -> false
                                    else -> false
                                }
                            } else {
                                false
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    detail: MediaDetail,
    onPlay: (pageUrl: String, title: String) -> Unit,
    onOpenDetail: (url: String, title: String) -> Unit,
    onPersonClick: (PersonCard) -> Unit,
    playFocus: FocusRequester,
    backFocus: FocusRequester
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val seasons = remember(detail.url) {
        detail.seasonNumbers.ifEmpty {
            detail.seasons.keys.sorted().ifEmpty { listOf(1) }
        }
    }
    var selectedSeason by remember(detail.url) { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
    var episodes by remember(detail.url, selectedSeason) {
        mutableStateOf(detail.seasons[selectedSeason].orEmpty())
    }
    var episodesLoading by remember(detail.url, selectedSeason) { mutableStateOf(false) }

    LaunchedEffect(detail.url, selectedSeason) {
        val cached = detail.seasons[selectedSeason].orEmpty()
        if (cached.isNotEmpty()) episodes = cached
        episodesLoading = true
        runCatching { app.content.episodes(detail.url, selectedSeason) }
            .onSuccess { fetched -> if (fetched.isNotEmpty()) episodes = fetched }
        episodesLoading = false
    }

    val castFocus = remember(detail.url) { FocusRequester() }
    val seasonsFocus = remember(detail.url) { FocusRequester() }
    val episodesFocus = remember(detail.url) { FocusRequester() }
    val relatedFocus = remember(detail.url) { FocusRequester() }
    // Oynat ve Kitapliga Ekle dugmeleri arasinda klavye/kumanda gecisi
    // her cihazda calissin diye acik adresle yonetilir.
    val libraryFocus = remember(detail.url) { FocusRequester() }
    var libraryFocused by remember(detail.url) { mutableStateOf(false) }
    var zone by remember(detail.url) { mutableStateOf(DetailZone.Play) }
    // Odak gercekten oyun butonuna dustu mu? (Bazi cihazlarda istek sessizce basarisiz olur.)
    var playFocused by remember(detail.url) { mutableStateOf(false) }
    // Su an hangi bolumde odak var (secim gostergesi bunu kullanir).
    var zoneFocused by remember(detail.url) { mutableStateOf(DetailZone.Play) }

    LaunchedEffect(detail.url) {
        delay(180)
        // Odak dogrulanana kadar tekrar dene: kumandayla acilan detayda
        // odak her zaman oyun butonunda olmali.
        repeat(25) {
            if (playFocused) return@LaunchedEffect
            runCatching { playFocus.requestFocus() }
            delay(100)
        }
    }

    val zoneOrder = buildList {
        add(DetailZone.Play)
        if (detail.cast.isNotEmpty()) add(DetailZone.Cast)
        if (seasons.size > 1) add(DetailZone.Seasons)
        if (detail.type == MediaType.SERIES && episodes.isNotEmpty()) add(DetailZone.Episodes)
        if (detail.related.isNotEmpty()) add(DetailZone.Related)
    }

    fun focusOf(target: DetailZone): FocusRequester = when (target) {
        DetailZone.Back -> backFocus
        DetailZone.Play -> playFocus
        DetailZone.Cast -> castFocus
        DetailZone.Seasons -> seasonsFocus
        DetailZone.Episodes -> episodesFocus
        DetailZone.Related -> relatedFocus
    }

    fun moveZone(delta: Int): Boolean {
        val current = zoneOrder.indexOf(zone).let {
            if (it >= 0) it else zoneOrder.indexOfLast { entry -> entry.ordinal < zone.ordinal }
        }
        if (current < 0) return false
        val next = current + delta
        val target = when {
            // Yukarida geri tusu yok: sayfa yukarida kalir, odur geri tusu
            // ekranin ustunde her zaman gorunur.
            next < 0 -> return false
            next >= zoneOrder.size -> return true
            else -> zoneOrder[next]
        }
        // Odak istegi bazi cihazlarda sessizce basarisiz oluyor; bu yuzden
        // birkac kez deneniyor ve odak dogrulaninca bolum degisiyor.
        var moved = false
        repeat(4) {
            if (moved) return@repeat
            runCatching { focusOf(target).requestFocus() }
            if (zoneFocused == target) moved = true
        }
        if (moved) zone = target
        return moved
    }

    val latestWatch = app.settings.watchHistory()
        .filter { it.seriesKey == detail.url }
        .maxByOrNull { it.updatedAt }
    val resumeEntry = latestWatch?.takeIf { !it.completed && it.position > 3_000L }
    val finished = latestWatch?.completed == true
    val nextEpisode = if (finished && latestWatch != null) {
        nextEpisodeAfter(detail, latestWatch.season, latestWatch.episode)
    } else null
    val action: Pair<String, String> = when {
        resumeEntry != null -> resumeEntry.url to detail.title
        nextEpisode != null -> nextEpisode.url to
            "${detail.title} · ${nextEpisode.season}. Sezon ${nextEpisode.number}. Bölüm"
        else -> detail.firstPlayUrl to detail.title
    }
    val actionLabel = when {
        resumeEntry != null -> "Devam Et"
        finished && nextEpisode != null -> "Sonraki Bölüm"
        finished && detail.type == MediaType.MOVIE -> "Baştan İzle"
        detail.type == MediaType.SERIES -> "İlk Bölümü İzle"
        else -> "İzle"
    }
    var favorite by remember(detail.url) { mutableStateOf(app.settings.isFavorite(detail.url)) }
    val progressLabel = latestWatch?.let { entry ->
        if (entry.completed) "İzleme tamamlandı"
        else buildString {
            append("Kaldığın yer")
            if (entry.episodeLabel.isNotBlank()) append(" · ${entry.episodeLabel}")
            append(" · %${(entry.progress * 100).toInt()}")
        }
    }.orEmpty()

    val detailScroll = rememberScrollState()
    // Fragman tam ekran modu: sayfa icerigi gizlenir, video ekrani kaplar.
    var trailerFullscreen by remember(detail.url) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
            .verticalScroll(detailScroll)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    // Asagi tusu bolumler arasinda gecer; odak gecince sayfa
                    // otomatik kayar.
                    Key.DirectionDown -> moveZone(1)
                    Key.DirectionRight -> {
                        if (!libraryFocused && zone == DetailZone.Play) {
                            runCatching { libraryFocus.requestFocus() }.isSuccess
                        } else {
                            false
                        }
                    }
                    Key.DirectionLeft -> {
                        if (libraryFocused) {
                            runCatching { playFocus.requestFocus() }.isSuccess
                        } else {
                            false
                        }
                    }
                    // Yukarida once sayfa kaydirilir (en ustte degilse),
                    // en ustteyken bolum geri gider.
                    Key.DirectionUp -> {
                        if (zone == DetailZone.Play && detailScroll.value > 8f) {
                            scope.launch { detailScroll.animateScrollBy(-420f) }
                            true
                        } else {
                            moveZone(-1)
                        }
                    }
                    else -> false
                }
            }
    ) {
        DetailHero(
            detail = detail,
            playFocus = playFocus,
            libraryFocus = libraryFocus,
            onPlayFocusChanged = { focused ->
                playFocused = focused
                if (focused) { zone = DetailZone.Play; zoneFocused = DetailZone.Play; libraryFocused = false }
            },
            onLibraryFocusChanged = { focused ->
                libraryFocused = focused
                if (focused) { zone = DetailZone.Play; zoneFocused = DetailZone.Play }
            },
            actionLabel = actionLabel,
            progressLabel = progressLabel,
            favorite = favorite,
            onToggleFavorite = {
                favorite = app.settings.toggleFavorite(
                    MediaCard(
                        title = detail.title,
                        url = detail.url,
                        poster = detail.poster,
                        type = detail.type
                    )
                )
            },
            onPlay = { onPlay(action.first, action.second) },
            fullscreen = trailerFullscreen,
            onToggleFullscreen = { trailerFullscreen = !trailerFullscreen }
        )

        if (detail.description.isNotBlank()) {
            SectionTitle("Özet")
            Text(
                text = detail.description,
                style = MaterialTheme.typography.bodyMedium,
                color = NotflixColors.TextSecondary,
                modifier = Modifier.padding(horizontal = screenPadding()),
                lineHeight = 22.sp
            )
        }

        if (detail.cast.isNotEmpty()) {
            SectionTitle("Oyuncular")
            LazyRow(
                modifier = Modifier.focusGroup(),
                contentPadding = PaddingValues(horizontal = screenPadding()),
                horizontalArrangement = Arrangement.spacedBy(if (isTvLayout()) 14.dp else 10.dp)
            ) {
                itemsIndexed(detail.cast, key = { _, person -> person.name + person.url }) { index, person ->
                    CastCard(
                        person = person,
                        onClick = { if (person.url.isNotBlank()) onPersonClick(person) },
                        modifier = if (index == 0) {
                            Modifier
                                .focusRequester(castFocus)
                                .onFocusChanged { if (it.hasFocus) { zone = DetailZone.Cast; zoneFocused = DetailZone.Cast } }
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }

        if (detail.type == MediaType.SERIES) {
            SectionTitle("Bölümler")
            if (seasons.size > 1) {
                LazyRow(
                    modifier = Modifier.focusGroup(),
                    contentPadding = PaddingValues(horizontal = screenPadding()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(seasons, key = { _, season -> season }) { index, season ->
                        FilterChip(
                            label = "$season. Sezon",
                            selected = selectedSeason == season,
                            onClick = { selectedSeason = season },
                            modifier = if (index == 0) {
                                Modifier
                                    .focusRequester(seasonsFocus)
                                    .onFocusChanged { if (it.hasFocus) { zone = DetailZone.Seasons; zoneFocused = DetailZone.Seasons } }
                            } else {
                                Modifier
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Column {
                if (episodesLoading && episodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularLoading("Bölümler yükleniyor…")
                    }
                } else if (episodes.isEmpty()) {
                    Text(
                        text = "Bu sezon için bölüm bulunamadı.",
                        color = NotflixColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = screenPadding())
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.focusGroup(),
                        contentPadding = PaddingValues(horizontal = screenPadding()),
                        horizontalArrangement = Arrangement.spacedBy(
                            if (isTvLayout()) 14.dp else 10.dp
                        )
                    ) {
                        itemsIndexed(episodes, key = { _, episode -> episode.url }) { index, episode ->
                            EpisodeCard(
                                episode = episode,
                                poster = detail.poster,
                                modifier = if (index == 0) {
                                    Modifier
                                        .focusRequester(episodesFocus)
                                        .onFocusChanged { if (it.hasFocus) { zone = DetailZone.Episodes; zoneFocused = DetailZone.Episodes } }
                                } else {
                                    Modifier
                                },
                                onPlay = {
                                    onPlay(
                                        episode.url,
                                        "${detail.title} · ${episode.season}. Sezon ${episode.number}. Bölüm"
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        if (detail.related.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            MediaRow(
                title = "Benzer İçerikler",
                items = detail.related,
                onItemClick = { card -> onOpenDetail(card.url, card.title) },
                firstFocus = relatedFocus,
                firstFocusChanged = { focused -> if (focused) { zone = DetailZone.Related; zoneFocused = DetailZone.Related } }
            )
        }

        Spacer(modifier = Modifier.height(if (isTvLayout()) 44.dp else 28.dp))
    }
}

@Composable
private fun DetailHero(
    detail: MediaDetail,
    playFocus: FocusRequester,
    libraryFocus: FocusRequester,
    onPlayFocusChanged: (Boolean) -> Unit,
    onLibraryFocusChanged: (Boolean) -> Unit,
    actionLabel: String,
    progressLabel: String,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    fullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {}
) {
    val tv = isTvLayout()
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication
    var trailerKeys by remember(detail.url) { mutableStateOf<List<String>>(emptyList()) }
    var trailerIndex by remember(detail.url) { mutableIntStateOf(0) }
    var trailerFailed by remember(detail.url) { mutableStateOf(false) }
    var trailerPlaying by remember(detail.url) { mutableStateOf(true) }
    // Ses varsayilan olarak acik kalir; yalnizca kullanici kapatir.
    var trailerSoundOn by remember(detail.url) { mutableStateOf(true) }

    LaunchedEffect(detail.url) {
        trailerKeys = runCatching {
            app.content.trailerKeys(detail.title, detail.year, detail.type)
        }.getOrDefault(emptyList())
    }

    Box(
        modifier = if (fullscreen) {
            Modifier.fillMaxSize().background(Color.Black)
        } else {
            Modifier
                .fillMaxWidth()
                .height(if (tv) 540.dp else 430.dp)
        }
    ) {
        if (!fullscreen) {
            AsyncImage(
                model = detail.backdrop.ifBlank { detail.poster },
                contentDescription = detail.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        val key = trailerKeys.getOrNull(trailerIndex)
        if (key != null && !trailerFailed) {
            TrailerPlayer(
                videoKey = key,
                playing = trailerPlaying,
                soundOn = trailerSoundOn,
                onFailed = {
                    if (trailerIndex + 1 < trailerKeys.size) {
                        trailerIndex += 1
                        trailerPlaying = true
                        trailerSoundOn = false
                    } else {
                        trailerFailed = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.10f))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.78f),
                        0.6f to Color.Black.copy(alpha = 0.28f),
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to NotflixColors.Bg
                    )
                )
        )
        if (key != null && !trailerFailed) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        end = screenPadding(),
                        top = if (tv) 20.dp else 14.dp
                    )
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fragman",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(start = 7.dp, end = 3.dp)
                )
                TrailerControl(
                    icon = if (trailerSoundOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (trailerSoundOn) "Fragman sesini kapat"
                    else "Fragman sesini aç",
                    onClick = { trailerSoundOn = !trailerSoundOn }
                )
                TrailerControl(
                    icon = if (trailerPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (trailerPlaying) "Fragmanı duraklat"
                    else "Fragmanı oynat",
                    onClick = { trailerPlaying = !trailerPlaying }
                )
                TrailerControl(
                    icon = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (fullscreen) "Tam ekrandan çık" else "Tam ekran",
                    onClick = onToggleFullscreen
                )
            }
        }
        if (fullscreen) {
            // Tam ekranda sol ustte kapat dugmesi.
            IconAction(
                icon = Icons.Filled.Close,
                contentDescription = "Tam ekrandan çık",
                onClick = onToggleFullscreen,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = screenPadding(),
                        top = if (tv) 18.dp else 10.dp
                    )
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = screenPadding(), vertical = if (tv) 26.dp else 16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (detail.poster.isNotBlank() && tv) {
                AsyncImage(
                    model = detail.poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(140.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(12.dp)
                        )
                )
                Spacer(modifier = Modifier.width(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (detail.type == MediaType.MOVIE) Color(0xFF2563EB)
                                else NotflixColors.Accent
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (detail.type == MediaType.MOVIE) "FİLM" else "DİZİ",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    detail.year?.let { year ->
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = year,
                            color = NotflixColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    detail.rating?.let { rating ->
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = NotflixColors.Star,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detail.title,
                    style = if (tv) MaterialTheme.typography.displayMedium
                    else MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (detail.genres.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = detail.genres.joinToString(" · "),
                        color = NotflixColors.AccentSoft,
                        fontSize = if (tv) 13.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(if (tv) 18.dp else 12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NotflixButton(
                        text = actionLabel,
                        icon = Icons.Filled.PlayArrow,
                        onClick = onPlay,
                        modifier = Modifier
                            .focusRequester(playFocus)
                            .onFocusChanged { onPlayFocusChanged(it.hasFocus) }
                    )
                    NotflixButton(
                        text = if (favorite) "Kitaplıkta" else "Kitaplığa Ekle",
                        icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        primary = false,
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .focusRequester(libraryFocus)
                            .onFocusChanged { onLibraryFocusChanged(it.hasFocus) }
                    )
                }
                if (progressLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = progressLabel,
                        color = NotflixColors.AccentSoft,
                        fontSize = if (tv) 12.sp else 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        color = NotflixColors.TextPrimary,
        modifier = Modifier.padding(
            start = screenPadding(),
            end = screenPadding(),
            top = if (isTvLayout()) 26.dp else 20.dp,
            bottom = 10.dp
        )
    )
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    poster: String,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .width(if (isTvLayout()) 238.dp else 174.dp)
            .focusBringIntoView()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onPlay)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .background(NotflixColors.Card)
                .border(
                    if (focused) 2.5.dp else 1.dp,
                    if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape
                )
        ) {
            AsyncImage(
                model = episode.still.ifBlank { poster },
                contentDescription = episode.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.78f)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "S${episode.season} · B${episode.number}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (focused) 42.dp else 0.dp)
                    .clip(CircleShape)
                    .background(NotflixColors.Accent.copy(alpha = 0.95f)),
                contentAlignment = Alignment.Center
            ) {
                if (focused) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Oynat",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = episode.title,
            color = if (focused) Color.White else NotflixColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun CastCard(person: PersonCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val initials = person.name.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
    Column(
        modifier = modifier
            .width(if (isTvLayout()) 94.dp else 78.dp)
            .focusBringIntoView()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(if (isTvLayout()) 86.dp else 72.dp)
                .clip(CircleShape)
                .background(NotflixColors.Card)
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) Color.White
                    else Color.White.copy(alpha = 0.10f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (person.photo.isNotBlank()) {
                AsyncImage(
                    model = person.photo,
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initials,
                    color = NotflixColors.TextSecondary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = person.name,
            color = if (focused) Color.White else NotflixColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp
        )
    }
}

private fun nextEpisodeAfter(detail: MediaDetail, season: Int, number: Int): Episode? {
    val seasonNumbers = detail.seasonNumbers.ifEmpty { detail.seasons.keys.sorted() }.sorted()
    for (candidate in seasonNumbers) {
        if (candidate < season) continue
        val episodes = detail.seasons[candidate].orEmpty().sortedBy { it.number }
        val next = episodes.firstOrNull { candidate > season || it.number > number }
        if (next != null) return next
    }
    return null
}

@Composable
private fun TrailerControl(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(if (isTvLayout()) 40.dp else 34.dp)
            .clip(CircleShape)
            .background(
                if (focused) Color.White.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.10f)
            )
            .border(
                1.dp,
                Color.White.copy(alpha = if (focused) 0.60f else 0.22f),
                CircleShape
            )
            .focusBringIntoView()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(if (isTvLayout()) 21.dp else 18.dp)
        )
    }
}
