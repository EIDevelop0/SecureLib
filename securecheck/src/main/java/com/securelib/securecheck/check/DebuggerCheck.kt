package com.securelib.securecheck.check

import android.os.Debug
import java.io.File

internal class DebuggerCheck : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean {
        if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) return false
        return readTracerPid() == 0
    }

    // TracerPid > 0 => a process (gdb, Frida, strace, …) is attached via ptrace.
    private fun readTracerPid(): Int =
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("TracerPid:") }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull()
                ?: 0
        }

    companion object {
        const val NAME = "DebuggerCheck"
    }
}
