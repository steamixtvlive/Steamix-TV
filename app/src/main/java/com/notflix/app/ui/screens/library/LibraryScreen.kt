package com.notflix.app.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.data.AppSettings
import com.notflix.app.data.MediaCard
import com.notflix.app.ui.components.EmptyState
import com.notflix.app.ui.components.PosterCard
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.theme.NotflixColors

@Composable
fun LibraryScreen(
    settings: AppSettings,
    onItemClick: (MediaCard) -> Unit
) {
    var items by remember { mutableStateOf(settings.favorites()) }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(
                start = screenPadding(),
                end = screenPadding(),
                top = if (isTvLayout()) 18.dp else 12.dp,
                bottom = 10.dp
            )
        ) {
            Text(
                text = "Kitaplığım",
                style = if (isTvLayout()) MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.headlineSmall,
                color = NotflixColors.TextPrimary
            )
            Text(
                text = if (items.isEmpty()) "Detay sayfasından kitaplığına içerik ekleyebilirsin."
                else "${items.size} içerik · silmek için kartın sağ üstündeki ×",
                color = NotflixColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (items.isEmpty()) {
            EmptyState("Kitaplığın henüz boş.")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isTvLayout()) 148.dp else 104.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = screenPadding(),
                end = screenPadding(),
                top = 4.dp,
                bottom = 28.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isTvLayout()) 14.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTvLayout()) 18.dp else 14.dp)
        ) {
            items(items, key = { it.url }) { card ->
                Box {
                    PosterCard(
                        card = card,
                        onClick = { onItemClick(card) },
                        width = null
                    )
                    RemoveBadge(
                        contentDescription = "${card.title} kitaplıktan çıkar",
                        modifier = Modifier.align(Alignment.TopEnd),
                        onClick = {
                            settings.removeFavorite(card.url)
                            items = settings.favorites()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoveBadge(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val size = if (isTvLayout()) 30.dp else 26.dp
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(size)
            .background(Color.Black.copy(alpha = 0.72f), CircleShape)
            .border(
                if (focused) 1.5.dp else 1.dp,
                if (focused) NotflixColors.AccentSoft else Color.White.copy(alpha = 0.22f),
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(if (isTvLayout()) 17.dp else 15.dp)
        )
    }
}
