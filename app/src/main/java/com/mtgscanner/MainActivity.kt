package com.mtgscanner

import android.graphics.Color as AndroidColor
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mtgscanner.ui.collection.BrowseScreen
import com.mtgscanner.ui.collection.CollectionScreen
import com.mtgscanner.ui.decks.DeckScreen
import com.mtgscanner.ui.detail.CardDetailScreen
import com.mtgscanner.ui.home.HomeScreen
import com.mtgscanner.ui.scan.ScanScreen
import com.mtgscanner.ui.search.SearchScreen
import com.mtgscanner.ui.settings.ScanLogScreen
import com.mtgscanner.ui.trade.TradeScreen
import com.mtgscanner.ui.settings.SettingsScreen
import com.mtgscanner.ui.theme.MtgTheme
import com.mtgscanner.ui.theme.Palette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Palette.systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        setContent {
            // Status and navigation bar icons follow the app's own light/dark choice.
            val dark = Palette.dark
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            MtgTheme { AppRoot() }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("scan", "Scan", Icons.Outlined.CameraAlt, Icons.Filled.CameraAlt),
    Tab("search", "Search", Icons.Outlined.Search, Icons.Filled.Search),
    Tab("collection", "Collection", Icons.Outlined.CollectionsBookmark, Icons.Filled.CollectionsBookmark),
)

/** Switches bottom tabs the standard way: one copy of each tab, state kept. */
fun NavHostController.goToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        containerColor = Palette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = Palette.NavBar, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    tabs.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.goToTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.Accent,
                                selectedTextColor = Palette.Accent,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = Palette.Muted,
                                unselectedTextColor = Palette.Muted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable("home") {
                HomeScreen(
                    onScan = { nav.goToTab("scan") },
                    onSearch = { nav.goToTab("search") },
                    onCollection = { nav.goToTab("collection") },
                    onOpenCard = { nav.navigate("card/$it") },
                    onSettings = { nav.navigate("settings") },
                )
            }
            composable("scan") { ScanScreen(onOpenCard = { nav.navigate("card/$it") }) }
            composable("search") { SearchScreen() }
            composable("collection") {
                CollectionScreen(
                    onBrowse = { boxId -> nav.navigate("browse/$boxId") },
                    onSettings = { nav.navigate("settings") },
                    onOpenDeck = { id -> nav.navigate("deck/$id") },
                    onOpenCard = { id -> nav.navigate("card/$id") },
                    onTrade = { nav.navigate("trade") },
                )
            }
            composable(
                "deck/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                DeckScreen(deckId = it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
            composable(
                "browse/{boxId}",
                arguments = listOf(navArgument("boxId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                BrowseScreen(
                    boxId = it.arguments?.getLong("boxId") ?: -1L,
                    onBack = { nav.popBackStack() },
                    onOpenCard = { id -> nav.navigate("card/$id") },
                )
            }
            composable(
                "settings",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { SettingsScreen(onBack = { nav.popBackStack() }, onScanLog = { nav.navigate("scanlog") }) }
            composable(
                "trade",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { TradeScreen(onBack = { nav.popBackStack() }) }
            composable(
                "scanlog",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { ScanLogScreen(onBack = { nav.popBackStack() }) }
            composable(
                "card/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                CardDetailScreen(id = it.arguments?.getLong("id") ?: 0L, onBack = { nav.popBackStack() })
            }
        }
    }
}
