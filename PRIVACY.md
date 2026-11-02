# Приватність Писанки · Pysanka privacy

## Українською

**Коротко: Писанка нічого не збирає й нікуди не надсилає.** У неї немає доступу до інтернету: дозволу на нього немає в застосунку, і збірка зупиняється, якщо він з'явиться.

- **Що ви набираєте**, нікуди не передається й не записується без потреби. Поля пароля, режим інкогніто й поля з позначкою «не вчитися» не потрапляють ні в словник, ні в буфер, ні до ШІ чи диктування.
- **Що зберігається на телефоні:** історія буфера, вивчені слова, заміни тексту, зразки стилю. Усе це зашифровано, ключ в апаратному сховищі Android. Налаштування й недавні емодзі зберігаються звичайним файлом застосунку (їх захищає шифрування телефона).
- **ШІ й диктування** працюють лише на телефоні. Звук розпізнається в пам'яті й одразу затирається; ніде не записується.
- **Дозволи:** вібрація, сповіщення про збій (лише якщо ви ввімкнете), відбиток пальця для відкриття буфера (лише якщо ввімкнете) і мікрофон для диктування (лише якщо ви дозволите самі). Більше нічого: ні контактів, ні фото, ні місцезнаходження.
- **Резервні копії:** Писанка не потрапляє в хмарну копію Android і не переноситься на інший телефон сама. Копію буфера ви робите вручну, під власним паролем; забутий пароль відновити неможливо.
- **Збої:** якщо застосунок впаде, на телефоні лишається короткий технічний звіт (лише назви помилок і місця в коді, без вашого тексту). Ви самі вирішуєте, чи надсилати його.
- **Аналітики, реклами й акаунтів немає.**

Питання: [Issues](https://github.com/Marybezu/pysanka/issues).

## English

**In short: Pysanka collects nothing and sends nothing anywhere.** It has no internet access: the app has no permission for it, and the build fails if one appears.

- **What you type** is not sent or stored without need. Password fields, incognito mode and fields marked "no learning" never reach the dictionary, the clipboard, the AI or dictation.
- **What is stored on the phone:** clipboard history, learned words, text shortcuts, style samples. All of it is encrypted, with the key in Android's hardware-backed keystore. Settings and recent emoji are kept in a plain app file (protected by the phone's own encryption).
- **AI and dictation** run on the phone only. Sound is recognised in memory and wiped at once; it is never written anywhere.
- **Permissions:** vibration, crash notifications (only if you turn them on), the fingerprint to open the clipboard (only if you turn it on) and the microphone for dictation (only if you allow it yourself). Nothing else: no contacts, photos or location.
- **Backups:** Pysanka is left out of Android's cloud backup and never moves to another phone by itself. You make a clipboard copy by hand, with your own password; a forgotten password cannot be recovered.
- **Crashes:** if the app crashes, a short technical report stays on the phone (only error names and code locations, none of your text). You decide whether to send it.
- **No analytics, no ads, no accounts.**

Questions: [Issues](https://github.com/Marybezu/pysanka/issues).
