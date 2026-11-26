# Building Anagram Solver

There's no build step. It's plain HTML, CSS and JavaScript.

## Running it

```sh
docker compose up -d     # http://localhost:8090
docker compose down
```

`compose.yaml` mounts each site file into an `nginx:alpine` container one by one. If you add a file the page loads, add it to `compose.yaml` too.

To host it elsewhere, serve the files from any web server or static host. `nginx.conf` sends `Cache-Control: no-cache, no-transform` so browsers pick up updates and CDNs don't rewrite the page or its scripts. The service worker (offline use) only runs over HTTPS or on localhost.

## Checks

CI runs these:

```sh
node --check app.js && node --check sw.js && node --check theme.js
jq empty manifest.webmanifest
shellcheck -S error tools/build-words.sh
```

It also builds and lints the Android app, then starts the site with Docker Compose and checks that every file is served.

## Android app

`android/` is a thin Kotlin shell around the same web files; it doesn't have its own copy of the app. At build time Gradle copies `index.html`, `style.css`, `app.js`, `theme.js`, `icon.svg` and `words/*.txt` from the repo root into the APK, and a WebView serves them from a private `https://appassets.androidplatform.net` origin. The APK has no INTERNET permission; Wiktionary links open in the browser.

Requirements: JDK 17 or newer (21 recommended) and the Android SDK with `platforms;android-37.0`.

```sh
cd android
./gradlew assembleRelease lintRelease   # app/build/outputs/apk/release/app-release.apk
```

Unset `ANDROID_SDK_ROOT` if Gradle complains that it conflicts with `ANDROID_HOME`.

**Signing:** release builds use the debug key unless `android/keystore.properties` exists (git-ignored) with `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Android won't update an app signed with a different key, so moving to a new key means uninstalling first.

What the shell adds to `index.html` as it serves it:

- the `android` class on `<html>`, which switches `style.css` to the phone layout: title and status on top, results in the middle, filters, tabs and the input at the bottom (one scrolling page again on short screens such as landscape)
- on Android 12 and newer, the system's Material You colours as the page's CSS variables; older versions keep the monochrome palette
- the system bar and keyboard insets as `--inset-*` variables
- the theme saved by the shell, written to `localStorage` before `theme.js` reads it

`theme.js` reports the chosen theme through `window.AnagramApp` (a WebView message listener limited to the app's origin), so the shell can colour the status bar icons to match.

The launcher icon layers are generated from the `icon.svg` design by `android/tools/make-icon.py` (needs fontTools and Liberation Serif).

## Word lists

`words/` holds the two lists. Regenerate them with `tools/build-words.sh`, which needs Arch's `words` package.

- `scrabble.txt`: ENABLE (public domain), about 173,000 words, from `tools/enable1.src.txt`
- `british.txt`: SCOWL British English from the `words` package, lowercase a–z only

## Files

| File | Purpose |
| --- | --- |
| `index.html`, `style.css` | The page |
| `app.js` | Word list loading, searching, filters and sorting |
| `theme.js` | Light, dark and system theme, remembered between visits |
| `sw.js` | Service worker that caches the site for offline use |
| `manifest.webmanifest` | Lets the site be installed as an app |
| `android/` | The Android app shell (see above) |
