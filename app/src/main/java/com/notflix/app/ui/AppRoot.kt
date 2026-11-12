package com.notflix.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.notflix.app.BuildConfig
import com.notflix.app.NotflixApplication
import com.notflix.app.data.LiveChannel
import com.notflix.app.data.MediaType
import com.notflix.app.data.UpdateInfo
import com.notflix.app.data.UpdateResult
import com.notflix.app.ui.components.NavTab
import com.notflix.app.ui.components.NotflixTopBar
import com.notflix.app.ui.screens.browse.BrowseScreen
import com.notflix.app.ui.screens.collection.CollectionScreen
import com.notflix.app.ui.screens.detail.DetailScreen
import com.notflix.app.ui.screens.discover.DiscoverScreen
import com.notflix.app.ui.screens.home.HomeScreen
import com.notflix.app.ui.screens.intro.IntroScreen
import com.notflix.app.ui.screens.library.ContinueScreen
import com.notflix.app.ui.screens.library.LibraryScreen
import com.notflix.app.ui.screens.live.LiveScreen
import com.notflix.app.ui.screens.player.PlayerScreen
import com.notflix.app.ui.screens.search.SearchScreen
import com.notflix.app.ui.screens.settings.SettingsScreen
import com.notflix.app.ui.screens.splash.SplashScreen
import com.notflix.app.ui.screens.top.Top250Screen
import com.notflix.app.ui.screens.update.UpdateGateScreen
import com.notflix.app.ui.theme.NotflixColors
import kotlinx.coroutines.launch

private enum class Stage { Splash, Intro, Main }

private sealed interface Screen {
    data object Home : Screen
    data object Discover : Screen
    data object Series : Screen
    data object Movies : Screen
    data object Live : Screen
    data object Continue : Screen
    data object Library : Screen
    data object Top250 : Screen
    data object Search : Screen
    data object Settings : Screen
    data class Collection(val title: String, val moreUrl: String = "") : Screen
    data class Genre(val slug: String, val label: String) : Screen
    data class Detail(val url: String, val title: String) : Screen
    data class Player(
        val pageUrl: String,
        val title: String,
        val liveUrl: String?,
        val liveIndex: Int = -1
    ) : Screen
}

