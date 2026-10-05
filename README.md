# Fractal Eye — живые обои для Android

Анимированный фрактальный глаз (Mandelbulb, рейтрейсинг во фрагментном шейдере) как Live Wallpaper.
Шейдеры лежат в `app/src/main/assets/`; `fractal-eye.html` — веб-превью с тем же шейдером
(см. «Шейдеры» ниже).

## Сборка
1. Установи Android Studio (Koala или новее, JDK 17 идёт в комплекте).
2. File → Open → папка `FractalEyeWallpaper`, дождись Gradle Sync (нужен интернет).
3. Build → Build Bundle(s) / APK(s) → Build APK(s). Файл: `app/build/outputs/apk/debug/app-debug.apk`.
4. Перенеси APK на телефон и установи (разреши установку из неизвестных источников).
   Либо подключи телефон по USB с включённой отладкой и нажми Run ▶.

Из командной строки: `./gradlew lint assembleDebug`.

### Gradle Wrapper (один раз)
В репозитории пока нет `gradlew`, `gradlew.bat` и `gradle/wrapper/gradle-wrapper.jar`
(есть только `gradle-wrapper.properties` с версией 8.9). Создай их и закоммить:

    gradle wrapper --gradle-version 8.9

Если Gradle не установлен, подойдут эти же три файла из любого другого Gradle-проекта:
версию задаёт `gradle-wrapper.properties`. После этого в `.github/workflows/android.yml`
замени `gradle lint assembleDebug` на `./gradlew lint assembleDebug`.

## Релиз
1. Создай ключ (один раз, храни вне репозитория):

       keytool -genkeypair -v -keystore fractaleye-release.jks -alias fractaleye \
           -keyalg RSA -keysize 2048 -validity 10000

2. Скопируй `keystore.properties.example` в `keystore.properties` и впиши путь и пароли.
   Файл в `.gitignore`; `*.jks`, `*.keystore`, `*.apk` и `*.aab` тоже не коммитятся.
3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/`. Без `keystore.properties`
   APK получится неподписанным.
4. В release включена минификация (R8): один раз установи release-APK и проверь, что обои
   запускаются с иконки и из системного выбора живых обоев.

## Установка обоев
Открой приложение «Fractal Eye Live Wallpaper» — сразу появится предпросмотр с кнопкой «Установить».
Или: Настройки → Обои → Живые обои → Fractal Eye.

## Что внутри
- Сцена рисуется в текстуру пониженного разрешения (30–55 % от экрана) и растягивается на весь экран.
  Масштаб подстраивается автоматически по скорости кадра.
- Лимит 30 кадров/с; когда обои не видны (экран выключен, открыто приложение), рендер приостанавливается.
- При перелистывании рабочих столов глаз слегка поворачивается.
- Если рендер упал, он один раз перезапускается; если не помогло, поверхность заливается
  тёмным цветом, а причина пишется в Logcat с тегом `FractalEye`.

## Код (`app/src/main/java/com/fractaleye/wallpaper/`)
| Файл | За что отвечает |
|------|-----------------|
| `FractalWallpaperService` | точка входа сервиса, общий `TAG` для логов |
| `FractalEngine` | жизненный цикл обоев, старт и остановка потока рендера |
| `RenderThread` | цикл кадров, повторный запуск при сбое, заливка цветом как запасной вариант |
| `EglHelper` | EGL: дисплей, контекст, поверхность, проверка каждого вызова |
| `SceneRenderer` | сцена в текстуру, вывод на экран, владение объектами GL |
| `AdaptiveScaler` | подбор разрешения по времени кадра |
| `GlUtils` | шейдеры и программы, чтение ассетов, проверка ошибок GL (только debug) |
| `Tuning` | все настроечные значения с единицами и пояснениями |
| `SetWallpaperActivity` | иконка приложения, открывает предпросмотр обоев |

## Настройка
Все константы в `Tuning.java`:
- `STEPS`, `ITERS` — качество фрактала. В коде сейчас сбалансированный пресет: 90 / 7
  (среднее: 120 / 9, максимум: 180 / 11, как в пресетах `fractal-eye.html`);
- `SCALE_MAX` — максимальное разрешение (сейчас 0.60; адаптивный scaler автоматически снижает его при перегрузке);
- `FRAME_NS` — лимит FPS (33_333_333L = 30 кадров/с, 16_666_667L = 60 кадров/с);
- пороги адаптивного разрешения и параметры анимации — там же.

## Шейдеры
Источник истины — `app/src/main/assets/quad.vert` и `shader.frag` (`blit.frag` нужен только Android).
Веб-превью обновляется командой:

    python3 tools/sync_shader.py

`python3 tools/sync_shader.py --check` проверяет, что превью не разошлось с шейдерами (запускается в CI).
В шейдерах только ASCII, в том числе в комментариях: часть GLSL-компиляторов не принимает иное.

## Стандарты
Правила кода и чек-лист для pull request — в `docs/CODING_STANDARDS.md`.
