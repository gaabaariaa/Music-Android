# Architecture

Musiq is a single Gradle module (`:app`) organized in layers by package. It follows Clean Architecture with MVVM:
Compose screens observe `StateFlow`s of Hilt `ViewModel`s, which call repository interfaces defined in `domain`,
implemented in `data`.

I kept one module on purpose: it builds faster and was safer to evolve without a local build environment. The
package boundaries below are the seams where it could be split into Gradle modules later.

## Layers

```
feature/*  (Compose UI + ViewModels)
    │  depends on
    ▼
domain     models, repository interfaces
    ▲  implemented by
    │
data       Room, DataStore, WorkManager, HTTP sources, TagLib
core       cross-cutting: Hilt modules, theme, pure helpers (parsers, formatting, smart playlists, duplicates)
app        Application, MainActivity, navigation graph
```

Rules of thumb:

* `domain` has no Compose and no database types. (A few interfaces expose `IntentSender` for the Android 11+
  confirmation dialogs; that is a deliberate shortcut.)
* Anything with real logic is a **pure function** in `core/util` or `data/*/…Parsers.kt` so it can be unit tested on
  the JVM: LRC parsing, smart playlists, duplicate detection, source parsers, health score, settings codecs.

## Feature packages

| Package | Responsibility |
| --- | --- |
| `feature/home` | Configurable Home sections |
| `feature/library` | Songs/artists/albums/genres/folders tabs, detail pages, song menu, favorites state |
| `feature/player` | `MusicPlaybackService` (ExoPlayer + MediaSession), `MusicPlayerManager` (MediaController wrapper, sleep timer), mini player, Now Playing, artwork loader |
| `feature/tags` | Tag editor (single and multi), edit session |
| `feature/artwork` | Cover search, embed/remove/save |
| `feature/lyrics` | Lyrics editor and the synced lyrics pane |
| `feature/search` | Global search |
| `feature/health` | Library health, duplicate review and deletion |
| `feature/downloads` | Source search and the download queue |
| `feature/playlists` | Playlist dialogs, tab, actions |
| `feature/settings` | All settings |

## Playback

`MusicPlaybackService` owns the `ExoPlayer` and a `MediaSession` (media3 handles notification, lock screen, Bluetooth
and headset buttons). The UI never touches the player directly: `MusicPlayerManager` (a singleton) connects a
`MediaController` and exposes it as a `StateFlow`. `PlayerViewModel` mirrors the controller into `PlayerUiState`.
The sleep timer lives in the singleton, not in a ViewModel, so it still fires when the UI is gone.
A song counts as played after 30 seconds (or half of a shorter song) and is written to `play_history`.

## Data

Room database `music.db`, **version 7**, with explicit migrations for every step (no destructive fallback).

| Table | Purpose |
| --- | --- |
| `songs` | One row per MediaStore audio file: tags, duration, size, mime type, path, folder, date added, bitrate |
| `lyrics` | Lyrics saved in the app (plain or LRC) |
| `song_audit` | Result of the background file inspection: has artwork, has embedded lyrics (valid while `sizeBytes` matches) |
| `downloads` | Download queue and history |
| `favorites`, `play_history` | Personal data for Home and smart playlists |
| `playlists`, `playlist_songs` | Normal playlists (ordered) and smart playlists (an encoded rule, no song rows) |

Artists, albums, genres and folders are not tables: they are `GROUP BY` queries over `songs`, which keeps a single
source of truth. User preferences are in Jetpack DataStore (`settings`).

The first value of the settings flow is read synchronously at startup (a few milliseconds) so the first frame already
has the right theme and language.

## Background work (WorkManager + Hilt workers)

| Worker | What it does |
| --- | --- |
| `LibraryScanWorker` | Reads MediaStore and updates `songs`; runs at start and every 6 hours. Skips without permission so a revoked permission cannot wipe the library |
| `LibraryAuditWorker` | Opens each file once with TagLib to learn whether it has artwork/lyrics; started by the user |
| `DownloadWorker` | One per download: https only, manual redirects, resume with `Range`, progress to Room, validates the file with TagLib, saves into `Music/Musiq` via MediaStore, fills missing tags, embeds the cover |

## Safe file changes

`SafeFileWriter` is used by tag, lyrics and artwork writes: copy the original to the app cache, run the change,
read it back and compare, and restore the copy if anything fails. On Android 11+ the system asks the user to allow
modifying the files (`MediaStore.createWriteRequest`); deleting uses `createDeleteRequest`.

## Network

Plain `HttpURLConnection` with timeouts, a User-Agent, size limits and https-only checks. Each source is a small
function plus a **pure parser** that is tested with sample responses. A failure of one source never blocks the
others ("All sources" runs them in parallel and reports errors per source).

## Localization and accessibility

All UI text is in `strings.xml` (English) and `values-fa/strings.xml` (Persian); a unit test fails the build if the
keys or placeholders differ. The language can be changed in the app: `MainActivity.attachBaseContext` wraps the context
with the saved locale and the Activity restarts when it changes. Headings, seek bar state, selection state and
click labels are exposed to TalkBack; touch targets are at least 48dp.

## Build and release

* Gradle wrapper 8.13, AGP 8.9, Kotlin 2.2, KSP, Hilt, Compose BOM, compileSdk 36, targetSdk 35, minSdk 26.
* Debug: shared committed debug key. Release: R8 + resource shrinking, signed from environment variables / CI secrets.
* CI (`.github/workflows/build-apk.yml`): wrapper validation, unit tests, debug APK on every push; release APK on
  `main` and version tags; tags publish a GitHub Release.
