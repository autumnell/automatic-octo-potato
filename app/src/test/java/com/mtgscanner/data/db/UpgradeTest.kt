package com.mtgscanner.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Every app update must keep every card. For each past database version (layouts in app/schemas), this builds a
 * database exactly as that version left it, puts a row in every table, then opens it with today's app: Room runs
 * the upgrade steps and checks the result matches today's layout, and no row may go missing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class UpgradeTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val dir = File("schemas/com.mtgscanner.data.db.AppDatabase")
    private val json = Json { ignoreUnknownKeys = true }

    private fun schema(v: Int): JsonObject? =
        File(dir, "$v.json").takeIf { it.exists() }?.let { json.parseToJsonElement(it.readText()).jsonObject["database"]!!.jsonObject }

    /** Today's version, from the @Database line (Room's annotation isn't kept at run time). */
    private val current: Int by lazy {
        val src = File("src/main/java/com/mtgscanner/data/db/AppDatabase.kt").readText()
        Regex("""version\s*=\s*(\d+)""").find(src)!!.groupValues[1].toInt()
    }

    @Test
    fun todaysLayoutIsSaved() {
        assertTrue(
            "app/schemas has no layout for database version $current. Build once and commit app/schemas.",
            File(dir, "$current.json").exists(),
        )
    }

    @Test
    fun everyOldVersionUpgradesWithoutLosingRows() {
        val old = (1 until current).filter { schema(it) != null }
        assertTrue("No old database layouts in app/schemas", old.isNotEmpty())
        val report = StringBuilder()
        for (v in old) {
            val name = "upgrade-from-$v.db"
            ctx.deleteDatabase(name)
            val before = buildOld(name, v, schema(v)!!)
            val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
                .addMigrations(*AppDatabase.ALL_MIGRATIONS)
                .allowMainThreadQueries()
                .build()
            try {
                val sql = db.openHelper.writableDatabase // runs the upgrade and checks the layout
                assertEquals("version after upgrading from $v", current, sql.version)
                for ((table, n) in before) {
                    val exists = sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use { it.moveToFirst() }
                    if (!exists) continue
                    val after = sql.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }
                    assertTrue("$table lost rows upgrading from version $v ($n before, $after after)", after >= n)
                }
                // The card itself, as it was.
                sql.query("SELECT scryfallId, name, quantity FROM cards").use {
                    assertTrue("the card is gone after upgrading from $v", it.moveToFirst())
                    assertEquals("card-$v", it.getString(0))
                    assertEquals("Card $v", it.getString(1))
                    assertEquals(3, it.getInt(2))
                }
                report.append("version $v: ok\n")
            } finally {
                db.close()
                ctx.deleteDatabase(name)
            }
        }
        println(report)
    }

    /** Creates the database as version [v] made it, with one row per table. Returns the row counts. */
    private fun buildOld(name: String, v: Int, s: JsonObject): Map<String, Int> {
        val file = ctx.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        val counts = LinkedHashMap<String, Int>()
        try {
            for (e in s["entities"]!!.jsonArray) {
                val ent = e.jsonObject
                val table = ent["tableName"]!!.jsonPrimitive.content
                db.execSQL(ent["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                ent["indices"]?.jsonArray?.forEach {
                    db.execSQL(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                }
            }
            s["views"]?.jsonArray?.forEach {
                val view = it.jsonObject
                db.execSQL("CREATE VIEW `${view["viewName"]!!.jsonPrimitive.content}` AS ${view["createSql"]!!.jsonPrimitive.content.replace("\${VIEW_NAME}", view["viewName"]!!.jsonPrimitive.content)}")
            }
            s["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            for (e in s["entities"]!!.jsonArray) {
                val ent = e.jsonObject
                val table = ent["tableName"]!!.jsonPrimitive.content
                val cols = ent["fields"]!!.jsonArray.map { it.jsonObject }
                val values = android.content.ContentValues()
                for (c in cols) {
                    val col = c["columnName"]!!.jsonPrimitive.content
                    val affinity = c["affinity"]!!.jsonPrimitive.content
                    val notNull = c["notNull"]?.jsonPrimitive?.boolean ?: false
                    when {
                        table == "cards" && col == "scryfallId" -> values.put(col, "card-$v")
                        table == "cards" && col == "name" -> values.put(col, "Card $v")
                        table == "cards" && col == "quantity" -> values.put(col, 3)
                        col == "id" && affinity == "INTEGER" -> values.put(col, 1)
                        affinity == "INTEGER" -> values.put(col, if (col == "boxId") 1 else 0)
                        affinity == "REAL" -> values.put(col, 1.5)
                        affinity == "TEXT" -> values.put(col, if (col == "day") "2026-10-20" else "x")
                        notNull -> values.put(col, ByteArray(0))
                    }
                }
                db.insertOrThrow(table, null, values)
                counts[table] = db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { it.moveToFirst(); it.getInt(0) }
            }
            db.version = v
        } finally {
            db.close()
        }
        return counts
    }
}
