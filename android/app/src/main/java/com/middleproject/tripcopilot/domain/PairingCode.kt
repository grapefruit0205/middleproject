package com.middleproject.tripcopilot.domain

/**
 * Canonical validation of human-entered pairing codes. The raw code is normalized
 * (trimmed, uppercased) before the strict pattern check; invalid codes are rejected
 * with a bounded message and are never transmitted to the server.
 */
object PairingCode {

    private val PATTERN = Regex("[A-Z0-9]{5}-[A-Z0-9]{5}")

    /** Returns the canonical form or null when the input cannot be a valid code. */
    fun canonical(input: String?): String? {
        if (input == null) return null
        val trimmed = input.trim().uppercase()
        return if (PATTERN.matches(trimmed)) trimmed else null
    }
}
