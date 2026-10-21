# Быстрый звонок (CallWidget)

Android-приложение с виджетом на рабочем столе. На виджете кнопки, которые вы настраиваете
в приложении (имя и номер). Нажатие на кнопку сразу звонит на номер. Если подключено
Bluetooth-устройство для звонков (гарнитура, автомобиль), система выводит разговор на него.

## Возможности

- Список кнопок: добавление, изменение, удаление, порядок (стрелки ▲▼).
- Номер можно ввести вручную или выбрать из контактов (разрешение на контакты не нужно).
- Переключатель «Звонить только при подключённом Bluetooth» (по умолчанию включён):
  если гарнитура или машина не подключены, звонок не выполняется.
- Виджет растягивается, число колонок подстраивается под ширину.

Минимальная версия Android: 8.0 (API 26), target/compile SDK: 35.

## Что нужно для сборки

| Инструмент   | Версия                                                                  |
|--------------|-------------------------------------------------------------------------|
| JDK          | 17 (Gradle 8.11 не работает на JDK 25)                                  |
| Android SDK  | `platforms;android-35`, `build-tools;35.0.0`, `platform-tools`          |
| Gradle       | 8.11.1, скачивается сам через wrapper (`gradlew`)                       |
| AGP / Kotlin | 8.7.3 / 2.0.21                                                          |

### Вариант 1: Android Studio

Откройте папку проекта в Android Studio. Она сама найдёт SDK, создаст `local.properties`
и подтянет зависимости. Запуск: кнопка **Run**.

### Вариант 2: командная строка (Windows, без Android Studio)

1. Создайте папку с инструментами (по умолчанию `D:\Android`) такой структуры:
   ```
   D:\Android\
     jdk17\          JDK 17 (например, Temurin: https://adoptium.net)
     sdk\            Android SDK
       cmdline-tools\latest\   содержимое commandlinetools-win-*.zip
     gradle-home\    кэш Gradle (создаётся сам)
   ```
2. Установите пакеты SDK:
   ```bat
   set JAVA_HOME=D:\Android\jdk17
   D:\Android\sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=D:\Android\sdk --licenses
   D:\Android\sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=D:\Android\sdk "platforms;android-35" "build-tools;35.0.0" "platform-tools"
   ```
3. Создайте в корне проекта `local.properties` (в git он не попадает):
   ```properties
   sdk.dir=D\:\\Android\\sdk
   ```
4. Соберите:
   ```bat
   build.bat
   ```
   Если инструменты лежат не в `D:\Android`, задайте путь: `set ANDROID_TOOLS=C:\path\to\tools` перед запуском.
   С аргументами `build.bat` передаёт их в Gradle, например `build.bat clean assembleDebug`.

Готовый APK: `app\build\outputs\apk\debug\app-debug.apk`.

### Linux / macOS

```sh
export JAVA_HOME=/path/to/jdk17
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
```

## Установка на телефон

- Скопируйте APK на телефон и откройте его (разрешите установку из неизвестных источников), или
- включите отладку по USB и выполните:
  ```bat
  D:\Android\sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
  ```

После установки: откройте приложение, разрешите звонки, добавьте кнопки. Затем долгое нажатие
на рабочем столе → **Виджеты** → **Быстрый звонок**.

## Возможные проблемы

- **`PKIX path building failed` при скачивании зависимостей.** Антивирус или прокси подменяет
  HTTPS-сертификаты, а Java им не доверяет. `build.bat` уже передаёт
  `-Djavax.net.ssl.trustStoreType=Windows-ROOT` клиенту Gradle; для демона добавьте строку
  `systemProp.javax.net.ssl.trustStoreType=Windows-ROOT` в `%GRADLE_USER_HOME%\gradle.properties`.
- **Сборка падает на JDK 25.** Используйте JDK 17 (`JAVA_HOME`).
- **Звук идёт не в Bluetooth.** Проверьте, что устройство подключено именно как гарнитура
  (профиль «Телефонные звонки» / HFP), а не только для музыки.

## Структура

```
app/src/main/java/com/callwidget/
  MainActivity.kt        настройка кнопок (список, диалог, выбор контакта)
  ButtonStore.kt         хранение кнопок и настроек (SharedPreferences, JSON)
  CallWidgetProvider.kt  виджет: разметка, шаблон нажатия
  CallWidgetService.kt   RemoteViewsFactory: элементы сетки виджета
  CallActivity.kt        прозрачная activity: проверка Bluetooth и разрешения, звонок
app/src/main/res/
  layout/widget_call.xml, widget_item.xml   разметка виджета
  xml/call_widget_info.xml                  параметры виджета
```
