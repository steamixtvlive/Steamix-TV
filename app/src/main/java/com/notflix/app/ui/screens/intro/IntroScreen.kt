package com.notflix.app.ui.screens.intro

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.ui.brand.NotflixBackdrop
import com.notflix.app.ui.brand.NotflixBloom
import com.notflix.app.ui.brand.NotflixWordmark
import com.notflix.app.ui.components.NotflixButton
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.launch

private data class IntroPage(
    val title: String,
    val text: String
)

private val pages = listOf(
    IntroPage(
        "NOTFLİX'e hoş geldiniz",
        "Filmler, diziler ve canlı TV tek yerde. Kendi oynatıcımız, sade ve hızlı bir arayüz."
    ),
    IntroPage(
        "Her ekranda çalışır",
        "TV box, Google TV, akıllı TV, BlueStacks ve telefon. Uzaktan kumanda, air mouse, fare ve dokunmatik desteklenir."
    ),
    IntroPage(
        "Telefonu yatay çevir",
        "Ekran ölçeklenir; kartlar ve yazılar bulunduğun ekrana uyum sağlar. Dokunarak, kaydırarak ve tıklayarak kullan."
    ),
    IntroPage(
        "Modern HLS oynatıcı",
        "Yayın tam ekranda başlar. Kaldığın yerden devam eder; bağlantı tazelenir, takılma olursa kendini toparlar."
    ),
    IntroPage(
        "Canlı TV bizim",
        "Türkiye kanalları en başta. Yayınlar doğrudan oynar; iframe yok, tarayıcı yok. Liste internetten güncellenir."
    ),
    IntroPage(
        "Her gün yeni filmler",
        "Ana ekranda IMDb puanı yüksek filmler döner; fragmanı kendiliğinden açılır. Tüm API bizden, izlemesi sizden."
    ),
    IntroPage(
        "Hoş geldiniz",
        "Her şey hazır. NOTFLİX iyi seyirler diler."
    )
)

@Composable
fun IntroScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage

    NotflixBackdrop {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isTvLayout()) 32.dp else 18.dp,
                        end = if (isTvLayout()) 32.dp else 18.dp,
                        top = if (isTvLayout()) 18.dp else 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NotflixWordmark(width = if (isTvLayout()) 96.dp else 70.dp)
                Spacer(modifier = Modifier.weight(1f))
                SkipButton(onClick = onFinish)
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1
            ) { index ->
                IntroPageContent(index = index, modifier = Modifier.fillMaxSize())
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isTvLayout()) 32.dp else 18.dp,
                        vertical = if (isTvLayout()) 22.dp else 14.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pages.indices.forEach { dot ->
                        val selected = dot == page
                        Box(
                            modifier = Modifier
                                .width(if (selected) 20.dp else 6.dp)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) NotflixColors.Accent
                                    else Color.White.copy(alpha = 0.20f)
                                )
                                .pointerInput(dot) {
                                    detectTapGestures {
                                        scope.launch { pagerState.animateScrollToPage(dot) }
                                    }
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (page > 0) {
                    NotflixButton(
                        text = "Geri",
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(page - 1) }
                        },
                        primary = false,
                        icon = Icons.Filled.KeyboardArrowLeft
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                NotflixButton(
                    text = when (page) {
                        0 -> "Şimdi Başlayalım"
                        pages.lastIndex -> "Başla"
                        else -> "İleri"
                    },
                    onClick = {
                        if (page == pages.lastIndex) {
                            onFinish()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                        }
                    },
                    icon = if (page == pages.lastIndex) Icons.Filled.PlayArrow
                    else Icons.Filled.KeyboardArrowRight
                )
            }
        }
    }
}

