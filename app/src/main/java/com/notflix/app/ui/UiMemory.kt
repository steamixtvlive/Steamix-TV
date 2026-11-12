package com.notflix.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

/**
 * Ekranlar arasinda "geri dondugunde yerini hatirla" icin kalici tutucu.
 *
 * Uygulamada navigasyon bir yigin (stack) uzerinden kuruluyor ve sadece
 * aktif ekran compose ediliyor. Bu yuzden ekrandan cikinca kaydirma konumu,
 * secili kategori ve oda kayboluyor. Bu sinif bu durumlari tutuyor.
 */
class UiMemory {

    private val gridStates = mutableMapOf<String, LazyGridState>()
    private val listStates = mutableMapOf<String, LazyListState>()
    private val scrollStates = mutableMapOf<String, ScrollState>()
    private val browseGenres = mutableMapOf<String, String?>()
    private val liveGroups = mutableMapOf<String, String>()

    fun grid(key: String): LazyGridState = gridStates.getOrPut(key) { LazyGridState() }

    fun list(key: String): LazyListState = listStates.getOrPut(key) { LazyListState() }

    fun scroll(key: String): ScrollState = scrollStates.getOrPut(key) { ScrollState(0) }

    /** Film/dizi ekraninda secili turun hatirlanmasi (null = "Tumu"). */
    fun browseGenre(key: String): String? = browseGenres[key]

    fun setBrowseGenre(key: String, slug: String?) {
        browseGenres[key] = slug
    }

    /** Canli TV ekraninda secili kategorinin hatirlanmasi. */
    fun liveGroup(key: String): String? = liveGroups[key]

    fun setLiveGroup(key: String, group: String) {
        liveGroups[key] = group
    }
}
