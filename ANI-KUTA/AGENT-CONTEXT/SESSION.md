# SESSION — Read This At The Start Of Every Session

> A 60-second orientation. Read this FIRST, every time, before any work.
> Refreshed in **Round 132 (2026-10-31).** If `memory/progress.md` shows a NEWER round than this file's stamps, trust progress.md — and refresh this file at the phase boundary (the Round-77 lesson).

---

## ⚡ Who You Are
You are the AI agent for **ANI-KUTA** — an Android multi-content streaming/downloading app (anime + movies + series today, manga and novels planned; Kotlin 2.2 + Jetpack Compose 1.10.4 + MPV + SQLDelight 2.0.2 + Koin 4.2.2 + Injekt + a CloudStream plugin system) — plus its companion web dashboard (Next.js → GitHub Pages).

**GitHub repo:** `testplay-byte/ANI-KUTA`. Repo root = the single wrapper folder `ANI-KUTA/` (per CORE_RULES §4) + `.github/` at root (a GitHub platform constraint) + the user's own `README.md` / `NEW_AGENT_SETUP.md` / `USER-UPLOADS/` (established).

---

## 📍 Current State (refreshed Round 162, 2026-11-21 — the orientation + the drift fixes + the normalization start; doc 141, D-921..D-923)

- **THE PHASE:** the SECOND redo phase (the round-166 revert). Main = the post-round-162 state (`49983800`) — the rounds 163-165's code (the DB v6, Simkl, the TMDB/TVmaze modules, the picker, the home modes, the priority foundation) is OFF main; it lives on release/1.1.101/102/103 + the tags on TEMP (the recovery). `ANI-KUTA/IMPROVEMENTS-TO-DO-2/` (README + 01-08) is the SINGLE record: the post-mortems (the airs expand-in-pipeline bug, the 24h-bound empty pool, the banned see-all→search routing, the stale field cache, the empty genre map, the undiagnosed update-over hang) + the home/search/tracking/priority specs + the keys + the redo order (the hang diagnosed FIRST; the airs 3-row contract; the real catalogs; the MAL module). THE DEBUG LINE: **v1.1.103/10203 LIVE on TEMP** (installed on the devices) — the next debug release continues **1.1.104/10204**; the ≥10204 floor is HARD (the update-over installs fail below it); the mainline constants stay 10120/1.1.20 (the bumps ride the release branches).
- **Mainline:** `main` @ round 162 (commits 1cfd14d1 + 3dd274d4 + 2528c820). The mainline carries 1.1.20/10120 (D-430: bumps ride release branches only).
- **THE LIVE LINE = TEMP:** testplay-byte is UNREACHABLE (the flagged account degraded — repo/API/Pages all 404 publicly AND the dev PAT is DEAD, 401, verified 2026-11-21). Pushes go to the `temp` remote (Khurram00964/TEMP, public, its PAT VALID); TEMP main = local main. **TEMP CI is DISPATCH-DRIVEN** (pushes do NOT fire workflows): after every push, POST /repos/Khurram00964/TEMP/actions/workflows/build-apk.yml/dispatches {"ref":"main"} with the temp PAT (expect 204). The official repo (Confused-Creature-180/ANI-KUTA) + its PAT are healthy (professional releases + the site).
- **Round 162 (doc 141):** the round-2 verification (the two-player truth + 11 stale DESIGN-LANGUAGE.md claims found and fixed), the knowledge/docs drift sweep (module-map/architecture/ui-customization re-verified: 59 modules, 26 tables, 3 .sqm, Koin 33, 52 branches), CORE_RULES §3.1 (the user-confirmed response format), the four specialized agents (.zcode/agents), the signing-keys organization (credentials/keystores + NOTES.md), the code-quality batch (the light error/onError pair + Surface4Light, the WatchLaunchArgs/CsEpisodeLaunchArgs named-arg bundles over the 17/11/9/12-param callbacks, the dead onPiPClick removed, the orphan build residue deleted), and the DetailsViewModel normalization start (4,965 → 4,383 — the state types + the D-242 tracking cluster as verbatim extension-function moves). CI: 44806908899 GREEN + 44808332206 RED (the swept-declarations miss — the lesson banked) + 44809252465 GREEN (the fix).
- **The normalization pattern (D-923):** top-level types → whole files; method clusters → same-package extension functions with private→internal widening; initialized-once FIELDS (StateFlows, stateIn) + init blocks STAY IN CLASS (an extension property re-instantiates). One cluster per CI cycle. The 5 hotspots: DetailsViewModel 4,383 (was 4,965), LibraryScreen 4,607, WatchScreen 4,480, DetailsScreen 4,172, MainActivity ~3,360.
- **Latest records:** Round 162 = doc **141**; the next record is **142**; the next decision is **D-924**. The decisions.md file carries D-869..D-879 at top (the D-904..D-920 records live in docs 136-140 only — the pre-existing split, disclosed).
- **The per-round loop (D-738 form):** device feedback / the user's order → implement on the mainline → push to TEMP + DISPATCH CI → CI green → **STOP — hand the user the report + the test checklist** → ledger + ntfy. Releases of either kind need the user's explicit order (debug releases ride TEMP's release-apk.yml dispatch; professional releases ride release-build-once.yml + the official-repo re-host per doc 122).
- **CI paths-ignore (D-472):** docs-only / AGENT-CONTEXT / DASHBOARD / USER-UPLOADS / `.github/**` / `**.md` pushes build NOTHING (on testplay-byte; on TEMP nothing fires without dispatch anyway).
- **THIS SESSION'S NOTIFICATION:** ntfy topics **THE-TASK-IS-DONE** (the §11 default) + **ANIKUTAPROGRESS** (the user's round-162 order — progress updates ride this topic too).

