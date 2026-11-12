package com.notflix.app.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.NotflixApplication
import com.notflix.app.data.MediaCard
import com.notflix.app.data.MediaType
import com.notflix.app.ui.components.ErrorState
import com.notflix.app.ui.components.LoadingState
import com.notflix.app.ui.components.NotflixButton
import com.notflix.app.ui.components.PosterCard
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.screens.browse.NOTFLIX_GENRES
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.delay

private data class Choice(val key: String, val label: String, val hint: String = "")

private val TYPE_CHOICES = listOf(
    Choice("movie", "Film", "Tek oturumda izle"),
    Choice("series", "Dizi", "Bölüm bölüm"),
    Choice("anime", "Anime", "Japon animasyonu"),
    Choice("any", "Fark etmez", "Hepsi karışık")
)

private val AUDIO_CHOICES = listOf(
    Choice("dub", "Dublaj", "Türkçe seslendirme"),
    Choice("original", "Orijinal", "Kendi dili"),
    Choice("any", "Fark etmez", "Hepsi")
)

private val MOOD_CHOICES = listOf(
    Choice("exciting", "Heyecanlı", "Aksiyon, gerilim"),
    Choice("emotional", "Duygusal", "Dram, romantik"),
    Choice("fun", "Eğlenceli", "Komedi, macera"),
    Choice("dark", "Karanlık", "Suç, gizem"),
    Choice("any", "Fark etmez", "Hepsi")
)

private val STEP_LABELS = listOf("Tür", "Ses", "Ruh hali", "Kategori")

