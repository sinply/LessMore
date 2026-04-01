package com.appcontrol.domain.usecase

import java.security.MessageDigest

/**
 * Shared SHA-256 hashing utility for password use cases.
 */
object PasswordHashUtil {
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
