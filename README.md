<img src=".github/repo_icon.png" width="96" alt="">

# Писанка · Pysanka

Приватна клавіатура для Android без доступу до інтернету. Набір, swipe, буфер обміну, а за бажанням — локальний ШІ, що редагує текст прямо на телефоні. Українська й англійська в інтерфейсі, розкладки для багатьох мов. Безкоштовно, без реклами й акаунтів.

> **Статус: у розробці, тестові версії** — у [Releases](../../releases). Помилки й побажання: [Issues](../../issues).

## Що вміє

- 🔒 **Приватність** — дозволу на інтернет немає, і збірка зупиняється, якщо він з'явиться. Нічого не потрапляє в хмарну копію чи на інший телефон.
- 📋 **Зашифрований буфер** — історія тексту й зображень зашифрована (SQLCipher + AES-256, ключ в апаратному сховищі). «Не зберігати паролі» — на ваш вибір. Копія буфера під паролем та імпорт із FlorisBoard. Кольорові теги й автотеги (зображення, посилання, номери, коди, пошта). Очищення посилань від стеження. Фото й скріншоти в історію одним дотиком, з розпізнаним текстом. Пошук, шаблони, за бажанням — відкриття відбитком.
- ✍️ **Підказки й виправлення описок** — українською й англійською; клавіатура вчиться ваших слів (зашифровано, ніколи з полів пароля чи інкогніто). Після пробілу — слова, що у вас зазвичай ідуть далі. Точніше влучання на межі клавіш і підлаштування під ваші дотики. Заміна тексту («дд» → «Добрий день!»), розумні лапки «» й апостроф ʼ, пробіл-трекпад, емодзі за словом («кава» → ☕).
- 🎨 **Теми в кольорах писанок** — Крейда, Віск, Небо, Поділля, Гуцульщина, Чорнозем, спокійні для довгого набору; або кольори системи.
- 🔍 **Текст із фото й камери** — розпізнається на телефоні.
- 🤖 **Локальний ШІ (Gemma 4), за бажанням** — ШІ-помічник просто в клавіатурі (✦ — дії плитками, відповідь замість клавіш): стиль, граматика й пунктуація, підсумок довгих текстів (з перевіркою чисел і назв), переклад, русизми, список, таблиця, нотатки, лист за описом, варіанти відповіді, приховати особисте, «Моїм стилем» (за вашими зашифрованими зразками); «Поділитися → Писанка ШІ» з будь-якого застосунку. Для соцмереж: лічильник знаків, тред, «Спокійніше», версії для кількох мереж у буфер, скільки емодзі — на ваш вибір.
- 🎙️ **Диктування на телефоні (Whisper), за бажанням** — звук розпізнається на телефоні й ніде не зберігається; мікрофон — лише якщо ви самі його дозволите.

Основа — FlorisBoard, з простішими налаштуваннями й посиленою приватністю. ШІ й диктування працюють лише на телефоні й лише після того, як ви самі додасте моделі; без них клавіатура працює повністю.

## Прозорість

- **Основа:** [FlorisBoard](https://github.com/florisboard/florisboard) (Apache 2.0), історію змін збережено. Pysanka — незалежний проєкт.
- **Збірка:** APK збирається з цього коду в GitHub Actions окремо від підпису й підписується лише після схвалення авторки. Контрольні суми залежностей і обгортки Gradle перевіряються.
- **Підпис:** відбиток сертифіката SHA-256 `ea082ca6d8620c201948d08a9572c0b74a75101eb71b2752ceddcb4807d038ce`.
- **Як створено:** код написано за допомогою ШІ (Claude, Anthropic) під керівництвом і з тестуванням авторки.

Kotlin · Jetpack Compose · Rust · C++ (whisper.cpp). Ліцензія GPL-3.0; код із FlorisBoard лишається під Apache 2.0 — що під якою ліцензією, у [NOTICE.md](NOTICE.md).

---

# Pysanka · English

A private keyboard for Android with no internet access. Typing, swipe, clipboard and, if you wish, an on-device AI that edits text right on the phone. Ukrainian and English interface, layouts for many languages. Free, no ads, no accounts.

> **Status: in development, test versions** in [Releases](../../releases). Bugs and wishes: [Issues](../../issues).

## Privacy

- No internet permission; the build fails if one appears. Nothing goes to a cloud backup or to another phone.
- Encrypted clipboard history (SQLCipher + AES-256, hardware-backed key); "Don't keep passwords" is your choice; password-protected clipboard copy and import from FlorisBoard. Coloured and automatic tags. Copied links are cleaned of tracking. Photos and screenshots into the history with one tap, with recognised text. Search, templates, and optionally a fingerprint to open the history.
- Word suggestions and careful typo correction in Ukrainian and English; the keyboard learns your words and what usually comes next, kept encrypted and never from password or incognito fields. More accurate taps on the edge between keys, adapting to how you tap. Text replacement, smart quotes and apostrophe, the space bar as a trackpad, emoji for the typed word.
- Optional on-device AI (Gemma 4), as a panel right in the keyboard (✦: actions as tiles, the answer in place of the keys): grammar, style, summaries of long texts (numbers and names checked), translation, Russianisms, list, table, notes, letters, reply options, hiding personal data, «In my style» (from your own encrypted samples); "Share → Pysanka AI" from any app. For social media: a character counter, threads, "Calmer", versions for several networks to the clipboard, as many emoji as you choose.
- Themes in pysanka colours, calm for long typing, or the system's colours.
- On-device text recognition from photos and the camera.
- Optional on-device dictation (Whisper): the sound is recognised on the phone and never saved; the microphone only if you allow it yourself.

Built on FlorisBoard, with simpler settings and stronger privacy. AI and dictation run on the phone only, and only once you add the models yourself; without them the keyboard works fully.

## Transparency

- **Based on** [FlorisBoard](https://github.com/florisboard/florisboard) (Apache 2.0), with its history kept. Pysanka is an independent project.
- **Build:** the APK is built from this code in GitHub Actions, separately from signing, and is signed only after the author's approval. Dependency and Gradle wrapper checksums are verified.
- **Signing:** certificate SHA-256 `ea082ca6d8620c201948d08a9572c0b74a75101eb71b2752ceddcb4807d038ce`.
- **How it was made:** the code was written with AI (Claude, Anthropic) under the author's direction and testing.

Licence: GPL-3.0; code from FlorisBoard stays under Apache 2.0 — what is under which licence, and third-party material: [NOTICE.md](NOTICE.md).
