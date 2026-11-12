package com.notflix.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.notflix.app.data.LiveChannel
import com.notflix.app.data.MediaCard
import com.notflix.app.data.MediaType
import com.notflix.app.ui.brand.NotflixWordmark
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.launch

data class NavTab(val id: String, val label: String)

@Composable
fun Modifier.focusBringIntoView(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusChanged {
            if (it.isFocused) {
                scope.launch { runCatching { requester.bringIntoView() } }
            }
        }
}

@Composable
fun isTvLayout(): Boolean {
    val config = LocalConfiguration.current
    return config.smallestScreenWidthDp >= 600 ||
        (config.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >=
        android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
}

@Composable
fun screenPadding(): Dp = if (isTvLayout()) 32.dp else 16.dp

@Composable
fun NotflixTopBar(
    tabs: List<NavTab>,
    currentTab: String,
    onTabSelected: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onLogoClick: () -> Unit
) {
    val tv = isTvLayout()
    val pad = screenPadding()
    val tabScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF20A0A0A))
    ) {
        // Sekmeler sigmazsa kaydirma oklari gosterilir.
        val available = maxWidth - (if (tv) 320.dp else 210.dp)
        val overflow = tabs.sumOf { it.label.length } * (if (tv) 9 else 7) > available.value.toInt()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (tv) 68.dp else 58.dp)
                .padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var logoFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (logoFocused) Color.White.copy(alpha = 0.06f) else Color.Transparent)
                    .onFocusChanged { logoFocused = it.isFocused }
                    .clickable(onClick = onLogoClick)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                NotflixWordmark(width = if (tv) 104.dp else 72.dp, glow = true)
            }
            Spacer(modifier = Modifier.width(if (tv) 28.dp else 12.dp))
            if (overflow) {
                OverflowArrow(
                    icon = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "Sekmeleri geri kaydır",
                    onClick = { scope.launch { tabScroll.animateScrollBy(-220f) } }
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(tabScroll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (tv) 22.dp else 4.dp)
            ) {
                tabs.forEach { tab ->
                    TopTab(
                        label = tab.label,
                        selected = tab.id == currentTab,
                        onClick = { onTabSelected(tab.id) }
                    )
                }
            }
            if (overflow) {
                Spacer(modifier = Modifier.width(4.dp))
                OverflowArrow(
                    icon = Icons.Filled.KeyboardArrowRight,
                    contentDescription = "Sekmeleri ileri kaydır",
                    onClick = { scope.launch { tabScroll.animateScrollBy(220f) } }
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconAction(Icons.Outlined.Search, "Ara", onSearch)
            Spacer(modifier = Modifier.width(2.dp))
            IconAction(Icons.Outlined.Settings, "Ayarlar", onSettings)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )
    }
}

/** Sekme listesi tasarsa gosterilen kaydirma oku. */
@Composable
private fun OverflowArrow(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(if (isTvLayout()) 34.dp else 28.dp)
            .clip(CircleShape)
            .background(
                if (focused) NotflixColors.Accent.copy(alpha = 0.55f)
                else Color.White.copy(alpha = 0.07f)
            )
            .border(
                1.dp,
                if (focused) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.14f),
                CircleShape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(if (isTvLayout()) 20.dp else 17.dp)
        )
    }
}

