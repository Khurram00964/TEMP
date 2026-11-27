// ─────────────────────────────────────────────────────────────────────────────
// The relationship map — every FK and doctrine-ref edge in the schema (25).
//
//   fk      (solid blue  #2563eb)  — enforced FOREIGN KEY (PRAGMA foreign_keys=ON)
//   logical (dashed teal  #0d9488) — a deliberate main_id reference with NO FK
//             (the D-862 survival doctrine: watch/track/cache/download rows
//             must outlive the identity row they hang off)
//   seed    (dotted blue #94a3b8)  — the one-time 6.sqm seed that populated
//             content_identity from the older stores
//
// Routing is DERIVED from the panel layout (src/lib/geometry.ts) — the routes
// below only declare the KIND of path, never coordinates:
//   lookup       — a lookup table (above) → main_entry's left edge
//   hubAdjacent  — the card next to main_entry (content_details) → its right edge
//   hub1b        — a spine sibling below main_entry (content_identity) → bottom
//   hub2         — a level-2 dependent → up into the corridor → main_entry bottom
//   hubDeep      — a level-3/4 dependent → level gutter → outer trunk → corridor
//   adjacentL    — a peer card to the LEFT inside the same panel
//   seedAdjacent — the 6.sqm seed, peer to peer inside the spine panel
//   seedLong     — the 6.sqm seed from a distant panel → content_identity's right
// ─────────────────────────────────────────────────────────────────────────────

export type EdgeKind = "fk" | "logical" | "seed";

export type EdgeRoute =
  | "lookup"
  | "hubAdjacent"
  | "hub1b"
  | "hub2"
  | "hubDeep"
  | "adjacentL"
  | "seedAdjacent"
  | "seedLong";

export interface EdgeDef {
  /** "table.col" the arrow leaves from */
  from: string;
  /** "table.col" the arrow points at */
  to: string;
  kind: EdgeKind;
  route: EdgeRoute;
  /** small perpendicular nudge so bundled lines stay readable */
  nudge?: number;
  label: string;
}

export const EDGES: EdgeDef[] = [
  // ── enforced FKs ──────────────────────────────────────────────────────────
  { from: "main_entry.data_source_id", to: "data_source.id", kind: "fk", route: "lookup", nudge: -6, label: "metadata source lookup" },
  { from: "main_entry.system_id", to: "system.id", kind: "fk", route: "lookup", nudge: 6, label: "ecosystem lookup (D-540)" },
  { from: "content_details.main_id", to: "main_entry.main_id", kind: "fk", route: "hubAdjacent", label: "1:1 metadata row" },
  { from: "content_genre.main_id", to: "main_entry.main_id", kind: "fk", route: "hub2", label: "genre junction" },
  { from: "content_genre.genre_id", to: "genre.id", kind: "fk", route: "adjacentL", label: "genre vocab" },
  { from: "library_item.main_id", to: "main_entry.main_id", kind: "fk", route: "hub2", label: "library membership" },
  { from: "library_item.category_id", to: "library_category.id", kind: "fk", route: "adjacentL", label: "category" },
  { from: "data_cache_episode.main_id", to: "main_entry.main_id", kind: "fk", route: "hub2", label: "enriched episode rows" },
  { from: "episode_schedule.main_id", to: "main_entry.main_id", kind: "fk", route: "hubDeep", label: "airing plan" },
  { from: "episode_update.main_id", to: "main_entry.main_id", kind: "fk", route: "hubDeep", label: "new-episode feed" },
  { from: "anime_update_state.main_id", to: "main_entry.main_id", kind: "fk", route: "hubDeep", label: "check state" },
  { from: "notification_config.main_id", to: "main_entry.main_id", kind: "fk", route: "hubDeep", label: "per-content switches" },
  { from: "notification_sent.main_id", to: "main_entry.main_id", kind: "fk", route: "hubDeep", label: "dedup log" },
  { from: "user_rating.main_id", to: "main_entry.main_id", kind: "fk", route: "hubDeep", label: "per-anime rating" },
  { from: "user_episode_rating.main_id", to: "main_entry.main_id", kind: "fk", route: "hubDeep", label: "per-episode rating" },

  // ── doctrine refs (deliberately FK-free) ──────────────────────────────────
  { from: "content_identity.main_id", to: "main_entry.main_id", kind: "logical", route: "hub1b", label: "multi-link identities (v7) — FK-free" },
  { from: "watch_progress.main_id", to: "main_entry.main_id", kind: "logical", route: "hub2", label: "D-862: history survives removal" },
  { from: "watch_session.main_id", to: "main_entry.main_id", kind: "logical", route: "hub2", label: "session ledger — FK-free" },
  { from: "track_entry.main_id", to: "main_entry.main_id", kind: "logical", route: "hub2", label: "tracker cache — FK-free (v6)" },
  { from: "content_tracking_state.main_id", to: "main_entry.main_id", kind: "logical", route: "hub2", label: "opt-in flag — FK-free (v6)" },
  { from: "download_queue.main_id", to: "main_entry.main_id", kind: "logical", route: "hubDeep", label: "active queue — no FK" },
  { from: "downloaded_episode.main_id", to: "main_entry.main_id", kind: "logical", route: "hubDeep", label: "disk index — no FK" },
  { from: "playback_cache_entry.main_id", to: "main_entry.main_id", kind: "logical", route: "hubDeep", label: "video cache — disposable" },

  // ── the 6.sqm seed provenance ────────────────────────────────────────────
  { from: "content_details.data_source_ref_id", to: "content_identity.source_ref_id", kind: "seed", route: "seedAdjacent", label: "6.sqm seed: data axis → identities" },
  { from: "track_entry.tracker_id", to: "content_identity.source_ref_id", kind: "seed", route: "seedLong", label: "6.sqm seed: tmdb/tvmaze track rows → identities" },
];

// ── card geometry constants — MUST match globals.css ─────────────────────────
export const CARD_HEADER_H = 32;
export const COL_ROW_H = 21;
export const CARD_PAD_X = 24; // where a top/bottom exit sits inside a card
