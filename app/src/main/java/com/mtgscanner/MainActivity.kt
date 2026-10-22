package com.mtgscanner

import android.graphics.Color as AndroidColor
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
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
import com.mtgscanner.ui.detail.CARD_GROW_MS
import com.mtgscanner.ui.detail.CardDetailScreen
import com.mtgscanner.ui.detail.LocalSharedTransition
import com.mtgscanner.ui.detail.ScreenAnimation
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
        openRequest = com.mtgscanner.data.BoxLinks.route(intent.dataString) ?: intent.getStringExtra(CollectionWidget.EXTRA_OPEN)
        com.mtgscanner.util.IncomingShare.accept(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.mtgscanner.ui.settings.CrashLog.install(this)
        if (savedInstanceState == null) {
            // A box's QR code or NFC tag (grimoire://box/12) opens that box.
            openRequest = com.mtgscanner.data.BoxLinks.route(intent?.dataString) ?: intent?.getStringExtra(CollectionWidget.EXTRA_OPEN)
            // A CSV, decklist or deck link shared to the app or opened with it.
            com.mtgscanner.util.IncomingShare.accept(intent)
        }
        Palette.systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // The window shows behind the first frame and during transitions: match the app's theme, not the XML's dark one.
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Palette.Background.toArgb()))
        setContent {
            // Status and navigation bar icons follow the app's own light/dark choice.
            val dark = Palette.dark
            LaunchedEffect(dark) {
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Palette.Background.toArgb()))
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

/** Whether [this] screen is the one showing, not one already on its way out. */
private val NavBackStackEntry.isResumed: Boolean
    get() = lifecycle.currentState == Lifecycle.State.RESUMED

/**
 * Goes back from [entry]'s screen. A second tap on Back while the screen is still animating out
 * would otherwise pop the screen under it too (often Home), leaving a blank page.
 */
fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (entry.isResumed) popBackStack()
}

