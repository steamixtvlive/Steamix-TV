package com.notflix.app.ui.screens.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.notflix.app.NotflixApplication
import com.notflix.app.data.BrowsePage
import com.notflix.app.data.MediaCard
import com.notflix.app.data.MediaType
import com.notflix.app.ui.components.CircularLoading
import com.notflix.app.ui.components.EmptyState
import com.notflix.app.ui.components.ErrorState
import com.notflix.app.ui.components.LoadingState
import com.notflix.app.ui.components.NotflixButton
import com.notflix.app.ui.components.PosterCard
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class GenreOption(val label: String, val slug: String?)

val NOTFLIX_GENRES = listOf(
    GenreOption("Tümü", null),
    GenreOption("Aksiyon", "aksiyon"),
    GenreOption("Komedi", "komedi"),
    GenreOption("Dram", "dram"),
    GenreOption("Gerilim", "gerilim"),
    GenreOption("Korku", "korku"),
    GenreOption("Bilim Kurgu", "bilim-kurgu"),
    GenreOption("Romantik", "romantik"),
    GenreOption("Suç", "suc"),
    GenreOption("Macera", "macera"),
    GenreOption("Animasyon", "animasyon"),
    GenreOption("Belgesel", "belgesel"),
    GenreOption("Fantastik", "fantastik"),
    GenreOption("Gizem", "gizem"),
    GenreOption("Savaş", "savas")
)

