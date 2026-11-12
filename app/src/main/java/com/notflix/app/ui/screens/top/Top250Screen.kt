package com.notflix.app.ui.screens.top

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.NotflixApplication
import com.notflix.app.data.MediaCard
import com.notflix.app.ui.components.EmptyState
import com.notflix.app.ui.components.ErrorState
import com.notflix.app.ui.components.LoadingState
import com.notflix.app.ui.components.PosterCard
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.theme.NotflixColors

@Composable
fun Top250Screen(onItemClick: (MediaCard) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication

    var items by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(retry) {
        loading = true
        error = null
        runCatching { app.content.top250() }
            .onSuccess { items = it }
            .onFailure { error = it.message ?: "Liste yüklenemedi." }
        loading = false
    }

    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty()) {
            kotlinx.coroutines.delay(120)
            runCatching { firstFocus.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        GridHeader(count = items.size)
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading && items.isEmpty() -> LoadingState("IMDb Top 250 getiriliyor…")
                error != null && items.isEmpty() -> ErrorState(error ?: "Bağlantı hatası") {
                    retry++
                }
                items.isEmpty() -> EmptyState("Liste şu anda boş görünüyor.")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isTvLayout()) 148.dp else 104.dp),
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.fillMaxWidth().height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GridHeader(count: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = screenPadding())) {
        Text(
            text = "IMDb Top 250",
            style = MaterialTheme.typography.headlineSmall,
            color = NotflixColors.TextPrimary,
            modifier = Modifier.padding(top = if (isTvLayout()) 16.dp else 10.dp)
        )
        Text(
            text = if (count > 0) "Sitedeki sıralama · $count yapım" else "Sitedeki sıralama",
            color = NotflixColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = if (isTvLayout()) 14.dp else 8.dp)
        )
    }
}
