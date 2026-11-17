# Record 131 — Round 152: the audio round three + the home polish

> The user's device round on v1.1.91: the audio ANR + the silent default + the sheet
> UX + the offline audio regression + the rotate placement/icons + the home polish list
> (the fonts, the logo, the avatar, the hero, the pills, the airs, the mood, the field).
> The order: fix everything properly + THE DEBUG RELEASE + an ordered test checklist.

## 1. The player audio (the ANR + the round three)

- **THE ANR (root cause confirmed)**: path 2 of `onExternalAudioPicked` called
  `MPVLib.command(arrayOf("audio-add", url, "select", ...))` ON THE MAIN THREAD — a
  synchronous JNI mpv_command that opens the external demuxer (a network fetch + probe)
  inside the command → the UI thread blocked → "application isn't responding" → the
  audio landed when mpv finished. v1.1.90 felt instant because its audio-add ran on
  PlayerObserver's IO scope. FIXED: the select command dispatches to `Dispatchers.IO`
  (the v1.1.90 idiom); the pause + the 12s wait machinery intact.
- **THE NEVER-SILENT LADDER EXTENDED**: rung (a) respected ANY live valid aid —
  including the file's embedded-but-silent/broken track — so the externals never
  promoted. FIXED: when external versions exist and the active track is an embedded one
  matching neither the versions nor the remembered choice → the externals take priority
  (the variant tokens → any external); the embedded stays only when no external
  matches.
- **THE SHEET UX**: the plus icon REMOVED; the per-version states — the ACTIVE check
  (primary), the SWITCHING spinner (the live loading state the user asked for), the
  FAILED error (the honest 3s auto-clear; the failures never cached → re-picking
  retries); the version↔track highlight mapping resolved (the registry match exposed
  to the sheet).
- **The switch QoL**: the pause keeps, but the picked row shows the live loading state —
  the video waits WITH feedback, not frozen silently.

## 2. The offline audio (the regression fixed at the root)

- **Root cause**: every audio URL for those episodes is HLS (.m3u8) — the round-150
  skip produced ZERO files → no audio/ folder, no data.json audioUris, the sheet's
  honest empty state.
- **THE FIX**: FFmpegKit (bundled since forever: com.github.jmir1:ffmpeg-kit:1.18,
  api'd through player-mpv-lib) remuxes the HLS audio variants to local m4a
  (`-c copy -vn`, the headers passed as `Key: Value\r\n`, the 30s network timeout, -y
  against the no-stdin hang); the output feeds the existing audio/ publish path; the
  64KB gate stays. DASH keeps its own pipeline.

## 3. The cache-hit autoplay (the report item 9)

- The details' autoplay flow checked the NETWORK resolver first and ended at the
  selection sheet. FIXED: the pendingAutoPlay poll consults the PLAYBACK CACHE first
  (`PlaybackCacheStore.findByIdentitySync` — the upstream url/headers/quality/subs/
  audio from the entry) and navigates DIRECTLY to the player on a hit (the
  `buildWatchKeyFromCacheEntry` precedent); the network flow stays for the misses.
- **The playback pin**: `PlayerPreferences.get/setRememberedPlayback(mainId)` — the
  `serverKey|audio|quality` triple written on every playback start (initial, quality
  switch, episode switch) and CONSULTED by `tryAutoSelect` (a soft preference — the
  matching server preferred, never a filter).

## 4. The rotate

- Moved BETWEEN speed and next (the user's exact order: zoom → speed → ROTATE → next);
  the icon pair swapped (outlined ScreenRotation for free, filled ScreenLockRotation
  for locked — the visual state difference).

## 5. The home polish (agent B)

- **The fonts**: Bricolage Grotesque variable BUNDLED (408,496 bytes, the fvar/gvar
  tables verified; the canonical repo ateliertriay/bricolage — googlefonts 404s) — the
  TopBar wordmark, the section heads, the hero lockup, the browse heads in
  `DisplayFamily`; the body stays Roboto. ~170-220KB APK cost.
- **The logo**: the app's real artwork (icon_current.png) in the TopBar chip (the
  feature modules can't see app resources — passed as a param from MainActivity).
- **The profile avatar**: the user's SAVED image (profile_avatar_url — the file:// or
  URL) in the profile button, the initial/glyph fallback.
- **The hero**: the aspect was already 302:404 portrait ✓; the SNAP BUG fixed — the
  auto-advance effect keyed on `active` CANCELLED ITSELF mid-flight (active flips at
  the halfway point) leaving the row between covers for a whole cycle; now ONE
  never-cancelled loop with the target recomputed at fire time + the hold-off
  respected. The opacity correction: the handoff CSS sets .hero-card opacity 0.66
  idle → 1.0 active (kept; the "sides not visible" complaint was the snap bug).
- **The title lockup**: NEVER ellipsizes — `splitHeroTitle` (the trailing season/volume
  regex, the eyebrow = the words before the last 1-2, the main = the rest at 30sp
  ExtraBold DisplayFamily, the accent token INLINE in one AnnotatedString, maxLines 2,
  softWrap).
- **The pills**: the glass chips 11sp/700 + 0.22sp tracking + nowrap; the airs
  countdown chips minHeight 32→26, the padding 13/6→10/4; the node column 68→78dp (the
  time never wraps).
- **The airs**: the next-row 1.5dp primary outline pulse (the node halo kept); the
  RELEASE ANIMATION — the head + the rows are separate home-LazyColumn items with
  `key = episodeKey` + `animateItem()`, and the VM gained a per-minute ticker that
  recomposes the released flags, so a release happening on-screen actually animates
  (the top row out, the rows glide, the new bottom row in).
- **The schedule tab intent**: UpdatesKey → a data class with initialTab; the home's
  Schedule → UpdatesKey(initialTab = 1); the old backstacks decode (all-default
  params).
- **The spacing**: the poster-card title top 6→4dp (the trending + the catalog + the
  mood rails), the score gap 2dp.
- **The mood**: Surprise me starts ACTIVE; the chip tap scrolls the rail to the
  viewport top; the chip rail resets on the state change; the rail state hoisted to
  the Home level (survives the far-scroll disposal).
- **The genres cache**: the 19 genres hardcoded (never fetched), the popular tags
  persisted in HomePreferences (7-day TTL, cache-first, the background refresh, the
  failures can't blank the index) — the "couldn't reach AniList" dead.
- **The field**: the frame border removed; THE MELT BUG — the linearGradient without
  start/end renders the default 45° diagonal for ALL four directions (the "rotated
  strips") — fixed with the explicit per-edge geometry; the covers hoisted to the VM
  (the fetch-once guard + the trending fallback) + the SectionPreloader warm-up at the
  exact tile size — re-entry is instant.

## 6. The verification + the ledger

- CI: 44122963091 RED (the hls taskId Long + HomeFieldCover's internal visibility),
  **44125007041 GREEN on 7b330bff**. 2 runs, disclosed.
- **THE RELEASE (D-889)**: the user's explicit order — testplay-byte still flagged →
  the TEMP mirror: release/1.1.92 + ONE bump commit 6d21374a (10120/1.1.20 →
  **10192/1.1.92**) + the annotated tag v1.1.92 ON THE BUMP COMMIT → the Release APK
  run **44127049874 GREEN**; verified live: stable + latest,
  ani-kuta-v1.1.92-debug-arm64-v8a.apk (76,603,760 bytes) + SHA256SUMS.txt; the
  downloaded SHA (435d2552…) MATCHES. Sideloads over v1.1.91 (10192 > 10191).
- Next decision: D-890; next record: 132.
