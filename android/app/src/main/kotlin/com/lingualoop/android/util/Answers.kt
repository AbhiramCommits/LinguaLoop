package com.lingualoop.android.util

/**
 * Answer normalization, ported from the web client so grading is identical
 * across platforms: strip diacritics, lowercase, drop non-letter/digit
 * characters.
 */
fun normalizeAnswer(text: String): String =
    java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]"), "")

fun isCorrectAnswer(given: String, expected: String): Boolean =
    normalizeAnswer(given) == normalizeAnswer(expected)
