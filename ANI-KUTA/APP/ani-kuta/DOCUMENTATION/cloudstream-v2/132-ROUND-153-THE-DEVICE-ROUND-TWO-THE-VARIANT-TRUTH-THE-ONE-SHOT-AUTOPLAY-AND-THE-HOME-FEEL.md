# Record 132 — Round 153: the device round two — the variant truth, the one-shot autoplay, and the home feel

> The user's device round on v1.1.92: the dub episode auto-picked Japanese; the
> cache-hit back-loop trapped them in the player; "Add audio file" was wanted; the
> downloaded audio versions were labeled "Audio 1/2"; and a long home-feel list (the
> topbar, the hero size/snap, the pills, the continue spacing, the airs highlight/rail/
> animation, the mood behaviors, the genres index failing, the field). THE USER'S
> WORKFLOW ORDER: do the tasks MYSELF (not subagents), plan in a logical order, update
> the docs, and no release unless asked.

## 1. THE VARIANT TRUTH (the dub-picked-Japanese bug — fixed at the root)

- **Root cause**: `parseEpisodeMetadata().associateBy { it.episodeNumber }` COLLAPSES
  dual-variant rows — sub and dub share an episode number, so the map's scanlator for
  the number was the SUB row's label. The ladder's variant match read the collapsed
  scanlator → hasDub false → the jpn tokens → Japanese. The quality sheet's sub
  selection display rode the same collapse.
- **THE FIX**: the clicked episode's variant rides the nav:
  `WatchKey.episodeAudioVariant` ("dub"/"sub"/"", computed by the details from the
  CLICKED row's own scanlator + name via the new `episodeAudioVariantOf` helper) →
  `PlayerStateHolder.currentEpisodeAudioVariant` (the seed/update params) → the
  ladder's two variant reads OVERRIDE the collapsed map when the state carries
  dub/sub. The episode-switch paths pass "" (the map fallback) — disclosed.
- The details page already showed the correct per-row pills, so the click-time truth
  was available at the exact point the key was built.

## 2. THE CACHE-HIT BACK-LOOP (the "player never closes" trap)

- **Root cause**: `hasAutoPlayed` was plain `remember` — on back-navigation from the
  player, the details screen re-composed FRESH (the SaveableStateProvider restores
  saveable state only), so the Phase-3 autoplay re-fired → the cache-hit re-pushed the
  player → the loop (a double-back beat the re-fire).
- **THE FIX**: `rememberSaveable` — the autoplay is genuinely one-shot per details
  instance. The user returns to the DETAILS page normally now.

## 3. "ADD AUDIO FILE" (the new feature)

- The AudioSheet gains the always-available "Add audio file…" row (even when the
  stream carries no versions — that's exactly when the user brings their own). The SAF
  picker (OpenDocument audio/*) → `openFileDescriptor` → `fd://N` → `audio-add …
  "select"` in ONE command (registered AND selected), the descriptor held open in
  `mpvAudioPfds` for the session (closed on dispose) — the video pfd's discipline, the
  subtitle-picker precedent's IO wrapping.

## 4. THE OFFLINE AUDIO LABELS ("Audio 1/2" → the real names)

- The langs thread: `downloadAudioToCache` returns
  `DownloadedAudioVariant(file, lang)` pairs → `publishVideoFile(audioLangs=…)` →
  `PublishResult.audioLangs` → `.data.json audioLangs` (additive, nullable, both-way
  compatible) → `resolveAudioTracks` labels index-aligned (dedupe-safe via the
  source-list lookup); the disk walk's fallback stays "Audio N" (the filenames carry
  no lang). The sheet now shows "Japanese"/"English".

## 5. THE HOME FEEL (agent-free, by hand)

- **The topbar**: the logo chip is GONE (the user: "outright remove it"); the
  wordmark grew 23→27sp. The profile button shows the SAVED avatar (AsyncImage over
  profile_avatar_url — round-152's wiring kept).
- **The hero**: the center card narrows (100% − 48dp → 100% − 88dp — the user: the
  main cover "should not take up way too much space"), the content padding
  re-derived to a literal 44dp so the snap stays dead-centered and the neighbours'
  corners peek properly on both sides.
- **The pills**: the squished metrics (the vertical padding 4→2dp, the text
  line-height = the font size — the tall line boxes were the hidden height).
- **The continue card**: the title ONE line (the user's explicit rule), the top gap
  8→4dp, the title→time gap 2→1dp, the explicit line-heights.
- **The airs**: the row outline REMOVED (the highlight belongs ONLY to the release
  time node — the pulsing halo stays); THE RAIL CONTINUITY — the rail now draws on
  the UNPADDED outer Box (the old segments drew inside the 7dp-padded rows, gapping
  the line) — one continuous line across the rows.
- **The mood**: Surprise me is the ALWAYS-LIT brand button (the active style never
  turns off) and every tap reshuffles; the genre chips keep the toggle semantics; the
  chip tap scrolls the feed smoothly to the bottom; the TAPPED CHIP CENTERS in the
  rail (the old behavior scrolled to the first genre).
- **The trending/mood cards**: the tight title line-height (13sp) kills the font's
  descent gap; the score row hugs the title (0dp) — both cards.
- **The genres index**: the 30 popular tags CURATED APP-SIDE (STATIC_POPULAR_TAGS) —
  the index serves instantly and can NEVER say "couldn't reach AniList" on entry; the
  live fetch is a background freshness pass only.

## 6. The verification + the ledger

- CI: 44142923472 RED (the logoRes pass-through + the maxWidth scope + the
  HttpDownloader threading half-applied by my own earlier partial write), 44146256073
  RED (the duplicate extraction block + the results list typing), 44148191800 RED
  (the missing imports + THE WATCHKEY FIELD INSERTED MID-CLASS shifting every
  positional argument — the mangled regex edit reverted and re-applied cleanly),
  **44150435946 GREEN on fd883fa8**. 5 runs, disclosed (the no-subagent session —
  every error mine, every lesson mine: partial-write chains and mid-class field
  insertions are the two new banked traps).
- **The release**: NOT done this round — the user's message carried no release order
  (the v1.1.92 TEMP APK remains the installed test artifact; the next release carries
  rounds 151-153 when ordered).
- **The subagent context answer** (the user's question): the subagents do NOT share
  my context — each starts fresh with only the brief I write and must re-read the
  targeted files themselves. That is why the briefs carry the recon facts inline.
- **THE RELEASE (D-893)**: the user's explicit order — testplay-byte STILL flagged
  (404 unauthenticated), so Routine A adapted to the TEMP mirror: release/1.1.93 + ONE
  bump commit 3bb68f0c (10120/1.1.20 → **10193/1.1.93**) + the annotated tag v1.1.93 ON
  THE BUMP COMMIT → the Release APK run **44153597596 GREEN** (dispatched on TEMP);
  verified live: stable + latest, ani-kuta-v1.1.93-debug-arm64-v8a.apk (76,620,144
  bytes) + SHA256SUMS.txt; the downloaded SHA (6df50dc2…) MATCHES. Sideloads over
  v1.1.92 (10193 > 10192). The testplay-byte release stays pending the unflag + the
  order.
- Next decision: D-894; next record: 133.
