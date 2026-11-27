// ─────────────────────────────────────────────────────────────────────────────
// The sheet's prose data: migration ladder, the resolved flaw register, the
// open issues/watch-list, and the per-group census. Every claim is anchored to
// the ANI-KUTA repo (file:line where it matters).
// ─────────────────────────────────────────────────────────────────────────────

export interface Migration {
  v: string;
  file: string;
  round: string;
  what: string;
}

export const MIGRATIONS: Migration[] = [
  {
    v: "v1 → v2",
    file: "1.sqm",
    round: "127 · D-789",
    what: "watch_progress.played_time_ms — the cumulative actually-played gate: watched = fraction threshold AND ≥ 60 s genuinely played, totalled over sessions.",
  },
  {
    v: "v2 → v3",
    file: "2.sqm",
    round: "137 · D-830",
    what: "watch_session — the per-day watch-flow ledger (one row per continuous streak; the Profile charts bucket it by LOCAL day).",
  },
  {
    v: "v3 → v4",
    file: "3.sqm",
    round: "144 · D-862",
    what: "TRACKING SURVIVAL — watch_progress + watch_session rebuilt WITHOUT the main_entry CASCADE FK: history + sessions survive content/extension removal.",
  },
  {
    v: "v4 → v5",
    file: "4.sqm",
    round: "163 · R1/R5",
    what: "the season axis + the denormalized episode_number (kills the reactive CAST/SUBSTR scan on every 10 s save). v1.1.96–99 cohort (v5 without season_number) is repaired by the driver's one documented shape probe.",
  },
  {
    v: "v5 → v6",
    file: "5.sqm",
    round: "163 · R2/R6",
    what: "the D-862 doctrine covers the TRACKER data: track_entry + content_tracking_state rebuilt FK-free; times_watched lands; (tracker_type, tracker_id) becomes UNIQUE after an in-file dedup; browse sweep index.",
  },
  {
    v: "v6 → v7",
    file: "6.sqm",
    round: "173 · N2",
    what: "THE MULTI-LINK IDENTITY MODEL — content_identity (one row per (content, source) link, display_rank picks the driver). Seed migrates both content_details axes + the v1.1.107-era tmdb/tvmaze track rows (tmdb_movie/tmdb_tv normalized to 'tmdb').",
  },
];

export interface ResolvedFlaw {
  id: string;
  flaw: string;
  fix: string;
}

export const RESOLVED: ResolvedFlaw[] = [
  { id: "B1", flaw: "scanner INSERT OR REPLACE could clobber a canonical main_entry from the .data.json snapshot", fix: "insertMainEntryIfMissing = INSERT OR IGNORE (D-791)" },
  { id: "B2", flaw: "axis UPDATEs set every column incl. nulls — a forgetful writer erased metadata", fix: "COALESCE merges + explicit replace* for switches (D-792)" },
  { id: "B3", flaw: "display_source could flip to an axis with no link", fix: "validated writer refuses dead flips (D-794)" },
  { id: "B4", flaw: "restored extension-only downloads had no ext axis", fix: "restoreExtensionAxisFromSnapshot derives it on scan (D-793)" },
  { id: "B5", flaw: "three title sources could drift", fix: "main_entry.title = single display truth, validated writer (D-797)" },
  { id: "B6", flaw: "content_id regeneration hardcoded 'aniyomi'", fix: "shared ecosystemNameForEntry helper (D-796)" },
  { id: "B7", flaw: "extension id stored twice (TEXT + INTEGER twin) could drift", fix: "INTEGER twin DERIVED in SQL + startup reconcile (D-795)" },
  { id: "R1", flaw: "reactive highest-watched query CAST/SUBSTR-scanned every row per 10 s save", fix: "denorm episode_number + composite index (v5)" },
  { id: "R2", flaw: "tracker tables still CASCADE-wiped on library removal", fix: "both rebuilt FK-free with survival lock tests (v6)" },
  { id: "R3", flaw: "no growth bounds — sessions/metadata/expired browse lived forever", fix: "DatabaseMaintenance daily sweeps (400d/180d/expired)" },
  { id: "R4", flaw: "content_type axis dormant; playback MIME collision", fix: "documented; activation rides the movies/series work" },
  { id: "R5", flaw: "no season axis — S1E1 == S2E1 in five tables", fix: "season_number (v5) + 3-part keys, dual-format parsers" },
  { id: "R6", flaw: "two contents could claim one remote tracker entry", fix: "in-file dedup + UNIQUE (tracker_type, tracker_id) (v6)" },
];

