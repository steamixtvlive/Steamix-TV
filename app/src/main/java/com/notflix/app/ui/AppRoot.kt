package com.notflix.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notflix.app.BuildConfig
import com.notflix.app.NotflixApplication
import com.notflix.app.data.LiveChannel
import com.notflix.app.data.MediaType
import com.notflix.app.data.UpdateInfo
import com.notflix.app.data.UpdateResult
import com.notflix.app.ui.brand.NotflixBloom
import com.notflix.app.ui.brand.NotflixWordmark
import com.notflix.app.ui.components.NavTab
import com.notflix.app.ui.components.NotflixButton
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
import kotlinx.coroutines.delay
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
    NavTab("discover", "Tavsiye"),
    NavTab("series", "Diziler"),
    NavTab("movies", "Filmler"),
    NavTab("live", "Canlı TV"),
    NavTab("continue", "Devam Et"),
    NavTab("library", "Kitaplığım"),
    NavTab("top250", "Top 250")
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as NotflixApplication

    var stage by remember { mutableStateOf(Stage.Splash) }
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var liveChannels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    val current = stack.lastOrNull() ?: Screen.Home
    // Ekrandan cikinca kaybolan kaydirma/kategori secimleri burada tutulur.
    val memory = remember { UiMemory() }
    var liveFavorites by remember { mutableStateOf(app.settings.liveFavorites().toSet()) }

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

    // Uygulamadan temiz cikis: arka planda bir sey birakilmaz.
    fun exitApp() {
        val activity = context.findActivity()
        runCatching { activity?.finishAndRemoveTask() }
        kotlin.system.exitProcess(0)
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

    var exitConfirm by remember { mutableStateOf(false) }
    var exitPlaying by remember { mutableStateOf(false) }
    BackHandler(enabled = stage == Stage.Main) {
        // Ana ekrandayken geri tusu dogrudan kapatmaz; once onay sorulur.
        when {
            exitPlaying -> Unit
            exitConfirm -> exitConfirm = false
            stack.size > 1 -> back()
            else -> exitConfirm = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                                onSeeAll = { title, moreUrl -> push(Screen.Collection(title, moreUrl)) },
                                onRemoveResume = { entry -> app.settings.removeWatch(entry.seriesKey) },
                                listState = memory.list("home"),
                                resumeListState = memory.list("home-resume"),
                                autoFocusHero = !memory.heroFocusHandled,
                                onAutoFocusDone = { memory.heroFocusHandled = true },
                                memory = memory
                            )
                            is Screen.Discover -> DiscoverScreen(
                                onItemClick = { card -> push(Screen.Detail(card.url, card.title)) }
                            )
                            is Screen.Series -> BrowseScreen(
                                type = MediaType.SERIES,
                                onItemClick = { card -> push(Screen.Detail(card.url, card.title)) },
                                gridState = memory.grid("browse-series"),
                                initialGenre = memory.browseGenre("series"),
                                onGenreChange = { slug -> memory.setBrowseGenre("series", slug) }
                            )
                            is Screen.Movies -> BrowseScreen(
                                type = MediaType.MOVIE,
                                onItemClick = { card -> push(Screen.Detail(card.url, card.title)) },
                                gridState = memory.grid("browse-movies"),
                                initialGenre = memory.browseGenre("movies"),
                                onGenreChange = { slug -> memory.setBrowseGenre("movies", slug) }
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
                                onItemClick = { card -> push(Screen.Detail(card.url, card.title)) },
                                gridState = memory.grid("collection-" + screen.title)
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
                                onChannelsLoaded = { liveChannels = it },
                                gridState = memory.grid("live"),
                                initialGroup = memory.liveGroup("live"),
                                onGroupChange = { group -> memory.setLiveGroup("live", group) },
                                favoriteUrls = liveFavorites,
                                onToggleFavorite = { channel ->
                                    app.settings.toggleLiveFavorite(channel.url)
                                    liveFavorites = app.settings.liveFavorites().toSet()
                                }
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
                                    },
                                    onClearAll = {
                                        app.settings.clearPositions()
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
                                },
                                onExitApp = { exitApp() }
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

        if (stage == Stage.Main && exitConfirm) {
            ExitConfirmOverlay(
                onCancel = { exitConfirm = false },
                onConfirm = {
                    exitConfirm = false
                    exitPlaying = true
                }
            )
        }
        if (exitPlaying) {
            ExitGlowOverlay(onDone = { exitApp() })
        }
    }
}

@Composable
private fun ExitConfirmOverlay(onCancel: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { cancelFocus.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF141013))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 34.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NotflixWordmark(width = 124.dp, glow = true)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Uygulamadan çıkılsın mı?",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    shadow = Shadow(
                        color = NotflixColors.Accent.copy(alpha = 0.55f),
                        blurRadius = 18f
                    )
                )
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "NOTFLİX tamamen kapanacak.",
                color = NotflixColors.TextSecondary,
                fontSize = 12.5.sp
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NotflixButton(
                    text = "Vazgeç",
                    onClick = onCancel,
                    modifier = Modifier
                        .width(132.dp)
                        .focusRequester(cancelFocus)
                )
                NotflixButton(
                    text = "Çıkış Yap",
                    onClick = onConfirm,
                    primary = false,
                    modifier = Modifier.width(132.dp)
                )
            }
        }
    }
}

@Composable
private fun ExitGlowOverlay(onDone: () -> Unit) {
    // Cikista splash isigini ters yonde akitip uygulamayi kapatir.
    val sweep = remember { Animatable(1f) }
    val bloom = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        launch { sweep.animateTo(0f, tween(durationMillis = 900, easing = LinearEasing)) }
        launch { bloom.animateTo(0f, tween(durationMillis = 900, easing = LinearEasing)) }
        delay(760)
        fade.animateTo(0f, tween(durationMillis = 280))
        onDone()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0506))
            .graphicsLayer { alpha = fade.value },
        contentAlignment = Alignment.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            NotflixBloom(
                modifier = Modifier.width(220.dp).height(62.dp),
                intensity = bloom.value
            )
            NotflixWordmark(width = 152.dp, glow = true, sweep = sweep.value)
        }
    }
}