## 📂 If The Environment Was Just Cloned
1. The repo lives locally at `C:\Users\khurr\Desktop\ANI-KUTA\project\ANI-KUTA` (clone of testplay-byte/ANI-KUTA — currently UNREACHABLE online). **PUSH to TEMP** uses the one-shot credential helper reading `C:/Users/khurr/Desktop/ANI-KUTA/credentials/temp-repo-credentials` (git-credential FORMAT — for raw API calls extract the `password=` line; NEVER commit it, NEVER paste it). The dev PAT (`credentials/github-credentials`) is DEAD (401, 2026-11-21); the official-repo PAT (`credentials/official-repo-credentials`) is VALID. ALL signing material + notes: `credentials/keystores/` (NOTES.md is the master).
2. Checkout the mainline `main` (the default branch — renamed from `feature/round-57-cloudstream-downloads` in Round 118, D-743).
3. Read `AGENT-CONTEXT/memory/progress.md` — the TOP **CURRENT STATUS** block first, then the newest `## Round NN` sections at the BOTTOM (the file grows downward; the middle "Historical session" paragraphs are old).
4. Read `AGENT-CONTEXT/memory/decisions.md` — the newest entries sit at the TOP; latest = **D-819** (round 134, the v1.1.78 release; D-816..D-818 beside it — the .anikuta format/merge-restore, the automatic backup, the player polish).
5. Read `AGENT-CONTEXT/HANDOFF-ROUND-116.md` — the round-116 handoff (+ the round-117 addendum; historical context now) + doc 99 §7 (the round-117 re-host record). The latest round record: doc **102** (round 120 — the player overhaul + the downloads reliability set).
6. Read `AGENT-CONTEXT/knowledge/` files on demand (architecture, module-map, tech-stack, ui-customization, emulator-testing…).

---

