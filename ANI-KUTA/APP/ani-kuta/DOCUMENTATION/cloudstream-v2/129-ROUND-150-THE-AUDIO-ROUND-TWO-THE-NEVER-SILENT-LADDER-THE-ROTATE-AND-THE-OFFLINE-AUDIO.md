# Record 129 — Round 150: the audio round two — the never-silent ladder, the rotate, and the offline audio

> The user's device round on the v1.1.90 TEMP build: the install-over worked (the
> signing ✓), the AudioSheet's selection works, the fullscreen sheet is good — BUT the
> default was SILENT, the offline downloaded audio was broken ("no audio tracks found",
> glitched manual files, data.json unread), and they asked for the auto-select ladder,
> the pause-until-playing QoL, the versions-only sheet (the tracks as debug), and the
> ROTATE button (FREE/LOCK) + the portrait-fullscreen tolerance. NO release (the
> user's order).

## 1. The never-silent ladder (the report's centerpiece)

- **Root cause**: mpv's `audio-add <url> auto` sets `no_default=true` and NEVER selects
  (verified against mpv upstream's command.c flag mapping). A video without embedded
  audio started silent; the externals registered; the only app-side aid writer
  (`applyRememberedAudio`) early-returned when nothing was remembered. mpv's own
  auto-selection only fires for the main demuxer's streams, never for later-added
  external files.
- **The ladder** (in `applyRememberedAudio`, runs after EVERY track reload):
  1. A live, valid `currentAudioTrack` → respect it (the user's pick wins).
  2. The remembered per-series label (name OR lang, case-insensitive).
  3. The episode-variant match: `parseAudioAvailability` over the LIVE metadata
     scanlator + title — DUB → "eng"/"english", SUB/HSUB → "jpn"/"japanese".
  4. The FIRST track — never silent.
  The ladder never writes the per-series memory (only real taps persist).

## 2. The switch QoL + the sheet

- Picking an UNREGISTERED external version: pause first (the deliberate user pause is
  respected), `audio-add select`, then a watcher waits for the aid to land (the live
  observation) and unpauses; a 12s timeout unpauses anyway. Registered picks stay
  instant. Consecutive picks share one cycle (the was-playing snapshot).
- The sheet: **"Audio versions" first** (the user-facing surface); the raw mpv
  **"Audio tracks" list is DEBUG-only** (the QualitySheet's DebugPreferences gate); the
  honest empty states. The drawer shells are orientation-aware (0.45f landscape /
  0.85f portrait) — the portrait-fullscreen sheets stop being cramped.

## 3. The rotate (FREE/LOCK)

- The button returns to the fullscreen bottom-right tray (after the skip icon) — WIRED
  this time (the round-145 removal premise was "never wired"). FREE =
  `SCREEN_ORIENTATION_FULL_SENSOR` (all four orientations, incl. 180); LOCK =
  `SCREEN_ORIENTATION_LOCKED` (API 27+, else the landscape re-assert). MINIMIZED stays
  portrait; the orientation effect keys on `(playerMode, rotationLocked)`. The
  orientation-aware drawers handle portrait-fullscreen.

## 4. The offline audio (the report's broken downloads)

- **The chain**: `DefaultDownloadManager.resolveAudioTracks(mainId, episodeNumber)` —
  the .data.json `audioUris` (written since round 149; NEVER read before) → the disk
  walk over `audio/` + the legacy siblings (the scanner's rule) — feeds the THREE
  hand-offs that passed empty audio (the details-page key, the downloads-page key, the
  offline episode switch). The offline player finally receives the versions and seeds
  `pendingAudioTracks`.
- **The loading route**: content:// never reaches mpv — the audio files load via the
  VIDEO's proven route (`openFileDescriptor` → `fd://N`, the descriptors kept open per
  switch, closed on dispose). The subtitle precedent was checked first (the subs are
  copied to cache; impractical for whole audio files).
- **The glitched files**: `downloadAudioToCache` now SKIPS `.m3u8/.m3u` audio variants
  (a flat fetch of a playlist was the garbage the user manually played) and gates
  publication on a 64 KB minimum. DASH keeps its own pipeline.
- **The DB**: deliberately UNCHANGED — the .data.json + the disk walk are the source of
  truth (the scanner rebuilds them on every scan; no new columns, no schema risk).

## 5. The verification + the ledger

- CI: **run 43948352917 GREEN on 8d29118a, FIRST TRY** (dispatched on the TEMP mirror;
  the flagged testplay-byte account still receives the push silently for when it
  unflags). NO release (the user's order) — the v1.1.90 TEMP APK remains the test
  artifact; the next TEMP-built APK carries this round when the user wants a test
  build.
- Next decision: D-885; next record: 130.
