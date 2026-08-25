// JNI facade for com.securelib.securecheck.internal.NativeChecks.
//
// Three synchronous jboolean probes callable from the Kotlin object.
// Function names follow the standard JNI mangling rule
// (Java_<fully_qualified_class>_<method>) so that plain `external fun`
// declarations resolve without an explicit RegisterNatives step.
//
// Anti-hooking / anti-tamper techniques and marker choices are inspired
// by the NativeShield project (https://github.com/PhuongDoZz/NativeShield,
// MIT, © 2025 PhuongDo). Sources here are original implementations
// written for the on-demand check() API of this library.

#include <jni.h>

#include "debugger_probe.h"
#include "frida_scan.h"
#include "zygisk_probe.h"

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_securelib_securecheck_internal_NativeChecks_fridaDetected(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return scanMapsForFrida() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_securelib_securecheck_internal_NativeChecks_debuggerDetected(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return isDebuggerAttached() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_securelib_securecheck_internal_NativeChecks_zygiskDetected(
    JNIEnv* /*env*/, jobject /*thiz*/) {
    return isZygiskDetected() ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
