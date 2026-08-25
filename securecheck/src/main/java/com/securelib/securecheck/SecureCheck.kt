package com.securelib.securecheck

import android.content.Context
import com.securelib.securecheck.check.DebugBuildCheck
import com.securelib.securecheck.check.DebuggerCheck
import com.securelib.securecheck.check.FridaCheck
import com.securelib.securecheck.check.InstallerCheck
import com.securelib.securecheck.check.PackageNameCheck
import com.securelib.securecheck.check.PlayIntegrityCheck
import com.securelib.securecheck.check.PlayIntegrityVerifier
import com.securelib.securecheck.check.RootCheck
import com.securelib.securecheck.check.SecurityCheck
import com.securelib.securecheck.check.SignatureCheck
import com.securelib.securecheck.check.XposedCheck

class SecureCheck internal constructor(
    private val checks: List<SecurityCheck>,
) {
    suspend fun check(): Boolean = checkDetailed().passed

    suspend fun checkDetailed(): SecurityCheckResult {
        val outcomes = checks.map { securityCheck ->
            try {
                CheckOutcome(
                    name = securityCheck.name,
                    passed = securityCheck.evaluate(),
                )
            } catch (t: Throwable) {
                CheckOutcome(
                    name = securityCheck.name,
                    passed = false,
                    error = t.message ?: t::class.simpleName,
                )
            }
        }
        return SecurityCheckResult(
            passed = outcomes.all { it.passed },
            checks = outcomes,
        )
    }

    class Builder(context: Context) {
        private val appContext: Context = context.applicationContext
        private var expectedPackageName: String? = null
        private var allowedInstallers: Set<String> = InstallerCheck.DEFAULT_ALLOWED
        private val disabledDefaults = mutableSetOf<String>()
        // Opt-in checks configured but not yet materialised. Kept as lambdas
        // so we don't need to construct check instances until build().
        private val optInFactories = mutableListOf<() -> SecurityCheck>()

        fun expectedPackageName(packageName: String) = apply {
            expectedPackageName = packageName
        }

        fun allowedInstallers(vararg installerPackageNames: String) = apply {
            require(installerPackageNames.isNotEmpty()) {
                "allowedInstallers() requires at least one package name."
            }
            allowedInstallers = installerPackageNames.toSet()
        }

        // ---- Disable default checks -----------------------------------------

        fun disablePackageNameCheck() = apply { disabledDefaults.add(PackageNameCheck.NAME) }

        fun disableDebugBuildCheck() = apply { disabledDefaults.add(DebugBuildCheck.NAME) }

        fun disableDebuggerCheck() = apply { disabledDefaults.add(DebuggerCheck.NAME) }

        fun disableRootCheck() = apply { disabledDefaults.add(RootCheck.NAME) }

        fun disableFridaCheck() = apply { disabledDefaults.add(FridaCheck.NAME) }

        fun disableXposedCheck() = apply { disabledDefaults.add(XposedCheck.NAME) }

        fun disableInstallerCheck() = apply { disabledDefaults.add(InstallerCheck.NAME) }

        // ---- Opt-in checks ---------------------------------------------------

        fun addSignatureValidator(expectedSha256: String) = apply {
            optInFactories += { SignatureCheck(appContext, expectedSha256) }
        }

        /**
         * The [verifier] receives the raw Play Integrity token and must return
         * whether the device/app is trusted. In production, forward the token
         * to your backend for decryption and verdict inspection — client-side
         * interpretation is trivial to defeat.
         */
        fun addPlayIntegrityValidator(
            cloudProjectNumber: Long,
            verifier: PlayIntegrityVerifier,
        ) = apply {
            optInFactories += {
                PlayIntegrityCheck(appContext, cloudProjectNumber, verifier)
            }
        }

        fun build(): SecureCheck {
            val checks = mutableListOf<SecurityCheck>()

            if (PackageNameCheck.NAME !in disabledDefaults) {
                val expected = expectedPackageName ?: error(
                    "expectedPackageName(...) must be set, or call disablePackageNameCheck() to skip.",
                )
                checks += PackageNameCheck(appContext, expected)
            }
            if (DebugBuildCheck.NAME !in disabledDefaults) {
                checks += DebugBuildCheck(appContext)
            }
            if (DebuggerCheck.NAME !in disabledDefaults) {
                checks += DebuggerCheck()
            }
            if (RootCheck.NAME !in disabledDefaults) {
                checks += RootCheck(appContext)
            }
            if (FridaCheck.NAME !in disabledDefaults) {
                checks += FridaCheck()
            }
            if (XposedCheck.NAME !in disabledDefaults) {
                checks += XposedCheck()
            }
            if (InstallerCheck.NAME !in disabledDefaults) {
                checks += InstallerCheck(appContext, allowedInstallers)
            }

            optInFactories.forEach { checks += it() }

            return SecureCheck(checks)
        }
    }
}
