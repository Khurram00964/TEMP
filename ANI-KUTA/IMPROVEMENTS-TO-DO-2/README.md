# IMPROVEMENTS-TO-DO 2 — the redo folder (2026-11-21)

> **What this folder is**: the user's order after the v1.1.103 device round
> failed ("nothing was done properly at all... revert back from where we
> started"): create a FOLDER (the IMPROVEMENTS-TO-DO.md precedent, expanded)
> carrying EVERYTHING — the state before the revert, the device report +
> post-mortems, the requirements (the home page, the search page, the
> tracking, the priority system), all the keys, and the leftover list — then
> revert main to the post-round-162 state and continue from there.

## The revert record

- **The revert target**: main = `49983800` (the exact post-round-162 state —
  the v1.1.102-era code is OFF main; the release branches/tags stay on TEMP
  as the recovery).
- **What was reverted OFF main** (the rounds 163–165 code): the Step-0 airs
  changes, the DB v6 (the extension_usage table + the rebuilds + the prunes),
  Simkl, the TMDB/TVmaze modules, the search picker + the data sources, the
  home multi-select modes, the extension-priority foundation. The round
  records 141–146 are PRESERVED (docs only, copied back after the reset).
- **What stays**: everything up to and including the v1.1.100-era code
  (the post-v1.1.8 clean base + the round-162 doc/agent state).

## The recovery map (where the reverted work lives)

| The work | The branch / tag on TEMP |
|---|---|
| Round 163 (Steps 0–3c: the airs fix + the DB v6 + Simkl + the data sources + the picker) + v1.1.101 | `release/1.1.101` / tag `v1.1.101` |
| Round 164 (Step 3d: the home modes + the bold Settings) + v1.1.102 | `release/1.1.102` / tag `v1.1.102` |
| Round 165 (the device-fix round: the crashes + the search parity + the home depth + the priority foundation) + v1.1.103 | `release/1.1.103` / tag `v1.1.103` |

## The folder's contents

| File | What |
|---|---|
| `01-THE-STATE-BEFORE-THE-REVERT.md` | The rounds 163–165's implemented work + the post-mortems (what failed and WHY — the honest technical record) |
| `02-THE-DEVICE-REPORT-V1.1.103.md` | The user's v1.1.103 report, itemized + post-mortemed |
| `03-THE-HOME-PAGE-SPEC.md` | The HOME PAGE requirements as discussed (the airs contract, the modes, every section's behavior) |
| `04-THE-SEARCH-PAGE-SPEC.md` | The SEARCH PAGE requirements (the shared base, the title styles, the picker, the hand-off) |
| `05-THE-TRACKING-SPEC.md` | The tracking decisions (the AniList+MAL anime / Simkl movies+TV) + the FULL MAL plan |
| `06-THE-EXTENSION-PRIORITY-SPEC.md` | The full priority-system spec (the copy — the system was reverted with the code) |
| `07-THE-LEFTOVER-AND-THE-ORDER.md` | The remaining items + the implementation order for the redo |
| `08-THE-KEYS.md` | ALL the credentials/keys (the MAL, the Trakt, the TMDB, the Simkl, the redirects) |
| `09-THE-PLAN.md` | The round-167 investigation: the PM-6 code evidence, **the DB-v7 DOWNGRADE BLOCKER** (item 0 — no release from main before the migration re-land), the PM-6 hardening plan, the normalization plan (the 5 giants), the consolidated redo queue |

## The rules for the redo (the user's standing orders, restated)

1. The AIRS collapsed = **3 rows** (2 released + THE next-to-air); the
   expand = the full window with the glides. The Show more/less must WORK
   (the expand state must drive the recomposition — the round-165 bug: the
   flag changed nothing because the rows' composition lived in the data
   pipeline, not the UI state).
2. The SEE-ALL must NOT open the search page — the user rejected that
   routing outright. The kind catalogs need real pages.
3. The SUGGESTED FIELD must follow the enabled kinds (no stale anime covers,
   no DB-cached anime covers surviving the kind switch).
4. The MOOD/GENRE by-kind pools must work for movies/series (the empty-map
   class bugs are root-caused in 01/02 — the fix pattern is documented).
5. The SEARCH PAGE = ONE shared base (the collapse, the recents, the
   pagination, the title styles, the rating/details toggles — for EVERY
   source).
6. The TRACKING: anime = AniList + MAL (the full MAL module); movies+TV =
   Simkl. The >1-linked tracker picker in the 3-dot menu; "Stop tracking".
7. The EXTENSION PRIORITY system: the full spec in 06 — implemented AFTER
   the home/search are right.
8. The UPDATE-OVER HANG: the black-screen-on-update must be diagnosed
   (the logcat on the next repro) BEFORE the next multi-migration release.
