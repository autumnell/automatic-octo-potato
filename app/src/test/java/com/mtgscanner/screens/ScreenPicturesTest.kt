package com.mtgscanner.screens

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.mtgscanner.ui.theme.Space
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mtgscanner.ui.decks.BracketBody
import com.mtgscanner.ui.decks.GoldfishBody
import com.mtgscanner.ui.decks.ManaBaseBody
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mtgscanner.MtgApp
import com.mtgscanner.data.db.AppDatabase
import com.mtgscanner.ui.theme.MtgTheme
import com.mtgscanner.ui.theme.Palette
import com.mtgscanner.ui.theme.ThemeMode
import com.mtgscanner.ui.theme.ThemeSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** The app with a sample collection in memory and no background jobs, for drawing screens. */
class ShotApp : MtgApp() {
    override fun openDatabase(): AppDatabase =
        Room.inMemoryDatabaseBuilder(this, AppDatabase::class.java).build()
    override val backgroundJobs: Boolean get() = false
}

/**
 * Draws the main screens with a sample collection, in dark and light and with large text, into
 * build/screens/ for checking layouts without a phone. CI runs it with SCREEN_PICTURES=1 and posts the pictures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = ShotApp::class, qualifiers = "w393dp-h852dp-xhdpi")
class ScreenPicturesTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val out = File("build/screens")

    @Before
    fun setUp() {
        assumeTrue("Set SCREEN_PICTURES=1 to draw the screens", System.getenv("SCREEN_PICTURES") == "1")
        out.mkdirs()
        val app = ApplicationProvider.getApplicationContext<ShotApp>()
        runBlocking {
            if (app.repository.db.cardDao().all().isEmpty()) SampleCollection.fill(app.repository)
        }
    }

    /**
     * Lets loading finish (lists are worked out off the main thread) and entrance animations end. The clock is
     * moved by hand: some screens keep a timer or a shimmer running, so Compose is never fully idle.
     */
    private fun settle() {
        repeat(16) {
            Thread.sleep(120)
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeBy(250)
        }
    }

    /** The window drawn into a picture (Compose's own capture waits for an idle that never comes). */
    private fun capture(): Bitmap {
        val view = rule.activity.window.decorView
        return Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
            view.draw(android.graphics.Canvas(it))
        }
    }

    private var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var fontScale by mutableFloatStateOf(1f)
    private var started = false

    /** Draws [screen] in each look and saves it as [name]-dark.png, -light.png and -large.png. */
    private fun shoot(name: String, screen: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        val looks = listOf(Triple("dark", ThemeMode.DARK, 1f), Triple("light", ThemeMode.LIGHT, 1f), Triple("large", ThemeMode.DARK, 1.3f))
        for ((label, m, scale) in looks) {
            fontScale = scale
            ThemeSettings.updateMode(m)
            content = screen
            if (!started) {
                started = true
                rule.setContent {
                    val d = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                        MtgTheme {
                            // Like the app's Scaffold: the page color, and text in the page's text color.
                            androidx.compose.material3.Surface(
                                Modifier.fillMaxSize(),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                            ) { content?.invoke() }
                        }
                    }
                }
            }
            settle()
            val bmp = capture()
            File(out, "$name-$label.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        // Take the screen away so no timer or endless animation is left running: the test rule waits for Compose
        // to go idle when the test ends, and a running shimmer or ticker would keep it busy until it times out.
        content = null
        settle()
    }

    @Test fun home() = shoot("01-home") {
        com.mtgscanner.ui.home.HomeScreen(onScan = {}, onSearch = {}, onCollection = {}, onOpenCard = {}, onSettings = {})
    }

    @Test fun collection() = shoot("02-collection") {
        com.mtgscanner.ui.collection.CollectionScreen(onBrowse = {}, onSettings = {}, onOpenDeck = {}, startTab = com.mtgscanner.ui.collection.CollectionTab.BOXES)
    }

    @Test fun collectionAll() = shoot("02b-collection-all") {
        com.mtgscanner.ui.collection.CollectionScreen(onBrowse = {}, onSettings = {}, onOpenDeck = {}, startTab = com.mtgscanner.ui.collection.CollectionTab.ALL)
    }

    @Test fun browse() = shoot("03-browse-all") {
        com.mtgscanner.ui.collection.BrowseScreen(boxId = -1L, onBack = {}, onOpenCard = {})
    }

    @Test fun card() = shoot("04-card") {
        com.mtgscanner.ui.detail.CardDetailScreen(id = 1L, onBack = {})
    }

    @Test fun deck() = shoot("05-deck") {
        com.mtgscanner.ui.decks.DeckScreen(deckId = 1L, onBack = {})
    }

    @Test fun deckPictures() = shoot("05b-deck-pictures") {
        com.mtgscanner.ui.decks.DeckScreen(deckId = 1L, onBack = {}, startPictures = true)
    }

    @Test fun deckActions() = shoot("05c-deck-actions") {
        com.mtgscanner.ui.decks.DeckScreen(deckId = 1L, onBack = {}, onPlaytest = {}, startMenu = true)
    }

    @Test fun deckEditCards() = shoot("05d-deck-edit-cards") {
        com.mtgscanner.ui.decks.DeckScreen(deckId = 1L, onBack = {}, startEditTab = com.mtgscanner.ui.decks.EditTab.CARDS)
    }

    /** The deck page's insight sheets, drawn as their sheet would show them, with numbers like a real deck's. */
    @Composable
    private fun InsightSheet(title: String, body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().background(com.mtgscanner.ui.theme.Palette.Surface).padding(top = Space.s4),
        ) {
            androidx.compose.material3.Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Space.s4))
            body()
        }
    }

    @Test fun deckBracket() = shoot("05e-deck-bracket") {
        val b = com.mtgscanner.ui.decks.Bracket(2, emptyList(), emptyList(), emptyList(), listOf("Fabricate", "From Father to Son", "Guidelight Pathmaker"), emptyList())
        InsightSheet("Bracket 2: Core") { BracketBody(b) }
    }

    @Test fun deckManaBase() = shoot("05f-deck-mana-base") {
        val m = com.mtgscanner.ui.decks.ManaBase(36, 39, 3.05, mapOf('W' to 22, 'U' to 18), mapOf('W' to 17, 'U' to 22))
        InsightSheet("Mana base") { ManaBaseBody(m) }
    }

    @Test fun deckGoldfish() = shoot("05g-deck-goldfish") {
        val g = com.mtgscanner.ui.decks.Goldfish(1000, 0.78, listOf(0.96, 0.84, 0.69, 0.51), 0.69, 3, 3.8)
        InsightSheet("Goldfish") { GoldfishBody(g, true) {} }
    }

    @Test fun search() = shoot("06-search") {
        com.mtgscanner.ui.search.SearchScreen()
    }

    @Test fun stats() = shoot("07-stats") {
        com.mtgscanner.ui.home.StatsScreen(onBack = {}, onOpenCard = {}, onBrowseAll = {})
    }

    @Test fun settings() = shoot("08-settings") {
        com.mtgscanner.ui.settings.SettingsScreen(onBack = {})
    }

    @Test fun game() = shoot("09-game-setup") {
        com.mtgscanner.ui.game.GameSetupScreen(onPlay = {})
    }

    @Test fun gamePlayers() = shoot("09c-game-players") {
        com.mtgscanner.ui.game.GameSetupScreen(onPlay = {}, startAt = 1)
    }

    @Test fun gameSeats() = shoot("09d-game-seats") {
        com.mtgscanner.ui.game.GameSetupScreen(onPlay = {}, startAt = 2)
    }

    @Test fun gameNames() = shoot("09e-game-names") {
        com.mtgscanner.ui.game.GameSetupScreen(onPlay = {}, startAt = 3)
    }

    @Test fun gamePanelBack() = shoot("09f-game-panel-back") {
        // The back of a panel at a 4-player table (player 1, on the left side, short and wide), then at a
        // 2-player table with partners (the near seat, taller). The damage tiles sit where the players do.
        val g = com.mtgscanner.game.GameState.new(com.mtgscanner.game.GameFormat.COMMANDER, 4)
            .let { com.mtgscanner.game.Game.commanderDamage(it, 0, 1, 7, false) }
            .let { com.mtgscanner.game.Game.poison(it, 0, 2) }
        val two = com.mtgscanner.game.GameState.new(com.mtgscanner.game.GameFormat.COMMANDER, 2, partners = true)
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(Space.s4)) {
            Box(androidx.compose.ui.Modifier.size(380.dp, 170.dp)) {
                com.mtgscanner.ui.game.PlayerBack(g, 0, androidx.compose.ui.Modifier, { _, _, _ -> }, {}, { _, _ -> }, {}, {}, rotation = 90)
            }
            androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(Space.s4))
            Box(androidx.compose.ui.Modifier.size(350.dp, 300.dp)) {
                com.mtgscanner.ui.game.PlayerBack(two, 1, androidx.compose.ui.Modifier, { _, _, _ -> }, {}, { _, _ -> }, {}, {}, rotation = 0)
            }
        }
    }

    @Test fun playtest() = shoot("11-playtest") {
        com.mtgscanner.ui.decks.playtest.PlaytestScreen(deckId = 1L, onBack = {}, preview = PlaytestPreview.table)
    }

    @Test fun playtestOpening() = shoot("11b-playtest-opening") {
        com.mtgscanner.ui.decks.playtest.PlaytestScreen(deckId = 1L, onBack = {}, preview = PlaytestPreview.opening, previewOpening = true)
    }

    @Test fun sets() = shoot("10-sets") {
        com.mtgscanner.ui.collection.SetsScreen(onBack = {}, onOpenSet = {})
    }
}
