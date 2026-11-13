package com.notflix.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.notflix.app.data.HeroSlide
import com.notflix.app.data.HomeData
import com.notflix.app.data.HomeSection
import com.notflix.app.data.MediaCard
import com.notflix.app.data.MediaType
import com.notflix.app.data.WatchEntry
import com.notflix.app.ui.components.ErrorState
import com.notflix.app.ui.components.LoadingState
import com.notflix.app.ui.components.MediaRow
import com.notflix.app.ui.components.DeleteButton
import com.notflix.app.ui.components.NotflixButton
import com.notflix.app.ui.components.focusBringIntoView
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.screens.player.PlayerKeyBus
import com.notflix.app.ui.theme.NotflixColors
import com.notflix.app.ui.theme.NotflixTheme
import com.notflix.app.NotflixApplication
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private sealed interface HomeUi {
    data object Loading : HomeUi
    data class Ready(val data: HomeData) : HomeUi
    data class Failed(val message: String) : HomeUi
}

@Composable
fun HomeScreen(
    onPlay: (pageUrl: String, title: String) -> Unit,
    onOpenDetail: (url: String, title: String) -> Unit,
    onSeeAll: (sectionTitle: String, moreUrl: String) -> Unit,
    onRemoveResume: (WatchEntry) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    resumeListState: LazyListState = rememberLazyListState(),
    autoFocusHero: Boolean = false,
    onAutoFocusDone: () -> Unit = {},
    memory: com.notflix.app.ui.UiMemory = com.notflix.app.ui.UiMemory()
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication

    var ui by remember { mutableStateOf<HomeUi>(HomeUi.Loading) }
    var reloadTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadTick) {
        ui = HomeUi.Loading
        val result = runCatching {
            withTimeoutOrNull(45_000) { app.content.home(force = reloadTick > 0) }
                ?: throw TimeoutException("Sunucu yanıt vermedi.")
        }
            .fold(
                onSuccess = { HomeUi.Ready(it) },
                onFailure = { failure ->
                    val cached = app.content.cachedHome()
                    if (cached != null) {
                        HomeUi.Ready(cached)
                    } else {
                        HomeUi.Failed(failure.message ?: "Katalog yüklenemedi.")
                    }
                }
            )
        ui = result
    }

    when (val state = ui) {
        is HomeUi.Loading -> {
            NotflixTheme { Box(Modifier.fillMaxSize()) { LoadingState("Katalog hazırlanıyor…") } }
        }
        is HomeUi.Failed -> {
            Box(Modifier.fillMaxSize()) {
                ErrorState(state.message) { reloadTick++ }
            }
        }
        is HomeUi.Ready -> {
            HomeContent(
                data = state.data,
                onPlay = onPlay,
                onOpenDetail = onOpenDetail,
                onSeeAll = onSeeAll,
                onRemoveResume = onRemoveResume,
                listState = listState,
                resumeListState = resumeListState,
                autoFocusHero = autoFocusHero,
                onAutoFocusDone = onAutoFocusDone,
                memory = memory
            )
        }
    }
}

