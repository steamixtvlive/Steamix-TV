package com.notflix.app.data

import android.util.Log
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FilmmoduRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(22, TimeUnit.SECONDS)
        .callTimeout(32, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val tmdbClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private val fetchGate = Semaphore(12)
    private val MOOD_GENRES = mapOf(
        "exciting" to listOf("aksiyon", "gerilim", "macera"),
        "emotional" to listOf("dram", "romantik"),
        "fun" to listOf("komedi", "macera", "animasyon"),
        "dark" to listOf("suc", "gizem", "korku", "gerilim")
    )
    private val homeCache = TimedCache<HomeData>(10 * 60_000L)
    private val detailCache = TimedCache<MediaDetail>(20 * 60_000L)
    private val coreCache = TimedCache<String>(6 * 60 * 60_000L)
    private val seasonCache = TimedCache<Pair<String, List<Episode>>>(30 * 60_000L)
    private val audioCache = KeyedCache<PlayInfo>(96, 30 * 60_000L)
    private val trailerCache = KeyedCache<String>(300, 12 * 60 * 60_000L)
    private val trailerInFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<List<String>>>()
    private val pageCache = PageCache(PageCache.MAX_PAGES, 10 * 60_000L)
    private val suggestCache = SuggestCache()
    private val indexLock = Any()
    private val searchIndex = object : LinkedHashMap<String, MediaCard>(1024, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaCard>?) =
            size > 6000
    }

    suspend fun home(force: Boolean = false): HomeData = withContext(Dispatchers.IO) {
        if (!force) homeCache.get()?.let { return@withContext it }
        val doc = fetchDoc(BASE)
        val data = parseHome(doc)
        if (data.sections.isEmpty() && data.hero.isEmpty()) {
            throw IOException("Katalog okunamadı.")
        }
        homeCache.put(data)
        data
    }

    suspend fun section(title: String): List<MediaCard> = withContext(Dispatchers.IO) {
        runCatching { home() }.getOrNull()
            ?.sections
            ?.firstOrNull { it.title == title }
            ?.items
            .orEmpty()
    }

    suspend fun collection(url: String): List<MediaCard> = collectionPage(url, 1).items

    suspend fun collectionPage(url: String, page: Int): BrowsePage = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext BrowsePage(emptyList(), false, null)
        listingPage(url, page, null)
    }

    fun cachedHome(): HomeData? = homeCache.get()

    fun cacheHome(data: HomeData) = homeCache.put(data)

    suspend fun browse(type: MediaType, page: Int = 1, genre: String? = null): BrowsePage =
        withContext(Dispatchers.IO) {
            val base = if (type == MediaType.MOVIE) "$BASE/filmler" else "$BASE/diziler"
            val url = if (genre.isNullOrBlank()) base else "$base?tur=$genre"
            listingPage(url, page, type)
        }

    private suspend fun listingPage(url: String, page: Int, type: MediaType?): BrowsePage {
        val key = "$url#$page#${type ?: "all"}"
        pageCache.get(key)?.let { return it }
        val doc = fetchDoc(pageUrl(url, page))
        val container = doc.selectFirst("main") ?: doc
        val parsed = parseCards(container)
        val items = if (type == null) parsed else parsed.filter { it.type == type }.ifEmpty { parsed }
        val total = totalOf(doc)
        val nextPage = page + 1
        val hasNextLink = items.isNotEmpty() && (
            doc.select("a[href*='page=$nextPage']").isNotEmpty() ||
                doc.selectFirst("a[rel=next]") != null
            )
        val hasMore = hasNextLink && page < 400
        val result = BrowsePage(
            items = items,
            hasMore = hasMore,
            total = total ?: items.size.takeIf { type == null }
        )
        pageCache.put(key, result)
        return result
    }

    suspend fun suggest(query: String): SuggestResult = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext SuggestResult(emptyList(), emptyList())
        suggestCache.get(clean)?.let { return@withContext it }
        val result = runCatching { fetchSuggest(clean) }.getOrNull()
            ?: SuggestResult(localSearch(clean, 24), emptyList())
        val merged = if (result.media.isEmpty()) {
            result.copy(media = localSearch(clean, 24))
        } else {
            result
        }
        if (merged.media.isNotEmpty() || merged.people.isNotEmpty()) {
            suggestCache.put(clean, merged)
        }
        merged
    }

    private suspend fun fetchSuggest(clean: String): SuggestResult {
        val body = fetch("$BASE/ara/oneri?q=" + URLEncoder.encode(clean, "UTF-8"))
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return SuggestResult(emptyList(), emptyList())
        val media = mutableListOf<MediaCard>()
        for (key in listOf("movies", "series")) {
            val array = root.optJSONArray(key) ?: continue
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = absolute(item.optString("url"))
                val title = item.optString("title").trim()
                val poster = absolute(item.optString("poster"))
                if (title.isBlank() || !isContentUrl(url) || poster.isBlank()) continue
                val year = YEAR.find(item.optString("meta"))?.value
                media += MediaCard(
                    title = title,
                    url = normalizeContentUrl(url),
                    poster = poster,
                    type = typeOf(url),
                    year = year
                )
            }
        }
        val people = root.optJSONArray("people")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i) ?: return@mapNotNull null
                val name = item.optString("title").trim()
                val url = absolute(item.optString("url"))
                val photo = absolute(item.optString("poster"))
                if (name.isBlank() || !url.contains("/oyuncu/") || photo.isBlank()) null
                else PersonCard(name, url, photo)
            }
        }.orEmpty()
        media.forEach(::indexCard)
        return SuggestResult(media.distinctBy { it.url }, people.distinctBy { it.url })
    }

    fun localSearch(query: String, limit: Int = 24): List<MediaCard> {
        val needle = normalizeSearch(query)
        if (needle.length < 2) return emptyList()
        val snapshot = synchronized(indexLock) { searchIndex.values.toList() }
        if (snapshot.isEmpty()) return emptyList()
        val starts = ArrayList<MediaCard>()
        val contains = ArrayList<MediaCard>()
        for (card in snapshot) {
            val haystack = normalizeSearch(card.title)
            when {
                haystack.startsWith(needle) -> starts += card
                haystack.contains(needle) -> contains += card
            }
            if (starts.size >= limit) break
        }
        return (starts + contains).distinctBy { it.url }.take(limit)
    }

    fun indexKnown(cards: List<MediaCard>) = cards.forEach(::indexCard)

    suspend fun top250(): List<MediaCard> = withContext(Dispatchers.IO) {
        val doc = fetchDoc("$BASE/imdb-top-250")
        val ranked = mutableListOf<MediaCard>()
        for (script in doc.select("script[type=application/ld+json]")) {
            val raw = script.data()
            if (!raw.contains("\"ItemList\"")) continue
            runCatching {
                val root = JSONObject(raw)
                val graph = root.optJSONArray("@graph") ?: JSONArray().put(root)
                for (i in 0 until graph.length()) {
                    val node = graph.optJSONObject(i) ?: continue
                    if (node.optString("@type") != "ItemList") continue
                    val list = node.optJSONArray("itemListElement") ?: continue
                    for (j in 0 until list.length()) {
                        val element = list.optJSONObject(j) ?: continue
                        val url = absolute(element.optString("url"))
                        val title = element.optString("name").trim()
                        val poster = absolute(element.optString("image"))
                        if (title.isBlank() || poster.isBlank() || !isContentUrl(url)) continue
                        ranked += MediaCard(
                            title = title,
                            url = normalizeContentUrl(url),
                            poster = poster,
                            type = typeOf(url),
                            rank = element.optInt("position", ranked.size + 1)
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "IMDb listesi okunamadı", it) }
        }
        ranked.distinctBy { it.url }.sortedBy { it.rank ?: Int.MAX_VALUE }.take(250)
            .also(::indexKnown)
    }

    suspend fun recommend(
        type: MediaType?,
        genres: List<String>,
        limit: Int = 36,
        audio: String? = null,
        mood: String? = null
    ): List<MediaCard> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(22_000) {
            recommendInternal(type, genres, limit, audio, mood)
        } ?: fallbackRecommend(type, limit)
    }

    private suspend fun recommendInternal(
        type: MediaType?,
        genres: List<String>,
        limit: Int,
        audio: String?,
        mood: String?
    ): List<MediaCard> {
        val moodSlugs = if (mood.isNullOrBlank() || mood == "any") {
            emptyList()
        } else {
            MOOD_GENRES[mood].orEmpty()
        }
        val slugs = when {
            genres.isNotEmpty() -> genres
            moodSlugs.isNotEmpty() -> moodSlugs
            else -> emptyList()
        }
        val collected = LinkedHashMap<String, MediaCard>()
        for (slug in slugs.take(4)) {
            runCatching {
                val doc = fetchDoc("$BASE/tur/$slug")
                val main = doc.selectFirst("main") ?: doc
                parseCards(main)
            }.getOrDefault(emptyList())
                .filter { type == null || it.type == type }
                .forEach { collected.putIfAbsent(it.url, it) }
        }
        if (genres.isNotEmpty() && moodSlugs.isNotEmpty()) {
            val moodUrls = HashSet<String>()
            for (slug in moodSlugs.take(4)) {
                runCatching {
                    val doc = fetchDoc("$BASE/tur/$slug")
                    parseCards(doc.selectFirst("main") ?: doc)
                }.getOrDefault(emptyList()).forEach { moodUrls += it.url }
            }
            if (moodUrls.isNotEmpty()) {
                val narrowed = collected.filterKeys { it in moodUrls }
                collected.clear()
                collected.putAll(narrowed)
            }
        }
        if (collected.size < limit) {
            val types = if (type == null) listOf(MediaType.MOVIE, MediaType.SERIES) else listOf(type)
            for (t in types) {
                for (page in 1..2) {
                    runCatching { browse(t, page, null) }.getOrNull()?.items
                        ?.forEach { collected.putIfAbsent(it.url, it) }
                }
            }
        }
        var pool = collected.values.toList()
        if (!audio.isNullOrBlank() && audio != "any") {
            pool = filterByAudio(pool, audio, limit)
        }
        return pool.sortedWith(compareByDescending<MediaCard> { it.rating ?: 0.0 })
            .take(limit)
    }

    private suspend fun fallbackRecommend(type: MediaType?, limit: Int): List<MediaCard> {
        val fromHome = cachedHome()?.sections
            ?.flatMap { it.items }
            ?.filter { type == null || it.type == type }
            ?.distinctBy { it.url }
            .orEmpty()
        if (fromHome.isNotEmpty()) return fromHome.take(limit)
        return runCatching {
            withTimeoutOrNull(6_000) {
                val doc = fetchDoc("$BASE/kesfet")
                parseCards(doc.selectFirst("main") ?: doc)
                    .filter { type == null || it.type == type }
                    .distinctBy { it.url }
                    .take(limit)
            }
        }.getOrNull().orEmpty()
    }

    private suspend fun filterByAudio(
        pool: List<MediaCard>,
        mode: String,
        limit: Int
    ): List<MediaCard> {
        val candidates = pool
            .sortedWith(compareByDescending<MediaCard> { it.rating ?: 0.0 })
            .take(minOf(24, maxOf(12, limit)))
        val matches = java.util.Collections.synchronizedList(mutableListOf<Pair<Int, MediaCard>>())
        runCatching {
            withTimeoutOrNull(12_000) {
                coroutineScope {
                    val gate = Semaphore(8)
                    candidates.mapIndexed { index, card ->
                        async {
                            gate.withPermit {
                                val profile = runCatching { audioProfile(card.url) }.getOrNull()
                                    ?: return@withPermit
                                val turkishAudio = profile.first.any {
                                    it.contains("türkçe", ignoreCase = true) ||
                                        it.contains("turkce", ignoreCase = true)
                                }
                                val turkishSub = profile.second.any {
                                    it.contains("türkçe", ignoreCase = true) ||
                                        it.contains("turkce", ignoreCase = true)
                                }
                                val ok = when (mode) {
                                    "dub" -> turkishAudio
                                    "sub" -> turkishSub && !turkishAudio
                                    "original" -> !turkishAudio
                                    else -> true
                                }
                                if (ok) matches.add(index to card)
                            }
                        }
                    }.awaitAll()
                }
            }
        }
        return java.util.Collections.synchronizedList(matches)
            .sortedBy { it.first }
            .map { it.second }
            .take(limit)
    }

    suspend fun fillDescriptions(slides: List<HeroSlide>): List<HeroSlide> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val gate = Semaphore(3)
                slides.take(20).map { slide ->
                    async {
                        // Aciklama ya da kapak gorseli eksikse detay sayfasi cekilir.
                        if (slide.description.isNotBlank() && slide.poster.isNotBlank()) return@async slide
                        gate.withPermit {
                            val found = runCatching {
                                withTimeoutOrNull(15_000) { detail(slide.url) }
                            }.getOrNull()
                            val text = found?.description.orEmpty().trim()
                            val posterUrl = found?.poster.orEmpty().trim()
                            if (text.isNotBlank() || posterUrl.isNotBlank()) {
                                slide.copy(
                                    description = text.ifBlank { slide.description },
                                    poster = posterUrl.ifBlank { slide.poster }
                                )
                            } else slide
                        }
                    }
                }.awaitAll()
            }
        }

    suspend fun search(query: String): List<MediaCard> = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val doc = fetchDoc("$BASE/ara?q=$encoded")
        val main = doc.selectFirst("main") ?: doc
        parseCards(main).distinctBy { it.url }
    }

    suspend fun detail(url: String): MediaDetail = withContext(Dispatchers.IO) {
        detailCache.get()?.takeIf { cached -> cached.url == url }?.let { return@withContext it }
        val doc = fetchDoc(url)
        val detail = parseDetail(doc, url)
        detailCache.put(detail)
        detail
    }

    suspend fun episodes(pageUrl: String, season: Int): List<Episode> = withContext(Dispatchers.IO) {
        val cacheKey = "$pageUrl#$season"
        seasonCache.get()?.takeIf { it.first == cacheKey }?.let { return@withContext it.second }
        val doc = fetchDoc("$pageUrl?sezon=$season")
        val parsed = parseEpisodes(doc, season)
        seasonCache.put(cacheKey to parsed)
        parsed
    }

    /**
     * Dizide verilen bolumden sonraki bolumu bulur.
     *
     * "Devam Et" satiri, Devam Et sekmesi ve detay ekranindan oynatildiginda
     * otomatik "sonraki bolume gecis" calissin diye kullanilir.
     */
    suspend fun nextEpisode(seriesUrl: String, season: Int, number: Int): Episode? =
        withContext(Dispatchers.IO) {
            if (seriesUrl.isBlank() || season <= 0 || number <= 0) return@withContext null
            val detail = runCatching { detail(seriesUrl) }.getOrNull()
            val seasons = (detail?.seasonNumbers?.takeIf { it.isNotEmpty() }
                ?: detail?.seasons?.keys?.sorted()
                ?: emptyList()).sorted()
            val candidates = if (seasons.isEmpty()) listOf(season) else seasons
            for (candidate in candidates.filter { it >= season }) {
                var list = detail?.seasons?.get(candidate).orEmpty().sortedBy { it.number }
                // Bolumler sayfada yoksa sezon sayfasindan ayrica cekilir.
                if (list.none { candidate > season || it.number > number }) {
                    val fetched = runCatching { episodes(seriesUrl, candidate) }.getOrDefault(emptyList())
                    if (fetched.isNotEmpty()) list = fetched.sortedBy { it.number }
                }
                val next = list.firstOrNull { candidate > season || it.number > number }
                if (next != null) return@withContext next
            }
            null
        }

    suspend fun resolve(pageUrl: String): PlayInfo = withContext(Dispatchers.IO) {
        var effectivePage = pageUrl
        var doc = fetchDoc(effectivePage)
        var pv = doc.selectFirst("[data-pv]")?.attr("data-pv").orEmpty()

        if (pv.isBlank()) {
            val episodeUrl = doc.selectFirst("a[href*=/sezon-][href*=/bolum-]")
                ?.absUrl("href")
                .orEmpty()
            if (episodeUrl.isNotBlank()) {
                effectivePage = episodeUrl
                doc = fetchDoc(effectivePage)
                pv = doc.selectFirst("[data-pv]")?.attr("data-pv").orEmpty()
            }
        }
        if (pv.isBlank()) throw IOException("Bu içerik için oynatma anahtarı bulunamadı.")

        val scriptSrc = doc.selectFirst("script[src*=/assets/js/core.js]")?.attr("src").orEmpty()
        val playerBase = if (scriptSrc.startsWith("http")) {
            val parsed = URI(scriptSrc)
            "${parsed.scheme}://${parsed.host}"
        } else {
            "https://pilavyerplay.top"
        }

        val core = coreCache.get() ?: fetch("$playerBase/assets/js/core.js").also { coreCache.put(it) }
        val sPath = Regex("""["']([^"']*s\.php\?s=)["']""")
            .find(core)?.groupValues?.getOrNull(1)
            ?: "/assets/js/s.php?s="
        val tokenUrl = if (sPath.startsWith("http")) sPath + pv else playerBase + sPath + pv

        val playerHtml = fetch(tokenUrl, referer = effectivePage)
        val json = Regex(
            """window\.__PLAYER__\s*=\s*(\{.*?\});\s*</script>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(playerHtml)?.groupValues?.getOrNull(1)
            ?: throw IOException("Oynatıcı yanıtı çözümlenemedi.")

        val player = JSONObject(json)
        val stream = player.optString("stream")
        if (stream.isBlank()) throw IOException("Yayın adresi bulunamadı.")
        val audios = player.optJSONArray("audios")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.optString("label")?.takeIf { it.isNotBlank() }
            }
        }.orEmpty()
        val subtitles = player.optJSONArray("subs")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.optString("label")?.takeIf { it.isNotBlank() }
            }
        }.orEmpty()
        val info = PlayInfo(
            streamUrl = stream,
            title = player.optString("title").ifBlank { doc.selectFirst("h1")?.text().orEmpty() },
            poster = player.optString("poster"),
            referer = effectivePage,
            audios = audios,
            subtitles = subtitles
        )
        audioCache.put(effectivePage, info)
        audioCache.put(pageUrl, info)
        info
    }

    suspend fun audioProfile(pageUrl: String): Pair<List<String>, List<String>>? =
        withContext(Dispatchers.IO) {
            audioCache.get(pageUrl)?.let { return@withContext it.audios to it.subtitles }
            val info = runCatching { withTimeoutOrNull(6_000) { resolve(pageUrl) } }.getOrNull()
                ?: return@withContext null
            info.audios to info.subtitles
        }

    fun cachedTrailerKeys(title: String, year: String?, type: MediaType): List<String> {
        val clean = title.substringBefore(" · ").trim()
        if (clean.isBlank()) return emptyList()
        val cacheKey = "${type.name}|${clean.lowercase()}|${year.orEmpty()}"
        return trailerCache.get(cacheKey)?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun trailerKeys(title: String, year: String?, type: MediaType): List<String> =
        withContext(Dispatchers.IO) {
            val clean = title.substringBefore(" · ").trim()
            if (clean.isBlank()) return@withContext emptyList()
            val cacheKey = "${type.name}|${clean.lowercase()}|${year.orEmpty()}"
            trailerCache.get(cacheKey)?.let { return@withContext it.split(',').filter { k -> k.isNotBlank() } }
            trailerInFlight[cacheKey]?.let { return@withContext withTimeoutOrNull(12_000) { it.await() }.orEmpty() }
            val job = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
                runCatching { lookupTrailers(clean, year, type) }.getOrDefault(emptyList())
                    .also { trailerCache.put(cacheKey, it.joinToString(",")) }
            }
            trailerInFlight[cacheKey] = job
            try { withTimeoutOrNull(12_000) { job.await() }.orEmpty() }
            finally { if (job.isCompleted) trailerInFlight.remove(cacheKey, job) }
        }

    suspend fun trailerKey(title: String, year: String?, type: MediaType): String? =
        trailerKeys(title, year, type).firstOrNull()

    private fun lookupTrailers(title: String, year: String?, type: MediaType): List<String> {
        val kind = if (type == MediaType.MOVIE) "movie" else "tv"
        val query = URLEncoder.encode(title, "UTF-8")
        val releaseYear = year?.take(4)?.takeIf { it.length == 4 }
        val id = findTmdbId(kind, query, releaseYear)

        val ordered = LinkedHashSet<String>()
        if (id != 0) {
            // Onemli: Videolar sadece "language=tr-TR" ile sorgulanirsa cogu
            // film icin 0 sonuc geliyordu (Inception: tr-TR=0, en-US=27) ve
            // fragman hic gosterilmiyordu. Bu yuzden once dilsiz (en-US)
            // sorgusu yapilir, tr-TR sonra eklenir.
            val bodies = listOf(
                runCatching {
                    tmdbGet("https://api.themoviedb.org/3/$kind/$id/videos?api_key=$TMDB_KEY")
                }.getOrNull(),
                runCatching {
                    tmdbGet("https://api.themoviedb.org/3/$kind/$id/videos?api_key=$TMDB_KEY&language=tr-TR")
                }.getOrNull()
            )
            val candidates = ArrayList<JSONObject>()
            bodies.forEach { body ->
                val videos = runCatching { JSONObject(body ?: "{}").optJSONArray("results") }
                    .getOrNull() ?: return@forEach
                for (index in 0 until videos.length()) {
                    videos.optJSONObject(index)?.takeIf {
                        it.optString("site").equals("YouTube", true) &&
                            it.optString("key").isNotBlank()
                    }?.let { candidates += it }
                }
            }
            val tier = listOf(
                candidates.filter { it.optString("type").equals("Trailer", true) && it.optString("official") == "true" },
                candidates.filter { it.optString("type").equals("Trailer", true) },
                candidates.filter { it.optString("type").equals("Teaser", true) },
                candidates
            )
            tier.forEach { group ->
                group.forEach { ordered += it.optString("key") }
            }
        }
        // TMDB'de video yoksa YouTube aramasina dus.
        if (ordered.isEmpty()) {
            searchTrailerOnYoutube(title).forEach { ordered += it }
        }
        if (ordered.isEmpty()) return emptyList()
        // Gomulmesi engellenen videolar (152-4/153) oynaticida siyah ekran
        // birakir; oEmbed ile elenir.
        val playable = LinkedHashSet<String>()
        for (key in ordered) {
            if (playable.size >= 4) break
            if (isEmbeddable(key)) playable += key
        }
        return playable.toList()
    }

    /** TMDB kimlik aramasi: once tr-TR, sonra dilsiz/en-US (farkli isimler icin). */
    private fun findTmdbId(kind: String, query: String, releaseYear: String?): Int {
        val variants = listOf("language=tr-TR", "", "language=en-US")
        for ((index, langPart) in variants.withIndex()) {
            val url = buildString {
                append("https://api.themoviedb.org/3/search/$kind?api_key=$TMDB_KEY")
                if (langPart.isNotEmpty()) append("&").append(langPart)
                append("&query=$query")
                if (index == 0 && releaseYear != null) {
                    append(if (kind == "movie") "&year=$releaseYear" else "&first_air_date_year=$releaseYear")
                }
            }
            val results = runCatching { JSONObject(tmdbGet(url)).optJSONArray("results") }.getOrNull()
            if (results != null && results.length() > 0) {
                val id = results.optJSONObject(0)?.optInt("id") ?: 0
                if (id != 0) return id
            }
        }
        return 0
    }

    /**
     * YouTube aramasi.
     *
     * Once Data API denenir; ancak anahtarin gunluk kotası (100 arama) dolu
     * olunca 429 donuyor ve fragmanlar hic bulunamıyordu. Bu yuzden anahtarsiz
     * arama sayfasi yedegi de var (kota yok, her zaman calisir).
     */
    private fun searchTrailerOnYoutube(title: String): List<String> {
        val clean = title.trim()
        if (clean.isBlank()) return emptyList()
        val queries = listOf("$clean official trailer", "$clean fragman")
        for (query in queries) {
            val viaApi = runCatching {
                val url = "https://www.googleapis.com/youtube/v3/search?part=snippet" +
                    "&q=${URLEncoder.encode(query, "UTF-8")}&type=video&maxResults=5&key=$YOUTUBE_API_KEY"
                val items = JSONObject(tmdbGet(url)).optJSONArray("items") ?: return@runCatching emptyList<String>()
                (0 until items.length()).mapNotNull { index ->
                    items.optJSONObject(index)
                        ?.optJSONObject("id")
                        ?.optString("videoId")
                        ?.takeIf { it.isNotBlank() }
                }
            }.getOrDefault(emptyList())
            if (viaApi.isNotEmpty()) return viaApi
        }
        // Anahtarsiz yedek: YouTube arama sayfasindan video kimlikleri alinir.
        for (query in queries) {
            val scraped = runCatching {
                val url = "https://www.youtube.com/results?search_query=" +
                    URLEncoder.encode(query, "UTF-8")
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val html = response.body?.string().orEmpty()
                    Regex("\\\"videoId\\\":\\\"([A-Za-z0-9_-]{11})\\\"")
                        .findAll(html)
                        .map { it.groupValues[1] }
                        .distinct()
                        .take(8)
                        .toList()
                }
            }.getOrDefault(emptyList())
            if (scraped.isNotEmpty()) return scraped
        }
        return emptyList()
    }

    /**
     * YouTube gomulmesini kapatilan videolari eler. oEmbed cevabi "html" alani
     * icermiyorsa ya da 401/403 donuyorsa gomulemez.
     */
    private fun isEmbeddable(videoId: String): Boolean {
        return runCatching {
            val oembedUrl = "https://www.youtube.com/oembed?url=" +
                "${URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")}&format=json"
            val request = Request.Builder()
                .url(oembedUrl)
                .header("Accept", "application/json")
                .header("User-Agent", BROWSER_USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 -> return false
                    response.code in 200..299 -> {
                        val text = response.body?.string().orEmpty()
                        if (!text.contains("\"html\"")) return false
                    }
                }
            }
            true
        }.getOrDefault(true)
    }

    private fun tmdbGet(url: String): String {
        // Once API anahtari ile; olmezse okuma erisim jetonu (bearer) ile dener.
        runCatching { tmdbRequest(url, null) }.getOrNull()?.let { return it }
        return tmdbRequest(url, TMDB_TOKEN)
    }

    private fun tmdbRequest(url: String, bearer: String?): String {
        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        tmdbClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("TMDB HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun parseHome(doc: Document): HomeData {
        val hero = doc.select("section[aria-label*=Öne çıkan] [role=group]").mapNotNull { slide ->
            val image = slide.selectFirst("img[src*=/backdrops/]") ?: return@mapNotNull null
            val link = slide.selectFirst("a[href*=/film/], a[href*=/dizi/], a[href*=/anime/]")
                ?: return@mapNotNull null
            val url = link.absUrl("href").ifBlank { link.attr("href") }
            val title = slide.selectFirst("h1")?.text()?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            val badges = slide.select("span.badge").map { it.text().trim() }
            val rating = slide.selectFirst("span.text-star")?.text()
                ?.let { RATING.find(it)?.groupValues?.getOrNull(1)?.toDoubleOrNull() }
            val year = slide.select("span").map { it.text().trim() }
                .firstOrNull { YEAR.matches(it) }
            val genreLine = slide.select("span.line-clamp-1").firstOrNull()?.text()?.trim()
                ?: badges.filterNot { it in BADGE_WORDS }.joinToString(", ")
            val description = slide.selectFirst("p")?.text()?.trim().orEmpty()
            // Kapak gorseli hero'da sag tarafta gosteriliyor; sitede varsa al,
            // yoksa detay sayfasindan (fillDescriptions) sonradan gelir.
            val posterImage = slide.selectFirst("img[src*=/posters/]")
            val posterUrl = posterImage?.absUrl("src")?.ifBlank { posterImage.attr("src") }.orEmpty()
            HeroSlide(
                title = title,
                url = url,
                backdrop = image.absUrl("src").ifBlank { image.attr("src") },
                type = typeOf(url),
                rating = rating,
                year = year,
                genres = genreLine,
                description = description,
                poster = posterUrl
            )
        }.distinctBy { it.url }

        val sections = mutableListOf<HomeSection>()
        doc.select("h2.section-title").forEach { heading ->
            val section = heading.closest("section") ?: return@forEach
            val cards = parseCards(section)
                .filter { !it.url.contains("/sezon-") }
                .distinctBy { it.url }
                .take(24)
            if (cards.size >= 2) {
                val moreUrl = (heading.parent()?.selectFirst("a[href]")
                    ?: section.select("a[href]")
                        .firstOrNull { it.text().contains("Tümünü", ignoreCase = true) })
                    ?.absUrl("href")
                    .orEmpty()
                    .takeIf { it.startsWith("http") }
                    .orEmpty()
                sections += HomeSection(heading.text().trim(), cards, moreUrl)
            }
        }

        val deduped = sections.map { section ->
            HomeSection(section.title, section.items, section.moreUrl)
        }
        val featured = hero.ifEmpty { buildHeroFallback(deduped) }
        return HomeData(buildHeroQueue(featured, deduped), deduped)
    }

    private fun buildHeroQueue(siteHero: List<HeroSlide>, sections: List<HomeSection>): List<HeroSlide> {
        val seen = LinkedHashMap<String, HeroSlide>()
        siteHero.forEach { seen[it.url] = it }
        val queues = sections.map { section ->
            section.items.filter { !it.url.contains("/sezon-") && it.url !in seen }
                .distinctBy { it.url }
                .toMutableList()
        }
        var guard = 0
        while (seen.size < HERO_SIZE && guard < 500) {
            var added = false
            for (queue in queues) {
                if (queue.isEmpty()) continue
                val card = queue.removeAt(0)
                if (card.url in seen) continue
                seen[card.url] = HeroSlide(
                    title = card.title,
                    url = card.url,
                    backdrop = card.poster,
                    type = card.type,
                    rating = card.rating,
                    year = card.year,
                    poster = card.poster
                )
                added = true
                if (seen.size >= HERO_SIZE) break
            }
            if (!added) break
            guard++
        }
        return seen.values.take(HERO_SIZE)
    }

    private fun buildHeroFallback(sections: List<HomeSection>): List<HeroSlide> =
        sections.flatMap { it.items }.distinctBy { it.url }.take(5).map {
            HeroSlide(
                title = it.title,
                url = it.url,
                backdrop = it.poster,
                type = it.type,
                rating = it.rating,
                year = it.year,
                poster = it.poster
            )
        }

    private fun parseCards(container: Element): List<MediaCard> {
        val cards = mutableListOf<MediaCard>()
        for (anchor in container.select("a[href]")) {
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            if (!isContentUrl(href)) continue
            val image = anchor.selectFirst("img") ?: continue
            val poster = image.absUrl("src").ifBlank { image.attr("src") }
            if (poster.isBlank()) continue
            val title = anchor.selectFirst("h3")?.text()?.trim()?.takeIf { it.isNotBlank() }
                ?: image.attr("alt").removeSuffix(" izle").trim().takeIf { it.isNotBlank() }
                ?: continue
            val normalizedUrl = normalizeContentUrl(href)
            val ratingText = anchor.selectFirst(".badge-rating, .text-star")?.text().orEmpty()
            val rating = RATING.find(ratingText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            val year = YEAR.find(anchor.text())?.value
            cards += MediaCard(
                title = title,
                url = normalizedUrl,
                poster = poster,
                type = typeOf(normalizedUrl),
                rating = rating,
                year = year
            )
        }
        val unique = cards.distinctBy { it.url }
        indexKnown(unique)
        return unique
    }

    private fun parseDetail(doc: Document, url: String): MediaDetail {
        val ld = findJsonLd(doc)
        val title = ld?.optString("name")?.trim()?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h1")?.text()?.trim().orEmpty()
        val poster = ld?.optString("image")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
        val backdrop = doc.selectFirst("img[src*=/storage/tmdb/backdrops/]")
            ?.absUrl("src")?.takeIf { it.isNotBlank() }
            ?: poster
        val baseDescription = ld?.optString("description")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("meta[name=description]")?.attr("content").orEmpty()
        // JSON-LD ozeti dizilerde cok kisadir; sayfadaki tam ozet paragrafini kullan.
        val description = findSynopsis(doc, baseDescription).ifBlank { baseDescription }
        val date = ld?.optString("datePublished").orEmpty()
            .ifBlank { ld?.optString("startDate").orEmpty() }
        val year = YEAR.find(date)?.value ?: YEAR.find(doc.text())?.value
        val rating = ld?.optJSONObject("aggregateRating")?.optDouble("ratingValue")
            ?.takeIf { !it.isNaN() }
        val genres = ld?.optJSONArray("genre").toStringList()
        val castPeople = doc.select("a[href*=/oyuncu/]").mapNotNull { anchor ->
            val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
            val image = anchor.selectFirst("img")
            val name = anchor.selectFirst("h3, p, span")?.text()?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: image?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            PersonCard(
                name = name,
                url = href,
                photo = image?.absUrl("src").orEmpty()
            )
        }.distinctBy { it.url }.take(20)
        val cast = castPeople.ifEmpty {
            ld?.optJSONArray("actor")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    val name = array.optJSONObject(i)?.optString("name")
                        ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    PersonCard(name, "", "")
                }
            }.orEmpty()
        }
        val type = when (ld?.optString("@type")) {
            "Movie" -> MediaType.MOVIE
            "TVSeries", "TVShow" -> MediaType.SERIES
            else -> typeOf(url)
        }

        val episodes = parseEpisodes(doc, null)

        val seasons = episodes.groupBy { it.season }
        val seasonCount = ld?.optInt("numberOfSeasons", 0) ?: 0
        val seasonNumbers = if (seasonCount > 0) {
            (1..seasonCount).toList()
        } else {
            seasons.keys.sorted().ifEmpty { listOf(1) }
        }
        val related = parseCards(doc)
            .filter { it.url != normalizeContentUrl(url) }
            .distinctBy { it.url }
            .take(18)

        val firstPlayUrl = when {
            type == MediaType.MOVIE -> url
            episodes.isNotEmpty() -> episodes.first().url
            else -> url
        }

        return MediaDetail(
            title = title,
            url = url,
            type = type,
            poster = poster,
            backdrop = backdrop,
            description = description,
            year = year,
            rating = rating,
            genres = genres,
            cast = cast,
            seasons = seasons,
            seasonNumbers = seasonNumbers,
            firstPlayUrl = firstPlayUrl,
            related = related
        )
    }

    private fun parseEpisodes(doc: Document, season: Int?): List<Episode> {
        return doc.select("a[href*=/sezon-][href*=/bolum-]")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                val match = EPISODE.find(href) ?: return@mapNotNull null
                val seasonNo = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val number = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                if (season != null && seasonNo != season) return@mapNotNull null
                val image = anchor.selectFirst("img")
                val fallbackTitle = if (seasonNo > 0) "$seasonNo. Sezon $number. Bölüm" else "Bölüm $number"
                val title = anchor.selectFirst("p")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() && !it.startsWith("İlk Bölüm") }
                    ?: image?.attr("alt")?.trim()?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle
                Episode(
                    season = seasonNo,
                    number = number,
                    title = title,
                    url = href,
                    still = image?.absUrl("src").orEmpty()
                )
            }
            .distinctBy { it.url }
            .sortedWith(compareBy({ it.season }, { it.number }))
    }

    /**
     * Sayfadaki tam ozet paragrafini bulur.
     * JSON-LD/meta ozeti bazen "..." ile kesilmis ya da cok kisa olabiliyor;
     * ayni metni iceren en uzun paragrafi tercih ediyoruz.
     */
    private fun findSynopsis(doc: Document, base: String): String {
        val key = base.trim().take(30)
        if (key.length < 10) return ""
        var best = ""
        for (paragraph in doc.select("p")) {
            val text = paragraph.text().trim()
            if (text.length <= base.length) continue
            if (text.contains(key, ignoreCase = true) && text.length > best.length) {
                best = text
            }
        }
        return best
    }

    private fun findJsonLd(doc: Document): JSONObject? {        for (script in doc.select("script[type=application/ld+json]")) {
            val raw = script.data()
            if (!raw.contains("\"Movie\"") && !raw.contains("\"TVSeries\"")) continue
            try {
                val root = JSONObject(raw)
                val graph = root.optJSONArray("@graph") ?: JSONArray().put(root)
                for (i in 0 until graph.length()) {
                    val node = graph.optJSONObject(i) ?: continue
                    if (node.optString("@type") in setOf("Movie", "TVSeries", "TVShow")) {
                        return node
                    }
                }
            } catch (error: Exception) {
                Log.w(TAG, "JSON-LD okunamadı", error)
            }
        }
        return null
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).takeIf { it.isNotBlank() }
        }
    }

    private suspend fun fetchDoc(url: String): Document = Jsoup.parse(fetch(url), url)

    private suspend fun fetch(url: String, referer: String? = null): String {
        var lastError: Exception? = null
        for (attempt in 0..1) {
            try {
                return fetchGate.withPermit { executeFetch(url, referer) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                lastError = error
                if (attempt == 0) delay(400L)
            }
        }
        throw lastError ?: IOException("Bağlantı kurulamadı.")
    }

    private suspend fun executeFetch(url: String, referer: String?): String = suspendCancellableCoroutine { cont ->
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.7")
        if (!referer.isNullOrBlank()) {
            builder.header("Referer", referer)
            builder.header("Origin", "https://filmmodu.news")
        }
        val call = client.newCall(builder.build())
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (cont.isActive) cont.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { result ->
                    if (!result.isSuccessful) {
                        if (cont.isActive) cont.resumeWithException(IOException("HTTP ${result.code}"))
                        return
                    }
                    val body = try {
                        result.body?.string()
                    } catch (error: Exception) {
                        null
                    }
                    if (!cont.isActive) return
                    if (body == null) cont.resumeWithException(IOException("Boş yanıt"))
                    else cont.resume(body)
                }
            }
        })
    }

    private fun pageUrl(url: String, page: Int): String {
        if (page <= 1) return url
        return if (url.contains('?')) "$url&page=$page" else "$url?page=$page"
    }

    private fun totalOf(doc: Document): Int? =
        TOTAL.find(doc.text())?.groupValues?.get(1)?.replace(".", "")?.toIntOrNull()

    private fun absolute(url: String): String {
        val value = url.trim()
        if (value.isBlank() || value.startsWith("http")) return value
        return if (value.startsWith("/")) BASE + value else "$BASE/$value"
    }

    private fun isContentUrl(url: String) =
        url.contains("/film/") || url.contains("/dizi/") || url.contains("/anime/")

    private fun normalizeContentUrl(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#")
        val episodeIndex = clean.indexOf("/sezon-")
        return if (episodeIndex > 0) clean.substring(0, episodeIndex) else clean
    }

    private fun typeOf(url: String): MediaType =
        if (url.contains("/film/")) MediaType.MOVIE else MediaType.SERIES

    private fun indexCard(card: MediaCard) {
        if (card.title.length < 2 || card.poster.isBlank()) return
        synchronized(indexLock) { searchIndex[card.url] = card }
    }

    private fun normalizeSearch(value: String): String {
        val lower = value.lowercase(Locale("tr"))
        val out = StringBuilder(lower.length)
        for (ch in lower) {
            out.append(
                when (ch) {
                    'ı', 'î' -> 'i'
                    'ç' -> 'c'
                    'ğ' -> 'g'
                    'ü', 'û' -> 'u'
                    'ş' -> 's'
                    'ö' -> 'o'
                    'â' -> 'a'
                    else -> ch
                }
            )
        }
        return out.toString().trim()
    }

    private class PageCache(private val max: Int, private val ttlMillis: Long) {        private val entries = object : LinkedHashMap<String, Pair<Long, BrowsePage>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, BrowsePage>>?) =
                size > max
        }

        @Synchronized
        fun get(key: String): BrowsePage? {
            val entry = entries[key] ?: return null
            if (System.currentTimeMillis() - entry.first > ttlMillis) {
                entries.remove(key)
                return null
            }
            return entry.second
        }

        @Synchronized
        fun put(key: String, page: BrowsePage) {
            entries[key] = System.currentTimeMillis() to page
        }

        companion object {
            const val MAX_PAGES = 240
        }
    }

    private class SuggestCache {
        private val entries = object : LinkedHashMap<String, Pair<Long, SuggestResult>>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, SuggestResult>>?) =
                size > 150
        }

        @Synchronized
        fun get(key: String): SuggestResult? {
            val entry = entries[key] ?: return null
            if (System.currentTimeMillis() - entry.first > 30 * 60_000L) {
                entries.remove(key)
                return null
            }
            return entry.second
        }

        @Synchronized
        fun put(key: String, value: SuggestResult) {
            entries[key] = System.currentTimeMillis() to value
        }
    }

    private class KeyedCache<T>(private val max: Int, private val ttlMillis: Long) {
        private val entries = object : LinkedHashMap<String, Pair<Long, T>>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, T>>?) =
                size > max
        }

        @Synchronized
        fun get(key: String): T? {
            val entry = entries[key] ?: return null
            if (System.currentTimeMillis() - entry.first > ttlMillis) {
                entries.remove(key)
                return null
            }
            return entry.second
        }

        @Synchronized
        fun put(key: String, value: T) {
            entries[key] = System.currentTimeMillis() to value
        }
    }

    private class TimedCache<T>(private val ttlMillis: Long) {
        @Volatile private var value: T? = null
        @Volatile private var storedAt: Long = 0L

        fun get(): T? {
            val current = value ?: return null
            if (System.currentTimeMillis() - storedAt > ttlMillis) return null
            return current
        }

        fun put(newValue: T) {
            value = newValue
            storedAt = System.currentTimeMillis()
        }
    }

    companion object {
        private const val TAG = "NotflixCatalog"
        private const val BASE = "https://filmmodu.news"
        private const val TMDB_KEY = "21e3ff1da3bd38d1f167560193503c34"
        private const val YOUTUBE_API_KEY = "AIzaSyDAivPXYp-wdmN2AmL7HUXvf4wHP2o9dHQ"
        private const val TMDB_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIyMWUzZmYxZGEzYmQzOGQxZjE2NzU2MDE5MzUwM2MzNCIsIm5iZiI6MTc5MzQ0MDIxOS4wNjIsInN1YiI6IjZhZTViOWRiZDU4ZTI5MGIxMmM4YWUzOSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.FxLyGjSG37R3qK1J1S_vyoyL3u3Q-7yVlp56ddPFHzM"
        private const val HERO_SIZE = 24
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private val RATING = Regex("""(\d+(?:[.,]\d+)?)""")
        private val YEAR = Regex("""(19|20)\d{2}""")
        private val EPISODE = Regex("""/sezon-(\d+)/bolum-(\d+)""")
        private val TOTAL = Regex("""Toplam\s+([\d.]+)\s+(?:film|dizi)""", RegexOption.IGNORE_CASE)
        private val BADGE_WORDS = setOf("Film", "Dizi", "Anime", "HD", "4K", "Altyazılı", "Dublaj")
    }
}