/** Switches bottom tabs the standard way: one copy of each tab, state kept. */
fun NavHostController.goToTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppRoot(openRequest: String?, onOpened: () -> Unit) {
    com.mtgscanner.util.Haptics.Attach()
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
    // A message saved before a restart (restoring a backup), with its Undo.
    com.mtgscanner.ui.settings.RestartNoticeEffect()
    LaunchedEffect(openRequest) {
        // From the widget, app shortcuts and notifications.
        when (openRequest) {
            "scan" -> nav.goToTab("scan")
            "home" -> nav.goToTab("home")
            "search" -> nav.goToTab("search")
            "check" -> { com.mtgscanner.util.OpenRequests.priceCheck.value = true; nav.goToTab("scan") }
            "settings" -> nav.navigate("settings") { launchSingleTop = true }
            else -> openRequest?.takeIf { (it.startsWith("card/") || it.startsWith("browse/")) && it.substringAfter('/').toLongOrNull() != null }?.let { nav.navigate(it) }
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
            if (tabs.any { it.route == route }) AppTabBar(route) { nav.goToTab(it) }
        },
    ) { padding ->
      // Card pictures can grow into the card page (see ui/detail/CardTransition.kt).
      SharedTransitionLayout {
       CompositionLocalProvider(LocalSharedTransition provides this) {
        NavHost(
            nav,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            composable("home") {
              ScreenAnimation(this) {
                HomeScreen(
                    onScan = { nav.goToTab("scan") },
                    onSearch = { nav.goToTab("search") },
                    onCollection = { nav.goToTab("collection") },
                    onOpenCard = { nav.navigate("card/$it") },
                    onSettings = { nav.navigate("settings") },
                    onBrowseAll = { nav.navigate("browse/-1") },
                    onGoals = { nav.navigate("goals") },
                    onStats = { nav.navigate("stats") },
                )
              }
            }
            composable(
                "stats",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
              // Card pictures on Stats grow into the card page too.
              ScreenAnimation(this) {
                com.mtgscanner.ui.home.StatsScreen(
                    onBack = { nav.popFrom(it) },
                    onOpenCard = { id -> nav.navigate("card/$id") },
                    onBrowseAll = { nav.navigate("browse/-1") },
                )
              }
            }
            composable(
                "goals",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { com.mtgscanner.ui.goals.GoalsScreen(onBack = { nav.popFrom(it) }) }
            composable("scan") { ScreenAnimation(this) { ScanScreen(onOpenCard = { nav.navigate("card/$it") }) } }
            composable("search") { SearchScreen(onOpenSet = { nav.navigate("set/$it") }, onOpenCard = { nav.navigate("card/$it") }) }
            composable("collection") {
              ScreenAnimation(this) {
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
            }
            composable("game") { GameSetupScreen(onPlay = { nav.navigate("game/play") }) }
            composable("game/play") { GamePlayScreen(onExit = { if (it.isResumed) nav.popBackStack("game", inclusive = false) }) }
            composable(
                "sets",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { com.mtgscanner.ui.collection.SetsScreen(onBack = { nav.popFrom(it) }, onOpenSet = { nav.navigate("set/$it") }) }
            composable(
                "set/{code}",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                com.mtgscanner.ui.collection.SetDetailScreen(
                    code = it.arguments?.getString("code").orEmpty(),
                    onBack = { nav.popFrom(it) },
                    onOpenCard = { id -> nav.navigate("card/$id") },
                )
            }
            composable(
                "extras",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { com.mtgscanner.ui.collection.ExtrasScreen(onBack = { nav.popFrom(it) }) }
            composable(
                "deck/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                DeckScreen(deckId = it.arguments?.getLong("id") ?: 0L, onBack = { nav.popFrom(it) })
            }
            composable(
                "browse/{boxId}",
                arguments = listOf(navArgument("boxId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
              ScreenAnimation(this) {
                BrowseScreen(
                    boxId = it.arguments?.getLong("boxId") ?: -1L,
                    onBack = { nav.popFrom(it) },
                    onOpenCard = { id -> nav.navigate("card/$id") },
                    onTrade = { binderId -> nav.navigate("trade/$binderId") },
                )
              }
            }
            composable(
                "settings",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) {
                SettingsScreen(
                    onBack = { nav.popFrom(it) },
                    onScanLog = { nav.navigate("scanlog") },
                    onTour = { showTour = true },
                )
            }
            composable(
                "trade/{boxId}",
                arguments = listOf(navArgument("boxId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { TradeScreen(binderId = it.arguments?.getLong("boxId") ?: -1L, onBack = { nav.popFrom(it) }, onSellList = { nav.navigate("sell") }) }
            composable(
                "sell",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { com.mtgscanner.ui.sell.SellScreen(onBack = { nav.popFrom(it) }, onBrowse = { nav.navigate("browse/-1") }) }
            composable(
                "scanlog",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { ScanLogScreen(onBack = { nav.popFrom(it) }) }
            // The card page grows out of the picture tapped, and shrinks back into it.
            composable(
                "card/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
                enterTransition = { scaleIn(tween(CARD_GROW_MS), initialScale = 0.9f) + fadeIn(tween(CARD_GROW_MS - 60)) },
                popExitTransition = { scaleOut(tween(CARD_GROW_MS - 40), targetScale = 0.9f) + fadeOut(tween(CARD_GROW_MS - 80)) },
            ) {
                ScreenAnimation(this) { CardDetailScreen(id = it.arguments?.getLong("id") ?: 0L, onBack = { nav.popFrom(it) }) }
            }
        }
       }
      }
    }
    if (route == "scan") SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
    if (showTour) FirstRunTour(onDone = { Tour.setSeen(ctx, true); showTour = false })
    // Tap and hold any card picture to see it big; drawn over everything.
    com.mtgscanner.ui.components.CardZoomHost()
    // Imports shared from other apps, and the offer to restore a backup on a new phone.
    com.mtgscanner.ui.settings.DataHost()
    }
}

/**
 * The bottom tabs. Scan sits in the middle as a bigger, raised accent button: it's what the app is
 * for. The other tabs look like a standard navigation bar.
 */
@Composable
private fun AppTabBar(route: String?, onTab: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.NavBar)
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .defaultMinSize(minHeight = 80.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { tab ->
            val selected = route == tab.route
            if (tab.route == "scan") {
                ScanTab(selected) { onTab(tab.route) }
            } else {
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTab(tab.route) },
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
}

/** The raised Scan button: a solid accent circle that sits a little above the bar. */
@Composable
private fun RowScope.ScanTab(selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .semantics { this.selected = selected; role = Role.Tab }
            .clickable(onClickLabel = "Scan", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .offset(y = (-14).dp)
                .size(58.dp)
                .shadow(10.dp, CircleShape, ambientColor = Palette.Accent, spotColor = Palette.Accent)
                .clip(CircleShape)
                .background(Palette.Accent)
                // A ring in the bar's color sets it into the bar.
                .border(3.dp, Palette.NavBar, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Palette.OnAccent, modifier = Modifier.size(28.dp))
        }
        Text(
            "Scan",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Palette.Accent else Palette.Muted,
            modifier = Modifier.offset(y = (-10).dp),
        )
    }
}
