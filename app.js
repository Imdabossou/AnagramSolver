'use strict';

const SCORES = {
  a: 1, b: 3, c: 3, d: 2, e: 1, f: 4, g: 2, h: 4, i: 1, j: 8, k: 5, l: 1, m: 3,
  n: 1, o: 1, p: 3, q: 10, r: 1, s: 1, t: 1, u: 1, v: 4, w: 4, x: 8, y: 4, z: 10,
};
const GROUP_LIMIT = 150;
const A = 97;

const $ = (id) => document.getElementById(id);
const els = {
  letters: $('letters'), clear: $('clear'), hint: $('hint'), dict: $('dict'), sort: $('sort'),
  starts: $('starts'), contains: $('contains'), ends: $('ends'), length: $('length'),
  exact: $('exact'), status: $('status'), results: $('results'),
};

const dictionaries = {};
let mode = 'anagram';

async function loadDict(name) {
  if (!dictionaries[name]) {
    dictionaries[name] = fetch(`words/${name}.txt`)
      .then((r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.text();
      })
      .then((t) => t.split('\n').filter(Boolean));
  }
  return dictionaries[name];
}

function cleanLetters(s) {
  return s.toLowerCase().replace(/[^a-z?]/g, '');
}

// Returns null if `word` can't be built from the rack, else the indexes of letters that need a blank.
function fit(word, counts, blanks) {
  const left = counts.slice();
  let spare = blanks;
  const blankIdx = [];
  for (let i = 0; i < word.length; i++) {
    const c = word.charCodeAt(i) - A;
    if (left[c] > 0) left[c]--;
    else if (spare > 0) { spare--; blankIdx.push(i); }
    else return null;
  }
  return blankIdx;
}

function score(word, blankIdx) {
  let s = 0;
  for (let i = 0; i < word.length; i++) if (!blankIdx.includes(i)) s += SCORES[word[i]];
  return s;
}

function solveAnagram(words, rack) {
  const counts = new Array(26).fill(0);
  let blanks = 0;
  for (const ch of rack) {
    if (ch === '?') blanks++;
    else counts[ch.charCodeAt(0) - A]++;
  }
  const max = rack.length;
  const starts = cleanLetters(els.starts.value).replace(/\?/g, '');
  const contains = cleanLetters(els.contains.value).replace(/\?/g, '');
  const ends = cleanLetters(els.ends.value).replace(/\?/g, '');
  const wantLen = els.exact.checked ? max : Number(els.length.value) || 0;

  const out = [];
  for (const w of words) {
    if (w.length > max) continue;
    if (wantLen && w.length !== wantLen) continue;
    if (starts && !w.startsWith(starts)) continue;
    if (ends && !w.endsWith(ends)) continue;
    if (contains && !w.includes(contains)) continue;
    const b = fit(w, counts, blanks);
    if (b) out.push({ w, b, s: score(w, b), full: w.length === max });
  }
  return out;
}

function solvePattern(words, pattern) {
  // ? = one letter, * = any run of letters
  const src = pattern.toLowerCase().replace(/[^a-z?*]/g, '');
  if (!src.replace(/\*/g, '')) return [];
  const re = new RegExp('^' + src.replace(/\?/g, '[a-z]').replace(/\*+/g, '[a-z]*') + '$');
  const out = [];
  for (const w of words) {
    if (re.test(w)) {
      const b = [];
      for (let i = 0; i < w.length && i < src.length && !src.includes('*'); i++) if (src[i] === '?') b.push(i);
      out.push({ w, b, s: score(w, []), full: false });
    }
  }
  return out;
}

function sortResults(list) {
  const by = els.sort.value;
  const alpha = (x, y) => (x.w < y.w ? -1 : x.w > y.w ? 1 : 0);
  if (by === 'score') list.sort((x, y) => y.s - x.s || y.w.length - x.w.length || alpha(x, y));
  else if (by === 'alpha') list.sort(alpha);
  else list.sort((x, y) => y.w.length - x.w.length || y.s - x.s || alpha(x, y));
  return list;
}

function wordEl(r) {
  const a = document.createElement('a');
  a.className = 'word' + (r.full ? ' full' : '');
  a.href = `https://en.wiktionary.org/wiki/${r.w}`;
  a.target = '_blank';
  a.rel = 'noopener';
  a.title = 'Look up definition';
  const letters = document.createElement('span');
  for (let i = 0; i < r.w.length; i++) {
    const ch = r.w[i].toUpperCase();
    if (r.b.includes(i)) {
      const span = document.createElement('span');
      span.className = 'b';
      span.textContent = ch;
      letters.append(span);
    } else {
      letters.append(ch);
    }
  }
  a.append(letters);
  const sub = document.createElement('sub');
  sub.textContent = r.s;
  a.append(sub);
  return a;
}

function renderGroup(container, title, list) {
  const g = document.createElement('div');
  g.className = 'group';
  const h = document.createElement('h2');
  h.textContent = title + ' ';
  const n = document.createElement('small');
  n.textContent = `(${list.length})`;
  h.append(n);
  const box = document.createElement('div');
  box.className = 'words';
  const frag = document.createDocumentFragment();
  list.slice(0, GROUP_LIMIT).forEach((r) => frag.append(wordEl(r)));
  box.append(frag);
  g.append(h, box);
  if (list.length > GROUP_LIMIT) {
    const more = document.createElement('button');
    more.className = 'more';
    more.textContent = `Show all ${list.length}`;
    more.onclick = () => {
      const f = document.createDocumentFragment();
      list.slice(GROUP_LIMIT).forEach((r) => f.append(wordEl(r)));
      box.append(f);
      more.remove();
    };
    g.append(more);
  }
  container.append(g);
}

