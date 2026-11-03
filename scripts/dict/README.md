# Word lists for suggestions and swipe

`app/src/main/assets/ime/dict/uk.json`, `en.json`, `ru.json`, and the languages added for 1.0 (`build_more.py`: pl, hu, ro, sk, es, pt, fr, de; `build_be.py`: Belarusian from Belacorpus and two hunspell dictionaries, official and tarask) — the most frequent words with a frequency
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

The languages added for 1.0 (libhunspell must be installed; the dictionaries checked out as above
with `git sparse-checkout set uk_UA ru_RU pl_PL hu_HU ro be_BY sk_SK es pt_PT pt_BR fr_FR de`):

```sh
python3 -I scripts/dict/build_more.py fw lo app/src/main/assets/ime/dict
git clone --depth 1 https://github.com/lidia-m/belacorpus_corpus && git clone --depth 1 https://github.com/375gnu/spell-be-tarask
(cd spell-be-tarask && make be_BY@tarask.aff be_BY@tarask.dic VERSION_NUMBER=0)
python3 -I scripts/dict/build_be.py belacorpus_corpus lo/be_BY spell-be-tarask app/src/main/assets/ime/dict
python3 -I scripts/dict/themes.py dict_uk lo/uk_UA app/src/main/assets/ime/dict lo/ru_RU
python3 -I scripts/dict/extras.py app/src/main/assets/ime/dict
```
