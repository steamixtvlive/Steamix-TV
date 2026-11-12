package com.notflix.app.ui.screens.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.BuildConfig
import com.notflix.app.data.ApkInstaller
import com.notflix.app.data.UpdateInfo
import com.notflix.app.ui.components.NotflixButton
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.launch

@Composable
fun UpdateGateScreen(info: UpdateInfo, onRetryCheck: () -> Unit) {
    val context = LocalContext.current
    val installer = remember { ApkInstaller(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B0A0D), NotflixColors.Bg, NotflixColors.BgDeep)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NOTFLİX",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NotflixColors.Accent.copy(alpha = 0.16f))
                    .border(1.dp, NotflixColors.Accent.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "GÜNCELLEME GEREKLİ",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Devam etmek için yeni sürümü kur",
                style = MaterialTheme.typography.headlineSmall,
                color = NotflixColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Yeni sürüm hazır: ${info.versionName} · sende ${BuildConfig.VERSION_NAME}",
                color = NotflixColors.TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            if (info.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = info.notes,
                        color = NotflixColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (downloading) {
                    CircularProgressIndicator(
                        color = NotflixColors.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                }
                NotflixButton(
                    text = if (downloading) "İndiriliyor %$progress" else "İndir ve Kur",
                    onClick = {
                        if (!downloading) {
                            // Kurulum izni yoksa once otomatik olarak ayar ekranini ac.
                            if (!installer.canInstallPackages()) {
                                installer.openInstallPermissionSettings()
                                error = "Kurulum izni açıldı. İzin verdikten sonra tekrar 'İndir ve Kur' de."
                                return@NotflixButton
                            }
                            downloading = true
                            progress = 0
                            error = null
                            scope.launch {
                                runCatching {
                                    val file = installer.download(info.apkUrl) { percent ->
                                        progress = percent
                                    }
                                    installer.install(file)
                                }.onFailure {
                                    error = "İndirilemedi: ${it.message ?: "bilinmeyen hata"}"
                                }
                                downloading = false
                            }
                        }
                    }
                )
            }
            error?.let { text ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = text,
                    color = NotflixColors.AccentSoft,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (installer.canInstallPackages()) {
                    "Kurulum ekranı açılmazsa 'Bilinmeyen kaynaklara izin ver' seçeneğini aç."
                } else {
                    "Kurulum için 'Bilinmeyen kaynaklara izin ver' gerekiyor. 'İndir ve Kur' deyince izni açan ekran gelir."
                },
                color = NotflixColors.TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Tekrar dene",
                color = NotflixColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onRetryCheck)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