@Composable
private fun IntroPageContent(index: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val page = pages[index]
        val artHeight = (maxHeight * 0.52f).coerceIn(110.dp, 280.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isTvLayout()) 40.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artHeight),
                contentAlignment = Alignment.Center
            ) {
                IntroArt(index = index)
            }
            Spacer(modifier = Modifier.height(if (isTvLayout()) 28.dp else 14.dp))
            Text(
                text = page.title,
                style = if (isTvLayout()) MaterialTheme.typography.displaySmall
                else MaterialTheme.typography.headlineMedium,
                color = NotflixColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = page.text,
                style = MaterialTheme.typography.bodyMedium,
                color = NotflixColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp)
            )
        }
    }
}

@Composable
private fun IntroArt(index: Int) {
    when (index) {
        0 -> WelcomeArt()
        1 -> DevicesArt()
        2 -> TouchArt()
        3 -> PlayerArt()
        4 -> LiveArt()
        5 -> DailyArt()
        else -> FinalArt()
    }
}

@Composable
private fun WelcomeArt() {
    val infinite = rememberInfiniteTransition(label = "welcome")
    val float by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "float"
    )
    Box(modifier = Modifier.size(width = 300.dp, height = 200.dp)) {
        PosterBlock(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-74).dp, y = 8.dp)
                .rotate(-9f),
            color = Color(0xFF1E293B)
        )
        PosterBlock(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 74.dp, y = 8.dp)
                .rotate(9f),
            color = Color(0xFF3F1D24)
        )
        PosterBlock(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = float.dp),
            color = Color(0xFF2A1215),
            highlighted = true
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(58.dp)
                .clip(CircleShape)
                .background(NotflixColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@Composable
private fun PosterBlock(
    modifier: Modifier,
    color: Color,
    highlighted: Boolean = false
) {
    Box(
        modifier = modifier
            .width(96.dp)
            .height(144.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    listOf(color, Color(0xFF0C0C0C))
                )
            )
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) NotflixColors.Accent.copy(alpha = 0.8f)
                else Color.White.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(36.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                )
        )
    }
}

@Composable
private fun DevicesArt() {
    val infinite = rememberInfiniteTransition(label = "devices")
    val glow by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "glow"
    )
    Box(modifier = Modifier.size(width = 320.dp, height = 190.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-18).dp)
                .width(208.dp)
                .height(124.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161616))
                .border(2.dp, NotflixColors.Accent.copy(alpha = glow), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            ColoredScreen(modifier = Modifier.fillMaxSize().padding(7.dp))
            Text(
                text = "NOTFLİX",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(x = (-18).dp, y = (-6).dp)
                .width(60.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.18f))
        )
        RemoteShape(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-14).dp, y = 18.dp)
        )
        PhoneShape(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 18.dp, y = (-4).dp)
        )
    }
}

@Composable
private fun ColoredScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2A0A0E), Color(0xFF120406), Color(0xFF0B0B0B))
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .width(90.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(NotflixColors.Accent.copy(alpha = 0.9f))
        )
    }
}

@Composable
private fun RemoteShape(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(38.dp)
            .height(98.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1C1C1C))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(NotflixColors.Accent)
        )
        repeat(3) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
            )
        }
        Box(
            modifier = Modifier
                .width(18.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Color.White.copy(alpha = 0.08f))
        )
    }
}

@Composable
private fun PhoneShape(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(82.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF171717))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3A0D12), Color(0xFF0D0D0D))))
        )
    }
}

@Composable
private fun TouchArt() {
    val infinite = rememberInfiniteTransition(label = "touch")
    val ripple by infinite.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "ripple"
    )
    val tilt by infinite.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "tilt"
    )
    Box(
        modifier = Modifier
            .size(width = 300.dp, height = 180.dp)
            .rotate(tilt),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(232.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF151515))
                .border(2.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            ColoredScreen(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = 0.7f + ripple * 0.6f
                        scaleY = 0.7f + ripple * 0.6f
                        alpha = 1f - ripple
                    }
                    .clip(CircleShape)
                    .border(2.dp, NotflixColors.Accent, CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(NotflixColors.Accent)
            )
        }
    }
}