@Composable
fun DiscoverScreen(onItemClick: (MediaCard) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication

    var step by remember { mutableIntStateOf(0) }
    var type by remember { mutableStateOf("any") }
    var audio by remember { mutableStateOf("any") }
    var mood by remember { mutableStateOf("any") }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }

    var results by remember { mutableStateOf<List<MediaCard>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitTick by remember { mutableIntStateOf(0) }

    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(submitTick) {
        if (submitTick == 0) return@LaunchedEffect
        loading = true
        error = null
        val mediaType = when (type) {
            "movie" -> MediaType.MOVIE
            "series", "anime" -> MediaType.SERIES
            else -> null
        }
        try {
            val found = app.content.recommend(mediaType, genres, 36, audio = audio, mood = mood)
            results = found
            if (found.isEmpty() && audio != "any") {
                error = "Bu seçimde uygun ses seçeneği bulunan içerik yok. Seçimleri değiştir."
            }
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            throw cancel
        } catch (failure: Throwable) {
            results = emptyList()
            error = failure.message ?: "Öneriler hazırlanamadı."
        }
        loading = false
    }

    fun submit() {
        results = emptyList()
        loading = true
        error = null
        submitTick++
    }

    val current = results
    if (current != null) {
        ResultGrid(
            items = current,
            loading = loading,
            error = error,
            summary = summary(type, audio, mood, genres),
            onRetry = { submitTick++ },
            onRestart = {
                results = null
                loading = false
                error = null
                step = 0
            },
            onItemClick = onItemClick
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        LaunchedEffect(step) {
            delay(80)
            runCatching { firstFocus.requestFocus() }
        }
        if (wide) {
            val railWidth = (maxWidth * 0.30f).coerceIn(272.dp, 348.dp)
            Row(modifier = Modifier.fillMaxSize()) {
                DiscoverRail(
                    step = step,
                    type = type,
                    audio = audio,
                    mood = mood,
                    genres = genres,
                    modifier = Modifier
                        .width(railWidth)
                        .fillMaxHeight()
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 26.dp, end = 30.dp, top = 22.dp, bottom = 18.dp)
                ) {
                    DiscoverQuestion(step = step, compact = false)
                    Spacer(modifier = Modifier.height(14.dp))
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        DiscoverOptions(
                            step = step,
                            type = type,
                            audio = audio,
                            mood = mood,
                            genres = genres,
                            firstFocus = firstFocus,
                            onType = { type = it },
                            onAudio = { audio = it },
                            onMood = { mood = it },
                            onGenre = { slug -> genres = toggleGenre(genres, slug) }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    DiscoverActions(
                        step = step,
                        onBack = { if (step > 0) step-- },
                        onNext = { if (step < 3) step++ else submit() }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = screenPadding(),
                        end = screenPadding(),
                        top = 12.dp,
                        bottom = 14.dp
                    )
            ) {
                DiscoverHeader(step = step)
                Spacer(modifier = Modifier.height(10.dp))
                DiscoverQuestion(step = step, compact = true)
                Spacer(modifier = Modifier.height(10.dp))
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    DiscoverOptions(
                        step = step,
                        type = type,
                        audio = audio,
                        mood = mood,
                        genres = genres,
                        firstFocus = firstFocus,
                        onType = { type = it },
                        onAudio = { audio = it },
                        onMood = { mood = it },
                        onGenre = { slug -> genres = toggleGenre(genres, slug) }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                DiscoverActions(
                    step = step,
                    onBack = { if (step > 0) step-- },
                    onNext = { if (step < 3) step++ else submit() }
                )
            }
        }
    }
}

private fun toggleGenre(current: List<String>, slug: String): List<String> = when {
    slug in current -> current - slug
    current.size >= 3 -> current
    else -> current + slug
}

@Composable
private fun DiscoverRail(
    step: Int,
    type: String,
    audio: String,
    mood: String,
    genres: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1D0B0F), Color(0xFF120A0C), NotflixColors.Bg)
                )
            )
            .padding(horizontal = 22.dp, vertical = 24.dp)
    ) {
        Text(
            text = "TAVSİYE ROBOTU",
            color = NotflixColors.AccentSoft,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Ne izlesem?",
            style = MaterialTheme.typography.displaySmall,
            color = NotflixColors.TextPrimary,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Birkaç soruya cevap ver; zevkine göre film, dizi ve anime önerelim. Üyelik gerekmez, 30 saniye sürer.",
            color = NotflixColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(22.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            STEP_LABELS.forEachIndexed { index, label ->
                RailStep(
                    index = index,
                    label = label,
                    value = stepValue(index, type, audio, mood, genres),
                    active = index == step,
                    done = index < step
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = "ŞU AN",
                    color = NotflixColors.AccentSoft,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = summary(type, audio, mood, genres),
                    color = NotflixColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun RailStep(
    index: Int,
    label: String,
    value: String,
    active: Boolean,
    done: Boolean
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when {
                        active -> NotflixColors.Accent
                        done -> NotflixColors.Accent.copy(alpha = 0.20f)
                        else -> Color.White.copy(alpha = 0.07f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (done) "✓" else "${index + 1}",
                color = when {
                    active -> Color.White
                    done -> NotflixColors.AccentSoft
                    else -> NotflixColors.TextMuted
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (active) NotflixColors.TextPrimary else NotflixColors.TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
            )
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    color = NotflixColors.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DiscoverHeader(step: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Tavsiye Robotu",
                style = MaterialTheme.typography.headlineSmall,
                color = NotflixColors.TextPrimary
            )
            Text(
                text = "Ne izlesem? Birkaç soru, 30 saniye.",
                color = NotflixColors.TextMuted,
                fontSize = 11.sp
            )
        }
        val shape = RoundedCornerShape(50)
        Box(
            modifier = Modifier
                .clip(shape)
                .background(NotflixColors.Accent.copy(alpha = 0.14f))
                .border(1.dp, NotflixColors.Accent.copy(alpha = 0.45f), shape)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = "${step + 1}/4 · ${STEP_LABELS[step]}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DiscoverQuestion(step: Int, compact: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        StepBar(step = step)
        Spacer(modifier = Modifier.height(if (compact) 10.dp else 16.dp))
        Text(
            text = when (step) {
                0 -> "Ne izlemek istersin?"
                1 -> "Ses tercihin ne?"
                2 -> "Bugünkü ruh halin?"
                else -> "Hangi kategoriler?"
            },
            style = if (compact) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.headlineSmall,
            color = NotflixColors.TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = when (step) {
                0 -> "Film mi dizi mi, karar ver."
                1 -> "Dublaj mı orijinal mi?"
                2 -> "Nasıl bir his arıyorsun?"
                else -> "En fazla üç tür seçebilirsin. Boş bırakırsan karışık gelir."
            },
            color = NotflixColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun StepBar(step: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            index < step -> NotflixColors.Accent.copy(alpha = 0.55f)
                            index == step -> NotflixColors.Accent
                            else -> Color.White.copy(alpha = 0.08f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun DiscoverOptions(
    step: Int,
    type: String,
    audio: String,
    mood: String,
    genres: List<String>,
    firstFocus: FocusRequester,
    onType: (String) -> Unit,
    onAudio: (String) -> Unit,
    onMood: (String) -> Unit,
    onGenre: (String) -> Unit
) {
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 620.dp) 3 else 2
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
        ) {
            when (step) {
                0 -> ChoiceGrid(
                    choices = TYPE_CHOICES,
                    selected = type,
                    columns = columns,
                    firstFocus = firstFocus,
                    onSelect = onType
                )
                1 -> ChoiceGrid(
                    choices = AUDIO_CHOICES,
                    selected = audio,
                    columns = columns,
                    firstFocus = firstFocus,
                    onSelect = onAudio
                )
                2 -> ChoiceGrid(
                    choices = MOOD_CHOICES,
                    selected = mood,
                    columns = columns,
                    firstFocus = firstFocus,
                    onSelect = onMood
                )
                else -> GenreGrid(
                    selected = genres,
                    columns = columns,
                    firstFocus = firstFocus,
                    onToggle = onGenre
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DiscoverActions(
    step: Int,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (step > 0) {
            NotflixButton(text = "Geri", primary = false, onClick = onBack)
        }
        NotflixButton(
            text = if (step < 3) "Devam  →" else "Önerileri Getir",
            modifier = Modifier.weight(1f),
            onClick = onNext
        )
    }
}

@Composable
private fun ChoiceGrid(
    choices: List<Choice>,
    selected: String,
    columns: Int,
    firstFocus: FocusRequester,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { index, choice ->
                    ChoiceTile(
                        choice = choice,
                        selected = selected == choice.key,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (rowIndex == 0 && index == 0) Modifier
                                    .focusRequester(firstFocus) else Modifier
                            ),
                        onClick = { onSelect(choice.key) }
                    )
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ChoiceTile(
    choice: Choice,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(if (isTvLayout()) 76.dp else 70.dp)
            .clip(shape)
            .background(
                when {
                    selected -> Brush.horizontalGradient(
                        listOf(
                            NotflixColors.Accent.copy(alpha = 0.26f),
                            NotflixColors.AccentDeep.copy(alpha = 0.18f)
                        )
                    )
                    focused -> Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.04f))
                    )
                    else -> Brush.horizontalGradient(
                        listOf(NotflixColors.Elevated, NotflixColors.Elevated)
                    )
                }
            )
            .border(
                width = if (focused || selected) 1.5.dp else 1.dp,
                color = when {
                    focused -> Color.White.copy(alpha = 0.75f)
                    selected -> NotflixColors.AccentSoft
                    else -> Color.White.copy(alpha = 0.07f)
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (selected) NotflixColors.Accent else Color.White.copy(alpha = 0.06f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (selected) "✓" else choice.label.take(1),
                color = if (selected) Color.White else NotflixColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = choice.label,
                color = if (selected || focused) Color.White else NotflixColors.TextSecondary,
                fontSize = if (isTvLayout()) 14.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (choice.hint.isNotBlank()) {
                Text(
                    text = choice.hint,
                    color = NotflixColors.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GenreGrid(
    selected: List<String>,
    columns: Int,
    firstFocus: FocusRequester,
    onToggle: (String) -> Unit
) {
    val genres = NOTFLIX_GENRES.filter { it.slug != null }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.chunked(columns).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { index, genre ->
                    val slug = genre.slug.orEmpty()
                    GenreChip(
                        label = genre.label,
                        selected = slug in selected,
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (rowIndex == 0 && index == 0) Modifier
                                    .focusRequester(firstFocus) else Modifier
                            ),
                        onClick = { onToggle(slug) }
                    )
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GenreChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(if (isTvLayout()) 46.dp else 42.dp)
            .clip(shape)
            .background(
                when {
                    selected -> NotflixColors.Accent.copy(alpha = 0.22f)
                    focused -> Color.White.copy(alpha = 0.07f)
                    else -> NotflixColors.Elevated
                }
            )
            .border(
                width = if (focused || selected) 1.5.dp else 1.dp,
                color = when {
                    focused -> Color.White.copy(alpha = 0.75f)
                    selected -> NotflixColors.AccentSoft
                    else -> Color.White.copy(alpha = 0.07f)
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (selected) "✓ $label" else label,
            color = if (selected || focused) Color.White else NotflixColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ResultGrid(
    items: List<MediaCard>,
    loading: Boolean,
    error: String?,
    summary: String,
    onRetry: () -> Unit,
    onRestart: () -> Unit,
    onItemClick: (MediaCard) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty()) {
            delay(120)
            runCatching { firstFocus.requestFocus() }
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
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sana önerdiklerimiz",
                    style = MaterialTheme.typography.headlineSmall,
                    color = NotflixColors.TextPrimary
                )
                Text(
                    text = summary,
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp
                )
            }
            NotflixButton(text = "Yeniden Başlat", primary = false, onClick = onRestart)
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> LoadingState("Zevkine göre seçiliyor…")
                error != null && items.isEmpty() -> ErrorState(error) { onRetry() }
                items.isEmpty() -> ErrorState("Uygun yapım bulunamadı. Seçimleri değiştir.") {
                    onRestart()
                }
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
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "${items.size} yapım",
                            color = NotflixColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    itemsIndexed(items, key = { _, card -> card.url }) { index, card ->
                        PosterCard(
                            card = card,
                            onClick = { onItemClick(card) },
                            width = null,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus)
                            else Modifier
                        )
                    }
                }
            }
        }
    }
}

private fun stepValue(
    index: Int,
    type: String,
    audio: String,
    mood: String,
    genres: List<String>
): String = when (index) {
    0 -> if (type == "any") "" else TYPE_CHOICES.firstOrNull { it.key == type }?.label.orEmpty()
    1 -> if (audio == "any") "" else AUDIO_CHOICES.firstOrNull { it.key == audio }?.label.orEmpty()
    2 -> if (mood == "any") "" else MOOD_CHOICES.firstOrNull { it.key == mood }?.label.orEmpty()
    else -> genres.joinToString(", ") { slug ->
        NOTFLIX_GENRES.firstOrNull { it.slug == slug }?.label ?: slug
    }
}

private fun summary(type: String, audio: String, mood: String, genres: List<String>): String {
    val typeLabel = TYPE_CHOICES.firstOrNull { it.key == type }?.label ?: "Fark etmez"
    val audioLabel = AUDIO_CHOICES.firstOrNull { it.key == audio }?.label.orEmpty()
    val moodLabel = MOOD_CHOICES.firstOrNull { it.key == mood }?.label.orEmpty()
    val genreLabels = NOTFLIX_GENRES.filter { it.slug in genres }.joinToString(", ") { it.label }
    return listOf(typeLabel, audioLabel, moodLabel, genreLabels)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}
