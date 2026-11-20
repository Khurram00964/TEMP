# ANI-KUTA App Design Language

> The canonical design language for the ANI-KUTA Android app. This is a **living
> document** — updated as design decisions are made. When the user requests UI
> changes, update this doc AND propagate to the code.
>
> **Status:** Fresh start (session web-3a43f99b). The old design language doc
> was deleted. We're rebuilding from the ground up, one rule at a time.
>
> **Distinction:** This is the *app's* design language. The *dashboard's* design
> language lives at `DASHBOARD/webpage/DESIGN.md` (separate product).

---

## 1. Purpose

This document captures the UI/UX rules that define how the ANI-KUTA app looks,
feels, and moves. Every screen follows these rules. When in doubt, refer here.

Rules are added one at a time, only after the user confirms them. Don't add
speculative rules.

---

## 2. Rules

### 2.1 — Collapsing Header (shrinks on scroll)

Every screen with a scrollable list has a **pinned top header** with the screen
title. The header:

- **Expanded (at top):** Large title — 32sp, ExtraBold (800), letterSpacing
  -0.02sp. Status-bar padding applies.
- **Collapsed (scrolled past 20px):** Title shrinks to 24sp (ExtraBold). The
  transition animates via `animateFloatAsState`, 300ms, `FastOutSlowInEasing`.
- **Pinned:** The header sits OUTSIDE the scroll container. It never scrolls
  away — only the title size changes. Actions (back, settings, etc.) stay in
  place.
- **Implementation:** `CollapsingHeader` composable in `:core:designsystem`.
  Pass a `LazyListState` / `LazyGridState` and compute `collapsed =
  state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 20`.

### 2.2 — Scroll Blur Overlay (frosted-glass at header edge)

When content scrolls beneath the pinned header, a **frosted-glass blur overlay**
fades in at the header's bottom edge:

- **At top (scroll = 0):** No blur. The header's background is solid.
- **Scrolled:** A gradient scrim fades in — transparent → `surface.copy(alpha
  = 0.8f)` → `surface` — at the header's bottom edge. The scrim has rounded
  bottom corners.
- **Scroll back to top:** The blur fades out smoothly. The header grows back
  to full size.
- **Implementation:** `ScrollBlurOverlay` composable in `:core:designsystem`.
  Takes a `scrollOffset: () -> Float` lambda. Fades in based on the offset.
  Aligned `TopCenter` over the content Box.
- **Scrim shape (verified 2026-11-21):** a 7-stop top-to-bottom gradient
  (alpha 1.0 / 0.92 / 0.70 / 0.42 / 0.18 / 0.05 / 0.0) at a default 36dp
  height, 24dp corner radius, smoothstepped over 24px.
- **Toggle:** `ThemePreferences.headerBlurEffect` is currently a **DEAD
  setting** — the switch renders in Settings, but NO `ScrollBlurOverlay` call
  site passes it (the only `enabled=` usage in the repo is an unrelated tab
  check in UpdatesScreen). The overlay always renders. Wiring the toggle in
  (or removing the switch) is pending a user decision.

### 2.3 — Hide-on-Scroll Top Bar (Search page specific)

On screens with a multi-row top section (search bar + filters + source toggle),
the **entire top section smoothly hides** when the user scrolls down, and
**smoothly reappears** when scrolling back up:

- **Expanded:** Title (large) + source toggle (AniList/Extension) + search bar
  + filters/sort row — all visible.
- **Scrolled down:** The source toggle + filters row fade + slide out. The
  search bar animates up to replace the toggle's position. The title shrinks.
- **Scrolled back to top:** Everything smoothly re-expands.
- **Animation:** 300ms, `FastOutSlowInEasing`. Use `animateDpAsState` for
  position, `animateFloatAsState` for alpha.
- **Implementation:** `SearchTopBar` in `:feature:anime-search`. The pattern
  can be reused on other multi-row screens if needed.

---

### 2.4 — Nav-Row Icon Language (unified across More + Settings hubs)

