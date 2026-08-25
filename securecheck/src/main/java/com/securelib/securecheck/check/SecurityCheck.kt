package com.securelib.securecheck.check

internal interface SecurityCheck {
    val name: String

    suspend fun evaluate(): Boolean
}
