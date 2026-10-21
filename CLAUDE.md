# CLAUDE.md

Android-приложение «Быстрый звонок» (`com.callwidget`): виджет с настраиваемыми кнопками,
нажатие звонит на номер (звук идёт в подключённое Bluetooth-устройство). Kotlin, View-based UI
(без Compose), Material 3. Общаться с пользователем на русском.

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

- `ButtonStore`: список `CallButton(name, number)` в SharedPreferences (JSON) + флаг `require_bt`.
  `save()` сам вызывает `CallWidgetProvider.refreshAll()`, так что после изменения данных
  виджет обновлять отдельно не нужно.
- Виджет устроен как коллекция: `GridView` + `CallWidgetService` (RemoteViewsFactory).
  Нажатие: mutable `PendingIntent` template на `CallActivity` + fill-in intent с
  `EXTRA_NUMBER`/`EXTRA_NAME`.
- `CallActivity`: прозрачная (`Theme.Translucent.NoTitleBar`), проверяет Bluetooth-аудио через
  `AudioManager.getDevices` (SCO/BLE headset, без BT-разрешений), запрашивает `CALL_PHONE`,
  делает `ACTION_CALL`. Явно направить звук в BT нельзя (это делает Telecom/дефолтный
  дайлер), поэтому реализована только проверка подключения.
- `MainActivity`: RecyclerView + MaterialAlertDialog. Контакт выбирается через
  `ACTION_PICK` с `Phone.CONTENT_URI` (временный доступ, без READ_CONTACTS).
- `setRemoteAdapter(int, Intent)` deprecated в API 35, предупреждение подавлено намеренно.

## Соглашения

- Строки UI только в `res/values/strings.xml` (на русском).
- Иконки: векторные drawable (Material paths), иконка лаунчера адаптивная (`mipmap-anydpi-v26`).
- Версии зависимостей прописаны прямо в `app/build.gradle.kts` (без version catalog).
- Устройство для проверки не всегда подключено (`adb devices`): если его нет, звонки и виджет
  вживую не проверить, и об этом нужно прямо сказать.
