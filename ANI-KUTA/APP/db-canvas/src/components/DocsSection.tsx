"use client";

import { FAMILIES, famFor } from "@/src/data/tables";
import { MIGRATIONS, RESOLVED, ISSUES, STRENGTHS } from "@/src/data/meta";
import { useTheme } from "./ThemeContext";

const Check = ({ double = false }: { double?: boolean }) => (
  <svg
    className="check"
    width={double ? 32 : 17}
    height="15"
    viewBox="0 0 32 16"
    aria-hidden="true"
  >
    <path
      d="M2 8.5 L7 13.5 L15.5 1.5"
      fill="none"
      stroke="#16a34a"
      strokeWidth="2.7"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
    {double && (
      <path
        d="M15 8.5 L20 13.5 L28.5 1.5"
        fill="none"
        stroke="#16a34a"
        strokeWidth="2.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    )}
  </svg>
);

// ── per-group census (verified against the .sq files, 2026-11-23) ────────────
const CENSUS: {
  group: string;
  tables: string;
  keys: string;
  links: string;
  touches: string;
}[] = [
  {
    group: "Seeded Lookups",
    tables: "data_source · system",
    keys: "id",
    links: "referenced by main_entry FKs",
    touches: "seeded once at first launch; edited only by app updates (never by the user)",
  },
  {
    group: "Identity Spine",
    tables: "main_entry · content_details · content_identity",
    keys: "main_id / main_id 1:1 / (main_id, source_kind)",
    links: "15 of the enforced FKs hang here; content_identity is FK-free",
    touches: "ContentRepository (switch/link/unlink/axis merges), the details VMs, the startup ecosystem heal + reconcile",
  },
  {
    group: "Genre System",
    tables: "genre · content_genre",
    keys: "id / (main_id, genre_id)",
    links: "both FK → main_entry + genre",
    touches: "written by the data-axis refresh; read by filters/profile",
  },
  {
    group: "Library",
    tables: "library_category · library_item",
    keys: "id / id",
    links: "item FKs → entry + category; UNIQUE (main_id, category_id)",
    touches: "the Library UI + the 2-query batch loader (D-285); EXISTS semi-joins guard the Schedule (D-381)",
  },
  {
    group: "Watch",
    tables: "watch_progress · watch_session",
    keys: "episode_key / id AUTOINCREMENT",
    links: "NO FK — D-862: history survives removal (v4)",
    touches: "the player's 10 s saves, WatchSessionRecorder, the reactive TrackingWatchSyncBridge (getAllHighestWatchedNumbers)",
  },
  {
    group: "Trackers",
    tables: "track_entry · content_tracking_state",
    keys: "(main_id, tracker_type) both",
    links: "NO FK (v6); UNIQUE (tracker_type, tracker_id) after in-file dedup",
    touches: "tracker repos + the link sheets write; the sync bridge reconciles tracked contents only (WS-E)",
  },
  {
    group: "Metadata Caches",
    tables: "data_cache_episode",
    keys: "(main_id, episode_number)",
    links: "FK CASCADE",
    touches: "DataCacheRepository; the enriched per-episode rows feed audio pills, fillers and the episode list",
  },
  {
    group: "Download System",
    tables: "download_queue · downloaded_episode",
    keys: "id / (main_id, episode_key)",
    links: "no FK — DISK + .data.json is the truth, the DB is a reconciled index",
    touches: "the queue engine (7 states, retry, re-resolve) + DownloadScanner (INSERT OR IGNORE, B1)",
  },
  {
    group: "Update Engine",
    tables: "episode_schedule · episode_update · anime_update_state",
    keys: "id / id / main_id",
    links: "all FK CASCADE",
    touches: "UpdateEngine worker (backoff, dub checks), ScheduleEngine, the 'New' feed + SUB/DUB variant evidence (D-499)",
  },
  {
    group: "Notifications",
    tables: "notification_config · notification_sent",
    keys: "main_id / (main_id, ep, variant, trigger)",
    links: "both FK CASCADE",
    touches: "NotificationConfigStore + the schedule engine; the sent-log is a 90-day dedup log, NOT backup-eligible",
  },
  {
    group: "Ratings",
    tables: "user_rating · user_episode_rating",
    keys: "main_id / (main_id, episode_key)",
    links: "both FK CASCADE",
    touches: "RatingStore; 0–100 AniList-native; backup-eligible",
  },
  {
    group: "Playback Cache",
    tables: "playback_cache_entry",
    keys: "cache_key",
    links: "no FK (disposable, may outlive content rows)",
    touches: "PlaybackCacheManager (identity-hash keys, LRU eviction, progressive + HLS)",
  },
  {
    group: "Not linked to anything",
    tables: "activity_event · app_settings · browse_cache",
    keys: "id / setting_key / section_key",
    links: "NO relationship at all — no FK in or out, no main_id spoke",
    touches: "ActivityTracker (365-day prune), the SettingsRepository mirror (partial — see S6), the browse/search section cache",
  },
];

export function DocsSection() {
  const { theme } = useTheme();
  const pill = (fam: keyof typeof FAMILIES) => {
    const c = FAMILIES[fam][theme];
    return { background: c.fill, borderColor: c.border, color: c.text };
  };

  return (
    <div className="sheet">
      {/* ── 1. read me first ── */}
      <hr className="section-rule" />
      <div className="section-head">
        <span className="section-pill" style={pill("yellow")}>
          1 · Read me first
        </span>
      </div>
      <p className="prose">
        The <b>Canvas</b> tab is the <span className="kw">whole database</span> — all{" "}
        <span className="u-red">27 tables</span>, none left out, each showing its real columns
        (copied verbatim from the <span className="mono">.sq</span> files) plus a{" "}
        <span className="u-blue">demo data block</span> that is always visible and exercises every
        state the schema models. Tables are <span className="kw">grouped into tinted panels</span>{" "}
        — one colour + one place per category, arranged as the real hierarchy (lookups → the spine
        → its dependents → their dependents) — and the three tables that link to{" "}
        <span className="u-red">nothing at all</span> sit apart in the{" "}
        <span className="mono">grey dashed</span> panel.
      </p>
      <ul className="prose-list">
        <li>
          <span className="kw">scroll wheel</span> = zoom around the cursor (8%–250%);{" "}
          <span className="kw">left-click drag</span> = pan the sheet.
        </li>
        <li>
          <span className="kw">hover a column cell</span> = trace it — the wire lights up and the
          exact cell it connects to highlights on the other table (FK → PK, both ways).
        </li>
        <li>
          <span className="kw">click a table</span> = spotlight it — everything else dims and its
          arrows turn crimson; <span className="mono">Esc</span> clears.
        </li>
        <li>
          <span className="mono">fit</span> shows the whole hierarchy; <span className="mono">1:1</span>{" "}
          returns to full size. Collapse the header (the chevron) for the full screen.
        </li>
      </ul>
      <p className="prose">
        Arrow language — two channels at once: the <span className="kw">line style</span> says what
        kind of link it is, and the <span className="kw">colour</span> says which category it
        comes from.
      </p>
      <ul className="prose-list">
        <li>
          <span className="kw">solid</span> = an enforced FOREIGN KEY (the driver enables{" "}
          <span className="mono">PRAGMA foreign_keys = ON</span>).
        </li>
        <li>
          <span className="kw">dashed</span> = a deliberate{" "}
          <span className="mono">main_id</span> reference with NO FK — the D-862 survival doctrine.
        </li>
        <li>
          <span className="kw">dotted</span> = the one-time 6.sqm seed that filled{" "}
          <span className="mono">content_identity</span> from the older stores.
        </li>
      </ul>
      <p className="prose">
        Every arrow wears its <span className="u-blue">source panel&apos;s colour</span>, leaves
        from a <span className="u-red">port dot on the exact cell</span>, and is routed through the
        panel gutters and shared riser lanes — no wire ever crosses a table.
      </p>

      {/* ── 2. the spine ── */}
      <hr className="section-rule" />
      <div className="section-head">
        <span className="section-pill" style={pill("yellow")}>
          2 · The spine
        </span>
      </div>
      <svg className="spine-fig" viewBox="0 0 880 322" role="img" aria-label="The identity spine diagram">
        <text className="fig-caption" x="24" y="22">
          THE SPINE — EVERYTHING HANGS OFF main_id
        </text>
        <rect className="n-lookup" x="30" y="52" width="190" height="60" rx="11" strokeWidth="1.8" />
        <text className="node-label n-lookup-t" x="45" y="80">data_source</text>
        <text className="node-sub" x="45" y="100">5 seeds · metadata vs tracking</text>
        <rect className="n-lookup" x="30" y="140" width="190" height="60" rx="11" strokeWidth="1.8" />
        <text className="node-label n-lookup-t" x="45" y="168">system</text>
        <text className="node-sub" x="45" y="188">4 ecosystems · the D-540 rule</text>
        <rect className="n-spine" x="330" y="86" width="205" height="76" rx="11" strokeWidth="1.8" />
        <text className="node-label n-spine-t" x="345" y="118">main_entry</text>
        <text className="node-sub" x="345" y="140">stable main_id · content_id</text>
        <rect className="n-spine" x="630" y="40" width="222" height="76" rx="11" strokeWidth="1.8" />
        <text className="node-label n-spine-t" x="645" y="72">content_details</text>
        <text className="node-sub" x="645" y="94">the data_* axis + the ext_* axis</text>
        <rect className="n-spine" x="630" y="152" width="222" height="76" rx="11" strokeWidth="1.8" />
        <text className="node-label n-spine-t" x="645" y="184">content_identity</text>
        <text className="node-sub" x="645" y="206">the multi-link set (v7) · rank 0 drives</text>
        <g className="fig-wire" strokeLinecap="round" strokeLinejoin="round" fill="none">
          <path d="M220 82 H 285 V 112 H 330" />
          <path d="M220 170 H 285 V 136 H 330" />
          <path d="M535 112 H 585 V 78 H 630" />
          <path d="M535 128 H 585 V 190 H 630" />
        </g>
        <g className="fig-head">
          <path d="M330 112 l -9 -4.5 v 9 Z" />
          <path d="M330 136 l -9 -4.5 v 9 Z" />
          <path d="M630 78 l -9 -4.5 v 9 Z" />
          <path d="M630 190 l -9 -4.5 v 9 Z" />
        </g>
        <text className="fig-note" x="24" y="268">
          main_entry = the stable identity (main_id UUID never changes; content_id regenerates on
          every switch/link/unlink). content_details holds TWO independent axes in one row —
        </text>
        <text className="fig-note" x="24" y="290">
          the data_* (AniList/metadata) and ext_* (Aniyomi/CloudStream) — each merged by COALESCE,
          replaced on switches, cleared on unlinks. content_identity (round 173) is the
        </text>
        <text className="fig-note" x="24" y="312">
          authoritative multi-link record: AniList + MAL + Simkl + TMDB/TVmaze + the extension
          link coexist; display_rank 0 drives the details page.
        </text>
      </svg>

      {/* ── 3. the census ── */}
      <hr className="section-rule" />
      <div className="section-head">
        <span className="section-pill" style={pill("mint")}>
          3 · The census — who owns what
        </span>
      </div>
      <div className="table-scroll">
        <table className="sheet-table">
          <thead>
            <tr>
              <th style={{ width: "15%" }}>group</th>
              <th className="th-pk" style={{ width: "22%" }}>
                <span className="pk-micro">TABLES</span>all 27, verbatim
              </th>
              <th style={{ width: "19%" }}>primary keys</th>
              <th style={{ width: "18%" }}>link discipline</th>
              <th>written / read by</th>
            </tr>
          </thead>
          <tbody>
            {CENSUS.map((r) => (
              <tr key={r.group}>
                <td>{r.group}</td>
                <td className="mono" style={{ textAlign: "left" }}>{r.tables}</td>
                <td className="mono">{r.keys}</td>
                <td>{r.links}</td>
                <td>{r.touches}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <span className="keynote">
        counts verified against the .sq files · main = schema v7 · 15 enforced FKs + 8 doctrine-free
        refs + 2 seed edges = 25 arrows
      </span>

      {/* ── 4. the migration ladder ── */}
      <hr className="section-rule" />
      <div className="section-head">
        <span className="section-pill" style={pill("peach")}>
          4 · The migration ladder — v1 → v7
        </span>
      </div>
      <p className="prose">
        The rule (DATABASE-CONTRACT.md): <span className="u-red">every schema change ships in a new
        numbered .sqm with its backfill inside the same file</span>, gets a lock test that runs the
        real <span className="mono">Schema.migrate</span>, and the legacy hasColumn-probe block in{" "}
        <span className="mono">DatabaseDriverFactory</span> is frozen — no new probes, ever.
      </p>
      <div className="table-scroll">
        <table className="sheet-table">
          <thead>
            <tr>
              <th className="th-pk" style={{ width: "12%" }}>
                <span className="pk-micro">STEP</span>version
              </th>
              <th style={{ width: "12%" }}>file</th>
              <th style={{ width: "16%" }}>round</th>
              <th>what it did</th>
            </tr>
          </thead>
          <tbody>
            {MIGRATIONS.map((m) => (
              <tr key={m.file}>
                <td className="mono">{m.v}</td>
                <td className="mono">{m.file}</td>
                <td>{m.round}</td>
                <td>{m.what}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <span className="keynote">
        the one documented exception: the v1.1.96–99 cohort (v5 without season_number) is repaired
        by migrateV5ShapeIfNeeded BEFORE the chain runs — everything else goes through the
        versioned ladder only
      </span>

      {/* ── 5. what's solid ── */}
      <hr className="section-rule" />
      <div className="section-head">
        <span className="section-pill" style={pill("lavender")}>
          5 · What&apos;s solid — the health check
        </span>
      </div>
      <ul className="prose-list">
        {STRENGTHS.map((s, i) => (
          <li key={i}>
            <Check double={i === 0} />
            {s}
          </li>
        ))}
      </ul>

      {/* ── 6. issues & watch-list ── */}
      <hr className="section-rule strong" />
      <div className="section-head">
        <span className="section-pill" style={pill("rose")}>
          6 · Issues &amp; the watch-list
        </span>
      </div>
      <p className="prose">
        The register below (<span className="kw">B1–B7</span>, <span className="kw">R1–R6</span>)
        is the repo&apos;s own record —{" "}
        <span className="u-red">every entry was fixed with a lock test</span>, so a regression is a
        red build, not a silent wipe. What follows are the <span className="kw">open</span>{" "}
        findings from this review.
      </p>
      <div className="table-scroll">
        <table className="sheet-table">
          <thead>
            <tr>
              <th style={{ width: "8%" }}>id</th>
              <th style={{ width: "44%" }}>the flaw (as it was)</th>
              <th>the fix</th>
            </tr>
          </thead>
          <tbody>
            {RESOLVED.map((r) => (
              <tr key={r.id}>
                <td className="mono">
                  <Check /> {r.id}
                </td>
                <td>{r.flaw}</td>
                <td>{r.fix}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="sticky-grid" style={{ marginTop: 26 }}>
        {ISSUES.map((iss) => (
          <div className="sticky" key={iss.id}>
            <div className="sticky-head">
              <span className={`sev sev-${iss.severity}`}>{iss.severity}</span>
              <span className="sticky-title">
                <span className="mono">{iss.id}</span> — {iss.title}
              </span>
            </div>
            <p>{iss.body}</p>
            <div className="sticky-evidence">{iss.evidence}</div>
            <div className="sticky-footer-tag">
              {iss.severity === "INFO" ? "by design ✓" : "needs attention"}
            </div>
          </div>
        ))}
      </div>

      <div className="sheet-footer">
        <span className="ok">✓ End of sheet — every table shown, every claim anchored</span>
        <span>
          ANI-KUTA · core/database · 17 .sq files (app.sq empty) · demo data is illustrative,
          state-complete
        </span>
      </div>
    </div>
  );
}
