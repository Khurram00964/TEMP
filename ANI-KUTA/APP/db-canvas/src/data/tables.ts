import { darkTriplet, grayTriplet, type FamColors, type ThemeName } from "./colors";

// ─────────────────────────────────────────────────────────────────────────────
// ANI-KUTA database — the full schema map (SQLDelight `AnikutaDatabase`, v7).
// Source of truth: core/database/src/main/sqldelight/com/confused/anikuta/core/database/*.sq
// 27 tables across 16 .sq files (app.sq is intentionally empty). Column order,
// types, PK/FK/UNIQUE flags and defaults are copied verbatim from the .sq DDL.
// Demo rows are ILLUSTRATIVE (labelled on the sheet) but state-complete: every
// enum/state axis the schema models is represented at least once.
// ─────────────────────────────────────────────────────────────────────────────

export type ColType = "TEXT" | "INTEGER" | "REAL";

export interface Col {
  name: string;
  type: ColType;
  /** part of the primary key */
  pk?: boolean;
  /** enforced FOREIGN KEY → "table.col" (ON DELETE rules noted on the sheet) */
  fk?: string;
  /** UNIQUE (standalone or composite-member) */
  u?: boolean;
  /** NOT NULL */
  nn?: boolean;
  /** DEFAULT value literal */
  def?: string;
  /** short schema note (tooltip) */
  note?: string;
}

export type GroupId =
  | "lookups"
  | "spine"
  | "genre"
  | "library"
  | "watch"
  | "tracking"
  | "caches"
  | "downloads"
  | "updates"
  | "notifications"
  | "ratings"
  | "playback"
  | "standalone";

export interface GroupDef {
  id: GroupId;
  label: string;
  band: string; // panel header text
  family: Record<ThemeName, FamColors>;
  blurb: string;
}

export type DemoVal = string | number | null;

export interface TableDef {
  name: string;
  file: string;
  group: GroupId;
  /** assigned by the panel layout pass below */
  x: number;
  y: number;
  w: number;
  cols: Col[];
  /** which columns render in the demo block (curated; full row lives in this file) */
  demoCols: string[];
  demoNote?: string;
  demo: Record<string, DemoVal>[];
}

// ── palette families (pastel highlighter system, §5 of the design spec) ──────
// Authored once in their LIGHT form; the dark and gray triplets are derived in
// src/data/colors.ts so a family is defined exactly once.
const LIGHT_FAMILIES: Record<string, FamColors> = {
  yellow: { fill: "#fef08a", border: "#eab308", text: "#713f12", panel: "rgba(254,240,138,0.34)" },
  blue: { fill: "#dbeafe", border: "#93c5fd", text: "#1d4ed8", panel: "rgba(219,234,254,0.55)" },
  lavender: { fill: "#e9d5ff", border: "#d8b4fe", text: "#581c87", panel: "rgba(233,213,255,0.45)" },
  mint: { fill: "#bbf7d0", border: "#86efac", text: "#065f46", panel: "rgba(187,247,208,0.45)" },
  peach: { fill: "#fed7aa", border: "#fdba74", text: "#9a3412", panel: "rgba(254,215,170,0.45)" },
  rose: { fill: "#fecdd3", border: "#fb7185", text: "#881337", panel: "rgba(254,205,211,0.45)" },
  cyan: { fill: "#cffafe", border: "#67e8f9", text: "#155e75", panel: "rgba(207,250,254,0.55)" },
  indigo: { fill: "#e0e7ff", border: "#a5b4fc", text: "#3730a3", panel: "rgba(224,231,255,0.55)" },
  amber: { fill: "#fef3c7", border: "#fbbf24", text: "#92400e", panel: "rgba(254,243,199,0.55)" },
  pink: { fill: "#fce7f3", border: "#f9a8d4", text: "#9d174d", panel: "rgba(252,231,243,0.55)" },
  lime: { fill: "#ecfccb", border: "#bef264", text: "#3f6212", panel: "rgba(236,252,203,0.55)" },
  slate: { fill: "#e2e8f0", border: "#94a3b8", text: "#334155", panel: "rgba(226,232,240,0.55)" },
  sky: { fill: "#e0f2fe", border: "#7dd3fc", text: "#0369a1", panel: "rgba(224,242,254,0.55)" },
};

export const FAMILIES = Object.fromEntries(
  Object.entries(LIGHT_FAMILIES).map(([k, light]) => [
    k,
    { light, dark: darkTriplet(light), gray: grayTriplet(light) },
  ]),
) as Record<string, Record<ThemeName, FamColors>>;

/** a group's colours for the active UI mode (GROUPS carries the family key) */
export const famFor = (id: GroupId, theme: ThemeName): FamColors =>
  GROUP_BY_ID[id].family[theme];

export const GROUPS: GroupDef[] = [
  {
    id: "lookups",
    label: "Seeded Lookups",
    band: "LOOKUPS",
    family: FAMILIES.blue,
    blurb:
      "Seeded once at first launch, edited only by app updates. main_entry references them by FK.",
  },
  {
    id: "spine",
    label: "Identity Spine",
    band: "SPINE",
    family: FAMILIES.yellow,
    blurb:
      "The core: main_entry is the stable identity (main_id UUID + regenerable content_id); content_details holds the two metadata axes; content_identity (v7) is the authoritative multi-link set.",
  },
  {
    id: "genre",
    label: "Genre System",
    band: "GENRE",
    family: FAMILIES.lavender,
    blurb:
      "Normalized genre vocabulary (AniList-canonical, ~40 seeded) + the content junction table replacing free-text genre columns.",
  },
  {
    id: "library",
    label: "Library",
    band: "LIBRARY",
    family: FAMILIES.mint,
    blurb:
      "Categories + membership. 'Default' is permanent. UNIQUE (main_id, category_id) dedups — one content may sit in several categories.",
  },
  {
    id: "watch",
    label: "Watch Progress",
    band: "WATCH",
    family: FAMILIES.peach,
    blurb:
      "Per-episode resume + the watched state machine + the per-day session ledger. FK-free by doctrine (D-862): history survives content/extension removal.",
  },
  {
    id: "tracking",
    label: "Trackers",
    band: "TRACKING",
    family: FAMILIES.rose,
    blurb:
      "track_entry = the LOCAL CACHE of the remote tracker entry; content_tracking_state = the user's explicit opt-in. Both rebuilt FK-free (v6); tracker links are UNIQUE per remote entry.",
  },
  {
    id: "caches",
    label: "Metadata Caches",
    band: "CACHES",
    family: FAMILIES.cyan,
    blurb:
      "Enriched per-episode metadata (AniZip/Jikan/Kitsu columns, audio-pill preservation) and the browse section cache (the home's 6h namespace + per-source keys).",
  },
  {
    id: "downloads",
    label: "Download System",
    band: "DOWNLOADS",
    family: FAMILIES.indigo,
    blurb:
      "download_queue = the active pipeline (7 states, retry/resolve context); downloaded_episode = the disk index (disk + .data.json is the truth; the scanner reconciles).",
  },
  {
    id: "updates",
    label: "Update Engine",
    band: "UPDATES",
    family: FAMILIES.amber,
    blurb:
      "The smart-update engine: airing schedule (SUB/DUB variants), the 'New episodes' feed, and per-anime check state (backoff + learned release offset).",
  },
  {
    id: "notifications",
    label: "Notifications",
    band: "NOTIF",
    family: FAMILIES.pink,
    blurb:
      "Per-content notification switches + the sent-log used for dedup (not backup-eligible, 90-day retention).",
  },
  {
    id: "ratings",
    label: "Ratings",
    band: "RATINGS",
    family: FAMILIES.lime,
    blurb:
      "Per-anime and per-episode user ratings, 0–100 AniList-native (displayed as 0–10).",
  },
  {
    id: "playback",
    label: "Playback Cache",
    band: "PLAYBACK",
    family: FAMILIES.sky,
    blurb:
      "playback_cache_entry — the identity-hash keyed video cache (progressive byte ranges + HLS segments), disposable, no FK, LRU-evicted.",
  },
  {
    id: "standalone",
    label: "Not linked to anything",
    band: "STANDALONE",
    family: FAMILIES.slate,
    blurb:
      "These three tables have NO relationship at all — no FK in or out and no main_id spoke: activity_event (internal analytics, 365-day prune), app_settings (the backup mirror of PreferenceStore) and browse_cache (the browse section cache). They are deliberately shown apart, in grey.",
  },
];

