package com.securelib.securecheck.check

import android.os.Debug
import com.securelib.securecheck.internal.NativeChecks

/**
 * Fails when either a JDWP debugger (Android Studio / IntelliJ Debug) or a
 * native ptrace-based tracer (Frida server, gdb, strace) is attached.
 *
 * Two orthogonal signals are combined because they cover disjoint threats:
 *   - JDWP attach is visible only via [android.os.Debug] — the JDWP handshake
 *     is handled inside the ART VM without invoking ptrace, so TracerPid
 *     stays 0 in /proc/self/status.
 *   - Ptrace attach is visible only from native code reading /proc/self/status —
 *     Java-level Debug APIs are unaware of Frida / gdb / strace.
 */
internal class NativeDebuggerCheck : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return false
        if (NativeChecks.debuggerDetected()) return false
        return true
    }

    companion object {
        const val NAME = "DebuggerCheck"
    }
}