function render(list) {
  const container = document.createDocumentFragment();
  if (els.sort.value === 'length') {
    const groups = new Map();
    for (const r of list) {
      if (!groups.has(r.w.length)) groups.set(r.w.length, []);
      groups.get(r.w.length).push(r);
    }
    for (const [len, items] of groups) renderGroup(container, `${len} letters`, items);
  } else {
    renderGroup(container, els.sort.value === 'score' ? 'By score' : 'A–Z', list);
  }
  els.results.replaceChildren(container);
}

function updateLengthOptions(max) {
  const current = els.length.value;
  const opts = ['<option value="">Any</option>'];
  for (let i = 2; i <= Math.max(max, 2); i++) opts.push(`<option value="${i}">${i} letters</option>`);
  els.length.innerHTML = opts.join('');
  els.length.value = Number(current) <= max ? current : '';
}

let runId = 0;
async function run() {
  const id = ++runId;
  saveState();
  const raw = els.letters.value;
  let words;
  try {
    words = await loadDict(els.dict.value);
  } catch (e) {
    els.status.textContent = `Couldn't load the dictionary (${e.message}).`;
    return;
  }
  if (id !== runId) return;

  let list;
  if (mode === 'anagram') {
    const rack = cleanLetters(raw.replace(/ /g, '?'));
    updateLengthOptions(rack.length);
    if (rack.length < 2) {
      els.status.textContent = rack.length ? 'Enter at least two letters.' : `${words.length.toLocaleString()} words loaded. Type some letters to unscramble.`;
      els.results.replaceChildren();
      return;
    }
    list = solveAnagram(words, rack);
  } else {
    const pat = raw.replace(/ /g, '?');
    if (!/[a-z?]/i.test(pat)) {
      els.status.textContent = 'Type a pattern like C?T or ST*NG.';
      els.results.replaceChildren();
      return;
    }
    list = solvePattern(words, pat);
  }

  sortResults(list);
  const full = list.filter((r) => r.full).length;
  els.status.textContent = list.length
    ? `${list.length.toLocaleString()} word${list.length === 1 ? '' : 's'} found` +
      (mode === 'anagram' && full ? `, ${full} using every letter (highlighted).` : '.')
    : 'No words found.';
  render(list);
}

let timer;
function scheduleRun() {
  clearTimeout(timer);
  timer = setTimeout(run, 120);
}

function setMode(m) {
  mode = m;
  document.body.classList.toggle('mode-pattern', m === 'pattern');
  document.querySelectorAll('.tab').forEach((t) => {
    const on = t.dataset.mode === m;
    t.classList.toggle('active', on);
    t.setAttribute('aria-selected', on);
  });
  if (m === 'pattern') {
    els.letters.placeholder = 'Pattern, e.g. C?T or ST*NG';
    els.hint.innerHTML = '<kbd>?</kbd> = one unknown letter, <kbd>*</kbd> = any number of letters.';
  } else {
    els.letters.placeholder = 'Enter letters, ? for blank';
    els.hint.innerHTML = 'Use <kbd>?</kbd> or space for a blank tile.';
  }
  run();
}

const STATE_KEY = 'anagram-solver-state';
function saveState() {
  try {
    localStorage.setItem(STATE_KEY, JSON.stringify({ mode, dict: els.dict.value, sort: els.sort.value }));
$('search').addEventListener('submit', (ev) => {
  ev.preventDefault();
  clearTimeout(timer);
  run();
  document.activeElement?.blur();
});
  } catch { /* storage unavailable */ }
}
function loadState() {
  try {
    const s = JSON.parse(localStorage.getItem(STATE_KEY) || '{}');
    if (s.dict) els.dict.value = s.dict;
    if (s.sort) els.sort.value = s.sort;
    return s.mode === 'pattern' ? 'pattern' : 'anagram';
  } catch {
    return 'anagram';
  }
}

els.letters.addEventListener('input', scheduleRun);
// Enter runs the search straight away and closes the on-screen keyboard.
[els.letters, els.starts, els.contains, els.ends].forEach((e) => e.addEventListener('keydown', (ev) => {
  if (ev.key !== 'Enter') return;
  ev.preventDefault();
  clearTimeout(timer);
  run();
  e.blur();
}));
[els.starts, els.contains, els.ends].forEach((e) => e.addEventListener('input', scheduleRun));
[els.dict, els.sort, els.length, els.exact].forEach((e) => e.addEventListener('change', run));
els.clear.addEventListener('click', () => {
  els.letters.value = '';
  els.letters.focus();
  run();
});
document.querySelectorAll('.tab').forEach((t) => t.addEventListener('click', () => {
  setMode(t.dataset.mode);
  els.letters.focus();
}));

const initialQuery = new URLSearchParams(location.search).get('q');
if (initialQuery) els.letters.value = initialQuery;
setMode(loadState());
els.letters.focus();

if ('serviceWorker' in navigator && window.isSecureContext) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
