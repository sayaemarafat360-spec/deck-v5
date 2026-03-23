package com.sayaem.nebula

import androidx.compose.ui.*
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sayaem.nebula.data.models.Song
import com.sayaem.nebula.ui.Screen
import com.sayaem.nebula.ui.components.MiniPlayer
import com.sayaem.nebula.ui.screens.*
import com.sayaem.nebula.notifications.DeckToastEngine
import com.sayaem.nebula.notifications.DeckToastOverlay
import com.sayaem.nebula.backend.BackendViewModel
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val backendVm: BackendViewModel by viewModels()

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> backendVm.handleGoogleSignInResult(result) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) try { vm.scanMedia() } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { DeckRoot(vm, backendVm, onGoogleSignIn = { googleSignInLauncher.launch(backendVm.getGoogleSignInIntent(this)) }) }
        requestPermissions()
        // Schedule today's 10 engagement notifications
        com.sayaem.nebula.notifications.DeckNotificationEngine(this).scheduleDailyNotifications()
    }

    private fun requestPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }
}

// ── Folder drill-down state ───────────────────────────────────────────────
data class FolderContent(
    val name: String,
    val songs: List<Song>,
    val videos: List<Song>,
)

@Composable
fun DeckRoot(
    vm: MainViewModel,
    backendVm: BackendViewModel,
    onGoogleSignIn: () -> Unit = {},
) {
    val isDark          by vm.isDark.collectAsStateWithLifecycle()
    val songs           by vm.songs.collectAsStateWithLifecycle()
    val videos          by vm.videos.collectAsStateWithLifecycle()
    val playback        by vm.playback.collectAsStateWithLifecycle()
    val favorites       by vm.favoriteSongs.collectAsStateWithLifecycle()
    val playlists       by vm.playlists.collectAsStateWithLifecycle()
    val recentSongs     by vm.recentSongs.collectAsStateWithLifecycle()
    val recentlyAdded   by vm.recentlyAdded.collectAsStateWithLifecycle()
    val audioSessionId  by vm.audioSessionId.collectAsStateWithLifecycle()
    val eqState         by vm.eqState.collectAsStateWithLifecycle()
    val sleepTimer      by vm.sleepTimer.collectAsStateWithLifecycle()
    val speed           by vm.playbackSpeed.collectAsStateWithLifecycle()

    val backendUser  by backendVm.user.collectAsStateWithLifecycle()
    val isPremium    by backendVm.isPremium.collectAsStateWithLifecycle()
    val prices       by backendVm.prices.collectAsStateWithLifecycle()
    val isScanning   by vm.isScanning.collectAsStateWithLifecycle()
    val backendMsg   by backendVm.message.collectAsStateWithLifecycle()

    // ── Nav state ─────────────────────────────────────────────────────
    var currentTab    by remember { mutableStateOf<Screen>(Screen.Home) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showEqualizer  by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showSpeed      by remember { mutableStateOf(false) }
    var showSearch     by remember { mutableStateOf(false) }
    // Video player queue state — stores the full list + which index to start at
    var videoQueue     by remember { mutableStateOf<List<com.sayaem.nebula.data.models.Song>>(emptyList()) }
    var videoStartIdx  by remember { mutableIntStateOf(0) }
    var showSplash     by remember { mutableStateOf(true) }
    var showOnboarding by remember { mutableStateOf(!vm.store.isOnboardingDone()) }
    var openFolder     by remember { mutableStateOf<FolderContent?>(null) }
    var openVideoFolder by remember { mutableStateOf<Pair<String, List<com.sayaem.nebula.data.models.Song>>?>(null) }
    var optionsSong    by remember { mutableStateOf<Song?>(null) }
    var optionsVideo   by remember { mutableStateOf<Song?>(null) }
    var editingTagSong by remember { mutableStateOf<Song?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(backendMsg) {
        backendMsg?.let {
            // Route backend messages through DeckToastEngine
            if (it.startsWith("Signed in")) DeckToastEngine.signedIn(it.removePrefix("Signed in as ").removeSuffix(" ✓"))
            else if (it.startsWith("Sign")) DeckToastEngine.error(it)
            else DeckToastEngine.info(it)
            backendVm.clearMessage()
        }
    }

    LaunchedEffect(backendUser) {
        if (backendUser != null) {
            backendVm.pullAndMerge(
                onFavorites = { cloudFavs ->
                    // Merge cloud favorites into local and refresh the StateFlow
                    val local = vm.store.getFavorites()
                    val merged = local + cloudFavs
                    vm.store.saveFavorites(merged)
                    vm.reloadFavorites()  // triggers UI update immediately
                },
                onPlaylists = { cloudPlaylists ->
                    // Always use the larger set to avoid data loss from other devices
                    if (cloudPlaylists.size > vm.store.getPlaylists().size) {
                        vm.store.savePlaylists(cloudPlaylists)
                        vm.refreshPlaylists()  // triggers UI update immediately
                    }
                }
            )
        }
    }

    DeckTheme(darkTheme = isDark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            // Back handlers
            if (optionsSong != null)   BackHandler { optionsSong = null }
            if (optionsVideo != null)  BackHandler { optionsVideo = null }
            if (editingTagSong != null) BackHandler { editingTagSong = null }
            if (showSearch)            BackHandler { showSearch = false }
            if (showSpeed)             BackHandler { showSpeed = false }
            if (showSleepTimer)        BackHandler { showSleepTimer = false }
            if (showEqualizer)         BackHandler { showEqualizer = false }
            if (showNowPlaying)        BackHandler { showNowPlaying = false }
            if (videoQueue.isNotEmpty()) BackHandler { videoQueue = emptyList() }
            if (openFolder != null)    BackHandler { openFolder = null }
            if (openVideoFolder != null) BackHandler { openVideoFolder = null }
            BackHandler(enabled = currentTab != Screen.Home) { currentTab = Screen.Home }
            BackHandler(enabled = currentTab == Screen.Home) { /* swallow */ }

            if (showSplash) {
                DeckSplashScreen(onFinished = { showSplash = false })
            } else if (showOnboarding) {
                OnboardingScreen(onDone = {
                    vm.store.markOnboardingDone()
                    showOnboarding = false
                })
            } else {

                // ── Main scaffold ──────────────────────────────────────
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        Column {
                            AnimatedVisibility(
                                visible = playback.currentSong != null && videoQueue.isEmpty(),
                                enter   = slideInVertically { it } + fadeIn(),
                                exit    = slideOutVertically { it } + fadeOut()
                            ) {
                                MiniPlayer(
                                    state        = playback,
                                    onTogglePlay = { vm.player.togglePlay() },
                                    onNext       = { vm.player.next() },
                                    onExpand     = { showNowPlaying = true }
                                )
                            }
                            if (videoQueue.isEmpty()) {
                                DeckBottomNav(currentTab) { tab -> currentTab = tab }
                            }
                        }
                    }
                ) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (currentTab) {
                            Screen.Home -> HomeScreen(
                                songs         = songs,
                                videos        = videos,
                                recentSongs   = recentSongs,
                                recentlyAdded = recentlyAdded,
                                playbackState = playback,
                                onSongClick   = { vm.playSong(it); showNowPlaying = true },
                                onVideoClick  = { clicked ->
                                    videoQueue = videos
                                    videoStartIdx = videos.indexOf(clicked).coerceAtLeast(0)
                                },
                                onMoreSong    = { optionsSong = it },
                                onMoreVideo   = { optionsVideo = it },
                                onResumeClick = { showNowPlaying = true },
                                onSearchClick = { showSearch = true },
                                onRefresh     = { vm.scanMedia() },
                                isPremium     = isPremium,
                                isScanning    = isScanning,
                            )
                            Screen.Videos -> VideosScreen(
                                videos        = videos,
                                onVideoClick  = { clicked ->
                                    videoQueue = videos
                                    videoStartIdx = videos.indexOf(clicked).coerceAtLeast(0)
                                },
                                onMoreClick    = { optionsVideo = it },
                                onSearchClick  = { showSearch = true },
                                onRefresh      = { vm.scanMedia() },
                                onFolderClick  = { name, folderVids ->
                                    openVideoFolder = Pair(name, folderVids)
                                },
                            )
                            Screen.Music -> MusicScreen(
                                songs              = songs,
                                currentSong        = playback.currentSong,
                                isPlaying          = playback.isPlaying,
                                favorites          = favorites,
                                playlists          = playlists,
                                onSongClick        = { vm.playSong(it); showNowPlaying = true },
                                onMoreClick        = { optionsSong = it },
                                onPlayNext         = { vm.playNext(it) },
                                onAddToQueue       = { vm.addToQueue(it) },
                                onPlayPlaylist     = { vm.playPlaylist(it); showNowPlaying = true },
                                onCreatePlaylist   = { vm.createPlaylist(it) },
                                onDeletePlaylist   = { vm.deletePlaylist(it) },
                                onRenamePlaylist   = { id, n -> vm.renamePlaylist(id, n) },
                                onAddSongToPlaylist = { pid, sid -> vm.addSongToPlaylist(pid, sid) },
                                onRemoveSongFromPlaylist = { pid, sid -> vm.removeSongFromPlaylist(pid, sid) },
                                onSearchClick      = { showSearch = true },
                            )
                            Screen.More -> MoreScreen(
                                isDark              = isDark,
                                currentUser         = backendUser,
                                isPremium           = isPremium,
                                onSignIn            = onGoogleSignIn,
                                onSignOut           = { backendVm.signOut() },
                                onToggleTheme       = vm::toggleTheme,
                                onEqualizerClick    = { showEqualizer = true },
                                onPremiumClick      = { currentTab = Screen.Premium },
                                onStatsClick        = { currentTab = Screen.Stats },
                                onSleepTimerClick   = { showSleepTimer = true },
                                onRescan            = { vm.scanMedia() },
                                onGaplessChanged    = { vm.setGapless(it) },
                                onSmartSkipChanged  = { vm.setSmartSkipEnabled(it) },
                                onCrossfadeChanged  = { vm.setCrossfade(it) },
                                onVolumeNormChanged = { vm.setVolumeNorm(it) },
                                onFadeOnPauseChanged = { vm.setFadeOnPause(it) },
                                initialGapless      = vm.store.getGapless(),
                                initialSmartSkip    = vm.store.getSmartSkip(),
                                initialCrossfade    = vm.store.getCrossfade(),
                                initialVolumeNorm   = vm.store.prefs.getBoolean("vol_norm", false),
                            )
                            Screen.Premium -> PremiumScreen(
                                onBack     = { currentTab = Screen.Home },
                                isPremium  = isPremium,
                                premiumPlan = backendVm.premiumPlan.collectAsStateWithLifecycle().value,
                                prices     = prices,
                                onPurchase = { plan -> backendVm.grantPremium(plan) },
                            )
                            Screen.Stats -> StatsScreen(
                                songs = songs,
                                stats = vm.listeningStats.collectAsStateWithLifecycle().value,
                                topSongs = vm.topSongs.collectAsStateWithLifecycle().value,
                                totalMinutes = vm.totalMinutes.collectAsStateWithLifecycle().value,
                                onBack = { currentTab = Screen.Home }
                            )
                            else -> HomeScreen(
                                songs = songs, videos = videos, recentSongs = recentSongs,
                                recentlyAdded = recentlyAdded, playbackState = playback,
                                onSongClick = { vm.playSong(it) }, onVideoClick = {},
                                onMoreSong = {}, onMoreVideo = {}, onResumeClick = {},
                                onSearchClick = { showSearch = true }, onRefresh = { vm.scanMedia() },
                                isPremium = isPremium,
                            )
                        }
                    }
                }

                // ── Search overlay ────────────────────────────────────
                AnimatedVisibility(visible = showSearch,
                    enter = fadeIn(tween(180)) + slideInVertically { -40 },
                    exit  = fadeOut(tween(150)) + slideOutVertically { -40 }) {
                    SearchOverlay(
                        songs         = songs,
                        videos        = videos,
                        onSongClick   = { vm.playSong(it); showNowPlaying = true; showSearch = false },
                        onVideoClick  = { clicked ->
                videoQueue = videos; videoStartIdx = videos.indexOf(clicked).coerceAtLeast(0)
                showSearch = false
            },
                        onDismiss     = { showSearch = false }
                    )
                }

                // ── Now Playing ───────────────────────────────────────
                AnimatedVisibility(visible = showNowPlaying,
                    enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    NowPlayingScreen(
                        state            = playback,
                        currentSpeed     = speed,
                        sleepTimerState  = sleepTimer,
                        isFavorite       = playback.currentSong?.let { vm.isFavorite(it.id) } ?: false,
                        onTogglePlay     = { vm.player.togglePlay() },
                        onNext           = { vm.player.next() },
                        onPrev           = { vm.player.previous() },
                        onSeek           = { vm.player.seekToFraction(it) },
                        onToggleShuffle  = { vm.player.toggleShuffle() },
                        onCycleRepeat    = { vm.player.cycleRepeat() },
                        onClose          = { showNowPlaying = false },
                        onEqualizerClick = { showEqualizer = true },
                        onSleepTimer     = { showSleepTimer = true },
                        onSpeedClick     = { showSpeed = true },
                        onShare          = { vm.shareSong(it) },
                        onToggleFavorite = { vm.toggleFavorite(it) },
                        onQueueSeekTo    = { vm.player.seekToIndex(it) },
                        onAddBookmark    = { vm.addBookmark() },
                        onSeekToBookmark = { vm.seekToBookmark(it) },
                        onDeleteBookmark = { vm.deleteBookmark(it) },
                        onEditTag        = { editingTagSong = it },
                        audioSessionId   = audioSessionId,
                    )
                    // Sheets shown ON TOP of NowPlaying — must be siblings inside same
                    // AnimatedVisibility so they render above the NowPlaying Box(fillMaxSize)
                    if (showSleepTimer) {
                        SleepTimerSheet(state = sleepTimer, onStart = { vm.startSleepTimer(it) },
                            onCancel = { vm.cancelSleepTimer() }, onDismiss = { showSleepTimer = false })
                    }
                    if (showSpeed) {
                        SpeedPickerSheet(current = speed, onSelect = { vm.setSpeed(it) },
                            onDismiss = { showSpeed = false })
                    }
                }

                // ── Equalizer ─────────────────────────────────────────
                AnimatedVisibility(visible = showEqualizer,
                    enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        EqualizerScreen(
                            eqState         = eqState,
                            onBandChanged   = { band, value -> vm.setEqBand(band, value) },
                            onPresetChanged = { vm.applyEqPreset(it) },
                            onToggleEq      = { vm.toggleEq() },
                            onBack              = { showEqualizer = false },
                            onSaveForSong       = { vm.saveEqForCurrentSong() },
                            currentSongTitle    = playback.currentSong?.title,
                        )
                    }
                }

                // Sleep/Speed also shown when NOT in NowPlaying (triggered from MoreScreen)
                if (!showNowPlaying && showSleepTimer) {
                    SleepTimerSheet(state = sleepTimer, onStart = { vm.startSleepTimer(it) },
                        onCancel = { vm.cancelSleepTimer() }, onDismiss = { showSleepTimer = false })
                }
                if (!showNowPlaying && showSpeed) {
                    SpeedPickerSheet(current = speed, onSelect = { vm.setSpeed(it) },
                        onDismiss = { showSpeed = false })
                }

                // ── Song Options (audio) ──────────────────────────────
                optionsSong?.let { song ->
                    val favs by vm.favorites.collectAsStateWithLifecycle()
                    SongOptionsSheet(
                        song = song, isFavorite = song.id in favs, playlists = playlists,
                        onDismiss = { optionsSong = null },
                        onPlayNow = { vm.playSong(song); showNowPlaying = true },
                        onPlayNext = { vm.playNext(song) },
                        onAddToQueue = { vm.addToQueue(song) },
                        onAddToPlaylist = { pid -> vm.addSongToPlaylist(pid, song.id) },
                        onCreateAndAddPlaylist = { name -> vm.createPlaylistAndAddSong(name, song) },
                        onToggleFavorite = { vm.toggleFavorite(song) },
                        onEditTags = { editingTagSong = song; optionsSong = null },
                        onShare = { vm.shareSong(song) },
                        onDelete = { vm.deleteSong(song) {} },
                    )
                }

                // ── Video Options ─────────────────────────────────────
                optionsVideo?.let { video ->
                    VideoOptionsSheet(
                        video = video, onDismiss = { optionsVideo = null },
                        onPlayNow = {
                        videoQueue = videos; videoStartIdx = videos.indexOf(video).coerceAtLeast(0)
                        optionsVideo = null
                    },
                        onShare = { vm.shareSong(video); optionsVideo = null },
                        onDelete = { vm.deleteSong(video) {}; optionsVideo = null },
                    )
                }

                // ── Tag Editor ────────────────────────────────────────
                editingTagSong?.let { song ->
                    TagEditorScreen(
                        song   = song,
                        onSave = { t, ar, al -> vm.updateTags(song, t, ar, al) { editingTagSong = null } },
                        onBack = { editingTagSong = null }
                    )
                }

                // ── Folder content ────────────────────────────────────
                openFolder?.let { folder ->
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        FolderContentScreen(
                            folderName   = folder.name,
                            songs        = folder.songs,
                            videos       = folder.videos,
                            onSongClick  = { vm.playSong(it); showNowPlaying = true; openFolder = null },
                            onVideoClick = { clicked ->
                                val folderVids = folder.videos
                                videoQueue = folderVids
                                videoStartIdx = folderVids.indexOf(clicked).coerceAtLeast(0)
                                openFolder = null
                            },
                            onMoreSong   = { optionsSong = it },
                            onMoreVideo  = { optionsVideo = it },
                            onBack       = { openFolder = null }
                        )
                    }
                }

                // ── Video Player ──────────────────────────────────────
                AnimatedVisibility(visible = videoQueue.isNotEmpty(),
                    enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                    if (videoQueue.isNotEmpty()) {
                        VideoPlayerScreen(
                            videos       = videoQueue,
                            startIndex   = videoStartIdx,
                            player       = vm.player.playerOrNull,
                            onPauseMusic = { if (vm.playback.value.isPlaying) vm.player.togglePlay() },
                            onBack       = { videoQueue = emptyList() }
                        )
                    }
                }

            } // end main content

            // ── In-app toast overlay — always on top ────────────────
            DeckToastOverlay()
        }
    }
}