private val TABS = listOf(
    NavTab("home", "Ana Sayfa"),
    NavTab("discover", "Keşfet"),
    NavTab("series", "Diziler"),
    NavTab("movies", "Filmler"),
    NavTab("live", "Canlı TV"),
    NavTab("continue", "Devam Et"),
    NavTab("library", "Kitaplığım"),
    NavTab("top250", "Top 250"),
    NavTab("search", "Ara")
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication

    var stage by remember { mutableStateOf(Stage.Splash) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var liveChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    val current = stack.lastOrNull() ?: Screen.Home

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun openRoot(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateCheckBusy by remember { mutableStateOf(false) }
    val updateScope = rememberCoroutineScope()

    fun checkUpdate() {
        if (updateCheckBusy) return
        updateCheckBusy = true
        updateScope.launch {
            val result = app.updates.checkLatest(BuildConfig.VERSION_CODE)
            pendingUpdate = (result as? UpdateResult.Available)?.info
            updateCheckBusy = false
        }
    }

    LaunchedEffect(Unit) { checkUpdate() }

    pendingUpdate?.let { info ->
        UpdateGateScreen(info = info, onRetryCheck = { checkUpdate() })
        return
    }

    BackHandler(enabled = stage == Stage.Main) {
        back()
    }

    when (stage) {
        Stage.Splash -> SplashScreen(onDone = {
            stage = if (app.settings.introSeen) Stage.Main else Stage.Intro
        })
        Stage.Intro -> IntroScreen(onFinish = {
            app.settings.introSeen = true
            stage = Stage.Main
        })
        Stage.Main -> {
            val rootTab = when (current) {
                is Screen.Home -> "home"
                is Screen.Discover -> "discover"
                is Screen.Series -> "series"
                is Screen.Movies -> "movies"
                is Screen.Live -> "live"
                is Screen.Continue -> "continue"
                is Screen.Library -> "library"
                is Screen.Top250 -> "top250"
                is Screen.Search -> "search"
                else -> ""
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NotflixColors.Bg)
            ) {
                if (rootTab.isNotBlank()) {
                    NotflixTopBar(
                        tabs = TABS,
                        currentTab = rootTab,
                        onTabSelected = { id ->
                            openRoot(
                                when (id) {
                                    "discover" -> Screen.Discover
                                    "series" -> Screen.Series
                                    "movies" -> Screen.Movies
                                    "live" -> Screen.Live
                                    "continue" -> Screen.Continue
                                    "library" -> Screen.Library
                                    "top250" -> Screen.Top250
                                    "search" -> Screen.Search
                                    else -> Screen.Home
                                }
                            )
                        },
                        onSearch = { openRoot(Screen.Search) },
                        onSettings = { push(Screen.Settings) },
                        onLogoClick = { openRoot(Screen.Home) }
                    )
                }
                Crossfade(
                    targetState = current,
                    animationSpec = tween(durationMillis = 220),
                    label = "screen"
                ) { screen ->
                    when (screen) {
                        is Screen.Home -> HomeScreen(
                            onPlay = { url, title -> push(Screen.Player(url, title, null)) },
                            onOpenDetail = { url, title -> push(Screen.Detail(url, title)) },
                            onSeeAll = { title, moreUrl -> push(Screen.Collection(title, moreUrl)) }
                        )
                        is Screen.Discover -> DiscoverScreen(
                            onItemClick = { card -> push(Screen.Detail(card.url, card.title)) }
                        )
                        is Screen.Series -> BrowseScreen(
                            type = MediaType.SERIES,
                            onItemClick = { card -> push(Screen.Detail(card.url, card.title)) }
                        )
                        is Screen.Movies -> BrowseScreen(
                            type = MediaType.MOVIE,
                            onItemClick = { card -> push(Screen.Detail(card.url, card.title)) }
                        )
                        is Screen.Genre -> BrowseScreen(
                            type = MediaType.MOVIE,
                            initialGenre = screen.slug,
                            heading = screen.label,
                            onItemClick = { card -> push(Screen.Detail(card.url, card.title)) }
                        )
                        is Screen.Collection -> CollectionScreen(
                            title = screen.title,
                            moreUrl = screen.moreUrl,
                            onBack = { back() },
                            onItemClick = { card -> push(Screen.Detail(card.url, card.title)) }
                        )
                        is Screen.Live -> LiveScreen(
                            onChannelClick = { channel ->
                                val index = liveChannels.indexOfFirst { it.url == channel.url }
                                push(
                                    Screen.Player(
                                        pageUrl = channel.url,
                                        title = channel.name,
                                        liveUrl = channel.url,
                                        liveIndex = index
                                    )
                                )
                            },
                            onChannelsLoaded = { liveChannels = it }
                        )
                        is Screen.Continue -> {
                            var entries by remember(screen) {
                                mutableStateOf(app.settings.watchHistory())
                            }
                            ContinueScreen(
                                entries = entries,
                                onResume = { entry ->
                                    push(Screen.Player(entry.url, entry.title, null))
                                },
                                onOpenDetail = { entry ->
                                    push(Screen.Detail(entry.url, entry.title))
                                },
                                onRemove = { entry ->
                                    app.settings.removeWatch(entry.seriesKey)
                                    entries = app.settings.watchHistory()
                                }
                            )
                        }
                        is Screen.Library -> LibraryScreen(
                            settings = app.settings,
                            onItemClick = { card -> push(Screen.Detail(card.url, card.title)) }
                        )
                        is Screen.Top250 -> Top250Screen(
                            onItemClick = { card -> push(Screen.Detail(card.url, card.title)) }
                        )
                        is Screen.Search -> SearchScreen(
                            onItemClick = { card -> push(Screen.Detail(card.url, card.title)) },
                            onPersonClick = { person ->
                                push(Screen.Collection(person.name, person.url))
                            }
                        )
                        is Screen.Settings -> SettingsScreen(
                            onBack = { back() },
                            onReplayIntro = {
                                openRoot(Screen.Home)
                                stage = Stage.Intro
                            }
                        )
                        is Screen.Detail -> DetailScreen(
                            url = screen.url,
                            onBack = { back() },
                            onPlay = { url, title -> push(Screen.Player(url, title, null)) },
                            onOpenDetail = { url, title -> push(Screen.Detail(url, title)) },
                            onPersonClick = { person ->
                                push(Screen.Collection(person.name, person.url))
                            }
                        )
                        is Screen.Player -> PlayerScreen(
                            pageUrl = screen.pageUrl,
                            title = screen.title,
                            liveUrl = screen.liveUrl,
                            liveChannels = liveChannels,
                            liveIndex = screen.liveIndex,
                            onBack = { back() }
                        )
                    }
                }
            }
        }
    }
}
