// Offline cache. Bump VERSION when any file changes.
const VERSION = 'v1';
const FILES = ['./', 'index.html', 'style.css', 'app.js', 'icon.svg', 'manifest.webmanifest',
  'words/scrabble.txt', 'words/british.txt'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener('fetch', (e) => {
  e.respondWith(caches.match(e.request, { ignoreSearch: true }).then((hit) => hit || fetch(e.request)));
});
