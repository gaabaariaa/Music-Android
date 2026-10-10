package com.gaabaariaa.music.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gaabaariaa.music.R
import com.gaabaariaa.music.core.designsystem.LocalAppSettings
import com.gaabaariaa.music.feature.artwork.ArtworkScreen
import com.gaabaariaa.music.feature.downloads.DownloadsScreen
import com.gaabaariaa.music.feature.downloads.SourceSearchScreen
import com.gaabaariaa.music.feature.health.DuplicatesScreen
import com.gaabaariaa.music.feature.home.HomeScreen
import com.gaabaariaa.music.feature.library.FavoritesState
import com.gaabaariaa.music.feature.library.FavoritesViewModel
import com.gaabaariaa.music.feature.library.LocalFavorites
import com.gaabaariaa.music.feature.playlists.LocalPlaylistActions
import com.gaabaariaa.music.feature.playlists.PlaylistActions
import com.gaabaariaa.music.feature.playlists.PlaylistsViewModel
import com.gaabaariaa.music.feature.health.HealthScreen
import com.gaabaariaa.music.feature.library.DETAIL_ROUTE
import com.gaabaariaa.music.feature.library.DetailScreen
import com.gaabaariaa.music.feature.library.LibraryScreen
import com.gaabaariaa.music.feature.library.detailRoute
import com.gaabaariaa.music.feature.lyrics.LyricsScreen
import com.gaabaariaa.music.feature.player.MiniPlayer
import com.gaabaariaa.music.feature.player.NowPlayingScreen
import com.gaabaariaa.music.feature.player.PlayerViewModel
import com.gaabaariaa.music.feature.search.SearchScreen
import com.gaabaariaa.music.feature.settings.SettingsScreen
import com.gaabaariaa.music.feature.tags.TagEditorScreen

private object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val DOWNLOADS = "downloads"
    const val DOWNLOAD_SEARCH = "download_search"
    const val HEALTH = "health"
    const val DUPLICATES = "duplicates"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now_playing"
    const val TAGS = "tags"
    const val ARTWORK = "artwork"
    const val LYRICS = "lyrics/{songId}"
}

private data class TopLevelDestination(val route: String, val label: Int, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, R.string.nav_home, Icons.Default.Home),
    TopLevelDestination(Routes.LIBRARY, R.string.nav_library, Icons.Default.LibraryMusic),
    TopLevelDestination(Routes.SEARCH, R.string.nav_search, Icons.Default.Search),
    TopLevelDestination(Routes.DOWNLOADS, R.string.nav_downloads, Icons.Default.Download),
    TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings)
)

