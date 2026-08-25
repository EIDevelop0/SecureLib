# SecureLib

Android-бібліотека безпеки — виявлення в рантаймі рутованих пристроїв,
приєднаних налагоджувачів, інструментації Frida / Xposed / Zygisk,
підмінених APK та встановлень з неофіційних джерел. Один synchronous
виклик повертає єдиний `Boolean`; додатковий виклик повертає розбивку
по кожній перевірці для логування.

Поширюється через **[JitPack](https://jitpack.io)**. Споживачам не
потрібен NDK, не потрібні GitHub-креденшіали, не потрібно збирати
бібліотеку самостійно — JitPack компілює AAR (включно з нативними
`.so` для всіх ABI, якщо вони є у версії) на своєму боці й віддає
готовий артефакт.

---

## Вибір версії

| Версія | Тип | Перевірки | Розмір | Вимоги |
|---|---|---|---|---|
| **`0.1.0`** | Чистий Kotlin | 7 базових | ~30 KB | лише Android SDK |
| **`0.2.0`** | Kotlin + native C++ | 8 базових (додано `ZygiskCheck`, а `FridaCheck` і `DebuggerCheck` мають нативний бекенд для стійкості до Java-хуків) | ~3 MB (4 ABI × ~800 KB) | NDK збирає JitPack — споживачу не треба |

**Що обрати:**
- Якщо ви робите звичайний застосунок і хочете базову захист — беріть **`0.1.0`**. Мінімальний оверхед, простіше життя.
- Якщо у застосунку є щось цінне (банкінг, платежі, крипта, DRM-контент, розмежування прав) — беріть **`0.2.0`**. Нативні перевірки суттєво важче обійти через LSPosed/Frida hooks на Java-API.

Обидві версії мають **однаковий публічний API** — переключення між ними
це просто зміна рядка версії в `implementation(...)`. Опис нижче
позначає різниці явно.

---

## Встановлення

### 1. Додати репозиторій JitPack

У `settings.gradle.kts` проєкту-споживача:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Додати залежність

У `build.gradle.kts` модуля-споживача (зазвичай `app`):

```kotlin
dependencies {
    // Оберіть одну версію:
    implementation("com.github.EIDevelop0:SecureLib:0.1.0")   // Kotlin-only
    // implementation("com.github.EIDevelop0:SecureLib:0.2.0") // Kotlin + native C++
}
```

### 3. Sync

```
File → Sync Project with Gradle Files
```

При першому синку JitPack збере тег на своєму боці (зазвичай 2–5 хв
для `0.1.0`, 3–8 хв для `0.2.0` через NDK-крок). Подальші синки
підтягуватимуть закешований артефакт миттєво.

**NDK на боці споживача не потрібен.** JitPack віддає готові `.so`
всередині AAR. Стандартного Android SDK у вашому проєкті достатньо.

---

## Швидкий старт

```kotlin
import com.securelib.securecheck.SecureCheck
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val secureCheck by lazy {
        SecureCheck.Builder(this)
            .expectedPackageName(BuildConfig.APPLICATION_ID)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MyApp() }

        lifecycleScope.launch {
            if (!secureCheck.check()) {
                showBlockingSecurityWarning()
                finish()
            }
        }
    }
}
```

`check()` — це `suspend` функція, тому що opt-in перевірки (Play
Integrity) можуть виконувати мережевий I/O. Викликати треба з будь-якого
coroutine scope: `lifecycleScope`, `viewModelScope`, `LaunchedEffect`.

---

## API

### Побудова інстансу

```kotlin
val secureCheck: SecureCheck = SecureCheck.Builder(context)
    // ---- Обов'язково ----
    .expectedPackageName("com.example.myapp")

    // ---- Опціональна конфігурація дефолтних перевірок ----
    .allowedInstallers(                   // за замовч.: лише Play Store
        "com.android.vending",
        "com.samsung.android.appstore",
        "ru.vk.store",
    )

    // ---- Opt-in перевірки ----
    .addSignatureValidator(
        expectedSha256 = "3A:5F:1E:...:AB:CD",
    )
    .addPlayIntegrityValidator(
        cloudProjectNumber = 1234567890L,
        verifier = { token -> myBackend.verifyIntegrityToken(token) },
    )

    // ---- Вимкнення окремих дефолтних перевірок ----
    // .disablePackageNameCheck()
    // .disableDebugBuildCheck()
    // .disableDebuggerCheck()
    // .disableRootCheck()
    // .disableFridaCheck()
    // .disableZygiskCheck()   // тільки 0.2.0+
    // .disableXposedCheck()
    // .disableInstallerCheck()

    .build()
```

`Builder(context)` всередині бере `context.applicationContext` — можна
передавати `Activity` без ризику витоку.

### Запуск перевірок

Два методи, обидва `suspend`:

```kotlin
val ok: Boolean = secureCheck.check()
```
Повертає `true`, коли всі активні перевірки пройшли. Ігнорує деталі
провалу.

```kotlin
val result: SecurityCheckResult = secureCheck.checkDetailed()
result.passed            // Boolean — те саме, що й check()
result.checks            // List<CheckOutcome> — усі перевірки в порядку реєстрації
result.failedChecks      // List<CheckOutcome> — лише провалені, для зручності

data class CheckOutcome(
    val name: String,      // "RootCheck", "SignatureCheck", ...
    val passed: Boolean,
    val error: String?,    // заповнене, якщо перевірка кинула виняток
)
```

Використовуйте `checkDetailed()` для логування, діагностичних екранів
або коли реакція залежить від конкретної провалу (напр. посилання на
стор — при провалі `InstallerCheck`, а екран "рутовані не підтримуються"
— при провалі `RootCheck`).

### Семантика провалів

- Перевірка, що повернула `false` → `passed = false`, `error = null`.
- Перевірка, що кинула виняток → трактується як провалена, і
  `throwable.message` потрапляє в `error`.
- Бібліотека ніколи не деградує безпеку тихо — якщо нативна бібліотека
  (у `0.2.0`) не вантажиться, три нативні перевірки віддадуть
  `UnsatisfiedLinkError` через `CheckOutcome.error`.

---

## Перевірки

### Дефолтні (активні без конфігурації)

Позначки: 🅺 = чистий Kotlin, 🇨 = нативний C++ бекенд, ✨ = додано у версії.

| Перевірка | Реалізація | Що виявляє | Вимкнути |
|---|---|---|---|
| `PackageNameCheck` | 🅺 | Ім'я пакета в рантаймі відрізняється від зашитого — ловить простих repackager'ів, які перейменували APK. | `.disablePackageNameCheck()` |
| `DebugBuildCheck` | 🅺 | Встановлений прапорець `ApplicationInfo.FLAG_DEBUGGABLE` — ловить випадкове відправлення debug-збірки в прод. | `.disableDebugBuildCheck()` |
| `DebuggerCheck` | 🅺 `0.1.0` / 🇨 `0.2.0` | Приєднаний JDWP-налагоджувач (Android Studio Debug, IntelliJ) АБО нативний ptrace-tracer (Frida server, gdb, strace). Комбінує Java `Debug.isDebuggerConnected()` з читанням `TracerPid` — жодне з двох окремо не ловить обидві загрози. | `.disableDebuggerCheck()` |
| `RootCheck` | 🅺 | 10 відомих шляхів до `su`, 12 відомих root-менеджер-пакетів (`com.topjohnwu.magisk`, `eu.chainfire.supersu`, …), `Build.TAGS = "test-keys"`. | `.disableRootCheck()` |
| `FridaCheck` | 🅺 `0.1.0` / 🇨 `0.2.0` | У `/proc/self/maps` знайдено `frida` / `gum-js-loop` / `gadget`. У версії `0.2.0` — нативний C++, стійкий до Java-хуків на `File.readLines`. | `.disableFridaCheck()` |
| `ZygiskCheck` ✨ `0.2.0` | 🇨 | У `/proc/self/maps` знайдено ін'єкцію Zygisk / Riru. Ловить Magisk-based приховувальники, що обходять file-path root-детекцію. | `.disableZygiskCheck()` |
| `XposedCheck` | 🅺 | Клас `de.robv.android.xposed.XposedBridge` завантажується, файл `/system/framework/XposedBridge.jar` присутній, фрейми Xposed у пробному stack trace (ловить LSPosed навіть коли клас прихований). | `.disableXposedCheck()` |
| `InstallerCheck` | 🅺 | Installer-пакет застосунку (через `PackageManager.getInstallSourceInfo`) не в дозволеному наборі. За замовч.: лише `com.android.vending`. Налаштовується через `.allowedInstallers(...)`. | `.disableInstallerCheck()` |

**Ключова відмінність версій:** у `0.2.0` `FridaCheck` і `DebuggerCheck`
переписані на нативний C++ (значно стійкіші до обходу через LSPosed або
Java-рівневі хуки), і додано зовсім нову `ZygiskCheck`.

### Opt-in перевірки (не в дефолтному наборі)

Не запускаються, поки ви не викличете відповідний `addXxx` метод.

Присутні в **обох версіях**. Реалізація ідентична.

#### `addSignatureValidator(expectedSha256)`

Порівнює SHA-256 сертифіката підпису APK з очікуваним значенням, зашитим
у застосунку. Виявляє перепакування навіть коли атакуючий залишив
package name.

```kotlin
.addSignatureValidator(
    expectedSha256 = "3A:5F:1E:B2:CC:DA:...",
)
```

Двокрапки, дефіси, пробіли й регістр нормалізуються — передавайте що
завгодно у форматі виводу `keytool`.

**Як отримати хеш для вашого keystore:**

```bash
keytool -list -v -keystore my-release-key.jks -alias my-alias | grep SHA256
```

Для debug-keystore, який Android Studio використовує за замовчуванням:

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android | grep SHA256
```

Зашивайте хеш як Kotlin string-константу — **не** читайте з ресурсу
(атакуючий, що патчить APK, так само пропатчить і ресурс).

#### `addPlayIntegrityValidator(cloudProjectNumber, verifier)`

Запитує токен Google Play Integrity, потім делегує вердикт вашій
`verifier` лямбді.

```kotlin
.addPlayIntegrityValidator(
    cloudProjectNumber = 1234567890L,
    verifier = { token ->
        // Надішліть токен на ВАШ бекенд для розшифрування та інспекції вердиктів.
        myBackend.verifyIntegrityToken(token)
    },
)
```

**Чому `verifier` обов'язковий:** відповідь Play Integrity зашифрована
ключем із вашого GCP service account. Лише ваш бекенд може її
розшифрувати та прочитати вердикти (`deviceIntegrity.deviceRecognitionVerdict`,
`appIntegrity.appRecognitionVerdict`, `accountDetails.appLicensingVerdict`).
Будь-яка клієнтська інтерпретація тривіально обходиться MITM'ом
відповіді на скомпрометованому пристрої. Бібліотека відмовляється
вгадувати.

Якщо для прототипу згодні на клієнтську інтерпретацію:

```kotlin
verifier = { token ->
    val payload = token.split(".")[1]
    val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
    "MEETS_DEVICE_INTEGRITY" in json
}
```

**Вимоги:**
- Google Play Services 22+ на пристрої
- Застосунок прив'язаний до GCP проєкту (Cloud Console → project number)
- На пристрої має бути Google Play Store — перевірка провалиться на
  Huawei-only пристроях, більшості емуляторів, ROM'ах без Google-сервісів.
  Це поведінка за задумом.

---

## Рецепти

### Fail-fast при старті застосунку

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val secureCheck = SecureCheck.Builder(this)
            .expectedPackageName(BuildConfig.APPLICATION_ID)
            .addSignatureValidator(expectedSha256 = BuildConfig.RELEASE_SIGNATURE_SHA256)
            .build()

        // Fire-and-forget: не блокуємо onCreate, але вбиваємо застосунок
        // якщо оточення скомпрометовано.
        GlobalScope.launch(Dispatchers.Default) {
            if (!secureCheck.check()) {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
```

### Реактивний UI з діагностичною розбивкою

```kotlin
@Composable
fun SecurityGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val secureCheck = remember {
        SecureCheck.Builder(context)
            .expectedPackageName(BuildConfig.APPLICATION_ID)
            .build()
    }
    var result by remember { mutableStateOf<SecurityCheckResult?>(null) }

    LaunchedEffect(Unit) { result = secureCheck.checkDetailed() }

    when (val snapshot = result) {
        null -> LoadingScreen()
        else -> if (snapshot.passed) {
            content()
        } else {
            BlockedScreen(
                reasons = snapshot.failedChecks.map { it.name },
            )
        }
    }
}
```

### Різна поведінка на різні провалені перевірки

```kotlin
val result = secureCheck.checkDetailed()
if (result.passed) return

result.failedChecks.forEach { outcome ->
    when (outcome.name) {
        "InstallerCheck"     -> promptOpenPlayStore()
        "RootCheck"          -> warnRootedDevice()
        "SignatureCheck"     -> hardBlock("Підмінений APK")
        "PlayIntegrityCheck" -> logToTelemetry("integrity failure", outcome.error)
        else                 -> logToTelemetry("security failure: ${outcome.name}", outcome.error)
    }
}
```

### Debug-збірки — послаблена політика

Деякі перевірки закономірно провалюються під час розробки (`DebuggerCheck`
при запуску з Android Studio Debug, `InstallerCheck` при `adb install`,
який виставляє installer у `null`). Вимикайте їх для debug:

```kotlin
SecureCheck.Builder(context)
    .expectedPackageName(BuildConfig.APPLICATION_ID)
    .apply {
        if (BuildConfig.DEBUG) {
            disableDebuggerCheck()
            disableInstallerCheck()
            disableDebugBuildCheck()
        }
    }
    .build()
```

Або взагалі виконуйте `secureCheck.check()` тільки якщо `!BuildConfig.DEBUG`.

### Мульти-стор дистрибуція

```kotlin
.allowedInstallers(
    "com.android.vending",          // Google Play Store
    "com.samsung.android.appstore", // Samsung Galaxy Store
    "com.huawei.appmarket",         // Huawei AppGallery
    "ru.vk.store",                  // RuStore
    "com.amazon.venezia",           // Amazon Appstore
)
```

---

## Вимоги

- **`minSdk` споживача**: 26 (Android 8.0)
- **`compileSdk`**: 33+ рекомендовано
- **Kotlin**: 1.9+ (використовує stdlib `suspend`)
- **Android Gradle Plugin**: 7.4+
- **Тільки для Play Integrity**: пристрій з Google Play Services

NDK на боці споживача не потрібен — JitPack збирає й пакує нативні
компоненти (`0.2.0`).

---

## Застереження

### Спільні для всіх версій

- **`DebuggerCheck` провалюється при запуску під конфігурацією *Debug*
  в Android Studio.** Використовуйте *Run* (Shift+F10) або додайте
  `.disableDebuggerCheck()` в блоці `if (BuildConfig.DEBUG)`.
- **`InstallerCheck` провалюється під час локальної розробки**
  (встановлення через `adb`, який виставляє installer у `null`).
  Вимикайте в debug-збірках або тестуйте через Play Console Internal Testing.
- **Root / Frida / Xposed / Zygisk детектори евристичні.** Наполегливі
  атакуючі з Magisk + Shamiko, LSPosed з хуками на `File.exists` або
  пропатчений Frida gadget обходять окремі перевірки. Бібліотека дає
  ешелоновану оборону, а не ідеальне покриття. Комбінуйте з серверними
  сигналами (Play Integrity, forwarded to backend, behavioural analytics).
- **Play Integrity має сенс лише з бекенд-валідацією.** Скомпрометований
  пристрій може підмінити відповідь на успішну; лише ваш бекенд, що читає
  розшифрований Google-вердикт, — надійний.
- **Стан системи змінюється рідко в межах сесії.** `check()` перезапускає
  все на кожен виклик, але (рутовано / не рутовано) в мідлі сесії не
  змінюється. Кешуйте результат на рівні застосунку, якщо викликаєте часто.
- **Перша збірка нового тегу в JitPack — 3–10 хвилин.** Розробник, що
  синкне свіжу версію одразу після `git tag`, побачить лог JitPack'у в
  Gradle sync. Наступні синки — з кеша JitPack'у миттєво.

### Специфічно для `0.2.0` (native)

- Розмір AAR — ~3 MB (4 ABI по ~800 KB). Якщо ваш застосунок цільує
  тільки arm64 — можете додати `abiFilters` в `defaultConfig.ndk` вашого
  застосунку, JitPack-варіант це поважає.
- Якщо нативна бібліотека не завантажилась (`UnsatisfiedLinkError` в
  `outcome.error`) — це швидше за все атака на процес. Перевірки
  провалюються навмисно.

---

## Випуск нової версії (для мейнтейнерів)

1. Оновіть `version` у `securecheck/build.gradle.kts` (напр. `0.2.0` → `0.3.0`)
2. Оновіть у цьому README рядок `implementation("com.github.EIDevelop0:SecureLib:X.Y.Z")`
3. Закомітьте й пушніть
4. Позначте тегом: `git tag 0.3.0 && git push origin 0.3.0`
5. (Опційно) Відкрийте тег у JitPack —
   `https://jitpack.io/com/github/EIDevelop0/SecureLib/0.3.0/` — щоб
   заздалегідь прогріти збірку. Інакше перший споживач, що запитає цю
   версію, тригерне її сам (і почекає кілька хвилин).

Споживачі просто оновлюють рядок версії. Жодних додаткових дій від
мейнтейнера крім push тегу не потрібно.

---

## Розробка бібліотеки

Відкрийте весь `SecureLib` в Android Studio. Модуль `:app` залежить від
`:securecheck` через `project(":securecheck")`, тож будь-яка зміна в
бібліотеці одразу видима в демо-застосунку — публікація не потрібна.

```
./gradlew :securecheck:assembleRelease       # зібрати AAR окремо
./gradlew :app:installDebug                  # запустити демо на підключеному пристрої
./gradlew :securecheck:publishToMavenLocal   # smoke-тест Maven-координат локально
```

Структура репозиторію:

```
SecureLib/
├── app/           Демо-застосунок для розробки та тестування бібліотеки
└── securecheck/   Модуль бібліотеки, який імпортують споживачі
    ├── src/main/cpp/       Native C++ джерела (тільки в 0.2.0+)
    ├── src/main/java/      Kotlin API, реалізації перевірок, Builder
    └── build.gradle.kts    CMake wiring (0.2.0+), ABI, maven-publish
```

**Гілки:**
- `master` — версія `0.1.0` (чистий Kotlin)
- `native_shield_update` — версія `0.2.0` (додає native C++ бекенд і `ZygiskCheck`)

---

## Кредити

Нативні техніки виявлення Frida, налагоджувача і Zygisk (в
`securecheck/src/main/cpp/`, версія `0.2.0`) натхнені проєктом
[NativeShield](https://github.com/PhuongDoZz/NativeShield) від
PhuongDoZz (MIT © 2025). Імплементації тут — оригінальні, переписані
під on-demand `check(): Boolean` API цієї бібліотеки, а не під
continuous background-thread модель NativeShield.

---

## Ліцензія

TBD — визначити перед публічним релізом. Якщо плануєте поширювати
бібліотеку за межі організації, додайте явний `LICENSE` файл у корені
репозиторію.
