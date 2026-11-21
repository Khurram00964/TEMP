package com.confused.anikuta.core.database

/**
 * ROUND 165: the EXTENSION PRIORITY SYSTEM's tracker (the spec:
 * DOCUMENTATION/EXTENSION-PRIORITY-SYSTEM.md). Records the usage events per
 * provider/server key and exposes the score-ordered reads the sort sites
 * consume.
 *
 * The events (the weights live in [scoreOf]):
 * - SEARCH  ×1 — a search ran on the provider
 * - OPEN    ×2 — a details page opened through it
 * - WATCH   ×5 — a playback started through it
 * - RESOLVE ×2 — a server resolved successfully
 * - FAIL    ×-3 — a resolve/play failure attributed to it (floor 0)
 *
 * EVERY write is a TRANSACTIONAL read-modify-write (UPSERT is unavailable
 * below SQLite 3.24 — the project constraint) and fire-and-forget-safe: the
 * callers record on IO without awaiting the outcome.
 *
 * KEYS:
 * - provider tier: "cs:<providerName>" | "aniyomi:<sourceId>"
 * - server tier:   "<providerKey>::<serverName>"
 *
 * THIS MODULE IS DEPENDENCY-FREE (no :core:common): no Logger (the callers
 * log through the central Logger) and no System clock ([record] takes
 * `nowMs` explicitly) — the DatabaseMaintenance precedent.
 *
 * The toggle lives in ExtensionPriorityPreferences (:core:preferences) — the
 * RECORDING always runs (the history survives the toggling); the SORT sites
 * check the toggle.
 */
class ExtensionUsageTracker(private val database: AnikutaDatabase) {

    companion object {
        const val TIER_PROVIDER = "provider"
        const val TIER_SERVER = "server"

        const val WEIGHT_SEARCH = 1
        const val WEIGHT_OPEN = 2
        const val WEIGHT_WATCH = 5
        const val WEIGHT_RESOLVE = 2
        const val WEIGHT_FAIL = 3 // subtracted

        fun providerKeyCs(providerName: String) = "cs:$providerName"
        fun providerKeyAniyomi(sourceId: Long) = "aniyomi:$sourceId"
        fun serverKey(providerKey: String, serverName: String) = "$providerKey::$serverName"

        private fun scoreOf(
            searches: Int, opens: Int, watches: Int, resolves: Int, fails: Int,
        ): Int = maxOf(
            0,
            searches * WEIGHT_SEARCH +
                opens * WEIGHT_OPEN +
                watches * WEIGHT_WATCH +
                resolves * WEIGHT_RESOLVE -
                fails * WEIGHT_FAIL,
        )
    }

    /** The event kinds (the caller-facing vocabulary). */
    enum class Event { SEARCH, OPEN, WATCH, RESOLVE, FAIL }

    /** The outcome of one record call (the caller logs; failure-isolated). */
    data class RecordResult(val recorded: Boolean)

    /**
     * Records one event against [key]. Transactional read-modify-write: the
     * concurrent events serialize on the DB transaction (no lost updates).
     * Never throws — a failing record no-ops (the caller logs via Logger).
     */
    suspend fun record(key: String, tier: String, event: Event, nowMs: Long): RecordResult = withContext(Dispatchers.IO) {
        runCatching {
            database.transaction {
                val existing = database.extensionUsageQueries.getUsage(key).executeAsOneOrNull()
                var searches = existing?.searches?.toInt() ?: 0
                var opens = existing?.opens?.toInt() ?: 0
                var watches = existing?.watches?.toInt() ?: 0
                var resolves = existing?.resolves?.toInt() ?: 0
                var fails = existing?.fails?.toInt() ?: 0
                when (event) {
                    Event.SEARCH -> searches += 1
                    Event.OPEN -> opens += 1
                    Event.WATCH -> watches += 1
                    Event.RESOLVE -> resolves += 1
                    Event.FAIL -> fails += 1
                }
                val score = scoreOf(searches, opens, watches, resolves, fails)
                if (existing == null) {
                    database.extensionUsageQueries.insertUsage(
                        key = key,
                        tier = tier,
                        searches = searches.toLong(),
                        opens = opens.toLong(),
                        watches = watches.toLong(),
                        resolves = resolves.toLong(),
                        fails = fails.toLong(),
                        score = score.toLong(),
                        last_used_at = nowMs,
                        updated_at = nowMs,
                    )
                } else {
                    database.extensionUsageQueries.applyUsageDeltas(
                        key = key,
                        searches = searches.toLong(),
                        opens = opens.toLong(),
                        watches = watches.toLong(),
                        resolves = resolves.toLong(),
                        fails = fails.toLong(),
                        score = score.toLong(),
                        last_used_at = nowMs,
                        updated_at = nowMs,
                    )
                }
            }
            RecordResult(recorded = true)
        }.getOrElse { RecordResult(recorded = false) }
    }

    /** The score-ordered rows for a tier (the sort sites' read). */
    suspend fun rowsByTier(tier: String): List<Extension_usage> = withContext(Dispatchers.IO) {
        runCatching { database.extensionUsageQueries.getUsageByTier(tier).executeAsList() }
            .getOrDefault(emptyList())

    /** The score for one key (0 when absent). */
    suspend fun scoreFor(key: String): Int = withContext(Dispatchers.IO) {
        runCatching { database.extensionUsageQueries.getUsage(key).executeAsOneOrNull()?.score?.toInt() ?: 0 }
            .getOrDefault(0)

    /** The Settings' reset action — wipes the learned history. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        runCatching { database.extensionUsageQueries.wipeAllUsage() }
    }
}
