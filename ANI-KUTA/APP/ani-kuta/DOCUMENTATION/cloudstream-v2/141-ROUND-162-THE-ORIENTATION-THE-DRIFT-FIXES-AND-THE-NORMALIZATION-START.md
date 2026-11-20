# Record 141 — Round 162 (2026-11-21): the orientation, the drift fixes, and the normalization start

> The user's order: a full deep re-orientation of the project (with an explicit
> round-2 verification — "verify, don't hand down"), then fix ALL the
> documentation drift (the dashboard stays frozen — a separate Next.js rebuild
> is a future consideration), then the code-quality set, organize the signing
> keys locally, create specialized sub-agents, lock the confirmed response
> format into the docs — then report and give the IMPROVEMENTS-TO-DO.md
> overview so the user directs the redo.

## D-921 — the round-2 verification (the user's two suspicions confirmed)

**The two players — verified in code, end to end:**

- **Player #1 (MPV — Aniyomi/extension + AniList):** entry via `WatchKey`
  (MainActivity 1256/1366 → WatchScreen), `AnikutaMPVView` + `PlayerStateHolder`
  + `PlayerObserver` (:core:player → :core:player-mpv-lib), the single-instance
  ADR-025 cache, `fd://` offline conversion (MPV cannot open `content://`),
  the playback-cache proxy MPV-only, NO background playback (ON_STOP pauses).
- **Player #2 (Media3 ExoPlayer — CloudStream):** two doors — online via
  `CsResolveSheet` (csResolveRequest) + OFFLINE direct
  (`onNavigateToCsOfflineWatch`, the D-539/D-548 cache/.dashmeta routes),
  `CsPlayerEngine` (:core:cs-player, Media3 + MergingMediaSource), screen-owned
  engine (the identical ADR-025 carve-out the docs never mentioned), Media3
  SimpleCache offline, torrent seam via :core:torrent (never raw).
- **The branch point:** source IDENTITY (`isLinkedSourceCloudStream()`,
  DetailsScreen.kt) for streaming; content FORMAT for downloads (a DASH
  episode ALWAYS routes to the CS player — "MPV cannot demux a manifest",
  MainActivity.kt).
