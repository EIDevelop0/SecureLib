package com.securelib.securecheck.check

import android.content.Context
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityTokenResponse
import java.security.SecureRandom
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A suspending function that takes the raw Play Integrity token and returns
 * whether the device/app should be considered trusted.
 *
 * The recommended implementation forwards the token to your backend, which
 * decrypts it with your GCP service account and inspects the verdicts
 * (`deviceIntegrity.deviceRecognitionVerdict`, `appIntegrity.appRecognitionVerdict`,
 * `accountDetails.appLicensingVerdict`). Client-side interpretation is trivial
 * to defeat by a MITM on the response.
 */
typealias PlayIntegrityVerifier = suspend (token: String) -> Boolean

internal class PlayIntegrityCheck(
    private val context: Context,
    private val cloudProjectNumber: Long,
    private val verifier: PlayIntegrityVerifier,
) : SecurityCheck {
    override val name: String = NAME

    override suspend fun evaluate(): Boolean {
        val token = requestToken() ?: return false
        return verifier(token)
    }

    private suspend fun requestToken(): String? {
        val manager = IntegrityManagerFactory.create(context)
        val request = IntegrityTokenRequest.builder()
            .setNonce(generateNonce())
            .setCloudProjectNumber(cloudProjectNumber)
            .build()
        val response: IntegrityTokenResponse = manager.requestIntegrityToken(request).await()
        return response.token()
    }

    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        const val NAME = "PlayIntegrityCheck"
    }
}

// Local Task.await() to avoid adding kotlinx-coroutines-play-services just for one call.
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { error -> cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
