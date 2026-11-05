package com.mtgscanner

import com.mtgscanner.ui.theme.Motion
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import com.mtgscanner.ui.home.cardsToSort
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import com.mtgscanner.ui.components.Undo
import com.mtgscanner.ui.game.GamePlayScreen
import com.mtgscanner.ui.game.GameSetupScreen
import com.mtgscanner.ui.home.FirstRunTour
import com.mtgscanner.ui.home.Tour
import com.mtgscanner.widget.CollectionWidget
import kotlinx.coroutines.flow.collectLatest
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
import com.mtgscanner.ui.components.ruleAbove
import com.mtgscanner.ui.theme.Corners
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    /** Where a widget tap asked to go ("scan" or "home"); cleared once handled. */
    private var openRequest by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest = com.mtgscanner.data.BoxLinks.route(intent.dataString) ?: intent.getStringExtra(CollectionWidget.EXTRA_OPEN)
        com.mtgscanner.util.IncomingShare.accept(intent)
    }

    /** Volume up/down add or skip the card on the Scan screen (see VolumeKeys); otherwise volume as usual. */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        val up = keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP
        if ((up || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) && com.mtgscanner.ui.scan.VolumeKeys.press(up, event.repeatCount)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.mtgscanner.ui.settings.CrashLog.install(this)
        // Reopened from Recents after Back: Android hands back the old intent, so don't repeat its share or link.
        val fromHistory = ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !fromHistory) {
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
            MtgTheme {
                // The first open downloads what the scanner uses before showing the app.
                var setupDone by remember { mutableStateOf(com.mtgscanner.data.FirstSetup.done(this@MainActivity)) }
                if (setupDone) AppRoot(openRequest) { openRequest = null }
                else com.mtgscanner.ui.home.FirstSetupScreen { setupDone = true }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private fun isTab(route: String?): Boolean = tabs.any { it.route == route }

private val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("search", "Search", Icons.Outlined.Search, Icons.Filled.Search),
    Tab("scan", "Scan", Icons.Outlined.CameraAlt, Icons.Filled.CameraAlt),
    Tab("collection", "Collection", Icons.Outlined.CollectionsBookmark, Icons.Filled.CollectionsBookmark),
    Tab("game", "Game", Icons.Outlined.Casino, Icons.Filled.Casino),
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
    // The tab bar slides away while a list scrolls down and comes back on the way up, or on a new page.
    val tabBar = remember { com.mtgscanner.ui.components.HideOnScroll() }
    LaunchedEffect(route) { tabBar.shown = true }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Haptic ticks work from any screen and view model from the start, not only after a screen that attaches them.
    com.mtgscanner.util.Haptics.Attach()
    // Reopens on the tab used last; widget, shortcut and link requests below still go where they ask.
    val startTab = remember { com.mtgscanner.ui.home.LastTab.load(ctx, tabs.map { it.route }) }
    LaunchedEffect(route) { if (route != null && tabs.any { it.route == route }) com.mtgscanner.ui.home.LastTab.save(ctx, route) }
    // The tour no longer opens by itself (first setup and the scan screen's own hints cover a first open);
    // Settings still shows it on request.
    var showTour by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // Changes anywhere in the app can be undone from one snackbar.
    LaunchedEffect(Unit) {
        Undo.events.collectLatest { e ->
            // Short and out of the way: a plain note for 2.5 s, one with Undo for 4 s (it can be swiped off).
            val result = kotlinx.coroutines.withTimeoutOrNull(if (e.undo != null) 4_000L else 2_500L) {
                snackbar.showSnackbar(e.message, actionLabel = if (e.undo != null) "Undo" else null, duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) {
                // Listed changes undo through Recent actions' history, so the list stays right.
                // Runs app-wide, so leaving right after the tap can't stop it partway.
                Undo.perform(e)
            }
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
            "collection" -> nav.goToTab("collection")
            "check" -> { com.mtgscanner.util.OpenRequests.priceCheck.value = true; nav.goToTab("scan") }
            "settings" -> nav.navigate("settings") { launchSingleTop = true }
            else -> openRequest?.takeIf { (it.startsWith("card/") || it.startsWith("browse/")) && it.substringAfter('/').toLongOrNull() != null }?.let { nav.navigate(it) }
        }
        if (openRequest != null) onOpened()
    }

    // Small counts on the tabs: cards waiting to be sorted, and scans saved until there's a connection.
    val badges by remember {
        val repo = (ctx.applicationContext as MtgApp).repository
        kotlinx.coroutines.flow.combine(repo.observeAll(), repo.observeBoxes(), repo.observePending(), com.mtgscanner.data.BoxPrefs.toSortFlow()) { cards, boxes, pending, marked ->
            TabBadges(
                toSort = runCatching { repo.cardsToSort(cards, boxes, marked).size }.getOrDefault(0) + pending.count { it.unknown },
                waiting = pending.count { !it.unknown },
            )
        }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    }.collectAsStateWithLifecycle(TabBadges())

    androidx.compose.foundation.layout.Box(Modifier) {
    Scaffold(
        containerColor = Palette.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // On Scan the bottom is busy (result card, controls, strip), so the snackbar shows at the top instead.
        snackbarHost = { if (route != "scan") com.mtgscanner.ui.components.UndoSnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route }) androidx.compose.animation.AnimatedVisibility(
                visible = tabBar.shown || route == "scan" || route == "game",
                enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) { AppTabBar(
                route, badges,
                shortcuts = { tab ->
                    fun wishlist() { com.mtgscanner.ui.home.WishlistLaunch.open(); nav.goToTab("collection") }
                    when (tab) {
                        "home" -> listOf(
                            "Stats" to { nav.navigate("stats") },
                            "Goals" to { nav.navigate("goals") },
                            "Settings" to { nav.navigate("settings") { launchSingleTop = true } },
                        )
                        "search" -> listOf("Browse all cards" to { nav.navigate("browse/-1") }, "Wishlist" to { wishlist() })
                        "scan" -> listOf(
                            "Batch scan" to { com.mtgscanner.util.OpenRequests.batch.value = true; nav.goToTab("scan") },
                            "Price check" to { com.mtgscanner.util.OpenRequests.priceCheck.value = true; nav.goToTab("scan") },
                        )
                        "collection" -> listOf(
                            "Browse all cards" to { nav.navigate("browse/-1") },
                            "Wishlist" to { wishlist() },
                            "Loans" to { nav.navigate("loans") },
                        )
                        else -> emptyList()
                    }
                },
            ) { nav.goToTab(it) } }
        },
    ) { padding ->
      // Card pictures can grow into the card page (see ui/detail/CardTransition.kt).
      SharedTransitionLayout {
       CompositionLocalProvider(LocalSharedTransition provides this) {
        NavHost(
            nav,
            startDestination = startTab,
            modifier = Modifier.padding(padding).nestedScroll(tabBar.connection),
            // Tabs crossfade quickly. A page opened on top slides in (set per page below) while the page under
            // it drifts a little to the left, and drifts back on the way back.
            enterTransition = { fadeIn(tween(Motion.base, easing = Motion.ease)) },
            exitTransition = {
                // The card page grows out of a picture on this page, so this page stays put under it.
                if (targetState.destination.route == "card/{id}") fadeOut()
                else if (isTab(targetState.destination.route)) fadeOut(tween(Motion.fast, easing = Motion.ease))
                else slideOutHorizontally(tween(Motion.slow, easing = Motion.ease)) { -it / 8 } + fadeOut(tween(Motion.slow, easing = Motion.ease), targetAlpha = 0.6f)
            },
            popEnterTransition = {
                if (initialState.destination.route == "card/{id}") fadeIn()
                else if (isTab(initialState.destination.route)) fadeIn(tween(Motion.base, easing = Motion.ease))
                else slideInHorizontally(tween(Motion.slow, easing = Motion.ease)) { -it / 8 } + fadeIn(tween(Motion.slow, easing = Motion.ease), initialAlpha = 0.6f)
            },
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
                    onOpenSet = { nav.navigate("set/$it") },
                    onOpenDeck = { nav.navigate("deck/$it") },
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
            ) { com.mtgscanner.ui.goals.GoalsScreen(onBack = { nav.popFrom(it) }, onOpenSet = { code -> nav.navigate("set/$code") }) }
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
                    onLoans = { nav.navigate("loans") },
                    onBuylist = { nav.navigate("buylist") },
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
                DeckScreen(deckId = it.arguments?.getLong("id") ?: 0L, onBack = { nav.popFrom(it) }, onOpenSet = { code -> nav.navigate("set/$code") })
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
                    // Swaps this box for the other one, so Back still goes where it did.
                    onOpenBox = { other -> nav.navigate("browse/$other") { popUpTo("browse/{boxId}") { inclusive = true } } },
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
            ) { TradeScreen(binderId = it.arguments?.getLong("boxId") ?: -1L, onBack = { nav.popFrom(it) }, onSellList = { nav.navigate("buylist") }) }
            composable(
                "loans",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { com.mtgscanner.ui.loans.LoansScreen(onBack = { nav.popFrom(it) }, onOpenCard = { id -> nav.navigate("card/$id") }) }
            composable(
                "buylist",
                enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
                popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() },
            ) { com.mtgscanner.ui.sell.SellScreen(onBack = { nav.popFrom(it) }, onBrowse = { nav.navigate("browse/-1") }, onOpenCard = { id -> nav.navigate("card/$id") }) }
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
                ScreenAnimation(this) {
                    CardDetailScreen(id = it.arguments?.getLong("id") ?: 0L, onBack = { nav.popFrom(it) }, onOpenSet = { code -> nav.navigate("set/$code") })
                }
            }
        }
       }
      }
    }
    if (route == "scan") com.mtgscanner.ui.components.UndoSnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
    if (showTour) FirstRunTour(onDone = { Tour.setSeen(ctx, true); showTour = false })
    // Tap and hold any card picture to see it big; drawn over everything.
    com.mtgscanner.ui.components.CardZoomHost()
    // Imports shared from other apps, and the offer to restore a backup on a new phone.
    com.mtgscanner.ui.settings.DataHost()
    // A list copied in another app: offer to add it (not over the camera or a game).
    if (route != "scan" && route?.startsWith("game") != true) {
        com.mtgscanner.ui.settings.ClipboardOffer(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp))
    }
    // Recent actions, from Settings › Data or by holding the snackbar.
    com.mtgscanner.ui.components.RecentActionsHost()
    }
}

