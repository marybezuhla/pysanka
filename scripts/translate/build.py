#!/usr/bin/env python3
"""
Pysanka: the offline translation dictionaries (app/src/main/assets/ime/translate/), made from the
Wiktionary data that extract.py kept (wiktextract, https://kaikki.org; Wiktionary's text is
CC BY-SA 4.0 and GFDL, so are these files).

A "meaning" joins the words that say the same thing in several languages:
- a translation table of a Wiktionary (the English one's "key: device to open a lock" → ключ,
  klucz, Schlüssel…), from every edition taken;
- a word of another language explained in a Wiktionary in short (Ukrainian «ключ» in the English
  one: "key", "wrench", "clef") – the explanation is the translation.
Meanings with the same words are one. The more languages a meaning has, the more basic it is: it
comes first.

Files (UTF-8, one entry a line, sorted, so the app finds a line by binary search without reading
everything into objects):
- meanings.txt: line N is meaning N: "<language>:<headword>\t<what it means>";
- <lang>.words.txt: "<word>\t<meaning ids>" (word in small letters, without stress marks);
- <lang>.forms.txt: "<form>\t<word>|<word>" (an inflected form and the words it is a form of);
- <lang>.meanings.txt: "<meaning id>\t<word>|<word>" (the words of that language, commonest first).
A meaning id is 4 (or 5, for more than 1.6 million) characters of base 36, so the order of the text is the order of the numbers.

Forms are also found with the LibreOffice hunspell dictionaries (the same as for the keyboard's
word lists, see scripts/dict), for the words of the keyboard's lists: «ключами» → «ключ».

Usage: python3 -I scripts/translate/build.py <wikt-data dir> app/src/main/assets [<LibreOffice dictionaries dir>]
"""
import ctypes
import gzip
import json
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path

LANGS = ("uk", "en", "ru", "be", "pl", "sk", "hu", "ro", "de", "fr", "es", "pt")
MAX_WORDS = 6  # words of one language in one meaning
MAX_IDS = 48  # meanings of one word
DROP_TAGS = {"obsolete", "archaic"}
APOSTROPHES = str.maketrans({"’": "'", "ʼ": "'", "`": "'", "ʹ": "'"})
STRESS = {"́", "̀"}


def clean(w: str) -> str:
    """The word as shown: without stress marks, one kind of apostrophe, single spaces."""
    w = unicodedata.normalize("NFD", w.strip().translate(APOSTROPHES))
    w = "".join(c for c in w if c not in STRESS)
    return re.sub(r"\s+", " ", unicodedata.normalize("NFC", w))


def key(w: str) -> str:
    return clean(w).lower()


GOOD = re.compile(r"^[^\d()\[\]{}<>=/\\|#@*_~^%$&+]+$")


def usable(w: str) -> bool:
    return 0 < len(w) <= 40 and w.count(" ") <= 3 and GOOD.match(w) is not None and not w.startswith(("-", "'")) and not w.endswith("-")


def gloss_words(g: str) -> list[str]:
    """A short explanation as translations ("wrench, spanner" → two words); a long one gives none."""
    g = re.sub(r"\([^)]*\)", "", g).strip().rstrip(".;:")
    if g.lower().startswith(("to ", "a ", "an ", "the ")):
        # "to go" is a translation; the article of "a key" is not part of the word
        g = g if g.lower().startswith("to ") else g.split(" ", 1)[1]
    out = []
    for part in re.split(r"[,;]", g):
        part = part.strip()
        if part.lower().startswith("to ") and len(part.split()) <= 4:
            part = part[3:]
        if part and len(part.split()) <= 3 and usable(part):
            out.append(part)
    return out


# The hunspell dictionary each language's forms are found with
HUNSPELL = {
    "uk": "uk_UA/uk_UA", "ru": "ru_RU/ru_RU", "be": "be_BY/be-official", "pl": "pl_PL/pl_PL",
    "sk": "sk_SK/sk_SK", "hu": "hu_HU/hu_HU", "ro": "ro/ro_RO", "de": "de/de_DE_frami",
    "fr": "fr_FR/dictionaries/fr", "es": "es/es_ES", "pt": "pt_PT/pt_PT",
}


