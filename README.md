<img src=".github/repo_icon.png" width="96" alt="">

# Писанка · Pysanka

Приватна клавіатура для Android без доступу до інтернету. Набір, свайп, буфер обміну, а за бажанням і локальний ШІ, що редагує текст прямо на телефоні. Інтерфейс українською, англійською, білоруською, польською, словацькою, угорською, румунською, німецькою, французькою, іспанською й португальською; розкладки для багатьох мов. Безкоштовно, без реклами й акаунтів.

> **Версія 1.0.** Завантаження в [Releases](../../releases). Помилки й побажання: [Issues](../../issues).

## Що вміє

- 🔒 **Приватність.** Дозволу на інтернет немає, і збірка зупиняється, якщо він з'явиться. Нічого не потрапляє в хмарну копію чи на інший телефон.
- 📋 **Зашифрований буфер.** Історія тексту й зображень зашифрована (SQLCipher + AES-256, ключ в апаратному сховищі). «Не зберігати паролі» на ваш вибір. Копія буфера під паролем та імпорт із FlorisBoard. Кольорові теги й автотеги (зображення, посилання, номери, коди, пошта). Очищення посилань від стеження. Фото й скріншоти в історію одним дотиком, з розпізнаним текстом. Пошук, шаблони, за бажанням відкриття відбитком.
- ✍️ **Підказки, свайп і виправлення описок 12 мовами:** українська, англійська, білоруська, польська, словацька, угорська, румунська, німецька, французька, іспанська, португальська й російська (щоб легше переходити на українську). Набір без діакритики виправляється сам («dziekuje» → «dziękuję», «fur» → «für»); клавіатура вчиться ваших слів (зашифровано, ніколи з полів пароля чи інкогніто). Після пробілу пропонуються слова, що у вас зазвичай ідуть далі. Точніше влучання на межі клавіш і підлаштування під ваші дотики. Словник знає сленг, військове, державне, бренди, медицину, міста й імена. Хештеги після «#»: одразу кілька з ключових слів допису, без ШІ. Заміна тексту («дд» → «Добрий день!»), типографські лапки «» й апостроф ʼ, пробіл-трекпад, емодзі за словом («кава» → ☕).
- 🌐 **Переклад слів між 12 мовами без інтернету.** Словники з Вікісловника вже в застосунку: кнопка над клавіатурою перекладає слово біля курсора й ставить переклад на його місце, а «Перекласти (Писанка)» є в меню виділеного тексту будь-якого застосунку (вимикається в налаштуваннях). Знаходить і форми слів («ключа» → ключ → key, klucz, Schlüssel).
- ☑️ **Списки, справи й дата** як у нотатках: кнопка над клавіатурою ставить •, 1. чи ☐ на початок рядка, повторний дотик позначає справу ☑; Enter продовжує список. У Joplin, Obsidian і Markor — Markdown, який вони показують своїми чекбоксами. Дата й час мовою клавіатури.
- ⌨️ **Фізична клавіатура** друкує мовою Писанки (Ctrl+Пробіл перемикає), а на планшетах і розкладних телефонах клавіатура розділяється на дві половини.
- 🎨 **Теми в кольорах писанок:** Льон, Віск, Небо, Мак, Смерека, Чорнозем, спокійні для довгого набору; або кольори системи. Висота клавіатури, розмір літер і відступи між клавішами на ваш смак.
- 🔍 **Текст із фото й камери** розпізнається на телефоні.
- 🤖 **Локальний ШІ (Gemma 4), за бажанням.** ШІ-помічник просто в клавіатурі (✦: дії плитками, відповідь замість клавіш): стиль, граматика й пунктуація, підсумок довгих текстів (з перевіркою чисел і назв), переклад, русизми, список, таблиця, нотатки, лист за описом, варіанти відповіді, приховати особисте, «Моїм стилем» (за вашими зашифрованими зразками); «Поділитися → Писанка ШІ» з будь-якого застосунку. Для соцмереж: лічильник знаків, тред, «Спокійніше», версії для кількох мереж у буфер, скільки емодзі, обираєте ви.
- 🎙️ **Диктування на телефоні (Whisper), за бажанням.** Модель уже в застосунку. Звук розпізнається на телефоні й ніде не зберігається; мікрофон лише тоді, коли ви самі його дозволите.