## 🔑 Key Rules (full detail in `CORE_RULES.md` — 31 sections; that file WINS)
- **No assumptions.** Unsure → ask the user. Never guess.
- **Don't sugarcoat.** If a request has an issue, flag it directly. Never blindly agree.
- **User uses speech-to-text.** Correct obvious transcription errors from context; if still unclear → stop and ask.
- **APK builds: GitHub Actions ONLY.** Never build locally; never install the Android SDK/JDK/Gradle locally; never run Gradle (§8, D-281 — CI is the compiler of record: push → poll the run → read the logs → fix → repeat).
- **Push path = DEBUG, arm64-v8a only** (D-445). **Shipped releases = ALL ABIs + universal, release-signed in CI** (D-423) — but under **D-738 nothing ships without the user's explicit order**: rounds end at the CI debug artifact.
- **The version NEVER moves without the user's explicit order** (D-425). Bumps ride the release branch only (D-430).
- **No R8/minification on the release line** (D-436).
- **The update repo follows the build type** (D-440): debug builds check `testplay-byte/ANI-KUTA` releases; release builds check `Confused-Creature-180/ANI-KUTA` (the official repo — LIVE since the repo-external round 79: professional v1.1.3 is published there with the APK+ZIP set and the Pages site).
- **Merges / main-branch operations are USER-GATED.** The user's explicit instruction is required (and note `main` no longer exists — "merge to main" orders need interpretation at the moment they are given).
- **No "sponsor"/"sponsored" words anywhere** — code, UI, copy, docs (the user's standing order).
- **Debug builds = schema freedom** (§30): no migrations needed; stale dev DBs get wiped. Proper migrations return only when the user signals production.
- **Sub-agents for webpage work** work ONLY in `DASHBOARD/webpage/`, never `AGENT-CONTEXT/` (§14, §19). The main agent owns all AGENT-CONTEXT updates.
- **Task notification:** ntfy.sh topic **`THE-TASK-IS-DONE`** (§11).
- **Quality over speed.** Don't rush. Don't skip steps (§18). Frozen/accepted surfaces stay byte-identical unless the user orders a change.

---

## 🔄 The Task Loop (full detail in `workflow.md`)
```
REFLECT → RESEARCH → PLAN → TODO LIST → EXECUTE → COMMIT → VERIFY (CI) → DOC UPDATE → NOTIFY
```
1. **Reflect** — summarize what you understood before executing.
2. **Research** — read the code/docs the task touches. Look before you write.
3. **Plan** — split into phases; build the todo list AFTER research, not before.
4. **Execute** — one phase at a time.
5. **Commit** — Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:`), each phase separately, with the **why** in the body.
6. **Verify (CI)** — push → poll the GitHub Actions API → read failures → fix → repeat until green. Do NOT dispatch sub-agents to pre-review for compile errors (D-281).
7. **Doc update** — progress.md + decisions.md + changelog.md + lessons-learned.md + the round's `download-research/NN` record — in the SAME session (§26; no "later").
8. **Notify** — ntfy after each phase + at the end.

## 📝 After Every Task (Update These)
- `memory/progress.md` — the top CURRENT STATUS block + a new `## Round NN` section at the BOTTOM.
- `memory/decisions.md` — a new D-NNN PREPENDS at the top (right after `## Decisions`).
- `memory/changelog.md` — append the round's section at the bottom (append-only narrative).
- `memory/lessons-learned.md` — any new lesson (dedup-check first).
- `AGENT-CONTEXT/download-research/NN-ROUND-<slug>.md` — the round record; numbering strictly sequential.
- Dashboard data (`DASHBOARD/webpage/lib/`) — delegate to a full-stack-dev sub-agent (§19) when the project's status facts changed; disclose any debt rather than silently skipping. (Standing round-89 instruction: the dashboard is left as-is until the user calls the focus.)

## 🚨 Session-End Checklist (NON-NEGOTIABLE)
- [ ] All work committed (`git add -A && git commit`).
- [ ] Pushed to GitHub (`git push`). **The environment can clear randomly — unpushed work is lost.**
- [ ] `git status` is clean.
- [ ] CI green — verified via the API, never assumed (lesson D-156).
- [ ] ntfy notification sent (topic `THE-TASK-IS-DONE`).
- [ ] Short formatted summary + test checklist given to the user (§3 + §31).

---

## 🚧 Open Items / Blocked (refreshed Round 120)
- **THE PHASE:** D-738 — debug builds only by default; ALL releases need the user's explicit order. **The user's device round on the ROUND-120 build (the player overhaul + the downloads reliability set) is the next input** — the checklist is the workspace `planning/2026-10-23-round-120-device-test.html` (+ its .md twin); the artifact is the green Build APK run's `anikuta-apk` (versionCode 10120 — the standing in-place-update wrinkle over v1.1.73: `adb install -d` or uninstall). OUT OF SCOPE BY ORDER (round 120): details/library pages, the database, the auto-download engine + the notification rework it depends on.
- **THE BRANCH NOTE:** the backup branch (`backup/main-pre-revert-2026-10-21`) is IGNORED by standing order — never re-analyze, never merge from it; the user deletes it. `release/1.1.73` stays pushed (the branch-point record).
- **THE HANDOFF:** `AGENT-CONTEXT/HANDOFF-ROUND-116.md` — the round-116 delta handoff (+ the round-117 addendum; HISTORICAL context now). `HANDOFF-ROUND-115.md` (the full original) remains valid context; `HANDOFF-POSTER-NOTIFICATIONS.md` is HISTORICAL (rounds 46-47; resolved by the round-80 notification rework D-566..D-569). This SESSION.md + doc 100 carry the round-118 state.
- **Standing code-level items (doc 96 §6 + the earlier rounds' NOT-APPLIED set, unchanged):** the "Hard Sub" spaced-label edge (consistent with the MPV parse); the token-matching semantics living in five places repo-wide (a future consolidation candidate — designsystem already depends on core:common); the badge dims with its watched row; the InFlight tap no-op during the resolve window; ExtensionInstaller's two OS-fallback toasts; the AppToast id-counter race; the dead AutoLinkPopup.kt cleanup candidate; a FUTURE Coil 3.1.x upgrade must ADD respectCacheHeaders(false) to list-icon requests (doc 81 §11.2); the D-557 momentum-handoff direction note (doc 87 §6).
- **THE DEBUG LINE IS NON-DEBUGGABLE now (D-674):** if any future work needs `adb run-as` or a debugger on the debug build, that path is GONE by design (the revert = the one gradle flag + the three annotated re-keys). The updater's dev-repo gate + the Logger's DEBUG level + the Developer-tools section are keyed on IS_DEBUG_LINE / the .debug suffix — NOT on BuildConfig.DEBUG / FLAG_DEBUGGABLE (both read false on the dev line now).
- **Credentials:** the workspace (the user's Windows machine, `C:\Users\khurr\Desktop\ANI-KUTA\`) holds the dev PAT repo-externally at `credentials/github-credentials` (git-credential format, account `testplay-byte`). Push auth uses the ONE-SHOT helper: `git -c credential.helper= -c credential.helper='!f() { cat "C:/Users/khurr/Desktop/ANI-KUTA/credentials/github-credentials"; }; f' push origin main` — the EMPTY first `-c credential.helper=` RESETS the helper list (a bare `-c` APPENDS, and the OS-stored Confused-Creature-180 credential then wins with a 403 — the round-120 lesson). The token never lands in `.git/config`, logs, or commits. The **official-repo (release) PAT is banked in the workspace** at `credentials/official-repo-credentials` (git-credential format, account Confused-Creature-180; admin+push — validated and used by the round-142 professional release).
- **The user's uploads folder:** `USER-UPLOADS/` contents may be cleared if cleanup is ever wanted, but the FOLDER itself stays (the user, round 89).
- **Dashboard deep debt (disclosed, D-565):** the dashboard's deep-history pages carry REPRESENTATIVE data (status facts refreshed at Round 115). (The user, round 89: leave the dashboard as-is for now — focus comes later.)
- **`feature/test-controller-v5`** stays dormant (kept by explicit user order — do not delete).
- **Version bookkeeping quirk (D-430):** the mainline says 1.1.20/10120 while the shipped debug line is at v1.1.71 — this is CORRECT by doctrine (bumps ride release branches). Don't "fix" it.

## 🧪 Testing on the Emulator
The sandbox CAN run the app on an Android emulator (user-authorized §8 exception) — but read
`knowledge/emulator-testing.md` BEFORE any emulator/adb work (double-fork detach, timeout-wrapped
adb, input-text limits, the 4GB memory ceiling). Note D-445: debug CI builds are arm64-v8a only —
an x86_64 emulator image cannot install the debug APKs anymore; device rounds are the real loop. (Round 99 made the debug line NON-DEBUGGABLE — the old prefs-injection `run-as` trick would not work either.)

## 📦 Project Folders
```
repo-root/
├── ANI-KUTA/                    ← wrapper folder (all zones inside)
│   ├── AGENT-CONTEXT/           # YOUR memory + rules (you maintain this)
│   │   ├── download-research/   # the early round records + download research (next: 39)
│   │   ├── memory/              # progress / decisions / changelog / lessons-learned
│   │   ├── knowledge/           # quick-reference summaries (read on demand)
│   │   ├── HANDOFF-ROUND-116.md # the round-116 handoff + the round-117 addendum (HISTORICAL)
│   │   ├── HANDOFF-ROUND-115.md # the round-115 full original (valid context)
│   │   └── HANDOFF-POSTER-NOTIFICATIONS.md  # HISTORICAL (rounds 46-47)
│   ├── APP/ani-kuta/            # Android app — 57 Gradle modules (1 app + 33 core + 2 data + 21 feature)
│   │   └── DOCUMENTATION/       # technical docs; cloudstream-v2/ = the round records (next: 99); release/ = the build+release guides
│   ├── DASHBOARD/webpage/       # Next.js dashboard (→ GitHub Pages; sub-agents build it)
│   └── REFERENCES/              # old-kuta + animiru + webview-cloudflare-captcha (read-only)
└── .github/workflows/           # CI — build-apk / release-apk / release-build-once / deploy-dashboard
```

---
*This file is the quick-start. For everything else, see `navigation.md`. History: the pre-Round-77 version of this file described the round-29 era (v0.4.17, `streaming/CLOUDSTREAM-V2`) — it was fully rewritten in Round 77 after the drift was caught; refreshed rounds 115-116.*
