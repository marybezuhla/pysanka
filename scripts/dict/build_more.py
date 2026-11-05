#!/usr/bin/env python3
"""
Pysanka: word lists for the languages added for 1.0 (pl, hu, ro, sk, es, pt, fr, de, be), the same
way as the Ukrainian and Russian ones (build_dicts.py):

- FrequencyWords (Hermit Dave), OpenSubtitles 2018 word counts, CC BY-SA 4.0 (Portuguese: the
  Portuguese and the Brazilian counts together).
- A LibreOffice hunspell dictionary of the language, through the system's libhunspell (the
  dictionaries of Hungarian and German need its full rules): only words it accepts are kept, so
  misspellings in subtitles are left out. A word the dictionary accepts only with a capital
  (German nouns, names) is kept with it. Used only while the lists are made; not part of Pysanka.

Output: <out>/<lang>.json, {"word": frequency 1..255}, the most frequent words, log scale.
Usage: build_more.py <FrequencyWords dir> <LibreOffice dictionaries dir> <out dir> [lang…]
Belarusian has no subtitle counts: see build_be.py.
"""
import ctypes
import json
import math
import re
import sys
from pathlib import Path

LIB = ctypes.CDLL("libhunspell-1.7.so.0")
LIB.Hunspell_create.restype = ctypes.c_void_p
LIB.Hunspell_create.argtypes = [ctypes.c_char_p, ctypes.c_char_p]
LIB.Hunspell_spell.argtypes = [ctypes.c_void_p, ctypes.c_char_p]
LIB.Hunspell_get_dic_encoding.restype = ctypes.c_char_p
LIB.Hunspell_get_dic_encoding.argtypes = [ctypes.c_void_p]


class Hunspell:
    def __init__(self, aff: Path, dic: Path):
        self.h = LIB.Hunspell_create(str(aff).encode(), str(dic).encode())
        self.enc = LIB.Hunspell_get_dic_encoding(self.h).decode()

    def ok(self, word: str) -> bool:
        try:
            return LIB.Hunspell_spell(self.h, word.encode(self.enc)) != 0
        except UnicodeEncodeError:
            return False


# language: (subtitle lists, hunspell dictionaries (any of them accepts), letters, words kept)
LANGS = {
    "pl": (["pl"], ["pl_PL/pl_PL"], "aąbcćdeęfghijklłmnńoóprsśtuwyzźż", 60000),
    "hu": (["hu"], ["hu_HU/hu_HU"], "aábcdeéfghiíjklmnoóöőpqrstuúüűvwxyz", 70000),
    "ro": (["ro"], ["ro/ro_RO"], "aăâbcdefghiîjklmnopqrsștțuvwxyz", 60000),
    "sk": (["sk"], ["sk_SK/sk_SK"], "aáäbcčdďeéfghiíjklĺľmnňoóôpqrŕsštťuúvwxyýzž", 60000),
    "es": (["es"], ["es/es_ES", "es/es_MX", "es/es_AR", "es/es_CO", "es/es_US"], "aábcdeéfghiíjklmnñoópqrstuúüvwxyz", 60000),
    "pt": (["pt", "pt_br"], ["pt_PT/pt_PT", "pt_BR/pt_BR"], "aáâãàbcçdeéêfghiíjklmnoóôõpqrstuúüvwxyz", 60000),
    "fr": (["fr"], ["fr_FR/dictionaries/fr"], "aàâæbcçdeéèêëfghiîïjklmnoôœpqrstuùûüvwxyÿz", 60000),
    "de": (["de"], ["de/de_DE_frami", "de/de_AT_frami", "de/de_CH_frami"], "aäbcdefghijklmnoöpqrsßtuüvwxyz", 70000),
}
# The words of one letter each language has (other single letters in subtitles are initials or
# parts of abbreviations, and would be offered while typing)
ONE = {"pl": "aiouwz", "hu": "aés", "ro": "aeio", "sk": "aikosuvz", "es": "aeouy", "pt": "aàeéoó", "fr": "aày", "de": ""}
# English words the subtitles of other languages have (titles, interjections), taken as names
ENGLISH = {"the", "hi", "he", "she", "mr", "mrs", "ms", "uh", "oh", "ok", "hey", "yes", "no", "you", "and"}
# Romanian subtitles often have the old cedilla letters: the correct comma-below ones instead
FIX = {"ro": {"ş": "ș", "ţ": "ț"}}


