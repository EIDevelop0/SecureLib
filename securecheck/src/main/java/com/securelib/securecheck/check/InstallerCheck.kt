package com.securelib.securecheck.check

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal class InstallerCheck(
    private val context: Context,
    private val allowedInstallers: Set<String>,
) : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean {
        val installer = readInstallerPackageName() ?: return false
        return installer in allowedInstallers
    }

    private fun readInstallerPackageName(): String? {
        val pm = context.packageManager
        val self = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(self).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(self)
        }
    }

    companion object {
        const val NAME = "InstallerCheck"

        const val PLAY_STORE_PACKAGE = "com.android.vending"

        val DEFAULT_ALLOWED: Set<String> = setOf(PLAY_STORE_PACKAGE)
    }
}
