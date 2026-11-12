package com.notflix.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.ui.theme.NotflixColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** App-wide volume level indicator. Replaces the Android system volume panel. */
object VolumeHud {

    internal data class Pulse(val level: Float, val tick: Long)

    internal var pulse by mutableStateOf<Pulse?>(null)
        private set

    fun show(level: Float) {
        pulse = Pulse(level.coerceIn(0f, 1f), System.nanoTime())
    }
}

@Composable
fun VolumeOverlay() {
    val current = VolumeHud.pulse
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(current?.tick) {
        if (current == null) return@LaunchedEffect
        visible = true
        delay(2_500)
        visible = false
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible && current != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val level = current?.level ?: 0f
            Column(
                modifier = Modifier
                    .width(196.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.74f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = when {
                        level <= 0.001f -> Icons.Filled.VolumeMute
                        level < 0.5f -> Icons.Filled.VolumeDown
                        else -> Icons.Filled.VolumeUp
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = "${(level * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(level)
                            .height(4.dp)
                            .background(NotflixColors.Accent)
                    )
                }
                Text(
                    text = "Ses",
                    color = NotflixColors.TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