- **Shared:** watch-progress, preferences, ratings, download, designsystem.
  ZERO shared player code (verified by import audit both directions — "CS
  links NEVER touch MPV" holds).
- **A third surface:** the extension test suite's muted 30s ExoPlayer preview
  card (diagnostic-only). NO trailer player (TrailerData is dead model code).
- **NO PiP/background/MediaSession on main** — the reverted PlaybackService
  exists only on release/1.1.98+.

**The design language — 11 stale claims fixed in DESIGN-LANGUAGE.md:**
the pointed badges are GONE (D-480 soft pills + shadows; PointedTagShape.kt is
dead legacy), the D-257 outlines removed, `headerBlurEffect` is a DEAD toggle
(the switch renders, nothing reads it), the back button lives IN the
CollapsingHeader (D-559; BackAction has one caller left), the floating pill
nav is SHIPPED (opaque, not translucent), the hero is 302:404 @ 28dp with
BIDIRECTIONAL auto-advance (not the doc's 16:9/20dp/forward-only), Bricolage
Grotesque was missing from §5, the scrim is a 7-stop gradient, and the
never-documented systems got §2.7 (honeycomb, hero wash, adaptive accent,
presets, random palette, AppToast, the TWO 4-layout episode systems,
drawer-vs-sheet).

**The inconsistencies found in the docs:** module-map/architecture carried
Round-115 counts (33 core/57 total/25 tables/"no .sqm needed"/Koin 30/44
branches) — reality 35/59/26/3 .sqm v1→v4/33/52; `:core:backup` +
`:core:torrent` were absent from module-map.md; cs-watch's impl deps omitted
:core:torrent (D-858); :data:extension's loader is PARENT-first (D-294 — the
doc said child-first); build-apk.yml's header claimed tests gate every push
(D-465 removed them); AndroidConfig's comment claimed 0.4.20/85; code comments
cited doc sections that no longer exist (§5/§5.9/§8 — re-anchored).

## D-921 — the drift fixes (all verified against code first)

- `knowledge/module-map.md` + `knowledge/architecture.md`: re-verified counts,
  the two missing core rows, the migration truth, the loader direction, the
  ADR-025 CS carve-out, the debt list updates (5 god files; the .sqm era
  started in round 127).
- `knowledge/ui-customization.md`: the dead toggle marked, the "translucent
  cards" recipe noted, the player's second 4-layout system added.
- `APP/ani-kuta/DESIGN-LANGUAGE.md`: §2.2/§2.4/§2.5/§3/§5 rewritten + §2.7.
- `.github/workflows/build-apk.yml` + `AndroidConfig.kt` comments.
- The code comment re-anchors: Shape.kt, BottomNavBar.kt, ScrollBlurOverlay.kt,
  CollapsingHeader.kt, FullscreenControls.kt (the tray list's pip mention).
- **CORE_RULES §3.1:** the user-confirmed response format (lead with outcome,
  emoji sections, critical items flagged, tables, short bullets, the decision
  block) — locked for task summaries AND analysis reports.
- **The specialized agents** (`.zcode/agents/`, versioned in the repo):
  `anikuta-verifier` (read-only code-truth), `anikuta-docs-medic` (drift
  repair, never the ledger), `anikuta-splitter` (verbatim normalization), +
  `anikuta-ci-runner` (the TEMP push→dispatch→poll loop).

## D-922 — the code-quality batch (CI 44806908899 GREEN)

1. **The light theme gap:** `LightColorScheme` gains `error` (#B3261E) +
   `onError` (white) + `surfaceContainerHighest` (Surface4Light #D8D4C8 — the
   ramp now has 4 tiers like the dark ramp's usage); `DarkColorScheme` gains
   `onError` (#601410). `buildCustomColorScheme` inherits (no override).
2. **The primitive-soup callbacks:** `WatchLaunchArgs` (the 17-param
   onNavigateToWatch) + `CsEpisodeLaunchArgs` (the 11/9/12-param CS trio,
   `offlineMediaUri` only on the D-539 route) — named-arg construction at all
   7 DetailsScreen call sites + the 8 MainActivity wiring lambdas; the
   WatchKey/CsWatchKey constructions are named too (the former WatchKey call
   passed 14 fields positionally). A swapped pair can no longer compile.
3. **The dead `onPiPClick`** removed from FullscreenControls (declared with a
   default, never wired; no PiP on main — the reverted round-157 PiP work will
   re-add its own wiring when re-landed).
4. **The orphan build residue** (core/tmdb, core/tracker-mal,
   core/tracker-trakt — untracked reverted-work leftovers) deleted.
5. **The signing keys organized** (the user's order): `credentials/keystores/`
   — the release keystore + keystore.properties + the provenance txts (copied
   from SECURITY/ANI-KUTA-v1.1.1-DELIVERY, the original untouched), the debug
   keystore backup, and NOTES.md (the alias, the cert SHA-256 f05160db…a02531f
   as verified in round 156, the TEMP secrets map, the rotation state). THE
   ACCESS STATE verified the same session: the dev PAT (testplay-byte) is DEAD
   (401 — the flagged account degraded further); TEMP + the official repo and
   their PATs are healthy; TEMP main = local main (the revert is in sync).

## D-923 — the god-file normalization START (CI 44808332206 RED → 44809252465 GREEN)

**The pattern set** (the D-808 successor for ViewModel-shaped files):

- Top-level TYPES move as whole files — `DetailsViewModelState.kt`
  (DetailsState/LinkedSource/NextEpisodeInfo/ManualSearchState/AutoLinkState/
  ReverseAutoLinkState/AniListSearchState/EpisodeState/ResolverState/
  TrackingNotice + TrackingNoticeKind, 140 lines verbatim).
- Method clusters move as SAME-PACKAGE EXTENSION FUNCTIONS with
  private→internal widening (module-only) — `DetailsViewModelTracking.kt`
  (the D-242 cluster: openTrackSheet/saveTrackEntry/startTracking/pushTrackEntry/
  applyTrackProgressToLocal/earliestFirstWatchedAt/removeTracking/
  deleteFromAniList + the mark-previous/series machinery, 450 lines verbatim;
  the three private helpers stay `private` as file-scoped extensions — the
  same reachability they had; a file-local TAG mirror keeps the bodies
  byte-identical).
- The invariant: initialized-once FIELDS (the MutableStateFlows, `isTracked`'s
  stateIn, `_pendingRemoteTrackEntry`) and the init-block observer STAY IN
  CLASS — an extension property would re-instantiate the flow on every read.
- `DetailsViewModel.kt`: 4,965 → 4,383 lines. Zero behavior change; the
  screens/host call sites untouched (same-package extensions resolve without
  imports).

**The staged continuation:** the mapper agent mapped the remaining seams of
DetailsViewModel (the refresh machinery, the load generation, the link
machines, fetchEpisodes) + LibraryScreen/WatchScreen/MainActivity — one
cluster per CI cycle, the user directs when.

## The session facts

- Commits: 1cfd14d1 (docs+agents) → 3dd274d4 (code quality) → 2528c820
  (normalization) → 9c7507d9 (the swept-declarations repair) — on main,
  pushed to TEMP (testplay-byte unreachable; its PAT dead).
- CI: 44806908899 GREEN (batch 1) + 44808332206 RED → 44809252465 GREEN
  (batch 2: the first cut swept the _trackSheetError/_trackSheetSaving FIELD
  pair into the extension file — they sat between two methods, not in the
  fields region; the 9c7507d9 repair moved them back in-class and the re-run
  verified green. THE LESSON: enumerate every declaration in a cut range
  before moving — banked in lessons-learned as the swept-declarations miss).
- The ledger: progress.md (the ROUND 162 block + the Round 162 section),
  decisions.md (D-921..D-923), changelog.md, lessons-learned.md (the
  hand-down-doc lesson, the extension-split invariant, the dead-credential
  fail mode, the swept-declarations miss), ledger_162.py in planning/.
- THE SEAM MAP: planning/ROUND-162-SPLIT-SEAM-MAP.md — the mapper agent's
  per-file regions + ranked candidates (LOW/MED/HIGH batches) drive the
  normalization continuation (one cluster per CI cycle).
- The dashboard stays frozen per the user's order; the redo list
  (IMPROVEMENTS-TO-DO.md §6) awaits the user's order.
