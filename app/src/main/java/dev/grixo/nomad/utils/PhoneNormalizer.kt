package dev.grixo.nomad.utils

object PhoneNormalizer {
    /** Match backend E.164-ish normalization (India 10-digit → +91). */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        var cleaned = trimmed.filter { it.isDigit() || it == '+' }
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.drop(2)
        }
        val digits = cleaned.filter { it.isDigit() }
        if (cleaned.startsWith("+")) {
            return if (digits.length >= 8) "+$digits" else null
        }
        if (digits.length == 10 && digits.first() in "6789") {
            return "+91$digits"
        }
        if (digits.length >= 11) {
            return "+$digits"
        }
        return cleaned.takeIf { it.length >= 8 }
    }
}
