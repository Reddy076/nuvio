package com.red.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.red.app.data.MissAvRepository
import com.red.app.theme.RedAppTheme
import com.red.app.ui.detail.DetailScreen
import com.red.app.ui.downloads.DownloadsScreen
import com.red.app.ui.home.HomeScreen
import com.red.app.ui.library.LibraryScreen
import com.red.app.ui.navigation.NavTab
import com.red.app.ui.navigation.NuvioFloatingBottomBar
import com.red.app.ui.navigation.Screen
import com.red.app.ui.navigation.rememberNuvioNavBarScrollState
import com.red.app.ui.player.PlayerScreen
import com.red.app.ui.profile.ProfileScreen
import com.red.app.ui.search.SearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        MissAvRepository.init(applicationContext)

        setContent {
            RedAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    RedAppRoot()
                }
            }
        }
    }
}

@Composable
fun RedAppRoot() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var currentTab by remember { mutableStateOf(NavTab.HOME) }
    val navBarScrollState = rememberNuvioNavBarScrollState()
    val tabStateHolder = rememberSaveableStateHolder()

    // Unified Back Navigation Handler
    val canGoBack = currentScreen !is Screen.Main || currentTab != NavTab.HOME
    BackHandler(enabled = canGoBack) {
        when (val screen = currentScreen) {
            is Screen.Player -> {
                currentScreen = Screen.Detail(screen.item)
            }
            is Screen.Detail -> {
                currentScreen = Screen.Main
            }
            is Screen.Downloads -> {
                currentScreen = Screen.Main
                currentTab = NavTab.LIBRARY
            }
            is Screen.Main -> {
                if (currentTab != NavTab.HOME) {
                    currentTab = NavTab.HOME
                }
            }
        }
    }

    Crossfade(targetState = currentScreen) { screen ->
        when (screen) {
            is Screen.Main -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    tabStateHolder.SaveableStateProvider(currentTab.name) {
                        when (currentTab) {
                            NavTab.HOME -> HomeScreen(
                                onItemClick = { item ->
                                    currentScreen = Screen.Detail(item)
                                },
                                onContinueWatchingClick = { cwItem ->
                                    val options = MissAvRepository.getStreamOptions(cwItem.mediaItem)
                                    val stream = options.first()
                                    currentScreen = Screen.Player(
                                        item = cwItem.mediaItem,
                                        streamUrl = stream.streamUrl,
                                        qualityTitle = stream.label,
                                        streamOptions = options
                                    )
                                },
                                scrollState = navBarScrollState
                            )
                            NavTab.SEARCH -> SearchScreen(
                                onItemClick = { item ->
                                    currentScreen = Screen.Detail(item)
                                }
                            )
                            NavTab.LIBRARY -> LibraryScreen(
                                onItemClick = { item ->
                                    currentScreen = Screen.Detail(item)
                                },
                                onDownloadsClick = {
                                    currentScreen = Screen.Downloads
                                }
                            )
                            NavTab.PROFILE -> ProfileScreen()
                        }
                    }

                    // 1:1 Nuvio Floating Jelly Bottom Navigation Bar
                    // Stays persistent, never destroyed across tabs, 120 FPS spring physics
                    NuvioFloatingBottomBar(
                        currentTab = currentTab,
                        onTabSelected = { tab ->
                            if (currentTab != tab) {
                                currentTab = tab
                            }
                        },
                        scrollState = navBarScrollState,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            is Screen.Downloads -> {
                DownloadsScreen(
                    onBackClick = {
                        currentScreen = Screen.Main
                        currentTab = NavTab.LIBRARY
                    }
                )
            }

            is Screen.Detail -> {
                DetailScreen(
                    item = screen.item,
                    onBackClick = {
                        currentScreen = Screen.Main
                    },
                    onPlayStream = { option, allOptions ->
                        currentScreen = Screen.Player(
                            item = screen.item,
                            streamUrl = option.streamUrl,
                            qualityTitle = option.label,
                            streamOptions = allOptions
                        )
                    }
                )
            }

            is Screen.Player -> {
                PlayerScreen(
                    item = screen.item,
                    streamUrl = screen.streamUrl,
                    qualityTitle = screen.qualityTitle,
                    streamOptions = screen.streamOptions,
                    onBackClick = {
                        currentScreen = Screen.Detail(screen.item)
                    }
                )
            }
        }
    }
}
