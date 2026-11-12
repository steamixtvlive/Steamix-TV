package com.notflix.app.data

import android.content.Context
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class LiveTvRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(18, TimeUnit.SECONDS)
        .build()

    @Volatile private var channels: List<LiveChannel>? = null

    suspend fun load(forceRefresh: Boolean = false): List<LiveChannel> = withContext(Dispatchers.IO) {
        val current = channels ?: loadAsset().also { channels = it }
        if (forceRefresh) refresh() ?: current else current
    }

    suspend fun refresh(): List<LiveChannel>? = withContext(Dispatchers.IO) {
        val catalog = runCatching { fetchCatalogMetadata() }
            .onFailure { Log.w(TAG, "Kanal bilgileri güncellenemedi: ${it.message}") }
            .getOrNull() ?: return@withContext null
        val merged = (channels ?: loadAsset()).map { channel ->
            val meta = catalog[channel.tvg] ?: catalog[normalize(channel.name)]
            if (meta == null) channel else channel.copy(
                logo = channel.logo.ifBlank { meta.logo },
                programs = meta.programs.ifEmpty { channel.programs }
            )
        }
        channels = merged
        merged
    }

    suspend fun resolveStream(pageUrl: String): String? = withContext(Dispatchers.IO) {
        if (pageUrl.isBlank()) return@withContext null
        val endpoint = "$HOST/live/resolve?url=" + java.net.URLEncoder.encode(pageUrl, "UTF-8")
        runCatching {
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                json.optString("url").takeIf { it.startsWith("http") }
            }
        }.onFailure { Log.w(TAG, "Yayın adresi alınamadı: ${it.message}") }.getOrNull()
    }

    private fun loadAsset(): List<LiveChannel> {
        val json = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("channels") ?: JSONArray()
        val result = ArrayList<LiveChannel>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("n").trim()
            val url = item.optString("u").trim()
            if (name.isBlank() || url.isBlank()) continue
            val programs = item.optJSONArray("e")?.let { epg ->
                (0 until epg.length()).mapNotNull { index ->
                    val row = epg.optJSONArray(index) ?: return@mapNotNull null
                    val title = row.optString(0)
                    val start = row.optLong(1)
                    val end = row.optLong(2)
                    if (title.isBlank() || start <= 0L || end <= start) null
                    else TvProgram(title, start, end)
                }
            }.orEmpty()
            val alternates = item.optJSONArray("a")?.let { alt ->
                (0 until alt.length()).mapNotNull { alt.optString(it).takeIf { s -> s.startsWith("http") } }
            }.orEmpty()
            result += LiveChannel(
                name = name,
                url = url,
                logo = item.optString("l"),
                group = item.optString("g").ifBlank { "Diğer" },
                stream = item.optString("s"),
                tvg = item.optString("t"),
                programs = programs,
                alternates = alternates
            )
        }
        val preferred = result.filterNot { normalize(it.name).contains("kurdi") }
        return preferred.sortedWith(
            compareBy<LiveChannel> { groupRank(it.group) }
                .thenBy { desiredRank(it.name) }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun fetchCatalogMetadata(): Map<String, ChannelMeta> {
        val result = HashMap<String, ChannelMeta>()
        var cursor: String? = null
        var guard = 0
        while (guard++ < 40) {
            val suffix = cursor?.let { "?cursor=$it" }.orEmpty()
            val request = Request.Builder()
                .url("$HOST/live/catalog/live/channels.json$suffix")
                .header("User-Agent", USER_AGENT)
                .build()
            val json = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return result
                JSONObject(response.body?.string().orEmpty())
            }
            val metas = json.optJSONArray("items") ?: json.optJSONArray("metas") ?: break
            for (i in 0 until metas.length()) {
                val meta = metas.optJSONObject(i) ?: continue
                val tags = meta.optJSONArray("tags")
                val isTurkey = tags != null && (0 until tags.length()).any {
                    tags.optString(it).equals("Turkey", ignoreCase = true)
                }
                if (!isTurkey) continue
                val tvg = meta.optJSONObject("ids")?.optString("tvg").orEmpty()
                val logo = meta.optJSONObject("images")?.optString("logo").orEmpty()
                val programs = meta.optJSONArray("epg")?.let { epg ->
                    (0 until minOf(epg.length(), 12)).mapNotNull { index ->
                        val row = epg.optJSONObject(index) ?: return@mapNotNull null
                        val start = parseIso(row.optString("start"))
                        val end = parseIso(row.optString("end"))
                        val title = row.optString("title")
                        if (start <= 0L || end <= start || title.isBlank()) null
                        else TvProgram(title, start, end)
                    }
                }.orEmpty()
                val entry = ChannelMeta(
                    logo = logo.takeIf { it.startsWith("http") }
                        ?: logo.takeIf { it.startsWith("/") }?.let { HOST + it }.orEmpty(),
                    programs = programs
                )
                if (tvg.isNotBlank()) result[tvg] = entry
                val key = normalize(meta.optString("name"))
                if (key.isNotBlank()) result.putIfAbsent(key, entry)
            }
            cursor = json.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" }
            if (cursor == null) break
        }
        return result
    }

    private fun parseIso(value: String): Long = runCatching {
        java.time.Instant.parse(value).toEpochMilli()
    }.getOrDefault(0L)

    private fun groupRank(group: String): Int = when (group) {
        "Ulusal" -> 0
        "Haber" -> 1
        "Spor" -> 2
        "Sinema & Dizi" -> 3
        "Belgesel" -> 4
        "Müzik" -> 5
        "Çocuk" -> 6
        "Dini" -> 7
        "Yaşam" -> 8
        "Yerel" -> 9
        "Avrupa" -> 10
        else -> 11
    }

    private fun desiredRank(name: String): Int {
        val key = normalize(name)
        ORDER_INDEX[key]?.let { return it }
        val base = normalize(
            name.replace(Regex("\\s+(HD|FHD|4K|UHD)$", RegexOption.IGNORE_CASE), "")
        )
        ORDER_INDEX[base]?.let { return it + 2_000 }
        return Int.MAX_VALUE
    }

    private data class ChannelMeta(val logo: String, val programs: List<TvProgram>)

    companion object {
        private const val TAG = "NotflixLive"
        private const val HOST = "http://131.123.43.89"
        private const val ASSET = "live_channels.json"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"

        private fun normalize(name: String): String = name.lowercase()
            .replace("ı", "i").replace("ş", "s").replace("ğ", "g")
            .replace("ü", "u").replace("ö", "o").replace("ç", "c")
            .replace(Regex("[^a-z0-9]"), "")

        private val DESIRED_ORDER = listOf(
            "TRT 1", "TRT 2", "TRT Haber", "TRT World", "TRT Türk", "TRT Belgesel",
            "TRT Müzik", "TRT Spor", "TRT Spor Yıldız", "TRT 4K", "TRT Avaz", "TRT Arabi",
            "TRT GAP", "TRT TSR", "ATV", "Kanal D", "Show TV", "Star TV", "NOW", "TV8",
            "Kanal 7", "Beyaz TV", "360", "TV 100", "24", "TVNet", "Ülke TV", "Akit TV",
            "Bengü Türk", "Halk TV", "TELE1", "Sözcü TV", "KRT", "Ulusal Kanal",
            "Bloomberg HT", "CNBC-e", "a Haber", "a Spor", "A News", "a2", "CNN Türk", "NTV",
            "Habertürk", "TGRT Haber", "Haber Global", "beIN Sports 1", "beIN Sports 2",
            "beIN Sports 3", "beIN Sports 4", "beIN Sports 4K", "beIN Sports Max 1",
            "beIN Sports Max 2", "S Sport", "S Sport 2", "S Sport Plus", "Tivibu Spor 1",
            "Tivibu Spor 2", "Tivibu Spor 3", "Tivibu Spor 4", "Spor Smart", "Spor Smart 2",
            "Eurosport 1", "Eurosport 2", "Eurosport 4K", "FB TV", "GS TV", "BJK TV", "BRT 2",
            "HT Spor", "tabii Spor", "NBA TV", "Sinema TV", "Sinema TV 2", "Sinema Aksiyon 1",
            "Sinema Aksiyon 2", "Sinema Aile 1", "Sinema Aile 2", "Sinema Komedi",
            "Sinema Komedi 2", "Sinema Yerli 1", "Sinema Yerli 2", "Sinema 1001",
            "Sinema 1002", "beIN Movies Premiere", "beIN Movies Stars", "beIN Movies Türk",
            "beIN Movies Action", "beIN Movies Family", "beIN Movies Festival",
            "beIN Series 1", "beIN Series 2", "beIN Series Vice", "beIN Series Comedy",
            "beIN İZ", "Movie Smart Classic", "Movie Smart Türk", "Movie Smart Premium",
            "Movie Smart Gold", "Movie Smart Fest", "Movie Smart Action", "Movie Smart Family",
            "Movie Smart Premiere", "Dizi Smart Premium", "Dizi Smart Max", "Dizi Smart Gold",
            "Dizi Smart Classic", "FilmBox", "FilmBox Extra", "FilmBox Arthouse",
            "FilmBox Premium", "FilmBox Family", "FilmBox Stars", "FilmScreen", "FX",
            "Dizi TV", "Dizi Channel", "Show Max", "Euro D", "Euro Star", "TLC", "DMAX",
            "BBC Earth", "Love Nature", "Discovery Channel", "Discovery Science",
            "Discovery ID", "Animal Planet", "Nat Geo", "Nat Geo Wild", "History",
            "Viasat Nature", "Viasat Explore", "Viasat History", "Da Vinci Learning",
            "TGRT Belgesel", "Yaban TV", "24Kitchen", "Travel Channel", "Food Network",
            "Outdoor Channel", "Insight TV", "DocuBox", "Fast & FunBox", "Diyanet TV",
            "Vav TV", "Rehber TV", "Dost TV", "Lalegül TV", "Semerkand TV", "Hilal TV",
            "Meltem TV", "Mesaj TV", "Kanal Hayat", "FM TV", "Kral TV", "Kral Pop TV",
            "Dream Türk", "Dream TV", "Number 1", "NR1 Türk", "NR1 Dance", "NR1 Pop",
            "PowerTürk TV", "Power TV", "Power Hit Radio TV", "Viva TV", "45LİK", "JoyTürk",
            "SlowTürk", "FenomenTürk", "Radyo Fenomen TV", "Trace Urban", "Trace Tropical",
            "Stingray iConcerts", "Stingray Classica", "Mezzo", "Mezzo Live HD", "MTV Live HD",
            "Club MTV", "MTV Hits", "VH1", "Kiss TV", "Magic TV", "Çay TV", "BRT 1", "BRT 3",
            "Kanal 7 Avrupa", "TV8 int", "TGRT EU", "Euro ATV", "Show Türk", "Star TV Avrupa",
            "Kanal D Avrupa", "CNN Türk Avrupa", "NTV Avrupa", "Habertürk Avrupa",
            "a Haber Avrupa", "TV 5", "TV 8.5", "TV 24", "TV 35", "TV 52", "TV 61", "TV 68",
            "TV 72", "TV 360", "TV 7", "TV 6", "TV 4", "TV 3", "TV 2", "TV 1",
            "Premier Sports", "Sports TV", "beIN Sports Haber", "Sinema TV HD", "Sinema 2 HD",
            "Sinema Aksiyon HD", "Sinema Aile HD", "Sinema Komedi HD", "Sinema 1001 HD",
            "Sinema 1002 HD", "Sinema Yerli HD", "FilmBox Arthouse HD", "FX HD", "TLC HD",
            "DMAX HD", "Discovery Showcase HD", "Viasat Nature HD", "Viasat Explore HD",
            "Viasat History HD", "BBC Earth HD", "Love Nature 4K", "TV 100 HD", "24 HD",
            "TVNet HD", "Ülke TV HD", "Akit TV HD", "Bengü Türk TV HD", "Halk TV HD",
            "TELE1 HD", "Sözcü TV HD", "KRT HD", "Ulusal Kanal HD", "Bloomberg HT HD",
            "CNBC-e HD", "a Haber HD", "a Spor HD", "A News HD", "a2 HD", "CNN Türk HD",
            "NTV HD", "Habertürk HD", "TGRT Haber HD", "Haber Global HD", "ATV HD",
            "Kanal D HD", "Show TV HD", "Star TV HD", "NOW HD", "TV8 HD", "Kanal 7 HD",
            "Beyaz TV HD", "360 HD", "TRT 1 HD", "TRT 2 HD", "TRT Haber HD", "TRT Spor HD",
            "TRT Belgesel HD", "TRT Müzik HD", "TRT Türk HD", "TRT Avaz HD", "TRT Arabi HD",
            "Diyanet TV HD", "Vav TV HD", "Kral TV HD", "Kral Pop TV HD", "Dream Türk HD",
            "Number 1 TV HD", "TRT Çocuk", "TRT Diyanet Çocuk", "TRT Genç", "MinikaÇocuk",
            "MinikaGO", "Cartoon Network", "Boomerang", "Disney Channel", "Disney Junior",
            "Nickelodeon", "Nick Jr.", "BabyTV", "Da Vinci Kids", "Duck TV", "Luli TV",
            "TRT EBA İlkokul"
        )

        private val ORDER_INDEX: Map<String, Int> = DESIRED_ORDER
            .mapIndexed { index, name -> normalize(name) to index }
            .toMap()
    }
}
