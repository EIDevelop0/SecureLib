package com.securelib.securecheck.internal

/**
 * JNI facade for native security probes. All probes are synchronous
 * and return `true` when a threat is detected (matching C++ convention).
 *
 * Loads `libsecurecheck.so` on first access. If the native library is
 * missing (e.g. the consumer stripped ABIs and the device architecture
 * isn't included), calls to the external functions raise
 * `UnsatisfiedLinkError`, which the SecureCheck aggregator surfaces as
 * a failed check with the error message attached.
 */
internal object NativeChecks {
    init {
        System.loadLibrary("securecheck")
    }

    external fun fridaDetected(): Boolean

    external fun debuggerDetected(): Boolean

    external fun zygiskDetected(): Boolean
}
