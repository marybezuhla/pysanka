#!/usr/bin/env python3
"""
Pysanka: words the subtitle lists lack, added to the main lists (app/src/main/assets/ime/dict/
<lang>.json): modern words and slang, military and wartime life, government and digital services,
brands, medicine, Ukraine's main cities and regions, first names.

Sources:
- Pysanka's own lists here (GPL-3.0): <theme>_<lang>.txt, one word per line. A word the Ukrainian
  hunspell dictionary knows gets all its forms from it («дрон» → дрона, дронів…).
- dict_uk (https://github.com/brown-uk/dict_uk, GPL 3.0+ / LGPL 2.1+ / MPL 1.1): names of medicines
  (pharm.lst) and first names (names-anim.lst), their forms from the uk_UA hunspell dictionary
  (made from dict_uk, as in LibreOffice).

Each theme has its own frequency: high enough to be offered and never "corrected", low enough not
to push everyday words aside. A word already in the main list is left out.

It also takes from the main Ukrainian list the Russian words the subtitles brought in (STOP in
build_dicts.py).

Russian (when the ru_RU hunspell dir is given): Pysanka's own <theme>_ru.txt, forms from the
LibreOffice ru_RU hunspell dictionary; a word the subtitles have but rarely («клавиатура») is raised
to the theme's frequency.

Usage (after build_dicts.py): python3 -I scripts/dict/themes.py <dict_uk dir> <uk_UA hunspell dir> app/src/main/assets/ime/dict [<ru_RU hunspell dir>]
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
    "slang": (("uk", "en", "ru"), 100),
    "everyday": (("uk", "ru"), 130),
    "military": (("uk",), 110),
    "state": (("uk",), 100),
    "brands": (("uk", "en", "ru"), 100),
    "medical": (("uk",), 70),
    "places": (("uk", "ru"), 90),
    "names": (("uk",), 50),
    # Ukrainian things (culture, food, history; in the other languages also refugees' words)
    "ukraine": (("uk",), 100),
}

# The languages added for 1.0: every theme whose list exists (<theme>_<lang>.txt), with the forms
# written in the list (no hunspell forms are made for them)
MORE = ("pl", "hu", "ro", "sk", "es", "pt", "fr", "de", "be")


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
        # First names: tagged "<" (a surname is "<+")
        "names": lemmas(dicts / "names-anim.lst", lambda t: "<" in t and "<+" not in t and "patr" not in t),
    }
    langs = ["uk", "en"] + [l for l in MORE if (out / f"{l}.json").exists()]
    stems_ru: dict[str, set[str]] = {}
    if len(sys.argv) > 4:
        ru_dir = Path(sys.argv[4])
        stems_ru = hunspell(ru_dir / "ru_RU.dic", ru_dir / "ru_RU.aff")
        langs.insert(2, "ru")
    for lang in langs:
        path = out / f"{lang}.json"
        base: dict[str, int] = json.loads(path.read_text(encoding="utf-8"))
        # Without the Russian words the subtitles brought in
        base = {w: n for w, n in base.items() if w not in STOP.get(lang, set())}
        known = {w.lower() for w in base}
        for theme, (theme_langs, freq) in THEMES.items():
            if lang not in theme_langs and not (lang in MORE and own_list(theme, lang)):
                continue
            added = 0
            forms_of = stems if lang == "uk" else stems_ru if lang == "ru" else {}
            for w in own_list(theme, lang) + (from_dict_uk.get(theme, []) if lang == "uk" else []):
                for f in forms_of.get(w, {w}):
                    if f.lower() not in known:
                        base[f] = freq
                        known.add(f.lower())
                        added += 1
                    elif lang not in ("uk", "en") and f not in base and f != f.lower() and base.get(f.lower(), freq) < freq:
                        # A name the subtitles have in small letters, and rarely as a word («киев»): its
                        # capital, as common as the theme. A common word stays as it is («ровно», «новая»)
                        base[f] = max(base.pop(f.lower()), freq)
                        added += 1
                    elif lang not in ("uk", "en") and base.get(f, freq) < freq:
                        # In the subtitles, but rarely: as common as the theme
                        base[f] = freq
                        added += 1
            print(lang, theme, added)
        base = dict(sorted(base.items(), key=lambda kv: -kv[1]))
        path.write_text(json.dumps(base, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        print(lang, "words", len(base))


if __name__ == "__main__":
    main()