// ── shared demo timestamps (all epochs are ms; T0 = 2026-11-22T00:00Z) ───────
const H = 3_600_000;
const D = 86_400_000;
export const T0 = 1_795_305_600_000; // 2026-11-22T00:00:00Z

// demo content identities (stable UUIDs)
export const M_FRI = "main-frieren";
export const M_AOT = "main-aot";
export const M_OSHI = "main-oshinoko";
export const M_MON = "main-monster";
export const M_KIZ = "main-kizumono";
export const M_DAN = "main-dandadan";
export const M_FMAB = "main-fmab";

const c = (
  name: string,
  type: ColType,
  o: Omit<Col, "name" | "type"> = {},
): Col => ({ name, type, ...o });

// Cards carry no coordinates: the PANEL layout below decides where each one
// sits (a table always lives inside its category's panel).
type RawTable = Omit<TableDef, "x" | "y" | "w">;

/** card widths (long key columns need more room) */
const WIDTHS: Record<string, number> = {
  data_source: 250,
  system: 250,
  main_entry: 280,
  content_details: 320,
  content_identity: 300,
  genre: 280,
  content_genre: 250,
  library_category: 260,
  library_item: 260,
  watch_progress: 320,
  watch_session: 290,
  track_entry: 310,
  content_tracking_state: 290,
  data_cache_episode: 330,
  browse_cache: 270,
  download_queue: 340,
  downloaded_episode: 330,
  episode_schedule: 300,
  episode_update: 300,
  anime_update_state: 310,
  notification_config: 290,
  notification_sent: 290,
  user_rating: 260,
  user_episode_rating: 290,
  playback_cache_entry: 340,
  activity_event: 300,
  app_settings: 300,
};
// ── measured text metrics (must match globals.css) ──────────────────────────
const NAME_ADVANCE = 7.0; // JetBrains Mono 700 @11px (measured conservative)
const TYPE_ADVANCE = 5.6; // JetBrains Mono 600 @9px
const HEAD_ADVANCE = 5.7; // demo header, JetBrains Mono 700 @8.5px
const VAL_ADVANCE = 6.8; // demo value, JetBrains Mono 700 @10.5px
const TEXT_ADVANCE = 7.4; // demo value, Patrick Hand 400 @13px (wide handwriting)
const CELL_PAD = 18; // 8px padding on each side of a cell
const BADGE_ROOM = 62; // room for up to two PK/FK/U/DEF chips
const DEMO_CELL_PAD = 18;
const DEMO_MIN = 56;
const CARD_PAD = 20; // the card's own horizontal padding

/** the pixel width of every demo column (headers AND longest values fit) */
export function demoColWidths(t: { demo: Record<string, unknown>[]; demoCols: string[] }): number[] {
  return t.demoCols.map((col) => {
    let w = col.length * HEAD_ADVANCE;
    for (const row of t.demo) {
      const v = row[col];
      if (v === null || v === undefined) continue;
      const str = String(v);
      const numeric = typeof v === "number" || /^[0-9.eE:+-]+$/.test(str);
      // numbers render via toLocaleString → count the thousands separators too
      const len = numeric ? str.length + (str.length > 3 ? Math.floor((str.length - 1) / 3) : 0) : str.length;
      const vw = len * (numeric ? VAL_ADVANCE : TEXT_ADVANCE);
      if (vw > w) w = vw;
    }
    return Math.ceil(w + DEMO_CELL_PAD);
  });
}

/** the width a table needs so that NO column heading is ever clipped */
function requiredWidth(t: RawTable): number {
  const cols = columnsRows(t.cols.length);
  const rows = Math.ceil(t.cols.length / cols);
  let schema = 0;
  for (let s = 0; s < cols; s++) {
    const strip = t.cols.slice(s * rows, (s + 1) * rows);
    const maxName = Math.max(...strip.map((c) => c.name.length));
    const maxType = Math.max(...strip.map((c) => c.type.length));
    schema +=
      CARD_PAD / cols + maxName * NAME_ADVANCE + CELL_PAD + maxType * TYPE_ADVANCE + BADGE_ROOM + 6;
  }
  // demo block: header AND the longest value must fit, per column — the same
  // demoColWidths() the rendered grid uses as its inline template, so the card
  // can never be narrower than its own demo table (+4px border slack)
  const demo =
    t.demo.length > 0
      ? CARD_PAD + demoColWidths(t).reduce((a, w) => a + w, 0) + 4
      : 0;
  return Math.ceil(Math.max(schema, demo, 240));
}

const cardWidth = (t: RawTable) => Math.max(WIDTHS[t.name] ?? 290, requiredWidth(t));

