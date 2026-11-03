#!/usr/bin/env python3
"""
Pysanka: the part of a Wiktionary extract (wiktextract, https://kaikki.org, CC BY-SA 4.0 and GFDL
like Wiktionary itself) that the offline translation dictionaries are made from.

Reads the raw JSONL of one Wiktionary edition on stdin and writes, for every one of Pysanka's
languages, the entries in that language with only what build.py uses: the word, its part of speech,
short meanings, the lemma an inflected form belongs to, its forms, and the translations into the
other languages.

Usage: curl -sL <kaikki raw data> | gzip -dc | python3 -I extract.py <edition> <out dir>
"""
import gzip
import json
import sys
from pathlib import Path

LANGS = {"uk", "en", "ru", "be", "pl", "sk", "hu", "ro", "de", "fr", "es", "pt"}


def translations(items):
    out = []
    for t in items or ():
        code = t.get("lang_code") or t.get("code")
        word = (t.get("word") or "").strip()
        if code in LANGS and word:
            x = {"l": code, "w": word}
            if t.get("sense"):
                x["s"] = t["sense"][:120]
            out.append(x)
    return out


def main() -> None:
    edition, out = sys.argv[1], Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    files = {}
    seen = 0
    for line in sys.stdin:
        try:
            e = json.loads(line)
        except ValueError:
            continue
        code = e.get("lang_code")
        word = e.get("word")
        if code not in LANGS or not word:
            continue
        seen += 1
        r = {"w": word, "p": e.get("pos", "")}
        tr = translations(e.get("translations"))
        senses = []
        for s in e.get("senses") or ():
            x = {}
            g = s.get("glosses") or s.get("raw_glosses")
            if g:
                x["g"] = g[-1][:160]
            lemma = [f.get("word") for f in (s.get("form_of") or []) + (s.get("alt_of") or []) if f.get("word")]
            if lemma:
                x["f"] = lemma
            st = translations(s.get("translations"))
            if st:
                x["t"] = st
            tags = [t for t in (s.get("tags") or []) if t in ("obsolete", "archaic", "dated", "rare", "vulgar", "offensive", "slang", "derogatory")]
            if tags:
                x["k"] = tags
            if x:
                senses.append(x)
        if senses:
            r["s"] = senses
        if tr:
            r["t"] = tr
        forms = sorted({f["form"] for f in e.get("forms") or () if f.get("form") and "romanization" not in (f.get("tags") or ()) and "table-tags" not in (f.get("tags") or ()) and "inflection-template" not in (f.get("tags") or ())})
        if forms and code != "en":
            r["x"] = forms[:200]
        if code == "en" and not tr and not any("t" in x for x in senses):
            # An English word helps only through its translations
            continue
        f = files.get(code)
        if f is None:
            f = files[code] = gzip.open(out / f"{edition}-{code}.jsonl.gz", "wt", encoding="utf-8")
        f.write(json.dumps(r, ensure_ascii=False, separators=(",", ":")) + "\n")
    for f in files.values():
        f.close()
    print(edition, "entries", seen, file=sys.stderr)


if __name__ == "__main__":
    main()
