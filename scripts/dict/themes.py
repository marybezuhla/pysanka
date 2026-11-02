#!/usr/bin/env python3
"""
Pysanka: the extra word lists a person can turn on (Settings → Typing → Extra dictionaries):
app/src/main/assets/ime/dict/<lang>-<theme>.json, each {"word": frequency}.

Sources:
- Pysanka's own lists here (GPL-3.0): <theme>_<lang>.txt, one word per line. A word the Ukrainian
  hunspell dictionary knows gets all its forms from it («дрон» → дрона, дронів…).
- dict_uk (https://github.com/brown-uk/dict_uk, GPL 3.0+ / LGPL 2.1+ / MPL 1.1): names of medicines
  (pharm.lst), Ukrainian settlements (geo-ukr-koatuu.lst) and first names (names-anim.lst), their
  forms from the uk_UA hunspell dictionary (made from dict_uk, as in LibreOffice).

Each theme has its own frequency: high enough to be offered and never "corrected", low enough not
to push everyday words aside. A word already in the main list is left out.

It also takes from the main Ukrainian list the Russian words the subtitles brought in (STOP in
build_dicts.py).

Usage: python3 -I scripts/dict/themes.py <dict_uk dir> <uk_UA hunspell dir> app/src/main/assets/ime/dict
"""
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from build_dicts import STOP  # noqa: E402

# theme: (languages, frequency)
THEMES = {
    "slang": (("uk", "en"), 100),
    "military": (("uk",), 110),
    "state": (("uk",), 100),
    "brands": (("uk", "en"), 100),
    "medical": (("uk",), 70),
    "places": (("uk",), 40),
    "names": (("uk",), 50),
}


def own_list(theme: str, lang: str) -> list[str]:
    f = HERE / f"{theme}_{lang}.txt"
    if not f.exists():
        return []
    out = []
    for line in f.read_text(encoding="utf-8").splitlines():
        w = line.strip().replace("ʼ", "'").replace("’", "'")
        if w and not w.startswith("#"):
            out.append(w)
    return out


def hunspell(dic: Path, aff: Path) -> dict[str, set[str]]:
    """Every stem of the dictionary with its forms."""
    rules: dict[str, list[tuple[str, str, re.Pattern]]] = {}
    for line in aff.read_text(encoding="utf-8").splitlines():
        p = line.split()
        if len(p) >= 5 and p[0] == "SFX":
            strip, add = ("" if p[2] == "0" else p[2]), ("" if p[3] == "0" else p[3].split("/")[0])
            rules.setdefault(p[1], []).append((strip, add, re.compile(p[4] + "$")))
    stems: dict[str, set[str]] = {}
    for line in dic.read_text(encoding="utf-8").splitlines()[1:]:
        line = line.split("\t")[0].strip()
        if not line or line.startswith("+"):
            continue
        stem, _, flags = line.partition("/")
        forms = stems.setdefault(stem, set())
        forms.add(stem)
        for f in flags:
            for strip, add, cond in rules.get(f, []):
                if cond.search(stem) and stem.endswith(strip):
                    forms.add(stem[: len(stem) - len(strip)] + add)
    return stems


def lemmas(path: Path, keep) -> list[str]:
    """The words of a dict_uk list whose tags pass [keep] (comments and alternatives left out)."""
    out = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.split("#")[0].strip()
        if not line:
            continue
        word, _, tags = line.partition(" ")
        if keep(tags.strip()):
            out.append(word.strip().replace("ʼ", "'").replace("’", "'"))
    return out


def main() -> None:
    dict_uk, hun_dir, out = (Path(a) for a in sys.argv[1:4])
    stems = hunspell(hun_dir / "uk_UA.dic", hun_dir / "uk_UA.aff")
    dicts = dict_uk / "data" / "dict"
    from_dict_uk = {
        "medical": lemmas(dicts / "pharm.lst", lambda t: True),
        # Nouns only (not the adjectives made from the names)
        "places": lemmas(dicts / "geo-ukr-koatuu.lst", lambda t: t.startswith("/n")),
        # First names: tagged "<" (a surname is "<+")
        "names": lemmas(dicts / "names-anim.lst", lambda t: "<" in t and "<+" not in t and "patr" not in t),
    }
    base_cache: dict[str, dict[str, int]] = {}
    for theme, (langs, freq) in THEMES.items():
        for lang in langs:
            base = base_cache.setdefault(lang, json.loads((out / f"{lang}.json").read_text(encoding="utf-8")))
            known = {w.lower() for w in base}
            words = own_list(theme, lang) + (from_dict_uk.get(theme, []) if lang == "uk" else [])
            data: dict[str, int] = {}
            for w in words:
                forms = stems.get(w, {w}) if lang == "uk" else {w}
                for f in forms:
                    if f.lower() not in known and f not in data:
                        data[f] = freq
            (out / f"{lang}-{theme}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
            print(lang, theme, len(data))
    # The main list without the Russian words the subtitles brought in
    uk = base_cache["uk"]
    cleaned = {w: n for w, n in uk.items() if w not in STOP["uk"]}
    (out / "uk.json").write_text(json.dumps(cleaned, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("uk main list: removed", len(uk) - len(cleaned))


if __name__ == "__main__":
    main()
