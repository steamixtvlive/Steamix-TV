package com.notflix.app.ui.screens.search

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.notflix.app.NotflixApplication
import com.notflix.app.data.MediaCard
import com.notflix.app.data.PersonCard
import com.notflix.app.ui.components.EmptyState
import com.notflix.app.ui.components.FilterChip
import com.notflix.app.ui.components.LoadingState
import com.notflix.app.ui.components.PosterCard
import com.notflix.app.ui.components.ScreenTitleBar
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val POPULAR = listOf(
    "Kurtlar Vadisi",
    "Recep İvedik",
    "Gibi",
    "Diriliş",
    "Yalı Çapkını",
    "Kızılcık Şerbeti",
    "Yargı",
    "Teşkilat"
)

@Composable
fun SearchScreen(
    onItemClick: (MediaCard) -> Unit,
    onPersonClick: (PersonCard) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication
    val focusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var refreshTick by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf<List<MediaCard>>(emptyList()) }
    var people by remember { mutableStateOf<List<PersonCard>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val firstChipFocus = remember { FocusRequester() }

    fun submit(term: String) {
        val clean = term.trim()
        if (clean.isBlank()) return
        submitted = clean
        if (query == clean) refreshTick++ else query = clean
    }

    LaunchedEffect(query, refreshTick) {
        val clean = query.trim()
        if (clean.length < 2) {
            results = emptyList()
            people = emptyList()
            searched = false
            error = null
            loading = false
            return@LaunchedEffect
        }
        searched = true
        error = null
        val instant = withContext(Dispatchers.Default) { app.content.localSearch(clean, 60) }
        results = instant
        people = emptyList()
        loading = instant.isEmpty()
        delay(180)
        val suggestion = runCatching { app.content.suggest(clean) }.getOrNull()
        if (query.trim() != clean) return@LaunchedEffect
        if (suggestion != null) {
            results = (suggestion.media + instant).distinctBy { it.url }
            people = suggestion.people
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        repeat(8) {
            if (searchFocused) return@LaunchedEffect
            runCatching { focusRequester.requestFocus() }
            delay(110)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (onBack != null) {
            ScreenTitleBar(title = "Ara", onBack = onBack)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTvLayout()) 58.dp else 46.dp)
                    .padding(horizontal = screenPadding()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ara",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NotflixColors.TextPrimary
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = screenPadding())
                .focusRequester(focusRequester)
                .onFocusChanged { searchFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        // Text fields consume D-pad arrows to move the caret, which traps
                        // the focus on TV boxes. Escape downwards into the content instead.
                        Key.DirectionDown -> {
                            if (!focusManager.moveFocus(FocusDirection.Down)) {
                                runCatching { firstChipFocus.requestFocus() }
                            }
                            true
                        }
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                },
            placeholder = { Text("Film, dizi veya oyuncu yazın", color = NotflixColors.TextMuted) },
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = NotflixColors.TextMuted)
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(query) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NotflixColors.Accent,
                unfocusedBorderColor = ColorLine,
                focusedTextColor = NotflixColors.TextPrimary,
                unfocusedTextColor = NotflixColors.TextPrimary,
                cursorColor = NotflixColors.Accent,
                focusedContainerColor = NotflixColors.Elevated,
                unfocusedContainerColor = NotflixColors.Elevated
            )
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = screenPadding()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(POPULAR, key = { _, term -> term }) { index, term ->
                FilterChip(
                    label = term,
                    selected = submitted == term,
                    onClick = { submit(term) },
                    modifier = if (index == 0) Modifier.focusRequester(firstChipFocus) else Modifier
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading && results.isEmpty() && people.isEmpty() -> LoadingState("Aranıyor…")
                error != null -> EmptyState("Arama başarısız: ${error ?: ""}")
                searched && results.isEmpty() && people.isEmpty() ->
                    EmptyState("\"${query.trim()}\" için sonuç bulunamadı.")
                results.isNotEmpty() || people.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isTvLayout()) 148.dp else 104.dp),
                    modifier = Modifier.fillMaxSize().focusGroup(),
                    contentPadding = PaddingValues(
                        start = screenPadding(),
                        end = screenPadding(),
                        top = 2.dp,
                        bottom = 28.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (isTvLayout()) 14.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTvLayout()) 18.dp else 14.dp)
                ) {
                    if (people.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    text = "Oyuncular",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NotflixColors.TextPrimary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(
                                        if (isTvLayout()) 14.dp else 10.dp
                                    )
                                ) {
                                    items(people, key = { it.url }) { person ->
                                        PersonPoster(person = person, onClick = { onPersonClick(person) })
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "Yapımlar",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = NotflixColors.TextPrimary,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                            }
                        }
                    }
                    items(results, key = { it.url }) { card ->
                        PosterCard(card = card, onClick = { onItemClick(card) }, width = null)
                    }
                }
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = screenPadding()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(34.dp))
                    Text(
                        text = "Binlerce film ve dizi arasında arayın",
                        color = NotflixColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Yazmaya başladığınız anda posterli sonuçlar gelir; oyuncu araması da desteklenir.",
                        color = NotflixColors.TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonPoster(person: PersonCard, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .width(if (isTvLayout()) 116.dp else 92.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(NotflixColors.Card)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) NotflixColors.AccentSoft
                    else Color.White.copy(alpha = 0.08f),
                    shape = shape
                )
        ) {
            AsyncImage(
                model = person.photo,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "★", color = NotflixColors.Star, fontSize = 9.sp)
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = person.name,
            color = if (focused) Color.White else NotflixColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val ColorLine = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f)
