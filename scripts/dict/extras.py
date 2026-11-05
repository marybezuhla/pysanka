#!/usr/bin/env python3
"""
Pysanka: app/src/main/assets/ime/dict/extras.json from Pysanka's own lists here (GPL-3.0-or-later):
- offensive_<lang>.txt: swear words and slurs, never offered unless typed in full ([stems]: a word
  starting so; [words]: whole words);
- stop_<lang>.txt: little words that never become a hashtag or an emoji.
Output: {"offensive": {lang: {"stems": [...], "words": [...]}}, "stop": {lang: [...]}}, each list
for its own language only: an ordinary word of one language is a swear word or a little word of
another (Romanian «cu» is "with", Portuguese «cu» is not; French «car» is "because", English «car» 🚗).
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
    stop: dict[str, list[str]] = {}
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
        stop[f.stem.split("_", 1)[1]] = sorted({w for w in lines(f) if " " not in w})
    data = {"offensive": offensive, "stop": stop}
    (out / "extras.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print({k: (len(v["stems"]), len(v["words"])) for k, v in offensive.items()}, "stop", {k: len(v) for k, v in stop.items()})


if __name__ == "__main__":
    main()
