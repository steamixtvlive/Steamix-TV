package com.notflix.app.ui.screens.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.NotflixApplication
import com.notflix.app.data.BrowsePage
import com.notflix.app.data.MediaCard
import com.notflix.app.data.MediaType
import com.notflix.app.ui.components.EmptyState
import com.notflix.app.ui.components.ErrorState
import com.notflix.app.ui.components.LoadingState
import com.notflix.app.ui.components.NotflixButton
import com.notflix.app.ui.components.PosterCard
import com.notflix.app.ui.components.ScreenTitleBar
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun CollectionScreen(
    title: String,
    onBack: () -> Unit,
    onItemClick: (MediaCard) -> Unit,
    moreUrl: String = "",
    gridState: LazyGridState = rememberLazyGridState()
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    var items by remember(title, moreUrl) { mutableStateOf<List<MediaCard>>(emptyList()) }
    var page by remember(title, moreUrl) { mutableIntStateOf(0) }
    var total by remember(title, moreUrl) { mutableIntStateOf(0) }
    var hasMore by remember(title, moreUrl) { mutableStateOf(true) }
    var loading by remember(title, moreUrl) { mutableStateOf(true) }
    var loadingMore by remember(title, moreUrl) { mutableStateOf(false) }
    var error by remember(title, moreUrl) { mutableStateOf<String?>(null) }
    var retry by remember(title, moreUrl) { mutableIntStateOf(0) }
    var generation by remember(title, moreUrl) { mutableIntStateOf(0) }
    var fallbackType by remember(title, moreUrl) { mutableStateOf<MediaType?>(null) }

    suspend fun fetchPage(pageNo: Int): BrowsePage = withTimeoutOrNull(20_000) {
        if (moreUrl.isNotBlank()) app.content.collectionPage(moreUrl, pageNo)
        else {
            val known = fallbackType ?: run {
                val probe = app.content.section(title)
                val inferred = if (probe.count { it.type == MediaType.SERIES } > probe.size / 2) {
                    MediaType.SERIES
                } else {
                    MediaType.MOVIE
                }
                fallbackType = inferred
                inferred
            }
            app.content.browse(known, pageNo)
        }
    } ?: throw java.util.concurrent.TimeoutException("zaman aşımı")

    suspend fun load(reset: Boolean) {
        val gen = generation
        var seed: List<MediaCard> = emptyList()
        if (reset) {
            loading = true
            error = null
            page = 0
            total = 0
            hasMore = true
            if (items.isEmpty()) {
                seed = try {
                    app.content.section(title)
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    throw cancel
                } catch (_: Throwable) {
                    emptyList()
                }
                if (seed.isNotEmpty()) items = seed
            }
        } else {
            if (loadingMore || loading || !hasMore) return
            loadingMore = true
        }
        try {
            var failed: String? = null
            var replaceFirst = reset
            var current = page
            val batch = if (reset) 2 else 3
            for (step in 0 until batch) {
                if (gen != generation) return
                val target = current + 1
                var result: BrowsePage? = null
                var lastError: String? = null
                for (tryIndex in 0..1) {
                    try {
                        result = fetchPage(target)
                        lastError = null
                        break
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (failure: Throwable) {
                        lastError = failure.message
                        if (tryIndex == 0) kotlinx.coroutines.delay(350L)
                    }
                }
                if (result == null) {
                    failed = lastError ?: "İçerikler yüklenemedi."
                    break
                }
                if (result.items.isEmpty()) {
                    hasMore = false
                    break
                }
                val previousCount = items.size
                items = if (replaceFirst) {
                    if (moreUrl.isBlank() && seed.isNotEmpty()) {
                        (seed + result.items).distinctBy { it.url }
                    } else {
                        result.items
                    }
                } else {
                    (items + result.items).distinctBy { it.url }
                }
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

    LaunchedEffect(title, moreUrl, retry) {
        generation++
        items = emptyList()
        page = 0
        total = 0
        hasMore = true
        // Non-suspending variant: the grid still shows the loading state at this
        // point, so scrollToItem would wait for a first layout that never comes.
        gridState.requestScrollToItem(0)
        val finished = withTimeoutOrNull(75_000) {
            load(reset = true)
            true
        } ?: false
        if (!finished) {
            loading = false
            loadingMore = false
            hasMore = false
            if (items.isEmpty()) {
                error = "İçerikler yüklenemedi. Bağlantıyı kontrol edip tekrar dene."
            }
        }
    }

    LaunchedEffect(title, moreUrl, items.isNotEmpty()) {
        if (items.isNotEmpty()) {
            kotlinx.coroutines.delay(120)
            runCatching { firstFocus.requestFocus() }
        }
    }

    LaunchedEffect(gridState, title, moreUrl) {
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
        ScreenTitleBar(title = title, onBack = onBack)
        Text(
            text = when {
                loading && items.isEmpty() -> "İçerikler getiriliyor…"
                loading -> "${items.size} yüklendi · toplam ${total.takeIf { it > 0 }?.grouped() ?: "…"}"
                total > 0 -> "Toplam ${total.grouped()} içerik · ${items.size} yüklendi"
                else -> "${items.size} içerik"
            },
            color = NotflixColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(
                start = screenPadding(),
                bottom = if (isTvLayout()) 14.dp else 8.dp
            )
        )
        if (error != null && items.isNotEmpty()) {
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
                    text = error ?: "",
                    color = NotflixColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
                Text(
                    text = "Tekrar dene",
                    color = NotflixColors.AccentSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { scope.launch { load(reset = false) } }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading && items.isEmpty() -> LoadingState("İçerikler getiriliyor…")
                error != null && items.isEmpty() -> ErrorState(error ?: "Bağlantı hatası") {
                    retry++
                }
                items.isEmpty() -> EmptyState("Bu bölümde içerik bulunamadı.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isTvLayout()) 148.dp else 104.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(
                        start = screenPadding(),
                        end = screenPadding(),
                        bottom = 30.dp
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
                                    .height(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = NotflixColors.Accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.height(26.dp)
                                )
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
                                    .padding(vertical = 14.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Int.grouped(): String =
    toString().reversed().chunked(3).joinToString(".").reversed()
