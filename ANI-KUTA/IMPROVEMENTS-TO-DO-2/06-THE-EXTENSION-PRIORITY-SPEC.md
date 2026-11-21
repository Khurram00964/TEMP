# 06 — The EXTENSION PRIORITY Spec (the copy — the spec survives the revert)

> The spec was written (round 165, the user's document-first order) and the
> FOUNDATION was implemented (the schema v7 + the tracker + the toggle +
> the CS provider sort + the SEARCH records) — all reverted with the code.
> The source doc (DOCUMENTATION/EXTENSION-PRIORITY-SYSTEM.md) is also
> restored as docs after the reset; this copy is the folder's guarantee.
> The redo implements the REMAINING integration against this spec, AFTER
> the home/search are right (the README rule 7).

---

# The Extension Priority System — Design & Implementation Spec

> ROUND 165. The user's order: "a full-fledged priority and sorting system" —
> the user's usage patterns determine the ORDER of extensions, sources, and
> servers across the whole app, learned automatically, persisted, backed up,
> and toggleable. This document is the SPEC — the implementation is reviewed
> against it.

## 1. What gets tracked

One **usage event stream** per provider/server, three tiers:

| Tier | Key shape | Events that score it |
|---|---|---|
| Provider | `cs:<providerName>` / `aniyomi:<sourceId>` | SEARCH (a search ran on it), OPEN (a details page opened through it), WATCH (a playback STARTED through it), FAIL (a resolve/play error attributed to it) |
| Server | `<providerKey>::<serverName>` | RESOLVED (a server resolved successfully), WATCHED (the playback used it to the end / ≥ the auto-mark threshold), FAIL (its resolve or playback failed) |

Weights (the starting algorithm, tunable):

```
score = searches*1 + opens*2 + watches*5 + resolves*2 + serverWatches*5 - fails*3
```

- Fails subtract (the user's "if one server fails... it will get a negative
  rating") — the score floors at 0 (a provider can't go negative; the
  alphabetical tail is the natural bottom).
- Every write LOGS itself (CORE_RULES §5/D-785) at DEBUG.

## 2. Where it lives

- **DB table `extension_usage`** (schema v6 → v7, `6.sqm`): `key TEXT PK`,
  `tier TEXT ('provider'|'server')`, `searches/opens/watches/resolves/fails
  INTEGER NOT NULL DEFAULT 0`, `score INTEGER NOT NULL DEFAULT 0` (the
  denormalized total — the sort reads the column), `last_used_at INTEGER NOT
  NULL DEFAULT 0`, `updated_at INTEGER NOT NULL DEFAULT 0`. Backup parity:
  added to BACKUP_TABLES (the user: "properly saved... properly backed up").
- **The toggle**: `ExtensionPriorityPreferences.enabled` (default ON) — the
  Settings → Extensions page's row. OFF = every sort site falls back to the
  historical order (the trackers still record; the order ignores them).

## 3. The integration points (each reads the SAME tracker)

1. **The source pickers** (the search screen's ExtensionSourcePickerSheet):
   the CS section sorts by the provider score desc; the never-used providers
   follow alphabetically. The aniyomi section: the same. The remembered
   selection still renders its checkmark wherever it sits.
2. **The linking search** (AniList → the extension match: the manual search's
   source order + the auto-link's source iteration): the same score order —
   "whichever one the user prefers to use the most... will be used to search
   for it first."
3. **The resolver** (the episode resolve → the server list): the servers sort
   by their SERVER-tier score desc (the user's "the third-in-line server
   rises to the top"), and the auto-select prefers it ("it will be by default
   selected" — the existing expansion + the auto-select read the same order).
4. **The fail attribution**: a resolve/play failure increments the FAIL of
   the server that produced the link (and the provider's fail — the negative
   pressure). A successful watch increments the WATCH tier.
5. **The data-source hand-off** (the movies/series metadata tap): the
   provider pick (the remembered/first) becomes the HIGHEST-SCORED trusted CS
   provider.

## 4. The Settings surface

- Extensions settings gains the "Usage priority" card: the toggle + the
  "reset learned priorities" action (the table wipe) + the top-3 list (a
  read-only glance).

## 5. The rules

- The tracker NEVER blocks the UI path: every record is fire-and-forget on IO.
- A disabled toggle stops the SORT influence but not the recording (the
  history survives the toggling).
- The table is bounded: the provider/server rows are naturally bounded by the
  installed extensions — no prune needed (documented in the contract §7).
- Backup/restore: the table rides BACKUP_TABLES; the merge-restore semantics
  (INSERT OR IGNORE by PK + the score columns take the MAX of the two) — the
  restored scores merge, never regress.

---

## The implementation STATUS at the revert (what was done vs. left)

**DONE (the round-165 foundation — the recovery branch `release/1.1.103`):**
- The schema v6→v7 (`6.sqm`): the extension_usage table + the score index.
- ExtensionUsageTracker (:core:database, dependency-free): the transactional
  read-modify-write (NO UPSERT — below SQLite 3.24, the project constraint),
  the events SEARCH/OPEN/WATCH/RESOLVE/FAIL with the weights 1/2/5/2/-3
  (floor 0), the `nowMs` param (no System — the purity rule), the
  score-ordered reads.
- ExtensionPriorityPreferences (:core:preferences): the toggle (ON default;
  the recording continues while OFF).
- The search VM: the SEARCH records (the CS + the aniyomi) + the
  priority-aware CS provider sort (the score desc, the never-used
  alphabetical).

**LEFT (the deep integration — the redo):**
1. The resolver's server order + the auto-select reading the SERVER tier.
2. The fail attribution (the resolve/play failures → the FAIL records) +
   the WATCH records at the playback start.
3. The OPEN records at the details open.
4. The Settings → Extensions "Usage priority" card (the toggle + the reset
   + the top-3 glance).
5. The aniyomi-source sort (the picker's aniyomi section).
6. The backup parity (BACKUP_TABLES + the merge-restore semantics).
7. The data-source hand-off's provider pick = the highest-scored trusted CS
   provider.
