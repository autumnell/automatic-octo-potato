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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.content.Intent
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.game.GamePlayScreen
import com.mtgscanner.ui.game.GameSetupScreen
import com.mtgscanner.ui.home.FirstRunTour
import com.mtgscanner.ui.home.Tour
import com.mtgscanner.widget.CollectionWidget
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
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
    /** Where a widget tap asked to go ("scan" or "home"); cleared once handled. */
    private var openRequest by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest = intent.getStringExtra(CollectionWidget.EXTRA_OPEN)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest = intent?.getStringExtra(CollectionWidget.EXTRA_OPEN)
        Palette.systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        setContent {
            // Status and navigation bar icons follow the app's own light/dark choice.
            val dark = Palette.dark
            LaunchedEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
            }
            MtgTheme { AppRoot(openRequest) { openRequest = null } }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("game", "Game", Icons.Outlined.Casino, Icons.Filled.Casino),
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
private fun AppRoot(openRequest: String?, onOpened: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var showTour by remember { mutableStateOf(!Tour.seen(ctx)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Changes anywhere in the app can be undone from one snackbar.
    LaunchedEffect(Unit) {
        Undo.events.collectLatest { e ->
            val result = snackbar.showSnackbar(
                e.message,
                actionLabel = if (e.undo != null) "Undo" else null,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) e.undo?.let { u -> scope.launch { runCatching { u() } } }
        }
    }
    LaunchedEffect(openRequest) {
        when (openRequest) {
            "scan" -> nav.goToTab("scan")
            "home" -> nav.goToTab("home")
        }
        if (openRequest != null) onOpened()
    }

    androidx.compose.foundation.layout.Box(Modifier) {
    Scaffold(
        containerColor = Palette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // On Scan the bottom is busy (result card, controls, strip), so the snackbar shows at the top instead.
        snackbarHost = { if (route != "scan") SnackbarHost(snackbar) },
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
                    onBrowseAll = { nav.navigate("browse/-1") },
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
                    onTrade = { binderId -> nav.navigate("trade/$binderId") },
                    onSets = { nav.navigate("sets") },
                    onExtras = { nav.navigate("extras") },
                )
            }
            composable("game") { GameSetupScreen(onPlay = { nav.navigate("game/play") }) }
            composable("game/play") { GamePlayScreen(onExit = { nav.popBackStack("game", inclusive = false) }) }
            composable(
                "sets",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { com.mtgscanner.ui.collection.SetsScreen(onBack = { nav.popBackStack() }, onOpenSet = { nav.navigate("set/$it") }) }
            composable(
                "set/{code}",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                com.mtgscanner.ui.collection.SetDetailScreen(
                    code = it.arguments?.getString("code").orEmpty(),
                    onBack = { nav.popBackStack() },
                    onOpenCard = { id -> nav.navigate("card/$id") },
                )
            }
            composable(
                "extras",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { com.mtgscanner.ui.collection.ExtrasScreen(onBack = { nav.popBackStack() }) }
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
                    onTrade = { binderId -> nav.navigate("trade/$binderId") },
                )
            }
            composable(
                "settings",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    onScanLog = { nav.navigate("scanlog") },
                    onTour = { showTour = true },
                )
            }
            composable(
                "trade/{boxId}",
                arguments = listOf(navArgument("boxId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { TradeScreen(binderId = it.arguments?.getLong("boxId") ?: -1L, onBack = { nav.popBackStack() }, onSellList = { nav.navigate("sell") }) }
            composable(
                "sell",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { com.mtgscanner.ui.sell.SellScreen(onBack = { nav.popBackStack() }, onBrowse = { nav.navigate("browse/-1") }) }
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
    if (route == "scan") SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
    if (showTour) FirstRunTour(onDone = { Tour.setSeen(ctx, true); showTour = false })
    }
}
