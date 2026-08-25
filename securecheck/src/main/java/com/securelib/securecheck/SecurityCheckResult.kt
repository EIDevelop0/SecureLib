package com.securelib.securecheck

data class SecurityCheckResult(
    val passed: Boolean,
    val checks: List<CheckOutcome>,
) {
    val failedChecks: List<CheckOutcome>
        get() = checks.filterNot { it.passed }
}

data class CheckOutcome(
    val name: String,
    val passed: Boolean,
    val error: String? = null,
)
