# Record 128 — Round 149: the Aniyomi multi-audio

> The user's order: extensions like NUME provide multiple AUDIO VERSIONS for an episode
> (VF/VFQ/VOSTFR, per-language dubs) — implement selecting between them in the player
> (the landscape Audio button right of Quality, WIRED; the portrait row becomes exactly
> [Subtitles, Quality, Audio] with prev/next REMOVED), and downloading all available
> audio versions. The user is designing the homepage meanwhile — no release ordered;
> the round ships at CI green (D-738).

## 1. The contract (recon-verified, upstream + vendored)

- Upstream aniyomiorg/aniyomi source-api: `Video.audioTracks: List<Track(url, lang)>` —
  EXTERNAL audio files an extension attaches to one video; loaded into mpv as external
  audio; the player sheet switches `aid`. SEpisode carries NO audio field — sub/dub is
  scanlator/name text.
- OUR vendored API already carried it (core/source-api Video.kt:37) and the resolver
  flowed it through (ResolvedVideo.audioTracks, ResolverVideo.audioTracks) — NOTHING
  consumed it. The mpv machinery existed too: PlayerObserver.loadExternalTracks sends
  `audio-add <url> auto "" <lang>` after FILE_LOADED; PlayerStateHolder.audioTracks
  mirrors mpv's track-list; AnikutaMPVView.aid selects instantly; PlaybackStateStore
  persisted audioTrackId per episode. The landscape button had been REMOVED in round 145
  (P-M3 — "never wired"); the portrait row was [Subtitles, Quality, Prev, Next].

## 2. The implementation (9 files)

- **THE AUDIO SHEET** (PlayerSheets.kt, a mirror of SubtitleTracksSheet's panel
  language): "Audio tracks" = the LIVE mpv list (embedded + audio-add'ed externals,
  aid-selected, instant, no reload); "Audio versions" = the extension's external Tracks —
  a registered mpv track is found by lang/name and aid-set; an unregistered one is
  `audio-add <url> select`'d and lights up LIVE (the observer's track-list reload +
  the aid property feed).
- **The landscape Audio button** re-added right of Quality — NOW WIRED (the round-145
  removal premise no longer holds; the comment history kept accurate).
- **The portrait row** = exactly [Subtitles, Quality, Audio] — prev/next REMOVED from
  MinimizedControls (episode navigation stays fullscreen-only; the underlying
  onSkipBackward/onSkipForward functions stay for the fullscreen cluster).
- **The per-series audio memory** (the D-408 subtitle-memory twin,
  `pref_audio_track_memory_<mainId>`): the pick persists and pre-applies after every
  track-list reload via the onTracksLoaded hook.
- **pendingAudioTracks verified** on all three loadfile paths (init/quality/episode) —
  no path needed fixing.
- **DOWNLOAD-ALL-AUDIO**: one enqueue downloads the video + EVERY attached audio version
  — buildRequest maps video.audioTracks to TrackKind.AUDIO_VARIANT (the ONE
  (main_id, episode_key) row carries them all; no per-audio rows), HttpDownloader
  fetches them after the subtitles, DownloadStorageProvider publishes the D-550 audio/
  layout (`<base>.audio<N>.mp4`) for progressive too, .data.json records the URIs, DASH
  keeps its own pipeline. The zero-consumer TrackKind.AUDIO stays in the enum (old
  persisted rows deserialize it).

## 3. The verification + the gaps

- CI: run **43909899466 GREEN on cbd38176, first try** — no diagnostics.
- The DISCLOSED GAPS: (a) OFFLINE playback of downloaded audio versions is not wired yet
  (multi-fd + the Downloads-DB → pendingAudioTracks bridge — deep; the files + URIs are
  stored and scanner-recorded for the future wiring); (b) no progressive sidecar carries
  the audio lang label on disk (URIs only in .data.json — matches DASH's naming);
  (c) PRE-EXISTING: the retry handler re-loadfiles without re-setting pendingAudioTracks
  (external subs AND now audio versions drop on a cache-failure retry — the subtitle
  behavior since before; noted, not touched).
- The home-page design comes from the user next (they are designing it); no release this
  round — the debug line stays v1.1.89/10189, the next release carries 147-149.
- Next decision: D-882; next record: 129.
