# Licences and third-party material

## Pysanka: GPL-3.0

Pysanka as a whole is distributed under the **GNU General Public License, version 3** (`LICENSE`).
Anyone who shares a changed Pysanka must share its source code under the same licence.

| What | Licence |
|---|---|
| Pysanka's own code: everything in `ua.pysanka` (`app/src/main/kotlin/ua/pysanka`, its tests and resources such as `strings_pysanka.xml`), the build and CI scripts written for Pysanka, the documents | GPL-3.0 |
| Pysanka's changes inside FlorisBoard files (marked with `Pysanka:` comments) | GPL-3.0 |
| Code that comes from FlorisBoard (files under `dev.patrickgold.florisboard`, `lib/`, `libnative/` and others with a FlorisBoard copyright header) | Apache 2.0 (`LICENSE-APACHE`), as before; their headers stay |

Apache 2.0 code may be part of a GPL-3.0 work; the parts from FlorisBoard keep their own licence and
notices, and can still be taken from here under Apache 2.0 (or from FlorisBoard itself). Releases up
to 0.5.1 (and this repository before November 2026) published Pysanka's own code under Apache 2.0;
those copies keep that licence.

## Third-party material

- `app/src/main/assets/ime/dict/uk.json`, `en.json` — word lists for suggestions and swipe, made from
  FrequencyWords by Hermit Dave (https://github.com/hermitdave/FrequencyWords; word counts from
  OpenSubtitles 2018, http://opus.nlpl.eu/OpenSubtitles.php, http://www.opensubtitles.org/),
  licensed CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/). Changed: limited to the
  most frequent words, rescaled to 1–255, Ukrainian words filtered as below and a few respelled
  (2019 spelling). The lists are under CC BY-SA 4.0; how they are made: `scripts/dict/`.
- Words added to those lists by theme (slang, military, medicine…): Pysanka's own lists
  (`scripts/dict/*_uk.txt`, `*_en.txt`, GPL-3.0) and, for medicines and first names, words
  from dict_uk (https://github.com/brown-uk/dict_uk) with their forms from its uk_UA hunspell
  dictionary, GPL 3.0+ / LGPL 2.1+ / MPL 1.1, here under GPL-3.0; made by `scripts/dict/themes.py`.
  The Ukrainian filter uses the uk_UA hunspell dictionary (dict_uk project, as in LibreOffice,
  MPL 1.1) only while the lists are made; it is not part of Pysanka.

- `app/src/main/assets/ime/media/emoji/uk.txt` — Ukrainian emoji names and keywords from the Unicode
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
