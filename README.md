# OmniTools — Android App

The native Android app for **OmniTools** (100+ free tools: music, live TV,
downloader, bhakti library, trains, panchang and much more).

**[⬇ Download the APK (v1.0)](https://github.com/Pranshu198/omnitools-app/releases/download/v1.0/OmniTools-1.0.apk)**
— free, no Play Store needed. Or browse all builds on the
[Releases page](https://github.com/Pranshu198/omnitools-app/releases).

The app is a lean native shell around the OmniTools web app
(<https://jackbhai.github.io/omnitools/>) with a real Android bridge —
so inside the app you get things a browser cannot do:

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
> carries over. Android only allows that because every release is
> signed with the same key — see below.

## Signing

Every release is signed with the same keystore. Verify any APK with:

```
apksigner verify --print-certs OmniTools-1.0.apk
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
(`minSdk 26`, `targetSdk 34`). Signing is read from
`keystore.properties` (not committed — create your own keystore and
matching properties file to build a signed release).

Maintainer note: in the sandbox where v1.0 was built, the Gradle
daemon protocol could not run (the sandbox intercepts loopback TCP),
so the release APK was produced with `build-manual.sh` — the exact
same pipeline Gradle runs (aapt2 → javac → R8 → zipalign →
apksigner), verified with `apksigner verify` and `aapt dump
badging`. Android Studio / Gradle builds the identical app from this
source on a normal machine.

## Layout of this repo

- `app/src/main/java/com/jackbhai/omnitools/` — the shell:
  `MainActivity` (WebView + bridge + file chooser + deep links),
  `JsBridge` (the `window.OmniApp` API), `PlaybackService`
  (background-music notification), `CmdReceiver` (widget buttons),
  `Widgets` + the three widget providers, `Store` (widget state).
- `app/src/main/res/` — layouts, widget metadata, theme, icons.
- The app-mode home screen itself (greeting, My Day, customisable
  grid, app tab bar) lives in the web app repo, guarded behind the
  bridge check so the website is unchanged:
  <https://github.com/jackbhai/omnitools>
