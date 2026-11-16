package com.notflix.app.data

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONObject

/**
 * Bir icerigin gercek dil ve sezon bilgisi.
 *
 * Degerler yalnizca detay sayfasinda gercekten okunabildiginde kaydedilir;
 * boylece posterlerde uydurma "Dublaj" / "Altyazi" etiketi basilmaz.
 */
data class MediaMeta(
    val turkishDubbing: Boolean = false,
    val hasSubtitles: Boolean = false,
    val seasonCount: Int = 0
) {
    val isEmpty: Boolean
        get() = !turkishDubbing && !hasSubtitles && seasonCount <= 0
}

/**
 * Detay sayfasinda kesfedilen dil/sezon bilgisini ana sayfa posterleriyle
 * paylasan kucuk onbellek. Compose state oldugu icin posterler bilgi
 * geldiginde kendiliginden guncellenir; cihazda da kalici tutulur.
 */
object MediaMetaStore {

    private const val PREFS = "notflix_media_meta"
    private const val KEY_ENTRIES = "entries"

    private val cache = mutableStateMapOf<String, MediaMeta>()
    private var loaded = false
    private var prefs: android.content.SharedPreferences? = null

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        loaded = true
        prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        runCatching {
            val raw = prefs?.getString(KEY_ENTRIES, null) ?: return
            val json = JSONObject(raw)
            json.keys().forEach { key ->
                val obj = json.optJSONObject(key) ?: return@forEach
                cache[key] = MediaMeta(
                    turkishDubbing = obj.optBoolean("dub"),
                    hasSubtitles = obj.optBoolean("sub"),
                    seasonCount = obj.optInt("seasons", 0)
                )
            }
        }
    }

    @Synchronized
    fun put(context: Context, url: String, meta: MediaMeta) {
        if (url.isBlank() || meta.isEmpty) return
        init(context)
        if (cache[url] == meta) return
        cache[url] = meta
        runCatching { persist() }
    }

    /** Poster bilesenleri bunu okur; Compose state oldugu icin aninda yenilenir. */
    fun metaFor(url: String): MediaMeta? = cache[url]

    private fun persist() {
        val json = JSONObject()
        cache.forEach { (url, meta) ->
            json.put(
                url,
                JSONObject().apply {
                    put("dub", meta.turkishDubbing)
                    put("sub", meta.hasSubtitles)
                    put("seasons", meta.seasonCount)
                }
            )
        }
        prefs?.edit()?.putString(KEY_ENTRIES, json.toString())?.apply()
    }
}
