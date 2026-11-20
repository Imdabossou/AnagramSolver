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

It then starts the site with Docker Compose and checks that every file is served.

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
