package com.summer.core.util

import com.summer.core.data.local.entities.SenderType

private val PHONE_NUMBER_REGEX = Regex("^\\+?[0-9]{6,15}$")

/**
 * Checks if the string is a valid phone number format.
 * A valid phone number:
 * - Optionally starts with '+'
 * - Contains only digits (6-15 characters)
 */
fun String?.isValidPhoneNumber(): Boolean {
    if (this.isNullOrBlank()) return false
    return this.trim().matches(PHONE_NUMBER_REGEX)
}

fun String.determineSenderType(): SenderType {
    return when {
        matches(Regex("^\\+?[0-9]{8,15}$")) -> SenderType.CONTACT

        else -> SenderType.BUSINESS
    }
}

fun String.trimSenderId(): String {
    return replace(Regex("^[A-Z]{2}-"), "")
}

/**
 * Strips all non-digit characters from a phone number, keeping only the leading '+' if present.
 */
fun String.stripNonDigits(): String {
    val hasPlus = this.contains("+")
    val digitsOnly = replace(Regex("[^0-9]"), "")
    return if (hasPlus) "+$digitsOnly" else digitsOnly
}

/**
 * Normalizes a phone number to a consistent format for comparison.
 * First strips all non-digit characters, then applies country code normalization.
 */
fun String.normalizePhoneNumber(defaultCountryCode: Int): String {
    // First strip all non-digit characters (except leading +)
    val stripped = stripNonDigits()
    
    return when {
        stripped.matches(Regex("^\\+[0-9]{10,15}$")) -> stripped

        stripped.matches(Regex("^${defaultCountryCode}[0-9]{10}$")) -> "+$stripped"

        stripped.matches(Regex("^0[0-9]{10}$")) -> "+$defaultCountryCode${stripped.substring(1)}"

        stripped.matches(Regex("^[0-9]{10}$")) -> "+$defaultCountryCode$stripped"

        stripped.matches(Regex("^[789][0-9]{9}$")) -> "$defaultCountryCode$stripped"

        else -> stripped
    }
}