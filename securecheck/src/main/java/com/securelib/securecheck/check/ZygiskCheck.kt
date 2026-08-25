package com.securelib.securecheck.check

import com.securelib.securecheck.internal.NativeChecks

internal class ZygiskCheck : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean = !NativeChecks.zygiskDetected()

    companion object {
        const val NAME = "ZygiskCheck"
    }
}
