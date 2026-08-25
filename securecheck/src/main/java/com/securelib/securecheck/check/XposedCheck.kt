package com.securelib.securecheck.check

import java.io.File

internal class XposedCheck : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean {
        if (xposedClassPresent()) return false
        if (File("/system/framework/XposedBridge.jar").exists()) return false
        if (xposedFramesInStackTrace()) return false
        return true
    }

    private fun xposedClassPresent(): Boolean = try {
        Class.forName("de.robv.android.xposed.XposedBridge")
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    // Even when LSPosed hides the XposedBridge class, its hooks leave
    // frames in the stack of a thrown exception.
    private fun xposedFramesInStackTrace(): Boolean = try {
        throw XposedProbe()
    } catch (probe: XposedProbe) {
        probe.stackTrace.any { it.className.startsWith("de.robv.android.xposed") }
    }

    private class XposedProbe : Throwable()

    companion object {
        const val NAME = "XposedCheck"
    }
}
