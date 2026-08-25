package com.securelib.securecheck.check

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

internal class RootCheck(
    private val context: Context,
) : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean {
        if (hasTestKeysBuildTag()) return false
        if (anySuBinaryPresent()) return false
        if (anyRootPackageInstalled()) return false
        return true
    }

    private fun hasTestKeysBuildTag(): Boolean =
        Build.TAGS?.contains("test-keys") == true

    private fun anySuBinaryPresent(): Boolean = SU_PATHS.any { File(it).exists() }

    private fun anyRootPackageInstalled(): Boolean {
        val pm = context.packageManager
        return ROOT_PACKAGES.any { pkg -> isPackageInstalled(pm, pkg) }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean =
        try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    companion object {
        const val NAME = "RootCheck"

        private val SU_PATHS = listOf(
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/vendor/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/su/bin/su",
        )

        // Kept in sync with <queries> in AndroidManifest.xml.
        internal val ROOT_PACKAGES = listOf(
            "com.topjohnwu.magisk",
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.kingroot.kinguser",
            "com.kingo.root",
            "com.smedialink.oneclickroot",
            "com.zhiqupk.root.global",
            "com.alephzain.framaroot",
        )
    }
}
