# Record 137 — Round 158: the verification round — three passes over the groundwork

> The user's order: "test the things out, do a review... multiple rounds of
> verification... nothing should be rushed or skipped... think about future
> proofing". No new features — EVERYTHING shipped in round 157 went through
> three verification passes, every finding fixed, the result released as
> v1.1.97.

## 1. V1 — the self-sweep (5af94ec9)

Re-reading the shipped paths with fresh eyes caught:
- **The home mode observer double-loaded** — the init `when` + the StateFlow
  collect's immediate first emission ran every loader twice (two concurrent
  fetches when the cache was expired). The loads live ONLY in the collect.
- **The auto-PiP picker trap**: the system SAF pickers (the audio/subtitle
  file choosers) fire onUserLeaveHint — tapping "Add audio file..." mid-play
  threw the player into PiP. The eligibility now requires NO sheet open
  (anySheetOpen / anyCsSheetOpen in the effects).
- **The PiP aspect**: the window followed a hardcoded 16:9 (portrait/4:3
  sources letterboxed). PipState now carries the video size (the screens
  write it — MPV's width/height properties, CS's videoWidth/Height), the
  entry uses Rational(w, h), and an out-of-range aspect retries 16:9.
- **The CS home block's honest error**: a provider failure (Cloudflare/
  network) claimed "install a plugin" — a distinct error message now.
- The debug gate's subtitle carries its hold-10-seconds hint (the audit's
  discoverability trap); the stale Phase-4 doc comment refreshed.

## 2. V2 — the critic round 2

Verified ALL 8 round-157 fixes (P0-1's channel now lands end-to-end: the
bridge -> SAnimeImpl -> UnifiedAnime -> ExtensionExtras). Caught:
- **P1 (the one real miss)**: the sourcesLoaded await was a NO-OP — a
  StateFlow `firstOrNull()` returns its CURRENT value instantly, so the
  cold-start race survived the round-1 fix. The Search page's exact pattern
  applied: `withTimeoutOrNull(20s) { sourcesLoaded.first { it } }`.
- P2s: one CS load job at a time (a pull-to-refresh during a streaming load
  duplicated shelves); the CS subtitle-settings sheet joined the auto-PiP
  guard (the MPV twin was guarded — the asymmetry); the PipState video size
  resets on screen dispose; the 180d prune's doc drift (the AnikutaApp
  comment + the contract rule 7 said 30d).

## 3. V3 — the final verification (f944a11e)

All fixed + compiled; 8/8 migration tests; **44634941096 GREEN**.

## 4. THE RELEASE (D-911)

**v1.1.97 / 10197 LIVE on Khurram00964/TEMP**: the Release run **44636421601
GREEN**; verified: stable + latest, ani-kuta-v1.1.97-debug-arm64-v8a.apk; the
downloaded SHA (b9c39ff4...) MATCHES. Sideloads over v1.1.96.

## 5. The state of the roadmap

- DONE: the groundwork (WS-A), the player quick wins + PiP (WS-E1/E2/E3),
  the content-type axis + the movie mode (WS-B), the home content mode
  (WS-D), the tracking translation layer + the dormant Trakt (WS-C), and
  this verification round.
- NEXT: background playback (the engine-ownership move — the round's one
  deferred structural item), then the Trakt/TMDB activation + the
  search-with picker (awaiting the user's TMDB API key + the Trakt
  client_id), MAL when ordered — the groundwork IS the enabler for each.
- The manga/novel future: the contract doc's content-type/format axis
  (`video` -> `image`/`text`) is the designed path — zero re-architecture.
