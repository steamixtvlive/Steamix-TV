package com.notflix.app.ui.screens.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.ui.brand.NotflixBackdrop
import com.notflix.app.ui.brand.NotflixBloom
import com.notflix.app.ui.brand.NotflixRail
import com.notflix.app.ui.brand.NotflixWordmark
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SplashScreen(onDone: () -> Unit) {
    var finished by remember { mutableStateOf(false) }
    var startedAt by remember { mutableStateOf(System.currentTimeMillis()) }

    val finishOnce = {
        if (!finished) {
            finished = true
            onDone()
        }
    }

    LaunchedEffect(Unit) {
        startedAt = System.currentTimeMillis()
        delay(4200)
        finishOnce()
    }

    val sweepTransition = rememberInfiniteTransition(label = "splashSweep")
    val sweep by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val railTransition = rememberInfiniteTransition(label = "splashRail")
    val railPulse by railTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rail"
    )
    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 900),
        label = "reveal"
    )

    NotflixBackdrop {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (System.currentTimeMillis() - startedAt > 900) finishOnce()
                    }
                }
        ) {
            SplashSkipButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (onTv()) 32.dp else 18.dp),
                onClick = finishOnce
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val tv = onTv()
                val markWidth = minOf(maxWidth * 0.52f, maxHeight * 0.44f)
                    .coerceIn(150.dp, 460.dp)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.graphicsLayer {
                            alpha = reveal
                            translationY = (1f - reveal) * 26f
                        }
                    ) {
                        NotflixBloom(
                            modifier = Modifier
                                .width(markWidth * 1.24f)
                                .height(markWidth * 0.32f),
                            intensity = 0.55f
                        )
                        NotflixWordmark(
                            width = markWidth,
                            glow = true,
                            sweep = sweep
                        )
                    }
                    Spacer(modifier = Modifier.height(if (tv) 34.dp else 32.dp))
                    Text(
                        text = "İZLE, KEŞFET, YAŞA",
                        color = NotflixColors.TextSecondary,
                        fontSize = if (tv) 14.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = if (tv) 5.sp else 3.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = if (onTv()) 40.dp else 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NotflixRail(
                    modifier = Modifier
                        .width(if (onTv()) 260.dp else 180.dp)
                        .height(2.dp),
                    progress = railPulse
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Yayın çizgileri hazırlanıyor…",
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}

@Composable
private fun SplashSkipButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) ColorWhite(0.7f) else ColorWhite(0.18f),
                shape = RoundedCornerShape(50)
            )
            .background(ColorWhite(if (focused) 0.08f else 0.03f), RoundedCornerShape(50))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = "ATLA",
            color = NotflixColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
private fun onTv(): Boolean {
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    return config.smallestScreenWidthDp >= 600
}

private fun ColorWhite(alpha: Float) = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha)
