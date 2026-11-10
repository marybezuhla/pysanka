# Приватність Писанки · Pysanka privacy

## Українською

**Коротко: Писанка нічого не збирає й нікуди не надсилає.** У неї немає доступу до інтернету: дозволу на нього немає в застосунку, і збірка зупиняється, якщо він з'явиться.

- **Що ви набираєте**, нікуди не передається й не записується без потреби. Поля пароля, режим інкогніто й поля з позначкою «не вчитися» не потрапляють ні в словник, ні до ШІ чи диктування, ні в історію буфера (скопійоване з поля пароля потрапить туди, лише якщо ви самі вимкнете «Не зберігати паролі»).
- **Що зберігається на телефоні:** історія буфера, вивчені слова, заміни тексту, зразки стилю. Усе це зашифровано, ключ в апаратному сховищі Android. Налаштування й недавні емодзі зберігаються звичайним файлом застосунку (їх захищає шифрування телефону).
- **ШІ й диктування** працюють лише на телефоні. Звук розпізнається в пам'яті й затирається, щойно стає непотрібним; ніде не записується.
- **Моделі ШІ й диктування** ви завантажуєте самі у своєму браузері з Hugging Face: цей сайт бачить адресу телефону, з якої завантажують файл. Сама Писанка в інтернет не ходить.
- **Диктування іншим застосунком** (якщо ви його виберете): Писанка лише перемикає на його голосову клавіатуру, а звук і текст обробляє той застосунок за своїми правилами. У полях пароля й інкогніто Писанка не перемикає.
- **Читання вголос** працює лише на телефоні: текст, який ви даєте прочитати (або який інші застосунки дають Писанці як голосу телефону), перетворюється на звук у пам'яті й ніде не записується. Голоси ви завантажуєте самі у браузері: Тетяну з GitHub (випуск Писанки v1.0.0), Cori з Hugging Face; ці сайти бачать адресу телефону. У сповіщенні фонового читання текст не показується.
- **Дозволи:** вібрація, сповіщення про збій (лише якщо ви ввімкнете), відбиток пальця для відкриття буфера (лише якщо ввімкнете), мікрофон для диктування (лише якщо ви дозволите самі) і фонове відтворення для читання вголос (Android вимагає вказати його для всіх; працює, лише коли ви ввімкнете «Читати у фоні»). Більше нічого: ні контактів, ні фото, ні місцезнаходження.
- **Резервні копії:** Писанка не потрапляє в хмарну копію Android і не переноситься на інший телефон сама. Копію буфера ви робите вручну, під власним паролем; забутий пароль відновити неможливо.
- **Збої:** якщо застосунок впаде, на телефоні лишається короткий технічний звіт (лише назви помилок і місця в коді, без вашого тексту). Ви самі вирішуєте, чи надсилати його.
- **Аналітики, реклами й акаунтів немає.**

Питання: [Issues](https://github.com/marybezuhla/pysanka/issues).

## English

**In short: Pysanka collects nothing and sends nothing anywhere.** It has no internet access: the app has no permission for it, and the build fails if one appears.

- **What you type** is not sent or stored without need. Password fields, incognito mode and fields marked "no learning" never reach the dictionary, the AI, dictation or the clipboard history (a copy from a password field gets there only if you turn off "Don't keep passwords" yourself).
- **What is stored on the phone:** clipboard history, learned words, text shortcuts, style samples. All of it is encrypted, with the key in Android's hardware-backed keystore. Settings and recent emoji are kept in a plain app file (protected by the phone's own encryption).
- **AI and dictation** run on the phone only. Sound is recognised in memory and wiped as soon as it is no longer needed; it is never written anywhere.
- **The AI and dictation models** are downloaded by you, in your browser, from Hugging Face: that site sees the address of the phone that downloads the file. Pysanka itself never goes online.
- **Dictating with another app** (if you choose it): Pysanka only switches to its voice keyboard; the sound and the text are handled by that app under its own rules. Pysanka does not switch in password or incognito fields.
- **Reading aloud** runs on the phone only: the text you give it to read (or that other apps hand Pysanka as the phone's voice) becomes sound in memory and is never written anywhere. You download the voices yourself in the browser: Tetiana from GitHub (Pysanka's v1.0.0 release), Cori from Hugging Face; those sites see the phone's address. The background-reading notification never shows the text.
- **Permissions:** vibration, crash notifications (only if you turn them on), the fingerprint to open the clipboard (only if you turn it on), the microphone for dictation (only if you allow it yourself) and background playback for reading aloud (Android has it declared for everyone; it works only when you turn on «Read in the background»). Nothing else: no contacts, photos or location.
- **Backups:** Pysanka is left out of Android's cloud backup and never moves to another phone by itself. You make a clipboard copy by hand, with your own password; a forgotten password cannot be recovered.
- **Crashes:** if the app crashes, a short technical report stays on the phone (only error names and code locations, none of your text). You decide whether to send it.
- **No analytics, no ads, no accounts.**

Questions: [Issues](https://github.com/marybezuhla/pysanka/issues).
