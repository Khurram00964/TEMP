package com.confused.anikuta.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ROUND 127 (D-798): the schema-MIGRATION lock tests. The user's requirement:
 * "if we update the database, then it should properly be able to convert the
 * old database to the new with the update." These tests run the REAL versioned
 * migration (1.sqm, v1 → v2) against a faithful v1-shaped database and assert
 * the two things that matter: the new column exists, and the old DATA SURVIVES.
 *
 * The v1 shape here is the exact pre-migration watch_progress: the 6 original
 * columns + the 5 Phase-WP columns the on-open probes had already added (every
 * install that ran the current app has them). 1.sqm's ALTER must apply cleanly
 * on top of exactly this.
 *
 * Mechanics: JdbcSqliteDriver("jdbc:sqlite::memory:") (the bare ":memory:" is
 * not a JDBC URL), raw v1 DDL + a data row, then
 * `AnikutaDatabase.Schema.migrate(driver, 1, 2)` — the same call the
 * AndroidSqliteDriver.Callback makes on an upgrading install.
 */
class SchemaMigrationTest {

    private lateinit var driver: SqlDriver

    @Before
    fun setUp() {
        driver = JdbcSqliteDriver("jdbc:sqlite::memory:")
        // The v1 watch_progress — byte-for-byte the shape every existing
        // install has (created by the old .sq + the on-open guarded ALTERs).
        driver.execute(
            null,
            """
            CREATE TABLE watch_progress (
                episode_key TEXT NOT NULL PRIMARY KEY,
                position INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER,
                last_watched_at INTEGER NOT NULL,
                main_id TEXT,
                watch_count INTEGER NOT NULL DEFAULT 0,
                first_watched_at INTEGER,
                auto_mark_suppressed INTEGER NOT NULL DEFAULT 0,
                user_marked_watched INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
            )
            """.trimMargin(),
            0,
        )
        // Real data a user would have on their device.
        driver.execute(
            null,
            """
            INSERT INTO watch_progress(
                episode_key, position, duration, completed, completed_at, last_watched_at,
                main_id, watch_count, first_watched_at, auto_mark_suppressed, user_marked_watched
            ) VALUES ('main-1|00001', 600, 1440, 0, NULL, 1729800000000, 'main-1', 0, NULL, 0, 0)
            """.trimMargin(),
            0,
        )
        // main_entry too — the FK target (foreign_keys is off by default in
        // SQLite, but a faithful v1 also had the table).
        driver.execute(
            null,
            """
            CREATE TABLE main_entry (
                main_id TEXT NOT NULL PRIMARY KEY,
                content_id TEXT NOT NULL,
                title TEXT NOT NULL,
                content_type TEXT NOT NULL DEFAULT 'anime',
                content_format TEXT NOT NULL DEFAULT 'video',
                data_source_id INTEGER,
                system_id INTEGER,
                extension_repo_id INTEGER,
                extension_id INTEGER,
                source_id INTEGER,
                anime_url TEXT,
                display_source TEXT NOT NULL DEFAULT 'extension',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            "INSERT INTO main_entry(main_id, content_id, title, created_at, updated_at) " +
                "VALUES ('main-1', 'cid-1', 'Test Anime', 1, 1)",
            0,
        )
        // ROUND 176: browse_cache too — 5.sqm:70 CREATEs an INDEX on it;
        // the missing table threw 'no such table: main.browse_cache' on
        // every chain (the first device-class failure the run surfaced).
        driver.execute(
            null,
            """
            CREATE TABLE browse_cache (
                section_key TEXT NOT NULL PRIMARY KEY,
                data_json TEXT NOT NULL,
                fetched_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            )
            """.trimMargin(),
            0,
        )
        // ROUND 176 (S3 — THE FIXTURE REPAIR): the chains to v8 run 5.sqm
        // (the track_entry/content_tracking_state REBUILDS) and 6.sqm (the
        // content_identity seeds FROM content_details/main_entry/track_entry)
        // — the v1.1.109-112 fixtures lacked these tables, so every chain
        // reaching v7/v8 THREW (silently: CI never runs the tests, D-465).
        // The shapes: content_details = the CURRENT .sq shape (no sqm alters
        // it); track_entry/content_tracking_state = the PRE-5 shapes 5.sqm
        // rebuilds FROM (the old columns + the cascade FK).
        driver.execute(
            null,
            """
            CREATE TABLE content_details (
                main_id TEXT NOT NULL PRIMARY KEY,
                data_source_type   TEXT,
                data_source_ref_id TEXT,
                data_score          INTEGER,
                data_episodes       INTEGER,
                data_season         TEXT,
                data_season_year    INTEGER,
                data_status         TEXT,
                data_genres         TEXT,
                data_synopsis       TEXT,
                data_cover_url      TEXT,
                data_banner_url     TEXT,
                data_extra_json     TEXT,
                data_updated_at     INTEGER,
                extension_type      TEXT,
                extension_id        TEXT,
                source_id           INTEGER,
                anime_url           TEXT,
                ext_description     TEXT,
                ext_genres          TEXT,
                ext_status          TEXT,
                ext_author          TEXT,
                ext_artist          TEXT,
                ext_thumbnail_url   TEXT,
                ext_extra_json      TEXT,
                ext_updated_at      INTEGER,
                cover_accent_argb   INTEGER,
                FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE track_entry (
                main_id TEXT NOT NULL,
                tracker_type TEXT NOT NULL,
                tracker_id INTEGER NOT NULL,
                status TEXT NOT NULL,
                score INTEGER,
                progress INTEGER NOT NULL,
                total_episodes INTEGER,
                list_id INTEGER,
                started_at INTEGER,
                completed_at INTEGER,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (main_id, tracker_type),
                FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE content_tracking_state (
                main_id TEXT NOT NULL,
                tracker_type TEXT NOT NULL,
                tracked INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (main_id, tracker_type)
            )
            """.trimMargin(),
            0,
        )
    }

    @After
    fun tearDown() {
        driver.close()
    }

    private fun columnNames(table: String): List<String> {
        val names = mutableListOf<String>()
        driver.executeQuery(null, "PRAGMA table_info($table)", { cursor ->
            while (cursor.next().value) {
                names.add(cursor.getString(1)!!)
            }
            app.cash.sqldelight.db.QueryResult.Value(Unit)
        }, 0)
        return names
    }

    @Test
    fun `migration 1 to 2 adds played_time_ms to watch_progress`() {
        assertTrue("precondition: v1 has no played_time_ms", "played_time_ms" !in columnNames("watch_progress"))

        AnikutaDatabase.Schema.migrate(driver, 1, 2)

        assertTrue(
            "the migration must add played_time_ms",
            "played_time_ms" in columnNames("watch_progress"),
        )
    }

    @Test
    fun `migration 1 to 2 preserves existing watch progress data`() {
        // ROUND 163 review fix: the chain runs to the CURRENT version (8 —
        // ROUND 176: 6.sqm v7 + 7.sqm v8 joined; the comment's "6" was
        // stale since round 173 and no test caught it — CI never runs the
        // tests, D-465) —
        // the generated row mapper reads the FULL v6 shape positionally, so a
        // chain that stops at v2 hands it a 12-column table (the reviewer's
        // P1: "tests that read through the generated code must end the chain
        // at the CURRENT schema version").
        AnikutaDatabase.Schema.migrate(driver, 1, 8)

        val database = AnikutaDatabase(driver)
        val row = database.watchQueries.getWatchProgress("main-1|00001").executeAsOneOrNull()
        assertNotNull("the pre-migration row must survive the migration", row)
        assertEquals(600L, row!!.position)
        assertEquals(1440L, row.duration)
        assertEquals(0L, row.completed)
        assertEquals(1729800000000L, row.last_watched_at)
        assertEquals("main-1", row.main_id)
        assertEquals("the new column starts at 0 for pre-existing rows", 0L, row.played_time_ms)
    }

    @Test
    fun `migrated database is fully usable by the generated code`() {
        AnikutaDatabase.Schema.migrate(driver, 1, 8)

        val database = AnikutaDatabase(driver)
        // A save through the typed API on the MIGRATED database must work —
        // the generated INSERT lists every v2 column.
        database.watchQueries.upsertWatchProgress(
            episode_key = "main-1|00002",
            position = 120L,
            duration = 1440L,
            completed = 0L,
            completed_at = null,
            last_watched_at = 1729800001000L,
            main_id = "main-1",
            watch_count = 0L,
            first_watched_at = null,
            auto_mark_suppressed = 0L,
            user_marked_watched = 0L,
            played_time_ms = 61_000L,
            season_number = 1L,
            episode_number = 2L,
        )
        val row = database.watchQueries.getWatchProgress("main-1|00002").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(61_000L, row!!.played_time_ms)
    }

    @Test
    fun `fresh install creates the current schema directly`() {
        // A fresh JdbcSqliteDriver + Schema.create = a brand-new install: the
        // column is there without any migration.
        val fresh = JdbcSqliteDriver("jdbc:sqlite::memory:")
        AnikutaDatabase.Schema.create(fresh)
        val database = AnikutaDatabase(fresh)
        assertEquals(null, database.watchQueries.getWatchProgress("nope").executeAsOneOrNull())
        val names = mutableListOf<String>()
        fresh.executeQuery(null, "PRAGMA table_info(watch_progress)", { cursor ->
            while (cursor.next().value) names.add(cursor.getString(1)!!)
            app.cash.sqldelight.db.QueryResult.Value(Unit)
        }, 0)
        assertTrue("a fresh install has played_time_ms from the .sq", "played_time_ms" in names)
        fresh.close()
    }

    // ── ROUND 144 (D-862): the v3 → v4 TRACKING-SURVIVAL migration ───────────
    //
    // The user's order: watch data must SURVIVE content removal AND extension
    // deletion. Until v4 both tracking tables carried FOREIGN KEY (main_id)
    // REFERENCES main_entry(main_id) ON DELETE CASCADE — and the app enables
    // PRAGMA foreign_keys = ON — so any main_entry deletion silently wiped the
    // user's history + session ledger for that content. The migration rebuilds
    // both tables WITHOUT the FK.

    /** The faithful v3 shape: both tracking tables WITH the cascade FK. */
    private fun createV3Shape() {
        // ROUND 176: the era-shape functions DROP the tables they own (the
        // setUp already created main_entry/watch_progress — the double
        // CREATE threw 'table main_entry already exists' before any
        // migration ran).
        driver.execute(null, "DROP TABLE IF EXISTS main_entry", 0)
        driver.execute(null, "DROP TABLE IF EXISTS content_details", 0)
        driver.execute(null, "DROP TABLE IF EXISTS watch_progress", 0)
        driver.execute(null, "DROP TABLE IF EXISTS track_entry", 0)
        driver.execute(null, "DROP TABLE IF EXISTS content_tracking_state", 0)
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS main_entry (
                main_id TEXT NOT NULL PRIMARY KEY,
                content_id TEXT NOT NULL,
                title TEXT NOT NULL,
                content_type TEXT NOT NULL DEFAULT 'anime',
                content_format TEXT NOT NULL DEFAULT 'video',
                data_source_id INTEGER,
                system_id INTEGER,
                extension_repo_id INTEGER,
                extension_id INTEGER,
                source_id INTEGER,
                anime_url TEXT,
                display_source TEXT NOT NULL DEFAULT 'extension',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE content_details (
                main_id TEXT NOT NULL PRIMARY KEY,
                data_source_type   TEXT,
                data_source_ref_id TEXT,
                data_score          INTEGER,
                data_episodes       INTEGER,
                data_season         TEXT,
                data_season_year    INTEGER,
                data_status         TEXT,
                data_genres         TEXT,
                data_synopsis       TEXT,
                data_cover_url      TEXT,
                data_banner_url     TEXT,
                data_extra_json     TEXT,
                data_updated_at     INTEGER,
                extension_type      TEXT,
                extension_id        TEXT,
                source_id           INTEGER,
                anime_url           TEXT,
                ext_description     TEXT,
                ext_genres          TEXT,
                ext_status          TEXT,
                ext_author          TEXT,
                ext_artist          TEXT,
                ext_thumbnail_url   TEXT,
                ext_extra_json      TEXT,
                ext_updated_at      INTEGER,
                cover_accent_argb   INTEGER
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE watch_progress (
                episode_key TEXT NOT NULL PRIMARY KEY,
                position INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER,
                last_watched_at INTEGER NOT NULL,
                main_id TEXT,
                watch_count INTEGER NOT NULL DEFAULT 0,
                first_watched_at INTEGER,
                auto_mark_suppressed INTEGER NOT NULL DEFAULT 0,
                user_marked_watched INTEGER NOT NULL DEFAULT 0,
                played_time_ms INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
            )
            """.trimMargin(),
            0,
        )
        // ROUND 176: track_entry/content_tracking_state (the PRE-5 shapes —
        // 5.sqm rebuilds both FROM these; the 3→8 chains reach 5.sqm).
        driver.execute(
            null,
            """
            CREATE TABLE track_entry (
                main_id TEXT NOT NULL,
                tracker_type TEXT NOT NULL,
                tracker_id INTEGER NOT NULL,
                status TEXT NOT NULL,
                score INTEGER,
                progress INTEGER NOT NULL,
                total_episodes INTEGER,
                list_id INTEGER,
                started_at INTEGER,
                completed_at INTEGER,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (main_id, tracker_type),
                FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE content_tracking_state (
                main_id TEXT NOT NULL,
                tracker_type TEXT NOT NULL,
                tracked INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (main_id, tracker_type)
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE watch_session (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                main_id TEXT NOT NULL,
                episode_key TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER NOT NULL,
                played_ms INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
            )
            """.trimMargin(),
            0,
        )
        // The user's data: one in-progress episode + one session ledger row.
        driver.execute(
            null,
            "INSERT INTO watch_progress(episode_key, position, duration, completed, completed_at, last_watched_at, main_id, watch_count, first_watched_at, auto_mark_suppressed, user_marked_watched, played_time_ms) " +
                "VALUES ('main-1|00001', 600, 1440, 0, NULL, 1729800000000, 'main-1', 0, NULL, 0, 0, 2520000)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO watch_session(main_id, episode_key, started_at, ended_at, played_ms) " +
                "VALUES ('main-1', 'main-1|00001', 1729800000000, 1729802520000, 2520000)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO main_entry(main_id, content_id, title, created_at, updated_at) VALUES ('main-1', 'cid-1', 'Test Anime', 1, 1)",
            0,
        )
    }

    private fun foreignKeysOf(table: String): List<String> {
        val targets = mutableListOf<String>()
        driver.executeQuery(null, "PRAGMA foreign_key_list($table)", { cursor ->
            while (cursor.next().value) {
                targets.add(cursor.getString(2) ?: "")
            }
            app.cash.sqldelight.db.QueryResult.Value(Unit)
        }, 0)
        return targets
    }

    @Test
    fun `migration 3 to 4 strips the cascade foreign keys`() {
        createV3Shape()
        // The app enables FKs on every open — reproduce that first.
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        assertTrue(
            "precondition: v3 watch_progress carries the main_entry FK",
            foreignKeysOf("watch_progress").contains("main_entry"),
        )

        AnikutaDatabase.Schema.migrate(driver, 3, 8) // ROUND 176: the reads end at the CURRENT shape (the P1 doctrine)

        assertTrue(
            "v4 watch_progress must have NO foreign keys",
            foreignKeysOf("watch_progress").isEmpty(),
        )
        assertTrue(
            "v4 watch_session must have NO foreign keys",
            foreignKeysOf("watch_session").isEmpty(),
        )
    }

    @Test
    fun `migration 3 to 4 keeps tracking alive after main_entry deletion`() {
        createV3Shape()
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)

        AnikutaDatabase.Schema.migrate(driver, 3, 8) // ROUND 176: the reads end at the CURRENT shape (the P1 doctrine)

        // THE user scenario: the content's identity row dies (extension
        // uninstall / rebuild) — the watch history + session ledger SURVIVE.
        driver.execute(null, "DELETE FROM main_entry WHERE main_id = 'main-1'", 0)

        val database = AnikutaDatabase(driver)
        val progress = database.watchQueries.getWatchProgress("main-1|00001").executeAsOneOrNull()
        assertNotNull("the watch progress must survive main_entry deletion", progress)
        assertEquals(600L, progress!!.position)
        assertEquals(2520000L, progress.played_time_ms)
        val sessions = database.watchQueries.getAllWatchSessions().executeAsList()
        assertEquals("the session ledger must survive main_entry deletion", 1, sessions.size)
        assertEquals(2520000L, sessions.first().played_ms)
    }

    // ── ROUND 163 (STEP-1): the v4 → v6 hardening migrations ────────────────
    //
    // 4.sqm (v4→v5): the watch_progress denorm (season_number + episode_number
    // + the backfill + the index). 5.sqm (v5→v6): the TRACKING-SURVIVAL fix —
    // the D-862 doctrine finally covers the TRACKER data (track_entry +
    // content_tracking_state lose the CASCADE FK that silently erased the
    // tracker link + opt-in flag on every library removal), track_entry gains
    // times_watched, the (tracker_type, tracker_id) index becomes UNIQUE after
    // an in-file dedup, and browse_cache gains its expires sweep index.

    /** The faithful v4 shape: the tracking tables still WITH the cascade FK. */
    private fun createV4Shape() {
        // ROUND 176: the same era-shape drops (the setUp collisions).
        driver.execute(null, "DROP TABLE IF EXISTS main_entry", 0)
        driver.execute(null, "DROP TABLE IF EXISTS content_details", 0)
        driver.execute(null, "DROP TABLE IF EXISTS watch_progress", 0)
        driver.execute(null, "DROP TABLE IF EXISTS track_entry", 0)
        driver.execute(null, "DROP TABLE IF EXISTS content_tracking_state", 0)
        driver.execute(null, "DROP TABLE IF EXISTS browse_cache", 0)
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS main_entry (
                main_id TEXT NOT NULL PRIMARY KEY,
                content_id TEXT NOT NULL,
                title TEXT NOT NULL,
                content_type TEXT NOT NULL DEFAULT 'anime',
                content_format TEXT NOT NULL DEFAULT 'video',
                data_source_id INTEGER,
                system_id INTEGER,
                extension_repo_id INTEGER,
                extension_id INTEGER,
                source_id INTEGER,
                anime_url TEXT,
                display_source TEXT NOT NULL DEFAULT 'extension',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE watch_progress (
                episode_key TEXT NOT NULL PRIMARY KEY,
                position INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER,
                last_watched_at INTEGER NOT NULL,
                main_id TEXT,
                watch_count INTEGER NOT NULL DEFAULT 0,
                first_watched_at INTEGER,
                auto_mark_suppressed INTEGER NOT NULL DEFAULT 0,
                user_marked_watched INTEGER NOT NULL DEFAULT 0,
                played_time_ms INTEGER NOT NULL DEFAULT 0
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE track_entry (
                main_id TEXT NOT NULL,
                tracker_type TEXT NOT NULL,
                tracker_id INTEGER NOT NULL,
                status TEXT NOT NULL,
                score INTEGER,
                progress INTEGER NOT NULL DEFAULT 0,
                total_episodes INTEGER,
                list_id INTEGER,
                started_at INTEGER,
                completed_at INTEGER,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (main_id, tracker_type),
                FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE content_tracking_state (
                main_id TEXT NOT NULL,
                tracker_type TEXT NOT NULL,
                tracked INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (main_id, tracker_type),
                FOREIGN KEY (main_id) REFERENCES main_entry(main_id) ON DELETE CASCADE
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE browse_cache (
                section_key TEXT NOT NULL PRIMARY KEY,
                data_json TEXT NOT NULL,
                fetched_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            )
            """.trimMargin(),
            0,
        )
        // The user's data: one watched episode + one tracker link + one opt-in
        // flag + one (expired) browse row.
        driver.execute(
            null,
            "INSERT INTO watch_progress(episode_key, position, duration, completed, completed_at, last_watched_at, main_id, watch_count, first_watched_at, auto_mark_suppressed, user_marked_watched, played_time_ms) " +
                "VALUES ('main-1|00001', 600, 1440, 1, 1729800001000, 1729800000000, 'main-1', 1, 1729800000000, 0, 0, 2520000)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO track_entry(main_id, tracker_type, tracker_id, status, score, progress, total_episodes, list_id, started_at, completed_at, updated_at) " +
                "VALUES ('main-1', 'anilist', 555, 'WATCHING', 85, 3, 12, 9001, NULL, NULL, 1729800000000)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO content_tracking_state(main_id, tracker_type, tracked, updated_at) " +
                "VALUES ('main-1', 'anilist', 1, 1729800000000)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO browse_cache(section_key, data_json, fetched_at, expires_at) " +
                "VALUES ('home_trending', '[]', 1, 2)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO main_entry(main_id, content_id, title, created_at, updated_at) VALUES ('main-1', 'cid-1', 'Test Anime', 1, 1)",
            0,
        )
        // ROUND 176 (S3): the 4→8 chains run 6.sqm (the identity seeds FROM
        // content_details) — the v4 fixture needed the table.
        driver.execute(
            null,
            """
            CREATE TABLE content_details (
                main_id TEXT NOT NULL PRIMARY KEY,
                data_source_type   TEXT,
                data_source_ref_id TEXT,
                data_score          INTEGER,
                data_episodes       INTEGER,
                data_season         TEXT,
                data_season_year    INTEGER,
                data_status         TEXT,
                data_genres         TEXT,
                data_synopsis       TEXT,
                data_cover_url      TEXT,
                data_banner_url     TEXT,
                data_extra_json     TEXT,
                data_updated_at     INTEGER,
                extension_type      TEXT,
                extension_id        TEXT,
                source_id           INTEGER,
                anime_url           TEXT,
                ext_description     TEXT,
                ext_genres          TEXT,
                ext_status          TEXT,
                ext_author          TEXT,
                ext_artist          TEXT,
                ext_thumbnail_url   TEXT,
                ext_extra_json      TEXT,
                ext_updated_at      INTEGER,
                cover_accent_argb   INTEGER
            )
            """.trimMargin(),
            0,
        )
    }

    @Test
    fun `migration 4 to 6 adds the denorm columns and backfills them`() {
        createV4Shape()
        assertTrue("precondition: no season_number", "season_number" !in columnNames("watch_progress"))

        AnikutaDatabase.Schema.migrate(driver, 4, 8)

        assertTrue("season_number must exist", "season_number" in columnNames("watch_progress"))
        assertTrue("episode_number must exist", "episode_number" in columnNames("watch_progress"))
        val database = AnikutaDatabase(driver)
        val row = database.watchQueries.getWatchProgress("main-1|00001").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("the legacy 2-part key backfills season 1", 1L, row!!.season_number)
        assertEquals("the key's numeric tail backfills the number", 1L, row.episode_number)
    }

    @Test
    fun `migration 4 to 6 strips the tracker-table cascade foreign keys`() {
        createV4Shape()
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        assertTrue(
            "precondition: v4 track_entry carries the main_entry FK",
            foreignKeysOf("track_entry").contains("main_entry"),
        )

        AnikutaDatabase.Schema.migrate(driver, 4, 8)

        assertTrue(
            "v6 track_entry must have NO foreign keys",
            foreignKeysOf("track_entry").isEmpty(),
        )
        assertTrue(
            "v6 content_tracking_state must have NO foreign keys",
            foreignKeysOf("content_tracking_state").isEmpty(),
        )
    }

    @Test
    fun `migration 4 to 6 keeps the tracker link and opt-in alive after main_entry deletion`() {
        createV4Shape()
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)

        AnikutaDatabase.Schema.migrate(driver, 4, 8)

        // THE device-critical scenario: library removal deletes the identity
        // row — the tracker link + the user's opt-in flag MUST survive (the
        // pre-v6 CASCADE silently erased both; the user's "very huge issue").
        driver.execute(null, "DELETE FROM main_entry WHERE main_id = 'main-1'", 0)

        val database = AnikutaDatabase(driver)
        val entry = database.trackQueries.getTrackEntry("main-1", "anilist").executeAsOneOrNull()
        assertNotNull("the tracker link must survive main_entry deletion", entry)
        assertEquals(555L, entry!!.tracker_id)
        assertEquals(0L, entry.times_watched)
        val state = database.trackQueries.getTrackingState("main-1", "anilist").executeAsOneOrNull()
        assertNotNull("the opt-in flag must survive main_entry deletion", state)
        assertEquals(1L, state!!.tracked)
    }

    @Test
    fun `migration 4 to 6 dedups tracker ids and enforces uniqueness`() {
        createV4Shape()
        // A second content claiming the SAME AniList entry — the silent-winner
        // bug's exact shape (the old index was non-unique).
        driver.execute(
            null,
            "INSERT INTO track_entry(main_id, tracker_type, tracker_id, status, score, progress, total_episodes, list_id, started_at, completed_at, updated_at) " +
                "VALUES ('main-2', 'anilist', 555, 'COMPLETED', NULL, 12, 12, NULL, NULL, NULL, 1729900000000)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO main_entry(main_id, content_id, title, created_at, updated_at) VALUES ('main-2', 'cid-2', 'Test Anime 2', 1, 1)",
            0,
        )

        AnikutaDatabase.Schema.migrate(driver, 4, 8)

        val database = AnikutaDatabase(driver)
        val rows = database.trackQueries.getTrackEntryByTrackerId("anilist", 555L)
        // The generated query is LIMIT 1 — count via the raw driver instead.
        var count = 0
        driver.executeQuery(
            null,
            "SELECT COUNT(*) FROM track_entry WHERE tracker_type = 'anilist' AND tracker_id = 555",
            { cursor ->
                cursor.next().value
                count = cursor.getLong(0)!!.toInt()
                app.cash.sqldelight.db.QueryResult.Value(Unit)
            },
            0,
        )
        assertEquals("the dedup must leave exactly one row per remote entry", 1, count)
        assertTrue("the survivor must carry times_watched", rows.executeAsOneOrNull() != null)
        // The UNIQUE index now rejects a second claim.
        val insertRejected = runCatching {
            driver.execute(
                null,
                "INSERT INTO track_entry(main_id, tracker_type, tracker_id, status, progress, updated_at, times_watched) " +
                    "VALUES ('main-3', 'anilist', 555, 'WATCHING', 0, 1730000000000, 0)",
                0,
            )
        }.isFailure
        assertTrue("the unique index must reject a duplicate remote link", insertRejected)
    }

    @Test
    fun `migration 4 to 6 keeps the browse sweep usable and data intact`() {
        createV4Shape()
        AnikutaDatabase.Schema.migrate(driver, 4, 8)
        val database = AnikutaDatabase(driver)
        // The expired row sweeps away; the table stays queryable.
        database.dataCacheQueries.deleteExpiredBrowseCache(10L)
        assertEquals(null, database.dataCacheQueries.getBrowseCache("home_trending").executeAsOneOrNull())
    }

    // ── ROUND 163 (the adversarial review's P0): the WS-A-v5 COHORT ──────────
    //
    // v1.1.96-99 shipped schema v5 with watch_progress.episode_number ONLY
    // (no season_number). The versioned chain skips 4.sqm for oldVersion == 5
    // (the generated guard: oldVersion <= 4), so the cohort's shape repair
    // lives in DatabaseDriverFactory.migrateV5ShapeIfNeeded — an onUpgrade
    // pre-flight that cannot be exercised through Schema.migrate (which owns
    // only the .sqm chain). This test pins the COHORT SHAPE itself: a v5
    // table (episode_number present, season_number missing) must NOT be
    // accepted as-is — the detector's precondition column must be provably
    // missing so the guard fires on the real device. The detector's ALTER is
    // DB-driver SQL (SupportSQLiteDatabase), verified on device; the unit
    // lock here is the SHAPE CONTRACT: the v5 shape differs from the v4 shape
    // by exactly episode_number, and 5.sqm alone (what the cohort runs)
    // yields a season-carrying table.

    @Test
    fun `the WS-A v5 cohort shape is recoverable by 5 sqm after the detector adds season_number`() {
        // The faithful WS-A-v5 shape: 13 columns — the v4 table + episode_number.
        // ROUND 176: the era fixture drops the setUp's tables it recreates.
        driver.execute(null, "DROP TABLE IF EXISTS watch_progress", 0)
        driver.execute(null, "DROP TABLE IF EXISTS track_entry", 0)
        driver.execute(null, "DROP TABLE IF EXISTS content_tracking_state", 0)
        driver.execute(null, "DROP TABLE IF EXISTS content_details", 0)
        driver.execute(null, "DROP TABLE IF EXISTS browse_cache", 0)
        driver.execute(
            null,
            """
            CREATE TABLE watch_progress (
                episode_key TEXT NOT NULL PRIMARY KEY,
                position INTEGER NOT NULL,
                duration INTEGER NOT NULL,
                completed INTEGER NOT NULL DEFAULT 0,
                completed_at INTEGER,
                last_watched_at INTEGER NOT NULL,
                main_id TEXT,
                watch_count INTEGER NOT NULL DEFAULT 0,
                first_watched_at INTEGER,
                auto_mark_suppressed INTEGER NOT NULL DEFAULT 0,
                user_marked_watched INTEGER NOT NULL DEFAULT 0,
                played_time_ms INTEGER NOT NULL DEFAULT 0,
                episode_number INTEGER NOT NULL DEFAULT 0
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            "INSERT INTO watch_progress(episode_key, position, duration, completed, completed_at, last_watched_at, main_id, watch_count, first_watched_at, auto_mark_suppressed, user_marked_watched, played_time_ms, episode_number) " +
                "VALUES ('main-1|00003', 600, 1440, 1, 1729800001000, 1729800000000, 'main-1', 1, 1729800000000, 0, 0, 2520000, 3)",
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE track_entry (
                main_id TEXT NOT NULL,
                tracker_type TEXT NOT NULL,
                tracker_id INTEGER NOT NULL,
                status TEXT NOT NULL,
                score INTEGER,
                progress INTEGER NOT NULL DEFAULT 0,
                total_episodes INTEGER,
                list_id INTEGER,
                started_at INTEGER,
                completed_at INTEGER,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (main_id, tracker_type)
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE content_tracking_state (
                main_id TEXT NOT NULL,
                tracker_type TEXT NOT NULL,
                tracked INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (main_id, tracker_type)
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE browse_cache (
                section_key TEXT NOT NULL PRIMARY KEY,
                data_json TEXT NOT NULL,
                fetched_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL
            )
            """.trimMargin(),
            0,
        )
        // ROUND 178 (the P1 doctrine — the chain now runs to v8): 6.sqm's
        // seeds SELECT FROM content_details (the setUp's copy was dropped
        // above) — the era fixture carries the v4-era shape.
        driver.execute(
            null,
            """
            CREATE TABLE content_details (
                main_id TEXT NOT NULL PRIMARY KEY,
                data_source_type   TEXT,
                data_source_ref_id TEXT,
                data_score          INTEGER,
                data_episodes       INTEGER,
                data_season         TEXT,
                data_season_year    INTEGER,
                data_status         TEXT,
                data_genres         TEXT,
                data_synopsis       TEXT,
                data_cover_url      TEXT,
                data_banner_url     TEXT,
                data_extra_json     TEXT,
                data_updated_at     INTEGER,
                extension_type      TEXT,
                extension_id        TEXT,
                source_id           INTEGER,
                anime_url           TEXT,
                ext_description     TEXT,
                ext_genres          TEXT,
                ext_status          TEXT,
                ext_author          TEXT,
                ext_artist          TEXT,
                ext_thumbnail_url   TEXT,
                ext_extra_json      TEXT,
                ext_updated_at      INTEGER,
                cover_accent_argb   INTEGER
            )
            """.trimMargin(),
            0,
        )
        driver.execute(
            null,
            "INSERT INTO track_entry(main_id, tracker_type, tracker_id, status, score, progress, total_episodes, list_id, started_at, completed_at, updated_at) " +
                "VALUES ('main-1', 'anilist', 555, 'WATCHING', 85, 3, 12, 9001, NULL, NULL, 1729800000000)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO content_tracking_state(main_id, tracker_type, tracked, updated_at) " +
                "VALUES ('main-1', 'anilist', 1, 1729800000000)",
            0,
        )
        // The detector's move (what migrateV5ShapeIfNeeded does on device):
        // season_number lands BEFORE the chain runs 5.sqm.
        driver.execute(
            null,
            "ALTER TABLE watch_progress ADD COLUMN season_number INTEGER NOT NULL DEFAULT 1",
            0,
        )

        // The cohort's chain: 4.sqm is SKIPPED (oldVersion 5) — then 5→8.
        // ROUND 178 (the P1 doctrine): the chain ends at the CURRENT version —
        // the mapper reads below must describe the shipped shape (v8).
        AnikutaDatabase.Schema.migrate(driver, 5, 8)

        val database = AnikutaDatabase(driver)
        val row = database.watchQueries.getWatchProgress("main-1|00003").executeAsOneOrNull()
        assertNotNull("the cohort's rows must survive", row)
        assertEquals("the detector's default stamps season 1", 1L, row!!.season_number)
        assertEquals("the WS-A backfilled values are untouched", 3L, row.episode_number)
        assertTrue(
            "the tracker tables are FK-free for the cohort too",
            foreignKeysOf("track_entry").isEmpty(),
        )
        val entry = database.trackQueries.getTrackEntry("main-1", "anilist").executeAsOneOrNull()
        assertNotNull("the tracker link survives", entry)
    }

    // ── ROUND 176 (S2+S1): THE v7/v8 LOCK TESTS ─────────────────────────────
    //
    // S2 (the audit's upgraded finding): 6.sqm's data-axis seed was a plain
    // INSERT — two content_details rows sharing (kind, ref) threw the UNIQUE
    // violation INSIDE onUpgrade → the version stayed old → the upgrade
    // retried every launch (the crash-loop class). The fix = OR IGNORE (the
    // 5.sqm dedup doctrine); THIS test pins it — the duplicate seed must
    // migrate cleanly with the first main keeping its identity.
    //
    // S1: the seeded identity rows collapsed the fine TMDB kinds (tmdb_movie/
    // tmdb_tv) to 'tmdb' — the movie and TV id sequences numerically overlap,
    // so the coarse key collided across media types. 7.sqm re-splits the kind
    // from content_details' preserved fine type; THIS test pins the round
    // trip (coarse at v7 → fine at v8).

    @Test
    fun `migration 6 to 8 survives duplicate (kind, ref) seed pairs without aborting`() {
        createV4Shape()
        // Two contents tracked to the SAME (tmdb, 123) pair — the shape a
        // restored backup produces (the audit's reachability chain:
        // resolveOrCreateForExtension's ext-only rows + linkAniList's
        // no-claim-check merge).
        driver.execute(
            null,
            "INSERT INTO main_entry(main_id, content_id, title, created_at, updated_at) VALUES ('main-2', 'cid-2', 'The Same TMDB Id', 1, 1)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO content_details(main_id, data_source_type, data_source_ref_id, data_updated_at) VALUES ('main-1', 'tmdb', '123', 1)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO content_details(main_id, data_source_type, data_source_ref_id, data_updated_at) VALUES ('main-2', 'tmdb', '123', 1)",
            0,
        )

        // The v1.1.109-112 shape THREW right here (the UNIQUE violation
        // inside onUpgrade). The chain must complete.
        AnikutaDatabase.Schema.migrate(driver, 6, 8)

        val database = AnikutaDatabase(driver)
        val main1 = database.contentQueries.getIdentitiesForContent("main-1").executeAsList()
        val main2 = database.contentQueries.getIdentitiesForContent("main-2").executeAsList()
        assertEquals("main-1 keeps its identity (the first seed wins)", 1, main1.size)
        assertEquals("main-2's duplicate was SKIPPED, not aborted", 0, main2.size)
        assertEquals(
            "the skipped duplicate's kind is what collided",
            "tmdb",
            main1.first().source_kind,
        )
    }

    @Test
    fun `migration 7 to 8 re-splits the coarse tmdb identity kind from the fine content_details type`() {
        createV4Shape()
        // createV4Shape already provides main-1 (the round-177 CI round:
        // the test's own insert PK-duplicated it).
        // The fine type PRESERVED in content_details (the audit's "the fix
        // data already exists and is being dropped").
        driver.execute(
            null,
            "INSERT INTO content_details(main_id, data_source_type, data_source_ref_id, data_updated_at) VALUES ('main-1', 'tmdb_movie', '123', 1)",
            0,
        )

        // v6 → v7: 6.sqm seeds the identity row with the COARSE kind (its
        // SELECT normalizes 'tmdb%' → 'tmdb').
        AnikutaDatabase.Schema.migrate(driver, 6, 7)
        val atV7 = AnikutaDatabase(driver)
            .contentQueries.getIdentitiesForContent("main-1")
            .executeAsList()
        assertEquals("the v7 row is coarse (the documented collapse)", "tmdb", atV7.first().source_kind)

        // v7 → v8: 7.sqm re-splits the kind from the preserved fine type.
        AnikutaDatabase.Schema.migrate(driver, 7, 8)
        val atV8 = AnikutaDatabase(driver)
            .contentQueries.getIdentitiesForContent("main-1")
            .executeAsList()
        assertEquals(
            "the identity re-splits to the FINE kind (the collision fix)",
            "tmdb_movie",
            atV8.first().source_kind,
        )
        assertEquals("the ref stays numeric (the fine KIND separates)", "123", atV8.first().source_ref_id)
    }

    // ── ROUND 178 (the audit): the 6.sqm HARDENING LOCKS ─────────────────────
    //
    // The round-178 audit found two more ways the shipped 6.sqm could abort
    // or corrupt on real cohorts, both fixed in place:
    // 1. the bare CREATE TABLE threw when the onUpgrade pre-flight's probe
    //    onCreate had ALREADY created content_identity (the full .sq schema
    //    runs before the chain on any device missing one probe table) →
    //    rollback → the S2 crash-loop class; IF NOT EXISTS closes it.
    // 2. the ext/track seeds were INSERT OR REPLACE — REPLACE DELETES the
    //    conflicting row first, so the track seed CLOBBERED the rich
    //    data-axis row (same PK) and MOVED identities cross-main (the remote
    //    UNIQUE) at migration time; OR IGNORE makes first-wins universal.

    @Test
    fun `migration 6 to 8 survives a pre-created content_identity from the probe onCreate`() {
        createV4Shape()
        driver.execute(
            null,
            "INSERT INTO content_details(main_id, data_source_type, data_source_ref_id, data_updated_at) VALUES ('main-1', 'tmdb_movie', '123', 1)",
            0,
        )
        // The pre-flight cohort: the probe onCreate ran the FULL .sq schema
        // BEFORE the chain — content_identity ALREADY exists (empty) when
        // 6.sqm runs. The shipped bare CREATE threw right here.
        driver.execute(
            null,
            """
            CREATE TABLE content_identity (
                main_id TEXT NOT NULL,
                source_kind TEXT NOT NULL,
                source_ref_id TEXT NOT NULL,
                display_title TEXT,
                cover_url TEXT,
                synopsis TEXT,
                score INTEGER,
                episodes INTEGER,
                status TEXT,
                genres TEXT,
                year INTEGER,
                display_rank INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (main_id, source_kind)
            )
            """.trimMargin(),
            0,
        )

        AnikutaDatabase.Schema.migrate(driver, 6, 8)

        val database = AnikutaDatabase(driver)
        val rows = database.contentQueries.getIdentitiesForContent("main-1").executeAsList()
        assertEquals("the seed lands INTO the pre-created table (no abort)", 1, rows.size)
        assertEquals("the kind re-splits fine at v8 as usual", "tmdb_movie", rows.first().source_kind)
    }

    @Test
    fun `migration 6 to 8 first-wins keeps the rich data-axis seed over the track seed`() {
        createV4Shape()
        // main-1: the RICH data axis (the search-persisted metadata row).
        driver.execute(
            null,
            "INSERT INTO content_details(main_id, data_source_type, data_source_ref_id, data_synopsis, data_updated_at) VALUES ('main-1', 'tmdb_movie', '123', 'rich synopsis', 1)",
            0,
        )
        // main-2: the SAME TMDB id via a v1.1.107-era track_entry pick — the
        // shipped OR REPLACE seed deleted main-1's rich row (the remote
        // UNIQUE conflict resolves by DELETE-then-INSERT) and installed a
        // bare all-NULL one in its place.
        driver.execute(
            null,
            "INSERT INTO main_entry(main_id, content_id, title, created_at, updated_at) VALUES ('main-2', 'cid-2', 'Second', 1, 1)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO track_entry(main_id, tracker_type, tracker_id, status, progress, updated_at) VALUES ('main-2', 'tmdb', 123, 'PLAN_TO_WATCH', 0, 1)",
            0,
        )

        AnikutaDatabase.Schema.migrate(driver, 6, 8)

        val database = AnikutaDatabase(driver)
        val main1 = database.contentQueries.getIdentitiesForContent("main-1").executeAsList()
        assertEquals("main-1 keeps its identity", 1, main1.size)
        assertEquals(
            "the RICH data-axis row survives (first-wins, never clobbered)",
            "rich synopsis",
            main1.first().synopsis,
        )
        val main2 = database.contentQueries.getIdentitiesForContent("main-2").executeAsList()
        assertEquals(
            "main-2's duplicate track pick is SKIPPED (the remote index is claimed)",
            0,
            main2.size,
        )
    }
}
