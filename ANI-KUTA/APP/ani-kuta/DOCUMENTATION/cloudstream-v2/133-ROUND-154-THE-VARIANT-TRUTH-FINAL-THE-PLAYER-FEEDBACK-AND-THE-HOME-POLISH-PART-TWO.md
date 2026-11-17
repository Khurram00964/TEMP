# Record 133 — Round 154: the device round three — the variant truth final, the player feedback, and the home polish part two

> The v1.1.93 device round: the variant bug STILL failed (a sub pick landed on
> Japanese); the initial stream fetch showed black with no loading animation;
> audio switches showed no loading; and the home wanted the hero cover-wash, a
> stronger airs highlight, the Surprise rail hidden on entry, the chip
> centering, and the mood collapse. THE ORDER: complete everything properly —
> this is the LAST debug build before the professional release line on the
> official repository.

## 1. THE VARIANT TRUTH FINAL (D-894) — the sheet pick's own label is the truth

- **The v1.1.93 report**: opening an episode, picking SUB, and the player STILL
  auto-selected Japanese (and the mirror case for dub). Round 153's chain rode
  the CLICKED ROW's variant — but for these dual-variant extensions the row's
  scanlator/name parse is unreliable (the rows can be variant-less), so the
  key could carry "" and the ladder fell through to the Japanese default.
- **THE FIX — the picked row's own label**: the resolver sheet's SUB/DUB chips
  GROUP the videos by their audio-version label, so the label of the row the
  user tapped IS the user's intent. The sheet callback
  `onPickVideo(ResolvedVideo, serverName, audioLabel)` already carried it —
  DetailsScreen now maps it (`DUB` -> "dub"; `SUB`/`HSUB` -> "sub"; unknown ->
  "" — never worse than before) and rides it as the key's
  `episodeAudioVariant`. (ResolvedVideo carries NO title field — the first
  attempt parsed a nonexistent `video.title`, then `videoTitle` — both wrong;
  the label was there all along.)
- **The steering**: `tryAutoSelect(success, episodeVariant)` prefers the
  episode variant's DUB/SUB group (the best-quality video in the group) OVER
  the continuity pin AND the configured engine; an empty group falls through
  to the pin/engine.
- **The ladder**: reads the LIVE video flavor first
  (`currentAudioVersion` DUB/SUB/HSUB beats the key's variant beats the
  metadata map). A resolver-sheet pick therefore survives episode switches
  AND the remember chain. `parseAudioAvailability` in the helper is FULLY
  QUALIFIED — the feature.watch package carries a same-name parse returning a
  different type (the shadowing bit CI once).

## 2. THE PLAYER FEEDBACK (D-895) — the first frame + the audio-switch pill

- **The black hole**: opening an episode stared at pure black for the whole
  initial fetch (the spinner only showed WITH the controls visible). NEW
  `PlayerCoverLoadingOverlay` (both stacks, above the mpv surface, below the
  controls, gestures pass through): while `LOADING && duration == 0` or an
  in-flight stream switch hasn't buffered, the episode's cover (the nav-riding
  coverUrl) fills the player under a 45% scrim with a centered spinner, then
  crossfades out (450ms) the moment real frames are visible.
- **The silent audio switch**: picking an external audio version deliberately
  PAUSES the player while mpv fetches + demuxes the new stream (up to the 12s
  timeout) — which read as a frozen app. NEW `AudioSwitchingPill` (a glass
  capsule: spinner + "Switching audio…", centered) rides the PLAYER while
  `audioSwitchPendingUrl != null` — visible even with the sheet dismissed
  (the sheet row spinner was the only feedback before). Passed into both
  MinimizedMode and FullscreenMode as a param.

## 3. THE HOME POLISH PART TWO (D-896) — the wash, the highlight, the collapse

- **The hero wash**: a blurred active-art underlay + a background-fade Box
  behind the carousel — the subtle cover-derived wash the user asked for
  ("a very subtle looking kind of one").
- **The airs next node**: the highlight strengthened — the pill rides the
  pulse (the bg 0.10->0.28, the border 0.30->0.85), the text lerps toward
  primary, the halo deepens.
- **The mood collapse**: `activeMood` starts "" (the rail HIDDEN on entry —
  only the header + the always-lit Surprise me); scrolling away collapses it
  again (the derivedStateOf moodsVisible -> the LaunchedEffect reset); a tap
  re-opens + centers the tapped chip (the centering never resets to the left).

## 4. CI + THE RELEASE (D-897)

- CI: run 44159612915 RED (10 errors / 4 roots: the missing `blur` import;
  the shadowed `parseAudioAvailability`; `video.title` on a title-less
  ResolvedVideo; `variantVideo.headers` on ResolverVideo's `videoHeaders`) ->
  fixed + **44172029051 GREEN on 6ab1f86d** (the LOCAL compile check now runs
  BEFORE the dispatch — the round-153 lesson applied). The (3/3) commit ->
  **44175100066 GREEN on 36553e16**. 2 dispatches, 2 green (the local gate
  worked).
- **THE RELEASE (D-897)**: the user's explicit order — "our very last debug
  build, and then we are going to do the proper release build". release/1.1.94
  cut from 36553e16 + ONE bump commit 5c5a77f4 (10120/1.1.20 -> **10194/1.1.94**)
  + the annotated tag v1.1.94 ON THE BUMP -> the Release APK run
  **44176156508 GREEN**; verified live: stable, ani-kuta-v1.1.94-debug-
  arm64-v8a.apk (76,620,144 bytes) + SHA256SUMS.txt; the downloaded SHA
  (b7aedf27...) MATCHES. Sideloads over v1.1.93 (10194 > 10193). The release
  carries round 154: the variant truth final, the first-frame cover, the
  audio-switch pill, the home polish part two.
- NEXT: the device round on v1.1.94; on the user's validation -> the proper
  release build on the OFFICIAL repository (the release-signed all-ABI line).
