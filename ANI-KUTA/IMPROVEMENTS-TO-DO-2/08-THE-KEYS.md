# 08 — THE KEYS (all the credentials/keys, mapped)

> Two classes, handled differently:
> - **The APP keys** (the OAuth client ids + the API keys): public-by-design
>   (PKCE carries no secret) and ALREADY in the public source
>   (TrackerKeys.kt / AniListOAuth.kt / the manifest) — they live INLINE
>   here (the IMPROVEMENTS-TO-DO.md §1 precedent).
> - **The REPO/SIGNING credentials** (the GitHub PATs, the keystore
>   passwords): NEVER committed anywhere — they live in the LOCAL vault
>   (git-ignored). Their PATHS are mapped here; open the file for the value.

## A. The app keys (inline)

| The service | The key | The redirect / the notes |
|---|---|---|
| **AniList** (the anime tracking, the OAuth implicit) | `48714` | `anikuta://anilist-auth` (the fragment-carried token, D-220); registered at anilist.co/settings/developer |
| **MyAnimeList** (the anime/manga tracking, PKCE-plain) | `3ef19e73545f5029bbb1f2bfd62fd6c5` | `anikuta://mal-auth`; registered at myanimelist.net/apiconfig; the PKCE-plain quirk (the challenge = the base64url of the SHA-256 HEX — 05-T-2) |
| **Trakt** (registered; the module unbuilt — the Development tier) | `RCANXjQblhWRIIYj0ax1ZmWH_S8e-IM0Va3Cz1A9qSY` | `anikuta://trakt-auth`; the Production switch is a same-page action when needed |
| **TMDB v3** (the movies/series data source + the metadata; the query-param auth) | `210d23e812a0b2f4ea75fe4e934ff526` | — |
| **TMDB v4** (the read token, the bearer for the future v4 endpoints; NOT an account token) | `eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiIyMTBkMjNlODEyYTBiMmY0ZWE3NWZlNGU5MzRmZjUyNiIsIm5iZiI6MTc5NTE5MjMwNi4yNjcsInN1YiI6IjZiMDA3NWYyZDA0N2RiYmRmMjhmNzJiZSIsInNjb3BlcyI6WyJhcGlfcmVhZCJdLCJ2ZXJzaW9uIjoxfQ.t5xheRXvTuL5hMVL5nLTuLZxx76q-hDfYJ_R39KazHA` | — |
| **Simkl** (the movies + TV tracking, PKCE-S256) | `bad5a61d7eb08f8bd0059650b1e66bf0135542014f900100bbb8069c8cd604ba` | `anikuta://simkl-auth`; registered 2026-11-21 (simkl.com/apps); the LINKED-LIVE flow proved on device |
| **TVmaze** | NO KEY — the open API (`api.tvmaze.com`), the keyless data source | — |

The single source in code: `core/tracker-api/.../TrackerKeys.kt` + `core/tracker-anilist/.../AniListOAuth.kt` (CLIENT_ID = 48714) — the recovery branches hold the full shape.

## B. The repo credentials (the LOCAL vault paths — the values live ONLY there)

| The credential | The file |
|---|---|
| The TEMP mirror PAT (Khurram00964 — the LIVE CI surface) | `C:\Users\khurr\Desktop\ANI-KUTA\credentials\temp-repo-credentials` (VALID; the notes: `temp-repo-notes.md`) |
| The official-repo PAT (Confused-Creature-180 — the releases + the site) | `C:\Users\khurr\Desktop\ANI-KUTA\credentials\official-repo-credentials` (VALID) |
| The dev-repo PAT (testplay-byte) | `C:\Users\khurr\Desktop\ANI-KUTA\credentials\github-credentials` — **DEAD since 2026-11-21** (the flagged account, the API 401) |
| The GitHub account notes | `C:\Users\khurr\Desktop\ANI-KUTA\credentials\github.md` |

## C. The signing keystores (the LOCAL vault paths)

| The material | The path |
|---|---|
| The release keystore + the properties + the provenance docs | `C:\Users\khurr\Desktop\ANI-KUTA\credentials\keystores\release\` |
| The debug keystore backup | `C:\Users\khurr\Desktop\ANI-KUTA\credentials\keystores\debug\` |
| The master notes (the aliases, the passwords' location, the CI secrets mapping) | `C:\Users\khurr\Desktop\ANI-KUTA\credentials\keystores\NOTES.md` |

The CI signs via the TEMP repo's GitHub Actions secrets (the setup procedure: `credentials\temp-repo-notes.md`).

## D. The vault rules (from credentials\README.md — binding)

- The vault is git-ignored — NEVER remove the exclusion; NEVER paste its
  contents into chat/logs/commits/tickets (this file carries the app keys
  only because they are already public in source).
- The replaced credentials keep their old line marked REVOKED with the date.
- The long-lived tokens are revoked at the provider when no longer needed.
