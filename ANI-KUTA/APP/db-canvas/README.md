# ANI-KUTA — Database Study Sheet (db-canvas)

An interactive ERD canvas of the ANI-KUTA SQLDelight database, plus annotated
notes: the census, the migration ladder, the resolved flaw register and the open
findings. Styled as a "handwritten study sheet" (pastel highlighters, graph
paper, Fredoka / Patrick Hand / JetBrains Mono).

Standalone: this folder is NOT part of the Android repo and nothing in it ships
with the app.

## Run it

```bash
npm install        # already done
npm run dev        # http://localhost:3210  (dev, hot reload)
# or
npm run build && npm start   # production build + serve on :3210
```

## The grouping rule

Every table lives inside its category's **tinted panel** — one colour + one
place per category, arranged as the real hierarchy:

```
        LOOKUPS  (data_source · system)
           |
        SPINE  (main_entry · content_details · content_identity)
           |
        LEVEL 2  genre · library · watch · trackers · caches   (direct main_id dependents)
           |
        LEVEL 3  downloads · updates · notifications · ratings (their dependents)
           |
        LEVEL 4  playback cache + STANDALONE (grey dashed: the 3 tables with NO link at all)
```

Deep spokes climb the interior gutters between panels (visible lanes); the
outer margins are only used by the level-4 pair. Same panel as a table's link
target ⇒ same background, same place.

## What's in it

| Path | What |
|---|---|
| `app/page.tsx` | the sheet: title block, the canvas, the census, the migration ladder, health check, issues board, footer |
| `app/globals.css` | the whole design system (paper + graph grid, pills, tables, sticky notes, canvas chrome) |
| `src/data/tables.ts` | **all 27 tables** — columns copied verbatim from the `.sq` files, PK/FK/UNIQUE/NOT NULL/DEFAULT flags, layout coordinates, and the demo dataset |
| `src/data/edges.ts` | the 25 relationships: 15 enforced FKs, 8 doctrine-refs (no FK), 2 seed edges — each with a hand-verified routing channel |
| `src/data/meta.ts` | migrations v1→v7, the resolved register (B1–B7, R1–R6), the open issues (S1–S8), the strengths |
| `src/lib/geometry.ts` | the orthogonal edge router (hub fan-out, staging exits, arrowheads) + band badges |
| `src/components/CanvasSurface.tsx` | pan / zoom / spotlight engine, legend, controls |
| `src/components/TableCard.tsx` | the table card: header, columns, demo mini-table, note |

## UI modes · views · chrome

- **Three modes**: Light (the authored pastel sheet) / Dark / Gray — switcher in the
  header, persisted in `localStorage`. Families are authored once in light and their
  dark/gray triplets are derived in `src/data/colors.ts` (rgba-aware).
- **Collapsible header**: the chevron collapses the title block into a slim sticky
  bar and the canvas grows to the full viewport height.
- **Two views**: `Canvas` and `Documentation` (the census, migration ladder, health
  check and issues board live in their own tab, not stacked under the canvas).

## Cell-level linking

Every schema cell carries `data-cell="table.col"`. Hover a cell and — via delegated
DOM listeners (no React re-render) — the cell, the wire through it and the PEER cell
at the other end highlight, so an FK cell visually connects to the exact PK cell it
references. Each wire also leaves from a **port dot** on its source cell, and FK
cells carry `FOREIGN KEY → table.col` tooltips. The colour of a wire = its source
panel; a spotlighted table's wires turn crimson.

## Demo data

Always visible at every zoom (the LOD hiding is gone). Card width accounts for the
demo block too: each demo column is sized from its longest header AND longest value
(`demoColWidths()`), so demo content does not truncate either. The lock check
covers `.col-name`, `.col-type`, `.dhead` AND `.dval`.

## Arrows

Two channels: the **line style** = link kind (solid FK · dashed FK-free doctrine ·
dotted 6.sqm seed), the **colour** = the source table's panel (so you can read at a
glance which category a wire comes from). Spotlighted arrows turn crimson. Every
spoke runs through panel gutters and shared riser lanes; nothing crosses a card.

## Card widths

Widths are **computed from the text**, not guessed: the longest column heading in
each strip (mono 11px/700 advance 6.62px) + its type + room for two PK/FK/U/DEF
chips, and the demo header row. `requiredWidth()` in `src/data/tables.ts` owns
this and the `WIDTHS` map is only a floor. The lock check: zoom in and no
`.col-name`, `.col-type` or `.dhead` may have `scrollWidth > clientWidth` — all
290 column names + 290 types + 121 demo headers fit.

## Canvas controls

- **scroll wheel** — zoom around the cursor (8%–250%)
- **left-click drag** — pan
- **click a table** — spotlight it (others dim, its arrows turn crimson); `Esc` clears
- **fit** — the whole sheet; **1:1** — 100%; **−/+** — step zoom

Demo rows auto-hide below 50% zoom (LOD) to keep the sheet light; column types
and badges collapse below 28%.

## Geometry contract (important)

Card geometry is load-bearing: `header 32px · column row 21px · demo pad 8/10 ·
demo label 18px · demo value row 20px · note ≈40px · panel pad 14 · panel header
32 · card gap 26`. And `estimateTableHeight()` / `columnsBlockHeight()` in
`src/data/tables.ts` must stay in sync with `globals.css` — panels, cards and
every edge anchor are computed from them. Wide tables (>16 columns) render
their schema in two balanced columns, and the router's `colAnchor()` accounts
for that split.

## Source of truth

`core/database/src/main/sqldelight/com/confused/anikuta/core/database/*.sq`
(schema v7, 16 non-empty files, 27 tables) and the `1.sqm`–`6.sqm` migration
chain. Demo data is illustrative but state-complete: every enum/state axis the
schema models appears at least once.
