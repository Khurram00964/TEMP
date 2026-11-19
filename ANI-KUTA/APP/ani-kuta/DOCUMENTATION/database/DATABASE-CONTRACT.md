# ANI-KUTA DATABASE CONTRACT

> The schema, the key formats, and the migration rules — the single reference
> for anyone touching `core/database`. Established by the round-157 groundwork
> audit (WS-A); keep it current with every schema change.

## 1. The storage

- **Engine**: SQLDelight 2.0.2, one database (`AnikutaDatabase`), schema v5.
- **Source of truth**: `core/database/src/main/sqldelight/com/confused/anikuta/core/database/` — the `.sq` files declare the CURRENT shape; the numbered `.sqm` files migrate existing installs.
- **Integrity**: `PRAGMA foreign_keys = ON` on every open + `ON DELETE CASCADE` on the child tables. `watch_progress` and `watch_session` are deliberately FK-free (v4, D-862): tracking and history survive content/extension removal.

## 2. The migration rules (NON-NEGOTIABLE)

1. **Every schema change goes in a new numbered `.sqm`** (`5.sqm` migrates v5→v6), one migration per file, with the backfill inside the same file.
2. **The `.sq` CREATE statements must match the post-migration shape** — fresh installs never run `.sqm`; existing installs never re-run CREATE.
3. **No new hasColumn probes.** `DatabaseDriverFactory.migrateSchemaIfNeeded` is the legacy pre-v1.1.X rescue path ONLY — it must not learn new columns (the versioned chain owns them now).
4. **Every new migration gets a test** in `SchemaMigrationTest` (a v-shaped table + a data row → migrate → assert column + value). Tests that read through the generated code must end the chain at the CURRENT schema version.
5. **Bulk multi-writes are transactional** (`database.transaction`): mark-all-watched, the library multi-select batches, the download completion pair. New multi-statement paths must be too.
6. **Backup parity**: `BackupManager.BACKUP_TABLES` must carry every new table (except the documented exclusions: transient queue rows, dedup logs, analytics, the byte cache). Post-restore reconciles (e.g. the `episode_number` backfill) run in `restoreBackup` after the DB commit.
7. **Growth bounds**: every append-heavy table needs a prune (the precedent: `activity_event` 365d, `watch_session` 400d, `data_cache_episode` 30d-fetched, `browse_cache` expired-delete). New caches declare their TTL at creation.

## 3. The key formats (canonical)

| Key | Shape | Notes |
|---|---|---|
| `episode_key` | `"{mainId}\|{padded_5_digit}"` (e.g. `main-1\|00012`) | PK of `watch_progress`; the number is DENORMALIZED into `watch_progress.episode_number` (v5) — queries read the column, never parse the key |
| `track_entry` | PK `(main_id, tracker_type)` | `tracker_type` ∈ anilist/mal/shikimori (+ trakt when wired); tracker IDs live in `tracker_id` |
| `browse_cache` | PK `section_key` | the home's 6h namespace: `home_trending`, `home_airs`, `home_field_covers` (+ the browse screens' keys) |
| library items | UNIQUE `(main_id, category_id)` | display_order per category |

## 4. The content-type axis (the expansion groundwork)

- `main_entry.content_type` TEXT (values today: `anime`; reserved: `tv_series`, `movie` — set by the content layer from the source's response kind) + `content_format` (`video`; reserved: `image` for manga, `text` for novels).
- `content_details.data_source_type` ∈ anilist/kitsu/mal/**tmdb** + `data_source_ref_id` — the generic external-ID slot (`ContentDetails.tmdbId` reads it).
- Extension-carried external IDs (CloudStream `syncData`: tmdb/imdb) persist in `content_details.ext_extra_json` (typed, `ignoreUnknownKeys`).
- The `WHERE content_type` filters and any first-class medium column arrive as migrations per rule 2 — the axis is intentionally TEXT so new mediums need data, not schema.

## 5. The tables at a glance

20 tables across 16 files. The heavy-hitters: `main_entry`/`content_details` (identity + the two-axis metadata), `watch_progress`/`watch_session` (progress + the per-day ledger), `track_entry`/`content_tracking_state` (the tracker cache + the opt-in gate), `downloaded_episode`/`download_queue`, `browse_cache`/`data_cache_episode` (the 6h/metadata caches), `library_*`, `episode_schedule`/`episode_update` (the notifications), `activity_event` (the analytics, 365d).

## 6. Change checklist (copy into every DB-touching PR/round)

- [ ] New columns/tables in a numbered `.sqm` + the `.sq` CREATE updated
- [ ] A `SchemaMigrationTest` case for the new migration
- [ ] Bulk writes transactional
- [ ] `BACKUP_TABLES` reviewed (new tables in, or an exclusion comment)
- [ ] A prune/TTL for any new append-heavy table
- [ ] This doc updated (the version, the keys, the axis)