@Composable
private fun TopTab(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    val textColor by animateColorAsState(
        targetValue = when {
            selected -> Color.White
            focused -> Color.White
            // Secili olmayan sekmeler mat gri: secili ve odakli olandan
            // hemen ayirt edilebilsin.
            else -> NotflixColors.TextMuted
        },
        label = "tabColor"
    )
    Column(
        modifier = Modifier
            .focusBringIntoView()
            .clip(shape)
            .background(
                when {
                    // Secili sekme net gorunsun: beyaz zemin + belirgin cerceve.
                    selected -> Color.White.copy(alpha = 0.16f)
                    focused -> Color.White.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = when {
                    focused -> 2.dp
                    selected -> 1.5.dp
                    else -> 0.dp
                },
                color = when {
                    focused -> Color.White
                    selected -> Color.White.copy(alpha = 0.72f)
                    else -> Color.Transparent
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = if (isTvLayout()) 10.dp else 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = if (isTvLayout()) 14.sp else 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(if (selected) 26.dp else 0.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) NotflixColors.Accent else Color.Transparent
                )
        )
    }
}

@Composable
fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(if (isTvLayout()) 42.dp else 36.dp)
            .clip(CircleShape)
            .background(if (focused) Color.White.copy(alpha = 0.09f) else Color.Transparent)
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Color.White.copy(alpha = 0.85f) else Color.Transparent,
                CircleShape
            )
            .focusBringIntoView()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) Color.White else NotflixColors.TextSecondary,
            modifier = Modifier.size(if (isTvLayout()) 21.dp else 19.dp)
        )
    }
}

