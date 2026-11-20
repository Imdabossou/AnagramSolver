# AGENTS.md

Ad-free anagram solver and crossword pattern helper. Static HTML, CSS and JavaScript with no build step; all searching runs in the browser.

## Commands

- Run: `docker compose up -d` (port 8090), stop with `docker compose down`
- Checks: `node --check` on each JS file, `jq empty manifest.webmanifest`, `shellcheck -S error tools/build-words.sh`
- Regenerate word lists: `tools/build-words.sh`
- More in [BUILDING.md](BUILDING.md)

## Conventions

- Branches: work on `development`; `main` only gets tested changes.
- No ads, analytics, trackers or third-party scripts. The only outside link is Wiktionary for definitions.
- Light, dark and system themes, defaulting to the system, with the choice remembered.
- When the Android app is built, wrap these web files (a WebView shell or Capacitor). Don't rewrite the app.
- Keep README.md short. Developer detail goes in BUILDING.md.

## Traps

- `compose.yaml` mounts single files, not the folder. A new file the page needs must be added there, and to the CI file check and the service worker's `FILES` list.
- Because of those single-file mounts, editing a file in place with `sed -i` replaces it, so the container keeps the old copy. Restart the container after such edits.
- `python3 -m http.server` stalls on the large word list fetch. Test with Docker Compose instead.
- Word lists are served cache-first by the service worker. Bump `VERSION` in `sw.js` when they change, or returning visitors keep the old lists.
