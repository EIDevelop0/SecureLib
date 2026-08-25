package com.securelib.securecheck.check

import android.content.Context

internal class PackageNameCheck(
    private val context: Context,
    private val expectedPackageName: String,
) : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean = context.packageName == expectedPackageName

    companion object {
        const val NAME = "PackageNameCheck"
    }
}