class Stemmer:
    def __init__(self, base: Path):
        self.lib = ctypes.CDLL("libhunspell-1.7.so.0")
        self.lib.Hunspell_create.restype = ctypes.c_void_p
        self.lib.Hunspell_create.argtypes = [ctypes.c_char_p, ctypes.c_char_p]
        self.lib.Hunspell_get_dic_encoding.restype = ctypes.c_char_p
        self.lib.Hunspell_get_dic_encoding.argtypes = [ctypes.c_void_p]
        self.lib.Hunspell_stem.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.POINTER(ctypes.c_char_p)), ctypes.c_char_p]
        self.lib.Hunspell_free_list.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.POINTER(ctypes.c_char_p)), ctypes.c_int]
        self.h = self.lib.Hunspell_create(f"{base}.aff".encode(), f"{base}.dic".encode())
        self.enc = self.lib.Hunspell_get_dic_encoding(self.h).decode()

    def stems(self, word: str) -> list[str]:
        try:
            b = word.encode(self.enc)
        except UnicodeEncodeError:
            return []
        lst = ctypes.POINTER(ctypes.c_char_p)()
        n = self.lib.Hunspell_stem(self.h, ctypes.byref(lst), b)
        out = [lst[i].decode(self.enc, "replace") for i in range(n)]
        self.lib.Hunspell_free_list(self.h, ctypes.byref(lst), n)
        return out