const RAW_TABLES: RawTable[] = [
  // ══════════════════════════════ LOOKUPS (content.sq) ══════════════════════
  {
    name: "data_source",
    file: "content.sq",
    group: "lookups",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true }),
      c("name", "TEXT", { u: true, nn: true }),
      c("display_name", "TEXT", { nn: true }),
      c("type", "TEXT", { nn: true, def: "'metadata'" }),
      c("created_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["id", "name", "display_name", "type"],
    demoNote: "seeded once — 'metadata' vs 'tracking' type axis",
    demo: [
      { id: 1, name: "anilist", display_name: "AniList", type: "metadata" },
      { id: 2, name: "mal", display_name: "MyAnimeList", type: "tracking" },
      { id: 3, name: "simkl", display_name: "Simkl", type: "tracking" },
      { id: 4, name: "tmdb", display_name: "TMDB", type: "metadata" },
      { id: 5, name: "tvmaze", display_name: "TVmaze", type: "metadata" },
    ],
  },
  {
    name: "system",
    file: "content.sq",
    group: "lookups",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true }),
      c("name", "TEXT", { u: true, nn: true }),
      c("display_name", "TEXT", { nn: true }),
      c("package_prefix", "TEXT"),
      c("created_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["id", "name", "display_name", "package_prefix"],
    demoNote: "the D-540 rule: the recorded system follows the REAL ecosystem",
    demo: [
      { id: 1, name: "aniyomi", display_name: "Aniyomi", package_prefix: "xyz.aniyomi." },
      { id: 2, name: "cloudstream", display_name: "CloudStream", package_prefix: "com.lagradost.cloudstream3" },
      { id: 3, name: "sora", display_name: "Sora", package_prefix: "com.sora." },
      { id: 4, name: "mangayomi", display_name: "MangaYomi", package_prefix: "xyz.mangayomi." },
    ],
  },

  // ══════════════════════════════ SPINE (content.sq) ════════════════════════
  {
    name: "main_entry",
    file: "content.sq",
    group: "spine",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, note: "stable UUID" }),
      c("content_id", "TEXT", { nn: true, note: "regenerated on switch" }),
      c("title", "TEXT", { nn: true }),
      c("content_type", "TEXT", { nn: true, def: "'anime'" }),
      c("content_format", "TEXT", { nn: true, def: "'video'" }),
      c("data_source_id", "INTEGER", { fk: "data_source.id" }),
      c("system_id", "INTEGER", { fk: "system.id" }),
      c("extension_repo_id", "INTEGER", { note: "kept per user directive" }),
      c("extension_id", "INTEGER", { note: "Aniyomi INTERNAL source.id — D-189" }),
      c("source_id", "INTEGER"),
      c("anime_url", "TEXT"),
      c("display_source", "TEXT", { nn: true, def: "'extension'" }),
      c("created_at", "INTEGER", { nn: true }),
      c("updated_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["main_id", "title", "content_type", "display_source"],
    demoNote: "display_source picks the shown axis; title is the single display truth (B5)",
    demo: [
      { main_id: M_FRI, title: "Frieren: Beyond Journey's End", content_type: "anime", display_source: "data_source", extension_id: 1234, anime_url: "/anime/frieren-sousou-no-frieren" },
      { main_id: M_AOT, title: "Attack on Titan", content_type: "anime", display_source: "extension", extension_id: 1234, anime_url: "/anime/shingeki-no-kyojin" },
      { main_id: M_MON, title: "Monster", content_type: "manga", display_source: "data_source", extension_id: 9012, anime_url: "/manga/monster-urasawa" },
      { main_id: M_KIZ, title: "Kizumonogatari I: Tekketsu", content_type: "movie", display_source: "extension", extension_id: 1234, anime_url: "/anime/kizumonogatari-i" },
      { main_id: M_DAN, title: "Dandadan", content_type: "anime", display_source: "data_source", extension_id: null, anime_url: null },
      { main_id: M_FMAB, title: "Fullmetal Alchemist: Brotherhood", content_type: "anime", display_source: "extension", extension_id: 1234, anime_url: "/anime/fullmetal-alchemist-brotherhood" },
      { main_id: M_OSHI, title: "Oshi no Ko", content_type: "anime", display_source: "extension", extension_id: 1234, anime_url: "/anime/oshi-no-ko" },
    ],
  },
  {
    name: "content_details",
    file: "content.sq",
    group: "spine",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, fk: "main_entry.main_id" }),
      c("data_source_type", "TEXT", { note: "'anilist'|'kitsu'|'mal'|'tmdb'|NULL" }),
      c("data_source_ref_id", "TEXT", { note: "partial idx (anilist hot path)" }),
      c("data_score", "INTEGER"),
      c("data_episodes", "INTEGER", { note: "NULL for manga/novels — D-198" }),
      c("data_season", "TEXT"),
      c("data_season_year", "INTEGER"),
      c("data_status", "TEXT"),
      c("data_genres", "TEXT"),
      c("data_synopsis", "TEXT"),
      c("data_cover_url", "TEXT"),
      c("data_banner_url", "TEXT"),
      c("data_extra_json", "TEXT", { note: "DataSourceExtras (idMal, trailer…)" }),
      c("data_updated_at", "INTEGER"),
      c("extension_type", "TEXT", { note: "'aniyomi'|'cloudstream'|…|NULL" }),
      c("extension_id", "TEXT", { note: "CANONICAL — the TEXT form" }),
      c("source_id", "INTEGER", { note: "DERIVED twin — NULLIF(CAST(...),0)" }),
      c("anime_url", "TEXT"),
      c("ext_description", "TEXT"),
      c("ext_genres", "TEXT"),
      c("ext_status", "TEXT"),
      c("ext_author", "TEXT"),
      c("ext_artist", "TEXT"),
      c("ext_thumbnail_url", "TEXT"),
      c("ext_extra_json", "TEXT", { note: "ExtensionExtras (scanlator, ch/vol…)" }),
      c("ext_updated_at", "INTEGER"),
      c("cover_accent_argb", "INTEGER", { note: "Palette-extracted — D-223" }),
    ],
    demoCols: ["main_id", "data_source_type", "data_source_ref_id", "extension_type", "extension_id"],
    demoNote: "two independent axes in ONE row — each merges via COALESCE, switches via replace*, unlinks via clear*",
    demo: [
      { main_id: M_FRI, data_source_type: "anilist", data_source_ref_id: "154587", data_score: 93, extension_type: "aniyomi", extension_id: "1234", anime_url: "/anime/frieren-sousou-no-frieren" },
      { main_id: M_AOT, data_source_type: "anilist", data_source_ref_id: "16498", data_score: 89, extension_type: "aniyomi", extension_id: "1234", anime_url: "/anime/shingeki-no-kyojin" },
      { main_id: M_MON, data_source_type: "anilist", data_source_ref_id: "30656", data_score: 93, extension_type: "aniyomi", extension_id: "9012", anime_url: "/manga/monster-urasawa" },
      { main_id: M_DAN, data_source_type: "anilist", data_source_ref_id: "177089", data_score: 88, extension_type: null, extension_id: null, anime_url: null },
    ],
  },
  {
    name: "content_identity",
    file: "content.sq",
    group: "spine",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, note: "composite PK part 1" }),
      c("source_kind", "TEXT", { pk: true, nn: true, note: "anilist|mal|simkl|tmdb|tvmaze|metadata|aniyomi|cloudstream" }),
      c("source_ref_id", "TEXT", { nn: true, u: true, note: "UNIQUE (kind, ref); ext = '<extId>:<url>'" }),
      c("display_title", "TEXT"),
      c("cover_url", "TEXT"),
      c("synopsis", "TEXT"),
      c("score", "INTEGER"),
      c("episodes", "INTEGER"),
      c("status", "TEXT"),
      c("genres", "TEXT"),
      c("year", "INTEGER"),
      c("display_rank", "INTEGER", { nn: true, def: "0", note: "0 = drives the details page" }),
      c("created_at", "INTEGER", { nn: true }),
      c("updated_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["main_id", "source_kind", "source_ref_id", "display_rank", "year"],
    demoNote: "ROUND 173 (N2): several identities COEXIST per content; idx_identity_remote is the reopen reverse-lookup",
    demo: [
      { main_id: M_FRI, source_kind: "anilist", source_ref_id: "154587", display_rank: 0, year: 2023 },
      { main_id: M_FRI, source_kind: "aniyomi", source_ref_id: "1234:/anime/frieren-sousou-no-frieren", display_rank: 1, year: null },
      { main_id: M_FRI, source_kind: "mal", source_ref_id: "52991", display_rank: 2, year: 2023 },
      { main_id: M_FRI, source_kind: "simkl", source_ref_id: "269520", display_rank: 2, year: 2023 },
      { main_id: M_FRI, source_kind: "tmdb", source_ref_id: "209867", display_rank: 2, year: 2023 },
      { main_id: M_AOT, source_kind: "anilist", source_ref_id: "16498", display_rank: 0, year: 2013 },
      { main_id: M_AOT, source_kind: "tmdb", source_ref_id: "60624", display_rank: 2, year: 2013 },
      { main_id: M_OSHI, source_kind: "tvmaze", source_ref_id: "48549", display_rank: 2, year: 2024 },
    ],
  },

  // ══════════════════════════════ GENRE (genres.sq) ═════════════════════════
  {
    name: "genre",
    file: "genres.sq",
    group: "genre",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true, def: "AUTOINCREMENT" }),
      c("anilist_name", "TEXT", { u: true, nn: true }),
      c("display_name", "TEXT", { nn: true }),
      c("category", "TEXT", { nn: true, def: "'genre'", note: "'genre'|'theme'|'demographic'" }),
      c("sort_key", "INTEGER", { nn: true, def: "0" }),
      c("is_nsfw", "INTEGER", { nn: true, def: "0" }),
    ],
    demoCols: ["id", "anilist_name", "category", "sort_key", "is_nsfw"],
    demoNote: "subset shown — seeded with AniList's canonical vocabulary (~40)",
    demo: [
      { id: 1, anilist_name: "Action", category: "genre", sort_key: 1, is_nsfw: 0 },
      { id: 2, anilist_name: "Adventure", category: "genre", sort_key: 2, is_nsfw: 0 },
      { id: 4, anilist_name: "Drama", category: "genre", sort_key: 4, is_nsfw: 0 },
      { id: 6, anilist_name: "Fantasy", category: "genre", sort_key: 6, is_nsfw: 0 },
      { id: 14, anilist_name: "Mystery", category: "genre", sort_key: 14, is_nsfw: 0 },
      { id: 19, anilist_name: "Supernatural", category: "genre", sort_key: 19, is_nsfw: 0 },
      { id: 22, anilist_name: "Ecchi", category: "genre", sort_key: 22, is_nsfw: 1 },
      { id: 25, anilist_name: "Adult Cast", category: "theme", sort_key: 25, is_nsfw: 0 },
      { id: 31, anilist_name: "Time Travel", category: "theme", sort_key: 31, is_nsfw: 0 },
      { id: 33, anilist_name: "Shounen", category: "demographic", sort_key: 33, is_nsfw: 0 },
      { id: 34, anilist_name: "Seinen", category: "demographic", sort_key: 34, is_nsfw: 0 },
    ],
  },
  {
    name: "content_genre",
    file: "genres.sq",
    group: "genre",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, fk: "main_entry.main_id" }),
      c("genre_id", "INTEGER", { pk: true, nn: true, fk: "genre.id" }),
      c("source", "TEXT", { nn: true, def: "'anilist'", note: "'anilist' | 'extension'" }),
    ],
    demoCols: ["main_id", "genre_id", "source"],
    demo: [
      { main_id: M_FRI, genre_id: 2, source: "anilist" },
      { main_id: M_FRI, genre_id: 6, source: "anilist" },
      { main_id: M_FRI, genre_id: 4, source: "anilist" },
      { main_id: M_AOT, genre_id: 1, source: "anilist" },
      { main_id: M_AOT, genre_id: 33, source: "anilist" },
      { main_id: M_MON, genre_id: 14, source: "anilist" },
      { main_id: M_MON, genre_id: 34, source: "anilist" },
      { main_id: M_DAN, genre_id: 1, source: "anilist" },
      { main_id: M_DAN, genre_id: 19, source: "anilist" },
    ],
  },

  // ══════════════════════════════ LIBRARY (library.sq) ══════════════════════
  {
    name: "library_category",
    file: "library.sq",
    group: "library",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true }),
      c("name", "TEXT", { u: true, nn: true }),
      c("display_order", "INTEGER", { nn: true, def: "0" }),
      c("is_permanent", "INTEGER", { nn: true, def: "0", note: "'Default' = 1, undeletable" }),
      c("created_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["id", "name", "display_order", "is_permanent"],
    demo: [
      { id: 1, name: "Default", display_order: 0, is_permanent: 1 },
      { id: 2, name: "Watching", display_order: 1, is_permanent: 0 },
      { id: 3, name: "Plan to Watch", display_order: 2, is_permanent: 0 },
      { id: 4, name: "Completed", display_order: 3, is_permanent: 0 },
    ],
  },
  {
    name: "library_item",
    file: "library.sq",
    group: "library",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true }),
      c("main_id", "TEXT", { nn: true, fk: "main_entry.main_id" }),
      c("category_id", "INTEGER", { nn: true, fk: "library_category.id" }),
      c("display_order", "INTEGER", { nn: true, def: "0" }),
      c("added_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["main_id", "category_id", "display_order", "added_at"],
    demoNote: "UNIQUE (main_id, category_id) — AOT sits in TWO categories (the D-381 semi-join shape)",
    demo: [
      { main_id: M_FRI, category_id: 2, display_order: 0, added_at: T0 - 30 * D },
      { main_id: M_DAN, category_id: 2, display_order: 1, added_at: T0 - 12 * D },
      { main_id: M_AOT, category_id: 1, display_order: 2, added_at: T0 - 90 * D },
      { main_id: M_AOT, category_id: 4, display_order: 0, added_at: T0 - 21 * D },
      { main_id: M_MON, category_id: 3, display_order: 0, added_at: T0 - 3 * D },
      { main_id: M_FMAB, category_id: 4, display_order: 1, added_at: T0 - 60 * D },
      { main_id: M_KIZ, category_id: 1, display_order: 3, added_at: T0 - 8 * D },
    ],
  },

  // ══════════════════════════ WATCH (watch.sq) ══════════════════════════════
  {
    name: "watch_progress",
    file: "watch.sq",
    group: "watch",
    cols: [
      c("episode_key", "TEXT", { pk: true, nn: true, note: "'{mainId}|{pad}' or '{mainId}|{s}|{pad}' (v5)" }),
      c("position", "INTEGER", { nn: true, note: "seconds" }),
      c("duration", "INTEGER", { nn: true }),
      c("completed", "INTEGER", { nn: true, def: "0" }),
      c("completed_at", "INTEGER"),
      c("last_watched_at", "INTEGER", { nn: true }),
      c("main_id", "TEXT", { note: "FK REMOVED (v4/D-862) — survives removal" }),
      c("watch_count", "INTEGER", { nn: true, def: "0" }),
      c("first_watched_at", "INTEGER"),
      c("auto_mark_suppressed", "INTEGER", { nn: true, def: "0", note: "CF1" }),
      c("user_marked_watched", "INTEGER", { nn: true, def: "0", note: "CF1 sticky" }),
      c("played_time_ms", "INTEGER", { nn: true, def: "0", note: "v2/D-789 cumulative gate (≥60s)" }),
      c("season_number", "INTEGER", { nn: true, def: "1", note: "v5 season axis" }),
      c("episode_number", "INTEGER", { nn: true, def: "0", note: "v5 denorm — kills CAST/SUBSTR scan" }),
    ],
    demoCols: ["episode_key", "completed", "played_time_ms", "season_number", "episode_number"],
    demoNote: "row 4 = the D-789 gate: pinned at EOF with only 2s played → auto-mark can NEVER fire; row 3 = CF1 un-mark (suppressed); row 1 = the v5 3-part TV key",
    demo: [
      { episode_key: "main-aot|2|00005", position: 1320, duration: 1440, completed: 1, played_time_ms: 1410000, season_number: 2, episode_number: 5 },
      { episode_key: "main-frieren|00012", position: 600, duration: 1440, completed: 1, played_time_ms: 1320000, season_number: 1, episode_number: 12 },
      { episode_key: "main-frieren|00013", position: 900, duration: 1440, completed: 0, played_time_ms: 540000, season_number: 1, episode_number: 13 },
      { episode_key: "main-dandadan|00004", position: 1440, duration: 1440, completed: 0, played_time_ms: 2000, season_number: 1, episode_number: 4 },
      { episode_key: "main-aot|00012", position: 720, duration: 1440, completed: 0, played_time_ms: 60000, season_number: 1, episode_number: 12 },
      { episode_key: "main-kizumono|00001", position: 5760, duration: 5760, completed: 1, played_time_ms: 5760000, season_number: 1, episode_number: 1 },
    ],
  },
  {
    name: "watch_session",
    file: "watch.sq",
    group: "watch",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true, def: "AUTOINCREMENT" }),
      c("main_id", "TEXT", { nn: true, note: "FK REMOVED (v4/D-862)" }),
      c("episode_key", "TEXT", { nn: true }),
      c("started_at", "INTEGER", { nn: true }),
      c("ended_at", "INTEGER", { nn: true }),
      c("played_ms", "INTEGER", { nn: true, def: "0", note: "DB-side +delta (no RMW race)" }),
    ],
    demoCols: ["main_id", "episode_key", "started_at", "played_ms"],
    demoNote: "one row per continuous streak — the Profile's per-day buckets (D-830)",
    demo: [
      { main_id: M_FRI, episode_key: "main-frieren|00012", started_at: T0 - 2 * D + 9 * H, played_ms: 2520000 },
      { main_id: M_FRI, episode_key: "main-frieren|00013", started_at: T0 - 1 * D + 20 * H, played_ms: 540000 },
      { main_id: M_FRI, episode_key: "main-frieren|00013", started_at: T0 - 1 * D + 21 * H, played_ms: 480000 },
      { main_id: M_AOT, episode_key: "main-aot|2|00005", started_at: T0 - 6 * D + 15 * H, played_ms: 1410000 },
    ],
  },

  // ══════════════════════════ TRACKING (track.sq) ═══════════════════════════
  {
    name: "track_entry",
    file: "track.sq",
    group: "tracking",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, note: "composite PK part 1 — FK REMOVED (v6)" }),
      c("tracker_type", "TEXT", { pk: true, nn: true, note: "'anilist'|'mal'|'simkl'|'tmdb'|'tvmaze'" }),
      c("tracker_id", "INTEGER", { nn: true, u: true, note: "UNIQUE (type, id) since v6 — 1 remote entry = 1 content" }),
      c("status", "TEXT", { nn: true, note: "TrackStatus enum value" }),
      c("score", "INTEGER", { note: "0–100 AniList-native" }),
      c("progress", "INTEGER", { nn: true, def: "0", note: "movies: 0/1" }),
      c("total_episodes", "INTEGER"),
      c("list_id", "INTEGER", { note: "AniList MediaList.id; others null" }),
      c("started_at", "INTEGER"),
      c("completed_at", "INTEGER"),
      c("updated_at", "INTEGER", { nn: true }),
      c("times_watched", "INTEGER", { nn: true, def: "0", note: "v6 — movie rewatch count" }),
    ],
    demoCols: ["main_id", "tracker_type", "tracker_id", "status", "score", "times_watched"],
    demoNote: "kizumono row = the movie-rewatch semantics (times_watched = 2); monster = PLAN_TO_WATCH",
    demo: [
      { main_id: M_FRI, tracker_type: "anilist", tracker_id: 154587, status: "WATCHING", score: 95, progress: 12, times_watched: 0 },
      { main_id: M_FRI, tracker_type: "mal", tracker_id: 52991, status: "WATCHING", score: 90, progress: 12, times_watched: 0 },
      { main_id: M_AOT, tracker_type: "anilist", tracker_id: 16498, status: "COMPLETED", score: 95, progress: 25, times_watched: 0 },
      { main_id: M_DAN, tracker_type: "mal", tracker_id: 48736, status: "WATCHING", score: 88, progress: 4, times_watched: 0 },
      { main_id: M_FMAB, tracker_type: "anilist", tracker_id: 5114, status: "COMPLETED", score: 100, progress: 64, times_watched: 0 },
      { main_id: M_KIZ, tracker_type: "anilist", tracker_id: 21221, status: "COMPLETED", score: 85, progress: 1, times_watched: 2 },
      { main_id: M_MON, tracker_type: "anilist", tracker_id: 30656, status: "PLAN_TO_WATCH", score: null, progress: 0, times_watched: 0 },
    ],
  },
  {
    name: "content_tracking_state",
    file: "track.sq",
    group: "tracking",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, note: "composite PK part 1 — FK REMOVED (v6)" }),
      c("tracker_type", "TEXT", { pk: true, nn: true }),
      c("tracked", "INTEGER", { nn: true, def: "0", note: "0 rows are KEPT — decisions are never forgotten" }),
      c("updated_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["main_id", "tracker_type", "tracked", "updated_at"],
    demoNote: "the WS-E contract: NOTHING syncs unless tracked = 1; linking alone never tracks",
    demo: [
      { main_id: M_FRI, tracker_type: "anilist", tracked: 1, updated_at: T0 - 30 * D },
      { main_id: M_FRI, tracker_type: "mal", tracked: 1, updated_at: T0 - 9 * D },
      { main_id: M_AOT, tracker_type: "anilist", tracked: 1, updated_at: T0 - 90 * D },
      { main_id: M_DAN, tracker_type: "mal", tracked: 1, updated_at: T0 - 12 * D },
      { main_id: M_MON, tracker_type: "anilist", tracked: 0, updated_at: T0 - 3 * D },
    ],
  },

  // ═══════════════════════ CACHES (dataCache.sq) ════════════════════════════
  {
    name: "data_cache_episode",
    file: "dataCache.sq",
    group: "caches",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, fk: "main_entry.main_id" }),
      c("episode_number", "REAL", { pk: true, nn: true }),
      c("title", "TEXT", { note: "AniList-enriched display title" }),
      c("description", "TEXT"),
      c("thumbnail_url", "TEXT"),
      c("air_date", "INTEGER"),
      c("fetched_at", "INTEGER", { nn: true }),
      c("episode_url", "TEXT"),
      c("source_name", "TEXT", { note: "DB-OPT: extension's ep.name → audio pills" }),
      c("scanlator", "TEXT", { note: "DB-OPT: ep.scanlator → audio pills" }),
      c("is_filler", "INTEGER", { note: "D-190: null=unknown, 0=no, 1=yes" }),
      c("is_recap", "INTEGER", { note: "D-190" }),
      c("title_japanese", "TEXT", { note: "D-190 Jikan/AniZip" }),
      c("title_romaji", "TEXT", { note: "D-190" }),
      c("runtime", "INTEGER", { note: "D-190 minutes" }),
      c("season_number", "INTEGER", { note: "D-190" }),
      c("episode_number_in_season", "INTEGER", { note: "D-190 AniZip" }),
      c("score", "REAL", { note: "D-190 Jikan 0–10" }),
    ],
    demoCols: ["main_id", "episode_number", "title", "is_filler", "runtime"],
    demoNote: "ep 13 row = is_filler NULL (Jikan failed → honest unknown, no badge)",
    demo: [
      { main_id: M_FRI, episode_number: 12, title: "Frieren the Slayer", source_name: "Frieren - S01E12 [SubsPlease]", scanlator: "SubsPlease", is_filler: 0, runtime: 24 },
      { main_id: M_FRI, episode_number: 13, title: "A Slightly Special Shop", source_name: "Frieren - S01E13 [SubsPlease]", scanlator: "SubsPlease", is_filler: null, runtime: 24 },
      { main_id: M_FRI, episode_number: 14, title: "It Would Be Shameful", source_name: "Frieren - S01E14 [SubsPlease]", scanlator: "SubsPlease", is_filler: 0, runtime: 24 },
      { main_id: M_AOT, episode_number: 5, title: "Declaration of War", source_name: "S04E05 - Declaration of War", scanlator: "HorribleSubs", is_filler: 0, runtime: 24 },
      { main_id: M_OSHI, episode_number: 6, title: "Return to Reality", source_name: "Oshi no Ko - S01E06", scanlator: "Golumpa", is_filler: 0, runtime: 24 },
    ],
  },
  {
    name: "browse_cache",
    file: "dataCache.sq",
    group: "standalone",
    cols: [
      c("section_key", "TEXT", { pk: true, nn: true, note: "namespaced per source — the D-278 trap" }),
      c("data_json", "TEXT", { nn: true, note: "versioned codec envelope" }),
      c("fetched_at", "INTEGER", { nn: true }),
      c("expires_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["section_key", "fetched_at", "expires_at"],
    demoNote: "row 3 = EXPIRED (the daily sweep reclaims it); row 4 = the round-176 search→Browse write-back",
    demo: [
      { section_key: "home_trending", fetched_at: T0 + 2 * H, expires_at: T0 + 8 * H },
      { section_key: "home_airs", fetched_at: T0 + 2 * H, expires_at: T0 + 8 * H },
      { section_key: "home_field_covers", fetched_at: T0 - 9 * H, expires_at: T0 - 3 * H },
      { section_key: "search_trending_anilist", fetched_at: T0 + 3 * H, expires_at: T0 + 15 * H },
    ],
  },

  // ═══════════════════════ DOWNLOADS (downloadQueue.sq / downloadedEpisode.sq) ═
  {
    name: "download_queue",
    file: "downloadQueue.sq",
    group: "downloads",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true, def: "AUTOINCREMENT" }),
      c("main_id", "TEXT", { nn: true, note: "no FK — logical ref" }),
      c("episode_key", "TEXT", { nn: true, u: true, note: "UNIQUE (main_id, episode_key)" }),
      c("content_id", "TEXT", { nn: true }),
      c("content_title", "TEXT", { nn: true }),
      c("episode_number", "REAL", { nn: true }),
      c("episode_name", "TEXT", { nn: true }),
      c("episode_description", "TEXT", { note: "D-242 SEpisode.summary" }),
      c("cover_url", "TEXT"),
      c("cover_color", "INTEGER"),
      c("source_id", "INTEGER"),
      c("video_server", "TEXT"),
      c("video_quality", "TEXT"),
      c("video_audio", "TEXT"),
      c("video_url", "TEXT", { nn: true }),
      c("video_headers", "TEXT", { note: "JSON: Map<String,String>" }),
      c("video_uri", "TEXT", { note: "content:// after publish" }),
      c("subtitle_tracks", "TEXT", { note: "JSON: List<SubtitleTrack>" }),
      c("audio_tracks", "TEXT", { note: "JSON: List<AudioTrack>" }),
      c("subtitle_uris", "TEXT", { note: "JSON after publish" }),
      c("state", "TEXT", { nn: true, def: "'QUEUED'", note: "QUEUED|DOWNLOADING|RETRYING|PAUSED|COMPLETED|ERROR|CANCELLED" }),
      c("progress", "INTEGER", { nn: true, def: "0" }),
      c("downloaded_bytes", "INTEGER", { nn: true, def: "0" }),
      c("total_bytes", "INTEGER", { nn: true, def: "-1", note: "-1 = unknown" }),
      c("prev_total_bytes", "INTEGER", { nn: true, def: "0" }),
      c("prev_estimate_bytes", "INTEGER", { nn: true, def: "0" }),
      c("recent_ratios_json", "TEXT", { note: "moving-average window" }),
      c("retry_attempt", "INTEGER", { nn: true, def: "0" }),
      c("retry_max_attempts", "INTEGER", { nn: true, def: "3" }),
      c("last_error", "TEXT"),
      c("resolve_context", "TEXT", { note: "JSON: proxy-churn re-resolve (M64)" }),
      c("queued_at", "INTEGER", { nn: true }),
      c("started_at", "INTEGER"),
      c("completed_at", "INTEGER"),
      c("updated_at", "INTEGER", { nn: true, def: "0" }),
    ],
    demoCols: ["main_id", "episode_number", "state", "progress", "retry_attempt"],
    demoNote: "ALL SEVEN states represented; the manga row rides content_format='images'",
    demo: [
      { main_id: M_AOT, episode_number: 13, state: "DOWNLOADING", progress: 45, total_bytes: 920000000, retry_attempt: 0 },
      { main_id: M_FRI, episode_number: 15, state: "QUEUED", progress: 0, total_bytes: -1, retry_attempt: 0 },
      { main_id: M_MON, episode_number: 3, state: "PAUSED", progress: 62, total_bytes: 48000000, retry_attempt: 0 },
      { main_id: M_FMAB, episode_number: 31, state: "COMPLETED", progress: 100, total_bytes: 540000000, retry_attempt: 0 },
      { main_id: M_OSHI, episode_number: 3, state: "RETRYING", progress: 30, total_bytes: 410000000, retry_attempt: 2 },
      { main_id: M_DAN, episode_number: 2, state: "CANCELLED", progress: 0, total_bytes: -1, retry_attempt: 1 },
    ],
  },
  {
    name: "downloaded_episode",
    file: "downloadedEpisode.sq",
    group: "downloads",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, note: "composite PK part 1 — no FK" }),
      c("episode_key", "TEXT", { pk: true, nn: true, note: "composite PK part 2" }),
      c("content_id", "TEXT", { nn: true }),
      c("content_title", "TEXT", { nn: true }),
      c("content_format", "TEXT", { nn: true, def: "'video'", note: "'video'|'images'|'text'|'audio'" }),
      c("content_type", "TEXT", { nn: true, def: "'anime'" }),
      c("episode_number", "REAL", { nn: true }),
      c("episode_name", "TEXT", { nn: true }),
      c("cover_url", "TEXT"),
      c("cover_color", "INTEGER"),
      c("content_folder_uri", "TEXT", { nn: true, note: "SAF DocumentFile URI" }),
      c("file_path", "TEXT", { nn: true }),
      c("file_size", "INTEGER", { nn: true }),
      c("quality", "TEXT"),
      c("video_uri", "TEXT"),
      c("video_file_name", "TEXT", { nn: true }),
      c("subtitle_uris", "TEXT", { note: "JSON: List<content:// URIs> — D-407 import" }),
      c("source_id", "INTEGER"),
      c("video_server", "TEXT"),
      c("video_audio", "TEXT"),
      c("verified_at", "INTEGER"),
      c("downloaded_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["main_id", "episode_number", "content_format", "file_size", "video_file_name"],
    demoNote: "CACHE/INDEX of disk; .data.json is the reinstall truth — the scanner reconciles on startup",
    demo: [
      { main_id: M_FMAB, episode_number: 30, content_format: "video", file_size: 538000000, video_file_name: "FMAB - E00030.mp4" },
      { main_id: M_FMAB, episode_number: 29, content_format: "video", file_size: 512000000, video_file_name: "FMAB - E00029.mp4" },
      { main_id: M_FRI, episode_number: 11, content_format: "video", file_size: 470000000, video_file_name: "Frieren - E00011.mp4" },
      { main_id: M_MON, episode_number: 2, content_format: "images", file_size: 31000000, video_file_name: "Monster - C00002.cbz" },
    ],
  },

  // ═══════════════════════ UPDATES (schedule / update / updateState) ════════
  {
    name: "episode_schedule",
    file: "episodeSchedule.sq",
    group: "updates",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true, def: "AUTOINCREMENT" }),
      c("main_id", "TEXT", { nn: true, fk: "main_entry.main_id" }),
      c("anilist_id", "INTEGER"),
      c("episode_number", "REAL", { nn: true }),
      c("scheduled_at", "INTEGER", { nn: true, note: "AniList airingAt — the PLANNED time" }),
      c("actual_at", "INTEGER", { note: "null until UpdateEngine detects the release" }),
      c("audio_variant", "TEXT", { nn: true, def: "'unknown'", note: "CF7: part of UNIQUE dedup" }),
      c("source", "TEXT", { nn: true, def: "'anilist'", note: "'anilist'|'extension'|'manual'" }),
      c("fetched_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["main_id", "episode_number", "audio_variant", "scheduled_at", "actual_at"],
    demoNote: "UNIQUE (main_id, ep, variant): ep 8 exists as sub AND dub rows",
    demo: [
      { main_id: M_DAN, episode_number: 8, audio_variant: "sub", scheduled_at: T0 + 1 * D, actual_at: null },
      { main_id: M_DAN, episode_number: 8, audio_variant: "dub", scheduled_at: T0 + 1 * D + 2 * H, actual_at: null },
      { main_id: M_OSHI, episode_number: 7, audio_variant: "sub", scheduled_at: T0 - 1 * D, actual_at: T0 - 1 * D + 47 * 60 * 1000 },
      { main_id: M_FRI, episode_number: 15, audio_variant: "sub", scheduled_at: T0 - 10 * D, actual_at: T0 - 10 * D + 9 * 60 * 1000 },
    ],
  },
  {
    name: "episode_update",
    file: "episodeUpdate.sq",
    group: "updates",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true, def: "AUTOINCREMENT" }),
      c("main_id", "TEXT", { nn: true, fk: "main_entry.main_id" }),
      c("episode_key", "TEXT", { nn: true }),
      c("episode_number", "REAL", { nn: true }),
      c("episode_title", "TEXT"),
      c("source_id", "INTEGER"),
      c("audio_variant", "TEXT", { nn: true, def: "'unknown'" }),
      c("discovered_at", "INTEGER", { nn: true }),
      c("acknowledged", "INTEGER", { nn: true, def: "0" }),
      c("acknowledged_at", "INTEGER", { note: "M9 retention rides this" }),
      c("batch_type", "TEXT", { nn: true, def: "'new'", note: "D-192: 'initial' = first-link batch" }),
      c("episode_count", "INTEGER", { note: "for 'initial': \"Episodes 1-12\"" }),
      c("new_expires_at", "INTEGER", { note: "D-193: the 'new' badge expiry (+3d)" }),
    ],
    demoCols: ["main_id", "episode_number", "audio_variant", "acknowledged", "batch_type"],
    demoNote: "getVariantsForEpisode feeds the SUB/DUB chips on the episode banner (D-499)",
    demo: [
      { main_id: M_OSHI, episode_number: 7, audio_variant: "sub", acknowledged: 0, batch_type: "new", new_expires_at: T0 + 3 * D },
      { main_id: M_OSHI, episode_number: 7, audio_variant: "dub", acknowledged: 0, batch_type: "new", new_expires_at: T0 + 3 * D },
      { main_id: M_MON, episode_number: 1, audio_variant: "unknown", acknowledged: 1, batch_type: "initial", new_expires_at: null },
      { main_id: M_FRI, episode_number: 14, audio_variant: "sub", acknowledged: 1, batch_type: "new", new_expires_at: T0 - 3 * D },
    ],
  },
  {
    name: "anime_update_state",
    file: "animeUpdateState.sq",
    group: "updates",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, fk: "main_entry.main_id" }),
      c("status", "TEXT", { note: "'RELEASING'|'FINISHED'|'NOT_YET_RELEASED'|'CANCELLED'|null" }),
      c("last_checked_at", "INTEGER"),
      c("next_check_at", "INTEGER", { note: "CF4 backoff scheduling" }),
      c("last_known_episode_count", "INTEGER", { note: "highest SUB seen" }),
      c("next_airing_episode", "INTEGER", { note: "S4 from Schedule fetch" }),
      c("next_airing_at", "INTEGER"),
      c("auto_update_enabled", "INTEGER", { nn: true, def: "1", note: "M3: 0 after 3 failures" }),
      c("consecutive_failures", "INTEGER", { nn: true, def: "0" }),
      c("backoff_step", "INTEGER", { nn: true, def: "0", note: "0..5 (1h→2h→4h→8h→24h cap)" }),
      c("last_known_dub_count", "INTEGER", { note: "D-193 dub tracking" }),
      c("last_checked_dub_at", "INTEGER"),
      c("total_episodes", "INTEGER", { note: "for FINISHED dub checks" }),
      c("learned_offset_ms", "INTEGER", { note: "D-193v2 smart-release averaging" }),
    ],
    demoCols: ["main_id", "status", "last_known_episode_count", "backoff_step", "learned_offset_ms"],
    demoNote: "fmab row = due for a DUB check (51 < 64, the COALESCE NULL-treatment query); kizumono = disabled after 3 failures",
    demo: [
      { main_id: M_DAN, status: "RELEASING", last_known_episode_count: 5, backoff_step: 2, learned_offset_ms: 1420000, next_check_at: T0 + 4 * H },
      { main_id: M_FMAB, status: "FINISHED", last_known_episode_count: 64, backoff_step: 0, learned_offset_ms: null, next_check_at: T0 + 6 * H },
      { main_id: M_FRI, status: "FINISHED", last_known_episode_count: 28, backoff_step: 0, learned_offset_ms: 540000, next_check_at: T0 + 12 * H },
      { main_id: M_KIZ, status: "FINISHED", last_known_episode_count: 1, backoff_step: 3, learned_offset_ms: null, next_check_at: null },
    ],
  },

  // ═══════════════════════ NOTIFICATIONS (notifications.sq) ═════════════════
  {
    name: "notification_config",
    file: "notifications.sq",
    group: "notifications",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, fk: "main_entry.main_id" }),
      c("enabled", "INTEGER", { nn: true, def: "1", note: "master toggle" }),
      c("notify_on_schedule", "INTEGER", { nn: true, def: "0" }),
      c("notify_on_watchable", "INTEGER", { nn: true, def: "1" }),
      c("notify_on_immediate", "INTEGER", { nn: true, def: "0" }),
      c("notify_sub", "INTEGER", { nn: true, def: "1" }),
      c("notify_dub", "INTEGER", { nn: true, def: "0" }),
    ],
    demoCols: ["main_id", "enabled", "notify_on_watchable", "notify_sub", "notify_dub"],
    demo: [
      { main_id: M_DAN, enabled: 1, notify_on_watchable: 1, notify_sub: 1, notify_dub: 1 },
      { main_id: M_FRI, enabled: 1, notify_on_watchable: 1, notify_sub: 1, notify_dub: 0 },
      { main_id: M_MON, enabled: 0, notify_on_watchable: 1, notify_sub: 1, notify_dub: 0 },
    ],
  },
  {
    name: "notification_sent",
    file: "notifications.sq",
    group: "notifications",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, fk: "main_entry.main_id" }),
      c("episode_number", "REAL", { pk: true, nn: true }),
      c("audio_variant", "TEXT", { pk: true, nn: true, def: "'unknown'" }),
      c("trigger_type", "TEXT", { pk: true, nn: true, note: "'schedule'|'watchable'|'immediate'" }),
      c("sent_at", "INTEGER", { nn: true, note: "90-day retention purge" }),
    ],
    demoCols: ["main_id", "episode_number", "audio_variant", "trigger_type", "sent_at"],
    demoNote: "NOT backup-eligible (ephemeral dedup log)",
    demo: [
      { main_id: M_DAN, episode_number: 8, audio_variant: "sub", trigger_type: "watchable", sent_at: T0 + 1 * D - 2 * H },
      { main_id: M_OSHI, episode_number: 7, audio_variant: "sub", trigger_type: "watchable", sent_at: T0 - 1 * D },
      { main_id: M_FRI, episode_number: 14, audio_variant: "sub", trigger_type: "schedule", sent_at: T0 - 6 * D },
    ],
  },

  // ═══════════════════════ RATINGS (ratings.sq) ═════════════════════════════
  {
    name: "user_rating",
    file: "ratings.sq",
    group: "ratings",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, fk: "main_entry.main_id" }),
      c("rating", "INTEGER", { nn: true, note: "0–100 AniList-native" }),
      c("rated_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["main_id", "rating", "rated_at"],
    demo: [
      { main_id: M_FRI, rating: 98, rated_at: T0 - 2 * D },
      { main_id: M_FMAB, rating: 100, rated_at: T0 - 60 * D },
      { main_id: M_AOT, rating: 95, rated_at: T0 - 20 * D },
    ],
  },
  {
    name: "user_episode_rating",
    file: "ratings.sq",
    group: "ratings",
    cols: [
      c("main_id", "TEXT", { pk: true, nn: true, fk: "main_entry.main_id" }),
      c("episode_key", "TEXT", { pk: true, nn: true }),
      c("rating", "INTEGER", { nn: true, note: "0–100, same scale" }),
      c("rated_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["main_id", "episode_key", "rating", "rated_at"],
    demo: [
      { main_id: M_FRI, episode_key: "main-frieren|00012", rating: 100, rated_at: T0 - 2 * D },
      { main_id: M_FRI, episode_key: "main-frieren|00013", rating: 90, rated_at: T0 - 1 * D },
      { main_id: M_AOT, episode_key: "main-aot|2|00005", rating: 96, rated_at: T0 - 6 * D },
    ],
  },

  // ════════════════════ MISC (playbackCache / tracking / appSettings) ═══════
  {
    name: "playback_cache_entry",
    file: "playbackCache.sq",
    group: "playback",
    cols: [
      c("cache_key", "TEXT", { pk: true, nn: true, note: "sha256(mainId·ep·source·serverKey) — NOT the URL" }),
      c("main_id", "TEXT", { nn: true, note: "no FK — disposable, may outlive content" }),
      c("anime_title", "TEXT", { nn: true }),
      c("episode_number", "REAL", { nn: true }),
      c("episode_title", "TEXT", { nn: true }),
      c("source_id", "INTEGER", { nn: true }),
      c("server_key", "TEXT", { nn: true }),
      c("quality", "TEXT", { nn: true }),
      c("content_type", "TEXT", { nn: true, def: "'video/mp4'" }),
      c("upstream_url", "TEXT", { nn: true, note: "refreshed every resolve (proxy churn)" }),
      c("upstream_headers", "TEXT", { nn: true, def: "''" }),
      c("content_length", "INTEGER"),
      c("cached_bytes", "INTEGER", { nn: true, def: "0" }),
      c("cached_ranges", "TEXT", { nn: true, def: "''", note: "merged sorted 'a-b,c-d'" }),
      c("complete", "INTEGER", { nn: true, def: "0" }),
      c("created_at", "INTEGER", { nn: true }),
      c("last_accessed_at", "INTEGER", { nn: true, note: "LRU eviction key" }),
      c("segment_total", "INTEGER", { nn: true, def: "0", note: "HLS" }),
      c("segments_cached", "INTEGER", { nn: true, def: "0" }),
      c("subtitle_tracks", "TEXT", { nn: true, def: "''" }),
      c("audio_tracks", "TEXT", { nn: true, def: "''" }),
    ],
    demoCols: ["main_id", "episode_number", "complete", "cached_bytes", "segments_cached"],
    demoNote: "row 1 = progressive byte-range cache; row 2 = HLS segment cache (D-246 identity recovery reads findEntriesByIdentity)",
    demo: [
      { main_id: M_FRI, episode_number: 13, complete: 0, cached_bytes: 4194304, segment_total: 0, segments_cached: 0 },
      { main_id: M_AOT, episode_number: 5, complete: 0, cached_bytes: 15800000, segment_total: 214, segments_cached: 89 },
      { main_id: M_FMAB, episode_number: 30, complete: 1, cached_bytes: 538000000, segment_total: 0, segments_cached: 0 },
    ],
  },
  {
    name: "activity_event",
    file: "tracking.sq",
    group: "standalone",
    cols: [
      c("id", "INTEGER", { pk: true, nn: true, def: "AUTOINCREMENT" }),
      c("event_type", "TEXT", { nn: true, note: "ActivityEventType value (WATCH_*, LIBRARY_*, SEARCH…)" }),
      c("content_key", "TEXT"),
      c("episode_key", "TEXT"),
      c("session_id", "TEXT", { nn: true }),
      c("route", "TEXT"),
      c("content_type", "TEXT"),
      c("duration_ms", "INTEGER"),
      c("payload", "TEXT"),
      c("timestamp", "INTEGER", { nn: true }),
    ],
    demoCols: ["id", "event_type", "content_key", "route", "timestamp"],
    demoNote: "internal analytics — pruned after 365 days, NOT backup-eligible",
    demo: [
      { id: 9041, event_type: "WATCH_COMPLETE", content_key: M_FRI, route: "watch", timestamp: T0 - 2 * D + 10 * H },
      { id: 9052, event_type: "SEARCH", content_key: null, route: "search", timestamp: T0 - 1 * D + 18 * H },
      { id: 9060, event_type: "LIBRARY_ADD", content_key: M_DAN, route: "details", timestamp: T0 - 12 * D },
      { id: 9074, event_type: "DOWNLOAD_START", content_key: M_AOT, route: "details", timestamp: T0 + 1 * H },
      { id: 9081, event_type: "RATING", content_key: M_FRI, route: "details", timestamp: T0 - 2 * D },
    ],
  },
  {
    name: "app_settings",
    file: "appSettings.sq",
    group: "standalone",
    cols: [
      c("setting_key", "TEXT", { pk: true, nn: true }),
      c("setting_value", "TEXT", { nn: true }),
      c("setting_type", "TEXT", { nn: true, def: "'string'", note: "bool|int|long|float|string|set" }),
      c("setting_category", "TEXT", { nn: true, def: "'general'" }),
      c("updated_at", "INTEGER", { nn: true }),
    ],
    demoCols: ["setting_key", "setting_value", "setting_type", "setting_category"],
    demoNote: "the backup/restore MIRROR — PreferenceStore stays the quick layer (partial mirror: see issues)",
    demo: [
      { setting_key: "auto_mark_threshold", setting_value: "0.85", setting_type: "float", setting_category: "player" },
      { setting_key: "download_quality", setting_value: "1080p", setting_type: "string", setting_category: "download" },
      { setting_key: "library_grid_density", setting_value: "3", setting_type: "int", setting_category: "appearance" },
      { setting_key: "trackers_enabled_mal", setting_value: "1", setting_type: "bool", setting_category: "general" },
    ],
  },
];

// ═════════════════════════════════════════════════════════════════════════════
// THE PANEL LAYOUT — the canvas is a hierarchy of tinted category panels:
//
//   level 0  LOOKUPS      (the FK parents of main_entry)
//   level 1  SPINE        (main_entry · content_details · content_identity)
//   level 2  the direct main_id dependents (genre, library, watch, trackers, caches)
//   level 3  the grandchildren (downloads, updates, notifications, ratings)
//   level 4  playback + the STANDALONE panel (grey: three unlinked tables)
//
// Same tint + same place = one category (or one linked cluster). Grey = not
// linked to anything. Panels are packed left-to-right per level; the gutter
// between levels is where the arrows run.
// ═════════════════════════════════════════════════════════════════════════════

export interface PanelDef {
  id: GroupId;
  level: number;
  x: number;
  y: number;
  w: number;
  h: number;
  arrangement: "row" | "col";
  members: string[];
  standalone?: boolean;
}

const PANEL_PAD = 14;
const PANEL_HEADER = 32;
const CARD_GAP = 26;
const LEVEL_GUTTER = 92;
/** the arrow corridor between the spine panel and level 2 */
const CORRIDOR = 200;
const LEVEL_X0 = 40;

export function columnsRows(n: number): 1 | 2 {
  return n > 16 ? 2 : 1;
}

/** the schema-column block height (2-column layout for very wide tables) */
export function columnsBlockHeight(t: { cols: unknown[] }): number {
  const rows = Math.ceil(t.cols.length / columnsRows(t.cols.length));
  return rows * 21;
}

export function estimateTableHeight(t: { cols: unknown[]; demo: unknown[]; demoNote?: string }): number {
  // MUST stay in sync with the CSS constants (header 32px, column row 21px,
  // demo pad 8/10, demo label 18px, demo value row 20px, note ≈2 lines 40px).
  const demoH =
    t.demo.length > 0 ? 8 + 18 + t.demo.length * 20 + (t.demoNote ? 40 : 0) + 10 : 0;
  return 32 + columnsBlockHeight(t) + 4 + demoH;
}

const PANEL_SPEC: { id: GroupId; level: number; arrangement: "row" | "col"; members: string[] }[] = [
  { id: "lookups", level: 0, arrangement: "row", members: ["data_source", "system"] },
  { id: "spine", level: 1, arrangement: "row", members: ["main_entry", "content_details", "content_identity"] },
  { id: "genre", level: 2, arrangement: "row", members: ["genre", "content_genre"] },
  { id: "library", level: 2, arrangement: "row", members: ["library_category", "library_item"] },
  { id: "watch", level: 2, arrangement: "row", members: ["watch_progress", "watch_session"] },
  { id: "tracking", level: 2, arrangement: "row", members: ["track_entry", "content_tracking_state"] },
  { id: "caches", level: 2, arrangement: "row", members: ["data_cache_episode"] },
  { id: "downloads", level: 3, arrangement: "row", members: ["download_queue", "downloaded_episode"] },
  { id: "updates", level: 3, arrangement: "row", members: ["episode_schedule", "episode_update", "anime_update_state"] },
  { id: "notifications", level: 3, arrangement: "row", members: ["notification_config", "notification_sent"] },
  { id: "ratings", level: 3, arrangement: "row", members: ["user_rating", "user_episode_rating"] },
  { id: "playback", level: 4, arrangement: "row", members: ["playback_cache_entry"] },
  { id: "standalone", level: 4, arrangement: "row", members: ["activity_event", "app_settings", "browse_cache"] },
];

function panelSize(p: (typeof PANEL_SPEC)[number], byName: Record<string, RawTable>) {
  const cards = p.members.map((m) => byName[m]);
  const innerGap = (cards.length - 1) * CARD_GAP;
  const tallest = Math.max(...cards.map((c) => estimateTableHeight(c)));
  let w: number;
  let h: number;
  if (p.arrangement === "row") {
    w = cards.reduce((a, c) => a + cardWidth(c), 0) + innerGap + PANEL_PAD * 2;
    h = PANEL_HEADER + PANEL_PAD + tallest + PANEL_PAD;
  } else {
    w = Math.max(...cards.map((c) => cardWidth(c))) + PANEL_PAD * 2;
    h =
      PANEL_HEADER +
      PANEL_PAD +
      cards.reduce((a, c) => a + estimateTableHeight(c), 0) +
      innerGap +
      PANEL_PAD;
  }
  return { w, h };
}

const byName = Object.fromEntries(RAW_TABLES.map((t) => [t.name, t])) as Record<string, RawTable>;

export const PANELS: PanelDef[] = (() => {
  const sizeById = new Map<GroupId, { w: number; h: number }>();
  for (const p of PANEL_SPEC) sizeById.set(p.id, panelSize(p, byName));

  // per level: pack left→right, then centre the level inside the widest level
  const levels = new Map<number, (typeof PANEL_SPEC)[number][]>();
  for (const p of PANEL_SPEC) {
    levels.set(p.level, [...(levels.get(p.level) ?? []), p]);
  }
  const levelWidth = (lv: number) => {
    const ps = levels.get(lv) ?? [];
    return ps.reduce((a, p) => a + (sizeById.get(p.id)?.w ?? 0), 0) + Math.max(0, ps.length - 1) * LEVEL_GUTTER;
  };
  const widest = Math.max(...[...levels.keys()].map(levelWidth));

  const out: PanelDef[] = [];
  let y = 40;
  for (const lv of [...levels.keys()].sort((a, b) => a - b)) {
    const ps = levels.get(lv)!;
    let x = LEVEL_X0 + (widest - levelWidth(lv)) / 2;
    const levelH = Math.max(...ps.map((p) => sizeById.get(p.id)?.h ?? 0));
    for (const p of ps) {
      const s = sizeById.get(p.id)!;
      out.push({
        id: p.id,
        level: lv,
        x,
        y,
        w: s.w,
        h: s.h,
        arrangement: p.arrangement,
        members: p.members,
        standalone: p.id === "standalone",
      });
      x += s.w + LEVEL_GUTTER;
    }
    y += levelH + (lv === 1 ? CORRIDOR : LEVEL_GUTTER);
  }
  return out;
})();

export const PANEL_BY_ID = Object.fromEntries(PANELS.map((p) => [p.id, p])) as Record<GroupId, PanelDef>;

export function panelOf(tableName: string): PanelDef {
  const group = byName[tableName].group;
  return PANEL_BY_ID[group];
}

/** cards get their coordinates from their panel */
export const TABLES: TableDef[] = (() => {
  const placed: TableDef[] = [];
  for (const p of PANELS) {
    let cx = p.x + PANEL_PAD;
    let cy = p.y + PANEL_HEADER + PANEL_PAD;
    for (const name of p.members) {
      const raw = byName[name];
      placed.push({ ...raw, x: cx, y: cy, w: cardWidth(raw) });
      if (p.arrangement === "row") cx += cardWidth(raw) + CARD_GAP;
      else cy += estimateTableHeight(raw) + CARD_GAP;
    }
  }
  // keep the source order of RAW_TABLES so iteration order stays predictable
  return placed.sort((a, b) => RAW_TABLES.findIndex((r) => r.name === a.name) - RAW_TABLES.findIndex((r) => r.name === b.name));
})();

/** the arrow corridor: the band under the spine panel where hub spokes run */
export const CORRIDOR_Y = (() => {
  const spine = PANEL_BY_ID.spine;
  return spine.y + spine.h + 16;
})();

// ── quick accessors ───────────────────────────────────────────────────────────
export const TABLE_BY_NAME = Object.fromEntries(
  TABLES.map((t) => [t.name, t]),
) as Record<string, TableDef>;

export const GROUP_BY_ID = Object.fromEntries(
  GROUPS.map((g) => [g.id, g]),
) as Record<GroupId, GroupDef>;

export const WORLD_BOUNDS = (() => {
  let maxX = 0;
  let maxY = 0;
  for (const p of PANELS) {
    maxX = Math.max(maxX, p.x + p.w);
    maxY = Math.max(maxY, p.y + p.h);
  }
  return { w: maxX + 40, h: maxY + 40 };
})();

/** horizontal trunk lanes in the outer margins (deep spokes climb these) */
export const TRUNK_LEFT = 16;
export const TRUNK_RIGHT = WORLD_BOUNDS.w - 16;
