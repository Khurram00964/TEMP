# 10 — THE UI-REUSE PRINCIPLE + THE STATE/IDENTITY RULES (D2/D3)

> The standing order (round 173): ONE surface for many sources. If something
> can be shared across materials, it MOST DEFINITELY should be. Any new agent
> building UI reads this FIRST.

## 1. THE ONE-SURFACE RULE

- **The search page is THE model:** ONE page, ONE card composable
  (`UnifiedResultCard`), ONE grid (`DataSourceResultsGrid`) serving FIVE
  data sources (AniList/TMDB/TVmaze/MAL/Simkl). A new source NEVER gets its
  own page/copy — it plugs into the shared machinery (the picker row, the
  result mapper, the details route).
- **The details page is THE model:** ONE `DetailsScreen` for every content
  kind. The metadata sources render through the synthetic-AniList route
  (`loadMetadataContent` → `UnifiedAnime` → the SAME page). No parallel
  details screens (the round-170 revert proved it).
- **The link sheet is THE model:** ONE `UnifiedLinkSheet` for all five
  linking flows — the UI layer shared, the backend per source
  (`searchUnifiedLink`/`pickUnifiedLink` dispatch on the source string).
  Adding a source = a new `when` branch + a logo res, NOT a new sheet.
- **The menu vocabulary** (the three-dot menu's language — reuse it for ANY
  new bottom-up menu): the section LABELS (all-caps, 10.5sp, primary tint,
  `MenuSectionLabel`), the crimped CARDS (`MenuSectionCard` — the brighter
  layer: surfaceVariant on dark / surface on the grey light panel), the
  disc ROWS (`MenuDiscRow` — the 38dp tinted circle + a 19dp glyph + a
  14sp Bold label; the logo variant `MenuDiscRowWithLogo`), the destructive
  tint = `colorScheme.error`.

## 2. THE SHEET ANATOMY (any ModalBottomSheet)

- `dragHandle = null`, `RoundedCornerShape(topStart/topEnd = 20-28.dp)`,
  `containerColor = surface`, `skipPartiallyExpanded = true`.
- The height cap: `heightIn(max = screenHeight * 0.78f..0.85f)`.
- The content Column: `padding(horizontal = 20.dp)` +
  `navigationBarsPadding()`.
- The header: the icon/LOGO disc + the title (20sp ExtraBold) + the close
  disc (32dp surfaceVariant circle + an 18dp Close icon).
- NO description paragraphs unless the user explicitly asks (the round-172
  order: "remove all of it").
- The results rows: the cover (48x64, 8dp clip) + the title (14sp ExtraBold,
  max 2 lines) + the "★ rating · year" meta row (11sp) + the action pill.
- The states: the spinner (32dp, primary) / the icon+message empty (with a
  CTA) / the error (the REAL reason + the retry button — never a silent
  empty).

## 3. THE STATE-RESET RULES (the VM)

- **Every load path resets EVERYTHING** (the D-131 discipline): the state,
  the bases, the episode/track/link/notice flows, the flags. A missed field
  = the previous content's shadow.
- **The generation guards** (`loadGeneration`/`relinkGeneration`): every
  async block captures the gen at launch and DISCARDS the stale results
  before touching any state. The audit round-173 lesson: `refreshLinkedIdentities`
  missed BOTH the gen guard and the sync state capture — the cross-content
  identity bleed (the F3 phantom rows).
- **The declaration-order rule:** a StateFlow assigned in an init block must
  be DECLARED ABOVE the init (the round-169 NPE).
- **The extension-function cluster rules** (the D-923 pattern): the cluster
  files (DetailsViewModelTracking/MultiTracker) read `internal` fields —
  widening `private → internal` is the sanctioned move; the clusters never
  re-declare state.

## 4. THE IDENTITY-KEYING RULES (round 173's model)

- **`content_identity` is THE authoritative identity set** (N2): one row per
  (main_id, source_kind); the reverse-unique index
  `(source_kind, source_ref_id)` resolves the reopens. Every link/unlink/pick
  writes/deletes a row. `content_details`' axes remain the write-through
  cache for the library batch loader.
- **The metadata entries:** mainId = `"kind:remoteId"`; the packed id =
  `-(remoteId*8 + kindIndex)` (tmdb=1, tvmaze=2, mal=3, simkl=4); the decode
  inverts. The keying: `currentAnimeId = packedId` (the link keys persist
  under it), `_isMetadataEntry = true`.
- **LINK ≠ TRACK:** the link flows store the IDENTITY only (the
  content_identity rows; the MAL/Simkl mappings double as the track seeds in
  track_entry). The tracking flows never consult the TMDB/TVMAZE types; the
  tracking opt-in lives in content_tracking_state.
- **The Simkl ids are SIMKL-NATIVE** (the round-173 fix): the sync/fetch
  probe `/{kind}/{id}` FIRST (200 = the owning kind), the anilist/tmdb
  ladder second.
- **EVERY Simkl request carries the user token** (the live 401) — refreshed
  via ensureFreshToken; the failures THROW (SimklRequestException) and the
  UI surfaces them.

## 5. THE ERROR-SURFACING RULE (round 173)

- A silent empty list is a BUG. The strict getters (getStrict) throw with
  the HTTP code; the flows catch → the user-visible feedback: the toast for
  the refresh failures ("Refresh failed due to network error" / "Refresh
  failed"), the in-sheet error + the Search-again button for the link
  searches, the Error state for the searches.
- The one-shot APIs (TVmaze/Simkl) get the honest "That's every result"
  end-cap — the list is COMPLETE, not broken.

## 6. THE TEST-CHECKLIST RULE

- The checklists follow the LOGICAL user flow (search → picker → cards →
  details → menu → link → track → settings), NOT the implementation order;
  never re-list the confirmed-working items (the round-173 order).
