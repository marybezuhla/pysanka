#!/usr/bin/env python3
"""
Pysanka: the Belarusian word list (app/src/main/assets/ime/dict/be.json). There are no subtitle
counts for Belarusian, so the counts come from Belacorpus (L. F. Mazzitelli, 2021,
https://github.com/Belarusian-Corpus; texts of 1987–2010: fiction, essays, Naša Niva, Novy Čas,
Narodnaja Volja, ARCHE and others; ≈1.5 million words). Only the counts of words are taken.

A word is kept if one of two hunspell dictionaries accepts it: LibreOffice's be_BY (the official
orthography) or spell-be-tarask (Mikalai Udodau, Hleb Valoshka; the classical orthography,
taraškievica, used by independent Belarusian media; CC BY-SA 3.0), so both are typed and offered.

Usage: build_be.py <belacorpus_corpus dir> <be_BY dir> <spell-be-tarask dir> <out dir>
"""
import json
import math
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_more import Hunspell  # noqa: E402

LETTERS = "абвгґдеёжзійклмнопрстуўфхцчшыьэюя"
WORD = re.compile(rf"[{LETTERS}]+(?:['’ʼ-][{LETTERS}]+)*", re.IGNORECASE)
KEEP = 60000
SHORT = {
    "а", "і", "у", "ў", "з", "я", "о", "ж", "на", "не", "да", "за", "па", "ён", "як", "ці", "то", "ну", "аб", "ад",
    "бо", "ды", "мы", "вы", "ты", "ім", "іх", "яе", "ля", "аж", "бы", "ой", "ох", "ах", "эх", "эй", "ну", "но", "ад",
}


def main() -> None:
    corpus, official, tarask, out = (Path(a) for a in sys.argv[1:5])
    hun = [Hunspell(official / "be-official.aff", official / "be-official.dic"),
           Hunspell(tarask / "be_BY@tarask.aff", tarask / "be_BY@tarask.dic")]
    counts: dict[str, int] = {}
    for f in corpus.rglob("*"):
        if not f.is_file():
            continue
        raw = f.read_bytes()
        # The corpus files are UTF-16 (with a BOM); a few may be in the old Windows Cyrillic
        if raw[:2] in (b"\xff\xfe", b"\xfe\xff"):
            text = raw.decode("utf-16", errors="ignore")
        else:
            try:
                text = raw.decode("utf-8")
            except UnicodeDecodeError:
                text = raw.decode("cp1251", errors="ignore")
        # Belarusian apostrophe as typed on the keyboard; the Latin i of some old texts as і
        text = text.replace("’", "'").replace("ʼ", "'").replace("i", "і").replace("I", "І")
        for m in WORD.finditer(text):
            w = m.group(0).lower()
            counts[w] = counts.get(w, 0) + 1
    kept: dict[str, int] = {}
    for w, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        if len(kept) >= KEEP:
            break
        # One or two letters: only the words that are so short (not «п.», «ст.» of abbreviations,
        # letter names «эм», «цэ», initials); a capital only from three letters on
        if len(w) <= 2 and w not in SHORT:
            continue
        if any(h.ok(w) for h in hun):
            kept[w] = kept.get(w, 0) + n
        elif len(w) > 3 and any(h.ok(w[0].upper() + w[1:]) for h in hun):
            W = w[0].upper() + w[1:]
            kept[W] = kept.get(W, 0) + n
    ordered = sorted(kept.items(), key=lambda kv: -kv[1])
    top, low = math.log(ordered[0][1]), math.log(max(ordered[-1][1], 1))
    data = {w: max(1, round(1 + 254 * (math.log(n) - low) / max(top - low, 1e-9))) for w, n in ordered}
    out.mkdir(parents=True, exist_ok=True)
    (out / "be.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print("be", len(data), list(data)[:15])


if __name__ == "__main__":
    main()