@Composable
private fun HomeContent(
    data: HomeData,
    onPlay: (pageUrl: String, title: String) -> Unit,
    onOpenDetail: (url: String, title: String) -> Unit,
    onSeeAll: (sectionTitle: String, moreUrl: String) -> Unit,
    onRemoveResume: (WatchEntry) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    resumeListState: LazyListState = rememberLazyListState(),
    autoFocusHero: Boolean = false,
    onAutoFocusDone: () -> Unit = {},
    memory: com.notflix.app.ui.UiMemory = com.notflix.app.ui.UiMemory()
) {
    val hero = data.hero
    val pagerState = rememberPagerState(pageCount = { hero.size })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication
    var heroFocused by remember { mutableStateOf(false) }
    val heroPlayFocus = remember { FocusRequester() }

    // Uygulama acilisinda odak hero slaytina gelir; alt satirlarda baslamaz.
    LaunchedEffect(Unit) {
        if (autoFocusHero && hero.isNotEmpty()) {
            listState.requestScrollToItem(0)
            delay(320)
            runCatching { heroPlayFocus.requestFocus() }
            onAutoFocusDone()
        }
    }

    val resumeEntries = remember(data) {
        app.settings.watchHistory()
            .groupBy { it.seriesKey }
            .values
            .mapNotNull { list -> list.maxByOrNull { it.updatedAt } }
            .filter { !it.completed && it.position > 3_000L }
            .sortedByDescending { it.updatedAt }
            .take(14)
    }

    LaunchedEffect(hero.size) {
        if (hero.size <= 1) return@LaunchedEffect
        while (true) {
            delay(6_000)
            if (!heroFocused && !pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % hero.size)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // 1.0.4: yukari tusu once hero odagina ve hero slaydinin
                // basina gider; hero zaten odakliysa nav'a normal gecer.
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionUp &&
                    !heroFocused &&
                    listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    hero.isNotEmpty()
                ) {
                    val moved = runCatching { heroPlayFocus.requestFocus() }.isSuccess
                    if (moved) {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                    moved
                } else {
                    false
                }
            },
        state = listState
    ) {
        item(key = "hero") {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val tv = isTvLayout()
                // Hero tam gorunecek kadar kisa tutulur (yukari/alt kesilmesin).
                val heroHeight = if (tv) 520.dp
                else (maxWidth * 0.78f).coerceIn(300.dp, 420.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heroHeight)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        HeroPanel(
                            slide = hero[page],
                            onPlay = { onPlay(hero[page].url, hero[page].title) },
                            onDetail = { onOpenDetail(hero[page].url, hero[page].title) },
                            onHeroFocus = { heroFocused = it },
                            playFocus = if (page == pagerState.currentPage) heroPlayFocus else null
                        )
                    }
                    if (hero.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(
                                    end = screenPadding(),
                                    bottom = if (isTvLayout()) 26.dp else 16.dp
                                ),
                            horizontalArrangement = Arrangement.spacedBy(if (hero.size > 12) 3.dp else 6.dp)
                        ) {
                            repeat(hero.size) { index ->
                                val selected = pagerState.currentPage == index
                                Box(
                                    modifier = Modifier
                                        .height(3.dp)
                                        .width(
                                            when {
                                                selected && hero.size > 12 -> 14.dp
                                                selected -> 22.dp
                                                hero.size > 12 -> 7.dp
                                                else -> 10.dp
                                            }
                                        )
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            if (selected) NotflixColors.Accent
                                            else Color.White.copy(alpha = 0.28f)
                                        )
                                        .pointerInput(index) {
                                            detectTapGestures {
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (resumeEntries.isNotEmpty()) {
            item(key = "continue-row") {
                ResumeRow(
                    entries = resumeEntries,
                    onResume = { entry -> onPlay(entry.url, entry.title) },
                    onRemove = onRemoveResume,
                    listState = resumeListState
                )
            }
        }

        items(data.sections.size, key = { "section-${data.sections[it].title}" }) { index ->
            val section: HomeSection = data.sections[index]
            // Her rayin yatay konumu saklanir; geri donuldugunde ayni yerde kalir.
            val rowState = remember(section.title) { memory.list("home-row-" + section.title) }
            MediaRow(
                title = section.title,
                items = section.items,
                onItemClick = { card -> onOpenDetail(card.url, card.title) },
                onSeeAll = { onSeeAll(section.title, section.moreUrl) },
                listState = rowState
            )
        }

        item(key = "bottom-space") {
            Spacer(modifier = Modifier.height(if (isTvLayout()) 40.dp else 24.dp))
        }
    }
}

@Composable
private fun ResumeRow(
    entries: List<WatchEntry>,
    onResume: (WatchEntry) -> Unit,
    onRemove: (WatchEntry) -> Unit = {},
    listState: LazyListState = rememberLazyListState()
) {
    val tv = isTvLayout()
    Column(modifier = Modifier.padding(bottom = if (tv) 12.dp else 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = screenPadding(),
                    end = screenPadding(),
                    top = if (tv) 6.dp else 2.dp,
                    bottom = if (tv) 12.dp else 8.dp
                ),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "Devam Et",
                style = if (tv) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.headlineSmall,
                color = NotflixColors.TextPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${entries.size} içerik kaldığın yerde",
                color = NotflixColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = screenPadding()),
            horizontalArrangement = Arrangement.spacedBy(if (tv) 14.dp else 10.dp)
        ) {
            items(entries, key = { it.url + it.updatedAt }) { entry ->
                ResumePreviewCard(
                    entry = entry,
                    onClick = { onResume(entry) }
                )
            }
        }
    }
}

@Composable
private fun ResumePreviewCard(
    entry: WatchEntry,
    onClick: () -> Unit
) {
    val tv = isTvLayout()
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .width(if (tv) 330.dp else 272.dp)
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.08f) else NotflixColors.Card)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) NotflixColors.AccentSoft else Color.White.copy(alpha = 0.08f),
                shape
            )
            .focusBringIntoView()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(if (tv) 68.dp else 58.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(NotflixColors.Elevated)
        ) {
            AsyncImage(
                model = entry.poster,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (tv) 28.dp else 24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (tv) 17.dp else 15.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = NotflixColors.TextPrimary,
                fontSize = if (tv) 13.5.sp else 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.episodeLabel.isNotBlank()) {
                Text(
                    text = entry.episodeLabel,
                    color = NotflixColors.TextMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(entry.progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(NotflixColors.Accent)
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = remainingLabel(entry),
                color = NotflixColors.AccentSoft,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun remainingLabel(entry: WatchEntry): String {
    val remaining = entry.duration - entry.position
    if (remaining <= 0L) return "%${(entry.progress * 100).toInt().coerceIn(0, 100)} izlendi"
    val minutes = (remaining / 60_000).toInt().coerceAtLeast(1)
    return "$minutes dk kaldı"
}

@Composable
private fun HeroPanel(
    slide: HeroSlide,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
    onHeroFocus: (Boolean) -> Unit,
    playFocus: FocusRequester? = null,
    detailFocus: FocusRequester? = null
) {
    val tv = isTvLayout()
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication
    var description by remember(slide.url) { mutableStateOf(slide.description) }

    LaunchedEffect(slide.url) {
        if (description.isNotBlank()) return@LaunchedEffect
        val filled = runCatching { app.content.fillDescriptions(listOf(slide)) }.getOrNull()
        val text = filled?.firstOrNull()?.description.orEmpty()
        if (text.isNotBlank()) description = text
    }

    val posterBackdrop = slide.backdrop.contains("/posters/")
    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = slide.backdrop,
            contentDescription = slide.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (posterBackdrop) {
                        Modifier
                            .blur(24.dp)
                            .graphicsLayer {
                                scaleX = 1.18f
                                scaleY = 1.18f
                            }
                    } else {
                        Modifier
                    }
                )
        )
        if (posterBackdrop) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.82f),
                        0.45f to Color.Black.copy(alpha = 0.35f),
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
                        0.55f to Color.Transparent,
                        1f to NotflixColors.Bg
                    )
                )
        )
        if (tv && posterBackdrop) {
            AsyncImage(
                model = slide.backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 54.dp, bottom = 26.dp)
                    .width(168.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(if (tv) 0.62f else 0.92f)
                .padding(
                    start = screenPadding(),
                    end = 12.dp,
                    bottom = if (tv) 46.dp else 28.dp
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(NotflixColors.Accent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (slide.type == MediaType.MOVIE) "FİLM" else "DİZİ",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                if (slide.rating != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = NotflixColors.Star,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = String.format("%.1f", slide.rating),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (slide.year != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = slide.year,
                        color = NotflixColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(if (tv) 12.dp else 7.dp))
            Text(
                text = slide.title,
                style = if (tv) MaterialTheme.typography.displayMedium
                else MaterialTheme.typography.displaySmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (slide.genres.isNotBlank()) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = slide.genres,
                    color = NotflixColors.AccentSoft,
                    fontSize = if (tv) 13.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (description.isNotBlank()) {
                Spacer(modifier = Modifier.height(if (tv) 10.dp else 7.dp))
                Text(
                    text = description,
                    color = NotflixColors.TextSecondary,
                    fontSize = if (tv) 14.sp else 12.sp,
                    lineHeight = if (tv) 21.sp else 18.sp,
                    maxLines = if (tv) 3 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(if (tv) 18.dp else 13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NotflixButton(
                    text = "İzle",
                    icon = Icons.Filled.PlayArrow,
                    onClick = onPlay,
                    modifier = Modifier
                        .then(if (playFocus != null) Modifier.focusRequester(playFocus) else Modifier)
                        .focusBringIntoView()
                        .onFocusChanged { onHeroFocus(it.hasFocus) }
                )
                NotflixButton(
                    text = "Detaylar",
                    primary = false,
                    onClick = onDetail,
                    modifier = Modifier
                        .then(if (detailFocus != null) Modifier.focusRequester(detailFocus) else Modifier)
                        .focusBringIntoView()
                        .onFocusChanged { onHeroFocus(it.hasFocus) }
                )
            }
        }
    }
}