Основа: FlorisBoard, з простішими налаштуваннями й посиленою приватністю. ШІ й диктування працюють лише на телефоні. Модель ШІ ви додаєте самі; без неї клавіатура працює повністю.

## Як встановити

1. Завантажте файл `pysanka-<версія>.apk` з [Releases](../../releases) на телефоні й відкрийте його. Android попросить дозволити встановлення з цього джерела (браузера чи файлового менеджера): дозвольте.
2. Google Play Protect може попередити про невідомого розробника: це звичайне попередження для застосунків не з Google Play. Справжність можна перевірити відбитком сертифіката нижче (наприклад, через AppVerifier).
3. Відкрийте Писанку й пройдіть два кроки: увімкнути клавіатуру й обрати її.
4. Для оновлень зручно Obtainium: «Додати застосунок» → `github.com/Marybezu/pysanka`.

На інший телефон переноситься лише буфер (копією під паролем). Вивчені слова, заміни тексту, зразки стилю й налаштування лишаються на цьому телефоні. Що й де зберігається: [PRIVACY.md](PRIVACY.md).

## Вимоги

- Android 8.0 або новіший, 64-бітний процесор ARM (майже всі телефони останніх років). Файл застосунку ≈100 МБ.
- Диктування: процесор ARMv8.2 (більшість телефонів з 2018 року; у частини дешевих моделей до 2022 року його немає, тоді диктування просто недоступне).
- Локальний ШІ: від 6 ГБ оперативної пам'яті (краще 8 ГБ) і ≈3 ГБ вільного місця для моделі (під час встановлення ненадовго ≈5,5 ГБ, поки завантажений файл ще не видалено).

## Прозорість

