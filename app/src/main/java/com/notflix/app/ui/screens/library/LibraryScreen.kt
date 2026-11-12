package com.notflix.app.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.data.AppSettings
import com.notflix.app.data.MediaCard
import com.notflix.app.ui.components.DeleteButton
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
                    text = "Kitaplığım",
                    style = if (isTvLayout()) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.headlineSmall,
                    color = NotflixColors.TextPrimary
                )
                Text(
                    text = if (items.isEmpty()) "Detay sayfasından kitaplığına içerik ekleyebilirsin."
                    else "Tek tek silmek için alttaki Sil düğmesi",
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (items.isNotEmpty()) {
                DeleteButton(
                    text = if (confirmClear) "Emin misin? Tekrar bas" else "Hepsini sil",
                    onClick = {
                        if (confirmClear) {
                            settings.clearFavorites()
                            items = settings.favorites()
                            confirmClear = false
                        } else {
                            confirmClear = true
                        }
                    }
                )
            }
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    PosterCard(
                        card = card,
                        onClick = { onItemClick(card) },
                        width = null
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        DeleteButton(
                            onClick = {
                                settings.removeFavorite(card.url)
                                items = settings.favorites()
                                confirmClear = false
                            }
                        )
                    }
                }
            }
        }
    }
}