@Composable
fun BrowseScreen(
    type: MediaType,
    onItemClick: (MediaCard) -> Unit,
    initialGenre: String? = null,
    heading: String? = null,
    gridState: LazyGridState = rememberLazyGridState(),
    onGenreChange: (String?) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication
    val scope = rememberCoroutineScope()

    var genre by remember(type, initialGenre) {
        mutableStateOf(NOTFLIX_GENRES.firstOrNull { it.slug == initialGenre } ?: NOTFLIX_GENRES.first())
    }
    var items by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }

    suspend fun seedFromGenres(): BrowsePage? {
        val merged = ArrayList<MediaCard>()
        for (slug in listOf("aksiyon", "dram", "komedi", "gerilim")) {
            val page = try {
                withTimeoutOrNull(8_000) { app.content.browse(type, 1, slug) }
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                null
            }
            merged += page?.items.orEmpty()
            if (merged.size >= 48) break
        }
        val unique = merged.distinctBy { it.url }
        if (unique.isEmpty()) return null
        return BrowsePage(items = unique, hasMore = true, total = null)
    }

    suspend fun fetchPage(target: Int): Result<BrowsePage> = try {
        var page = withTimeoutOrNull(18_000) {
            app.content.browse(type, target, genre.slug)
        }
        if ((page == null || page.items.isEmpty()) && genre.slug != null) {
            page = withTimeoutOrNull(18_000) {
                app.content.browse(type, target, null)
            }
        }
        val result = page ?: throw java.util.concurrent.TimeoutException("zaman aşımı")
        Result.success(result)
    } catch (cancel: kotlinx.coroutines.CancellationException) {
        throw cancel
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

    suspend fun load(reset: Boolean) {
        val gen = generation
        if (reset) {
            loading = true
            error = null
            page = 0
            hasMore = true
            total = 0
            items = emptyList()
            if (genre.slug == null) {
                val seeded = app.content.cachedHome()?.sections
                    ?.flatMap { it.items }
                    ?.filter { it.type == type }
                    ?.distinctBy { it.url }
                    .orEmpty()
                if (seeded.isNotEmpty()) items = seeded
            }
        } else {
            if (loadingMore || loading || !hasMore) return
            loadingMore = true
        }
        try {
            var failed: String? = null
            var replaceFirst = reset
            var current = page
            val batch = if (reset) 1 else 3
            for (step in 0 until batch) {
                if (gen != generation) return
                val target = current + 1
                var result: BrowsePage? = null
                var lastError: String? = null
                for (tryIndex in 0..1) {
                    val outcome = fetchPage(target)
                    if (gen != generation) return
                    result = outcome.getOrNull()
                    lastError = outcome.exceptionOrNull()?.message
                    if (result != null && result.items.isNotEmpty()) break
                    if (tryIndex == 0) kotlinx.coroutines.delay(350L)
                }
                if (result == null) {
                    if (reset && target == 1) {
                        val fallback = seedFromGenres()
                        if (fallback != null && fallback.items.isNotEmpty()) {
                            items = fallback.items
                            hasMore = true
                            break
                        }
                    }
                    failed = lastError ?: "İçerikler yüklenemedi."
                    break
                }
                if (result.items.isEmpty()) {
                    result = if (reset && target == 1) seedFromGenres() else null
                    if (result == null || result.items.isEmpty()) {
                        failed = "Bu listede içerik bulunamadı."
                        break
                    }
                }
                val previousCount = items.size
                items = if (replaceFirst) result.items
                else (items + result.items).distinctBy { it.url }
                val added = items.size - previousCount
                replaceFirst = false
                current = target
                page = target
                hasMore = result.hasMore
                result.total?.let { if (it > 0) total = it }
                if (!result.hasMore) break
                if (target > 1 && added <= 0) {
                    hasMore = false
                    break
                }
            }
            if (gen == generation) error = failed
        } finally {
            if (gen == generation) {
                loading = false
                loadingMore = false
            }
        }
    }

    fun startLoad(reset: Boolean) {
        scope.launch {
            withTimeoutOrNull(75_000) { load(reset = reset) }
            if (loading) {
                loading = false
                loadingMore = false
                if (items.isEmpty()) error = "Sunucu yanıt vermedi. Tekrar dene."
            }
        }
    }

    LaunchedEffect(type, genre) {
        generation++
        items = emptyList()
        loading = true
        error = null
        // scrollToItem suspends until the grid has been laid out. While the loading
        // placeholder is on screen the grid is not composed yet, so awaiting it here
        // would block the load forever. requestScrollToItem only schedules the scroll.
        gridState.requestScrollToItem(0)
        withTimeoutOrNull(75_000) { load(reset = true) }
        if (loading) {
            loading = false
            if (items.isEmpty()) error = "Sunucu yanıt vermedi. Tekrar dene."
        }
    }

    LaunchedEffect(genre, type, items.isNotEmpty()) {
        if (items.isNotEmpty()) {
            kotlinx.coroutines.delay(120)
            runCatching { firstFocus.requestFocus() }
        }
    }

    LaunchedEffect(gridState, genre, type) {
        snapshotFlow {
            val info = gridState.layoutInfo
            Triple(gridState.canScrollForward, info.totalItemsCount, info.visibleItemsInfo.size)
        }
            .distinctUntilChanged()
            .collect { (canScroll, count, _) ->
                if (count > 0 && !canScroll && items.isNotEmpty() &&
                    hasMore && !loading && !loadingMore
                ) {
                    load(reset = false)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = screenPadding(),
                    end = screenPadding(),
                    top = if (isTvLayout()) 18.dp else 12.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = heading ?: if (type == MediaType.MOVIE) "Filmler" else "Diziler",
                    style = if (isTvLayout()) {
                        androidx.compose.material3.MaterialTheme.typography.headlineMedium
                    } else {
                        androidx.compose.material3.MaterialTheme.typography.headlineSmall
                    },
                    color = NotflixColors.TextPrimary
                )
                Text(
                    text = when {
                        loading && items.isEmpty() -> "Yükleniyor…"
                        loading -> "${items.size} yüklendi · toplam ${total.takeIf { it > 0 }?.grouped() ?: "…"}"
                        total > 0 -> "Toplam ${total.grouped()} içerik · ${items.size} yüklendi"
                        else -> "${items.size} içerik"
                    },
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            GenreSelector(
                current = genre,
                onSelect = {
                    genre = it
                    onGenreChange(it.slug)
                }
            )
        }

        if (error != null && items.isNotEmpty()) {
            ErrorBanner(
                message = error ?: "",
                onRetry = { startLoad(false) }
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading && items.isEmpty() -> LoadingState("İçerikler getiriliyor…")
                error != null && items.isEmpty() -> ErrorState(
                    error ?: "Bağlantı hatası"
                ) { startLoad(true) }
                items.isEmpty() -> EmptyState("Bu türde içerik bulunamadı.")
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(if (isTvLayout()) 148.dp else 104.dp),
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(
                        start = screenPadding(),
                        end = screenPadding(),
                        top = 4.dp,
                        bottom = 28.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (isTvLayout()) 14.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTvLayout()) 18.dp else 14.dp)
                ) {
                    itemsIndexed(items, key = { _, card -> card.url }) { index, card ->
                        PosterCard(
                            card = card,
                            onClick = { onItemClick(card) },
                            width = null,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                    if (loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "loading-more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularLoading(label = "Devamı yükleniyor…")
                            }
                        }
                    } else if (hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                NotflixButton(
                                    text = "Daha fazla yükle (${items.size}/${total.takeIf { it > 0 } ?: "?"})",
                                    primary = false,
                                    onClick = { scope.launch { load(reset = false) } }
                                )
                            }
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "end") {
                            Text(
                                text = "${items.size} içerik · listenin sonu",
                                color = NotflixColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp)
                                    .padding(bottom = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = screenPadding(), vertical = 4.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(NotflixColors.Accent.copy(alpha = 0.10f))
            .border(1.dp, NotflixColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = NotflixColors.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Tekrar dene",
            color = NotflixColors.AccentSoft,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(".").reversed()

@Composable
private fun GenreSelector(
    current: GenreOption,
    onSelect: (GenreOption) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    var hasOpened by remember { mutableStateOf(false) }
    val triggerFocus = remember { FocusRequester() }
    val shape = RoundedCornerShape(10.dp)
    val tv = isTvLayout()

    LaunchedEffect(open) {
        if (open) {
            hasOpened = true
        } else if (hasOpened) {
            kotlinx.coroutines.delay(60L)
            runCatching { triggerFocus.requestFocus() }
        }
    }

    Box {
        Row(
            modifier = Modifier
                .heightIn(min = if (tv) 46.dp else 40.dp)
                .clip(shape)
                .background(if (open) NotflixColors.Accent else NotflixColors.Elevated)
                .border(
                    1.5.dp,
                    when {
                        open -> NotflixColors.Accent
                        focused -> Color.White.copy(alpha = 0.55f)
                        else -> Color.White.copy(alpha = 0.12f)
                    },
                    shape
                )
                .focusRequester(triggerFocus)
                .onFocusChanged { focused = it.isFocused }
                .clickable { open = !open }
                .padding(horizontal = if (tv) 16.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tür: ${current.label}",
                color = Color.White,
                fontSize = if (tv) 13.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        if (open) {
            val focusRequester = remember { FocusRequester() }
            val dropdownOffset = with(androidx.compose.ui.platform.LocalDensity.current) {
                IntOffset(0, (if (tv) 48.dp else 42.dp).roundToPx())
            }
            Popup(
                alignment = Alignment.TopEnd,
                offset = dropdownOffset,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .width(if (tv) 250.dp else 210.dp)
                        .heightIn(max = 340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NotflixColors.Elevated)
                        .border(1.dp, NotflixColors.Line, RoundedCornerShape(12.dp))
                        .padding(vertical = 6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    items(NOTFLIX_GENRES, key = { it.label }) { option ->
                        var rowFocused by remember { mutableStateOf(false) }
                        val selected = option.label == current.label
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        rowFocused -> Color.White.copy(alpha = 0.10f)
                                        selected -> NotflixColors.Accent.copy(alpha = 0.18f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        rowFocused -> Color.White.copy(alpha = 0.5f)
                                        selected -> NotflixColors.Accent.copy(alpha = 0.55f)
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(8.dp)
                                )
                                .then(
                                    if (selected) Modifier.focusRequester(focusRequester) else Modifier
                                )
                                .onFocusChanged { rowFocused = it.isFocused }
                                .clickable {
                                    onSelect(option)
                                    open = false
                                }
                                .padding(horizontal = 12.dp, vertical = if (tv) 11.dp else 10.dp)
                        ) {
                            Text(
                                text = option.label,
                                color = if (selected || rowFocused) Color.White else NotflixColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    runCatching { focusRequester.requestFocus() }
                }
            }
        }
    }
}
