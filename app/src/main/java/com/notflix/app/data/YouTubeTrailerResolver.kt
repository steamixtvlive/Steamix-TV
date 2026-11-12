package com.notflix.app.data

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * YouTube fragmanini dogrudan oynatilabilir MP4 adresine cevirir.
 *
 * WebView + YouTube iframe oynaticisi, cihaz VP9/AV1 decode edemiyorsa
 * sesi acar ama kadreleri siyah birakir (BlueStacks / emulator). Bu cozum
 * progressive H.264 (itag 18 / 22) akisini bulup ExoPlayer'a verir.
 */
class YouTubeTrailerResolver {

    sealed interface Stream {
        /** Ses + video tek dosyada (H.264 progressive). */
        data class Progressive(val url: String) : Stream

        /** Video ve ayri ses akislari (ExoPlayer birlestirir). */
        data class Adaptive(val videoUrl: String, val audioUrl: String) : Stream
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private data class InnertubeClient(
        val name: String,
        val version: String,
        val clientNumber: String,
        val userAgent: String,
        val key: String? = DEFAULT_KEY
    )

    suspend fun resolve(videoId: String): Stream? = withContext(Dispatchers.IO) {
        if (videoId.isBlank()) return@withContext null
        for (spec in CLIENTS) {
            val stream = runCatching { requestStream(spec, videoId) }.getOrNull()
            if (stream != null) return@withContext stream
        }
        runCatching { requestFromPiped(videoId) }.getOrNull()
    }

    private fun requestStream(spec: InnertubeClient, videoId: String): Stream? {
        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", spec.name)
                        .put("clientVersion", spec.version)
                        .put("hl", "en")
                        .put("gl", "US")
                        .put("userAgent", spec.userAgent)
                )
            )
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .toString()

        val url = buildString {
            append("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            if (!spec.key.isNullOrBlank()) append("&key=").append(spec.key)
        }
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", spec.userAgent)
            .header("X-YouTube-Client-Name", spec.clientNumber)
            .header("X-YouTube-Client-Version", spec.version)
            .header("Accept-Language", "en-US,en;q=0.9")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val text = response.body?.string() ?: return null
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
            val status = json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
            if (status.isNotBlank() && status != "OK") return null
            val data = json.optJSONObject("streamingData") ?: return null
            return pickStream(data.optJSONArray("formats"), data.optJSONArray("adaptiveFormats"))
        }
    }

    private fun pickStream(formats: JSONArray?, adaptive: JSONArray?): Stream? {
        var progressiveFallback: String? = null
        if (formats != null) {
            for (index in 0 until formats.length()) {
                val format = formats.optJSONObject(index) ?: continue
                val mime = format.optString("mimeType")
                if (!mime.contains("video/mp4")) continue
                val direct = format.optString("url")
                if (direct.isBlank() || format.has("signatureCipher")) continue
                val itag = format.optInt("itag", 0)
                if (itag == 18 || itag == 22) return Stream.Progressive(direct)
                if (progressiveFallback == null) progressiveFallback = direct
            }
        }

        var videoUrl: String? = null
        var audioUrl: String? = null
        if (adaptive != null) {
            for (index in 0 until adaptive.length()) {
                val format = adaptive.optJSONObject(index) ?: continue
                val mime = format.optString("mimeType")
                val direct = format.optString("url")
                if (direct.isBlank() || format.has("signatureCipher")) continue
                when {
                    mime.startsWith("video/mp4") && mime.contains("avc1") && videoUrl == null -> {
                        videoUrl = direct
                    }
                    mime.startsWith("audio/mp4") && audioUrl == null -> {
                        audioUrl = direct
                    }
                }
            }
        }
        if (videoUrl != null && audioUrl != null) return Stream.Adaptive(videoUrl, audioUrl)
        return progressiveFallback?.let { Stream.Progressive(it) }
    }

    /** Innertube engellenirse acik YouTube onaylari uzerinden dener. */
    private fun requestFromPiped(videoId: String): Stream? {
        for (instance in PIPED_INSTANCES) {
            val request = Request.Builder()
                .url("$instance/streams/$videoId")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36")
                .build()
            val body = runCatching {
                client.newCall(request).execute().use { response -> readPiped(response) }
            }.getOrNull()
            if (body != null) return body
        }
        return null
    }

    private fun readPiped(response: Response): Stream? {
        if (!response.isSuccessful) return null
        val text = response.body?.string() ?: return null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val items = json.optJSONArray("videoStreams") ?: return null
        var fallback: String? = null
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val mime = item.optString("mimeType")
            if (!mime.contains("mp4")) continue
            val url = item.optString("url")
            if (url.isBlank()) continue
            if (mime.contains("avc1")) return Stream.Progressive(url)
            if (fallback == null) fallback = url
        }
        return fallback?.let { Stream.Progressive(it) }
    }

    companion object {
        private const val DEFAULT_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

        private val CLIENTS = listOf(
            InnertubeClient(
                name = "ANDROID",
                version = "19.09.37",
                clientNumber = "3",
                userAgent = "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"
            ),
            InnertubeClient(
                name = "ANDROID_VR",
                version = "1.60.19",
                clientNumber = "28",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12) gzip"
            ),
            InnertubeClient(
                name = "IOS",
                version = "19.29.1",
                clientNumber = "5",
                userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"
            ),
            InnertubeClient(
                name = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                version = "2.0",
                clientNumber = "85",
                userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 Safari/605.1.15"
            ),
            InnertubeClient(
                name = "WEB",
                version = "2.20240726.00.00",
                clientNumber = "1",
                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
        )

        private val PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.reallyaweso.me",
            "https://pipedapi.leptons.xyz"
        )
    }
}
