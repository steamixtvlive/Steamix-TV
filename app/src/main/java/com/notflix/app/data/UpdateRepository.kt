package com.notflix.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)

sealed interface UpdateResult {
    data object NotConfigured : UpdateResult
    data object UpToDate : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

class UpdateRepository {

    companion object {
        private const val REPO_RAW =
            "https://raw.githubusercontent.com/steamixtvlive/Steamix-TV"
        const val DEFAULT_UPDATE_URL = "$REPO_RAW/main/update.json"
        private val UPDATE_URLS = listOf(
            DEFAULT_UPDATE_URL,
            "$REPO_RAW/master/update.json"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun checkLatest(currentVersionCode: Int): UpdateResult {
        var failed: UpdateResult = UpdateResult.Failed("Güncelleme denetlenemedi.")
        for (url in UPDATE_URLS) {
            val result = check(url, currentVersionCode)
            if (result !is UpdateResult.Failed) return result
            failed = result
        }
        return failed
    }

    suspend fun check(url: String, currentVersionCode: Int): UpdateResult =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext UpdateResult.NotConfigured
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "NOTFLIX-Android")
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext UpdateResult.Failed("Sunucu ${response.code} döndü.")
                    }
                    val body = response.body?.string()
                        ?: return@withContext UpdateResult.Failed("Boş yanıt.")
                    val json = JSONObject(body)
                    val code = json.optInt("versionCode", json.optInt("version_code", 0))
                    val name = json.optString("versionName")
                        .ifBlank { json.optString("version_name") }
                    val apk = json.optString("apkUrl")
                        .ifBlank { json.optString("apk_url") }
                    val notes = json.optString("notes").ifBlank { json.optString("changelog") }
                    if (code <= 0 || apk.isBlank()) {
                        return@withContext UpdateResult.Failed("Güncelleme dosyası eksik.")
                    }
                    if (code <= currentVersionCode) {
                        UpdateResult.UpToDate
                    } else {
                        UpdateResult.Available(
                            UpdateInfo(
                                versionCode = code,
                                versionName = name.ifBlank { "Yeni sürüm" },
                                apkUrl = apk,
                                notes = notes
                            )
                        )
                    }
                }
            } catch (error: Exception) {
                UpdateResult.Failed(error.message ?: "Güncelleme denetlenemedi.")
            }
        }
}
