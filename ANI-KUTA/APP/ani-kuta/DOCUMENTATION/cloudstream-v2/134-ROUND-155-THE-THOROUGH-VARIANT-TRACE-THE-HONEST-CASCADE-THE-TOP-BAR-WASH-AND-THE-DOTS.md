# Record 134 — Round 155: the thorough variant trace — the honest cascade, the top-bar wash, and the dots

> The v1.1.94 device round: a DUB episode STILL auto-selected Japanese (the
> third round on this bug — the user ordered it "properly and thoroughly
> understood"); the hero's cover wash should reach behind the TOP BAR (the app
> name + the profile icon); and the hero dots were rendering AT THE TOP over
> the cards instead of below the carousel. THE ORDER: fix everything, optimize,
> then a proper debug release.

## 1. THE VARIANT BUG — the thorough trace (D-898)

THE USER's report: opening a dub episode selected Japanese — and it did not
even select the extension's RECOMMENDED audio. Both halves traced to the
player's default-audio ladder (`applyRememberedAudio`) — THREE stacked bugs,
all pushing Japanese:

1. **The remembered audio gated the variant out and outranked it.** Rung (d)
   computed `variantMatch` ONLY when `rememberedMatch == null`, and the target
   chain was `rememberedMatch ?: variantMatch ?: audios.first()`. The
   remembered per-series choice (persisted by ANY previous sub session or
   resolver-sheet pick via `setPreferredAudioTrack`) therefore won EVERY time —
   an explicit dub-row click could never beat a stale memory.
2. **A variant-LESS episode actively hunted JAPANESE.** Both rungs' token
   tables were `if (variant.hasDub) eng else jpn` — the `else` caught hasSub
   AND the unknown flavor, so an episode whose row parse produced "" sent the
   ladder hunting jpn/japanese tokens. The unknown case must hunt NOTHING.
3. **The terminal stomp.** Rung (d) ended in `audios.first()` and the (a)-insert
   in `externalTracks.firstOrNull()` — both overriding mpv's OWN alang default,
   which IS the extension's recommendation (the user's "the recommended audio
   was not selected either").

**THE HONEST CASCADE (round 155's ladder)**, newest intent first:
- the LIVE video flavor (`currentAudioVersion` — the sheet pick) — unchanged;
- the EPISODE VARIANT (the clicked row / the sheet pick's label): hunt
  eng/english (dub) or jpn/japanese (sub) among the tracks/externals — NOW
  computed ALWAYS (never gated by the memory) and OUTRANKING it;
- the REMEMBERED choice — only when the variant said nothing;
- **mpv's own default — when neither determines a flavor, the aid is NOT
  touched** (alang picks the recommendation; it is never silent by itself —
  the embedded-silent case stays rung (a)'s job). The audios.first() stomp is
  retired.

**The resolver steering** (DetailsViewModel.tryAutoSelect): the DUB/SUB group
match is TOKEN-based (`contains`), not exact-label — "English Dub" /
"Japanese Sub" labels used to miss the exact equals() and drop the steering to
the stale pin. And when the flavor is KNOWN but the steering finds no group,
the continuity pin's AUDIO component is blanked (continuity keeps the server +
the quality; the flavor belongs to the episode).

## 2. THE HERO — the top-bar wash + the dots (D-899)

- **The top-bar wash**: the topbar and the hero were sibling LazyColumn items
  — the hero-internal wash could not reach the top bar. THE MERGE: ONE
  item(key = "hero") holds the wash underlay (the active slide's blurred art +
  the fade) and a Column(topbar, HomeHero). The wash now underlies the app
  name + the profile icon — the same subtle gradient — and still fades to the
  background by the hero's bottom (the rest of the feed untouched). HomeHero
  reports its wash index up (`onWashIndex`, a distinctUntilChanged
  snapshotFlow on the fractional position). The item count dropped by one →
  the moods auto-scroll index 6+size → 5+size.
- **THE DOTS BUG**: the round-154 wash wrapper Box stranded the dots Row as a
  BOX SIBLING — and Box children OVERLAY: the dots rendered at the TOP edge
  over the cards instead of below the carousel (exactly the report). The dots
  moved INSIDE the Column, after the LazyRow (top 10dp, bottom 2dp).

## 3. CI + THE RELEASE (D-900)

- The LOCAL compile gate ran first (the round-154 lesson): one error caught
  before dispatch (the steering's log line still referencing the removed
  `wanted`) — fixed, all four modules compiled clean.
- CI: **44341513087 GREEN on d904c19f** — one dispatch, one green.
- **THE RELEASE (D-900)**: the user's order ("do a proper release, the proper
  debug release too"). release/1.1.95 cut from d904c19f + ONE bump commit
  1e6a01dc (10120/1.1.20 -> **10195/1.1.95**) + the annotated tag v1.1.95 ON
  THE BUMP -> the Release APK run **44342680485**; verified live: stable,
  ani-kuta-v1.1.95-debug-arm64-v8a.apk + SHA256SUMS.txt; the downloaded SHA
  MATCHES. Sideloads over v1.1.94 (10195 > 10194). Carries round 155: the
  variant honest cascade, the top-bar wash, the dots below the carousel.
- NEXT: the device round on v1.1.95 — the dub-open check FIRST. On validation
  -> the proper release on the OFFICIAL repository.
