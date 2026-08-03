package com.erfangholami.solidshare.data.local.cache

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val MODULE_OUTBOX_COLUMNS =
    "module, webId, type, payload, status, attempts, nextRetryAt, lastError, createdAt, updatedAt"

private const val LEGACY_OUTBOX_COLUMNS =
    "webId, type, payload, status, attempts, nextRetryAt, lastError, createdAt, updatedAt"

/**
 * Folds the per-module outbox tables into one, carrying every queued operation across.
 *
 * Cached rows are rebuildable from the pod, but queued writes are the user's unsynced work and
 * exist nowhere else — so this migration is written out rather than left to the destructive
 * fallback, which would drop them.
 */
val MIGRATION_5_6: Migration = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `module_outbox_op` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`module` TEXT NOT NULL, " +
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
            "CREATE INDEX IF NOT EXISTS " +
                "`index_module_outbox_op_module_webId_status_nextRetryAt` " +
                "ON `module_outbox_op` (`module`, `webId`, `status`, `nextRetryAt`)",
        )
        carryOver(db, "ticket_outbox_op", "tickets")
        carryOver(db, "contact_outbox_op", "contacts")
        db.execSQL("DROP TABLE IF EXISTS `ticket_outbox_op`")
        db.execSQL("DROP TABLE IF EXISTS `contact_outbox_op`")
    }

    private fun carryOver(db: SupportSQLiteDatabase, table: String, module: String) {
        db.execSQL(
            "INSERT INTO `module_outbox_op` ($MODULE_OUTBOX_COLUMNS) " +
                "SELECT '$module', $LEGACY_OUTBOX_COLUMNS FROM `$table`",
        )
    }
}

/**
 * Folds the per-module cache tables into one, keyed by module.
 *
 * Synced rows are rebuildable from the pod, but rows in a pending state describe writes the
 * user has queued and nothing else holds — a provisional ticket created offline exists only
 * here — so they are carried, not dropped. The promoted per-module columns collapse into the
 * three generic ones the queries actually use.
 */
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `cached_entity` (" +
                "`module` TEXT NOT NULL, " +
                "`webId` TEXT NOT NULL, " +
                "`uri` TEXT NOT NULL, " +
                "`sortKey` TEXT, " +
                "`groupKey` TEXT, " +
                "`searchText` TEXT, " +
                "`detailJson` TEXT NOT NULL, " +
                "`etag` TEXT, " +
                "`syncState` TEXT NOT NULL, " +
                "`cachedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`module`, `webId`, `uri`))",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_cached_entity_module_webId` " +
                "ON `cached_entity` (`module`, `webId`)",
        )
        db.execSQL(
            "INSERT INTO `cached_entity` " +
                "(module, webId, uri, sortKey, groupKey, searchText, detailJson, etag, syncState, cachedAt) " +
                "SELECT 'contacts', webId, contactUri, name, bookUri, lower(name), " +
                "detailJson, etag, syncState, cachedAt FROM `cached_contact`",
        )
        db.execSQL(
            "INSERT INTO `cached_entity` " +
                "(module, webId, uri, sortKey, groupKey, searchText, detailJson, etag, syncState, cachedAt) " +
                "SELECT 'tickets', webId, ticketUri, eventStart, category, lower(title), " +
                "detailJson, etag, syncState, cachedAt FROM `cached_ticket`",
        )
        db.execSQL("DROP TABLE IF EXISTS `cached_contact`")
        db.execSQL("DROP TABLE IF EXISTS `cached_ticket`")
    }
}
