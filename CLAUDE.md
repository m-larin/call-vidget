# CLAUDE.md

Android-приложение «Быстрый звонок» (`com.callwidget`): виджет с настраиваемыми кнопками,
нажатие звонит на номер. Kotlin, View-based UI (без Compose), Material 3. Общаться с пользователем на русском.

**Приложение работает на автомагнитоле** (Unisoc `sprd ums512`, Android 10), а не на телефоне.
Телефон (две SIM) подключён к магнитоле по Bluetooth; звонок должен уходить через телефон (HFP),
звук идёт в динамики магнитолы. У самой магнитолы есть свой модем без SIM.

## Сборка

- Windows: `build.bat` (по умолчанию `assembleDebug`; аргументы передаются в Gradle, например `build.bat lint`).
  Скрипт сам выставляет JAVA_HOME, ANDROID_HOME и GRADLE_USER_HOME из `%ANDROID_TOOLS%` (по умолчанию `D:\Android`).
- Не запускайте `gradlew` напрямую с системным JAVA_HOME: там JDK 25, а Gradle 8.11 его не поддерживает. Нужен JDK 17 (`D:\Android\jdk17`).
- Из Git Bash: `cmd //c build.bat`. Из PowerShell: `cmd /c build.bat`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; adb: `D:\Android\sdk\platform-tools\adb.exe`.
- На этой машине HTTPS перехватывается (PKIX-ошибки в Java). Лечится через `trustStoreType=Windows-ROOT`:
  для клиента это `GRADLE_OPTS` в `build.bat`, для демона `D:\Android\gradle-home\gradle.properties`.
- Релиз: `build.bat assembleRelease` → `app/build/outputs/apk/release/app-release.apk` (R8 + shrinkResources).
  Подпись берётся из `keystore.properties` в корне проекта (gitignored); ключ и копия этого файла
  лежат в `D:\Android\keys\`. Никогда не коммитьте их и не выводите пароли в чат.
  Проверка подписи: `apksigner.bat verify --print-certs` (из Git Bash, с JAVA_HOME=JDK 17).
- `cmd /c` из PowerShell не находит `build.bat` по относительному пути, указывайте полный.
- `local.properties` машинно-зависим и в git не входит (`sdk.dir=D\:\\Android\\sdk`).
- `.bat` файлы пишите в ASCII с CRLF: cmd ломается на UTF-8 кириллице и LF.

## Архитектура

- `ButtonStore`: список `CallButton(name, number, type, http)` в SharedPreferences (JSON).
  Тип `CALL` (звонок) или `HTTP` (REST-запрос: `HttpRequest` с url, методом, телом, заголовками
  текстом «Имя: значение» построчно и Basic-аутентификацией). Пароль в JSON только как `passwordEnc`:
  AES-256/GCM ключом из Android Keystore (`SecretStore`, без deprecated security-crypto). Открытый
  `password` из старых версий читается и сразу перезаписывается зашифрованным. Ключ не попадает
  в резервную копию: после восстановления пароль не расшифруется (станет пустым).
  Записи без `type` (старые версии) читаются как звонки.
  `save()` сам вызывает `CallWidgetProvider.refreshAll()`, так что после изменения данных
  виджет обновлять отдельно не нужно.
- Виджет без коллекций: в контейнер `grid` (`widget_call`) `CallWidgetProvider` добавляет строки
  (`widget_row`) с кнопками (`widget_item`) через `RemoteViews.addView`. Строки и кнопки
  растягиваются весами, число колонок подбирается по размеру виджета (`onAppWidgetOptionsChanged`).
  У каждой кнопки свой immutable `PendingIntent` (requestCode = позиция): при наличии `CALL_PHONE`
  это broadcast в `CallReceiver`, без него `getActivity` на `CallActivity`. Поэтому виджет
  перестраивается при смене разрешения (`MainActivity.onResume`, после запроса разрешения).
- Звонок: `Dialer.call` → `TelecomManager.placeCall`, экран звонка открывает система.
  Цепочка «виджет → наша activity → `ACTION_CALL`» звонила только со второго нажатия
  (и с заставкой, и без неё), поэтому от неё отказались.
  В 1.2 `placeCall` без аккаунта не звонил вовсе (audioMode → IN_CALL, но в журнале телефона пусто):
  видимо, вызов уходил в модем магнитолы. С 1.3 `Dialer` ищет Bluetooth-аккаунт среди
  `callCapablePhoneAccounts` (нужен `READ_PHONE_STATE`) и передаёт его в `EXTRA_PHONE_ACCOUNT_HANDLE`.
  «Отправить журнал» выводит аккаунты, обработчики `ACTION_CALL`/`ACTION_DIAL` и пакеты BT/телефона.
- `socket failed: EPERM` в HTTP: `INTERNET` добавлен обновлением (1.2), системе нужна перезагрузка.
- `CallActivity`: прозрачная, нужна только чтобы запросить `CALL_PHONE`; затем звонит и закрывается.
  Проверок Bluetooth нет: звук в BT направляет система.
- Кнопка-запрос: `PendingIntent.getActivity` на `HttpActivity` (singleTask, прозрачная) с позицией
  кнопки; параметры берутся из `ButtonStore`. Запрос выполняет `HttpRunner` (OkHttp, таймаут 30 с).
  Заставка: ожидание → «OK» (2xx, закрывается через 1,5 с) или текст ошибки (до 15 с, закрывается тапом).
  HTTP без TLS разрешён (`usesCleartextTraffic`), так как серверы бывают локальными.
- `DebugLog`: события нажатий/звонков пишутся только в logcat (тег `CallWidget`). Меню главного
  экрана «Отправить журнал» выгружает `logcat -d` в `cache/logs/logcat.txt` и шлёт через FileProvider
  (на машине adb нет). Без `READ_LOGS` видны только строки приложения; полный журнал после
  `adb shell pm grant com.callwidget android.permission.READ_LOGS` (на Android 13+ ещё и запрос согласия).
- `MainActivity`: RecyclerView + MaterialAlertDialog. Контакт выбирается через
  `ACTION_PICK` с `Phone.CONTENT_URI` (временный доступ, без READ_CONTACTS).

## Соглашения

- Строки UI только в `res/values/strings.xml` (на русском).
- Иконки: векторные drawable (Material paths), иконка лаунчера адаптивная (`mipmap-anydpi-v26`).
- Версии зависимостей прописаны прямо в `app/build.gradle.kts` (без version catalog).
- Устройство для проверки не всегда подключено (`adb devices`): если его нет, звонки и виджет
  вживую не проверить, и об этом нужно прямо сказать.
