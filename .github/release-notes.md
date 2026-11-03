**Писанка 0.24.0**: приватна клавіатура для Android без доступу до інтернету. Тестова версія перед 1.0.

- ✍️ **Свайп розпізнає весь рух.** Раніше слово часто бралося з половини шляху («подивись» ставало «позбутися»). Тепер завжди з повного руху, а слова при швидких свайпах підряд ставляться в правильному порядку.
- #️⃣ **Миттєві хештеги без ШІ:** одразу після «#» перша підказка дає до 4 хештегів із ключових слів допису («#клавіатура #свайп»), один дотик ставить усі. ШІ-дію «Хештеги» прибрано: вона була повільна й неточна.
- 📏 **Висота клавіатури й відступи між клавішами** у «Вигляді»: висота 80–130 %, відступи «Звичайні» або «Просторі», коли клавіші стоять окремо.
- 🗂 **Розділи налаштувань відкриваються по одному.**
- 🙂 **Емодзі до слів точніші:** «мені» більше не дає 🤮, а на «дім» першим іде 🏠.
- 👆 **«Забути мої дотики»** у «Наборі»: клавіатура заново вчиться, куди ви влучаєте.
- 🔒 **Приватність:**
  - хештеги вчаться за тими самими правилами, що й слова;
  - текст із фото, відкритого з приватного поля, не потрапляє в історію буфера;
  - ШІ з «Поділитися» завжди враховує «Заборонити знімки екрана».
- ⌨️ **Фізична клавіатура:** англійська поверх української розкладки дає @ на Shift+2; пробіл вчить слова.
- Виправлено:
  - заміна слова посеред тексту могла стерти текст після нього;
  - після Enter ще пропонувалися хештеги.
- Тексти українською й англійською вичищено: друкарські помилки, кальки, однакові назви скрізь.

📲 Завантажте файл .apk нижче й відкрийте його на телефоні. 🔄 Для оновлень: Obtainium, «Додати застосунок» → `github.com/Marybezu/pysanka`.

---

**Pysanka 0.24.0**: a private keyboard for Android with no internet access. A test version before 1.0.

- Swipe reads the whole path. Before, the word was often taken from half of it; now always from the full swipe, and quick swipes in a row come out in order.
- Instant hashtags without AI: right after «#» the first suggestion is up to 4 hashtags from the post's key words; one tap puts them all. The slow, imprecise AI hashtag action is gone.
- Keyboard height (80–130 %) and gaps between keys (usual or wide) in the Look settings.
- Settings blocks open one at a time.
- Better emoji for words.
- «Forget my touches» resets the learned tap offset.
- Privacy: hashtags are learned under the same rules as words; text from a photo opened in a private field stays out of the clipboard history; the AI from Share always respects «Block screenshots».
- Physical keyboard: English over a Ukrainian system layout types US signs; Space learns words.
- Fixed: replacing a word mid-text could delete text after it; hashtags were offered after Enter.
- Ukrainian and English texts cleaned up.
