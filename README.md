# SecureLib

Android security library — runtime detection of rooted devices, attached
debuggers, Frida / Xposed / Zygisk instrumentation, tampered APKs and
un-official installers. One synchronous call returns a single `Boolean`;
a companion call returns a per-check breakdown for logging.

Distributed via **[JitPack](https://jitpack.io)**. Consumers don't need
NDK, don't need GitHub credentials, don't need to build the library
themselves — JitPack compiles the AAR (including native `.so` files for
all four ABIs) on its side and serves the prebuilt artifact.

---

## Installation

### 1. Add the JitPack repository

In your consumer project's **`settings.gradle.kts`**:

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

### 2. Add the dependency

In your app module's **`build.gradle.kts`**:

```kotlin
dependencies {
    implementation("com.github.EIDevelop0:SecureLib:0.2.0")
}
```

The version string matches a git tag in the repository. Available tags:

- `0.1.0` — pure Kotlin baseline (no NDK involvement, ~30 KB)
- `0.2.0` — adds native C++ backing for Frida / Debugger / Zygisk checks
  and introduces the new `ZygiskCheck` (recommended)

### 3. Sync

```
File → Sync Project with Gradle Files
```

On first sync JitPack will build the tag on its side (usually 3–8 minutes
for `0.2.0` because of the NDK step). Subsequent syncs pull the cached
artifact instantly.

**No NDK setup required on the consumer side.** JitPack ships the
already-built `.so` files for `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`
inside the AAR. Your project's regular Android SDK is enough.

---

## Quick start

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

`check()` returns `true` only when **every** enabled check has passed.
It is a `suspend` function because opt-in checks (Play Integrity) may
perform network I/O.

---

## API reference

### Building an instance

```kotlin
val secureCheck: SecureCheck = SecureCheck.Builder(context)
    // ---- Required ----
    .expectedPackageName("com.example.myapp")

    // ---- Optional configuration of defaults ----
    .allowedInstallers(                   // default: Play Store only
        "com.android.vending",
        "com.samsung.android.appstore",
        "ru.vk.store",
    )

    // ---- Opt-in checks ----
    .addSignatureValidator(
        expectedSha256 = "3A:5F:1E:...:AB:CD",
    )
    .addPlayIntegrityValidator(
        cloudProjectNumber = 1234567890L,
        verifier = { token -> myBackend.verifyIntegrityToken(token) },
    )

    // ---- Disable individual defaults ----
    // .disablePackageNameCheck()
    // .disableDebugBuildCheck()
    // .disableDebuggerCheck()
    // .disableRootCheck()
    // .disableFridaCheck()
    // .disableZygiskCheck()
    // .disableXposedCheck()
    // .disableInstallerCheck()

    .build()
```

The `Builder(context)` constructor grabs `context.applicationContext`
internally — you can safely pass an `Activity` without leaking it.

### Running the checks

Two entry points, both `suspend`:

```kotlin
val ok: Boolean = secureCheck.check()
```

Returns `true` when every enabled check passed, `false` otherwise. Ignores
which specific check failed.

```kotlin
val result: SecurityCheckResult = secureCheck.checkDetailed()
result.passed            // Boolean — same as check()
result.checks            // List<CheckOutcome> — every check, in registration order
result.failedChecks      // List<CheckOutcome> — failures only, convenience

// CheckOutcome shape:
data class CheckOutcome(
    val name: String,      // e.g. "RootCheck", "SignatureCheck", …
    val passed: Boolean,
    val error: String?,    // set when the check itself threw an exception
)
```

Use `checkDetailed()` for logging, diagnostic screens, or when your
mitigation depends on which check failed (e.g. show a store link if
`InstallerCheck` failed vs. a "root not supported" screen if `RootCheck`
failed).

### Failure semantics

- A check that returns `false` sets `passed = false` and leaves `error = null`.
- A check that throws an exception is treated as failed (`passed = false`)
  and its `throwable.message` lands in `error`.
- The library never silently degrades security — if the native library
  fails to load, the three native-backed checks each surface an
  `UnsatisfiedLinkError` message in `error`.

---

## Checks

### Default checks (active without configuration)

| Check | Detects | Disable |
|---|---|---|
| `PackageNameCheck` | Runtime package name differs from your baked-in value — catches simple re-packagers who renamed the APK. | `.disablePackageNameCheck()` |
| `DebugBuildCheck` | `ApplicationInfo.FLAG_DEBUGGABLE` is set — catches accidental shipping of a debug build to production. | `.disableDebugBuildCheck()` |
| `DebuggerCheck` | Either a JDWP debugger is attached (Android Studio Debug, IntelliJ) or a native ptrace-based tracer (Frida server, gdb, strace) is present. Combines Java `Debug.isDebuggerConnected()` with native `TracerPid` reading — neither alone catches both threats. | `.disableDebuggerCheck()` |
| `RootCheck` | 10 known `su` binary paths, 12 known root-manager packages (`com.topjohnwu.magisk`, `eu.chainfire.supersu`, …), `Build.TAGS = "test-keys"`. | `.disableRootCheck()` |
| `FridaCheck` | `/proc/self/maps` contains `frida` / `gum-js-loop` / `gadget`. Native C++ implementation — harder to hook than Java-level `File.readLines`. | `.disableFridaCheck()` |
| `ZygiskCheck` | `/proc/self/maps` contains injected Zygisk / Riru libraries. Catches Magisk-based hiding frameworks that bypass file-path root detection. Native C++. | `.disableZygiskCheck()` |
| `XposedCheck` | `de.robv.android.xposed.XposedBridge` class loadable, `/system/framework/XposedBridge.jar` file present, Xposed frames in a probe stack trace (catches LSPosed even when the class is hidden). | `.disableXposedCheck()` |
| `InstallerCheck` | App's installing package (via `PackageManager.getInstallSourceInfo`) is not in the allowed set. Default: `com.android.vending` only. Configurable via `.allowedInstallers(...)`. | `.disableInstallerCheck()` |

### Opt-in checks

Neither runs unless you call the corresponding `addXxx` method on the builder.

#### `addSignatureValidator(expectedSha256)`

Compares SHA-256 of the APK's signing certificate against an expected
value hardcoded in your app. Detects repackaging even when the attacker
kept the package name.

```kotlin
.addSignatureValidator(
    expectedSha256 = "3A:5F:1E:B2:CC:DA:...",
)
```

Colons, dashes, spaces and case are all normalised — pass whatever
`keytool` gave you.

**How to get the expected hash for your keystore:**

```bash
keytool -list -v -keystore my-release-key.jks -alias my-alias | grep SHA256
```

For the debug keystore Android Studio uses by default:

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android | grep SHA256
```

Ship the hash as a Kotlin string constant — **do not read it from a
resource** (attackers patching the APK will also patch the resource).

#### `addPlayIntegrityValidator(cloudProjectNumber, verifier)`

Requests a Google Play Integrity token, then delegates the verdict to a
`verifier` lambda you provide.

```kotlin
.addPlayIntegrityValidator(
    cloudProjectNumber = 1234567890L,
    verifier = { token ->
        // Send the token to YOUR backend for decryption and verdict inspection.
        myBackend.verifyIntegrityToken(token)
    },
)
```

**Why the verifier is mandatory:** the Play Integrity response is
encrypted with a key held by your GCP service account. Only your backend
can decrypt it and read the verdicts (`deviceIntegrity.deviceRecognitionVerdict`,
`appIntegrity.appRecognitionVerdict`, `accountDetails.appLicensingVerdict`).
Any client-side interpretation is trivially defeated by an on-device MITM
substituting a fake response. The library refuses to guess.

If you accept the risk for a prototype and want an inline verifier:

```kotlin
verifier = { token ->
    val payload = token.split(".")[1]
    val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
    "MEETS_DEVICE_INTEGRITY" in json
}
```

**Requirements:**
- Google Play Services 22+ on the device
- App linked to a GCP project (Cloud Console → project number)
- Device must have Google Play Store — the check fails on Huawei-only
  devices, most emulators, and de-Googled ROMs. That is by design.

---

## Recipes

### Fail-fast at app start

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val secureCheck = SecureCheck.Builder(this)
            .expectedPackageName(BuildConfig.APPLICATION_ID)
            .addSignatureValidator(expectedSha256 = BuildConfig.RELEASE_SIGNATURE_SHA256)
            .build()

        // Fire-and-forget: don't block onCreate, but bring down the app
        // early if the environment is compromised.
        GlobalScope.launch(Dispatchers.Default) {
            if (!secureCheck.check()) {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }
    }
}
```

### Reactive UI with diagnostic breakdown

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

### Different behavior per check

```kotlin
val result = secureCheck.checkDetailed()
if (result.passed) return

result.failedChecks.forEach { outcome ->
    when (outcome.name) {
        "InstallerCheck"   -> promptOpenPlayStore()
        "RootCheck"        -> warnRootedDevice()
        "SignatureCheck"   -> hardBlock("Tampered APK")
        "PlayIntegrityCheck" -> logToTelemetry("integrity failure", outcome.error)
        else               -> logToTelemetry("security failure: ${outcome.name}", outcome.error)
    }
}
```

### Debug builds — relaxed policy

Some checks legitimately fail during development (`DebuggerCheck` when
running under Android Studio Debug, `InstallerCheck` when `adb install`
sets the installer to `null`). Turn them off in debug:

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

Or gate the whole `secureCheck.check()` call behind `!BuildConfig.DEBUG`.

### Multi-store distribution

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

## Requirements

- **Consumer `minSdk`**: 26 (Android 8.0)
- **`compileSdk`**: 33+ recommended
- **Kotlin**: 1.9+ (uses stdlib `suspend`)
- **Android Gradle Plugin**: 7.4+
- **Play Integrity check only**: device with Google Play Services

No NDK required on the consumer side — JitPack builds and packages the
native components.

---

## Caveats

- **`DebuggerCheck` fails when running under Android Studio's *Debug*
  configuration.** Use *Run* (Shift+F10), or add `.disableDebuggerCheck()`
  under `if (BuildConfig.DEBUG)`.
- **`InstallerCheck` fails during local development** (installed via
  `adb`, which sets installer to `null`). Disable in debug builds or
  install via Play Console Internal Testing.
- **Root / Frida / Xposed / Zygisk detectors are heuristic.** Determined
  attackers running Magisk with Shamiko, LSPosed with hooked `File.exists`,
  or patched Frida gadgets will bypass individual checks. The library
  gives defense in depth, not perfect coverage. Combine with server-side
  signals (Play Integrity forwarded to your backend, behavioural analytics).
- **Play Integrity is only meaningful with backend verification.** A
  compromised device can inject a fake success response; only your backend
  reading Google's decrypted verdict is trustworthy.
- **Some verdicts are one-shot per launch.** `check()` re-runs everything
  each call, but system state (rooted / not) doesn't usually change
  mid-session. Cache the result at the app level if you invoke it often.
- **First JitPack build for a fresh tag takes 3–10 minutes** on JitPack's
  side. If a developer syncs a brand-new version right after your `git
  tag`, they'll see JitPack building in the sync log. Subsequent syncs
  pull from JitPack's cache instantly.

---

## Releasing a new version (maintainer notes)

1. Bump `version` in `securecheck/build.gradle.kts` (e.g. `0.2.0` → `0.3.0`)
2. Update this README's `implementation("com.github.EIDevelop0:SecureLib:X.Y.Z")` line
3. Commit and push
4. Tag the commit: `git tag 0.3.0 && git push origin 0.3.0`
5. (Optional) Open the tag in JitPack (`https://jitpack.io/com/github/EIDevelop0/SecureLib/0.3.0/`)
   to pre-warm the build — otherwise the first consumer to request that
   version will trigger it (and wait a few minutes)

Consumers then update the version string. No republishing action needed
from the maintainer's side beyond pushing the tag.

---

## Developing the library

Open the whole `SecureLib` project in Android Studio. The `:app` module
depends on `:securecheck` via `project(":securecheck")`, so any change
to the library is immediately visible in the sample app — no publishing
required for local iteration.

```
./gradlew :securecheck:assembleRelease       # build the AAR standalone
./gradlew :app:installDebug                  # run the sample app on a connected device
./gradlew :securecheck:publishToMavenLocal   # smoke-test Maven coordinates locally
```

Repository layout:

```
SecureLib/
├── app/           Sample application for developing and testing the library
└── securecheck/   The library module consumers actually import
    ├── src/main/cpp/       Native C++ sources (Frida / Debugger / Zygisk probes)
    ├── src/main/java/      Kotlin API, check implementations, Builder
    └── build.gradle.kts    CMake wiring, ABIs, maven-publish config
```

---

## Credits

Native detection techniques for Frida, debugger, and Zygisk (in
`securecheck/src/main/cpp/`) are inspired by
[NativeShield](https://github.com/PhuongDoZz/NativeShield) by PhuongDoZz
(MIT © 2025). Implementations here are original and rewritten around
this library's on-demand `check(): Boolean` API rather than
NativeShield's continuous background-thread logging model.

---

## License

TBD — decide before public release. If you plan to distribute this to
consumers outside your organization, add an explicit `LICENSE` file at
the repository root.
