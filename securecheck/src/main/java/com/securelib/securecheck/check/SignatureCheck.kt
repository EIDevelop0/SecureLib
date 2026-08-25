package com.securelib.securecheck.check

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

internal class SignatureCheck(
    private val context: Context,
    expectedSha256: String,
) : SecurityCheck {
    override val name: String = NAME

    private val expected: String = normalizeHex(expectedSha256)

    override suspend fun evaluate(): Boolean {
        val actualHashes = readSigningCertificatesSha256()
        return actualHashes.any { it == expected }
    }

    private fun readSigningCertificatesSha256(): List<String> {
        val signatures = readSignatures()
        val md = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig -> md.digest(sig.toByteArray()).toHex() }
    }

    private fun readSignatures(): List<Signature> {
        val pm = context.packageManager
        val pkg = context.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo ?: return emptyList()
            val array = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            array?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_CHARS[v ushr 4])
            sb.append(HEX_CHARS[v and 0x0F])
        }
        return sb.toString()
    }

    companion object {
        const val NAME = "SignatureCheck"

        private val HEX_CHARS = "0123456789abcdef".toCharArray()

        internal fun normalizeHex(hex: String): String =
            hex.replace(":", "")
                .replace("-", "")
                .replace(" ", "")
                .lowercase()
    }
}
