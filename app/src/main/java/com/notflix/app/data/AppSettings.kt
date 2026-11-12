package com.notflix.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WatchEntry(
    val url: String,
    val title: String,
    val poster: String,
    val type: MediaType,
    val position: Long,
    val duration: Long,
    val season: Int,
    val episode: Int,
    val completed: Boolean,
    val updatedAt: Long
) {
    val progress: Float
        get() = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val seriesKey: String get() = url.substringBefore("/sezon-").substringBefore("?")
    val episodeLabel: String get() = if (season > 0) "S$season · B$episode" else ""
}

class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("notflix_settings", Context.MODE_PRIVATE)

    var resumeEnabled: Boolean
        get() = prefs.getBoolean(KEY_RESUME, true)
        set(value) = prefs.edit().putBoolean(KEY_RESUME, value).apply()

    var introSeen: Boolean
        get() = prefs.getBoolean(KEY_INTRO_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_INTRO_SEEN, value).apply()

    var playerAspect: String
        get() = prefs.getString(KEY_ASPECT, "fit").orEmpty().ifBlank { "fit" }
        set(value) = prefs.edit().putString(KEY_ASPECT, value).apply()

    fun savedPosition(key: String): Long = prefs.getLong("position_$key", 0L)

    fun savePosition(key: String, positionMs: Long) {
        prefs.edit().putLong("position_$key", positionMs.coerceAtLeast(0L)).apply()
    }

    fun clearPositions() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("position_") }.forEach { editor.remove(it) }
        editor.remove(KEY_WATCH)
        editor.apply()
    }

    fun favorites(): List<MediaCard> = runCatching {
        val array = JSONArray(prefs.getString(KEY_LIBRARY, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url")
            val title = item.optString("title")
            val poster = item.optString("poster")
            if (url.isBlank() || title.isBlank()) return@mapNotNull null
            MediaCard(
                title = title,
                url = url,
                poster = poster,
                type = if (item.optString("type") == "SERIES") MediaType.SERIES
                else MediaType.MOVIE
            )
        }
    }.getOrDefault(emptyList())

    fun isFavorite(url: String): Boolean = favorites().any { it.url == url }

    fun toggleFavorite(card: MediaCard): Boolean {
        val current = favorites().toMutableList()
        val existing = current.indexOfFirst { it.url == card.url }
        val added = existing < 0
        if (added) current.add(0, card) else current.removeAt(existing)
        persistFavorites(current)
        return added
    }

    fun removeFavorite(url: String) {
        persistFavorites(favorites().filterNot { it.url == url })
    }

    /* ---------------- Canli TV favorileri ---------------- */

    fun liveFavorites(): List<String> = runCatching {
        val array = JSONArray(prefs.getString(KEY_LIVE_FAVORITES, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    fun isLiveFavorite(url: String): Boolean = liveFavorites().any { it == url }

    /** @return true eklendiyse, false kaldirildiysa. */
    fun toggleLiveFavorite(url: String): Boolean {
        val current = liveFavorites().toMutableList()
        val added = current.none { it == url }
        if (added) current.add(0, url) else current.removeAll { it == url }
        val array = JSONArray()
        current.forEach { array.put(it) }
        prefs.edit().putString(KEY_LIVE_FAVORITES, array.toString()).apply()
        return added
    }

    private fun persistFavorites(items: List<MediaCard>) {
        val array = JSONArray()
        items.forEach { card ->
            array.put(
                JSONObject().apply {
                    put("url", card.url)
                    put("title", card.title)
                    put("poster", card.poster)
                    put("type", card.type.name)
                }
            )
        }
        prefs.edit().putString(KEY_LIBRARY, array.toString()).apply()
    }

    fun watchHistory(): List<WatchEntry> = runCatching {
        val array = JSONArray(prefs.getString(KEY_WATCH, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val url = item.optString("url")
            if (url.isBlank()) return@mapNotNull null
            WatchEntry(
                url = url,
                title = item.optString("title"),
                poster = item.optString("poster"),
                type = if (item.optString("type") == "SERIES") MediaType.SERIES
                else MediaType.MOVIE,
                position = item.optLong("position"),
                duration = item.optLong("duration"),
                season = item.optInt("season"),
                episode = item.optInt("episode"),
                completed = item.optBoolean("completed"),
                updatedAt = item.optLong("updatedAt")
            )
        }
    }.getOrDefault(emptyList())

    fun watchFor(url: String): WatchEntry? = watchHistory().firstOrNull { it.url == url }

    fun recordWatch(entry: WatchEntry) {
        val current = watchHistory().toMutableList()
        current.removeAll { it.url == entry.url }
        if (entry.position > 3_000L || entry.completed) current.add(0, entry)
        persistWatch(current)
    }

    fun removeWatch(seriesKey: String) {
        val entries = watchHistory()
        val removed = entries.filter { it.seriesKey == seriesKey }
        if (removed.isEmpty()) return
        val editor = prefs.edit()
        removed.forEach { entry -> editor.remove("position_${entry.url}") }
        editor.apply()
        persistWatch(entries - removed.toSet())
    }

    private fun persistWatch(entries: List<WatchEntry>) {
        val keep = entries.sortedByDescending { it.updatedAt }.take(60)
        val array = JSONArray()
        keep.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("url", item.url)
                    put("title", item.title)
                    put("poster", item.poster)
                    put("type", item.type.name)
                    put("position", item.position)
                    put("duration", item.duration)
                    put("season", item.season)
                    put("episode", item.episode)
                    put("completed", item.completed)
                    put("updatedAt", item.updatedAt)
                }
            )
        }
        prefs.edit().putString(KEY_WATCH, array.toString()).apply()
    }

    private companion object {
        const val KEY_RESUME = "resume_enabled"
        const val KEY_INTRO_SEEN = "intro_seen"
        const val KEY_ASPECT = "player_aspect"
        const val KEY_LIBRARY = "library_items"
        const val KEY_WATCH = "watch_entries"
        const val KEY_LIVE_FAVORITES = "live_favorites"
    }
}