@Composable
fun MusicApp(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    favoritesViewModel: FavoritesViewModel = hiltViewModel(),
    playlistsViewModel: PlaylistsViewModel = hiltViewModel()
) {
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val favoriteIds by favoritesViewModel.ids.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val inNowPlaying = destination?.route == Routes.NOW_PLAYING
    val hideBottomBar = inNowPlaying || destination?.route == Routes.TAGS || destination?.route == Routes.ARTWORK ||
        destination?.route == Routes.LYRICS

    // Android 13+: ask for notification permission once, when playback first starts.
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var askedNotifications by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(player.hasMedia) {
        if (player.hasMedia && !askedNotifications && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askedNotifications = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    CompositionLocalProvider(
        LocalFavorites provides FavoritesState(favoriteIds, favoritesViewModel::toggle),
        LocalPlaylistActions provides PlaylistActions(
            playlists,
            playlistsViewModel::addTo,
            playlistsViewModel::createAndAdd,
            playlistsViewModel::create
        )
    ) {
    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (!hideBottomBar) {
                Column {
                    if (player.hasMedia && settings.miniPlayerEnabled) {
                        MiniPlayer(
                            state = player,
                            onOpen = {
                                navController.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
                            },
                            onPrevious = playerViewModel::previous,
                            onPlayPause = playerViewModel::togglePlayPause,
                            onNext = playerViewModel::next
                        )
                    }
                    NavigationBar {
                        topLevelDestinations.forEach { item ->
                            NavigationBarItem(
                                selected = destination?.hierarchy?.any { it.route == item.route } == true,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(stringResource(item.label)) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            enterTransition = { if (settings.animationsEnabled) fadeIn() else EnterTransition.None },
            exitTransition = { if (settings.animationsEnabled) fadeOut() else ExitTransition.None },
            popEnterTransition = { if (settings.animationsEnabled) fadeIn() else EnterTransition.None },
            popExitTransition = { if (settings.animationsEnabled) fadeOut() else ExitTransition.None }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onPlay = playerViewModel::playQueue,
                    onOpenDetail = { type, value -> navController.navigate(detailRoute(type, value)) },
                    onOpenHealth = { navController.navigate(Routes.HEALTH) }
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onPlay = playerViewModel::playQueue,
                    onPlayNext = playerViewModel::playNext,
                    onAddToQueue = playerViewModel::addToQueue,
                    onOpenDetail = { type, value -> navController.navigate(detailRoute(type, value)) },
                    onEditTags = { navController.navigate(Routes.TAGS) },
                    onFindArtwork = { navController.navigate(Routes.ARTWORK) },
                    onOpenLyrics = { id -> navController.navigate("lyrics/$id") },
                    onOpenHealth = { navController.navigate(Routes.HEALTH) }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onPlay = playerViewModel::playQueue,
                    onPlayNext = playerViewModel::playNext,
                    onAddToQueue = playerViewModel::addToQueue,
                    onOpenDetail = { type, value -> navController.navigate(detailRoute(type, value)) },
                    onEditTags = { navController.navigate(Routes.TAGS) },
                    onFindArtwork = { navController.navigate(Routes.ARTWORK) },
                    onOpenLyrics = { id -> navController.navigate("lyrics/$id") }
                )
            }
            composable(Routes.DOWNLOADS) {
                DownloadsScreen(
                    onOpenSearch = { navController.navigate(Routes.DOWNLOAD_SEARCH) },
                    onPlay = playerViewModel::playQueue
                )
            }
            composable(Routes.DOWNLOAD_SEARCH) { SourceSearchScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.HEALTH) {
                HealthScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDetail = { type, value -> navController.navigate(detailRoute(type, value)) },
                    onOpenDuplicates = { navController.navigate(Routes.DUPLICATES) }
                )
            }
            composable(Routes.DUPLICATES) { DuplicatesScreen(onBack = { navController.popBackStack() }) }
            composable(
                route = DETAIL_ROUTE,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("value") { type = NavType.StringType }
                )
            ) {
                DetailScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = playerViewModel::playQueue,
                    onPlayNext = playerViewModel::playNext,
                    onAddToQueue = playerViewModel::addToQueue,
                    onOpenDetail = { type, value -> navController.navigate(detailRoute(type, value)) },
                    onEditTags = { navController.navigate(Routes.TAGS) },
                    onFindArtwork = { navController.navigate(Routes.ARTWORK) },
                    onOpenLyrics = { id -> navController.navigate("lyrics/$id") }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(
                route = Routes.LYRICS,
                arguments = listOf(navArgument("songId") { type = NavType.LongType })
            ) { LyricsScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.ARTWORK) { ArtworkScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.TAGS) { TagEditorScreen(onBack = { navController.popBackStack() }) }
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(
                    state = player,
                    onBack = { navController.popBackStack() },
                    onPrevious = playerViewModel::previous,
                    onPlayPause = playerViewModel::togglePlayPause,
                    onNext = playerViewModel::next,
                    onSeek = playerViewModel::seekTo,
                    onSelect = playerViewModel::playQueueIndex,
                    onShuffle = playerViewModel::toggleShuffle,
                    onRepeat = playerViewModel::cycleRepeat,
                    onSpeed = playerViewModel::setSpeed,
                    onSleepTimer = playerViewModel::startSleepTimer,
                    onCancelSleepTimer = playerViewModel::cancelSleepTimer,
                    onOpenLyrics = { id -> navController.navigate("lyrics/$id") }
                )
            }
        }
    }
    }
}
