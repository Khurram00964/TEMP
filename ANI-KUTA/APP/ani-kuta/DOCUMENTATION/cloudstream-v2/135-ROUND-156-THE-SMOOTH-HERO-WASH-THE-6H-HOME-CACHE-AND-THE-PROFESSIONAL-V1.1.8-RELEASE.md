# Record 135 — Round 156: the smooth hero wash, the completed 6-hour home cache, and THE PROFESSIONAL v1.1.8 RELEASE

> The v1.1.95 device round: the user is satisfied — "things are handled well
> this time". Two improvements ordered: (1) the hero's cover wash "does not
> change at all" — it should shift cover to cover, smoothly; (2) the 6-hour
> home cache — every section cached, auto-refresh only past 6h, pull-to-
> refresh resets the timer. THEN the big order: the PROPER RELEASE on the
> ACTUAL release repository (v1.1.7 -> v1.1.8), the web page + the download
> links + the ZIPs + the what's new, all following the banked playbook.

## 1. THE HERO WASH — the crossfade (D-901)

- **The diagnosis**: the round-155 wash DID change (the rounded index drove
  the image swap) but under a 28dp blur + a 0.55-0.72 dim every cover
  collapsed to the same pastel haze, and the swap had NO transition — it read
  as "does not change at all".
- **THE FIX**: `HeroWashUnderlay` — a TWO-LAYER crossfade driven by the
  FRACTIONAL carousel position: the settled slide holds full alpha while the
  NEXT slide fades in with the fractional part — position-tied, symmetric for
  both swipe directions, seamless across the wrap seam (the last slide blends
  into the first). The blur eased to 20dp (the palettes stay legible under
  the dim) and the dim softened slightly (0.5/0.68/1.0). The fraction rides
  `mutableFloatStateOf` passed down UNREAD — the per-frame writes recompose
  ONLY the underlay, never the top bar or the hero.

## 2. THE 6-HOUR HOME CACHE — completed (D-902)

- **The audit**: `home_trending` (the hero + the trending rail + the mood
  rails) was ALREADY 6h cache-first (the round-148 `browse_cache` table,
  `BROWSE_CACHE_TTL_MS = 6h`) — a fresh cache served with zero network, a
  fetch only past expiry, and pull-to-refresh rewrote it (the timer reset).
  The VIOLATIONS: the GLOBAL "Airs today" schedule fetched from the network
  on EVERY activation (the flatMapLatest re-run), and the suggestion field's
  cover pool refetched per process.
- **THE FIX**: the GLOBAL airs caches under `home_airs` and the covers pool
  under `home_field_covers` — the SAME table + TTL + codec (zero schema
  change). `BrowseCacheCodec` gained an additive `nextAiring` field (the
  airing times ride the payload so the countdown chips keep ticking
  client-side from a cached feed; old rows still parse — forward-compatible).
  On entry a fresh cache serves with ZERO network; a fetch happens ONLY when
  expired/missing; pull-to-refresh force-fetches + rewrites (resetting the
  6h timer). The personal rails (continue watching, the library-schedule
  airs) stay reactive device-data flows — always live, no cache needed. The
  suggested pool itself is local (the library's genre affinity). The full
  contract is documented in the VM header.

## 3. CI

- The LOCAL compile gate: all touched modules compiled clean pre-dispatch.
- **44351460947 GREEN on ecf5fe4a** (Build APK) — one dispatch, one green.

## 4. THE PROFESSIONAL v1.1.8 RELEASE (D-903) — Routines B/C/D followed exactly

- **Routine B (the all-ABI build)**: release/1.1.8 cut from ecf5fe4a + ONE
  bump commit 6fdf7a96 (10120/1.1.20 -> **10108/1.1.8**; 10108 > 10107 so
  v1.1.7 devices update in place) -> `release-build-once.yml` dispatched on
  TEMP (tag=v1.1.8, ref=release/1.1.8). FIRST RUN 44352596960 FAILED: the
  signing secrets were missing on TEMP (they lived on testplay-byte, which is
  flagged — GitHub secrets are write-only, unexportable). FOUND the delivery
  keystore in the workspace (SECURITY/ANI-KUTA-v1.1.1-DELIVERY/) — VERIFIED
  it is the professional line's key (its cert SHA-256 f05160db...a02531f
  byte-matches the v1.1.7 APK's Signer #1 fingerprint) -> the 4 secrets set
  on TEMP via the libsodium sealed-box API (201/204). RE-DISPATCH -> run
  **44355252757 GREEN**; artifact ani-kuta-v1.1.8-release-allabi downloaded
  (curl -sL, ~610MB).
- **Routine C (the official re-host)**: staged per the playbook — the 5 APKs
  renamed Ani-Kuta-<abi>.apk (byte-identical, the CI sums verified), the ABI
  audit (4 singles exact + universal = all four), the 5 ZIPs (ZIP_DEFLATED
  level 9, the APK at the zip ROOT, the inner sha verified), SHA256SUMS.txt
  (10 lines, LF, newline-terminated). The release created on
  Confused-Creature-180/ANI-KUTA: id **450447584**, tag v1.1.8 -> main,
  stable + make_latest, the body = the short what's new (the home, the
  audio, the fixes) + the download notes + the website/Discord links. THE
  WRINKLES (disclosed): the first release-POST crashed in urllib's chunked
  path (a dict body) — v2 pre-encodes every body as bytes with an explicit
  Content-Length; the sums byte-check via the api-host download 404'd (the
  doc-122 redirect wrinkle) — the browser-host download matched BYTE-FOR-
  BYTE (609a5007...), and the arm64 re-download checksum MATCHED the staged
  file + the sums line. Post-upload verification: 11/11 assets, every size
  exact, /releases/latest -> v1.1.8.
- **Routine D (the website + the README)**: the gh-pages chip v1.1.7 ->
  v1.1.8 + the README's five /download/ links re-pointed; the Pages build +
  the live fetches verified.
- **The updater simulation**: v1.1.7 -> OFFERED v1.1.8 (the real 10108 >
  10107 installs in place); v1.1.8 -> no update.