/**
 * The bottom tabs: a dark bar with a hairline on top. The selected tab is in the text color (no pill);
 * Scan sits in the middle as a small accent block: it's what the app is for.
 */
@Composable
private fun AppTabBar(
    route: String?,
    badges: TabBadges,
    /** What holding a tab offers, by tab route; empty for none. */
    shortcuts: (String) -> List<Pair<String, () -> Unit>>,
    onTab: (String) -> Unit,
) {
    /** The tab whose shortcuts are open. */
    var holding by remember { mutableStateOf<String?>(null) }
    @Composable
    fun Shortcuts(tab: String) {
        DropdownMenu(expanded = holding == tab, onDismissRequest = { holding = null }, containerColor = Palette.Surface) {
            shortcuts(tab).forEach { (label, go) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { holding = null; go() })
            }
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Palette.NavBar)
            .ruleAbove()
            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
            .defaultMinSize(minHeight = 80.dp)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEach { tab ->
            val selected = route == tab.route
            if (tab.route == "scan") {
                ScanTab(selected, badges.waiting, onHold = { holding = "scan" }, menu = { Shortcuts("scan") }) { onTab(tab.route) }
            } else {
                val hasShortcuts = shortcuts(tab.route).isNotEmpty()
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTab(tab.route) },
                    modifier = Modifier.onHold(hasShortcuts) { holding = tab.route },
                    icon = {
                        Shortcuts(tab.route)
                        val n = if (tab.route == "collection") badges.toSort else 0
                        BadgedBox(badge = { if (n > 0) Badge(containerColor = Palette.Accent, contentColor = Palette.OnAccent) { Text(if (n > 99) "99+" else "$n") } }) {
                            Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = if (n > 0) "$n to sort" else null)
                        }
                    },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Palette.Text,
                        selectedTextColor = Palette.Text,
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
private fun RowScope.ScanTab(selected: Boolean, waiting: Int, onHold: () -> Unit, menu: @Composable () -> Unit, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .onHold(true, onHold)
            .semantics { this.selected = selected; role = Role.Tab }
            .clickable(onClickLabel = "Scan", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .offset(y = (-14).dp)
                .size(56.dp)
                // A small neutral shadow lifts it off the bar; no colored glow.
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(Palette.Accent)
                // A ring in the bar's color sets it into the bar.
                .border(3.dp, Palette.NavBar, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Scans saved while offline, waiting to be looked up.
            BadgedBox(badge = {
                if (waiting > 0) Badge(containerColor = Palette.SurfaceHigh, contentColor = Palette.Text) { Text(if (waiting > 99) "99+" else "$waiting") }
            }) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = if (waiting > 0) "$waiting scans waiting" else null,
                    tint = Palette.OnAccent,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        menu()
        Text(
            "Scan",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Palette.Accent else Palette.Muted,
            modifier = Modifier.offset(y = (-10).dp),
        )
    }
}

/** Counts shown on the bottom tabs. */
private data class TabBadges(val toSort: Int = 0, val waiting: Int = 0)

/**
 * Holding past the long-press time calls [onHold] and swallows the rest of the press, so the tab isn't also tapped.
 * A plain tap passes through untouched.
 */
private fun Modifier.onHold(enabled: Boolean, onHold: () -> Unit): Modifier = if (!enabled) this else composed {
    val hold by rememberUpdatedState(onHold)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var ended = false
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation(PointerEventPass.Initial)
                ended = true
            }
            if (ended) return@awaitEachGesture
            com.mtgscanner.util.Haptics.hold()
            hold()
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
                if (e.changes.none { it.pressed }) break
            }
        }
    }
}
