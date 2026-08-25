package com.securelib.securecheck.check

import com.securelib.securecheck.internal.NativeChecks

internal class NativeFridaCheck : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean = !NativeChecks.fridaDetected()

    companion object {
        const val NAME = "FridaCheck"
    }
}
