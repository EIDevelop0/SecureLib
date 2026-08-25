# SecureLib

Private Android security library distributed via **Gradle source dependencies**.
No artifact publishing required — Gradle clones the repository and builds the
`:securecheck` module directly from source, resolving versions via git tags.

Published module coordinates: **`com.securelib:securecheck`**

---

## Repository layout

```
SecureLib/
├── app/           Sample application for developing and testing the library
└── securecheck/   The library module consumers actually import
```

---

## Consuming the library

### 1. Grant git access

Every developer and every CI job that builds a consumer project must be able to
`git clone` this repository. Use one of:

- SSH key registered with the organization
- HTTPS + a git credential helper storing a Personal Access Token

Gradle uses the local git configuration transparently — no extra credentials
are wired through Gradle itself.

### 2. Declare the source dependency

In the consumer project's **`settings.gradle.kts`**, add a `sourceControl`
block at the top level (outside `dependencyResolutionManagement`):

```kotlin
sourceControl {
    gitRepository(java.net.URI("git@github.com:<your-org>/SecureLib.git")) {
        producesModule("com.securelib:securecheck")
    }
}
```

The `producesModule` string **must** match the `group:artifactId` published by
the library module (see `securecheck/build.gradle.kts`).

### 3. Depend on the module

In any consumer module's **`build.gradle.kts`**:

```kotlin
dependencies {
    implementation("com.securelib:securecheck:0.2.0")
}
```

Version strings resolve against **git tags** in this repository. Tags are
matched by exact version, so `0.2.0` resolves the commit tagged `0.2.0`
(or `v0.2.0` — Gradle strips the leading `v`).

### 4. First build

On the first build Gradle will:

1. Clone `SecureLib` into `~/.gradle/checkouts/`
2. Check out the tag matching the requested version
3. Build the `:securecheck` module
4. Cache the produced artifacts

Subsequent builds reuse the cache. To force a re-clone: delete
`~/.gradle/checkouts/`.

---

## API

### Minimum usage

```kotlin
val secureCheck = SecureCheck.Builder(context)
    .expectedPackageName("com.example.myapp")
    .build()

// From any coroutine scope (viewModelScope, lifecycleScope, LaunchedEffect…):
val ok: Boolean = secureCheck.check()
```

`check()` returns `true` only when **every** enabled check passed.

### Detailed result

```kotlin
val result: SecurityCheckResult = secureCheck.checkDetailed()
result.passed             // Boolean — same as check()
result.checks             // List<CheckOutcome> — every check, in registration order
result.failedChecks       // Convenience: only the failed ones

data class CheckOutcome(
    val name: String,       // e.g. "RootCheck"
    val passed: Boolean,
    val error: String?,     // set when the check itself threw
)
```

Use `checkDetailed()` for diagnostics, logging, or when you need to react
differently to different failures.

---

## Checks

### Default (active without configuration)

| Check | What it detects | Disable |
|---|---|---|
| `PackageNameCheck` | Runtime package name differs from the one you baked in | `.disablePackageNameCheck()` |
| `DebugBuildCheck` | `ApplicationInfo.FLAG_DEBUGGABLE` is set | `.disableDebugBuildCheck()` |
| `DebuggerCheck` | JDWP debugger attached, or `TracerPid != 0` in `/proc/self/status` (catches Frida/gdb ptrace). Implemented in native C++ so `java.io.File` hooks do not defeat it. | `.disableDebuggerCheck()` |
| `RootCheck` | `su` binaries in 10 known paths, 12 known root-manager packages installed, `Build.TAGS = test-keys` | `.disableRootCheck()` |
| `FridaCheck` | `/proc/self/maps` contains `frida` / `gum-js-loop` / `gadget`. Native C++ implementation — harder to hook than Java-level file access. | `.disableFridaCheck()` |
| `ZygiskCheck` | `/proc/self/maps` contains injected Zygisk / Riru libraries (`zygisk`, `libzygisk`, `riru`, `libriru`). Catches Magisk-based hiding frameworks that bypass file-path root detection. Native C++. | `.disableZygiskCheck()` |
| `XposedCheck` | `XposedBridge` class loadable, `/system/framework/XposedBridge.jar` present, Xposed frames in a probe stack trace (catches LSPosed) | `.disableXposedCheck()` |
| `InstallerCheck` | App was not installed from an allowed installer package (default: Play Store only) | `.disableInstallerCheck()` |

**Required configuration:**

- `.expectedPackageName("com.example.myapp")` — mandatory unless
  `PackageNameCheck` is explicitly disabled. `build()` throws
  `IllegalStateException` otherwise.

**Optional configuration:**