@Composable
fun ScreenTitleBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isTvLayout()) 68.dp else 58.dp)
            .padding(horizontal = screenPadding()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconAction(
            icon = Icons.Filled.KeyboardArrowLeft,
            contentDescription = "Geri",
            onClick = onBack
        )
        Spacer(modifier = Modifier.width(if (isTvLayout()) 14.dp else 8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = NotflixColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun PosterGrid(
    items: List<MediaCard>,
    onItemClick: (MediaCard) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(screenPadding()),
    minCellWidth: Dp = if (isTvLayout()) 148.dp else 104.dp
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCellWidth),
        modifier = modifier.fillMaxSize().focusGroup(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(if (isTvLayout()) 14.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(if (isTvLayout()) 18.dp else 14.dp)
    ) {
        items(items, key = { it.url }) { card ->
            PosterCard(
                card = card,
                onClick = { onItemClick(card) },
                width = null
            )
        }
    }
}

@Composable
fun NotflixButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (primary) NotflixColors.Accent else Color.White.copy(alpha = 0.07f)
    val fg = if (primary) Color.White else Color.White
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .border(
                width = if (focused) 2.dp else if (primary) 0.dp else 1.dp,
                color = if (focused) Color.White.copy(alpha = 0.85f)
                else if (primary) Color.Transparent else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(9.dp)
            )
            .focusBringIntoView()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun PosterCard(
    card: MediaCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = if (isTvLayout()) 166.dp else 132.dp
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "posterScale")
    val shape = RoundedCornerShape(11.dp)

    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .scale(scale)
            .focusBringIntoView()
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(NotflixColors.Card)
                .border(
                    // Odak vurgusu kirmizi ve net olsun: secilen poster hemen anlasilsin.
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) NotflixColors.Accent else Color.White.copy(alpha = 0.08f),
                    shape = shape
                )
        ) {
            AsyncImage(
                model = card.poster,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f)
                        )
                    )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (card.type == MediaType.MOVIE) Color(0xFF2563EB) else NotflixColors.Accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (card.type == MediaType.MOVIE) "Film" else "Dizi",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (card.rating != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.68f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = NotflixColors.Star,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = String.format("%.1f", card.rating),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            if (card.rank != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.74f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "#${card.rank}",
                        color = NotflixColors.Star,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NotflixColors.Accent.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = card.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) Color.White else NotflixColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val meta = listOfNotNull(card.year, if (card.type == MediaType.SERIES) "Dizi" else "Film")
            .joinToString(" · ")
        if (meta.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = NotflixColors.TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun MediaRow(
    title: String,
    items: List<MediaCard>,
    onItemClick: (MediaCard) -> Unit,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
    firstFocusChanged: ((Boolean) -> Unit)? = null
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = screenPadding(), vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = NotflixColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (onSeeAll != null) {
                Text(
                    text = "Tümünü Gör →",
                    color = NotflixColors.AccentSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .focusBringIntoView()
                        .clickable(onClick = onSeeAll)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        LazyRow(
            modifier = Modifier.focusGroup(),
            contentPadding = PaddingValues(horizontal = screenPadding()),
            horizontalArrangement = Arrangement.spacedBy(if (isTvLayout()) 14.dp else 10.dp)
        ) {
            itemsIndexed(items, key = { _, card -> card.url }) { index, card ->
                PosterCard(
                    card = card,
                    onClick = { onItemClick(card) },
                    modifier = Modifier
                        .then(
                            if (index == 0 && firstFocus != null) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            }
                        )
                        .then(
                            if (index == 0 && firstFocusChanged != null) {
                                Modifier.onFocusChanged { firstFocusChanged(it.hasFocus) }
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
        Spacer(modifier = Modifier.height(if (isTvLayout()) 26.dp else 20.dp))
    }
}

@Composable
fun LiveChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillCell: Boolean = false,
    nowMs: Long = 0L,
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val program = remember(channel, nowMs) {
        channel.programs.firstOrNull { nowMs in it.startMs until it.endMs }
    }
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .then(
                if (fillCell) Modifier.fillMaxWidth()
                else Modifier.width(if (isTvLayout()) 190.dp else 156.dp)
            )
            .focusBringIntoView()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTvLayout()) 104.dp else 88.dp)
                .clip(shape)
                .background(NotflixColors.Card)
                .border(
                    if (focused) 2.5.dp else 1.dp,
                    if (focused) Color.White else Color.White.copy(alpha = 0.07f),
                    shape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.03f),
                            1f to Color.Black.copy(alpha = 0.18f)
                        )
                    )
            )
            if (channel.logo.isNotBlank()) {
                AsyncImage(
                    model = channel.logo,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isTvLayout()) 13.dp else 10.dp)
                )
            } else {
                Text(
                    text = channel.name,
                    color = NotflixColors.TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(NotflixColors.Accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            if (onToggleFavorite != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(if (isTvLayout()) 30.dp else 26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, if (favorite) NotflixColors.Accent else Color.White.copy(alpha = 0.18f), CircleShape)
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (favorite) "Favorilerden çıkar" else "Favorilere ekle",
                        tint = if (favorite) NotflixColors.Accent else Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(if (isTvLayout()) 17.dp else 15.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused) Color.White else NotflixColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = program?.title ?: channel.group,
            style = MaterialTheme.typography.labelSmall,
            color = if (program != null) NotflixColors.AccentSoft else NotflixColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (program != null) {
            val total = (program.endMs - program.startMs).coerceAtLeast(1L)
            val progress = ((nowMs - program.startMs).toFloat() / total).coerceIn(0f, 1f)
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(NotflixColors.Accent)
                )
            }
        }
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    selected -> NotflixColors.Accent
                    focused -> Color.White.copy(alpha = 0.10f)
                    else -> NotflixColors.Elevated
                }
            )
            .border(
                width = if (focused && !selected) 2.dp else 1.dp,
                color = when {
                    selected -> NotflixColors.Accent
                    focused -> Color.White.copy(alpha = 0.90f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusBringIntoView()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else NotflixColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun CircularLoading(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            color = NotflixColors.Accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(text = label, color = NotflixColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
fun LoadingState(message: String = "Yükleniyor…") {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NotflixWordmark(
            width = if (isTvLayout()) 132.dp else 96.dp,
            glow = true
        )
        Spacer(modifier = Modifier.height(if (isTvLayout()) 22.dp else 16.dp))
        CircularProgressIndicator(
            color = NotflixColors.Accent,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = message, color = NotflixColors.TextMuted, fontSize = 13.sp)
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bağlantı kurulamadı",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = NotflixColors.TextMuted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(18.dp))
            NotflixButton(text = "Tekrar Dene", onClick = onRetry)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = NotflixColors.TextMuted, fontSize = 14.sp)
    }
}

/**
 * Kitaplik ve Devam Et listelerindeki tek tek / toplu silme dugmesi.
 * Poster uzerine binmedigi icin kumanda ve klavye odagi her zaman ulasir.
 */
@Composable
fun DeleteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Sil"
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(if (focused) Color(0xFFC62828) else Color(0xFF3A1416))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0xFF8E2F35),
                shape = shape
            )
            .focusBringIntoView()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
