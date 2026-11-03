#!/usr/bin/env python3
"""
Pysanka: app/src/main/assets/ime/dict/extras.json from Pysanka's own lists here (GPL-3.0-or-later):
- offensive_<lang>.txt: swear words and slurs, never offered unless typed in full ([stems]: a word
  starting so; [words]: whole words);
- stop_<lang>.txt: little words that never become a hashtag or an emoji.
Output: {"offensive": {lang: {"stems": [...], "words": [...]}}, "stop": [...]}: swear words per
language (Romanian «cu» is "with", Portuguese «cu» is not; Spanish «curva» is a curve), little words
of all languages together (a person mixes languages, and these only keep hashtags and emoji away).
Usage: python3 -I scripts/dict/extras.py app/src/main/assets/ime/dict
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def lines(f: Path):
    for line in f.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith("#"):
            yield line.lower().replace("’", "'")


def main() -> None:
    out = Path(sys.argv[1])
    offensive: dict[str, dict[str, list[str]]] = {}
    stop: set[str] = set()
    for f in sorted(HERE.glob("offensive_*.txt")):
        lang = f.stem.split("_", 1)[1]
        stems, words, section = set(), set(), None
        for line in lines(f):
            if line in ("[stems]", "[words]"):
                section = line
            elif section == "[stems]":
                stems.add(line)
            elif section == "[words]":
                words.add(line)
        offensive[lang] = {"stems": sorted(stems), "words": sorted(words)}
    for f in sorted(HERE.glob("stop_*.txt")):
        stop.update(w for w in lines(f) if " " not in w)
    data = {"offensive": offensive, "stop": sorted(stop)}
    (out / "extras.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print({k: (len(v["stems"]), len(v["words"])) for k, v in offensive.items()}, "stop", len(stop))


if __name__ == "__main__":
    main()
