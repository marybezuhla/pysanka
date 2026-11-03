# Licences and third-party material

## Pysanka: GPL-3.0-or-later

Pysanka as a whole is distributed under the **GNU General Public License, version 3 or (at your
option) any later version** (SPDX: `GPL-3.0-or-later`; the text of version 3 is in `LICENSE`).
Anyone who shares a changed Pysanka must share its source code under the same licence.

| What | Licence |
|---|---|
| Pysanka's own code: everything in `ua.pysanka` (`app/src/main/kotlin/ua/pysanka`, its tests and resources such as `strings_pysanka.xml`), the build and CI scripts written for Pysanka, the documents | GPL-3.0-or-later |
| Pysanka's changes inside FlorisBoard files (marked with `Pysanka:` comments) | GPL-3.0-or-later |
| Code that comes from FlorisBoard (files under `dev.patrickgold.florisboard`, `lib/` and others with a FlorisBoard copyright header) | Apache 2.0 (`LICENSE-APACHE`), as before; their headers stay |

Apache 2.0 code may be part of a GPL-3.0-or-later work; the parts from FlorisBoard keep their own licence and
notices, and can still be taken from here under Apache 2.0 (or from FlorisBoard itself). Releases up
to 0.5.1 (and this repository before November 2026) published Pysanka's own code under Apache 2.0;
those copies keep that licence.

## Third-party material

- `app/src/main/assets/ime/dict/uk.json`, `en.json`, `ru.json` — word lists for suggestions and swipe, made from
  FrequencyWords by Hermit Dave (https://github.com/hermitdave/FrequencyWords; word counts from
  OpenSubtitles 2018, http://opus.nlpl.eu/OpenSubtitles.php, http://www.opensubtitles.org/),
  licensed CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/). Changed: limited to the
  most frequent words, rescaled to 1–255, Ukrainian words filtered as below and a few respelled
  (2019 spelling). The lists are under CC BY-SA 4.0; how they are made: `scripts/dict/`.