- `.allowedInstallers(vararg String)` — override the installer whitelist.
  Example for multi-store distribution:
  ```kotlin
  .allowedInstallers("com.android.vending", "com.samsung.android.appstore", "ru.vk.store")
  ```

### Opt-in

Neither is active unless you call the corresponding `addXxx` method.

#### `addSignatureValidator(expectedSha256: String)`

Compares the SHA-256 of the running APK's signing certificate against a
hard-coded expected value. Detects APK repackaging.

```kotlin
.addSignatureValidator(
    expectedSha256 = "3A:5F:1E:...:AB:CD",  // colons / dashes / spaces / case are all tolerated
)
```

**Getting the expected hash for your release keystore:**

```bash
keytool -list -v -keystore my-release-key.jks -alias my-alias | grep SHA256
```

Ship the hash as a string in your code — do **not** read it from a resource
file, since a repackager will simply update the resource.

#### `addPlayIntegrityValidator(cloudProjectNumber, verifier)`

Requests a Play Integrity token from Google, then hands it to a `verifier`
lambda you supply. The check passes only if `verifier` returns `true`.

```kotlin
.addPlayIntegrityValidator(
    cloudProjectNumber = 1234567890L,
    verifier = { token ->
        // Send the token to YOUR backend, which decrypts and inspects it.
        // Return whether the device/app is trusted.
        myApi.verifyIntegrityToken(token)
    },
)
```

**Why is `verifier` mandatory?**

The Play Integrity response is encrypted with a key held by your GCP service
account. Only your backend can decrypt it. Any "client-side" interpretation
of the token is trivially bypassed by an attacker on a compromised device
who MITMs the response back into your app. The library therefore refuses to
guess a verdict — you decide how to trust the token.

If you accept the risk of client-side interpretation (e.g. for a pre-launch
prototype), your `verifier` can decode the JWT payload itself:

```kotlin
verifier = { token ->
    val payload = token.split(".")[1]
    val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE))
    "MEETS_DEVICE_INTEGRITY" in json
}
```

**Play Integrity requirements:**

- Google Play Services 22+ installed on the device
- App linked to a Google Cloud project (get the project number from the
  Google Cloud Console)
- Device must have Google Play Store — the check will fail on
  Huawei-only devices, most emulators, and de-Googled ROMs

---

## Caveats

- **`DebuggerCheck` fails when you run under Android Studio's debugger.** Use
  *Run* rather than *Debug*, or temporarily add `.disableDebuggerCheck()`
  while iterating.
- **Root / Frida / Xposed detectors are heuristic.** Determined attackers
  running Magisk Hide, Zygisk, LSPosed with hooks on `File.exists`, or
  patched Frida gadgets will bypass individual checks. The library gives you
  defense in depth, not perfect coverage. Combine with server-side signals.
- **Play Integrity is only meaningful with backend verification.** A device
  with a fake response injected client-side will otherwise pass every check.
- **`InstallerCheck` fails during local development** (installed via `adb`,
  which sets installer package name to `null`). Disable it in debug builds
  or run from Play Console internal testing.
- **First-build latency for consumers**: source dependencies clone + build
  once per version. Subsequent builds hit the cache.
- **NDK is required to build.** The library ships a small native component
  (`libsecurecheck.so`) used by `FridaCheck`, `DebuggerCheck`, and
  `ZygiskCheck`. First-time consumers will see Gradle download the Android
  NDK on their machine (one-time, ~1 GB). All four ABIs are built:
  `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`. If the native library
  fails to load at runtime, the three native-backed checks each surface
  `UnsatisfiedLinkError` through `CheckOutcome.error` — no silent
  degradation of security posture.

---

## Releasing a new version

1. Bump `version` in `securecheck/build.gradle.kts`
2. Commit
3. Tag the commit: `git tag 0.2.0 && git push origin 0.2.0`

Consumers then update the version string in their `implementation(...)` line.

---

## Developing the library

Open the whole `SecureLib` project in Android Studio. The `:app` module already
depends on `:securecheck` via `project(":securecheck")`, so any change to the
library is immediately visible in the sample app — no republishing needed.

```
./gradlew :securecheck:assembleRelease   # build the library standalone
./gradlew :app:installDebug              # run the sample app
./gradlew :securecheck:publishToMavenLocal   # smoke-test the maven coordinates
```

---

## Credits

Native detection techniques for Frida, debugger, and Zygisk (in
`securecheck/src/main/cpp/`) are inspired by
[NativeShield](https://github.com/PhuongDoZz/NativeShield) by PhuongDoZz
(MIT © 2025). Implementations here are original and adapted to this
library's on-demand `check(): Boolean` API rather than the continuous
background-thread model used by NativeShield.
