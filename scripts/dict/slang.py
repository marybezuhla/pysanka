#!/usr/bin/env python3
"""
Pysanka: adds modern words (social media, chat) that the subtitle-based lists lack: slang_<lang>.txt
into app/src/main/assets/ime/dict/<lang>.json, each word not yet there at a middle frequency, so it
is offered and never "corrected" but does not push common words aside.

Run after build_dicts.py (which calls it too): python3 -I scripts/dict/slang.py app/src/main/assets/ime/dict
"""
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
# About the frequency of words like «окей» or «блог»: offered, never ahead of everyday words
FREQUENCY = 100


def words(lang: str) -> list[str]:
    out = []
    for line in (HERE / f"slang_{lang}.txt").read_text(encoding="utf-8").splitlines():
        w = line.strip().lower().replace("ʼ", "'").replace("’", "'")
        if w and not w.startswith("#"):
            out.append(w)
    return out


def add(out_dir: Path, lang: str) -> int:
    path = out_dir / f"{lang}.json"
    data: dict[str, int] = json.loads(path.read_text(encoding="utf-8"))
    added = 0
    for w in words(lang):
        if w not in data:
            data[w] = FREQUENCY
            added += 1
    data = dict(sorted(data.items(), key=lambda kv: -kv[1]))
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    return added


def main() -> None:
    out = Path(sys.argv[1])
    for lang in ("uk", "en"):
        print(lang, "added", add(out, lang))


if __name__ == "__main__":
    main()
