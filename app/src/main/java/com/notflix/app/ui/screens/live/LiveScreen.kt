package com.notflix.app.ui.screens.live

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.NotflixApplication
import com.notflix.app.data.LiveChannel
import com.notflix.app.ui.components.EmptyState
import com.notflix.app.ui.components.ErrorState
import com.notflix.app.ui.components.FilterChip
import com.notflix.app.ui.components.IconAction
import com.notflix.app.ui.components.LiveChannelCard
import com.notflix.app.ui.components.LoadingState
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun LiveScreen(
    onChannelClick: (LiveChannel) -> Unit,
    onChannelsLoaded: (List<LiveChannel>) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication
    val scope = rememberCoroutineScope()

    var channels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf("Tümü") }
    var query by remember { mutableStateOf("") }
    var reloadTick by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var searchFocused by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val firstChipFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }

    LaunchedEffect(reloadTick) {
        loading = true
        error = null
        runCatching { app.live.load(forceRefresh = reloadTick > 0) }
            .onSuccess {
                channels = it
                onChannelsLoaded(it)
            }
            .onFailure { error = it.message ?: "Kanallar yüklenemedi." }
        loading = false
    }

    val groups = remember(channels) {
        val preferred = listOf(
            "Ulusal", "Haber", "Spor", "Sinema & Dizi", "Belgesel",
            "Çocuk", "Müzik", "Yaşam", "Dini", "Avrupa"
        )
        val present = channels.map { it.group }.distinct()
        val ordered = preferred.filter { it in present } +
            present.filter { it !in preferred }.sorted()
        listOf("Tümü") + ordered
    }
    val counts = remember(channels) {
        channels.groupingBy { it.group }.eachCount()
    }
    val needle = remember(query) { searchKey(query.trim()) }
    val visible = remember(channels, selectedGroup, needle) {
        channels.filter { channel ->
            val inGroup = selectedGroup == "Tümü" || channel.group == selectedGroup
            inGroup && (needle.length < 2 ||
                searchKey(channel.name).contains(needle) ||
                searchKey(channel.group).contains(needle))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .padding(
                    start = screenPadding(),
                    end = screenPadding(),
                    top = if (isTvLayout()) 18.dp else 12.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Canlı TV",
                    style = if (isTvLayout()) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.headlineSmall,
                    color = NotflixColors.TextPrimary
                )
                Text(
                    text = if (needle.length >= 2 || selectedGroup != "Tümü") {
                        "${visible.size} kanal gösteriliyor · toplam ${channels.size}"
                    } else {
                        "${channels.size} kanal · Türkiye · şu an yayında"
                    },
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            IconAction(
                icon = Icons.Filled.Refresh,
                contentDescription = "Kanalları yenile",
                onClick = {
                    if (!refreshing) {
                        refreshing = true
                        scope.launch {
                            val updated = app.live.refresh()
                            if (updated != null) {
                                channels = updated
                                onChannelsLoaded(updated)
                                error = null
                            } else {
                                error = "Kanal listesi güncellenemedi, mevcut liste kullanılıyor."
                            }
                            refreshing = false
                        }
                    }
                }
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = screenPadding())
                .heightIn(min = 46.dp)
                .onFocusChanged { searchFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            val moved = focusManager.moveFocus(FocusDirection.Down)
                            if (!moved) {
                                runCatching {
                                    if (groups.size > 1) firstChipFocus.requestFocus()
                                    else firstFocus.requestFocus()
                                }
                            }
                            true
                        }
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                },
            placeholder = {
                Text("Kanal ara", color = NotflixColors.TextMuted, fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    tint = NotflixColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Temizle",
                        tint = NotflixColors.TextMuted,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable { query = "" }
                            .padding(5.dp)
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NotflixColors.Accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                focusedTextColor = NotflixColors.TextPrimary,
                unfocusedTextColor = NotflixColors.TextPrimary,
                cursorColor = NotflixColors.Accent,
                focusedContainerColor = NotflixColors.Elevated,
                unfocusedContainerColor = NotflixColors.Elevated
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (groups.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = screenPadding()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(groups, key = { _, group -> group }) { index, group ->
                    val count = if (group == "Tümü") channels.size else counts[group] ?: 0
                    FilterChip(
                        label = "$group ($count)",
                        selected = group == selectedGroup,
                        onClick = { selectedGroup = group },
                        modifier = if (index == 0) Modifier.focusRequester(firstChipFocus) else Modifier
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        LaunchedEffect(visible.isNotEmpty(), selectedGroup) {
            if (visible.isNotEmpty() && !searchFocused) {
                delay(120)
                runCatching { firstFocus.requestFocus() }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> LoadingState("Canlı kanallar hazırlanıyor…")
                error != null && channels.isEmpty() -> ErrorState(error ?: "Bağlantı hatası") {
                    reloadTick++
                }
                visible.isEmpty() -> EmptyState(
                    if (needle.length >= 2) "Aramanla eşleşen kanal yok."
                    else "Bu kategoride kanal yok."
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isTvLayout()) 170.dp else 138.dp),
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(
                        start = screenPadding(),
                        end = screenPadding(),
                        top = 4.dp,
                        bottom = 28.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (isTvLayout()) 16.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTvLayout()) 20.dp else 14.dp)
                ) {
                    itemsIndexed(visible, key = { _, channel -> channel.name + channel.url }) { index, channel ->
                        LiveChannelCard(
                            channel = channel,
                            onClick = { onChannelClick(channel) },
                            fillCell = true,
                            nowMs = now,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                }
            }
        }
    }
}

private fun searchKey(value: String): String = value.lowercase()
    .replace('ı', 'i').replace('î', 'i').replace('ç', 'c').replace('ğ', 'g')
    .replace('ü', 'u').replace('û', 'u').replace('ş', 's').replace('ö', 'o')
    .replace('â', 'a')
