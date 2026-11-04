package com.mtgscanner.screens

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mtgscanner.data.CollectionRepository
import com.mtgscanner.data.ScryfallApi
import com.mtgscanner.data.db.AppDatabase
import com.mtgscanner.data.db.Box
import com.mtgscanner.ui.collection.BrowseViewModel
import com.mtgscanner.ui.collection.CollectionViewModel
import com.mtgscanner.ui.collection.SortBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A 30,000-card collection: how long the database and the Collection and Browse lists take to work out. Writes
 * build/big-collection.md, and fails only if something takes far longer than a phone user would wait.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class BigCollectionTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: CollectionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = CollectionRepository(ctx, db, ScryfallApi())
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private val rows = mutableListOf<Pair<String, Long>>()

    private inline fun <T> timed(label: String, block: () -> T): T {
        val t0 = System.nanoTime()
        val r = block()
        rows += label to (System.nanoTime() - t0) / 1_000_000
        return r
    }

    @Test
    fun thirtyThousandCards() = runBlocking {
        val n = 30_000
        val dao = db.cardDao()
        val boxes = (1..40).map { dao.insertBox(Box(name = "Box $it", sortOrder = it)) }
        timed("Add 30,000 rows") { dao.putCards(List(n) { SampleCollection.card(it, boxes[it % boxes.size]) }) }
        timed("Read every card") { dao.all() }
        timed("Read every card again") { dao.all() }
        timed("Collection value") { dao.collectionTotal() }
        timed("Box totals") { repo.db.cardDao().observeBoxTotals().first() }
        timed("Box counts") { dao.boxCounts() }

        val overview = CollectionViewModel(repo)
        timed("Collection tab totals") { withTimeout(60_000) { overview.ui.first { it.totalCards > 0 } } }

        val all = BrowseViewModel(repo, -1)
        timed("Browse all: first list") { withTimeout(60_000) { all.ui.first { it.loaded } } }
        all.update { it.copy(query = "bolt") }
        timed("Browse all: search \"bolt\"") { withTimeout(60_000) { all.ui.first { it.loaded && it.shownIds.size < n / 10 } } }
        all.update { it.copy(query = "t:creature c:g") }
        timed("Browse all: search \"t:creature c:g\"") { withTimeout(60_000) { all.ui.first { ui -> ui.loaded && ui.groups.flatMap { g -> g.second }.all { "Creature" in it.typeLine } } } }
        all.update { it.copy(query = "", sort = SortBy.PRICE) }
        timed("Browse all: sort by price") { withTimeout(60_000) { all.ui.first { ui -> ui.loaded && ui.shownIds.size == n } } }

        val one = BrowseViewModel(repo, boxes[3])
        timed("Browse one box: first list") { withTimeout(60_000) { one.ui.first { it.loaded } } }

        val report = buildString {
            append("### A 30,000-card collection\n\n| Step | ms |\n|---|---|\n")
            rows.forEach { (l, ms) -> append("| $l | $ms |\n") }
        }
        File("build").mkdirs()
        File("build/big-collection.md").writeText(report)
        println(report)
        val slow = rows.filter { it.second > 20_000 && !it.first.startsWith("Add") }
        assertTrue("Far too slow with 30,000 cards: $slow", slow.isEmpty())
    }
}
