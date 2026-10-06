package com.gaabaariaa.music.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.gaabaariaa.music.feature.library.DETAIL_ROUTE
import com.gaabaariaa.music.feature.library.DetailScreen
import com.gaabaariaa.music.feature.library.LibraryScreen
import com.gaabaariaa.music.feature.library.detailRoute
import com.gaabaariaa.music.feature.player.MiniPlayer
import com.gaabaariaa.music.feature.player.NowPlayingScreen
import com.gaabaariaa.music.feature.player.PlayerViewModel
import com.gaabaariaa.music.feature.settings.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now_playing"
}

private data class TopLevelDestination(val route: String, val label: Int, val icon: ImageVector)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.LIBRARY, R.string.nav_library, Icons.Default.LibraryMusic),
    TopLevelDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Default.Settings)
)

@Composable
fun MusicApp(playerViewModel: PlayerViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val inNowPlaying = destination?.route == Routes.NOW_PLAYING

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (!inNowPlaying) {
                Column {
                    if (player.hasMedia) {
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
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onPlay = playerViewModel::playQueue,
                    onPlayNext = playerViewModel::playNext,
                    onAddToQueue = playerViewModel::addToQueue,
                    onOpenDetail = { type, value -> navController.navigate(detailRoute(type, value)) }
                )
            }
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
                    onOpenDetail = { type, value -> navController.navigate(detailRoute(type, value)) }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
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
                    onRepeat = playerViewModel::cycleRepeat
                )
            }
        }
    }
}
