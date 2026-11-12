package com.notflix.app.ui.brand

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.fontResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.notflix.app.R
import com.notflix.app.ui.theme.NotflixColors

private const val WORDMARK = "NOTFLİX"

@Composable
fun NotflixWordmark(
    modifier: Modifier = Modifier,
    width: Dp = 180.dp,
    height: Dp = (width.value / 3.09f).dp,
    color: Color = NotflixColors.Accent,
    glow: Boolean = true,
    sweep: Float? = null
) {
    val context = LocalContext.current
    val typeface = remember {
        ResourcesCompat.getFont(context, R.font.bebas_neue) ?: Typeface.DEFAULT_BOLD
    }
    Canvas(modifier = modifier.width(width).height(height).clipToBounds()) {
        val w = size.width
        val h = size.height
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = color.toArgb()
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.05f
            textSize = h * 0.95f
        }
        val measured = textPaint.measureText(WORDMARK)
        if (measured > 0f) {
            textPaint.textSize *= (w * 0.92f) / measured
        }
        val path = Path().apply {
            moveTo(w * 0.04f, h * 0.95f)
            quadraticBezierTo(w * 0.5f, h * 0.70f, w * 0.96f, h * 0.95f)
        }
        val androidPath = path.asAndroidPath()

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            if (glow) {
                val glowPaint = Paint(textPaint).apply {
                    maskFilter = BlurMaskFilter(w * 0.045f, BlurMaskFilter.Blur.NORMAL)
                    alpha = 200
                }
                native.drawTextOnPath(WORDMARK, androidPath, 0f, 0f, glowPaint)
                val softGlow = Paint(textPaint).apply {
                    maskFilter = BlurMaskFilter(w * 0.11f, BlurMaskFilter.Blur.NORMAL)
                    alpha = 70
                }
                native.drawTextOnPath(WORDMARK, androidPath, 0f, 0f, softGlow)
            }
            native.drawTextOnPath(WORDMARK, androidPath, 0f, 0f, textPaint)

            if (sweep != null) {
                val bandWidth = w * 0.26f
                val left = sweep * (w + bandWidth * 2f) - bandWidth
                val sweepPaint = Paint(textPaint).apply {
                    this.color = Color.White.toArgb()
                    alpha = 150
                    maskFilter = BlurMaskFilter(w * 0.012f, BlurMaskFilter.Blur.NORMAL)
                }
                val save = native.save()
                native.clipRect(left, 0f, left + bandWidth, h)
                native.drawTextOnPath(WORDMARK, androidPath, 0f, 0f, sweepPaint)
                native.restoreToCount(save)
            }
        }
    }
}

@Composable
fun NotflixBloom(modifier: Modifier = Modifier, intensity: Float = 1f) {
    Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NotflixColors.Accent.copy(alpha = 0.20f * intensity),
                    NotflixColors.AccentDeep.copy(alpha = 0.10f * intensity),
                    Color.Transparent
                ),
                center = center,
                radius = size.minDimension * 0.9f
            ),
            radius = size.minDimension * 0.9f,
            center = center
        )
    }
}

@Composable
fun NotflixBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF16090B), NotflixColors.Bg, NotflixColors.Bg)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.width / 14f
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.025f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.018f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NotflixColors.Accent.copy(alpha = 0.11f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.05f),
                    radius = size.width * 0.55f
                ),
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.18f, size.height * 0.05f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NotflixColors.AccentDeep.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.92f, size.height * 0.85f),
                    radius = size.width * 0.45f
                ),
                radius = size.width * 0.45f,
                center = Offset(size.width * 0.92f, size.height * 0.85f)
            )
        }
        content()
    }
}

@Composable
fun NotflixRail(modifier: Modifier = Modifier, progress: Float) {
    Canvas(modifier = modifier) {
        val h = size.height.coerceAtLeast(1f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(NotflixColors.AccentDeep, NotflixColors.AccentSoft, NotflixColors.Accent)
            ),
            size = androidx.compose.ui.geometry.Size(size.width * progress.coerceIn(0f, 1f), size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f)
        )
    }
}
