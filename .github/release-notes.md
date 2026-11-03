**Писанка 0.22.0**: приватна клавіатура для Android без доступу до інтернету. Тестова версія перед 1.0. Попередню версію краще видалити й встановити цю заново (історія буфера почнеться з нуля).

- 🔒 **Безпечніший буфер:** зображення в історію беруться лише з інших застосунків (ніколи з файлів самої Писанки), задовгі тексти не ламають історію, звіт про збій не містить вашого тексту.
- ↩️ **Менше незворотного:** перед заміною тексту відповіддю ШІ попередній текст кладеться в історію буфера; видалення моделі, шаблонів, замін тексту й зразків стилю питає підтвердження.
- ♿ **Для TalkBack:** підписані кнопки буфера й тегів, повзунки читаються з назвою, стан диктування й копії буфера оголошується.
- 📱 **Чесніше на слабких телефонах:** попередження, якщо для ШІ замало памʼяті; диктування позначене недоступним, якщо процесор його не тягне.
- ℹ️ **«Про застосунок»:** ліцензія GPL-3.0 або новіша, джерела даних, політика приватності ([PRIVACY.md](https://github.com/Marybezu/pysanka/blob/main/PRIVACY.md)). Інтерфейс лише українською й англійською.
- 🧹 Застосунок стрункіший: прибрано зайві переклади й старий код.

📲 Завантажте файл .apk нижче й відкрийте його на телефоні. 🔄 Для оновлень: Obtainium, «Додати застосунок» → `github.com/Marybezu/pysanka`.

---

**Pysanka 0.22.0**: a private keyboard for Android with no internet access. A test version before 1.0. Best to uninstall the previous version and install this one (the clipboard history starts empty).

- Safer clipboard: pictures are taken only from other apps (never Pysanka's own files), very long texts cannot break the history, crash reports contain none of your text.
- Less that cannot be undone: before the AI replaces a text, the previous one goes to the clipboard history; deleting the model, templates, shortcuts or style samples asks first.
- For TalkBack: clipboard and tag buttons are labelled, sliders are read with their names, dictation and clipboard copy states are announced.
- Honest on modest phones: a warning when there is too little memory for the AI; dictation marked unavailable where the processor cannot run it.
- About: GPL-3.0 or later, data sources, privacy policy (PRIVACY.md). The interface is Ukrainian and English only.
- A leaner app: unused translations and old code removed.
