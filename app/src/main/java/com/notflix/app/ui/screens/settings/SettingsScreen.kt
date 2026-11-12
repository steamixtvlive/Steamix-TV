package com.notflix.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import com.notflix.app.BuildConfig
import com.notflix.app.NotflixApplication
import com.notflix.app.data.UpdateResult
import com.notflix.app.ui.components.ScreenTitleBar
import com.notflix.app.ui.components.isTvLayout
import com.notflix.app.ui.components.screenPadding
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReplayIntro: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication
    val settings = app.settings
    val scope = rememberCoroutineScope()

    var message by remember { mutableStateOf<String?>(null) }

    var resume by remember { mutableStateOf(settings.resumeEnabled) }

    var liveRefreshBusy by remember { mutableStateOf(false) }
    var cacheBusy by remember { mutableStateOf(false) }
    var updateBusy by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenTitleBar(
            title = "Ayarlar",
            onBack = onBack,
            trailing = {
                Text(
                    text = "sürüm ${BuildConfig.VERSION_NAME}",
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 36.dp)
        ) {
            message?.let { text ->
                item(key = "message") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = screenPadding(), vertical = 7.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(NotflixColors.Accent.copy(alpha = 0.12f))
                            .border(
                                1.dp,
                                NotflixColors.Accent.copy(alpha = 0.35f),
                                RoundedCornerShape(9.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                text = text,
                                color = NotflixColors.TextPrimary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Kapat",
                                color = NotflixColors.AccentSoft,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .clickable { message = null }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            item(key = "play-head") { SectionHeader("Oynatma") }
            item(key = "play-card") {
                SettingsCard {
                    ToggleRow(
                        title = "Kaldığın yerden devam et",
                        subtitle = "Kaldığın yer hatırlanır.",
                        checked = resume,
                        onToggle = {
                            resume = !resume
                            settings.resumeEnabled = resume
                        }
                    )
                    ActionRow(
                        title = "İzleme geçmişini temizle",
                        subtitle = "Kayıtlı yerler silinir.",
                        onClick = {
                            settings.clearPositions()
                            message = "İzleme geçmişi temizlendi."
                        }
                    )
                }
            }

            item(key = "content-head") { SectionHeader("Uygulama ve yayınlar") }
            item(key = "content-card") {
                SettingsCard {
                    ActionRow(
                        title = "Siteyi aç",
                        subtitle = "Tarayıcıda açılır.",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://steamixlive.onrender.com")
                                    )
                                )
                            }.onFailure {
                                message = "Tarayıcı açılamadı."
                            }
                        }
                    )
                    ActionRow(
                        title = "Kanalları yenile",
                        subtitle = "Kanal listesi güncellenir.",
                        busy = liveRefreshBusy,
                        onClick = {
                            if (!liveRefreshBusy) {
                                liveRefreshBusy = true
                                message = null
                                scope.launch {
                                    val channels = app.live.refresh()
                                    message = if (channels != null) {
                                        "Kanal listesi güncellendi: ${channels.size} kanal."
                                    } else {
                                        "Kanal listesi güncellenemedi. İnterneti kontrol et."
                                    }
                                    liveRefreshBusy = false
                                }
                            }
                        }
                    )
                    ActionRow(
                        title = "Görselleri temizle",
                        subtitle = "Afişler yeniden indirilir.",
                        busy = cacheBusy,
                        onClick = {
                            if (!cacheBusy) {
                                cacheBusy = true
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        val loader = Coil.imageLoader(context)
                                        loader.memoryCache?.clear()
                                        loader.diskCache?.clear()
                                    }
                                    cacheBusy = false
                                    message = "Görseller temizlendi."
                                }
                            }
                        }
                    )
ActionRow(
                        title = "Tanıtımı yeniden izle",
                        subtitle = "Baştan oynar.",
                        onClick = onReplayIntro
                    )
                }
            }

            item(key = "update-head") { SectionHeader("Güncelleme") }
            item(key = "update-card") {
                UpdateCard(
                    busy = updateBusy,
                    versionName = BuildConfig.VERSION_NAME,
                    onCheck = {
                        if (!updateBusy) {
                            updateBusy = true
                            message = null
                            scope.launch {
                                message = when (val result = app.updates.checkLatest(BuildConfig.VERSION_CODE)) {
                                    is UpdateResult.UpToDate -> "Uygulama güncel."
                                    is UpdateResult.Available -> "Yeni sürüm mevcut: ${result.info.versionName}."
                                    is UpdateResult.Failed -> "Güncelleme denetlenemedi."
                                    UpdateResult.NotConfigured -> "Güncelleme yapılandırılmamış."
                                }
                                updateBusy = false
                            }
                        }
                    }
                )
            }

        }
    }
}

@Composable
private fun UpdateCard(
    busy: Boolean,
    versionName: String,
    onCheck: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = screenPadding())
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        NotflixColors.Elevated,
                        NotflixColors.Accent.copy(alpha = 0.10f)
                    )
                )
            )
            .border(1.dp, NotflixColors.LineSoft, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(NotflixColors.Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = NotflixColors.AccentSoft,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Uygulama güncellemesi",
                    color = NotflixColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Kurulu sürüm $versionName",
                    color = NotflixColors.TextMuted,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(if (busy) NotflixColors.Card else NotflixColors.Accent)
                .clickable(enabled = !busy, onClick = onCheck)
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = NotflixColors.TextPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(
                    text = "Denetleniyor…",
                    color = NotflixColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Güncelleme denetle",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            text = "Yeni sürüm varsa uygulama yeniden başlatılınca kurulum ekranı açılır.",
            color = NotflixColors.TextMuted,
            fontSize = 10.5.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = NotflixColors.TextMuted,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(
            start = screenPadding(),
            end = screenPadding(),
            top = if (isTvLayout()) 24.dp else 18.dp,
            bottom = 8.dp
        )
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = screenPadding())
            .clip(RoundedCornerShape(12.dp))
            .background(NotflixColors.Elevated)
            .border(1.dp, NotflixColors.LineSoft, RoundedCornerShape(12.dp)),
        content = content
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) ColorWhite(0.035f) else androidx.compose.ui.graphics.Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NotflixColors.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = NotflixColors.TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                checkedTrackColor = NotflixColors.Accent,
                checkedBorderColor = NotflixColors.Accent,
                uncheckedThumbColor = NotflixColors.TextMuted,
                uncheckedTrackColor = NotflixColors.Card,
                uncheckedBorderColor = NotflixColors.Line
            )
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) ColorWhite(0.035f) else androidx.compose.ui.graphics.Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (busy) NotflixColors.TextMuted else NotflixColors.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = NotflixColors.TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (busy) {
            CircularProgressIndicator(
                color = NotflixColors.Accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(17.dp)
            )
        } else {
            Text(
                text = "›",
                color = if (focused) NotflixColors.AccentSoft else NotflixColors.TextMuted,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun ColorWhite(alpha: Float) = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha)
