package com.notflix.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.notflix.app.data.WatchEntry
import com.notflix.app.ui.components.DeleteButton
import com.notflix.app.ui.components.EmptyState
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.theme.NotflixColors

@Composable
fun ContinueScreen(
    entries: List<WatchEntry>,
    onResume: (WatchEntry) -> Unit,
    onOpenDetail: (WatchEntry) -> Unit,
    onRemove: (WatchEntry) -> Unit,
    onClearAll: () -> Unit
) {
    val latest = remember(entries) {
        entries.groupBy { it.seriesKey }
            .values
            .mapNotNull { list -> list.maxByOrNull { it.updatedAt } }
    }
    val active = remember(latest) { latest.filter { !it.completed }.sortedByDescending { it.updatedAt } }
    val finished = remember(latest) { latest.filter { it.completed }.sortedByDescending { it.updatedAt } }
    val all = active + finished
    var confirmClear by remember { mutableStateOf(false) }

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
                    text = "Kaldığın yerden devam",
                    style = if (isTvLayout()) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.headlineSmall,
                    color = NotflixColors.TextPrimary
                )
                Text(
                    text = if (all.isEmpty()) "İzlemeye başladığın içerikler burada görünür."
                    else "${active.size} devam eden · ${finished.size} biten · tek tek silmek için sağdaki Sil",
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (all.isNotEmpty()) {
                DeleteButton(
                    text = if (confirmClear) "Emin misin? Tekrar bas" else "Hepsini sil",
                    onClick = {
                        if (confirmClear) {
                            onClearAll()
                            confirmClear = false
                        } else {
                            confirmClear = true
                        }
                    }
                )
            }
        }

        if (all.isEmpty()) {
            EmptyState("Henüz izlemeye başladığın bir içerik yok.")
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isTvLayout()) 330.dp else 268.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = screenPadding(),
                end = screenPadding(),
                top = 4.dp,
                bottom = 28.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isTvLayout()) 16.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTvLayout()) 18.dp else 12.dp)
        ) {
            items(all, key = { it.url }) { entry ->
                ContinueCard(
                    entry = entry,
                    onClick = {
                        if (entry.completed) onOpenDetail(entry) else onResume(entry)
                    },
                    onRemove = {
                        onRemove(entry)
                        confirmClear = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(entry: WatchEntry, onClick: () -> Unit, onRemove: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.08f) else NotflixColors.Card)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Color.White else Color.White.copy(alpha = 0.08f),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(if (isTvLayout()) 88.dp else 74.dp)
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
                if (entry.completed) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(NotflixColors.Accent)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Bitti",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .size(22.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    color = NotflixColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.episodeLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = entry.episodeLabel,
                        color = NotflixColors.TextMuted,
                        fontSize = 10.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                val percent = (entry.progress * 100).toInt().coerceIn(0, 100)
                Text(
                    text = if (entry.completed) "Tamamlandı" else "%$percent izlendi",
                    color = if (entry.completed) NotflixColors.Success
                    else NotflixColors.AccentSoft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(5.dp))
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
                            .clip(RoundedCornerShape(50))
                            .background(if (entry.completed) NotflixColors.Success else NotflixColors.Accent)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            DeleteButton(onClick = onRemove)
        }
    }
}