- **Основа:** [FlorisBoard](https://github.com/florisboard/florisboard) (Apache 2.0), історію змін збережено. Pysanka є незалежним проєктом.
- **Збірка:** APK збирається з цього коду в GitHub Actions окремо від підпису й підписується лише після схвалення авторки. Контрольні суми залежностей і обгортки Gradle перевіряються.
- **Підпис:** відбиток сертифіката SHA-256 `ea082ca6d8620c201948d08a9572c0b74a75101eb71b2752ceddcb4807d038ce`.
- **Як створено:** код написано за допомогою ШІ (Claude, Anthropic) під керівництвом і з тестуванням авторки.

Kotlin · Jetpack Compose · C++ (whisper.cpp). Ліцензія GPL-3.0 або новіша (GPL-3.0-or-later); код із FlorisBoard лишається під Apache 2.0. Що під якою ліцензією: [NOTICE.md](NOTICE.md).

---

# Pysanka · English

A private keyboard for Android with no internet access. Typing, swipe, clipboard and, if you wish, an on-device AI that edits text right on the phone. Interface in Ukrainian, English, Belarusian, Polish, Slovak, Hungarian, Romanian, German, French, Spanish and Portuguese; layouts for many languages. Free, no ads, no accounts.

> **Version 1.0.** Downloads in [Releases](../../releases). Bugs and wishes: [Issues](../../issues).

## Privacy

- No internet permission; the build fails if one appears. Nothing goes to a cloud backup or to another phone.
- Encrypted clipboard history (SQLCipher + AES-256, hardware-backed key); "Don't keep passwords" is your choice; password-protected clipboard copy and import from FlorisBoard. Coloured and automatic tags. Copied links are cleaned of tracking. Photos and screenshots into the history with one tap, with recognised text. Search, templates, and optionally a fingerprint to open the history.
- Suggestions, swipe and careful typo correction in 12 languages: Ukrainian, English, Belarusian, Polish, Slovak, Hungarian, Romanian, German, French, Spanish, Portuguese and Russian (to make the move to Ukrainian easier). Letters typed without their marks are put right («dziekuje» → «dziękuję», «fur» → «für»); the keyboard learns your words and what usually comes next, kept encrypted and never from password or incognito fields. More accurate taps on the edge between keys, adapting to how you tap. The dictionary knows slang, military, government, brand, medical words, cities and names. Hashtags after «#»: a few from the post's key words at once, no AI. Text replacement, smart quotes and apostrophe, the space bar as a trackpad, emoji for the typed word.
- Optional on-device AI (Gemma 4), as a panel right in the keyboard (✦: actions as tiles, the answer in place of the keys): grammar, style, summaries of long texts (numbers and names checked), translation, Russianisms, list, table, notes, letters, reply options, hiding personal data, «In my style» (from your own encrypted samples); "Share → Pysanka AI" from any app. For social media: a character counter, threads, "Calmer", versions for several networks to the clipboard, as many emoji as you choose.
- Offline translation of words between the 12 languages, from Wiktionary dictionaries in the app: a button above the keyboard translates the word at the cursor and puts the translation in its place; "Translate (Pysanka)" in the text selection menu of any app (can be turned off). Forms of words are found too.
- Lists, to-dos and the date as in note apps: a button above the keyboard puts •, 1. or ☐ at the start of the line, a second tap ticks a to-do ☑, Enter continues the list. Markdown in Joplin, Obsidian and Markor, which show it as their own checkboxes. The date and time in the keyboard's language.
- A physical keyboard types in Pysanka's language (Ctrl+Space switches it); on tablets and foldables the keyboard splits in two halves.
- Themes in pysanka colours, calm for long typing, or the system's colours. Keyboard height, letter size and gaps between keys to your liking.
- On-device text recognition from photos and the camera.
- Optional on-device dictation (Whisper, the model comes with the app): the sound is recognised on the phone and never saved; the microphone only if you allow it yourself.

Built on FlorisBoard, with simpler settings and stronger privacy. AI and dictation run on the phone only. You add the AI model yourself; without it the keyboard works fully.

## Installing

1. Download `pysanka-<version>.apk` from [Releases](../../releases) on the phone and open it. Android asks to allow installing from that source (the browser or file manager): allow it.
2. Google Play Protect may warn about an unknown developer: the usual warning for apps not from Google Play. You can check it is genuine by the certificate fingerprint below (with AppVerifier, for example).
3. Open Pysanka and follow the two steps: turn the keyboard on and choose it.
4. For updates, Obtainium is handy: "Add app" → `github.com/Marybezu/pysanka`.

Only the clipboard moves to another phone (as a password-protected copy). Learned words, text shortcuts, style samples and settings stay on this phone. What is kept where: [PRIVACY.md](PRIVACY.md).

## Requirements

- Android 8.0 or newer, a 64-bit ARM processor (nearly every recent phone). The app is ≈100 MB.
- Dictation: an ARMv8.2 processor (most phones since 2018; some budget models up to 2022 lack it, and then dictation is simply unavailable).
- On-device AI: 6 GB of RAM or more (8 GB is better) and ≈3 GB of free space for the model (≈5.5 GB for a moment while installing, until the downloaded file is deleted).

## Transparency

- **Based on** [FlorisBoard](https://github.com/florisboard/florisboard) (Apache 2.0), with its history kept. Pysanka is an independent project.
- **Build:** the APK is built from this code in GitHub Actions, separately from signing, and is signed only after the author's approval. Dependency and Gradle wrapper checksums are verified.
- **Signing:** certificate SHA-256 `ea082ca6d8620c201948d08a9572c0b74a75101eb71b2752ceddcb4807d038ce`.
- **How it was made:** the code was written with AI (Claude, Anthropic) under the author's direction and testing.

Licence: GPL-3.0 or later (GPL-3.0-or-later); code from FlorisBoard stays under Apache 2.0. What is under which licence, and third-party material: [NOTICE.md](NOTICE.md).