@Composable
private fun PlayerArt() {
    val infinite = rememberInfiniteTransition(label = "player")
    val pulse by infinite.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse"
    )
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    Box(modifier = Modifier.size(width = 300.dp, height = 180.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(94.dp)
                .graphicsLayer {
                    scaleX = pulse
                    scaleY = pulse
                }
                .clip(CircleShape)
                .background(NotflixColors.Accent.copy(alpha = 0.12f))
                .border(1.dp, NotflixColors.Accent.copy(alpha = 0.5f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(NotflixColors.Accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barWidth = 5.dp.toPx()
            val gap = 7.dp.toPx()
            val totalWidth = barWidth * 13 + gap * 12
            var x = (size.width - totalWidth) / 2f
            repeat(13) { index ->
                val wave = kotlin.math.abs(kotlin.math.sin(phase + index * 0.55f))
                val height = (10.dp.toPx() + wave * 28.dp.toPx())
                drawRoundRect(
                    color = if (index % 3 == 0) NotflixColors.Accent
                    else Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(x, size.height - 22.dp.toPx() - height),
                    size = androidx.compose.ui.geometry.Size(barWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
                )
                x += barWidth + gap
            }
        }
    }
}

@Composable
private fun LiveArt() {
    val infinite = rememberInfiniteTransition(label = "live")
    val shine by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1900), RepeatMode.Reverse),
        label = "shine"
    )
    val labels = listOf("TRT", "ATV", "NOW", "TV8", "HABER", "SPOR")
    Column(
        modifier = Modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        labels.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEachIndexed { index, label ->
                    val highlighted = rowIndex == 0 && index == 0
                    Box(
                        modifier = Modifier
                            .width(94.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (highlighted) NotflixColors.Accent.copy(alpha = 0.16f)
                                else Color(0xFF161616)
                            )
                            .border(
                                if (highlighted) 1.5.dp else 1.dp,
                                if (highlighted) NotflixColors.Accent.copy(alpha = shine)
                                else Color.White.copy(alpha = 0.09f),
                                RoundedCornerShape(9.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (highlighted) Color.White else NotflixColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyArt() {
    val infinite = rememberInfiniteTransition(label = "daily")
    val float by infinite.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse),
        label = "dailyFloat"
    )
    val glow by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "dailyGlow"
    )
    Box(
        modifier = Modifier.size(width = 330.dp, height = 200.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(176.dp)
                .graphicsLayer { alpha = glow * 0.42f }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(NotflixColors.Accent, Color.Transparent)
                    )
                )
        )
        PosterBlock(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-86).dp, y = 4.dp)
                .rotate(-12f + float * 0.35f),
            color = Color(0xFF16233A)
        )
        PosterBlock(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 86.dp, y = 4.dp)
                .rotate(12f - float * 0.35f),
            color = Color(0xFF35201A)
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = float.dp)
                .width(112.dp)
                .height(168.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF4A131B), Color(0xFF1B0A0D), Color(0xFF0B0B0B))
                    )
                )
                .border(2.dp, NotflixColors.Accent.copy(alpha = 0.85f), RoundedCornerShape(11.dp))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                )
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.35f))
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFF5C518))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "IMDb 8.6",
                    color = Color.Black,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "FRAGMAN",
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = 10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.72f))
                .border(1.dp, NotflixColors.Accent.copy(alpha = glow), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NotflixColors.Accent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Her gün yeni",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FinalArt() {
    val infinite = rememberInfiniteTransition(label = "final")
    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    Box(modifier = Modifier.size(width = 320.dp, height = 180.dp), contentAlignment = Alignment.Center) {
        NotflixBloom(modifier = Modifier.fillMaxSize())
        NotflixWordmark(width = 250.dp, sweep = sweep)
    }
}

@Composable
private fun SkipButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                if (focused) 1.5.dp else 1.dp,
                if (focused) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(50)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Geç",
            color = NotflixColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
