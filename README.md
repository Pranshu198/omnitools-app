# OmniTools — Android App

The native Android app for **OmniTools** (100+ free tools: music, live TV,
downloader, bhakti library, trains, panchang and much more).

**[⬇ Download the APK (v2.0.1)](https://github.com/Pranshu198/omnitools-app/releases/download/v2.0.1/OmniTools-2.0.1.apk)**
— free, no Play Store needed. Or browse all builds on the
[Releases page](https://github.com/Pranshu198/omnitools-app/releases).

## Standalone — the app carries its own content (new in v2.0)

v1 was a thin shell that loaded the website. **v2 is a proper standalone
app: the entire OmniTools site — every tool, every data set — is bundled
inside the APK** and served locally (via `WebViewAssetLoader` under the
fixed local origin `https://appassets.androidplatform.net`). The app
never loads the website; if the site disappeared tomorrow, the app
would not notice.

- **APK size: ≈ 44.9 MB.** Inside it is the full **158 MB** of site
  content — the data sets are JSON text, which compresses ~3.5× inside
  the APK. Breakdown of what's inside (uncompressed): data shards
  130 MB (trains timetable, cricket archive, PIN & IFSC directories,
  bus/metro, devotional libraries…), medicine database 22 MB, app code
  (JS/CSS) 4.9 MB, names data 0.6 MB.
- **Works offline** for everything that is data: all calculators,
  converters and generators, the offline data tools (trains, PIN,
  IFSC, cricket, bus/metro planners), the full bhakti and books
  libraries, games, and any music you saved or auto-cached.
- **Needs internet, by nature:** live streams and live data — music
  search & playback of new songs, live TV, internet radio, podcasts,
  weather, gold/silver rates, the downloader, TTS studio voices,
  manga/novels (they stream from their library API), and the update
  check. These call their services directly from the app — they never
  depended on the OmniTools website, so nothing about them changed.
- **Updates:** the app checks this repo's Releases once a day and
  offers a newer APK in a dialog (also under the menu → *Check for
  updates*). Updates install over the app and keep your data.
  An *Open website* entry (menu / error screen) opens the live site in
  your browser, if you ever want it — the app itself never does.

> **Upgrading from v1:** v2 installs over v1 normally (same signing
> key), but because v2 runs on its own local origin instead of the
> website's, **data saved inside v1 (favourites, saved songs, reading
> progress and the like) does not carry over** — v2 starts fresh, like
> a new browser profile. From v2 onwards, updates keep everything.

On top of the bundled app, the native shell adds what a browser
cannot do:

- **An app-only home screen** — greeting, a *My Day* strip (Verse of the
  Day + today's Panchang + weather/AQI), a Continue row, and a
  **fully customisable layout**: reorder or hide categories, pin your
  own tools into *My Tools*, and choose the four shortcuts in the app
  bottom bar (long-press the home screen, or tap *Customise*).
- **Home-screen widgets** (below).
- **Real downloads** — files the tools save (downloader videos, scanner
  PDFs, exports) land in your phone's **Downloads** folder.
- **Background music** — playback keeps going when you switch apps,
  with a media notification.
- **Share sheet, haptics, camera & mic** wired properly for the
  Scanner and Voice Recorder tools.

## Widgets

| Widget | What it does |
| --- | --- |
| **Music** (4×1 / 4×2) | Current song + artist with play/pause and next/previous. Tap it to jump into the player. Shows an "Open OmniTools" state until you play something. |
| **Daily** (4×2) | Today's Verse of the Day line and today's Panchang headline (tithi · nakshatra), with the date they belong to. Tap the verse for the full Verse of the Day, the panchang line for the full Panchang. Values refresh whenever the app runs, plus a periodic fallback. Open the app once after installing so the first values arrive. |
| **Quick Tools** | One-tap shortcuts to **TV**, **Downloader**, **Radio**, **Games** and **Trains**, deep-linked straight into the right tool. |

All three widgets are resizable where Android allows it and follow
the app's dark green theme.

## Install

1. Download the APK from the link above (or the Releases page).
2. Open the file. Android will ask to allow installs from your
   browser/files app ("Install unknown apps") — allow it for this
   install. This is the standard step for any app installed outside
   the Play Store.
3. Tap **Install**, then **Open**. The app icon (OmniTools) also
   appears in your app drawer.
4. Add widgets: long-press your home screen → **Widgets** →
   **OmniTools** → drag the Music, Daily or Quick Tools widget out.

Requires Android 8.0 (API 26) or newer. No account, no ads, no
tracking — the same promise as the website.

> **Updates:** install a newer APK over the old one and everything
> carries over (see the one-time v1 → v2 exception above). Android
> only allows that because every release is signed with the same
> key — see below. The app also checks for updates itself, once a day.

## Signing

Every release is signed with the same keystore. Verify any APK with:

```
apksigner verify --print-certs OmniTools-2.0.1.apk
```

- Signer certificate SHA-256:
  `F8:29:C1:0A:04:F6:00:04:10:1B:65:D1:EF:63:5E:1D:AC:B7:2D:8A:A9:94:08:69:F2:ED:2B:AF:47:8A:17:64`

The keystore itself is **not** in this repo (it lives, with its
password file, in the maintainer's private `keystore/` folder, which
is git-ignored). **It must stay backed up:** if it is lost, future
versions can no longer be installed as updates — every user would
have to uninstall and reinstall, losing app data.

## Building from source

Standard Android project (Java, no Kotlin): open in Android Studio,
or `./gradlew assembleRelease` with JDK 17 + Android SDK 34
(`minSdk 26`, `targetSdk 34`; deps: `androidx.webkit` +
`androidx.core`). Signing is read from `keystore.properties` (not
committed — create your own keystore and matching properties file to
build a signed release).

**The bundled site is a build artifact, not source.** `app/src/main/assets/`
(the whole PWA, ~158 MB) is git-ignored; `build-manual.sh` syncs it
from the PWA build output (default `../omnitools/dist`, override with
`PWA_DIST`) before packaging, and refuses to build without it. Build
the PWA first (`npm run build` in the omnitools repo). Verify a bundle
is complete with `node scripts/check_assets.mjs <distDir> <assetsDir>`
— it asserts every built file and every referenced data path is in the
bundle (18,035 files, byte-identical for v2.0).

Maintainer note: in the sandbox where these releases are built, the
Gradle daemon protocol cannot run (the sandbox intercepts loopback
TCP), so release APKs are produced with `build-manual.sh` — the exact
same pipeline Gradle runs (aapt2 → javac → R8 → zipalign → apksigner,
with the androidx AARs' classes fed to javac/R8 the way AGP would),
verified with `apksigner verify` and `aapt dump badging`. Android
Studio / Gradle builds the identical app from this source on a normal
machine.

## Layout of this repo

- `app/src/main/java/com/jackbhai/omnitools/` — the shell:
  `MainActivity` (WebView + local asset serving for page **and**
  service-worker requests + bridge + file chooser + deep links),
  `JsBridge` (the `window.OmniApp` API), `UpdateCheck` (daily GitHub
  Releases check), `PlaybackService` (background-music notification),
  `CmdReceiver` (widget buttons), `Widgets` + the three widget
  providers, `Store` (widget state).
- `app/src/main/assets/` — the bundled PWA (generated, git-ignored).
- `app/src/main/res/` — layouts, widget metadata, theme, icons.
- `scripts/check_assets.mjs` — bundle-completeness checker.
- The app-mode home screen itself (greeting, My Day, customisable
  grid, app tab bar) lives in the web app repo, guarded behind the
  bridge check so the website is unchanged:
  <https://github.com/jackbhai/omnitools>
