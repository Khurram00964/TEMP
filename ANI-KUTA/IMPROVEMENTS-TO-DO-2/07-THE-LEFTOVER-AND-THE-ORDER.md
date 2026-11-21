# 07 — The Leftover List + THE REDO ORDER

> Everything that remains, in the implementation order. The redo starts
> from the post-round-162 base (main = `49983800`) and implements the
> specs in 03/04/05/06 against it — the post-mortems' fix patterns (01)
> are MANDATORY reading per item.

## The redo order (each item = design first, then implement, then the device round)

1. **THE UPDATE-OVER HANG (PM-6) — the #1 blocker.** Diagnose on the next
   repro (the logcat; the ErrorActivity net catches crashes — a splash
   hang is an ANR or a pre-handler death). Consider: the DB open fully off
   main, the migration timeout, the Koin-init laziness. NO schema-carrying
   release ships before this is understood.
2. **The AIRS contract (03-H-1)** — the 3-row collapsed (the fill rule
   guarantees 3), the WORKING expand (the UI-layer state — PM-1's
   pattern), the forgiving released pool (PM-2's pattern), the frosted
   pill stays.
3. **The SEE-ALL real catalogs (03-H-5)** — the kind-aware catalog pages,
   DESIGNED first (the user's design-first order), the search-page routing
   BANNED.
4. **The SUGGESTED field (03-H-3)** — the pools-only derivation, the
   kinds-flow re-derivation (PM-4's pattern).
5. **The MOOD/GENRE + the depth (03-H-4, H-6, H-7)** — the per-kind pools
   (the lazy genre map — PM-5's pattern), the combined all-genres, the
   deep pools (the trending/popular/top-rated × 3 calls per kind).
6. **The SEARCH page (04)** — the ONE shared base (the collapse + the
   pagination branches — the round-165 lessons), the LOGOS picker, the
   hand-off, the expanded customizability.
7. **The TRACKING (05)** — the MAL FULL module (the OAuth-plain, the
   link flow, the >1-linked picker, the Stop-tracking rename) + the Simkl
   rebuild from the recovery branch.
8. **The EXTENSION PRIORITY deep integration (06's leftover list)** — the
   resolver's server order, the fail attribution, the WATCH/OPEN records,
   the Settings card, the backup parity, the hand-off provider pick.
9. **The TVmaze airs + Bangumi** (the airs-by-date rows for the series
   kind; the donghua detail source) — the round-165 leftovers.
10. **The release** — Routine A at the end of every multi-step round
    (the automatic cadence), the version floor below.

## The version floor (CRITICAL — the installed-device constraint)

- The devices run **v1.1.103 / versionCode 10203**. Every future install
  (update-over) MUST have versionCode **≥ 10204** — a lower bump fails the
  install.
- The debug line continues: **v1.1.104 / 10204** is the NEXT debug
  release; the mainline constants ride the release branch only (the
  round-162 discipline).
- The mainline (post-revert) carries **10120 / 1.1.20** — the bump happens
  on the release branch at the release time, never on main.

## The recovery pointers (where the reverted work lives — NEVER re-derive from scratch)

| The work | The recovery |
|---|---|
| The rounds 163–165's full code (the DB v6, the Simkl, the TMDB/TVmaze modules, the picker, the home modes, the priority foundation) | TEMP branches `release/1.1.101`, `release/1.1.102`, `release/1.1.103` + the tags `v1.1.101/102/103` |
| The last-good Simkl shape (the login-state restore included) | `release/1.1.103` |
| The tracker keys (the values) | `08-THE-KEYS.md` (also TrackerKeys.kt on the recovery branches) |
| The GitHub PATs + the keystores | the LOCAL vault `C:\Users\khurr\Desktop\ANI-KUTA\credentials\` (08 maps it) |
| The round records 141–146 | DOCUMENTATION/cloudstream-v2/ (restored as docs after the reset) |
| The priority spec | 06 (the copy) + the restored DOCUMENTATION/EXTENSION-PRIORITY-SYSTEM.md |

## The standing rules (unchanged by the revert)

- **CI-only builds** (GitHub Actions; NEVER local Gradle/SDK/JDK).
- TEMP = the live CI surface (the push + the workflow dispatch).
- The push credential override (the empty first helper):
  `git -c credential.helper= -c credential.helper='!f() { cat ".../temp-repo-credentials"; }; f' push temp <ref>`
- The script-driven edits (python -I in planning/, marker-asserted); the
  single-quote char literal slip (3× hit) and the JVM setter clash are the
  known traps.
- The :core:database purity (no Logger/System — the nowMs param).
- The sub-agent review for the risky work; the report format locked
  (CORE_RULES §3.1); the ntfy notifications (ANIKUTAPROGRESS +
  THE-TASK-IS-DONE).
- No changes beyond the order; the releases/version bumps = the explicit
  order (Routine A's automatic debug release is the granted exception).
