# Record 138 — Round 159: the device-report P0s, MyAnimeList, background playback, and the trackers groundwork

> The v1.1.97 device round reported: a P0 startup crash (the black screen —
> the home view model died in construction) and a P0 plugin-load failure
> (NoSuchFieldError $stable). The order: fix both thoroughly, implement MAL
> fully, implement background playback properly WITH the user options,
> complete the Trakt/TMDB groundwork to keys-only, and provide the key
> instructions. The debug release v1.1.98 at the end.

## 1. P0 — THE BLACK-SCREEN CRASH (the init-order)

The pasted stack: NPE on `MutableStateFlow.setValue` at HomeViewModel.kt:249.
MECHANISM: the home mode observer (round-157's fix) launches in init;
`viewModelScope` uses **Dispatchers.Main.immediate** — the StateFlow's first
emission processes SYNCHRONOUSLY inside the constructor, and the collect
touched the CS state flows declared ~500 lines AFTER the init block → a NULL
backing flow → the constructor threw → the home never composed → the black
screen. FIX: the CS state declarations moved BEFORE the init block (the
comment documents the trap). The round-3 critic verified every property the
construction touches is now pre-declared.

## 2. P0 — THE PLUGIN LOAD FAILURE (the $stable stamp)

`NoSuchFieldError: No field $stable of type I in class Plugin` — the plugins
are built against the OFFICIAL compose-processed API whose Compose compiler
stamps a `$stable` int on every class; our clean-room API compiles WITHOUT
the Compose compiler → the plugins' generated code's getstatic failed. FIX
(two iterations): the round-3 critic proved from THIS repo's own bytecode
that the compose reads are **getstatic** — the field must be STATIC FINAL,
not instance. Final shape: a companion `const val $stable = 0` on BasePlugin
+ MainAPI — javap-verified `public static final int $stable` on both (the
inherited read covers Plugin). 0 = the all-stable mark (the safe default).

## 3. WS-MAL — the full MyAnimeList tracker (62ec6bd2)

- MalAuth: PKCE (**plain** challenge — MAL's documented quirk), the redirect
  anikuta://mal-auth, the 1h access token + the ~1-month refresh.
- MalTracker: the refresh on 401 (the authenticated executor retries once
  with the fresh token), PUT my_list_status (the status mapping WATCHING→
  watching / COMPLETED→completed / PAUSED→on_hold / DROPPED→dropped /
  PLAN_TO_WATCH→plan_to_watch / REWATCHING→watching+is_rewatching; the score
  0-100 → 0-10 only when set), GET fetchEntry (my_list_status), the search,
  GET /users/@me (the identity verify at login).
- MalLinkResolver: ContentDetails.malId — AniList's own idMal field ALREADY
  flows for every AniList-linked entry → zero network for the AniList-linked
  content; unlinked entries skip honestly.
- The plumbing: the manifest filter, the MainActivity dispatch (both cold
  and warm), the TrackersScreen MalCard (setup-pending/link/connected + the
  ON_RESUME state refresh — the OAuth return lands in the SAME composition).
- Dormant until the user's MAL app registration lands in TrackerKeys.

## 4. WS-BG — background playback (the full v1)

- PlaybackCommandBus (core/common — both stacks depend on it): the controls
  registration + the version bump + the foreground flag.
- PlaybackService (core/player, the module manifest): a FOREGROUND
  mediaPlayback service (NOT dataSync — the 6h timeout), the notification
  with play/pause/±10s, the version watcher, the self-stop on the controls
  unregistering AND on the playback ending away (the critic's P1), always-
  ongoing (the swipe-dismiss ghost).
- The screens register the controls (the MPV: the skipForwardHolder deferred
  capture — onSkipForward is declared later; the subtitle a live get(); the
  seek rides IO — the ANR pattern; the CS: play/pause branch on the real
  state — both were the same toggle).
- The ON_STOP observers gate on PlayerPreferences.backgroundPlayback
  (default ON — the user's want; OFF restores pause-on-leave). The option
  lives in Player settings > Playback behavior.
- MainActivity: ON_START stops the service; ON_STOP starts it gated on the
  CONTROLS existing (the critic's stale-isPlaying race fix).
- NOT a MediaSession yet (the notification actions only) — the media3-session
  upgrade is the documented next step.

## 5. WS-C — the Trakt/TMDB groundwork completed

- **TrackerKeys.kt** (core/common): the ONE documented keys file — the MAL
  client id, the Trakt client id, the TMDB API key — with the step-by-step
  registration instructions in the KDoc. MalAuth/TraktAuth read it. Blank =
  dormant (zero network, honest states).
- **TraktLinkResolver** registered: extExtras.tmdbId (the WS-B2 capture) →
  the data axis → null (the TMDB search fallback lands with the API key).
- The key-encryption question: answered honestly in the file's KDoc — APK
  keys are always extractable; these are user-scoped registration ids, the
  tokens live in private storage, one reviewed file.

## 6. The critic round 3 + the fixes

The review verified the init-order fix completely (every construction-touch
pre-declared) and caught: the $stable shape (static vs instance — the
IncompatibleClassChangeError trap), the service's lingering-FGS-on-end, the
stale-isPlaying race, the CS toggle play/pause, the always-ongoing
notification, the seek-on-main, the MAL handler's unguarded koin resolution.
ALL fixed.

## 7. THE RELEASE (D-914)

The Build run **44765449418 GREEN on 4692d92b**; the Release run
**44767410995 GREEN**; **v1.1.98 / 10198 LIVE on Khurram00964/TEMP** —
verified: stable + latest, the downloaded SHA (b4f1b907...) MATCHES.
Sideloads over v1.1.97. NEXT: the keys land → the Trakt/TMDB activation +
the MAL activation (the same instructions); background playback's
MediaSession upgrade; the manga groundwork.