// ── Bottom nav — Playit style ─────────────────────────────────────────────
@Composable
fun DeckBottomNav(current: Screen, onNavigate: (Screen) -> Unit) {
    val tabs = listOf(
        Triple(Screen.Home,   Icons.Filled.Home,         "Home"),
        Triple(Screen.Videos, Icons.Filled.VideoLibrary, "Videos"),
        Triple(Screen.Music,  Icons.Filled.MusicNote,    "Music"),
        Triple(Screen.More,   Icons.Filled.Menu,         "More"),
    )
    NavigationBar(containerColor = DarkBgSecondary, tonalElevation = 0.dp) {
        tabs.forEach { (screen, icon, label) ->
            NavigationBarItem(
                selected = current == screen,
                onClick  = { onNavigate(screen) },
                icon     = { Icon(icon, label, modifier = Modifier.size(22.dp)) },
                label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors   = NavigationBarItemDefaults.colors(
                    selectedIconColor   = NebulaViolet,
                    selectedTextColor   = NebulaViolet,
                    unselectedIconColor = LocalAppColors.current.textTertiary,
                    unselectedTextColor = LocalAppColors.current.textTertiary,
                    indicatorColor      = NebulaViolet.copy(alpha = 0.15f),
                )
            )
        }
    }
}