def read(path: Path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            yield json.loads(line)


def main() -> None:
    data, assets = Path(sys.argv[1]), Path(sys.argv[2])
    lo = Path(sys.argv[3]) if len(sys.argv) > 3 else None
    out = assets / "ime" / "translate"
    out.mkdir(parents=True, exist_ok=True)
    freq, raw = {}, {}
    for lang in LANGS:
        p = assets / "ime" / "dict" / f"{lang}.json"
        raw[lang] = json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}
        freq[lang] = {}
        for k, v in raw[lang].items():
            freq[lang][k.lower()] = max(v, freq[lang].get(k.lower(), 0))

    # meaning: hash of its (lang, key) pairs → (head, gloss, ((lang, shown words), …))
    meanings: dict[int, tuple] = {}
    forms: dict[str, dict[str, set[str]]] = {l: defaultdict(set) for l in LANGS}

    def common(l: str, w: str) -> bool:
        k = key(w)
        return k in freq[l] or (" " in k and all(p in freq[l] for p in k.split(" ")))

    def add(head: str, gloss: str, words: dict[str, list[str]]) -> None:
        # Only words people use (the keyboard's word list has them, or every part of an expression):
        # all of Wiktionary would make the app hundreds of megabytes bigger
        words = {l: [clean(w) for w in ws if usable(clean(w)) and common(l, clean(w))] for l, ws in words.items()}
        words = {l: list(dict.fromkeys(ws)) for l, ws in words.items() if ws}
        if len(words) < 2:
            return
        sig = hash(tuple(sorted((l, key(w)) for l, ws in words.items() for w in ws)))
        if sig not in meanings:
            meanings[sig] = (head, gloss[:100], tuple((l, tuple(ws)) for l, ws in words.items()))

    for path in sorted(data.glob("*-*.jsonl.gz")):
        edition, lang = path.name.split(".")[0].split("-")
        if lang not in LANGS or edition not in LANGS:
            continue
        print(path.name, file=sys.stderr)
        for e in read(path):
            w = e["w"]
            if not usable(clean(w)):
                continue
            senses = e.get("s", [])
            for s in senses:
                for lemma in s.get("f", ()):
                    if key(lemma) != key(w) and key(w) in freq[lang]:
                        forms[lang][key(w)].add(key(lemma))
            for f in e.get("x", ()):
                # Only forms people type (in the keyboard's word list): all of them would not fit in memory
                if key(f) != key(w) and key(f) in freq[lang]:
                    forms[lang][key(f)].add(key(w))
            if lang == edition:
                # Translation tables: grouped by the meaning they are for
                first = next((s["g"] for s in senses if "g" in s and "f" not in s), "")
                groups: dict[str, dict[str, list[str]]] = defaultdict(lambda: defaultdict(list))
                for t in e.get("t", ()):
                    groups[t.get("s", "")][t["l"]].append(t["w"])
                for s in senses:
                    if DROP_TAGS & set(s.get("k", ())):
                        continue
                    for t in s.get("t", ()):
                        groups[s.get("g", "")][t["l"]].append(t["w"])
                for sense, ws in groups.items():
                    ws[lang].insert(0, w)
                    add(f"{lang}:{clean(w)}", sense or first, ws)
            else:
                # A word explained in short in another language's Wiktionary
                for s in senses:
                    if "g" not in s or "f" in s or DROP_TAGS & set(s.get("k", ())):
                        continue
                    ws = gloss_words(s["g"])
                    if ws:
                        add(f"{lang}:{clean(w)}", s["g"], {lang: [w], edition: ws})

    # Most languages first; then the commoner words
    def weight(item):
        sig, (head, gloss, words) = item
        common = sum(freq[l].get(key(ws[0]), 0) > 0 for l, ws in words)
        return (-len(words), -common, head)

    ordered = sorted(meanings.items(), key=weight)
    print("meanings", len(ordered), file=sys.stderr)
    width = 4 if len(ordered) < 36 ** 4 else 5

    def b36(n: int) -> str:
        s = ""
        for _ in range(width):
            n, r = divmod(n, 36)
            s = "0123456789abcdefghijklmnopqrstuvwxyz"[r] + s
        return s

    index: dict[str, dict[str, list[int]]] = {l: defaultdict(list) for l in LANGS}
    per_lang: dict[str, list[str]] = {l: [] for l in LANGS}
    with open(out / "meanings.txt", "w", encoding="utf-8") as f:
        for i, (sig, (head, gloss, words)) in enumerate(ordered):
            f.write(f"{head}\t{clean(gloss)[:100]}\n")
            for l, ws in words:
                ws = sorted(ws, key=lambda x: -freq[l].get(key(x), 0))[:MAX_WORDS]
                per_lang[l].append(f"{b36(i)}\t{'|'.join(ws)}")
                for x in ws:
                    k = key(x)
                    if len(index[l][k]) < MAX_IDS:
                        index[l][k].append(i)
                    if l in ("ru", "be") and "ё" in k:
                        # Written without ё as often as with it
                        k2 = k.replace("ё", "е")
                        if len(index[l][k2]) < MAX_IDS:
                            index[l][k2].append(i)

    if lo is not None:
        for l, d in HUNSPELL.items():
            if not (lo / f"{d}.dic").exists():
                print("no hunspell for", l, file=sys.stderr)
                continue
            st = Stemmer(lo / d)
            n = 0
            for word in raw[l]:
                w = key(word)
                for stem in st.stems(word):
                    k = key(stem)
                    if k != w and k in index[l]:
                        forms[l][w].add(k)
                        n += 1
            print(l, "hunspell forms", n, file=sys.stderr)

    for l in LANGS:
        (out / f"{l}.meanings.txt").write_text("\n".join(per_lang[l]) + "\n", encoding="utf-8")
        words = index[l]
        (out / f"{l}.words.txt").write_text(
            "\n".join(f"{k}\t{','.join(b36(i) for i in ids)}" for k, ids in sorted(words.items())) + "\n", encoding="utf-8")
        # Forms of the words that have meanings; for a big list, only the forms people use (in the
        # word list of the keyboard) – the rest are looked up by their dictionary form
        fl = []
        for form, lemmas in sorted(forms[l].items()):
            lemmas = sorted(x for x in lemmas if x in words)
            if not lemmas or form in words or "\t" in form:
                continue
            fl.append(f"{form}\t{'|'.join(lemmas[:4])}")
        if len(fl) > 300_000 and freq[l]:
            fl = [x for x in fl if x.split("\t")[0] in freq[l]]
        (out / f"{l}.forms.txt").write_text("\n".join(fl) + "\n", encoding="utf-8")
        print(l, "words", len(words), "forms", len(fl), "meanings", len(per_lang[l]), file=sys.stderr)


if __name__ == "__main__":
    main()