- Words added to those lists by theme (slang, military, medicine…): Pysanka's own lists
  (`scripts/dict/*_uk.txt`, `*_en.txt`, GPL-3.0-or-later) and, for medicines and first names, words
  from dict_uk (https://github.com/brown-uk/dict_uk) with their forms from its uk_UA hunspell
  dictionary, GPL 3.0+ / LGPL 2.1+ / MPL 1.1, here under GPL-3.0; made by `scripts/dict/themes.py`.
  The Ukrainian filter uses the uk_UA hunspell dictionary (dict_uk project, as in LibreOffice,
  MPL 1.1) only while the lists are made; it is not part of Pysanka. The Russian list is filtered
  the same way with LibreOffice's ru_RU hunspell dictionary (Copyright (c) 1997-2008, Alexander I.
  Lebedev; BSD-style licence, https://github.com/LibreOffice/dictionaries/tree/master/ru_RU), which
  also gives the forms of the words added by theme (`scripts/dict/*_ru.txt`, GPL-3.0-or-later); the
  dictionary itself is not part of Pysanka.

- `app/src/main/assets/ime/dict/pl.json`, `hu.json`, `ro.json`, `sk.json`, `es.json`, `pt.json`, `fr.json`,
  `de.json` — word lists made the same way from FrequencyWords (OpenSubtitles 2018, CC BY-SA 4.0; for
  Portuguese the Portuguese and Brazilian counts together), under CC BY-SA 4.0. Only words that a
  LibreOffice hunspell dictionary of the language accepts are kept (https://github.com/LibreOffice/dictionaries:
  pl_PL, hu_HU, ro, sk_SK, es, pt_PT and pt_BR, fr_FR, de; each under its own free licence, see its README
  there); the dictionaries are used only while the lists are made and are not part of Pysanka
  (`scripts/dict/build_more.py`).
- `app/src/main/assets/ime/dict/be.json` — the Belarusian word list: word counts from Belacorpus
  (L. F. Mazzitelli, 2021, https://github.com/Belarusian-Corpus, texts at
  https://github.com/lidia-m/belacorpus_corpus; only the counts of words are used, no text is included),
  words kept if LibreOffice's be_BY dictionary (official orthography) or spell-be-tarask
  (https://github.com/375gnu/spell-be-tarask, classical orthography, M. Udodau, H. Valoshka,
  CC BY-SA 3.0) accepts them; the list is under CC BY-SA 4.0 (`scripts/dict/build_be.py`).
- Words added to all these lists by theme (everyday and digital words, chat words, places, services,
  Ukrainian things) and the lists of swear words and little words: Pysanka's own lists
  (`scripts/dict/*_<language>.txt`, GPL-3.0-or-later); `assets/ime/dict/extras.json` is made from them.
- `assets/ime/keyboard/.../jcuken_belarusian.json` and the `be` popups: Pysanka's Belarusian layout
  (GPL-3.0-or-later), made after FlorisBoard's Russian one (Apache 2.0).
- `app/src/main/assets/ime/translate/` — the offline translation dictionaries, made from Wiktionary
  (https://www.wiktionary.org/; the English, German, French, Russian, Polish, Spanish and Portuguese
  editions; text by the Wiktionary contributors, CC BY-SA 4.0 and GFDL) through the wiktextract
  extracts of kaikki.org (Tatu Ylonen, https://github.com/tatuylonen/wiktextract). Changed: only the
  words and translations are kept (no explanations), limited to the words of the keyboard's lists,
  joined by meaning, with the forms of words from the LibreOffice hunspell dictionaries (used only while
  the files are made). The files are under CC BY-SA 4.0; how they are made: `scripts/translate/`.
- `app/src/main/assets/ime/media/emoji/uk.txt`, `ru.txt`, `pl.txt`, `hu.txt`, `ro.txt`, `sk.txt`, `be.txt` — emoji names and keywords in those languages from the Unicode
  Common Locale Data Repository (CLDR) v48, © Unicode, Inc., Unicode License v3
  (https://www.unicode.org/license.txt); made by `scripts/emoji_uk.py`. The other emoji files come
  from FlorisBoard, made from the same CLDR data.

- Pysanka is based on **FlorisBoard** by The FlorisBoard Contributors
  (https://github.com/florisboard/florisboard), Apache License 2.0. The full history of FlorisBoard
  is kept in this repository; files from FlorisBoard keep their copyright headers. Pysanka is an
  independent project and is not affiliated with or endorsed by FlorisBoard.
- Libraries used by FlorisBoard (AndroidX, Kotlin, Jetpack Compose and others) are used as
  dependencies under their own licences; the in-app "Open-source licences" screen lists them.
- `app/src/main/assets/ocr/*.onnx` and `*_dict.txt` — PP-OCRv5 mobile text detection and East Slavic /
  Latin text recognition models by PaddlePaddle (PaddleOCR), Apache License 2.0, converted to ONNX
  unchanged in weights (see `SOURCES.json` and `scripts/ocr_models.py`).
- LiteRT-LM (Google, Apache 2.0) runs the AI editor's model. The Gemma model is not included; the
  owner downloads it under its own terms, and only the exact published file is accepted.
- `lib/whisper/src/main/cpp/whisper.cpp` — whisper.cpp v1.9.5 and ggml (The ggml authors, MIT, its
  `LICENSE` alongside), only the files the phone build needs, unchanged. It runs dictation.
- `app/src/main/assets/voice/ggml-base-q5_1.bin` — the Whisper base model (OpenAI, MIT,
  https://github.com/openai/whisper) in whisper.cpp's 5-bit format, unchanged; source and SHA-256 in
  `SOURCE.md` and `SHA256SUMS` alongside.
- ONNX Runtime (Microsoft, MIT), Bouncy Castle (MIT-style),
  SQLCipher for Android (BSD-style) are used as dependencies under their own licences.