Every list row that navigates to a sub-screen — whether on the **More** page or
any **Settings / Appearance / Notifications** hub — uses the **same icon
treatment**: a **24dp `Icon` tinted `primary`, seated on the shared
[`SettingsIconTile`](../core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/SettingsIconTile.kt)
— a 38dp rounded-square tile (`RoundedCornerShape(12.dp)`) tinted
`primary.copy(alpha = 0.12f)`** (a translucent lime wash that reads as its own
surface against the row's `surfaceVariant@0.4` card). The red notification dot
rides the tile's top-end corner.

- **Implementation:** the tile is ONE shared primitive in `:core:designsystem`
  ([`SettingsIconTile`](../core/designsystem/src/main/java/com/confused/anikuta/core/designsystem/component/SettingsIconTile.kt));
  [`MoreListRow`](#1-more-page) renders it for every nav row. Hand-rolled rows
  that copy the MoreListRow anatomy (the About update-check row, the debug
  door row, the settings search-result row) call it directly. Do NOT create a
  per-screen `*NavRow` variant or a differently-shaped/colored tile — one
  recipe everywhere is the entire point (that was D-250's real lesson).
- **Typography:** title `RobotoFamily ExtraBold 16sp`, subtitle `Normal 13sp`,
  trailing `Icons.Filled.ChevronRight`. All inherited from `MoreListRow`.
- **Back button:** SUPERSEDED by D-558/D-559 — `CollapsingHeader` now takes an
  `onBack` param and renders the arrow + the title as ONE combined leading
  clickable element (the heading text IS the back button). The standalone
  shared `BackAction` composable still exists in `:core:designsystem` but is
  used by only one screen (SettingsScreen).
- **History:** D-250 (2026-08-24) banned the tile — but the offending pattern
  was per-screen `primaryContainer` CHIP VARIANTS that made Settings look like
  a different format from the More page, not tiles per se; the fix unified the
  rows to bare icons. D-744 (2026-10-02, user order) re-introduces the tile:
  the user asked for the icons to "have a background… a square with rounded
  corners… a background color separate from the background itself," and the
  settings search-result row had already established the quiet translucent
  recipe. The tile now lives in ONE primitive, so the D-250 inconsistency
  class cannot recur. If a revert is ever wanted, it is a four-call-site change.

---

### 2.5 — Cover Badge Language (soft pills, D-480)

Badges overlaid on cover art (Library grid modes, Browse cards) share one
visual language. **Current state (D-480, superseding the D-252 pointed tags):**

- **SOFT PILLS**: every chip is a fully-rounded pill (`RoundedCornerShape(50)`)
  with a soft shadow (shadowElevation 4dp) for legibility on busy art. The
  user retired the 45° pointed tips (`PointedTagShape`) and the D-257 1dp
  outlines — "the badges look sharp and don't match the theme."
  `PointedTagShape.kt` still exists in `:core:designsystem:badge` as DEAD
  legacy (delete candidate).
- **Floating variant (D-533/D-489)**: a TOP_CENTER floating badge with a 2dp
  inset exists beside the corner-flush rows.
- **Corner-flush rows**: the badge row sits flush with the cover corner; its
  outer corner clips to the cover's corner radius (12dp rounded modes, 0dp for
  COVER_ONLY's square covers — `coverCornerRadius` param).
- **Shared colors**: `BadgeColorScheme` (designsystem) — SUB blue / DUB
  orange / Total green / Score amber / All-Caught-Up red, hand-picked Material
  pairs that adapt to the APPLIED theme (background luminance, not the system
  setting). Browse's score tag uses the same amber score colors — no
  per-screen badge palettes.
- **Compound badges** (SUB+DUB split) draw their split background with
  `drawBehind` — always `Modifier.clip(shape).drawBehind { ... }` (clip BEFORE
  draw), because M3 Surface applies its own shape-clip AFTER user modifiers.

### 2.6 — Custom Theme (D-254 / D-261)

When the CUSTOM accent preset is active, the theme comes entirely from the
user's `CustomThemeColors` — six elements (D-261 added `cardHeading` +
`cardDescription`; brightness offsets were removed entirely per device feedback
"there is no need for the brightness sliders at all"):

- `accent` → primary color family seed (`AccentColors.from` derivation).
- `background` → the app canvas; surface ramp derived (lerps toward text).
- `heading` → big screen titles → `LocalHeadingColor` (CollapsingHeader, plus
  the Library header clone since D-261).
- `card` → blocks/surfaces family → surfaceVariant + surfaceContainer tiers.
- `cardHeading` → D-261 NEW — title text inside cards/blocks →
  `LocalCardHeadingColor` (Browse cards, Library grid/list titles, Search
  result cards, Details anime title + block headers + episode rows).
- `cardDescription` → D-261 NEW — body/description text inside cards/blocks →
  `LocalCardDescriptionColor` (Browse subtitles, Library list meta, Details
  synopsis body + episode meta).

Custom colors apply **as-is in both light & dark mode**; the mode toggle only
affects presets. AMOLED is skipped while custom is active. One pick derives a
coherent theme: text colors by background luminance; surface ramp = background
lerped toward text; card family → surfaceVariant/containers.

Editor + picker sheet rules (D-259 / D-261):
- Editor sheets (CustomPaletteSheet) use a STICKY header (title + Reset
  OUTSIDE the scroll area; no X button — dismiss via swipe/scrim) with a
  scroll-driven `ScrollBlurOverlay` scrim at the top of the content.
- All RGBA channel sliders in ColorPickerSheet are **colorful** (D-263): the
  red slider's track is a black→red gradient, green's is black→green, blue's
  is black→blue, alpha's is transparent→opaque of the current color.
- Every numeric value renders as a TAPPABLE chip that opens the shared
  `NumericEntrySheet` keypad (live-applied).
- Presets are exactly FIVE distinct colors in a single equal-width line of
  rounded tiles.
- **Random palette (D-263)**: a Random button (`Icons.Filled.Casino`) sits
  left of the Reset button; tapping opens a nested `RandomPaletteSheet` with
  three options — Random dark / Random light / Completely random. Each
  generates a palette (constrained-HSV for the first two; fully-random
  per-channel with alpha forced opaque for chaos) and applies it via the
  same `setCustomTheme` path (persists across restart — D-261 fixed the
  v0.2.49/v0.2.50 persistence bug).
- The editor forces alpha opaque — translucent theme surfaces are not
  supported.

### 2.7 — Shipped Systems the Early Doc Eras Never Ruled (verified 2026-11-21)

One-line each; these are real, shipped design systems in `:core:designsystem`
/ feature modules:

- **Honeycomb suggestion field** (`HomeSuggestionField.kt`, anime-browse) —
  7×5 = 35-cover honeycomb, odd rows offset half a cell, 60×90dp tiles r14
  with 10dp gutter, lens magnification (center 1.5×, corners 0.72×),
  drag/tap physics, 30-pass pairwise min-gap solver.
- **Adaptive accent theming** (`AdaptiveAccent.kt` + `CoverColorExtractor.kt`)
  — AndroidX Palette on a 100×100 cover bitmap (vibrant→darkVibrant→muted→
  dominant, sat ≥ 0.40 / lightness 0.40–0.65), cached per-anime in DB;
  `AdaptiveAccentTheme` re-tints ONLY the primary quartet (details page,
  episode sheets, player) while app surfaces stay.
- **Accent preset engine** (`AccentPreset.kt`) — 10 accent-only presets
  (Lime, Coral, Rose, Amber, Red, Teal, Blue, Cyan, Violet, Emerald) + CUSTOM;
  `AccentColors.from(seed)` derives the 8-slot primary family via lerps.
- **Random palette generator** (`RandomPalette.kt`) — Dark/Light/Chaos;
  dark+light replicate the app's own ramp structure at a random family hue
  (±25°, WCAG-checked); chaos is fully random with alpha forced opaque.
- **AppToast** (`component/toast/AppToast.kt`) — the ONE themed toast:
  thread-safe StateFlow host above the nav bar, 3 tones (NEUTRAL/SUCCESS/
  ERROR), slide-up 280ms / 2.4s hold / sink-out 320ms. No raw
  `Toast.makeText` in feature UI.
- **Player drawer-vs-sheet presentation** (`PlayerSideDrawer.kt`) — portrait
  keeps bottom-up sheets; fullscreen/landscape presents the SAME panels as a
  fullscreen side drawer (callers pass `asDrawer`).
- **Player episode-list layouts** (`PlayerEpisodeListLayouts.kt`) — a SECOND
  4-layout system beside the details-page one:
  `CLASSIC / TRACKLIST / GRID / BANNER` (lenient `fromKey` migration).
- **Shared-element system** (`SharedTransitionLocals.kt`) — namespaced keys
  per screen (§6), 450ms page crossfade vs 600ms cover morph.

---

## 3. Shipped Rules (device-confirmed) + Pending

### 3.1 — Shipped (promoted from pending — verified in code 2026-11-21)

- **Floating pill bottom nav** — SHIPPED: `AnikutaBottomNavBar` in
  `:core:designsystem`, 4 tabs (Browse | Library | Search | More) in
  MainActivity. Nuance: the pill is OPAQUE `surfaceVariant` + 8dp shadow —
  not translucent as first sketched. Active tab gets a fully-rounded 28dp
  pill; scale 0.95f on press, no ripple.
- **Button/card press feedback** — SHIPPED (the no-ripple variant):
  NavPill scales 0.95f, MoreListRow 0.97f on press.
- **Section card backgrounds** — SHIPPED as the `surfaceVariant@0.4` recipe
  (settings rows, list cards).
- **Bottom-up sheet cap** — shipped per-screen (e.g. TrackSheet caps at
  85% screen height).
- **Translucent cards** — shipped as the surfaceVariant-at-reduced-alpha
  recipe, not a distinct "no shadow" card class.
- **Hero carousel (v4 — the current truth, superseding D-257's 16:9 note)**:
  a 302:404 portrait card, 28dp corners, neighbor peek scaling (0.88/0.66
  read inside `graphicsLayer`), glass chips, two-layer scrim, dots BELOW the
  card, bidirectional infinite auto-advance (5.5s cycle, 8s manual hold-off,
  3×N copies with silent middle re-anchor — direction-aware seam wrap both
  ways, NOT forward-only) over the **HeroWashUnderlay** — the active cover's
  art blurred 20dp + dimmed as a wash behind the hero AND the merged top
  bar, crossfading with the FRACTIONAL carousel position (round 155/156).

### 3.2 — Pending (not yet confirmed)

- (none currently queued)

---

## 4. Color System

(Lives in `:core:designsystem/theme/Color.kt`. Will be documented here as
rules solidify.)

- **Dark theme (default):** Warm-purple-tinted darks. Background `#14111F`,
  5-tier surface ramp → `#3D3656`. Primary lime `#B1F256`. Error pair themed
  (`ErrorDark`/`OnErrorDark`).
- **Light theme:** Warm-neutral backgrounds. Background `#FAF9F6`, 4-tier
  surface ramp (`Surface1-4Light`), error pair themed (`ErrorLight`
  `#B3261E` / `OnErrorLight` white — 2026-11-21; the scheme previously fell
  back to M3 defaults).
- **AMOLED:** Pure black backgrounds/surfaces when `amoled = true` + dark.
- **Accent override:** `AnikutaTheme(accentSeed = ...)` overrides the primary
  family (D-053). Background/surface ramp stays fixed.
- **Custom theme (D-254):** `AnikutaTheme(customTheme = ...)` builds the whole
  scheme from the user's per-element picks — see §2.6.

---

## 5. Typography

- **Body/UI font:** Roboto (bundled — 4 TTF files: regular, medium, bold,
  black). Fixes bold-text rendering on all devices. `RobotoFamily` in
  `:core:designsystem`.
- **Display font:** **Bricolage Grotesque** (bundled variable TTF
  `BricolageGrotesque[opsz,wdth,wght].ttf`, ROUND 152) — `DisplayFamily`/
  `BricolageFamily` used for the Browse hero title lockup + browse section
  heads.
- **Titles:** ExtraBold (800). Section labels: 14sp ExtraBold primary color.
- **Body:** Medium (500). 14-15sp.
- **Captions:** 11-12sp. onSurfaceVariant color.

---

## 6. Motion

- **Duration:** Standard 300ms, Short 150ms, Long 400ms (theme cross-fade),
  Container 450ms (the Details nav crossfade — D-324), SharedFlight 600ms
  (the shared-element cover's own bounds morph — D-327).
- **Easing:** `FastOutSlowInEasing` for standard transitions; the M3
  `EasingEmphasized` cubic (0.2, 0, 0, 1) for the Details crossfade AND the
  shared-element morph. The two durations are deliberately DECOUPLED
  (D-327): the page crossfade settles at 450ms while the cover keeps gliding
  to 600ms — "the details page can open up early but the image will move
  slowly" (user spec). What MUST stay in sync is the easing CURVE — the
  crossfade and the morph may run different durations on the same curve
  safely (only one thing is still moving once the page settles), but
  mismatched curves at equal duration read as jitter on device (the
  v0.2.61 lesson).
- **Shared-element keys are NAMESPACED PER SCREEN** (D-328):
  `cover:library:<url>`, `cover:search:<url>`, `cover:browse:<section>:<url>`
  — built ONLY through the canonical builders in `SharedTransitionLocals.kt`.
  During a screen switch both screens compose simultaneously, and ANY two
  matching keys morph — even across an instant snap switch. Library and
  Search once shared the `cover:<url>` format, so an anime present on both
  pages had its cover fly between them on every Library ⇄ Search switch
  (device-reported ghost morph). Details never constructs a key — it carries
  the source card's key through `AnimeDetailsKey.transitionKey`, so only
  list ⇄ Details (and its reverse) can ever match.
- **Shared-element morphs keep their shape:** while a cover morphs in the
  shared-transition overlay it is clipped to its rounded-12dp shape for the
  whole flight (the overlay's default parent-rectangle clip made rounded
  cards fly square and snap back to rounded on landing — D-324).
- **60fps target:** No heavy work on main thread during animation. Use
  `graphicsLayer` for scale/alpha (avoids recomposition).
- **No instant cuts:** State changes (expand/collapse, appear/disappear)
  always animate — never pop in/out.

---

## 7. Update Process

1. User requests a UI change.
2. Update this doc (add/modify a rule).
3. Implement in code (`:core:designsystem` or the feature module).
4. Verify visually.
5. Commit doc + code in the same commit (CORE_RULES §22).

---

## 8. Deep-Dive Documentation

The rules above are the **quick reference**. For implementation details, code
snippets, and screen-by-screen breakdowns, see the **Design System** folder:

```
APP/ani-kuta/DOCUMENTATION/DESIGN-SYSTEM/
├── README.md                           — Index + how to use
├── 01-navigation-and-sheets.md         — Bottom nav, collapsing header, scroll blur, bottom sheets, filter sheet, hide-on-scroll
├── 02-lists-cards-pills.md             — Pill/tag system, episode rows, download rows, library cards, downloads page
├── 03-settings-extensions-profile.md   — More page, profile, appearance, extensions, extension details, download settings, update sheet, bottom-up menu pattern
└── 04-player-details-search-library.md — Player (portrait+fullscreen), subtitle settings, details top, search, library
```

**22+ design patterns** documented across **18+ screens** with verbatim code
snippets, file paths, and design decision tables. Created in D-216.

---

*This document is the source of truth for the app's UI. If code disagrees with
this doc, fix the code (or update the doc if the change is intentional).*