export type Severity = "HIGH" | "MED" | "LOW" | "INFO";

export interface Issue {
  id: string;
  severity: Severity;
  tag: string;
  title: string;
  body: string;
  evidence: string;
}

export const ISSUES: Issue[] = [
  {
    id: "S1",
    severity: "MED",
    tag: "identity collision",
    title: "tmdb/tvmaze identity refs are bare numbers — movie vs TV namespaces collide",
    body: "The unified-sheet link writes sourceRefId = result.id.toString() with sourceKind 'tmdb'. TMDB movie ids and TV ids are SEPARATE sequences that overlap numerically, and 6.sqm normalized tmdb_movie/tmdb_tv → 'tmdb'. UNIQUE (source_kind, source_ref_id) then maps movie #279 and TV #279 to ONE row: the INSERT OR REPLACE clobbers the first content's identity and the reopen reverse-lookup (lookupIdentityMain) resolves to the wrong content. Namespacing the ref ('movie-279' / 'tv-279') or keeping the fine kinds fixes it.",
    evidence: "DetailsViewModelMultiTracker.kt:724-731 · content.sq idx_identity_remote · 6.sqm CASE normalization",
  },
  {
    id: "S2",
    severity: "MED",
    tag: "migration robustness",
    title: "6.sqm seed uses a PLAIN INSERT — a duplicate remote ref would abort the v7 migration mid-boot",
    body: "The data-axis seed (INSERT INTO content_identity … SELECT) trusts (data_source_type, data_source_ref_id) to be unique across content_details rows — but no UNIQUE index enforces that pre-v7 (idx_content_details_data_ref is a plain partial index). One duplicated AniList ref on a device → UNIQUE(idx_identity_remote) violation → SqliteException inside onUpgrade → permanent crash loop. The ext-axis insert is already OR REPLACE; the first one should be OR IGNORE (or a pre-dedup).",
    evidence: "6.sqm lines 29-50 · content.sq idx_content_details_data_ref (non-unique)",
  },
  {
    id: "S3",
    severity: "MED",
    tag: "test debt",
    title: "SchemaMigrationTest still chains to version 6 — the v6→v7 seed has NO lock test",
    body: "The contract's own rule: 'every new migration gets a test … tests must end the chain at the CURRENT schema version'. The latest asserts call migrate(driver, 1, 6) / (4, 6) / (5, 6) and their comments call 6 'the CURRENT version' — all pre-date 6.sqm. The identity seed's data preservation (both axes, tmdb/tvmaze carry-over, display_rank derivation) is unpinned.",
    evidence: "core/database/src/test/.../SchemaMigrationTest.kt:136,151,476… · DATABASE-CONTRACT.md rule 4",
  },
  {
    id: "S4",
    severity: "LOW",
    tag: "query sweep",
    title: "updateActualAt matches (main_id, episode_number) only — one UPDATE sweeps SUB and DUB rows",
    body: "episode_schedule's UNIQUE key is (main_id, episode_number, audio_variant) and CF7 exists precisely because variants are distinct rows — but updateActualAt omits audio_variant, so detecting a SUB release stamps actual_at on the DUB row too. Probably intended ('the episode aired'), but it silently blurs the variant axis and deserves either the variant in the WHERE or a comment + lock test.",
    evidence: "episodeSchedule.sq updateActualAt vs idx_schedule_unique",
  },
  {
    id: "S5",
    severity: "LOW",
    tag: "doc drift",
    title: "The DB docs are two facts behind the schema",
    body: "DATABASE-CONTRACT.md line 11 still says 'schema v6' (v7 landed in round 173) and SCHEMA.md still says '25 tables across 17 files … as of round 126' — the real count is 27 tables across 16 non-empty files (content_identity has no doc entry at all). The contract's own rule 5 requires the doc to move with the .sq files; both updates are one-commit fixes.",
    evidence: "DATABASE-CONTRACT.md:11 · DOCUMENTATION/database/SCHEMA.md header",
  },
  {
    id: "S6",
    severity: "LOW",
    tag: "backup coverage",
    title: "app_settings mirrors only PART of PreferenceStore — settings backup is knowingly partial",
    body: "SettingsRepository's own header: mirroring every preference 'via upsertSetting(...) is a follow-up task — not all PreferenceStore keys are mirrored'. Backup/restore of app_settings therefore restores a subset of settings; everything else lives only in SharedPreferences and dies on a clean install. The .sq design is fine — the mirror write-through is the unfinished half.",
    evidence: "core/preferences/.../SettingsRepository.kt:23",
  },
  {
    id: "S7",
    severity: "INFO",
    tag: "two clocks",
    title: "browse_cache carries two freshness regimes on one row",
    body: "expires_at (the 6h homepage contract + the daily sweep index) and fetched_at (the round-176 12h auto-refresh gate + the manual-pull write-back) tick independently. A row can read as a hit for the 12h gate while already expired for the sweep, and vice-versa. Harmless today, but any future 'cache TTL' tuning must say WHICH clock it means — the section key namespaces make it easy to fork the policy per source.",
    evidence: "dataCache.sq browse_cache · round-176 flicker-fix commit (fetched_at write-back)",
  },
  {
    id: "S8",
    severity: "INFO",
    tag: "by design ✓",
    title: "extension_id is INTEGER on main_entry but TEXT on content_details",
    body: "Not a flaw — the duality is documented doctrine: main_entry.extension_id keeps the Aniyomi INTERNAL source.id for the denormalized hot lookup (D-189), content_details.extension_id is the CANONICAL TEXT form, and the INTEGER twin (source_id) is DERIVED via NULLIF(CAST(...),0) + startup reconcile (D-795/B7). Listed so nobody 'unifies' it without reading B7 first.",
    evidence: "content.sq D-189/D-198 headers · FLAWS.md B7",
  },
];

export const STRENGTHS: string[] = [
  "Migration discipline is real: six .sqm files, each with its backfill in the same file, each pinned by lock tests that run the REAL Schema.migrate on an in-memory SQLite and assert data survival.",
  "The FK-free doctrine is deliberate and tested: watch history, session ledgers, tracker links and opt-in flags all survive content/extension removal (3.sqm/5.sqm rebuilds + survival tests).",
  "Storage-level defensive writes: COALESCE-merge axis updates can't be wiped by a forgetful writer; played_time_ms and times_watched increment DB-side; watched requires fraction AND ≥60 s genuinely played.",
  "The N+1 kills are done: the Library batch loader reads 653 entries in 2 queries (getAllLibraryMainEntries + getAllContentDetails + the audio-row batch), and watch aggregates GROUP BY per content.",
  "Index hygiene is maintained: redundant indexes were deliberately DROPPED (leftmost-column rule), hot paths get partial indexes (continue-watching, due-anime, retention purges), and growth bounds run daily (sessions 400d, episode metadata 180d, expired browse).",
  "Downloads treat DISK + .data.json as the truth and the DB as a reconciled index — the scanner uses INSERT OR IGNORE so it can never clobber canonical rows.",
];