def stems(dic: Path) -> set[str]:
    """The words a hunspell dictionary lists as such (before its affixes), as written there."""
    raw = dic.read_bytes()
    enc = "utf-8"
    for line in (dic.with_suffix(".aff")).read_bytes().decode("latin-1").splitlines():
        if line.startswith("SET "):
            enc = line.split()[1].strip()
    out = set()
    for line in raw.decode(enc, errors="ignore").splitlines()[1:]:
        w = line.split("/")[0].split("\t")[0].strip()
        if w:
            out.add(w)
    return out


def build(lang: str, fw: Path, lo: Path) -> dict[str, int]:
    subs, dicts, letters, keep = LANGS[lang]
    hun = [Hunspell(lo / f"{d}.aff", lo / f"{d}.dic") for d in dicts]
    # German: a noun the dictionary lists with its capital («Zeit», «Schule», «Spiel») is taken with
    # it, also when the small word is a verb form or a preposition too; but not one ending in -en,
    # most often typed as the verb («leben», «essen»: a typed word is kept as typed anyway)
    nouns: set[str] = set()
    if lang == "de":
        listed = set().union(*(stems(lo / f"{d}.dic") for d in dicts))
        nouns = {w for w in listed if w[:1].isupper() and (w.lower() not in listed or not w.endswith("en"))}
        # …and never a little word or one typed small nearly always («nicht», «schnell», «zwei»:
        # Pysanka's lists stop_de.txt and lower_de.txt)
        here = Path(__file__).resolve().parent
        small = set()
        for f in ("stop_de.txt", "lower_de.txt"):
            small |= {l.strip().lower() for l in (here / f).read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}
        nouns = {w for w in nouns if w.lower() not in small}
    word_re = re.compile(rf"^[{letters}]+(['’-][{letters}]+)*$")
    raw: dict[str, int] = {}
    for s in subs:
        for line in (fw / "content" / "2018" / s / f"{s}_full.txt").read_text(encoding="utf-8").splitlines():
            word, _, n = line.rpartition(" ")
            word = word.strip().lower().replace("’", "'")
            for a, b in FIX.get(lang, {}).items():
                word = word.replace(a, b)
            if word and n.isdigit() and word_re.match(word):
                raw[word] = raw.get(word, 0) + int(n)
    out: dict[str, int] = {}
    for word, n in sorted(raw.items(), key=lambda kv: -kv[1]):
        if len(out) >= keep:
            break
        if len(word) == 1 and word not in ONE[lang]:
            continue
        cap = word[0].upper() + word[1:]
        if cap in nouns:
            out[cap] = out.get(cap, 0) + n
            continue
        # As written in small letters if the dictionary takes it so; else with a capital (a German
        # noun, a name); else not a word
        if any(h.ok(word) for h in hun):
            out[word] = out.get(word, 0) + n
        elif len(word) > 2 and word not in ENGLISH and any(h.ok(cap) for h in hun):
            # (not a short one: initials and abbreviations; not an English word taken as a name)
            out[cap] = out.get(cap, 0) + n
    counts = sorted(out.items(), key=lambda kv: -kv[1])
    top, low = math.log(counts[0][1]), math.log(max(counts[-1][1], 1))
    return {w: max(1, round(1 + 254 * (math.log(n) - low) / max(top - low, 1e-9))) for w, n in counts}


def main() -> None:
    fw, lo, out = (Path(a) for a in sys.argv[1:4])
    out.mkdir(parents=True, exist_ok=True)
    for lang in sys.argv[4:] or list(LANGS):
        data = build(lang, fw, lo)
        (out / f"{lang}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        print(lang, len(data), list(data)[:12], flush=True)


if __name__ == "__main__":
    main()
