package com.kitsugi.animelist.data.auth

/**
 * Per-operation cache for the expensive catalog identity check.
 *
 * The cached value is `verified`, while callers need `blocked`; keeping the inversion here
 * prevents a rejected first target from becoming an allowed cached target (or vice versa).
 */
internal class CrossWriteVerificationGate {
    private var sourceIdentityVerified: Boolean? = null

    suspend fun isBlocked(
        target: String,
        rowSource: String,
        realMalId: Int?,
        verify: suspend () -> Boolean
    ): Boolean {
        if (target == rowSource || realMalId == null) return false
        sourceIdentityVerified?.let { return !it }
        val verified = try {
            verify()
        } catch (_: Exception) {
            false
        }
        sourceIdentityVerified = verified
        return !verified
    }
}
