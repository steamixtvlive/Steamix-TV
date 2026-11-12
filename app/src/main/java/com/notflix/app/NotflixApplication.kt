package com.notflix.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.notflix.app.data.AppSettings
import com.notflix.app.data.FilmmoduRepository
import com.notflix.app.data.LiveTvRepository
import com.notflix.app.data.UpdateRepository
import com.notflix.app.data.YouTubeTrailerResolver

class NotflixApplication : Application(), ImageLoaderFactory {

    lateinit var settings: AppSettings
        private set
    lateinit var content: FilmmoduRepository
        private set
    lateinit var live: LiveTvRepository
        private set
    lateinit var updates: UpdateRepository
        private set
    lateinit var trailers: YouTubeTrailerResolver
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = AppSettings(this)
        content = FilmmoduRepository()
        live = LiveTvRepository(this)
        updates = UpdateRepository()
        trailers = YouTubeTrailerResolver()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(false)
        .allowHardware(true)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("poster_cache"))
                .maxSizeBytes(320L * 1024 * 1024)
                .build()
        }
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.20)
                .build()
        }
        .build()

    companion object {
        lateinit var instance: NotflixApplication
            private set
    }
}
