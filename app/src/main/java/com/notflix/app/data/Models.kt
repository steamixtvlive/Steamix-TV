package com.notflix.app.data

enum class MediaType { MOVIE, SERIES }

data class MediaCard(
    val title: String,
    val url: String,
    val poster: String,
    val type: MediaType,
    val rating: Double? = null,
    val year: String? = null,
    val rank: Int? = null
)

data class HeroSlide(
    val title: String,
    val url: String,
    val backdrop: String,
    val type: MediaType,
    val rating: Double? = null,
    val year: String? = null,
    val genres: String = "",
    val description: String = ""
)

data class HomeSection(
    val title: String,
    val items: List<MediaCard>,
    val moreUrl: String = ""
)

data class HomeData(
    val hero: List<HeroSlide>,
    val sections: List<HomeSection>
)

data class Episode(
    val season: Int,
    val number: Int,
    val title: String,
    val url: String,
    val still: String = ""
)

data class MediaDetail(
    val title: String,
    val url: String,
    val type: MediaType,
    val poster: String,
    val backdrop: String,
    val description: String,
    val year: String?,
    val rating: Double?,
    val genres: List<String>,
    val cast: List<PersonCard>,
    val seasons: Map<Int, List<Episode>>,
    val seasonNumbers: List<Int>,
    val firstPlayUrl: String,
    val related: List<MediaCard>
)

data class BrowsePage(
    val items: List<MediaCard>,
    val hasMore: Boolean,
    val total: Int? = null
)

data class PersonCard(
    val name: String,
    val url: String,
    val photo: String
)

data class SuggestResult(
    val media: List<MediaCard>,
    val people: List<PersonCard>
)

data class PlayInfo(
    val streamUrl: String,
    val title: String,
    val poster: String,
    val referer: String,
    val audios: List<String> = emptyList(),
    val subtitles: List<String> = emptyList()
)

data class LiveChannel(
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
    val stream: String = "",
    val tvg: String = "",
    val programs: List<TvProgram> = emptyList(),
    val alternates: List<String> = emptyList()
)

data class TvProgram(
    val title: String,
    val startMs: Long,
    val endMs: Long
)
