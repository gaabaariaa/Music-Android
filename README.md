# Musiq

An offline-first music player and music library manager for Android, written in Kotlin with Jetpack Compose.
It plays the music already on the device, keeps it organized, fixes its tags, covers and lyrics, and can download
**legally free** music from several open sources. English and Persian (RTL) are fully supported.

> فارسی: [README.fa.md](README.fa.md)

The working name is **Musiq**. To rename the app see [Renaming](#renaming-the-app).

## Features

| Area | What it does |
| --- | --- |
| Library | Scans MediaStore in the background (WorkManager); songs, artists, albums, genres, folders, playlists; list or grid; sorting, search, multi-select |
| Home | Configurable sections (recently played/added, most played, favorites, recently downloaded, playlists, artists, albums, genres, missing tags/artwork/lyrics), reorderable |
| Player | Media3/ExoPlayer in a MediaSession service: notification, lock screen, Bluetooth and headset controls, queue, shuffle, repeat, sleep timer, playback speed, synced lyrics |
| Tags | TagLib reads and writes MP3, FLAC, M4A, OGG, OPUS, WAV; multi-song editing; every write is backed up, verified and rolled back on failure |
| Artwork | Finds covers on MusicBrainz and the Cover Art Archive; embed, remove, or save to Pictures |
| Lyrics | Embedded, saved in the app, imported `.lrc`/`.txt`, or found on LRCLIB; synced lyrics follow the music |
| Search | One search over songs, artists, albums, genres, folders and saved lyrics |
| Library Health | Score, missing tags/artwork/lyrics, low quality, unrecognized songs, duplicate finder with confirmed delete |
| Playlists | Normal playlists and smart playlists (rules such as recently added, most played, never played, favorites, genre, Persian, high quality, longer than N minutes) |
| Downloads | Resumable downloads from legal sources: Internet Archive, Openverse, Wikimedia Commons, ccMixter, Jamendo, direct https links, RSS/podcast feeds |
| Personalization | Light/dark/system, dynamic color, AMOLED, accent color, corner shape and roundness, text size, grid/list, artwork on/off, animations, tab and Home order |
| Languages | English and Persian, switchable inside the app, RTL, localized numbers |

## Requirements

* Android 8.0 (API 26) or newer. Some features need more: genre and bitrate (Android 11+), writing tags and deleting
  files with the system confirmation dialog (Android 11+), covers in lists (Android 10+).
* JDK 17 and the Android SDK (platform 36) to build locally. Android Studio Ladybug or newer is recommended.

## Build

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # unit tests
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

You do not need a local build: every push runs GitHub Actions, which tests the code and uploads the debug APK as an
artifact. Version tags (`v1.0.0`, ...) also publish a signed release APK on the *Releases* page.

### Signing

* **Debug** builds use the committed key `app/debug.keystore`. It is intentionally public: it only exists so that every
  debug APK has the same signature and can be installed over the previous one without uninstalling. Never use it for
  anything you distribute.
* **Release** builds read the key from environment variables, never from files in git:
  `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
  In CI these come from repository secrets (`RELEASE_KEYSTORE_BASE64` holds the base64 of the keystore).
  Without them the release build falls back to the debug key, so it can be tested but not published.
* **Keep a backup of the release keystore.** If it is lost, existing users can never receive updates.
* A release APK has a different signature from a debug APK, so switching between them needs one uninstall.

## Project structure

```
app/src/main/java/com/gaabaariaa/music
  app/        Application, MainActivity, navigation host
  core/       dependency injection, theme, utilities (parsers, formatting, smart playlists, duplicates)
  domain/     models and repository interfaces (no Android dependencies except a few system types)
  data/       Room database, repositories, WorkManager workers, network sources, TagLib access
  feature/    one package per screen group: home, library, player, tags, artwork, lyrics, search,
              health, downloads, playlists, settings
```

More detail, including the database schema and the background work, is in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Privacy and security

* No account, no analytics, no ads. The library never leaves the device.
* Network access happens only when you search for covers, lyrics or downloads, and always over https
  (`usesCleartextTraffic` is off; redirects are checked hop by hop).
* The only user-entered credential is the optional Jamendo client ID, stored in the app's private DataStore.
  No API keys are committed to git. `.gitignore` excludes keystores and `local.properties`.
* Files are never modified or deleted without a system or in-app confirmation; tag and artwork writes are backed up
  and verified first.

## Downloads and the law

Musiq only offers sources whose content is freely licensed, in the public domain, or published by the artist for
download, and shows the license of every track. You are responsible for following each license (for example crediting
the artist). The *Direct link* and *RSS feed* sources download whatever you point them at, so use them only for files
you have the right to download. The app does not search for or download copyrighted music from unauthorized sources.

## Testing

Unit tests cover the logic that is easy to get wrong: LRC parsing, smart playlists, duplicate detection, library
health score, search and sort, the source parsers (with sample responses), settings encoding, number formatting and
the string resources (English and Persian must have the same keys and placeholders).
CI runs them on every push. There are no instrumented (on-device) tests yet.

## Known limitations

* Not implemented: Android Auto, cover-based dynamic theme and blur, Paging 3 (lists are fully loaded from Room),
  a "Recommended" Home section, audio fingerprinting for duplicates and automatic *Identify Song*.
* Network sources were tested with sample data only; the live responses of ccMixter, Wikimedia Commons and Jamendo may
  differ slightly from what the parsers expect.
* Writing tags on Android 10 and older is best effort.
* Reordering uses arrow buttons, not drag and drop.

## Renaming the app

The visible name is the string `app_name` in `app/src/main/res/values/strings.xml` and `values-fa/strings.xml`.
The package and `applicationId` (`com.gaabaariaa.music`) can stay as they are; changing `applicationId` makes Android
treat the app as a different app. The launcher icon is in `res/drawable/ic_launcher_foreground.xml`.

## Data sources and credits

MusicBrainz and the Cover Art Archive (cover search), LRCLIB (lyrics), Internet Archive, Openverse, Wikimedia Commons,
ccMixter and Jamendo (downloads). TagLib (tag reading and writing) through the `io.github.kyant0:taglib` library.
