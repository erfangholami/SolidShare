package com.erfangholami.solidshare.data.local.cache

import android.content.Context
import androidx.room.Room
import androidx.room.util.TableInfo
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CacheMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DB_NAME)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(LegacyOutboxSchema)
                .build(),
        )
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        runCatching { helper.close() }
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(DB_NAME)
    }

    @Test
    fun `queued writes from both modules survive the move to one outbox table`() {
        insertLegacy("ticket_outbox_op", webId = ALICE, type = "CREATE", payload = "{\"a\":1}")
        insertLegacy("ticket_outbox_op", webId = ALICE, type = "DELETE", payload = "{\"a\":2}")
        insertLegacy("contact_outbox_op", webId = BOB, type = "MERGE", payload = "{\"b\":1}")

        MIGRATION_5_6.migrate(db)

        val rows = readModuleOutbox()
        assertEquals(3, rows.size)
        assertEquals(
            setOf(
                Row("tickets", ALICE, "CREATE", "{\"a\":1}"),
                Row("tickets", ALICE, "DELETE", "{\"a\":2}"),
                Row("contacts", BOB, "MERGE", "{\"b\":1}"),
            ),
            rows.toSet(),
        )
    }

    @Test
    fun `a queued op keeps its retry schedule rather than restarting`() {
        insertLegacy(
            "ticket_outbox_op",
            webId = ALICE,
            type = "UPDATE",
            payload = "{}",
            status = "FAILED",
            attempts = 4,
            nextRetryAt = 9_000L,
            lastError = "boom",
        )

        MIGRATION_5_6.migrate(db)

        db.query("SELECT status, attempts, nextRetryAt, lastError FROM module_outbox_op").use {
            assertTrue(it.moveToFirst())
            assertEquals("FAILED", it.getString(0))
            assertEquals(4, it.getInt(1))
            assertEquals(9_000L, it.getLong(2))
            assertEquals("boom", it.getString(3))
        }
    }

    @Test
    fun `the legacy tables are gone and the new one is indexed the way Room expects`() {
        MIGRATION_5_6.migrate(db)

        assertFalse(tableExists("ticket_outbox_op"))
        assertFalse(tableExists("contact_outbox_op"))
        assertTrue(tableExists("module_outbox_op"))

        val indexes = mutableListOf<String>()
        db.query("PRAGMA index_list(`module_outbox_op`)").use {
            while (it.moveToNext()) indexes.add(it.getString(it.getColumnIndexOrThrow("name")))
        }
        assertTrue(
            indexes.toString(),
            indexes.contains("index_module_outbox_op_module_webId_status_nextRetryAt"),
        )
    }

    @Test
    fun `migrating a pod with nothing queued is not an error`() {
        MIGRATION_5_6.migrate(db)

        assertTrue(readModuleOutbox().isEmpty())
    }

    @Test
    fun `cached rows from both modules land in one table with their keys mapped`() {
        db.execSQL(
            "INSERT INTO `cached_contact` " +
                "(webId, contactUri, bookUri, name, detailJson, etag, syncState, cachedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(ALICE, "https://a.pod/c/1#this", "https://a.pod/book#this", "Jane", "{}", null, "SYNCED", 5L),
        )
        db.execSQL(
            "INSERT INTO `cached_ticket` " +
                "(webId, ticketUri, title, category, eventStart, issuer, validThrough, " +
                "backgroundColor, foregroundColor, detailJson, etag, syncState, cachedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(ALICE, "https://a.pod/t/1#this", "Concert", "EVENT", "2026-09-01", null, null, null, null, "{}", null, "SYNCED", 6L),
        )

        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)

        val rows = mutableListOf<List<String?>>()
        db.query(
            "SELECT module, uri, sortKey, groupKey, searchText FROM cached_entity ORDER BY module",
        ).use {
            while (it.moveToNext()) {
                rows.add((0 until 5).map { i -> if (it.isNull(i)) null else it.getString(i) })
            }
        }
        assertEquals(
            listOf(
                listOf("contacts", "https://a.pod/c/1#this", "Jane", "https://a.pod/book#this", "jane"),
                listOf("tickets", "https://a.pod/t/1#this", "2026-09-01", "EVENT", "concert"),
            ),
            rows,
        )
        assertFalse(tableExists("cached_contact"))
        assertFalse(tableExists("cached_ticket"))
    }

    @Test
    fun `a provisional offline creation survives with its pending state`() {
        db.execSQL(
            "INSERT INTO `cached_ticket` " +
                "(webId, ticketUri, title, category, eventStart, issuer, validThrough, " +
                "backgroundColor, foregroundColor, detailJson, etag, syncState, cachedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                ALICE, "urn:solidshare:pending:x", "Draft", "GENERIC", null, null, null,
                null, null, "{\"title\":\"Draft\"}", null, "PENDING_CREATE", 7L,
            ),
        )

        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)

        db.query(
            "SELECT syncState, detailJson FROM cached_entity WHERE uri = 'urn:solidshare:pending:x'",
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("PENDING_CREATE", it.getString(0))
            assertEquals("{\"title\":\"Draft\"}", it.getString(1))
        }
    }

    @Test
    fun `the migrated tables are exactly what Room itself would create`() {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val fresh = Room.inMemoryDatabaseBuilder(context, SolidCacheDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val freshDb = fresh.openHelper.writableDatabase
            listOf("cached_entity", "module_outbox_op").forEach { table ->
                assertEquals(
                    "migration DDL for `$table` must match Room's expected schema, or every " +
                        "upgrading device throws at first open",
                    TableInfo.read(freshDb, table),
                    TableInfo.read(db, table),
                )
            }
        } finally {
            fresh.close()
        }
    }

    private fun tableExists(name: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'").use {
            it.moveToFirst()
        }

    private fun insertLegacy(
        table: String,
        webId: String,
        type: String,
        payload: String,
        status: String = "PENDING",
        attempts: Int = 0,
        nextRetryAt: Long = 0,
        lastError: String? = null,
    ) {
        db.execSQL(
            "INSERT INTO `$table` " +
                "(webId, type, payload, status, attempts, nextRetryAt, lastError, createdAt, updatedAt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                webId, type, payload, status, attempts, nextRetryAt, lastError, 1L, 1L,
            ),
        )
    }

    private fun readModuleOutbox(): List<Row> {
        val rows = mutableListOf<Row>()
        db.query("SELECT module, webId, type, payload FROM module_outbox_op ORDER BY id").use {
            while (it.moveToNext()) {
                rows.add(Row(it.getString(0), it.getString(1), it.getString(2), it.getString(3)))
            }
        }
        return rows
    }

    private data class Row(
        val module: String,
        val webId: String,
        val type: String,
        val payload: String,
    )

    private object LegacyOutboxSchema : SupportSQLiteOpenHelper.Callback(5) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_contact` (" +
                    "`webId` TEXT NOT NULL, `contactUri` TEXT NOT NULL, `bookUri` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `detailJson` TEXT NOT NULL, `etag` TEXT, " +
                    "`syncState` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`webId`, `contactUri`))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_ticket` (" +
                    "`webId` TEXT NOT NULL, `ticketUri` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`category` TEXT NOT NULL, `eventStart` TEXT, `issuer` TEXT, " +
                    "`validThrough` TEXT, `backgroundColor` TEXT, `foregroundColor` TEXT, " +
                    "`detailJson` TEXT NOT NULL, `etag` TEXT, `syncState` TEXT NOT NULL, " +
                    "`cachedAt` INTEGER NOT NULL, PRIMARY KEY(`webId`, `ticketUri`))",
            )
            listOf("ticket_outbox_op", "contact_outbox_op").forEach { table ->
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `$table` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`webId` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`attempts` INTEGER NOT NULL, " +
                        "`nextRetryAt` INTEGER NOT NULL, " +
                        "`lastError` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_${table}_webId_status_nextRetryAt` " +
                        "ON `$table` (`webId`, `status`, `nextRetryAt`)",
                )
            }
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DB_NAME = "cache-migration-test.db"
        const val ALICE = "https://alice.example/profile/card#me"
        const val BOB = "https://bob.example/profile/card#me"
    }
}