// ── Search overlay — floats over any tab ──────────────────────────────────
@Composable
fun SearchOverlay(
    songs: List<Song>,
    videos: List<Song>,
    onSongClick: (Song) -> Unit,
    onVideoClick: (Song) -> Unit,
    onDismiss: () -> Unit,
) {
    val appColors     = LocalAppColors.current
    var query         by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard      = LocalSoftwareKeyboardController.current

    // Auto-focus + show keyboard when search opens
    // Use 200ms delay — 100ms is sometimes too short before composition settles
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
        keyboard?.show()
    }
    val allMedia = remember(songs, videos) { songs + videos }
    val results  = remember(query, allMedia) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.lowercase()
            allMedia.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).clickable(onClick = onDismiss)) {
        Column(
            Modifier.fillMaxWidth()
                .clickable(enabled = false) {}
                .background(appColors.bg)
                .statusBarsPadding()
        ) {
            // Search bar
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(appColors.card)
                        .border(0.5.dp, appColors.border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = appColors.textTertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query, onValueChange = { query = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = appColors.textPrimary),
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search songs, videos, artists…",
                                style = MaterialTheme.typography.bodyMedium, color = appColors.textTertiary)
                            inner()
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, null, tint = appColors.textTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = NebulaViolet, style = MaterialTheme.typography.labelLarge)
                }
            }

            HorizontalDivider(color = appColors.borderSubtle, thickness = 0.5.dp)

            // Results
            if (query.isBlank()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Search, null, tint = appColors.textTertiary,
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Type to search your library", style = MaterialTheme.typography.bodyMedium,
                            color = appColors.textTertiary)
                    }
                }
            } else if (results.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No results for \"$query\"", style = MaterialTheme.typography.bodyMedium,
                        color = appColors.textTertiary)
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(results.size) { i ->
                        val item = results[i]
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (item.isVideo) onVideoClick(item) else onSongClick(item)
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (item.isVideo) NebulaRed.copy(0.15f) else NebulaViolet.copy(0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (item.isVideo) Icons.Filled.VideoFile else Icons.Filled.MusicNote,
                                    null,
                                    tint = if (item.isVideo) NebulaRed else NebulaViolet,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium,
                                    color = appColors.textPrimary, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (item.isVideo) item.sizeFormatted else item.artist,
                                    style = MaterialTheme.typography.bodySmall, color = appColors.textTertiary
                                )
                            }
                            Text(item.durationFormatted, style = MaterialTheme.typography.labelSmall,
                                color = appColors.textTertiary)
                        }
                        HorizontalDivider(Modifier.padding(start = 72.dp), color = appColors.borderSubtle, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
