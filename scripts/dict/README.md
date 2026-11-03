# Word lists for suggestions and swipe

`app/src/main/assets/ime/dict/uk.json`, `en.json` and `ru.json` — the most frequent words with a frequency
1..255, made by `build_dicts.py` from:

- **FrequencyWords** by Hermit Dave (https://github.com/hermitdave/FrequencyWords), word counts
  from OpenSubtitles 2018 (http://opus.nlpl.eu/OpenSubtitles.php, http://www.opensubtitles.org/),
  content under CC BY-SA 4.0. The lists made from it are under CC BY-SA 4.0 too.
- Ukrainian only: the Ukrainian hunspell dictionary of the dict_uk project
  (https://github.com/brown-uk/dict_uk, uk_UA.dic/.aff as in LibreOffice; GPL 3.0+ / LGPL 2.1+ /
  MPL 1.1), used only to keep Ukrainian words (no Russian words or misspellings from subtitles).
- Russian only: LibreOffice's ru_RU hunspell dictionary (https://github.com/LibreOffice/dictionaries,
  ru_RU), likewise only to keep real words; words with «ё» are kept also as typed with «е».

- **Words by theme**, added to the same lists by `themes.py` (run after `build_dicts.py`):
  Pysanka's own lists here (`everyday_uk`, `slang_*`, `military_uk`, `state_uk`, `brands_*`, `medical_uk`,
  `places_uk`; GPL-3.0-or-later; no Russian words, no swearing) and dict_uk's lists of medicines and first
  names (https://github.com/brown-uk/dict_uk), with forms from the uk_UA hunspell dictionary.
  `themes.py` also takes the Russian words (`STOP` in `build_dicts.py`) out of `uk.json`:

  ```sh
  git clone --depth 1 --filter=blob:none --sparse https://github.com/brown-uk/dict_uk && (cd dict_uk && git sparse-checkout set data/dict)
  python3 -I scripts/dict/themes.py dict_uk lo/uk_UA app/src/main/assets/ime/dict lo/ru_RU
  ```

```sh
git clone --depth 1 https://github.com/hermitdave/FrequencyWords fw
git clone --depth 1 --filter=blob:none --sparse https://github.com/LibreOffice/dictionaries lo && (cd lo && git sparse-checkout set uk_UA ru_RU)
python3 -I scripts/dict/build_dicts.py fw lo/uk_UA app/src/main/assets/ime/dict lo/ru_RU
```
