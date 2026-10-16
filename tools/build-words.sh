#!/bin/sh
# Regenerates words/*.txt. Needs the Arch `words` package and tools/enable1.src.txt.
set -e
cd "$(dirname "$0")/.."
LC_ALL=C grep -E '^[a-z]{2,}$' /usr/share/dict/british-english | sort -u > words/british.txt
LC_ALL=C tr -d '\r' < tools/enable1.src.txt | grep -E '^[a-z]{2,}$' | sort -u > words/scrabble.txt
wc -l words/*.txt
